# CLAUDE.md — Robot Rampage project notes

Cross-session memory for this repo: gotchas, decisions and their reasons,
conventions — so future sessions don't have to rediscover them. Game
design/architecture (what the game *is*, how each system is meant to work)
belongs in `design.md`, not here; this file is about *how we work on this
codebase* — build/tooling gotchas, testing conventions, working style. Both files
get updated the same session a decision/gotcha is found, not after. The user's
global `~/.claude/CLAUDE.md` (Javadoc, unit-test and git rules) also applies and is
not repeated here except where this project has its own twist.

## What this project is

**Robot Rampage** — an online multiplayer adaptation of the board game
**RoboRally** (classic 2005 rules), 2–8 players, dedicated authoritative server,
turn-based (simultaneous secret programming, then deterministic resolution). Full
design in [`design.md`](./design.md) — read that first for rules or architecture.
Sibling project and architecture template: `C:\Users\MKOEHLER\intellij-workspace\StarWars`
(real-time shooter, same frameworks; its `design.md`/`CLAUDE.md` are the model for
ours). Its netcode is *not* a template — ours is TCP-only and turn-based
(design.md 3.1).

## Current status

**M0, M1 (rules engine) and M2 (board format) done** — 177 unit tests. A whole turn can be resolved headlessly:
`Respawner.respawn` → `Programming.deal` → `Programming.submit` per robot →
`TurnResolver.resolve` (public API; returns a `TurnResult` of new state + stamped events).
Each sub-phase has its own package-private resolver (`MovementResolver`, `BeltResolver`,
`GearResolver`, `PusherResolver`, `LaserResolver`, `CrusherResolver`, `CheckpointResolver`,
`CleanupResolver`) that the `TurnResolver` calls in the order of design.md 2.4. Boards load with
`BoardLoader.loadResource("boards/<id>.json")` (validated; throws `InvalidBoardException` listing
every problem); the first board is `assets/boards/proving-grounds.json` (drafted by Claude,
picture in design.md 2.11 — that picture is *generated* by `BoardPicture` (test support) and
`DesignDocPictureTest` fails if design.md no longer shows the current board; its failure message
contains the new picture to paste in). `TurnFuzzTest` plays 30 random full games on it and checks engine
invariants every turn — keep it passing, and extend its invariants when rules change.
**Next: M3** (server session + protocol, design.md 6). After M1: M2 board
format + validator, and **I draft the first original 12x12 board myself** (user's
decision) — but only after `BoardValidator` exists, so the reachability check is
not hand-verified twice. The design was reviewed by the user (2026-09-21): tags removed
= confirmed, `DECISION:` notes in design.md 7. No assets are needed before M4.

## Decisions already made (with reasons)

- **Classic 2005 rules**, not the 2016 edition (asked and confirmed by the user).
- **One fixed original board for v1**, but the board format must stay open for many
  boards and *procedurally generated* boards (user requirement — design.md 3.6:
  flat runtime grid, composition/generation as pre-processing, server ships the board
  over the wire).
- **No Ashley, no Box2D, no gdxAI, no MCP server, no `dev-tools` module** — reasons
  in design.md 3.7. The user tests in-game themselves; that was faster than MCP in
  StarWars.
- **Kept:** libGDX, Maven, KryoNet (TCP only), Jackson, VisUI, JUnit 5.
- **GitHub:** https://github.com/mariokoehler/Robot-Rampage (public, branch `main`,
  created 2026-09-21; GitHub has no spaces in names, hence `Robot-Rampage`). The README
  carries a prominent work-in-progress warning — keep it until the game is actually
  playable. Commits are GPG-signed with the user's usual identity (same as StarWars; if
  `git commit` fails with a `gpg-agent` error, start Kleopatra and retry, never
  `--no-gpg-sign`) and contain no AI/Claude references (global rule).
- **jgitver computes `${project.version}`** from git tags/history (`.mvn/extensions.xml`
  + `.mvn/jgitver.config.xml`, which drops the branch qualifier for `main` — jgitver only
  does that for `master` by default). Every pom carries the placeholder `<version>0</version>`;
  never hand-edit versions. Untagged, builds are `0.0.0-SNAPSHOT`; release tags are plain
  annotated `vX.Y.Z`, commits after a tag build as `X.Y.(Z+1)-SNAPSHOT`. Design in
  design.md 3.9.
- The rules engine (`rules`, `board` packages) **never imports libGDX**, `net` or
  `client` (design.md 3.3). **This is discipline only** — `core` depends on `gdx` and
  `vis-ui`, so nothing stops an accidental import (and `server` inherits vis-ui
  transitively). If it ever slips, the clean fix is splitting client screens out of
  `core` into their own module; that is cheapest *before* M1 piles code into
  `core`, so reconsider at the start of M1 rather than after M4.
- **`ArchitectureTest` (ArchUnit, core) enforces that rule** for `rules`/`board`/`net`/
  `client`. It inspects bytecode, so a compile-time constant from a forbidden class
  (e.g. `MathUtils.PI`) is inlined and slips through — a real class reference is
  caught (verified by adding one deliberately). The first build after adding ArchUnit
  needed network access.
- **Screen disposal:** `Game.dispose()` only calls `hide()` on the current screen, not
  `dispose()`. `RobotRampageGame.dispose()` disposes the current screen; when M4
  adds screen transitions, each `setScreen` call site must dispose the screen it
  leaves (same discipline as StarWars).

## Build system

Maven, multi-module. Modules: `core` (rules, board format, net, client screens),
`lwjgl3` (desktop client launcher), `server` (`gdx-backend-headless` dedicated
server). Java 25 (`maven.compiler.release`), Maven 3.9.x.

- `mvn clean package` from the repo root builds everything and runs the tests;
  client jar `lwjgl3/target/RobotRampage-<version>.jar`, server jar
  `server/target/RobotRampage-Server-<version>.jar`. Run a jar with
  `java --enable-native-access=ALL-UNNAMED -jar <jar>`.
- `start_client.cmd` / `start_server.cmd` reinstall `core` and run the module. By
  hand: install `core` first, **then** run the target module *alone* — no `-am` on
  the exec step (a bare exec goal with `-am` walks the whole reactor, including the
  `pom`-packaging parent, and fails there):
  ```
  mvn install -pl core -am -DskipTests
  mvn -pl lwjgl3 compile exec:exec
  mvn -pl server compile exec:java
  ```
- Re-run `mvn install -pl core -am -DskipTests` after **every** change to `core`
  **and after every new commit or tag** before running `lwjgl3`/`server` alone — they
  resolve `core` from `~/.m2`, and a commit changes the jgitver version the next build
  expects, so the old install silently stops matching (or runs old code).
- `mvn -o` (offline) works once dependencies are cached. The first build needed
  network access for `org.lwjgl:lwjgl-bom` (not in the StarWars-populated cache).

### Maven + libGDX gotchas (carried over from StarWars, still valid)

- Maven's default `maven-compiler-plugin` ignores `maven.compiler.release`; the
  parent pins 3.13.0. Surefire is pinned to 3.2.5 for JUnit 5.
- **LWJGL is pinned via an `lwjgl-bom` import** in the parent's `dependencyManagement`
  (`lwjgl3Version` 3.4.3) — one import covers every artifact and native classifier,
  replacing StarWars' ~200-line per-classifier block. Verified with
  `mvn -pl lwjgl3 -am dependency:tree` (needs `-am`, and no `-q`).
- **Each native-backed libGDX piece needs its *own* `*-platform` `natives-desktop`
  dependency**, independently, in every runnable module (`lwjgl3`, `server`). Missing
  one only fails at *runtime* (`SharedLibraryLoadRuntimeException`), never at
  build/test time. Currently only core libGDX (`gdx-platform`). Adding `gdx-freetype`
  to `core` means adding `gdx-freetype-platform` to `lwjgl3` (and `server` if it
  uses it).
- `${project.parent.basedir}` is not a valid property — use
  `${project.basedir}/../assets` for the shared `assets/` resource dir.
- The KryoNet fork is JitPack-only; the parent pom declares the JitPack repository.
- **Kryo wire compatibility depends on registration order, not class names.** Every
  class sent over the wire is registered in `MessageRegistry.register(Kryo)`,
  append-only, never reordered; `MessageRegistryTest` guards it. Add every new
  message class to that registry *and* to the test. **Kryo 5.5 handles records and a
  field typed as a sealed interface** (verified with `LoggedEvent`/`GameEvent`), but
  every concrete record must still be registered explicitly:
  `MessageRegistryTest.everyGameEventTypeIsRegistered` fails if a new `GameEvent`
  record is forgotten.
- A plugin's top-level `<configuration>` applies to every goal of that plugin invoked
  from the CLI — the `lwjgl3` exec config is written for `exec:exec`; `exec:java`
  there would fail. Don't share one goal's config with another. Same-`groupId:artifactId`
  duplicate `<plugin>` blocks: Maven silently drops one — merge into one block.
- XML comments in poms can't contain a literal `--` anywhere in their text.
- The `Unsafe`/`System::load` JVM warnings at startup (from LWJGL, Kryo, libGDX) are
  harmless and identical to StarWars'; `--enable-native-access=ALL-UNNAMED` silences
  the last one.

## Testing conventions

- **JUnit 5, used selectively** (design.md 3.8): the rules engine, board format,
  message registry and server session logic get real tests; screens, rendering and
  animation do not. Don't chase coverage numbers.
- Assert on the resolver's **event list** and final state, not internals.
- **`AsciiBoard`** (`core/src/test/.../testsupport`) builds a `Board`/`GameState` from an
  ASCII picture — its grammar is documented in its class Javadoc (terrain characters
  `. > < ^ v E W N S o c a + x 1-9`, `|` for a wall east of the previous square, `-`
  lines for walls between rows, robots as digits in a second picture). Use it for every
  rules test. Text blocks strip common indentation, so a `-` wall line must sit
  *under the character it belongs to* relative to the other lines.
- **`AsciiBoard.state/board(..., builder -> ...)`** takes an extras hook for things without
  a picture character (lasers, pushers, crushers active in given registers). Walls are
  mirrored onto the neighbour: two adjacent pushers/lasers mounted on facing sides create a
  wall *between* their squares, which blocks pushes across it — a scenario that looks
  fine on paper can be geometrically impossible.
- **JSON classes for boards are records** (`BoardDefinition` and its nested records), not the
  bean-style classes of the StarWars config files: they are immutable data, and Jackson 2.22 reads
  records directly. Strict loading (unknown properties fail) is deliberate. Board coordinates in
  JSON use the enum names of `SquareFeature`/`Direction` (`GEAR_CLOCKWISE`, `NORTH`, ...).
- **Rule-test gotcha:** a test whose expected result contradicts the rules is usually a
  wrong test — re-derive from design.md before "fixing" the engine (e.g. entering a belt
  square bends a robot by the *turn between heading and belt direction*, not by the
  robot's old facing).
- Every new class and method gets HTML Javadoc (`@author Mario Koehler`), including
  private methods — see the global instructions. Never put session narrative
  ("changed because we discussed X") into Javadoc/comments.

## Asset pipeline

- `assets-raw/` — source files exactly as the user drops them (spontaneous filenames,
  any format, PSDs welcome); committed as backup. `assets/` —
  what the game loads at runtime; Claude integrates: rename properly, convert, pack
  atlases, copy over. **Never load from `assets-raw/`.**
- Both are currently empty (`.gitkeep`). When atlases are needed, reuse StarWars'
  `AtlasPacker` approach (libGDX `TexturePacker`, `gdx-tools` test-scoped in `lwjgl3`,
  the packer class under `lwjgl3/src/test`) — see StarWars `CLAUDE.md` "Asset
  pipeline" for the numeric-suffix and atlas-vs-`Texture` gotchas.
- The user has Photoshop and will hand-edit image assets on request — just ask.
- `assets/*.json` config files (connection config etc.) are runtime-generated and
  gitignored; the `*.cmd` helpers and the `exec:exec` config run the client with
  `assets/` as its working directory.

## Conventions / preferences

- **Design decisions go into `design.md` immediately**, in the same session as the
  decision. Section numbers are stable anchors — don't renumber.
- **When a sub-detail is unspecified, propose a concrete default directly in the doc
  and mark it *(unconfirmed)*** instead of stalling. **The user confirms by deleting the
  tag** — text whose *(unconfirmed)*/*(verify)* tag was removed is a confirmed/verified
  decision (treat it as settled, don't re-litigate). Open questions in design.md 7 get a
  `DECISION:` note behind them when settled. Reserve `AskUserQuestion` for
  foundational/hard-to-reverse choices (edition, a core library, a balance-defining
  rule).
- **Rules recalled from memory are marked *(verify)*** in design.md and must be
  checked against the actual rulebook (web) before implementing.
- **Proactively flag security-relevant concerns** during design (e.g. hidden hands
  must never be sent to other clients; hash passwords if accounts arrive), briefly.
- **Never commit or push** unless asked (global rule).
- **Advisor before and after** on any multi-file feature: consult before writing code
  (design-level issues) and again once it looks complete (integration-level issues).
- Sessions are incremental (evenings) — leave `design.md` and this file fully in sync
  before a session ends, don't just describe changes in chat.
- The user tests the game in-game and reports back; there is no remote-control/MCP
  harness for driving the client.
