// Renders the board sprites the client draws on top of the ground tiles: the SVGs are stripped of their floor background
// and of any numbers (the client draws numbers with the game fonts), then rasterized with a transparent background.
// Usage: node make-board-sprites.js   (reads assets-raw/design, writes assets/board)
const { Resvg } = require('@resvg/resvg-js');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..');
const source = path.join(root, 'assets-raw', 'design');
const target = path.join(root, 'assets', 'board');
const fontDir = path.join(root, 'assets', 'fonts');
const fontFiles = fs.readdirSync(fontDir).filter((f) => f.endsWith('.ttf')).map((f) => path.join(fontDir, f));

// [svg below assets-raw/design, png name below assets/board]
const SPRITES = [
  ['objects/flag-1.svg', 'flag.png'],
  ['objects/start-square.svg', 'start-square.png'],
  ['objects/crusher.svg', 'crusher.png'],
  ['objects/pusher.svg', 'pusher.png'],
  ['edges/wall.svg', 'wall.png'],
  ['edges/laser-emitter.svg', 'laser-emitter.png'],
  ['overlays/robot-badge.svg', 'robot-badge.png'],
];
// assets/board/robot-wedge.png is NOT generated: the owner repainted it by hand (bright green arrow, so the facing is easy
// to read), and rendering overlays/robot-wedge.svg would overwrite it. Edit the PNG directly.

const BACKGROUND = /<rect x="0" y="0" width="128" height="128" fill="#(?:d8d1bd|c3ccca)" stroke="#c2b9a2" stroke-width="1"><\/rect>/;
const TEXT = /<text\b[^>]*>[^<]*<\/text>/g;

fs.mkdirSync(target, { recursive: true });
for (const [svgName, pngName] of SPRITES) {
  const svg = fs.readFileSync(path.join(source, svgName), 'utf8').replace(BACKGROUND, '').replace(TEXT, '');
  const png = new Resvg(svg, {
    fitTo: { mode: 'width', value: 128 },
    font: { fontFiles, loadSystemFonts: false, defaultFontFamily: 'Bungee' },
  }).render().asPng();
  fs.writeFileSync(path.join(target, pngName), png);
}
console.log('wrote ' + SPRITES.length + ' sprites to ' + target);
