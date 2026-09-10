# Board scoring (V2)

Freerouting reports two independent **board scores**, each on a **0–1000** scale
(higher is better). They are not interchangeable, and they are not the maze-search
costs that steer A* while a trace is being laid.

| Layer | What it is | What it is not |
| --- | --- | --- |
| Maze-search costs | `router.scoring` (`via_costs`, preferred-direction trace costs, rip-up costs). These tell the maze which expansion is cheaper **right now**. | A board quality number. |
| Router score | “Is the board finished and legal?” Used by the autorouter, board history, GUI, and API `normalized_score`. Default formula: `V2_CONTINUOUS`. | Wire-length / via / bend beauty. |
| Optimizer score | “How efficient is the copper versus a computable lower bound?” Used by the optimizer keep/undo gate and API `optimizer_score`. Default formula: `V2_LOWER_BOUND`. | A completeness meter. Incomplete nets and extra DRC hits are **vetoes**, not terms in this formula. |

V1 (`V1_LEGACY`) remains an explicit compatibility override for each score
independently (`--router-scoring-version`, `--optimizer-scoring-version`, or
`--scoring-version` for both). Frozen Freerouting 1.9.0 keeps its historical
formula; it does not implement V2.

Implementation: `BoardStatistics.getRouterScore` /
`getOptimizerScore` in `app.freerouting.core.scoring`. Defaults:
`DefaultSettings`. Settings fields: `docs/settings.md`. Design history:
`docs/research/scoring_revision_plan.md`.

---

## Shared size scale

Some penalties must be smaller on a large, dense board than on a tiny one, otherwise
one extra via on an 800-pin design would look as bad as one extra via on a 10-pin
breakout.

$$
C = \max(1,\ P \times N_L)
\qquad
D = C
$$

| Symbol | Settings / code | Technical meaning | In plain language |
| --- | --- | --- | --- |
| \(P\) | `items.pin_count` | Number of pads/pins on the board. | How many places copper has to land. |
| \(N_L\) | `layers.signal_count` | Number of signal (routable copper) layers. | How many floors the building has. |
| \(C\) | `difficulty.complexity_c` | Complexity kernel: pin-layers. | A size tag: “lots of pins on lots of layers is a bigger job.” |
| \(D\) | `difficulty.difficulty_d` | Score denominator; currently \(D = C\), floored at 1. | The stick we divide via, bend, and DRC penalties by so a big board is not punished for existing. |

**What does not use \(D\):** the unrouted-connection fraction (that is already a
percentage of the job) and excess wire length (that is already a percentage of
\(L_{\min}\)). Board area is stored for analysis; it is **not** in the score.

---

## Router score (`V2_CONTINUOUS`)

The autorouter’s report card: start at 1000, subtract unfinished work and clearance
trouble, never go below 0.

$$
\mathrm{score}_{\mathrm{router}}
=
\max\bigl(0,\ 1000 - W_1 o_1 - W_2 o_2 - W_C \tfrac{N_{\mathrm{viol}}}{D}
- W_D \tfrac{\sum L_{\mathrm{um}}}{U_{\mathrm{scale}}\, D}\bigr)
$$

A fully open, clean board scores **0**. A fully connected board with no clearance
violations scores **1000**. A board with no nets at all and no violations also
scores **1000** (defined; not `NaN`).

### Unfinished connections

Let \(N_{\mathrm{conn}}\) be `connections.maximumCount` (the number of two-pin
connection edges the ratsnest still has to close — **not** DSN `net_count`).
Let \(N_{\mathrm{open}}\) be `connections.incompleteCount`.

$$
f = \begin{cases}
N_{\mathrm{open}} / N_{\mathrm{conn}} & \text{if } N_{\mathrm{conn}} > 0 \\
0 & \text{otherwise}
\end{cases}
\qquad
F = 0.5
$$

$$
o_1 = \min\bigl(1,\ \max(0,\ (f - F)/(1 - F))\bigr)
\qquad
o_2 = \min(1,\ f / F)
$$

| \(f\) (still open) | \(o_1\) | \(o_2\) | Score before DRC (defaults) | What that looks like |
| --- | --- | --- | --- | --- |
| 1.0 | 1 | 1 | 0 | Nothing routed yet. |
| 0.5 | 0 | 1 | \(\approx 333\) | About half the connections done. |
| 0.0 | 0 | 0 | 1000 | Every connection closed. |

The first half of the work (going from “everything open” to “half done”) is
cheaper than the last half. That matches the practical fact that the remaining
nets are usually the cramped, ugly ones.

| Symbol | Settings key | Default | Technical meaning | In plain language |
| --- | --- | --- | --- | --- |
| \(f\) | (derived) | — | Open connection fraction \(N_{\mathrm{open}}/N_{\mathrm{conn}}\). | What share of the job is still a ratsnest line. |
| \(F\) | `router.router_scoring.unrouted_free_fraction` | \(0.5\) | Split between the two unrouted weights. | The halfway mark where “easy” work ends and “hard” work is all that is left. |
| \(o_1\) | (derived) | — | How much of the **first** half of connections is still open. Zero once \(f \le F\). | “Have we even finished the easy half yet?” |
| \(o_2\) | (derived) | — | How much of the **last** half of connections is still open. One until \(f \le F\). | “How much of the stubborn remainder is still open?” |
| \(W_1\) | `unrouted_first_half_weight` | \(1000/3 \approx 333.3\) | Penalty weight for \(o_1\). | Points you lose while the easy half is undone. |
| \(W_2\) | `unrouted_second_half_weight` | \(2000/3 \approx 666.7\) | Penalty weight for \(o_2\). Twice \(W_1\), and \(W_1+W_2=1000\). | Points you lose while the hard half is undone — finishing matters more than starting. |
| \(N_{\mathrm{conn}}\) | `connections.maximum_count` | — | Maximum connectable edges. | How many wires the board is supposed to have. |
| \(N_{\mathrm{open}}\) | `connections.incomplete_count` | — | Edges that are not yet electrically complete. | How many ratsnest lines are still on the screen. |

`unrouted_connection_weight` is a V1 leftover and is **unused** by V2.

### Clearance (DRC)

Violations come from `DesignRulesChecker.getAllClearanceViolations()` (full pair
iteration), not from the outline-only shortcut used in some older statistics.

For each violating pair, the shortfall in micrometres is

$$
L_{\mathrm{um}} = \max(0,\ \text{expected clearance} - \text{actual clearance})
\times \text{board-unit-to-µm factor}
$$

| Symbol | Settings key | Default | Technical meaning | In plain language |
| --- | --- | --- | --- | --- |
| \(N_{\mathrm{viol}}\) | `clearance_violations.total_count` | — | Count of clearance-violation pairs. | How many “these two coppers are too close” flags you have. |
| \(\sum L_{\mathrm{um}}\) | `clearance_violations.total_violation_um` | — | Sum of positive clearance shortfalls, in µm. | How deep those overlaps are, stacked together — a 50 µm nibble plus a 200 µm crash is worse than two 1 µm nicks. |
| \(W_C\) | `clearance_violation_count_weight` | \(25\) | Points lost per violation, after dividing by \(D\). | A tax on **how many** clearance fights you have. |
| \(W_D\) | `clearance_violation_depth_weight` | \(300\) | Points lost per scaled micrometre of stacked shortfall, after dividing by \(D\). | A tax on **how bad** those fights are. |
| \(U_{\mathrm{scale}}\) | `clearance_violation_depth_scale` | \(1000\) µm | Converts stacked µm into a dimensionless depth of “one millimetre of shortfall.” | One millimetre of piled-up overlap is a full “depth unit.” |

There is no extra cap. Typical incomplete or slightly dirty boards stay above 0
because unrouted and DRC terms are scaled; the outer \(\max(0,\cdot)\) is only a
numeric floor.

---

## Optimizer score (`V2_LOWER_BOUND`)

The optimizer’s report card: start at 1000, subtract **excess** copper, vias, and
bends versus placement-derived lower bounds.

$$
\mathrm{score}_{\mathrm{opt}}
=
\max\bigl(0,\ 1000 - \Delta L - \Delta V - \Delta B\bigr)
$$

$$
\Delta L = W_L \times \max\Bigl(0,\ \frac{L_{\mathrm{actual}} - L_{\min}}{\max(L_{\min},\, L_{\mathrm{floor}})}\Bigr)
$$

$$
\Delta V = W_V \times \frac{\max(0,\ V_{\mathrm{actual}} - V_{\min})}{\max(D,\, D_{\mathrm{floor}})}
$$

$$
\Delta B = W_B \times \frac{\max(0,\ B_{\mathrm{actual}} - B_{\min})}{\max(D,\, D_{\mathrm{floor}})}
$$

A legal production board is **not** required to score 1000. Obstacles, fanout,
and power-plane vias can all sit above the bound. The synthetic fixture
`SyntheticPerfectTwoPin` (one net, two axis-aligned pins, no obstacles) is the
board that is supposed to score 1000 on **both** V2 scores.

### Lower bounds (the “perfect” floor)

Computed from placement + netlist when the board is loaded, then stored on the
result (`bounds.min_trace_length_mm`, `min_via_count`, `min_bend_count`) so scores
can be replayed later without rerouting.

| Symbol | Field | Technical meaning | In plain language |
| --- | --- | --- | --- |
| \(L_{\min}\) | `bounds.min_trace_length_mm` | Per-net Manhattan (\(L_1\)) minimum spanning tree of terminal XY coordinates, summed, in millimetres. Obstacles can make it optimistic. A Steiner tree can beat MST, so excess is clamped at 0. | The shortest “connect the dots with city-block streets” length we can prove from pin positions. Real traces will usually be longer because parts are in the way. |
| \(V_{\min}\) | `bounds.min_via_count` | Minimum via *items* per net from the set of layers the terminals occupy and the via spans the board allows. A through-via that covers every occupied layer counts as **one** item. A copper pour is just another terminal on its layer — no plane special case. | The fewest layer-change nails you must hammer in if nothing crowded the board. Mixed-layer nets will not reach 1000. |
| \(B_{\min}\) | `bounds.min_bend_count` | Using the same MST edges: 0 if the two terminals share \(x\) or \(y\), else 1 (one Manhattan corner). A straight 45° segment can beat this, so excess is clamped at 0. | The fewest corners a tidy Manhattan path would need. |
| \(L_{\mathrm{actual}}\) | `traces.total_length_mm` | Total routed trace length, including jogs, neckdowns, and fanout stubs. | How much copper you actually poured. |
| \(V_{\mathrm{actual}}\) | `vias.total_count` | Via item count on the board. | How many drill hits you actually placed. |
| \(B_{\mathrm{actual}}\) | `bends.total_count` | Polyline internal corners. **45° and 90° count equally.** | How many times a trace changes direction. A chamfer still counts as a bend. |

### Weights

| Symbol | Settings key | Default | Technical meaning | In plain language |
| --- | --- | --- | --- | --- |
| \(W_L\) | `excess_wire_length_weight` | \(1000\) | Points lost when actual length is 100% over \(L_{\min}\). | If the wires are twice as long as the bound, you lose the whole 1000 from length alone. |
| \(W_V\) | `excess_via_weight` | \(2000\) | Points lost per extra via after dividing by \(D\). | Extra vias are expensive: they cost board space and reliability, so they hurt more than extra millimetres. |
| \(W_B\) | `excess_bend_weight` | \(500\) | Points lost per extra bend after dividing by \(D\). | Extra corners are a smaller tax — tidy, but not as important as vias or length. |
| \(L_{\mathrm{floor}}\) | `length_floor` | \(1\) mm | Denominator floor so a near-zero \(L_{\min}\) cannot explode \(\Delta L\). | Don’t divide by a microscopically short “perfect” length. |
| \(D_{\mathrm{floor}}\) | `difficulty_scale_floor` | \(1\) | Denominator floor for via/bend terms. | Don’t divide by zero on an empty board. |

### Keep / undo gates (not score terms)

The optimizer **refuses** a candidate before looking at \(\mathrm{score}_{\mathrm{opt}}\)
if it made the board less complete or dirtier:

1. More incomplete connections than the incumbent → reject (`CONNECTIVITY_REGRESSION`).
2. Else more clearance-violation **count** than the incumbent → reject (`DRC_COUNT_REGRESSION`).
   Violation **depth** is ignored here.
3. Else if optimizer score is **strictly higher** → keep the candidate.
4. Else keep the incumbent (ties included). A more-complete but uglier board is
   **not** an automatic win.

### When the optimizer stops

`optimizer.improvement_threshold` (default **0.01**) is a **relative** gain:

$$
\frac{\mathrm{score}_{\mathrm{after}} - \mathrm{score}_{\mathrm{before}}}{\mathrm{score}_{\mathrm{before}}}
$$

Stop when that ratio is below the threshold. There is no “already close to 1000”
stop. On a typical finished board (~800–1000), 1% is about 8–10 points.

---

## Reading a pair of scores

| Router | Optimizer | Typical situation |
| --- | --- | --- |
| Low, optimizer high | Many nets still open; the copper that exists is already short. | Autorouter still working. |
| ~1000, optimizer low | Fully connected and clean, but long, via-heavy, or bendy. | Optimizer’s job. |
| Both ~1000 | Complete, clean, and close to the geometric floor. | Rare on real boards; expected on `SyntheticPerfectTwoPin`. |
| Router high, DRC terms biting | Connected, but coppers are too close. | Do not “optimize length” until DRC count is down. |

`getNormalizedScore()` is a deprecated alias of the **router** score.
