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
Sibling project and architecture template: `C:\Users\MKOEHLER\intellij-workspace\StarWars` (or 'C:\Users\mario\StarWars' depending on which machine we're working on)
(real-time shooter, same frameworks; its `design.md`/`CLAUDE.md` are the model for
ours). Its netcode is *not* a template — ours is TCP-only and turn-based
(design.md 3.1).

## Current status

**M0, M1 (rules engine), M2 (board format) and M3 (server session + protocol) done, M4 slices 1-7 (client shell, Startup,
Connect, Lobby, static board renderer, programming screen, turn replay, Game Over screen, respawn/power-down/eliminated
dialogs) done** — 363 unit tests in `core` plus 11 integration tests in `server` (real sockets, threads). A whole turn can be resolved headlessly:
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
The server side is complete and playable by any client that speaks the protocol (design.md 3.5): `ServerLauncher`
starts it, `ServerController` (server module) wires `NetworkServer` ↔ `GameSession` (core `session` package).
**UI (M4) inputs, all in the repo:** the mockups are the visual source of truth — Claude Design canvas
`https://claude.ai/artifact/QdtnZisPjfGof2Uvsn8iqT` (design.md 4.6 summarises every screen, dialog and what protocol
additions they need; read the canvas with `Artifact read`, large `.dc.html` files are saved to disk, not printed —
analyse them with a script). `client.ui.Theme` (from Claude Design, moved into `client.ui`, one fix: FreeType's letter
spacing field is `spaceX`) holds all tokens; fonts are in `assets/fonts`; derived vector art is in `assets-raw/design`
(re-derive with `tools/design-import`). Note `Theme.robotColor(seat)` takes the **displayed seat number 1..8 = robot id +
1**. Design canvas is 1920×1080 → `FitViewport`.
The canvas's "suggestion" items (Menu, Settings, leave confirmation, board key, standings ranking, archive-marker diamond)
were **confirmed as decisions** by the owner — build them as drawn.
**Client slice 1 (design.md 4.2, 6):** `RobotRampageGame` owns `UiKit` (fonts + shapes + widget factories, from `Theme`) and the
`SettingsStore`; screens extend `client.screen.StageScreen`; dialogs are `ModalDialog`. **Threading rule for the client:** anything
that blocks (connecting, disconnecting) runs off the render thread inside `ConnectionAttempt`, whose outcomes are only
drained by `update()` on the render thread; screens read `ConnectFlow`, never a captured screen reference from another
thread. `client.connect` and `client.settings` stay libGDX-free (`ArchitectureTest`), which is why they have unit tests and the
screens do not. The Settings button is disabled (no Settings dialog yet); `GameScreen` is the game screen (programming half only; resolution is not animated yet). **Lobby (slice 2):** `client.lobby.LobbyView` (libGDX-free, tested) decides
rows/chips/Start enablement and mirrors `GameSession.startGame` exactly (host + `minPlayers` + everyone *but the host* ready);
keep it in step with the server. A `LobbyScreen` never disconnects in `dispose()` (the link moves on to the next screen,
and disposal is deferred a frame) — only its explicit leave path does. Shapes from `Theme.Shapes` are **3 px taller than they
look** (shadow reserve): size chips/toggles with `UiKit.SHAPE_RESERVE`. Images: `UiKit.image(path)`; PNGs in `assets/robots`,
`assets/tiles` come from `tools/design-import/rasterize.js --width 128`.
**The extracted mockups are a snapshot** — the Claude Design canvas is the source of truth and was changed after the extraction
(e.g. the startup tagline), so re-read it before building each screen. The Startup and Connect screens (slice 1 version) were tried
in-game by the owner and look fine; the slice-2 startup redo and the lobby were tried with two clients and also look fine.
**There is no logo:** the name is Bungee text with the tagline "Plan carefully. Crash spectacularly." below it (owner decision).
**Not done from M3: autosave** (design.md 3.10). **Board renderer (slice 3):** `client.render.BoardActor` + libGDX-free `client.board` (`BoardGeometry`, `RobotPose`);
sprites in `assets/board` are generated by `tools/design-import/make-board-sprites.js` (run it after changing the SVGs) — **except `robot-wedge.png`**, which the owner repainted by hand (bright green arrow) and the script deliberately no longer touches: edit the PNG directly. `drawRobots` draws all bodies first, then all wedges/badges, so a wedge is never covered by another robot.
**To look at the renderer without a game, run `BoardSnapshot`** (`lwjgl3/src/test`; hidden window, writes a PNG — build the
classpath with `mvn -pl lwjgl3 dependency:build-classpath -Dmdep.includeScope=test`, run with `assets/` as working directory;
it flips the read-back rows itself; pass a board *file path* instead of a resource, e.g.
`lwjgl3/src/test/resources/renderer-probe.json`, to see pushers/crushers/multi-beam lasers, which `proving-grounds` has
none of). Not yet drawn: program preview, highlights, archive markers.
**Programming screen (slice 4):** `client.game` (`GameModel`, `ProgramDraft`, `CardLook`; libGDX-free, tested, ArchUnit-guarded) +
`GameScreen`, `CardView`, `ProgressPill`. Locked registers are the highest-numbered; send only free registers; nine damage confirms an
empty program. Rebuilding widgets often? Use `UiKit.rounded(...)` (cached), never `shapes.rounded` in a rebuild loop (it leaks a texture
each call). **Server-filled programs (timeout, squeeze, reconnect) are shown as hidden "?" slots** (the client is not told the cards; needs a
`ProgramFilledIn` message). **Run `GameScreenDriver`** (`lwjgl3/src/test`, package `client.screen`, same classpath recipe) after changing
`GameScreen`: it clicks through the real widgets on a hidden window and stops at the first wrong thing. **Look at states you cannot reach by playing with `ScreenSnapshot`** (`lwjgl3/src/test`, same classpath recipe as
`BoardSnapshot`; writes `game-*.png`). Cards/icons PNGs: `tools/design-import/rasterize.js` into `assets/cards`, `assets/icons`.
**Replay (slice 5):** `client.replay.TurnReplay` (libGDX-free; its main test replays real `TurnResolver` turns and compares with the
engine) + the resolution layout inside `GameScreen`. `GameModel` HOLDS the post-turn `StateSnapshot`/`GameOver` until
`completeResolution()`; never apply them earlier. The server's turn pause (`SessionConfig.defaults()`) is the playback budget: **1× is deliberately slow** (`TurnReplay.PACE` = 2, the owner asked for half speed), so the pause is 12 s + 260 ms/event, max 60 s — change `PACE` and the pause together, or the replay gets cut off by the next deal.
**Host pause (design.md 2.13):** `SetTimerPaused` (C→S, host only) / `TimerPaused` (S→all). `GameSession` freezes its clock (`now()`) and shifts the deadline and every `disconnectedAt` on resume; it only works in `PROGRAMMING` and ends by itself when the turn resolves. The host's button is in `GameScreen` next to the time pill; `ScreenSnapshot` writes `game-host*.png`/`game-guest-paused.png` for it.
Sample real turns for tools with `SampleTurn` (`lwjgl3/src/test`); `ScreenSnapshot` writes `resolution-*.png` incl. a laser volley.
Belts pick corner/join/T/X pieces from their neighbours (`BoardGeometry.beltPiece`); the design draws them leaving NORTH.
**Game Over (slice 6):** `GameOverView` is a third group inside `GameScreen`; `client.game.Standings` (libGDX-free, tested) ranks and words the results from what `GameModel` saw replayed (last flag per robot, elimination turns). `GameOver` carries `lobbyInSeconds`; the server's game-over countdown starts *after* the final turn's replay pause, otherwise the lobby switch would cut the results off. `Label.setFontScale` REPLACES the baked font scale (fonts are generated oversized): multiply by `font.getScaleX()`. `ScreenSnapshot` writes `gameover-*.png`.
**Dialogs (slice 7):** `ModalDialog`'s stripe is now a `Color` (`null` for none), not a `boolean`; `.buttons(float[], TextButton...)`
gives each button its own width. Power-down is gated: the toggle click reverts itself and opens an explanation dialog
(`GameScreen.showPowerDownDialog`/`applyPowerDownChoice`); turning it off is instant. `client.ui.FacingPicker` (new widget,
libGDX) is the four-way respawn-facing picker, pre-selected to the robot's current facing; wrap it in a plain `Table` before
adding it to a `ModalDialog.row(...)` — a bare `Group` isn't a `Layout`, so the dialog stretches it instead of centring it.
`GameModel.myEliminationJustSeen()` (tested, including the resync case) drives the "You're out" dialog. None of the three
needed a protocol change. `BoardSnapshot.flipped` is now public so `GameScreenDriver` (different package) can save PNGs too;
run it with an output folder argument to get `dialog-powerdown.png` (the only one of the three that needs a click, so
`ScreenSnapshot` can't reach it on its own).
**Next: reconnecting, drag and drop, the "time's up" banner — that is where the design system in `artifact B6rnPgeQteFmVd6PCSMu63` (Claude
Design; fonts in `assets-raw/ttf`, robot SVGs to be rasterised) and gdx-freetype come in. After M1: M2 board
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
- **Kept:** libGDX, Maven, KryoNet (TCP only), Jackson, JUnit 5, FreeType (fonts). UI is plain Scene2D on `client.ui.Theme` (design.md 4.2).
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
  `client` (design.md 3.3). **This is discipline only** — `core` depends on `gdx`,
  so nothing stops an accidental import. If it ever slips, the clean fix is splitting client screens out of
  `core` into their own module; that is cheapest *before* M1 piles code into
  `core`, so reconsider at the start of M1 rather than after M4.
- **`ArchitectureTest` (ArchUnit, core) enforces that rule** for `rules`/`board`/`net`/
  `client`. It inspects bytecode, so a compile-time constant from a forbidden class
  (e.g. `MathUtils.PI`) is inlined and slips through — a real class reference is
  caught (verified by adding one deliberately). The first build after adding ArchUnit
  needed network access.
- **Screen disposal:** `Game.dispose()` only calls `hide()` on the current screen, not
  `dispose()`. `RobotRampageGame.dispose()` disposes the current screen, and its `setScreen` override disposes the
  screen it leaves via `postRunnable` (screens change from inside their own `render`, so disposing at once would draw a
  disposed stage).

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
- **Threading contract (design.md 3.5):** KryoNet callbacks only enqueue; the owning loop drains via
  `NetworkServer.poll`/`NetworkClient.poll` on ITS thread; the session and the rules engine are never touched from
  another thread and have no locks. `ServerIntegrationTest` runs a dedicated loop thread + KryoNet threads + client
  threads at once — keep it that way, don't call session methods directly from a test thread there.
- **Session tests use a fake clock** (`GameSession` takes a `LongSupplier`) and a recording `Outbox` — never sleep in a
  session test. Use the 5×30 test board (no robot can reach the flag in one turn), otherwise a lucky random program
  ends the game in turn 1 and your test measures the wrong thing. To check card conservation *between* turns remember
  the next hands are already dealt (`cardsInPlay` + hands = 84); a disconnected player is always auto-filled, so a
  player is never removed while owing a hand.
- **Client threading:** `NetworkClient.connect` BLOCKS (up to 10 s) — never call it from the render thread (StarWars'
  socket-stall lesson). `ConnectionAttempt` does it on a worker thread; use it, don't call `connect` from a screen.
- **The game seed** is logged by the server at startup (`game seed N`) and can be given as the 2nd launcher argument
  (`ServerLauncher [port] [seed]`); put it in any bug report — random fills and shuffles are reproducible from it.
- **Modules:** run `mvn install -pl core -am -DskipTests` before `mvn -pl server test` (server resolves core from
  `~/.m2`, see Build system).
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
