# Robot Rampage

An online multiplayer adaptation of the board game **RoboRally**: program your
robot with five cards, then watch every robot's program play out — conveyor
belts, gears, lasers and pits included — as you race to touch the flags in order.
Built on [libGDX](https://libgdx.com/) with a dedicated authoritative server.

**Game design and technical architecture live in [`design.md`](./design.md)** —
the source of truth for how and why everything works. `CLAUDE.md` holds working
notes for the AI-assisted development process.

> [!WARNING]
> **This project is a work in progress.** It is playable end-to-end (2–8 players,
> a dedicated server, the full client) and has had several real playtests, but it
> is pre-1.0: no accounts, no autosave/reconnect-after-restart, one board, and no
> stable API. Everything, including the design, may change without notice.

## Playing

Grab the latest client from [Releases](https://github.com/mariokoehler/Robot-Rampage/releases) —
`RobotRampage-Client.zip` is self-contained (bundles its own Java runtime, nothing to
install) — unzip it and run `RobotRampage.exe`. You'll need a running server to connect to;
ask whoever is hosting for its address.

## Building from source

**Requirements:** JDK 25, Maven 3.9+.

```
mvn clean package
```

builds the client and server jars and runs the unit tests. To run locally on
Windows:

```
start_server.cmd    # one terminal
start_client.cmd    # one per player
```

Both scripts reinstall `core` before running — necessary because this project's version is
computed from git tags (`jgitver`), so a stale local install of `core` can silently fall out
of date the moment a new commit lands.

Not on Windows, or want the plain Maven commands:

```
mvn install -pl core -am -DskipTests
mvn -pl server compile exec:java      # one terminal
mvn -pl lwjgl3 compile exec:exec      # one per player
```

To run a packaged jar directly instead:

```
java --enable-native-access=ALL-UNNAMED -jar server/target/RobotRampage-Server-<version>.jar
java --enable-native-access=ALL-UNNAMED -jar lwjgl3/target/RobotRampage-<version>.jar
```

## Repository layout

| Path | Contents |
|---|---|
| `core/` | Shared code: rules engine, board format, network layer, client screens |
| `lwjgl3/` | Desktop client launcher |
| `server/` | Dedicated headless server |
| `assets/` | Game assets loaded at runtime |
| `assets-raw/` | Raw source assets, not loaded by the game |

## Releasing

Pushing a `vX.Y.Z` tag triggers two independent GitHub Actions workflows:
`release-client.yml` builds a self-contained Windows `RobotRampage-Client.zip`
(via `jpackage`) and publishes it as a GitHub Release asset; `release-server.yml`
builds and pushes a Docker image to `ghcr.io/mariokoehler/robotrampage-server`.
Both embed the same tag-derived version, which the client/server handshake
checks exactly — see design.md 3.9/3.11/3.12 for the full mechanism, and
`deploy/docker-compose.yml` for how the server actually gets deployed.

## Documentation

- **[`design.md`](./design.md)** — game design and system architecture:
  read this first for anything about how or why a feature works.
- **[`CLAUDE.md`](./CLAUDE.md)** — process notes, build gotchas, and
  session-by-session history for anyone (human or AI) picking up work
  on this codebase.

## Fan project note

RoboRally is a board game by Richard Garfield, published by Wizards of the
Coast / Avalon Hill and Renegade Game Studios. Robot Rampage is a non-commercial
personal project with its own name, art and board layouts.
