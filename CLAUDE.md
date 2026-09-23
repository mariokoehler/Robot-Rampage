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

**M0, M1 (rules engine), M2 (board format), M3 (server session + protocol) and M5 (pushers/crushers in play) done, M4
slices 1-11 (client shell, Startup, Connect, Lobby, static board renderer, programming screen, turn replay, Game Over
screen, respawn/power-down/eliminated dialogs, reconnecting a dropped client, the "Time's up" reveal, the one texture
atlas, the "ghost path" preview) done — nothing left "still to come" on M4's own roadmap** — 396 unit tests in `core`, 4
in `lwjgl3` (`Lwjgl3LauncherTest` pure arithmetic, `AtlasCoverageTest` parses `assets/textures/game.atlas` as text —
everything else in that module's test tree is a
`main()`-driven dev tool, not
picked up by surefire), plus 11 integration tests in `server` (real sockets, threads). A whole turn can be resolved headlessly:
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
`lwjgl3/src/test/resources/renderer-probe.json`, to see multi-beam lasers (still none in `proving-grounds`) or more
pusher/crusher pairs than the two `proving-grounds` now has (M5)). Not yet drawn: program preview, highlights, archive markers.
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
**Game Over (slice 6):** `GameOverView` is a third group inside `GameScreen`; `client.game.Standings` (libGDX-free, tested) ranks and words the results from what `GameModel` saw replayed (last flag per robot, elimination turns). `Label.setFontScale` REPLACES the baked font scale (fonts are generated oversized): multiply by `font.getScaleX()`. `ScreenSnapshot` writes `gameover-*.png`.
**Returning to the lobby (changed 2026-09-25, playtest feedback):** no more server timer — `GameSession` stays in `GAME_OVER`
indefinitely; the host's `ReturnToLobby` (C→S) is the only way out (`GameSession.returnToLobby`, host + phase-gated, refused
otherwise). `GameOver` no longer carries a countdown. `GameOverView`'s lobby button is real now: enabled and clickable for
the host (`GameModel.canReturnToLobby()` = `amHost() && stage == Stage.OVER`), disabled ("Waiting for host…") for everyone
else; `GameScreen.showGameOver`/the per-frame `update()` call `GameOverView.setCanReturnToLobby`, not the old
`setLobbySeconds`. `GameScreenDriver.driveGameOverAsHost` is the only driver scenario with a *host* player, since `drive()`'s
own `ME` is deliberately not the host (to check the disabled/non-host path); it builds its own minimal `GameScreen`/`GameOver`
rather than reusing `drive()`'s fixture.
**Dialogs (slice 7):** `ModalDialog`'s stripe is now a `Color` (`null` for none), not a `boolean`; `.buttons(float[], TextButton...)`
gives each button its own width. Power-down is gated: the toggle click reverts itself and opens an explanation dialog
(`GameScreen.showPowerDownDialog`/`applyPowerDownChoice`); turning it off is instant. `client.ui.FacingPicker` (new widget,
libGDX) is the four-way respawn-facing picker, pre-selected to the robot's current facing; wrap it in a plain `Table` before
adding it to a `ModalDialog.row(...)` — a bare `Group` isn't a `Layout`, so the dialog stretches it instead of centring it.
`GameModel.myEliminationJustSeen()` (tested, including the resync case) drives the "You're out" dialog. None of the three
needed a protocol change. `BoardSnapshot.flipped` is now public so `GameScreenDriver` (different package) can save PNGs too;
run it with an output folder argument to get `dialog-powerdown.png` (the only one of the three that needs a click, so
`ScreenSnapshot` can't reach it on its own).
**Reconnect (slice 8):** `client.connect.Reconnector` (new class, libGDX-free, unit-tested with a fake `ServerLink` in
`ReconnectorTest`) is the retry state machine: `GameScreen.onDisconnect()` starts one with the player's own display name
(`model.nameOf(model.mySeat())`), the session token and the grace seconds from `server.welcome()` and a `NetworkClient::new`
link factory, matching `ConnectionAttempt`'s pattern of taking a `ServerLink` rather than opening its own. **The display
name is not optional, even though the server ignores it whenever the token still
names a seat** (advisor caught this before it shipped): grace expiring server-side first, the server process
restarting, or the session already having moved back to `LOBBY` (which forgets a dropped player, 5.1) all turn the
retry into an ordinary join the server evaluates by name, and a blank name there is a silent wrong-seat bug, not an
error. `GameScreen` shows a "Connection lost" dialog (only a "Leave game" button — it retries by itself) while
`TRYING`, refreshed every frame with `reconnector.attemptNumber()`/`secondsLeft()`, the latter formatted `m:ss` like
the mockup's "Away 9:12" (`GameScreen.formatCountdown`, the same formula as `GameModel.timeText()` — no shared home for
it exists yet, so it is duplicated once rather than introducing one for a single extra call site); on `SUCCEEDED` it
hands the fresh `ConnectedServer` to a **new `LobbyScreen`**, exactly like a first join — `LobbyScreen` already turns a
`GameStarted` found among the connection's early messages into a fresh `GameScreen` on its own, so no reconnect-specific
transition code was needed for that part. `GameScreen.dispose()` now cancels a live `Reconnector`, mirroring
`ConnectScreen`'s own `dispose()` (its worker thread is a daemon, so this is tidiness, not a leak fix).
`LobbyScreen.onDisconnect` is untouched (a lobby disconnect has no seat to reconnect to). *Reviewed, not changed:* a
server that stays unreachable for the whole default 10-minute grace period at the 3 s retry delay creates on the order
of 200 short-lived `NetworkClient`s; each is torn down through the same `client.stop()` that already ends every failed
`ConnectionAttempt` (`NetworkClient.connect`'s catch block, then `ConnectionAttempt.release()`), so nothing new leaks —
just more Kryo-registration churn than before this slice, which is an accepted trade-off of retrying automatically
rather than a fix candidate. **Testing an async retry
loop against simulated time is easy to get backwards:** a busy-spin helper that drains the background `ConnectionAttempt`
must call `reconnector.update(0f)` — passing any nonzero delta on every spin iteration silently fast-forwards through the
whole grace period in a handful of loop iterations, regardless of real elapsed time (`ReconnectorTest.awaitAttempt`);
crossing the retry delay or the grace period is a separate, single, explicit `update(largeValue)` call. Needing this
exposed a real ordering bug in `Reconnector.update()` itself: the retry-countdown branch could start a new attempt in
the same tick the grace period had just expired (checked only afterwards, and skipped because `current` was no longer
`null`), so a `GAVE_UP` could be silently swallowed by a fresh `TRYING` — fixed by refusing to start the new attempt
once `remainingSeconds <= 0f`. `Reconnector.isWaitingToRetry()` exists only so the test (and any future UI) can tell
"a try just failed, the next is queued" apart from "trying right now" without waiting on wall-clock time either way.
`GameScreenDriver.driveReconnect` covers only what needs a real widget (the dialog opening on disconnect, and "Leave
game" returning to `ConnectScreen`) — the retry/grace/refusal state machine itself has no window to click and stays in
`ReconnectorTest`.
**Reconnect bugfixes (found by the user in-game, 2026-09-22):** two gaps `Reconnector` alone didn't cover.
(1) **Duplicate display names.** `GameSession.join` now refuses a second player under a name (case-insensitively) already
held by a seated, not-yet-left player — `"That name is already taken."` **The check must run *after* the
`phase != LOBBY` gate, not before**: a disconnected player's own seat is still in the `players` map (merely
`connected == false`, not `left`), so checking the name first told a player trying to get back in with a stale/missing
token that their own name was taken, instead of the correct "A game is already in progress." (advisor caught this before
it shipped; `GameSessionTest.aStaleTokenDuringARunningGameIsRefusedForBeingInProgressNotForTheName` guards the ordering).
(2) **A closed/crashed client couldn't rejoin at all**, because `Reconnector` only exists in memory inside a live
`GameScreen` — a relaunched client had no token to present and `ConnectScreen` always sent `null`, so the server (rightly)
refused a nameless new join mid-game with "A game is already in progress." **Fix: `ClientSettings` now persists the
session token too** (`ClientSettings.sessionToken`, `withSessionToken`), saved in `LobbyScreen`'s constructor — every
path that reaches the lobby (first join, a successful `Reconnector`, or the game handing the connection back) passes
through it — and presented again by `ConnectScreen.join` on every future connect attempt, whether or not it turns out to
be a reconnect. **This reverses the earlier "keep the token in memory only" call** for the *manual* reconnect path (the
automatic `Reconnector` above still only ever needs the in-memory one): it is safe to persist and always resend, because
an unrecognised token (wrong server, expired grace, server process restarted) makes `GameSession.join` simply fall
through to an ordinary join by name — never a wrong-seat bug, at worst a normal refusal. *Reviewed, not changed:* on a
shared computer under one Windows account, a second person could in principle inherit a still-live token within its
grace window if they leave the pre-filled name unchanged; low severity (a casual, no-stakes hobby game, `user.home`
already scopes the file per OS account) and the one guard considered (only resend the token when the typed name still
matches the name it was saved under) doesn't work as a cheap add-on — `ConnectScreen.tryToConnect` already overwrites
the saved display name with whatever was just typed *before* `join` reads it, so the comparison is always true by the
time it would run; doing this properly needs a persisted "name the token belongs to" separate from the free-typed
`displayName`, which was judged not worth it for the actual risk. **Not verified end-to-end** (no GUI-automation harness
exists for this project, by design — see "The user tests the game in-game" below): the exact scenario the user hit
(close the client mid-game, relaunch, reconnect) needs a real in-game check that `~/.robot-rampage/client-settings.json`
now carries a `sessionToken` after joining, and that relaunching and connecting actually re-seats the same player.
**"Time's up" reveal (slice 9):** `ProgramRevealed` (new message, S→ the affected player only, all five registers in
order) is `GameSession.revealProgram`'s answer to "your program is locked in, but you never chose these cards": sent
from `fillRandomly` (a live timeout, or a disconnected player's turn-start fill) and from `resync` (a reconnecting
player whose program is already confirmed, for any reason). **`ProgramDraft.revealed(lockedCards, freeCards)`** (new
factory) is why the free registers show in the normal card look, not the grayed locked one, exactly like a program the
player placed themselves: it writes straight into `placed[]`, bypassing `place`/`placeAt`'s hand-membership check,
since these cards were never in a hand the player picked from — `place`/`placeAt` stay exactly as they were, this is a
separate construction path. `GameModel` remembers the turn's real damage-locked tail from the most recent `HandDealt`
(`lockedCardsThisTurn`) to split `ProgramRevealed`'s flat five cards back into free vs. locked; `programVisible()` now
also returns `true` once a reveal arrives, not only for `submittedByMe`. **A second, adjacent bug came out of building
this:** `resync`'s "tell the reconnecting player who else has already confirmed" loop used to include the reconnecting
player's own seat — which a live client reads as "the timer just ran out" (`GameModel.apply`'s `PlayerConfirmed`
handling) — wrongly labelling a self-submitted-then-reconnected program as a random fill. Fixed by excluding the
player's own seat from that loop (their own status is conveyed by `HandDealt`/`ProgramRevealed` instead, which already
says why correctly); `revealProgram` in `resync` is folded into the `handFor(player) != null` check so the two messages
are only ever sent as a pair (advisor caught the pairing gap before it shipped, though no reachable path breaks it
today). `ScreenSnapshot` gained `game-time-up.png` (all free, no damage) and `game-time-up-locked.png` (damage tail +
revealed free registers side by side — the mixed case worth actually rendering, not just unit-testing) — see
`ProgramDraftTest`/`GameModelTest`/`GameSessionTest` for the logic itself.
**Drag and drop for cards is dropped from the roadmap (user, 2026-09-22): click-only placement stays.**
**One texture atlas (slice 10):** `AtlasPacker` (`lwjgl3/src/test/.../tools`, `gdx-tools` test-scoped) packs every PNG
under `assets/board`, `assets/cards`, `assets/icons`, `assets/robots` and `assets/tiles` (56 pictures, one 2048×2048
page) into `assets/textures/game.atlas`. **Packs from `assets/`, not `assets-raw/`**, unlike the StarWars template its
Javadoc otherwise follows: `assets/tiles`/`assets/robots`/`assets/board` are themselves *generated* (rasterize.js,
make-board-sprites.js — except the hand-repainted `robot-wedge.png`, which the script already skips), so packing the
already-rasterised PNGs keeps one generation path instead of re-deriving it inside the packer. **`UiKit.image(path)` was
the one place every picture in the client loads from** (confirmed with a search — every `ui.image(...)` call site in
`core`), so switching it from a per-path cached `Texture` to `atlas.findRegion(...)` needed no other file touched:
region names keep their subfolder prefix (`combineSubdirectories = true`, verified against the generated `.atlas` file
directly rather than assumed — e.g. `"tiles/floor"`), matching the path strings already in use once `.png` is stripped.
`AtlasCoverageTest` (new, `lwjgl3`) parses `assets/textures/game.atlas` as **plain text**, not as a real `TextureAtlas`
(which would load its page as a GPU texture and need a GL context, breaking it as an ordinary surefire test) — it
diffs the region names against every PNG actually present, both directions, so both a missing region (silently
invisible — none of `ScreenSnapshot`/`BoardSnapshot`/`GameScreenDriver` would catch that on their own) and a stale
leftover region are caught. **Regenerate after adding/removing/replacing a picture:**
```
mvn -q -pl core -am install -DskipTests
mvn -pl lwjgl3 dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt -Dmdep.includeScope=test
cd lwjgl3 && java -cp "target/classes;target/test-classes;$(cat target/test-cp.txt)" de.mkoehler.robotrampage.lwjgl3.tools.AtlasPacker
```
**Ghost path (slice 11):** `client.game.MovementPreview` (new, libGDX-free, `MovementPreviewTest`) plays a robot's own
cards against a `Board` and returns where each one leaves it — **does not reuse `MovementResolver`** (it's
package-private, and its push-chain semantics are wrong for a preview that can't see other robots' hidden programs
anyway): reimplements walk/step directly against `Board.hasWall`/`inBounds`/`featureAt`, all already public. Three
deliberate simplifications, all documented in the class Javadoc since a future "the preview is wrong" report needs to
be answerable: no belts/pushers/gears/lasers/crushers (only the player's own cards move the robot); another robot
**blocks like a wall, never gets pushed** (their program is secret); a pit or the board edge **ends the preview for
good**, no waypoint drawn for the destroying card. `GameModel.ghostPath()` (`GameModelTest`) feeds it the draft's cards
— **stopping at the first still-empty free register, even past a known damage-locked tail** (a path that skipped an
unknown gap would misrepresent what happens there) — the other active robots' squares as obstacles, and
`respawnFacing()` as the start facing when one was chosen (what will actually be submitted, not the server's
last-known facing). `GameScreen.refreshBoard` draws the steps as faint copies of the player's own robot, reusing
`RobotPose`'s existing `alpha` (no new art) and drawn *before* the live robots so one standing on a ghost square
always shows fully opaque on top. **`RobotPose` gained a `showBadge` flag** (default `true` via the unchanged 4-arg
constructor; every 6/7-arg call site updated) — several ghosts of the same robot on screen together made the repeated
seat-number badge pure noise, confirmed by literally comparing a 5-card ghost trail's screenshot before and after;
`BoardActor.drawRobots` still always draws the wedge, so facing stays visible per step. **`ProgramDraft.place`/`take`
do not bump `GameModel.revision()`, so `refreshBoard` must run after every draft mutation, not just `refreshProgram`**
— `place`/`takeBack` call the full `refreshAll` for exactly this reason (advisor caught the narrower pair as a latent
staleness bug before it shipped: `ProgramDraft.placeAt`, public, has no caller today but would have silently produced
a stale ghost). **The regular `ScreenSnapshot` states are too crowded with other players' robots to read a ghost
trail by eye** — verified instead with a from-scratch debug harness on a small open board (see the slice's design.md
4.3 paragraph for the exact scenario), plus a `GameScreenDriver` check that placing/taking back a card grows/shrinks
`model.ghostPath()`.
**With slice 11, M4's own roadmap has nothing left "still to come".** Verified with the same loop as every other
client slice: full `mvn clean package`, then `BoardSnapshot`/`ScreenSnapshot`/`GameScreenDriver` (all pictures —
robots, tiles, cards, icons, dialog icons — visually spot-checked across several generated PNGs, not just one).

**M5 (pushers/crushers in play): done.** The engine already had pushers and crushers (M1) and the client's power-down
toggle already shipped (M4 slice 7), so the actual gap was narrower than the roadmap's old wording suggested — a board
that uses them, and finding out whether the existing replay UI needed anything new for them. It didn't:
`TurnReplay.apply`'s `RobotMoved`/`RobotDestroyed` handling is generic over `MoveCause`/`DestructionCause` (a pusher's
shove and a crusher's kill already animate — smooth slide, fade to nothing — exactly like a card move or a pit death),
and its feed-line wording already had dedicated `PUSHERS`/`CRUSHER` cases from the M4 slice-5 turn-replay work. Added
two pusher/crusher pairs to `assets/boards/proving-grounds.json` (design.md 2.11), each a deliberate combo — a pusher
shoves a robot straight onto a crusher, in the same registers the crusher is active in.
**Board JSON files must list `squares`/`edges` in the exact order `BoardConverter.toDefinition`'s canonical export
would produce (`(y, x)` for squares; `(y, x, side.ordinal())` for edges), not just insertion/append order** —
`ProvingGroundsBoardTest.survivesAnExportAndReload` compares the checked-in file's parsed order against a fresh
export and fails on a mismatch. Appending new squares/edges at the end of their arrays (the obvious way to add them)
breaks this the moment the board isn't already fully sorted; insert at the correct sorted position instead. Found by
running the test, not by reasoning about it — worth remembering next time a board file changes by hand.
**`BoardPicture` (`core/src/test/.../testsupport`, the tool `DesignDocPictureTest` uses to keep design.md 2.11's ASCII
picture from drifting) had no glyph for a pusher at all** — even the pre-existing laser handling only marks the
mounted square (`L`), never which side the wall sits on, and pushers simply had nothing. Added a matching `P` glyph,
same limitation (reading aid, not lossless — see its Javadoc). **Verified empirically, not just by passing tests**:
`TurnFuzzTest`/`ProvingGroundsBoardTest` passing only proves no invariant broke, not that a hazard ever fires — a
throwaway instrumented copy of the fuzz loop (300 random games, counting `RobotMoved`/`MoveCause.PUSHER` and
`RobotDestroyed`/`DestructionCause.CRUSHER` by which pusher/crusher pair) found both pairs genuinely fire (one far more
often than the other — a wall a pusher implies on its own mount blocks one approach — left as designed asymmetry
rather than re-tuned) and every fire landed exactly on its paired crusher. **Considered and explicitly skipped:**
highlighting the active pusher/crusher tile during its firing beat (mirroring the laser volley's `Beam`/`setBeams`).
Real scope for a polish nobody asked for yet: the pusher's tile is only derivable from `RobotMoved.from()`, the
crusher's only from the replay's own position tracking *before* the destroy event applies, and neither covers a
hazard that fires with nobody on it (would need `Board` itself threaded into `TurnReplay`'s constructor). Revisit only
if real playtesting says the existing feedback isn't enough.

**Settings dialog + host-settable programming timer (2026-09-23): done.** `client.screen.SettingsDialog` (design.md
4.6) opens from both Startup and the in-game Menu; every control applies and saves live via
`RobotRampageGame.saveAndApplyPreferences`, no Cancel button (matches the mockup). New `client.ui.Slider` (own hand-rolled
`Actor`, `PillToggle`'s `DragListener` pattern) is the only genuinely new widget; its `touchDown` override on the inner
`DragListener` must return `boolean` (it overrides `InputListener.touchDown`, not `void`) — call `super.touchDown(...)`
first and only call `setFromTouch` if it returned `true`. **`ClientSettings` grew to 10 fields; `SettingsStore.load()`
merges the loaded JSON tree onto `ClientSettings.defaults()` (`ObjectNode.setAll`) rather than deserializing directly**
— a record's canonical constructor has no notion of "field absent, keep the default", so a settings file saved before a
field existed would otherwise silently zero/false it out instead of getting the real default; this merge is why
`SettingsStoreTest.unknownAndMissingPropertiesAreTolerated` exists and must keep passing after any new `ClientSettings`
field. **The turn-timer control is on the Lobby screen, not in Settings** (owner's explicit split, 2026-09-23): it is a
game rule the host sets for everyone, not a personal client preference. Its bounds
(`NetworkConstants.MIN_PROGRAMMING_SECONDS`/`MAX_PROGRAMMING_SECONDS`/`PROGRAMMING_SECONDS_STEP`) live in `net`, not
`session`, purely so `LobbyScreen` can clamp client-side without importing the server-only `session` package (the
client/server package boundary, above) — `GameSession.setProgrammingSeconds` re-checks the same bounds authoritatively.
**A short turn and the countdown warning sirens interact — found via `advisor`, not by playing it out.**
`AudioKit.updateCountdownWarning` started `WARNING_30` as soon as `secondsLeft <= 30` with no regard for how long the
turn actually was, so the host's new minimum (30 s) would start the siren on the turn's very first frame and run it the
whole way. First fix attempt was raising `MIN_PROGRAMMING_SECONDS` to 45 — rejected on a second `advisor` pass as
papering over it (a 45 s turn still spends its last 30 s, two-thirds of it, sirening). **Real fix: each warning band
only starts if the turn's total programming time is longer than that band's own threshold** — `GameModel.programmingSeconds()`
(new getter) is passed into `updateCountdownWarning` alongside `secondsLeft`, so `WARNING_30` needs `totalSeconds > 30`
and `WARNING_10` needs `totalSeconds > 10`. `MIN_PROGRAMMING_SECONDS` stayed at 30. **First-run window
size gotcha**: `ClientSettings.defaults()` hardcodes 1920x1080, but `Lwjgl3Launcher` sizes the window down to fit a
smaller monitor; applying preferences unconditionally on every launch would fight that and snap a small monitor's window
back up to 1920x1080. Fixed by only seeding the settings' window size from `Gdx.graphics.getWidth/Height()` on a
genuine first run (`SettingsStore.exists()` was false), before `RobotRampageGame.create()` calls `applyPreferences` —
once a real settings file exists, its window size is respected as-is, same as any other saved preference. **Also this
session:** the ready toggle in the Lobby now plays `BUTTON_CLICK` (it is a `PillToggle`, not a `TextButton`, so it needed
its own `ClickListener` rather than going through `UiKit.button`'s factory); `TurnReplay`'s laser rendering now skips
individual missed beams within a volley that has some hits, not just whole no-hit volleys; `GAME_WON` plays once on
reaching Game Over and `WELCOME_JINGLE` once per real app launch (`RobotRampageGame.create()`, deliberately not
`StartupScreen`'s constructor, which re-runs on every "Back" from Connect). **Not verified visually**: the Settings
dialog and the Lobby stepper were compiled and unit-tested but not pixel-checked via `ScreenSnapshot`/`GameScreenDriver`
— `ScreenSnapshot` lives in a different package than the package-private `SettingsDialog` and only drives `GameScreen`,
so wiring it in is real plumbing, not a quick addition (design decision, not an oversight — see design.md 4.6). The
owner's own playtest is the verification path for this one, per this file's testing conventions.

**Next: whatever the user picks** — M6 (more boards / Board Editor), or the first real playtest,
which is the owner's to run, not a further slice to build. After M1: M2 board
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
- **Window starts at 1920x1080, not maximized** (design.md 4.2): `Lwjgl3Launcher` inherited StarWars' `setMaximized(true)`,
  which doesn't fit a game whose entire UI is one fixed 1920×1080 `FitViewport` layout — maximizing on a bigger monitor
  just letterboxes it, it doesn't show more. `Lwjgl3Launcher.windowSize(width, height)` (package-private, unit-tested in
  `Lwjgl3LauncherTest`, the first real JUnit test in the `lwjgl3` module — the rest of that package is `main()`-driven
  dev tools, not picked up by surefire) is the pure arithmetic: native size if the monitor is at least that big,
  otherwise the largest 16:9 window that fits. `Graphics.DisplayMode` (not a nested type of
  `Lwjgl3ApplicationConfiguration`, despite `getDisplayMode()` living there) is the return type to import.
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
- **`mvn test-compile`/`test` can silently skip recompiling a test file whose only change is that a *dependency's* shape
  changed** (a record losing/gaining a component, a method removed) — the compiler plugin's staleness check didn't
  notice and exited 0 even though the test file still referenced the old signature. Found removing `SessionConfig`'s
  `gameOverMillis`/`GameOver`'s `lobbyInSeconds`: `mvn -pl core test-compile` passed clean, `mvn -pl core clean
  test-compile` immediately failed with the real errors. After changing a record's shape (not just a method body),
  `clean` before trusting a green `test-compile`/`test`, at least for the modules that depend on it.

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
- **Spin-waiting for an async worker thread while a class also tracks simulated time (`ConnectionAttemptTest`,
  `ReconnectorTest`) must pass `0f`/no time to the spin loop itself** — a busy loop can run far more iterations than
  real seconds pass, so a nonzero delta on every iteration silently fast-forwards through retry delays and grace
  periods. Advance simulated time only in a separate, explicit, single call at the exact point the test means to
  cross a threshold. This is what caught a real ordering bug in `Reconnector.update()` — see "Reconnect (slice 8)"
  above.
- **The game seed** is logged by the server at startup (`game seed N`) and can be given as the 2nd launcher argument
  (`ServerLauncher [port] [seed]`); put it in any bug report — random fills and shuffles are reproducible from it.
- **`client.debug.TurnLog`** (added 2026-09-24, for a playtest bug report) prints every protocol message a client
  sends or receives to its console, one line each, all prefixed `TURNLOG` (filter the console down to just those); the
  program a player is about to submit is logged separately just before it (`GameScreen.confirm`), since the card
  priorities inside `SubmitProgram` alone are not readable. `TurnResolved`'s full per-register event list and every
  `StateSnapshot` are the richest lines — records' default `toString()` already names every field, so no
  message-specific formatting was needed. Ask the user to paste the `TURNLOG` lines (plus the seed, above) from around
  the turn in question; that is normally enough to write a deterministic `GameSessionTest`/`TurnResolver` test that
  reproduces it without needing a live two-client repro.
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
- `assets-raw/` is mostly empty (the `.gitkeep`-era placeholder; `assets-raw/design`, extracted from the Claude Design
  canvas, is the exception). `assets/` holds the real runtime pictures, packed into one atlas by `AtlasPacker` — see
  "One texture atlas (slice 10)" above for the details and the regeneration command; `AtlasCoverageTest` guards it from
  going stale. Unlike StarWars' `AtlasPacker`, this one packs from `assets/` (already-rasterised PNGs), not
  `assets-raw/` — see its Javadoc for why. StarWars `CLAUDE.md` "Asset pipeline" still has the numeric-suffix
  (animation-frame) gotcha, not needed here since nothing in this game is a frame sequence.
- The user has Photoshop and will hand-edit image assets on request — just ask.
- `assets/*.json` config files (connection config etc.) are runtime-generated and
  gitignored; the `*.cmd` helpers and the `exec:exec` config run the client with
  `assets/` as its working directory.
- **Sound effects (added 2026-09-25):** `assets-raw/sfx/*.mp3` → `assets/sfx/*.mp3`, a verbatim copy — mp3s need no
  conversion, libGDX's LWJGL3 backend decodes them natively and needs no extra native dependency beyond the
  already-present `gdx-platform` natives (checked against StarWars: it has no separate audio native artifact either).
  `client.audio.AudioKit` (own package, owned by `RobotRampageGame` like `UiKit`, loaded/disposed the same way) loads
  every clip eagerly as a `Sound` — there are only a dozen, an `AssetManager` would be overkill. See design.md 4.7 for
  what plays where.

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
- **Commit and push after each slice, without asking first** (user, 2026-09-22 — overrides the global "never commit or
  push unless asked" rule for this project specifically). Applies once the slice is implemented, fully tested (unit
  tests plus a full `mvn clean package` green), documented (`design.md`/`CLAUDE.md` updated the same session), and —
  for a multi-file feature — reviewed via `advisor`.
- **Advisor before and after** on any multi-file feature: consult before writing code
  (design-level issues) and again once it looks complete (integration-level issues).
- Sessions are incremental (evenings) — leave `design.md` and this file fully in sync
  before a session ends, don't just describe changes in chat.
- The user tests the game in-game and reports back; there is no remote-control/MCP
  harness for driving the client.
