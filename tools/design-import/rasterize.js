// Rasterizes every SVG below <svg folder> to PNG below <png folder>, keeping the folder structure.
// Usage: node rasterize.js <svg folder> <png folder> [scale | --width N]
const { Resvg } = require('@resvg/resvg-js');
const fs = require('fs');
const path = require('path');

const [, , svgRoot, pngRoot, option, value] = process.argv;
if (!svgRoot || !pngRoot) {
  console.error('Usage: node rasterize.js <svg folder> <png folder> [scale | --width N]');
  process.exit(1);
}
const fontDir = path.join(__dirname, '..', '..', 'assets', 'fonts');
const fontFiles = fs.readdirSync(fontDir).filter((f) => f.endsWith('.ttf')).map((f) => path.join(fontDir, f));
let fitTo = { mode: 'zoom', value: 1 };
if (option === '--width') fitTo = { mode: 'width', value: parseInt(value, 10) };
else if (option) fitTo = { mode: 'zoom', value: parseFloat(option) };

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full);
    else if (entry.name.endsWith('.svg')) {
      const out = path.join(pngRoot, path.relative(svgRoot, full)).replace(/\.svg$/, '.png');
      fs.mkdirSync(path.dirname(out), { recursive: true });
      const svg = fs.readFileSync(full, 'utf8');
      const png = new Resvg(svg, { fitTo, font: { fontFiles, loadSystemFonts: false, defaultFontFamily: 'Bungee' } })
        .render().asPng();
      fs.writeFileSync(out, png);
    }
  }
}
walk(svgRoot);
console.log('done');
