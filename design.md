# Robot Rampage — Design Document

This is a living document describing what the game is, the architecture, and
current/open work. It describes the game **as designed or implemented right
now** — build narrative, bug-hunt stories and verification logs belong in git
history/commit messages, not here. Update it the same session a decision is
made. Section numbers are stable cross-reference anchors (including from
`CLAUDE.md`) — don't renumber when editing.

**Status legend.** Everything in this document is *design* unless a section
says "implemented". A detail marked **(unconfirmed)** is a concrete default
proposed to keep the design moving; it has not been signed off and may change.
Details marked **(verify)** are rules recalled from the board game that must be
checked against the actual rulebook before they are implemented.

## 1. Concept

An online multiplayer adaptation of the board game **RoboRally**. Each player
commands a factory robot racing across a hazard-filled board to touch a series
of numbered flags in order. Every turn, all players *simultaneously* program
five moves from a random hand of cards; then all robots execute their programs
register by register, and the board itself (conveyor belts, gears, lasers, pits)
fights back. Plans go wrong in entertaining ways — that is the game.

- **Rule base:** the classic **2005 (Avalon Hill) rules**, decided at project
  start. Damage reduces cards dealt and locks registers; one shared 84-card
  deck with priority numbers; power-down; archive-marker respawn. The 2016
  edition (SPAM cards, energy, upgrades, reboot tokens) is *not* the base. Any
  later borrowing of a 2016 idea is a separate, explicit decision.
- **Players:** 2–8 per game.
- **Genre:** simultaneous-programming, turn-based programming/puzzle race.
  Not real-time: there is no physics, no reflex element, and no frame-rate
  dependent gameplay.
- **Win condition:** first robot to touch the final flag, having touched all
  earlier flags in order. Robots have three lives; a player who loses all of
  them is eliminated.
- **Session shape:** a dedicated server on a public host; players download a
  client and connect directly. No matchmaking. (How games/lobbies are grouped on
  one server is still open, see 7.)
- **Scope for v1:** one fixed, original board (2.11), classic rules. The board
  format is designed from the start so that *more boards* and *procedurally
  generated boards* can be added without touching the rules engine (2.11, 3.6).
- **Players are international** — non-US/UK keyboard layouts are a real
  consideration; the UI should be mouse-first so this matters little (see 5.2).
- **Fan project note:** RoboRally is a board game by Richard Garfield, published
  by Wizards of the Coast / Avalon Hill and now Renegade Game Studios. This is a
  non-commercial personal project. It deliberately uses its own name, its own
  art and its own original board layouts; no published board layouts, card art or
  robot artwork are copied.

## 2. Core gameplay mechanics

The rules engine implementing this section lives in `core` under
`de.mkoehler.robotrampage.rules` and is pure Java (no libGDX imports), so it is
fully unit-testable without a window or natives (3.3, 3.8).

### 2.1 Board, coordinates and directions

- The board is a rectangular grid of squares. **Coordinates:** `x` grows east,
  `y` grows north, origin at the south-west corner — matches
  libGDX's y-up world coordinates, so board coordinates map to world coordinates
  without a flip.
- **Directions:** `NORTH`, `EAST`, `SOUTH`, `WEST`. A robot always faces one of
  them. Rotating right by 90° is `N→E→S→W→N`.
- **Walls** live on the *edges* between squares (or on the outer edge of the
  board), never on squares. A wall blocks robot movement and blocks lasers. An
  edge shared by two squares is stored once in the board format (3.6) so the two
  sides can never disagree.
- An outer edge without a wall is open space: a robot that leaves the board there
  is destroyed (2.9).
- At most one robot occupies a square at any time (2.9 covers the one case where
  a returning robot finds its square taken).

### 2.2 Robots

Per-robot state (all of it lives in `GameState`, 3.4):

| Field | Meaning |
|---|---|
| position, facing | Where the robot is and which way it points. |
| damage (0–9) | Damage tokens. The 10th point of damage destroys the robot. |
| lives (3→0) | Life tokens. 0 lives left = eliminated. |
| flags touched | Highest flag number touched so far (flags must be touched in order). |
| archive marker | Square the robot returns to when destroyed. Starts on the robot's start square. |
| registers[5] | The five programmed cards, some possibly *locked* (2.5). |
| powered down | Whether the robot is shut down this turn (2.8). |
| status | `ACTIVE`, `DESTROYED` (waiting to re-enter next turn) or `ELIMINATED`. |

### 2.3 Turn structure

A game is a sequence of turns. Each turn has these phases, driven by the server:

1. **Deal.** The server shuffles/deals from the shared deck: each active,
   powered-up robot receives `9 − damage` cards (2.5, 2.7).
2. **Program.** Every player *simultaneously and secretly* places 5 of their
   dealt cards into their registers (locked registers keep their old card, so a
   player only fills the unlocked ones), and may announce a **power-down** for the
   *next* turn (2.8). Ends when everyone has confirmed or the timer expires (2.13).
3. **Execute.** Registers 1–5 resolve in order (2.4). All randomness is over by
   now; execution is fully deterministic.
4. **Cleanup.** Repair sites heal, powered-down robots repair, destroyed robots
   are queued to return, cards are discarded, and the deck is refilled from the
   discard pile if needed. (A win is *not* checked here: the game ends the moment
   a robot touches the final flag, 2.10.)

Robots that are destroyed *during* a turn do not act again that turn; they
re-enter at the start of the next turn (2.9).

### 2.4 Register execution order

For each register `r = 1..5`, the following sub-phases resolve strictly in this
order. Each atomic change emits a `GameEvent` (3.5) so clients can animate it.

1. **Reveal.** Every robot's card for register `r` is revealed.
2. **Robot movement.** Cards are executed one robot at a time in **descending
   priority** (2.6). Each robot's card, including any pushes it causes, is fully
   resolved before the next robot's card is executed.
3. **Express belts.** Robots on *express* belts move one square (2.12).
4. **All belts.** Robots on *express or normal* belts move one square (2.12).
   Net effect: express belts move a robot two squares per register, normal belts
   one.
5. **Pushers.** Pushers active in register `r` push the robot standing on their
   square one square away from the wall they are mounted on (push rules: 2.6).
6. **Gears.** Robots on gears rotate 90° in the gear's direction.
7. **Lasers.** Board lasers and robot lasers all fire *simultaneously*; damage
   is applied to every hit robot, then destruction is checked (so two robots can
   destroy each other in the same volley) (2.9).
8. **Crushers.** Crushers active in register `r` destroy any robot on their
   square. 
9. **Checkpoints.** A robot that ends the register on the next flag in its
   sequence touches it, and its archive marker moves there (2.10).

Powered-down and destroyed robots have no card to reveal, but a powered-down
robot still occupies its square, is still moved by belts/pushers/gears, can be
hit by lasers and pushed by other robots.

### 2.5 Programming cards, damage and locked registers

- **Deck:** 84 cards shared by all players: **Move 1 ×18, Move 2 ×12, Move 3 ×6,
  Back Up ×6, Rotate Left ×18, Rotate Right ×18, U-Turn ×6**. Each card has a
  unique **priority** number.
- **Priority table:** priorities are `10, 20, … 840`, assigned in
  ascending order by card type: U-Turn (10–60), Rotate Left/Right alternating
  (70–420), Back Up (430–480), Move 1 (490–660), Move 2 (670–780), Move 3
  (790–840). What matters behaviourally is that all 84 priorities are
  unique (no ties, ever) and that the type ordering above holds.
- **Hand size:** `9 − damage` cards. 9 damage means 0 cards dealt.
- **Locked registers:** from 5 damage upward, registers lock starting at register
  5 and moving backwards: locked registers `= max(0, damage − 4)`, so 5 damage
  locks register 5, 7 damage locks registers 3–5, 9 damage locks all five. A
  locked register keeps its previous card, which stays in the register (it is
  *not* discarded or reshuffled) and executes again every turn. The locked set is
  always "register 5 down to register `10 − damage`", so repairing one point of
  damage unlocks the lowest-numbered locked register (e.g. going from 7 to 6
  damage frees register 3, leaving 4 and 5 locked).
- **Discard/reshuffle:** at cleanup every non-locked card returns to the discard
  pile. When the deck cannot cover the next deal, the discard pile is reshuffled
  into a new deck; cards sitting in locked registers are excluded from it.

### 2.6 Movement rules

Cards execute one atomic step at a time:

- **Move N** — up to N single-square steps forward, each step resolved fully
  before the next.
- **Back Up** — one step backward; the robot keeps its facing.
- **Rotate Left/Right** — 90° in place. **U-Turn** — 180° in place.

A single step in direction `d` from square `s`:

1. If a wall on the edge between `s` and its neighbour blocks `d`, the step fails
   (the rest of a Move N is lost too — the robot stays put and just wastes the
   remaining steps).
2. If the target square holds another robot, that robot is **pushed** one square
   in `d`, recursively: if it in turn is blocked by a wall, *nothing in the chain
   moves*. A push that shoves a robot onto a pit or off the board destroys that
   robot. Pushing costs no damage.
3. If the target square is a pit, or off the board, the robot is destroyed
   immediately (2.9) and any remaining steps of its card are lost.

Robots pushed by another robot do not change facing. Back Up pushes robots
standing behind it, exactly like a forward move.

### 2.7 Deck and deal

The deck is server-side state. The server owns the seed and the shuffle: a
client only ever learns *its own* hand, and other players' registers only at
reveal time (3.5). All randomness in the game (shuffles, the random fill on
timeout in 2.13) uses one seeded `java.util.Random` owned by the server session,
so a game is exactly reproducible from its seed plus the sequence of submitted
programs (useful for bug reports and tests).

### 2.8 Power down

A player may announce a power-down during the programming phase of turn `T`.
The robot still executes turn
`T`'s program normally; at the end of turn `T`'s cleanup it powers down, is
**fully repaired** (damage 0, all registers unlock), and stays shut down for turn
`T+1`: it is dealt no cards, executes no registers, fires no lasers, but is still 
moved by belts/pushers/gears and still hit by
lasers. At the start of turn `T+2`'s programming it is powered up again unless
its player announces a new power-down. Announcements are public (other players
see the power-down marker), the programmed cards are not.

### 2.9 Board elements, damage and destruction

**Square features:**

| Feature | Behaviour |
|---|---|
| Pit | Destroys any robot that enters or is placed on it. |
| Normal belt | Moves the robot one square in its direction each register (2.12). |
| Express belt | Moves the robot in its direction in *both* belt sub-phases (2.12). |
| Gear (cw/ccw) | Rotates the robot 90° in that direction each register. |
| Repair site | At cleanup, a robot ending the turn here removes 1 damage and sets its archive marker here (2.10). |
| Crusher | Destroys any robot on it in the registers it is active. Can sit on top of a belt. |
| Flag | See 2.10. |

**Edge features:** walls; **pushers** (mounted on a wall, active in a listed
subset of registers, push the robot standing on that square away from the wall);
**board lasers** (mounted on a wall, fire across the board with 1–3 beams, i.e.
1–3 damage per hit).

**Lasers:** a laser (board or robot) travels in a straight line until it hits a
wall or the first robot in its path — robots block lasers, robots are not pushed by lasers.
A robot hit takes 1
damage per beam. Robot lasers fire forward from every active robot; powered-down 
robots fire no lasers (see 2.8). A robot never hits itself. All lasers in a
volley fire simultaneously against the positions at the start of the volley
(2.4, step 7).

**Destruction** happens when a robot enters a pit or leaves the board (immediately),
is on an active crusher, or reaches 10 damage. A destroyed robot:

- is removed from the board at once, does not act again this turn, and has all
  its registers cleared: every programmed card is discarded, *including cards in
  locked registers* (2.5), since the robot returns with no damage and therefore no
  locked registers;
- loses one life. At 0 lives it is `ELIMINATED` and takes no further part;
- otherwise re-enters at the **start of the next turn** on its archive marker, in
  any facing of its player's choice, with **no damage**.

**Respawn conflicts:** if the archive square is occupied
when a robot re-enters, the returning robot is placed on the nearest free square
(breadth-first distance, ties broken by destruction order, then a fixed
direction order); if several robots return to the same archive square, they are
placed in the order they were destroyed, first-destroyed getting the archive
square itself.

### 2.10 Flags, archive marker and victory

- Flags are numbered `1..N` and must be touched **in order**. A robot touches
  flag `k` only if it has already touched `k−1` and ends a register (sub-phase 9)
  standing on it. Passing *through* a flag square mid-register does not count.
- Touching a flag moves the robot's archive marker to that square. A repair site
  also moves it, at cleanup.
- The game ends immediately at the end of the register in which a robot touches
  flag `N` (the rest of the turn is not played). If several robots touch it in that
  same register, the one earlier in the register's descending-priority order wins.
- If everyone but one player is eliminated or disconnected the remaining player
  wins by default.

### 2.11 The first board

v1 ships with one fixed board: an original 12×12 layout
(one classic board section) with 3 flags and up to 8 start squares, using the
v1 feature set (walls, pits, normal/express belts, gears, board lasers, repair
sites). Pushers, crushers and the power-down mechanic are part of the rules and
the board format from day one but are implemented in a second wave (6). The
actual layout is authored as its own step, as a JSON file in `assets/boards/`
(3.6), and is *not* a copy of any published board.

### 2.12 Conveyor-belt resolution algorithm

Belts are where digital RoboRally implementations go wrong, so this is specified
precisely. A *belt pass* is run once with only express belts active (sub-phase 3)
and once with all belts active (sub-phase 4). In a pass, every robot standing on
an *active* belt square gets an **intent**: to move one square in the belt's
direction. Belts never push robots.

```
beltPass(activeBelts):
    intent[r] = r.pos + dir(belt at r.pos)      for each robot r on an active belt
    repeat until nothing changes:
        for each robot r with an intent:
            block r if ANY of:
              - a wall is on the edge r.pos -> intent[r]
              - another robot s (with an intent) has intent[s] == intent[r]      (collision)
              - the robot at intent[r] is s, and intent[s] == r.pos               (swap)
              - a robot t stands on intent[r] and t has no intent                 (t is not moving:
                                                                                   off-belt, or blocked)
            a blocked robot loses its intent (it now counts as "not moving")
    apply every remaining intent simultaneously
    for each robot that moved onto a belt square: rotate it (below)
    destroy any robot now on a pit or off the board
```

Details this pins down:

- **Trains work.** A robot may move into a square a *moving* robot is vacating in
  the same pass; the repeat-until-stable loop handles it, including a loop of
  belts where every robot moves (a rotating ring).
- **Collisions and swaps block everyone involved.** Two robots aimed at the same
  square, or two robots trying to swap, all stay put. (A blocked robot then blocks
  robots feeding into its square on the next loop iteration.)
- **Rotation.** A robot that *entered* a belt square through a belt move rotates
  by the signed turn from its movement direction to that belt square's direction,
  if that turn is exactly ±90°; otherwise it does not rotate. This one rule
  covers curved belts and belts merging from the side without any per-tile curve
  data — the format only stores each belt's direction (3.6); the renderer derives
  the curved art from which neighbours feed the square. A robot that *starts* the
  pass on a curved square, or that walked onto a belt via a card, does not rotate.
- **Destruction is deferred to the end of the pass**, so robots blocked or moved
  in the same pass are resolved consistently first.

### 2.13 Programming timer and disconnects

The programming phase must never let one absent or slow player block everyone
(this is the most common complaint about existing digital versions). 

- **Hard cap:** each programming phase has a maximum length (default 90 s),
  starting at the deal.
- **Last-player pressure (classic rule):** as soon as *all but one* active player
  have confirmed, the remaining player has at most 30 s.
- **On expiry**, registers the player has not filled are filled *randomly* from
  their remaining dealt cards (as in the classic rules). Already-placed cards
  stay.
- **Confirmed = final.** A confirmed program cannot be edited; the server
  broadcasts only *that* a player has confirmed, never the cards.
- **Disconnect during programming:** treated like an immediately expired timer
  (random fill). The player gets a **reconnect grace period** (default 10 minutes)
  during which their robot keeps executing random programs; after that the robot
  is removed from the board and the player is marked as having left. Reconnecting
  within the grace period resumes control (needs a session token, see 7).
- **Disconnect during execution** has no gameplay effect: the server resolves the
  turn without any client involvement.
- A game with fewer than two connected/remaining players ends per 2.10.

## 3. Architecture

### 3.1 High-level shape

Client/server, **authoritative dedicated server**. The server owns everything
that matters: the deck and its shuffle, every player's hidden hand and
registers, and the resolution of each turn. Clients render, collect the player's
programming choices and animate what the server tells them. This is what makes
the hidden information trustworthy: a modified client cannot see other players'
hands, cannot reorder cards it wasn't dealt, and cannot claim a different
outcome.

**Deliberate divergence from the StarWars project.** StarWars is a real-time
game: UDP world snapshots, client-side prediction and reconciliation, dead
reckoning, a tick rate. **None of that applies here.** Robot Rampage is
turn-based, so the protocol is *command/event driven over reliable TCP only* (no
UDP, no prediction, no reconciliation, no tick rate) (3.5). Most of the StarWars
netcode gotchas in its `CLAUDE.md` do not carry over.

### 3.2 Tech stack

- **libGDX** (1.14.x) — client rendering, input, audio, Scene2D. Headless backend
  for the server.
- **Maven**, multi-module (3.3). Java 25.
- **KryoNet** (maintained `crykn` fork via JitPack) — networking, TCP only.
- **Jackson** (`jackson-databind`) — JSON: boards, config files, saved games.
- **VisUI** — Scene2D widgets for the menu-style screens (4.2).
- **JUnit 5** — tests, used pragmatically (3.8).
- **Not used, on purpose:** Ashley (ECS), Box2D, gdxAI, an MCP server. See 3.7.

### 3.3 Modules and packages

- `core` — everything shared. Package layout under `de.mkoehler.robotrampage`:
  - `rules` — **the rules engine.** Pure Java, no libGDX imports: cards, deck,
    `GameState`, `Robot`, the turn resolver, belt/laser/push logic, `GameEvent`s.
  - `board` — geometry (`Direction`, `Position`), the immutable runtime `Board` the
    rules engine queries (with its `Board.Builder`), and the board *format*
    (`BoardDefinition`, JSON loading, validation, still to come). Also pure Java;
    `board` never depends on `rules`.
  - `net` — `MessageRegistry`, wire messages, `NetworkServer`/`NetworkClient`
    wrappers, `AppVersion`. No libGDX.
  - `client` (and sub-packages) — screens, board renderer, animation, UI. Uses
    libGDX.
- `lwjgl3` — desktop client launcher (`Lwjgl3Launcher`, `StartupHelper`),
  packaging, natives.
- `server` — dedicated server: `ServerLauncher` + `GameServer` on the libGDX
  headless backend, game sessions, autosave.
- No `dev-tools` module until a concrete need appears (a board editor is the
  likely first one).

**Rule:** `rules` and `board` never import from `client`, `net` or libGDX.
Dependencies point inward: `client`/`server` → `net` → `rules` → `board`. This keeps
the engine testable and lets a bot, a replay tool or a board generator use it
without a window. The rule is **enforced by `ArchitectureTest`** (ArchUnit), which
fails the build on a violation.

### 3.4 Rules engine design

- **State:** `GameState` holds the board reference, the robots and the `Deck` (draw
  and discard pile). Robots are mutable plain classes identified by a stable integer
  `id`; `GameState.copy()` deep-copies robots and deck and shares the immutable
  `Board`. **There is no live `Random` in the state:** the deck derives every shuffle
  from a `seed` plus a shuffle counter, so a deck is fully described by three plain
  values plus its pile contents and can be persisted and resumed exactly (3.10).
- **The core function:** `TurnResolver.resolve(state, programs) → TurnResult`,
  where `programs` is the five cards per robot and `TurnResult` is the new state
  plus the **ordered list of `GameEvent`s** that led from the old state to the
  new one. The resolver contains *no randomness* — given the same inputs it always
  produces the same result. (Shuffling and timeout fills happen outside it, in the
  server session.)
- **Events:** a `sealed interface GameEvent` with a nested record per atomic change.
  Implemented so far: `RobotMoved(robotId, from, to, MoveCause)`,
  `RobotRotated(robotId, from, to, RotationCause)` and
  `RobotDestroyed(robotId, DestructionCause)`; more (`RegisterRevealed`,
  `RobotDamaged`, `LaserFired`, `RobotRespawned`, `FlagTouched`, `RobotRepaired`, …)
  are added as the rules are implemented. Events carry everything a client needs to
  animate them (from, to, cause) so the client never has to re-derive rules.
  Two properties are fixed: **events refer to robots by stable id** (robots are
  destroyed and re-enter, so an index would silently break the animation queue), and
  **every event is wrapped in a `LoggedEvent(register, subPhase, event)`** that records
  which register (1–5, or 0 for cleanup) and which `SubPhase` (2.4) it happened in, so
  clients can pace playback at phase boundaries and tests can assert on them. The
  turn's `EventLog` stamps the wrapper. **(unconfirmed)** the exact event set beyond
  the ones above; it grows as the rules are implemented.
- **Events are also the test oracle:** unit tests assert on the event list (and
  the final state), not on internals.

### 3.5 Networking and protocol

KryoNet over **TCP only**. `MessageRegistry` is the single place both ends
register wire classes, in a fixed append-only order (`MessageRegistryTest`
checks two independent `Kryo` instances agree on every id and that every message
round-trips). Implemented so far: `HandshakeRequest`/`HandshakeResponse` with the
build-version check (`AppVersion`, filtered from the Maven version).

Turn protocol **(unconfirmed, to be refined when implemented)**:

| Direction | Message | Purpose |
|---|---|---|
| S→C | `GameStarted` | Board (as JSON text, 3.6), players, seat/robot assignment, seed *not* included. |
| S→C (per player) | `HandDealt` | That player's cards only. |
| C→S | `SubmitProgram` | 5 card ids (locked registers excluded) + power-down intent. |
| S→all | `PlayerConfirmed` | *That* a player locked in — never the cards. |
| S→all | `TurnResolved` | Full reveal + the ordered `GameEvent` list for the turn. |
| S→all | `StateSnapshot` | Authoritative state after the turn (hands excluded), for resync/rejoin. |
| S→all | `GameOver` | Winner, final standings. |

**Clients replay the event list; they do not re-simulate.** (The alternative —
send programs and let every client run the rules — was rejected: a rules bug or a
version skew would silently desync clients.) The engine still lives in `core`, so
the client can *also* use it locally for a "preview my program" ghost path in the
programming UI, which is a pure convenience and never authoritative.

Buffer sizes in `NetworkConstants` are placeholders (a resolved turn with 8
robots is the largest message and must be measured once implemented).

### 3.6 Board format

Requirement from the project owner: start with one fixed board, but keep the
format open for **multiple authored boards** and **dynamically generated
boards**. Design decisions:

- **One flat grid at runtime.** The rules engine only ever sees a single
  rectangular grid (`Board`). Anything that composes boards (docking several 12×12
  sections into a course, or a procedural generator) is a *pre-processing step*
  that produces one flat `BoardDefinition`. New board sources therefore never
  touch the rules engine.
- **Data, not code.** A `BoardDefinition` is plain data serialised as JSON via
  Jackson, with a `formatVersion` so the format can evolve.
- **Sparse, edge-based.** Squares default to plain floor; only interesting squares
  are listed. Walls and other edge features are stored once per edge: the loader
  canonicalises an edge given from either neighbouring square to one representation,
  so the two sides of a wall can never disagree.
- **The server ships the board over the wire** in `GameStarted` (as JSON text), so
  a client never needs a copy of the board file. This is what makes generated
  boards possible: the server can generate on the fly and clients just render
  what they are told.
- **Validation is a first-class step** (`BoardValidator`): every flag and start
  square is in bounds and not on a pit; flag numbers are `1..N` and contiguous; no
  square has two belts or two features. A belt leading off the board or into a pit
  is a legal death trap, so it is only *reported* as a warning, not rejected. For
  generated boards there is an extra reachability check: every flag must be
  reachable from every start square over a path that avoids pits and walls. A
  generator's output must pass the same validator as a hand-made board.

Sketch of the JSON shape (field names are a starting proposal):

```json
{
  "formatVersion": 1,
  "id": "proving-grounds",
  "name": "Proving Grounds",
  "author": "…",
  "generator": null,
  "seed": null,
  "width": 12,
  "height": 12,
  "squares": [
    { "x": 3, "y": 4, "belt": { "dir": "EAST", "express": false } },
    { "x": 5, "y": 5, "feature": "PIT" },
    { "x": 6, "y": 2, "feature": "GEAR_CW" },
    { "x": 7, "y": 7, "feature": "REPAIR" }
  ],
  "edges": [
    { "x": 4, "y": 4, "side": "NORTH", "wall": true },
    { "x": 0, "y": 6, "side": "WEST", "laser": { "beams": 1 } },
    { "x": 2, "y": 9, "side": "SOUTH", "pusher": { "registers": [2, 4] } }
  ],
  "flags": [ { "x": 2, "y": 3 }, { "x": 9, "y": 8 }, { "x": 4, "y": 10 } ],
  "startSquares": [ { "x": 1, "y": 0, "facing": "NORTH" } ]
}
```

- A square has at most one *belt* and at most one *feature* (`PIT`, `GEAR_CW`,
  `GEAR_CCW`, `REPAIR`, `CRUSHER` with a `registers` list). A crusher may sit on a
  belt.
- Generated boards carry `generator` (id) and `seed` in their metadata so a board
  can be reproduced exactly.
- Belt curve/merge artwork is derived by the renderer from which neighbours feed a
  square (2.12); no per-tile curve data is stored.

### 3.7 Why no Ashley and no Box2D

The StarWars project used Ashley (ECS) and Box2D because it simulates continuous
physics with hundreds of short-lived entities. Robot Rampage has neither: the
whole game is "up to 8 robots on an integer grid, resolved one discrete step at a
time". An ECS's `Family`/`IteratingSystem` machinery buys nothing here and would
scatter the rules across many small systems, whereas a single deterministic
`resolve(state, programs)` function over plain data is simpler to read, to test
and to reason about (2.4 is literally its control flow). Dropping Box2D also
removes its native library from both client and server, the fixed-timestep
accumulator and interpolation machinery, and a whole family of physics gotchas.
**Revisit only if** a genuinely entity-heavy, real-time part shows up — and even
then only on the client (e.g. a particle/effect layer), never in the rules.

### 3.8 Testing strategy

Pragmatic, mirroring the StarWars project: tests where they buy real safety,
none for rendering/UI. Focus, roughly in priority order:

1. **Rules engine (`rules`)** — the bulk of the test effort: card priority
   ordering; movement, wall blocking, push chains including *push into a wall*
   (nobody moves) and *push into a pit*; belt resolution (collisions, swaps,
   trains, rotation on curves/merges, express double move); gears; laser line of
   sight incl. wall and robot blocking; damage → hand size and register locking;
   checkpoint ordering; archive/respawn including several robots returning to the
   same square; power-down; deck deal/discard/reshuffle with locked cards excluded.
2. **Board format (`board`)** — JSON round trip, validator rules, wall-edge
   consistency.
3. **Network layer (`net`)** — `MessageRegistry` id stability and round trips
   (already in place), and a localhost integration test of client↔server message
   flow once those classes exist.
4. **Server session logic** — the programming-phase state machine: confirmation,
   timer expiry random-fill, disconnect handling.

**Test helper (planned):** a tiny ASCII-art board parser for tests, so a scenario
reads like the board it describes instead of a wall of `set(x, y, …)` calls.

Not tested: screens, rendering, animation, VisUI wiring.

### 3.9 Build, versioning and release

Maven multi-module, same setup as the StarWars project (compiler plugin pinned
for Java 25, surefire pinned for JUnit 5, `lwjgl-bom` import pinning LWJGL to
3.4.x, shade plugin per runnable module, `exec-maven-plugin` for local runs).
Versions are computed by **jgitver** (`.mvn/extensions.xml`) from git tags and
history, exactly as in the StarWars project: every `pom.xml` carries the placeholder
version `0`, and a build gets the real one — a release tag `vX.Y.Z` builds as
`X.Y.Z`, every commit after it as `X.Y.(Z+1)-SNAPSHOT`, and before the first tag
`0.0.0-SNAPSHOT`. `.mvn/jgitver.config.xml` drops the branch qualifier for `main`
(jgitver only does that for `master` by default). The computed version is what
`AppVersion` reports and what the handshake compares. The tag-driven release
pipeline (client zip via jpackage, server Docker image) is deferred until there is
something to release — see StarWars `design.md` 3.10–3.12 for the pattern.

### 3.10 Persistence and config

All JSON via Jackson, plain bean-style classes for config files, following the
StarWars conventions:

- **Client:** `connection-config.json` (last server/name), later keybinds and
  audio/graphics settings. Runtime-generated, gitignored.
- **Server: autosave.** After every completed turn the server writes the full
  `GameState` (between turns there is no hidden hand data yet — the deck order and
  RNG state are all that is secret) to a JSON file, so a server restart can resume
  running games. Also the natural basis for replays.
- **Boards:** `assets/boards/*.json`, read by the server (3.6).
- **Accounts:** not designed (7).

## 4. Rendering & presentation

### 4.1 Board rendering and animation

A top-down, tile-based view drawn with `SpriteBatch`; the whole board fits the
window by default, with zoom/pan for large boards. Squares are drawn from a
texture atlas; belt curves are chosen by the renderer from neighbour analysis
(2.12). Robots are sprites facing one of four directions, tinted/numbered per
player.

**Animation is event-driven.** During the Execute phase the client receives the
turn's `GameEvent` list (3.4) and plays it through an *animation queue*: each
event maps to a short animation (slide, rotate, laser beam, explosion, flag
capture), played sequentially with a playback-speed setting and a "skip to end of
turn" button. Because the events are pre-computed by the server, the client has
no rules logic to get wrong. **(unconfirmed)** the animation timings.

### 4.2 UI framework

**VisUI** (Scene2D) for menu-style screens (connect, lobby, settings) and for the
programming UI's widgets; plain Scene2D/`SpriteBatch` for the board itself. Fonts
via libGDX `BitmapFont` initially; `gdx-freetype` is added when real TTF fonts are
needed (which brings a `gdx-freetype-platform` natives dependency in `lwjgl3`).

### 4.3 The programming UI

The most-used screen in the game, so it deserves care. Layout **(unconfirmed)**:
the board on top, the player's dealt hand along the bottom, five register slots
between them. Cards are placed by click or drag; locked registers are visibly
locked and pre-filled; a countdown timer (2.13) is always visible; a
**Confirm** button locks the program in; a **Power Down** toggle is available;
other players show only a "confirmed" tick. The "preview my program" ghost path
(3.5) is drawn on the board as the program is being built.

### 4.4 Audio

Later. `Sound` for short effects, `Music` for streamed tracks; same guidance as in
the StarWars project's `CLAUDE.md`.

### 4.5 Asset pipeline

Same two-folder setup as StarWars: `assets-raw/` holds source files as the owner
drops them (any filename, any format, PSDs welcome) and is committed as backup;
`assets/` holds what the game actually loads at runtime, with proper names and
atlases. Claude integrates: renames, converts, packs atlases, copies into
`assets/`. The game never loads from `assets-raw/`.

## 5. UX flow

### 5.1 Screen flow

`Startup` (placeholder implemented) → `Connect` (server address, display name) →
`Lobby` (the waiting room of the server's single game: see who joined, ready-up,
the host starts — the first player to connect is the host; game
options later belong in a "create game" step, see 7) → `Game` (alternating **Programming** and
**Resolution** views) → `Game Over` (standings) → back to `Lobby`. Details of the
lobby model are open (7).

### 5.2 Controls

Mouse-first: click/drag cards, click buttons. Keyboard shortcuts (1–9 to pick a
card, Enter to confirm) are a convenience layered on later, and — if they are
remappable — reuse the StarWars keybind-screen pattern. Since the primary input
is the mouse, keyboard-layout differences between players barely matter.

## 6. Components / TODOs

Proposed implementation order — engine first, because it needs
no UI and is where the test value is:

- **M0 — Project skeleton. Done.** Maven modules, launchers, version handshake
  classes, `MessageRegistry` + tests, `design.md`/`CLAUDE.md`, jgitver.
- **M1 — Rules engine, headless. In progress.** Heavily unit-tested with the ASCII
  board helper (`AsciiBoard`, test scope). *Done:* board model and `Board.Builder`,
  cards/deck (seeded, resumable shuffles), `Robot`/`GameState`, event log with
  register/sub-phase stamping, movement and pushing (2.6), destruction (2.9), belts
  (2.12), the `ArchitectureTest`, Kryo registration of the event types. *Still to do:*
  gears, pushers, lasers and damage (incl. locked registers when dealing), crushers,
  flags/archive/repair, respawn, power-down, the cleanup phase, and the `TurnResolver`
  that ties the sub-phases together in the order of 2.4.
- **M2 — Board format.** `BoardDefinition` + Jackson loading + `BoardValidator`;
  author the first original board.
- **M3 — Server session + protocol.** Session state machine (deal → program →
  execute), timer/timeout/disconnect handling (2.13), `NetworkServer`/`Client`,
  integration test on localhost.
- **M4 — Playable client.** Connect screen, board renderer, programming UI,
  animation queue. First real playtest.
- **M5 — Second wave of rules.** Pushers, crushers, power-down. (Multiple concurrent
  games per server / real lobbies come after v1, see 7.)
- **M6 — More boards & Board Editor.** Board selection, board composition, first procedural
  generator (3.6). Optional Board Editor, for authoring boards in a visual editor instead of JSON text.
  Could be a separate tool in a new dev-tools sub module.
- **M7 — Release pipeline.** jpackage client zip, Docker server image, tag-driven
  GitHub releases (3.9). jgitver is already in place.

## 7. Open design questions

Design questions:

- **Lobby model.** One game per server process, or several concurrent games in
  lobbies? Affects the session layer, not the rules. DECISION: one game per server process for v1, lobbies later.
- **Accounts/identity.** Display name only for now. Do we want persistent accounts
  (stats, rejoin authentication), or a lighter session-token scheme just for
  reconnecting (2.13)? DECISION: session token for reconnecting only, no persistent accounts for v1.
- **Spectators**, including eliminated players watching on. DECISION: eliminated players can spectate, but no spectators-only mode for v1.
- **Bots** to fill empty seats or replace disconnected players. The rules engine
  makes a simple bot easy (it can simulate); no gdxAI needed. DECISION: no bots for v1, but the engine is designed to support them later.
- **Fixed vs. random start squares and robot colours**; **game options** (number of
  flags, lives, timer lengths). DECISION: fixed start squares and colours for v1, options later (should be part of the 'create game' flow).
- **Chat.** In-game text chat is nearly free to add over the same TCP channel. DECISION: no chat for v1, but the protocol is designed to support it later.
- **Art direction** and audio — nothing decided. DECISION: the project owner will provide the necessary assets as they are needed for the next steps.
- **Game length / pacing.** Classic RoboRally can run long and eliminate players
  early; the option of a shorter default course or a spectate-and-rejoin mechanic
  should be evaluated after the first real playtests.
- **2016-edition ideas** worth borrowing later (e.g. reboot tokens instead of
  archive markers) — each is a separate decision.
