# Intent-Based Development: Package Intent Definitions

This document defines the core intent and purpose of each Java package in the `app.freerouting` codebase. In accordance with intent-based development (IBD) best practices, these definitions focus on the **why**—the specific domain, responsibility, and architectural role of each package—rather than just listing its contents or explaining how it works.

By clearly documenting the intent of each package, we aim to:
- Maintain high cohesion within packages.
- Prevent architectural drift and accidental coupling.
- Help developers quickly understand the domain boundaries of the application.

## Core Packages

### `app.freerouting.api`
**Intent:** Provide external interfaces and data transfer objects (DTOs) for interacting with the Freerouting engine programmatically or over a network. It encapsulates the system's boundary to shield internal routing logic from external communication concerns.

### `app.freerouting.autoroute`
**Intent:** House the automated routing intelligence and algorithms. Its responsibility is to find valid electrical paths between pins given a set of design rules and board constraints, operating independently of user interface or file I/O concerns.

### `app.freerouting.board`
**Intent:** Represent the physical domain of a Printed Circuit Board (PCB). It serves as the central data model for layers, nets, components, and the physical spaces they occupy, acting as the ground truth for the routing engine.

### `app.freerouting.boardgraphics`
**Intent:** Bridge the gap between the physical board model and its visual representation. Its sole purpose is to translate abstract board components into renderable graphical elements, separating visualization logic from the core data model.

### `app.freerouting.core`
**Intent:** Provide foundational services, events, and scoring mechanisms that orchestration the application's core workflow. It acts as the glue that coordinates changes across the board, rules, and routing engine.

### `app.freerouting.datastructures`
**Intent:** Supply highly optimized, specialized collections and spatial indexing mechanisms (e.g., shape trees) built specifically to handle the performance-critical demands of geometric intersection testing and pathfinding.

### `app.freerouting.designforms`
**Intent:** Abstract the parsing and serialization of various external PCB design formats. It isolates the complexity of third-party file formats from the internal board representation.
*   **`.specctra`**: Specifically handles the parsing and writing of the industry-standard SPECCTRA DSN (design) and SES (session) formats.

### `app.freerouting.drc`
**Intent:** Enforce Design Rule Checking (DRC). Its purpose is to continuously validate the board state against defined electrical and manufacturing constraints (like clearance and trace width) to ensure the generated design is physically viable.

### `app.freerouting.geometry`
**Intent:** Provide the mathematical and geometric foundation required for routing. It contains the primitives and operations (e.g., shapes, vectors, matrices) needed to calculate distances, intersections, and spatial relationships.
*   **`.planar`**: Focuses strictly on 2D Euclidean geometry operations, tailored for the planar nature of PCB layers.

### `app.freerouting.gui`
**Intent:** Deliver the Desktop user interface. It completely isolates all Swing/JavaFX components and user interaction flows from the analytical routing engine, adhering to the Model-View-Controller (MVC) paradigm.

### `app.freerouting.interactive`
**Intent:** Facilitate manual, user-driven routing tools (like shoving and dragging). It manages the complex state transitions and real-time validation required when a human operator is actively modifying the board layout.

### `app.freerouting.logger`
**Intent:** Centralize subsystem tracing, error recording, and application logging. It ensures a consistent, system-wide approach to auditability and diagnostics.

### `app.freerouting.management`
**Intent:** Oversee application lifecycle, cross-cutting concerns, and telemetry (analytics). It handles concerns that apply globally across the application but belong to no specific routing domain.

### `app.freerouting.rules`
**Intent:** Define and manage the constraints under which the routing engine and DRC must operate. It acts as the definitive source of truth for net classes, clearances, and routing boundaries, decoupling constraints from the entity models.

### `app.freerouting.settings`
**Intent:** Manage application configuration, user preferences, and environment-specific parameters. It isolates the persistence and retrieval of setup data to ensure the rest of the application remains stateless regarding environments.

---

> [!NOTE]
> **Implementation Note for Developers:**
> To keep the codebase aligned with these intents, ensure that `package-info.java` files in each of these directories are kept up-to-date with these definitions. Any new classes added strictly must align with the stated intent of their package.
