"""Extracts the inline SVGs of the Claude Design canvas sheets into standalone, named SVG files.

Usage: python extract_svgs.py <folder with the *.dc.html files> <output folder>

Each sheet has its own output sub-folder (board, cards, controls, status). A file is named
<index>-<label>.svg, where the label is the caption the designer placed after the drawing; drawings without
a caption of their own (a floor square under an overlay, an icon inside a button) get "unnamed".
"""
import os
import re
import sys

SHEETS = [("Sheet-Board.dc.html", "board"), ("Sheet-Cards.dc.html", "cards"),
          ("Sheet-Controls.dc.html", "controls"), ("Sheet-Status.dc.html", "status")]
SVG_RE = re.compile(r"<svg\b.*?</svg>", re.S)
LABEL_RE = re.compile(r"font-size:(?:16|15|13)px[^>]*font-weight:(?:700|600)[^>]*>([^<]{2,40})</div>")


def slug(text):
    text = text.lower().replace("\u00d7", "x").replace("'", "")
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-")


def extract(source_dir, output_dir):
    for sheet, kind in SHEETS:
        html = open(os.path.join(source_dir, sheet), encoding="utf-8").read()
        os.makedirs(os.path.join(output_dir, kind), exist_ok=True)
        matches = list(SVG_RE.finditer(html))
        for index, match in enumerate(matches):
            svg = match.group(0)
            if "xmlns=" not in svg.split(">")[0]:
                # Inline SVG in HTML has no namespace; a standalone SVG file needs one.
                svg = svg.replace("<svg", '<svg xmlns="http://www.w3.org/2000/svg"', 1)
            end = matches[index + 1].start() if index + 1 < len(matches) else len(html)
            label = LABEL_RE.search(html[match.end():end])
            name = "%02d-%s.svg" % (index, slug(label.group(1)) if label else "unnamed")
            with open(os.path.join(output_dir, kind, name), "w", encoding="utf-8", newline="\n") as out:
                out.write(svg)
        print(kind, len(matches))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    extract(sys.argv[1], sys.argv[2])
