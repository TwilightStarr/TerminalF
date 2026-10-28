#!/usr/bin/env python3
"""Generates icon.svg and the default PNG launcher icons (legacy square/round + monochrome).

Design (1024-unit square): a near-black rounded tile with an "F" made of three bars that fade from
white to gray. Geometry and colours live in tools/logo_common.py.

Run: python3 tools/gen_logo_assets.py     (needs Pillow + numpy)
"""
import os

import numpy as np
from PIL import Image, ImageDraw

import logo_common as L

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")
ADAPTIVE_SCALE = 0.92   # keeps the F inside the adaptive-icon safe zone
SS = 4                  # supersampling


def svg_text(stops=None):
    stops = stops or L.SILVER_RAMP
    defs, paths = [], []
    for i, (d, (x1, y1, x2, y2)) in enumerate(zip(L.BARS, L.AXES)):
        defs.append(
            f'    <linearGradient id="f{i + 1}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'
            f'<stop offset="0" stop-color="{stops[i * 2]}"/><stop offset="1" stop-color="{stops[i * 2 + 1]}"/></linearGradient>'
        )
        paths.append(f'  <path d="{d}" fill="url(#f{i + 1})"/>')
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="512" height="512">\n'
        "  <defs>\n" + "\n".join(defs) + "\n  </defs>\n"
        f'  <rect width="1024" height="1024" rx="{L.TILE_RADIUS}" fill="{L.TILE}"/>\n'
        + "\n".join(paths) + "\n</svg>\n"
    )


def gradient(size, c0, c1, axis, k, scale):
    x1, y1, x2, y2 = axis
    # transform axis like the geometry (scale around the centre)
    def tx(v):
        return (512 + (v - 512) * scale) * k
    x1, y1, x2, y2 = tx(x1), tx(y1), tx(x2), tx(y2)
    dx, dy = x2 - x1, y2 - y1
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    t = np.clip(((xs - x1) * dx + (ys - y1) * dy) / (dx * dx + dy * dy), 0, 1)[..., None]
    a, b = np.array(L.hex_rgb(c0), float), np.array(L.hex_rgb(c1), float)
    rgb = a + (b - a) * t
    return Image.fromarray(np.concatenate([rgb, np.full((size, size, 1), 255.0)], 2).round().astype(np.uint8), "RGBA")


def glyph(big, scale=1.0, mono=False, stops=None):
    stops = stops or L.SILVER_RAMP
    k = big / 1024.0
    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    for i, d in enumerate(L.BARS):
        pts = [((512 + (x - 512) * scale) * k, (512 + (y - 512) * scale) * k) for x, y in L.path_points(d)]
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).polygon(pts, fill=255)
        if mono:
            fill = Image.new("RGBA", (big, big), (255, 255, 255, 255))
        else:
            fill = gradient(big, stops[i * 2], stops[i * 2 + 1], L.AXES[i], k, scale)
        out.paste(fill, (0, 0), mask)
    return out


def tile(size, shape):
    big = size * SS
    k = big / 1024.0
    base = Image.new("RGBA", (big, big), L.hex_rgb(L.TILE) + (255,))
    if shape == "round":
        base = Image.alpha_composite(base, glyph(big, 0.9))
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    else:
        base = Image.alpha_composite(base, glyph(big, 1.0))
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=L.TILE_RADIUS * k, fill=255)
    base.putalpha(mask)
    return base.resize((size, size), Image.LANCZOS)


def save(img, rel):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def main():
    with open(os.path.join(ROOT, "icon.svg"), "w", encoding="utf-8") as f:
        f.write(svg_text())
    for name, d in {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}.items():
        legacy, adaptive = int(round(48 * d)), int(round(108 * d))
        save(tile(legacy, "squircle"), f"mipmap-{name}/ic_launcher.png")
        save(tile(legacy, "round"), f"mipmap-{name}/ic_launcher_round.png")
        big = adaptive * SS
        save(glyph(big, ADAPTIVE_SCALE, mono=True).resize((adaptive, adaptive), Image.LANCZOS),
             f"mipmap-{name}/ic_launcher_monochrome.png")
    print("Logo assets generated.")


if __name__ == "__main__":
    main()
