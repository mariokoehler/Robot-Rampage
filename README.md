# Robot Rampage

An online multiplayer adaptation of the board game **RoboRally**: program your
robot with five cards, then watch every robot's program play out — conveyor
belts, gears, lasers and pits included — as you race to touch the flags in order.
Built on [libGDX](https://libgdx.com/) with a dedicated authoritative server.

**Game design and technical architecture live in [`design.md`](./design.md)** —
the source of truth for how and why everything works. `CLAUDE.md` holds working
notes for the AI-assisted development process.

> [!WARNING]
> **This project is a work in progress and is not playable yet.** So far the
> repository contains only the project skeleton (a client window that shows a
> title, and a server that starts) and the game design document. There is no
> gameplay, no release and no stable API. Everything, including the design,
> may change without notice.

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

## Repository layout

| Path | Contents |
|---|---|
| `core/` | Shared code: rules engine, board format, network layer, client screens |
| `lwjgl3/` | Desktop client launcher |
| `server/` | Dedicated headless server |
| `assets/` | Game assets loaded at runtime |
| `assets-raw/` | Raw source assets, not loaded by the game |

## Fan project note

RoboRally is a board game by Richard Garfield, published by Wizards of the
Coast / Avalon Hill and Renegade Game Studios. Robot Rampage is a non-commercial
personal project with its own name, art and board layouts.
