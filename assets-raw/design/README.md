# Design assets (from the Claude Design canvas)

Vector art taken straight from the game's screen mockups
(`https://claude.ai/artifact/QdtnZisPjfGof2Uvsn8iqT`) and the robot designs of the design system
(`https://claude.ai/artifact/B6rnPgeQteFmVd6PCSMu63`). Colours are the design tokens, baked in. Derived with
`tools/design-import` (see there); the mockups remain the source of truth, so re-derive rather than hand-edit.

| Folder | Contents | How the game uses it |
|---|---|---|
| `tiles/` | floor, belt, express belt, gears (cw/ccw), pit, repair site (128 px) | Ground of a square. Belts are drawn pointing **east**; the client rotates them for other directions. |
| `objects/` | flags 1-3, start square, crusher, pusher | Things on a square. The numbers (flag, seat, pusher registers) are baked into these samples; the client draws them itself for other numbers. |
| `edges/` | wall, board laser | Sit on the edge of a square. |
| `overlays/` | archive marker, robot facing wedge + seat badge, program preview, damage tag, highlight | Reference drawings of things the client draws in code (they carry dynamic numbers and colours). |
| `cards/` | the seven card icons (112 px) | Card faces; the card frame, priority and name are drawn by the client. |
| `icons/` | lock, tick, chevron, close, settings, play, pause, power, host crown, flag, info, warning, eye, wifi | UI icons, drawn white or ink; tint them at runtime. |
| `robots/` | the eight robots, one per seat (128 px) | Bodies without facing; the client draws the facing wedge and seat badge. |

Not derived: audio (none exists yet), the window/application icon, a logo (the design uses plain type).
