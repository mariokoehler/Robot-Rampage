# design-import

Turns the vector art of the **Claude Design canvas** into files the game can use. Two small dev-time steps;
neither runs during a normal build.

1. **Get the canvas.** With Claude Code, read the canvas artifact (`Artifact read` on
   `https://claude.ai/artifact/QdtnZisPjfGof2Uvsn8iqT`); its `project/*.dc.html` files are saved to a local folder.
2. **Extract the SVGs.** `python extract_svgs.py <folder with the .dc.html files> <output folder>` pulls every inline
   `<svg>` out of the four `Sheet-*.dc.html` files (board, cards, controls, status) and names it after the label the
   designer put next to it. The curated selection that was copied into `assets-raw/design/` is listed in
   `assets-raw/design/README.md`.
3. **Rasterize.** `npm install` once, then `node rasterize.js <svg folder> <png folder> [scale | --width N]` renders
   PNGs with [resvg](https://github.com/RazrFalcon/resvg-js), loading the project fonts from `assets/fonts` (flags,
   badges and pushers contain text).

The atlas packing that follows (libGDX `TexturePacker`, as in the StarWars project) is not built yet.
