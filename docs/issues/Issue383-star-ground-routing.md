# Issue 383 — Star Ground Autorouting Support

**GitHub Issue:** https://github.com/freerouting/freerouting/issues/383  
**Label:** enhancement  
**Status:** Open — analysis complete, implementation not started

---

## What Is Being Requested?

The reporter asks for Freerouting to natively support **star ground routing** as an autorouting mode or constraint. Currently, achieving a star ground on a GND net requires:

1. Manually routing every ground trace from the star center point to each ground pin.
2. Locking those traces before running the autorouter.
3. Letting the autorouter route everything else around the pre-placed ground traces.

This workflow is fragile: placing the star point manually is a guess. If the board later proves that a different location for the star point would be globally better (e.g., fewer detours for other nets), the designer has to redo step 1 by hand, re-run the autorouter, and iterate. The request is to allow the router itself to find the optimal star center and radial path arrangement automatically.

---

## What Is a Star Ground?

A **star ground** (also called a single-point ground) is a grounding topology where every circuit sub-section connects its local ground return to **one common point** via independent, non-shared traces. No ground trace is shared between two sub-circuits.

```
    [Sub-circuit A] ──────────────────┐
                                      │
    [Sub-circuit B] ──────────────────┤◄── Star center (one physical pad or virtual junction)
                                      │
    [Sub-circuit C] ──────────────────┘
```

### Why Is It Used?

In a conventional ground net the router is free to create a spanning tree of any topology — it may daisy-chain pads or create a bus structure. This causes the return current of sub-circuit A to flow through the same copper that carries the return current of sub-circuit B. The resulting **common-impedance coupling** produces unwanted crosstalk. In audio amplifiers, analog front-ends, and sensitive sensor circuits the resulting ground noise can be measured and audible.

A star ground eliminates common-impedance coupling by ensuring each sub-circuit's return current has a private copper path back to the reference point. The only current that flows in a shared segment is beyond the star center, where all returns merge and flow to the supply rail — but at that stage the signal information is lost in the bulk current.

### Variants

| Variant | Description |
|---------|-------------|
| **Single-point star** | All pads connect to one physical location. Suited for low-frequency / audio designs. |
| **Multi-point star** | Groups of pads form local stars; each local star then connects to a global star. Common in mixed-signal boards. |
| **Hierarchical star** | Recursive multi-level grouping; digital sub-system has its own local star, analog has another, both share a single ground point at the power supply. |

### Key Electrical Properties

* Each branch of the star is **independent**: its impedance is determined only by that branch's geometry, not by the aggregate of all return currents.
* At **high frequencies**, parasitic inductance in long star radials can be worse than a solid ground plane; star grounds are primarily relevant below ~1 MHz.
* The star center need not be a physical component pad — it can be a copper polygon junction, a via, or a dedicated test point pad.

---

## Current Behavior in Freerouting

Freerouting uses a **minimum-spanning-tree / maze-search** approach (Lee algorithm / `MazeSearchAlgo`) for each net. The router has full freedom to choose any connection order that results in a valid spanning tree.

For a GND net with N pads, the router typically produces a daisy-chain or bus-like MST — exactly what a star ground must avoid.

Relevant code locations:

| Class | Role |
|-------|------|
| `BatchAutorouter.getAutorouteItems()` | Builds the ordered list of connectable items to route per pass |
| `BatchAutorouter.autoroute_pass()` | Iterates items and calls `autoroute_item()` for each |
| `MazeSearchAlgo` | Expands the routing maze from source items toward destination items |
| `InsertFoundConnectionAlgo` | Inserts the route found by the maze search into the board |
| `LocateFoundConnectionAlgo` | Traces the backpointer path through the maze result |
| `rules.Net` | Per-net metadata; already has `contains_plane` and `subnet_number` fields |
| `rules.NetClass` | Per-net-class routing rules; has `is_ignored_by_autorouter` |
| `io.specctra.parser.Network` | Parses the `(net ...)` scope including `(fromto ...)` and `(order ...)` clauses |

### Existing Subnet / FromTo Infrastructure

The Specctra DSN format already supports a `(fromto pin_A pin_B)` clause inside a `(net ...)` scope. When present, Freerouting's `Network.read_net_scope()` splits the net into **sub-nets** (distinct `Net` objects sharing the same `name` but different `subnet_number` values). The autorouter treats each sub-net as an independent routing task. This mechanism is currently the only way to enforce connectivity constraints at the net level.

Star ground is a special case of sub-net decomposition: every pin-to-star-center pair becomes its own sub-net (2 terminals each). The difficulty is that the star center is a **virtual junction** — it is not necessarily a physical pin in the netlist.

---

## Implementation Options

### Option A — Pure Post-Processing (No Router Changes)

**How it works:**  
After the autorouter finishes, post-process the routed GND net by introducing a virtual star-center junction and rerouting only the GND traces.

**Pros:** Minimal change to the core engine; no risk of routing regression.  
**Cons:** Does not guide the router toward a good star topology while routing other nets; the position of the star center is still chosen heuristically after the fact.

---

### Option B — Pre-Processing: DSN Sub-net Expansion (Recommended Starting Point)

**How it works:**  
When a net is flagged for star-ground routing (e.g., via a custom DSN attribute or a Freerouting router setting), the loader decomposes it into N sub-nets before routing begins, introducing a virtual star center node (a synthetic `Pin`-like object at a user-specified or automatically computed location). Each sub-net has exactly 2 terminals: the real GND pin and the virtual center.

The existing `fromto` / subnet mechanism in `rules.Net` (the `subnet_number` field and the `Nets.add(name, subnet_number, ...)` path) is already designed for this — each radial arm is just a 2-terminal sub-net of the same logical net.

**Star center placement strategies:**
- **User-specified**: The user picks a point or pad on the board; Freerouting honors it.
- **Geometric centroid**: Compute the centroid of all GND pads and use the nearest board point (or a nearby via).
- **Weighted centroid**: Weight pads by signal sensitivity (user-annotated component class); sensitive analog pads get more weight.

**Pros:**
- Reuses the already-working `fromto` / subnet routing path.
- Guarantees the star topology is achieved by construction.
- No changes to `MazeSearchAlgo` or `InsertFoundConnectionAlgo`.

**Cons:**
- The virtual star center node must be injected into the board model as a real `Item` (most likely a temporary "virtual pin" or a fixed trace stub at the computed star point).
- The star center location heavily affects routability; a bad choice can make other nets harder to route.
- Needs careful interaction with the optimizer pass: the star sub-nets must not be re-joined into a daisy chain by the optimizer.

---

### Option C — Topology Constraint in the Maze Router

**How it works:**  
Extend `MazeSearchAlgo` with a topology policy that, for nets flagged as star-ground, forces the destination set to always include the star center as a mandatory waypoint before reaching other terminals.

This is the most algorithmic approach but requires significant changes to the maze search state machine.

**Pros:** The router actively optimizes the star topology jointly with all other nets; can find globally better solutions.  
**Cons:** High implementation risk; likely to cause routing regressions until thoroughly validated.

---

### Option D — Routing Order Constraint (Simpler Heuristic)

**How it works:**  
Sort items to route such that, for a designated star-ground net, all items are grouped and routed sequentially from a common source (the designated star center pad). In `BatchAutorouter.getAutorouteItems()`, insert the star-center item first for the designated net; the maze search will naturally attach each subsequent terminal to the already-routed star.

**Pros:** Minimal code change; no new data model concepts.  
**Cons:** Does not guarantee a true star topology — the optimizer may later consolidate branches; works only if the star center is an existing physical pad.

---

## Recommended Implementation Path

A pragmatic, low-risk approach is to combine **Option B** (sub-net expansion) with **Option D** (controlled routing order) in two phases:

### Phase 1 — RouterSettings + NetClass Annotation

Add a `starGroundNetNames` (or `starGroundNetClass`) field to `RouterSettings` / `RouterScoringSettings`. When set, the named net(s) are treated as star-ground nets during routing.

#### Files to change
- `settings/RouterSettings.java` — add `public List<String> starGroundNetNames`
- `settings/sources/DefaultSettings.java` — initialize to empty list
- `settings/sources/ApiSettings.java` / `GlobalSettings.java` — expose in API/CLI/env

### Phase 2 — Star Center Computation

Add a `StarGroundPlanner` utility class (package: `app.freerouting.autoroute`) that:

1. Collects all pins of the designated net.
2. Computes a candidate star center (geometric centroid or user override).
3. Returns the board coordinates of the star center.

### Phase 3 — Virtual Star Center Item

Introduce the star center as a **shove-fixed zero-size via** or a **fixed point pad** on the board at the computed location. This becomes the common hub pin for all sub-net connections.

> [!IMPORTANT]
> The virtual hub item must survive board restores in `BoardHistory`. It must be inserted before routing begins and removed (or kept as a via) when the autorouter finishes.

### Phase 4 — Sub-net Expansion at Load Time

In `BatchAutorouter.runBatchLoop()` (or a pre-routing step called from `HeadlessBoardManager`), when a star-ground net is detected:

1. Create N sub-nets (one per real GND pin), each connecting `(real_pin, hub_via)`.
2. Remove the original monolithic net from the `Nets` collection (or mark it as ignored).
3. Proceed with normal routing.

This is analogous to what `Network.read_net_scope()` does for `(fromto ...)` clauses, but triggered programmatically rather than from the DSN file.

### Phase 5 — Optimizer Guard

After routing, the optimizer must not merge star sub-net traces into a daisy chain. Guard this with a check in `BatchOptimizer` or `OptViaAlgo`: if a trace belongs to a sub-net whose parent net is a star-ground net, skip trace-length reduction moves that would alter the star topology.

---

## Acceptance Criteria

- [ ] A net designated as a star ground is routed such that all its pins connect to a single common point (the star center) and not to each other in series.
- [ ] The star center location can be specified by the user (as a pad or coordinate) or computed automatically.
- [ ] No clearance violations are introduced by the star center hub item.
- [ ] The rest of the board (non-star nets) routes with no regression in completion rate vs. a baseline without the feature.
- [ ] The feature is opt-in: boards without the setting route identically to the current behavior.
- [ ] The feature is accessible via the REST API (`RouterSettings.starGroundNetNames`).
- [ ] A regression test is added using a simple multi-op-amp DSN fixture that verifies the GND net forms a star topology in the resulting `.ses` file.

---

## Open Questions

1. **Who specifies the star center?** Should it always be the closest existing pad (e.g., a power-supply bypass capacitor GND pin), or should a new concept of a "virtual pad" be introduced into the board model?
2. **Multi-level star:** Is a two-level hierarchical star (digital GND star + analog GND star, meeting at one point) in scope for a first implementation?
3. **Interaction with copper pours:** If the GND net already has a copper pour (`contains_plane = true`), star ground routing is somewhat redundant at DC. Should the feature be silently skipped for plane nets, or allowed and documented as "analog star on a signal layer above the pour"?
4. **DSN output:** Should the resulting sub-nets be exported as distinct `(net GND 1)`, `(net GND 2)`, ... entries in the `.ses` file, or re-merged into a single `GND` net for EDA tool compatibility?
5. **KiCad integration:** KiCad does not natively expose a DSN-level star constraint; the Freerouting KiCad plugin would need a UI element (e.g., a net-class property) to trigger the feature.

---

## References

- `src/main/java/app/freerouting/autoroute/BatchAutorouter.java` — `getAutorouteItems()`, `autoroute_pass()`
- `src/main/java/app/freerouting/rules/Net.java` — `subnet_number`, `contains_plane`
- `src/main/java/app/freerouting/rules/NetClass.java` — `is_ignored_by_autorouter`
- `src/main/java/app/freerouting/io/specctra/parser/Network.java` — `read_net_scope()`, `fromto` handling (line 1096–1101)
- `src/main/java/app/freerouting/autoroute/MazeSearchAlgo.java` — core maze expansion engine
- `src/main/java/app/freerouting/autoroute/InsertFoundConnectionAlgo.java` — trace insertion
- `src/main/java/app/freerouting/settings/RouterSettings.java` — settings model
- EE StackExchange "Star ground vs. ground plane": https://electronics.stackexchange.com/questions/11462
