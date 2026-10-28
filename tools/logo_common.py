"""Shared geometry and colour logic for the TerminalF logo (a rounded dark tile with an "F" made of
three bars that fade from light to dark). Keep in sync with:
  - app/src/main/java/com/terminalf/app/ui/components/AppLogo.kt  (in-app logo)
  - app/src/main/java/com/terminalf/app/core/Palettes.kt          (`logoRamp`, `LogoRamp`)
All coordinates are in a 1024-unit square.
"""
import colorsys
import re

TILE = "#0A0B0D"          # LOGO_TILE
TILE_RADIUS = 229
SILVER_TINT = "#C4C7CD"   # LogoStyle.SILVER_TINT

BARS = [
    "M318,286.6 C318,261.7 330.8,250 358,250 L714,250 C732.3,250 743.8,266.2 735.3,287.3 "
    "L703.5,366.5 C692,395.1 679.7,400 648.2,400 L354.8,400 C327.6,400 318,386.4 318,359.9 Z",
    "M318,466.5 C318,441.5 330.7,430 358.2,430 L627.8,430 C647.2,430 658.7,445.8 651.1,467.8 "
    "L623.9,546 C614.5,572.8 602.8,580 571.6,580 L354.4,580 C328.1,580 318,566.7 318,539.8 Z",
    "M318,644 C318,624.7 332.4,610 350,610 L436,610 C455.6,610 470,624.4 470,641.2 "
    "L470,672.2 C470,694.3 473.3,707.7 453.3,732.2 L442.6,745.4 C419.3,774 390.9,774 372.3,774 "
    "L358.6,774 C328.3,774 318,761.8 318,733.8 Z",
]
# Gradient axes per bar: (x1, y1, x2, y2)
AXES = [(318, 250, 738, 400), (318, 430, 654, 580), (318, 610, 470, 774)]

SILVER_RAMP = ["#FDFDFE", "#DFE1E5", "#CDD0D6", "#A1A5AD", "#8A8E96", "#60646C"]
LEVELS = [0.97, 0.86, 0.78, 0.64, 0.56, 0.40]
SATURATION = [0.30, 0.45, 0.65, 0.80, 0.90, 1.00]


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rgb_hex(rgb):
    return "#%02X%02X%02X" % tuple(int(round(max(0, min(255, v)))) for v in rgb)


def _hsl(h):
    r, g, b = (v / 255 for v in hex_rgb(h))
    hh, l, s = colorsys.rgb_to_hls(r, g, b)
    return hh * 360, s, l


def _from_hsl(h, s, l):
    r, g, b = colorsys.hls_to_rgb((h % 360) / 360, l, s)
    return rgb_hex((r * 255, g * 255, b * 255))


def _mix(a, b, t):
    ra, rb = hex_rgb(a), hex_rgb(b)
    return rgb_hex(tuple(x + (y - x) * t for x, y in zip(ra, rb)))


def ramp(colors):
    """Six gradient stops (top/middle/bottom bar, start+end) for 1-2 tint colours (mirrors logoRamp)."""
    if not colors:
        return list(SILVER_RAMP)
    first, last = colors[0], colors[-1]
    if _hsl(first)[1] < 0.12 and _hsl(last)[1] < 0.12:
        return list(SILVER_RAMP)
    out = []
    for i in range(6):
        tint = _mix(first, last, (i // 2) / 2)
        h, s, _ = _hsl(tint)
        out.append(_from_hsl(h, max(s, 0.6) * SATURATION[i], LEVELS[i]))
    return out


def presets(palettes_kt):
    """[(id, [hex colours])] for every LogoStyle preset with colours, read from Palettes.kt."""
    styles = []
    for m in re.finditer(r'LogoStyle\("(\w+)",\s*"[^"]*",\s*"[^"]*",\s*listOf\(([^)]*)\)\)', palettes_kt):
        cols = []
        for tok in re.findall(r"0xFF[0-9A-Fa-f]{6}|SILVER_TINT", m.group(2)):
            cols.append(SILVER_TINT if tok == "SILVER_TINT" else "#" + tok[4:].upper())
        if cols:
            styles.append((m.group(1), cols))
    return styles


def path_points(d, steps=24):
    """Flatten an absolute M/L/C/Z path into a polygon (list of (x, y))."""
    toks = re.findall(r"[MLCZ]|-?\d+\.?\d*", d)
    pts, i, cur = [], 0, (0.0, 0.0)
    cmd = None
    while i < len(toks):
        if toks[i] in "MLCZ":
            cmd = toks[i]
            i += 1
            if cmd == "Z":
                continue
        if cmd in ("M", "L"):
            cur = (float(toks[i]), float(toks[i + 1]))
            i += 2
            pts.append(cur)
        elif cmd == "C":
            p1 = (float(toks[i]), float(toks[i + 1]))
            p2 = (float(toks[i + 2]), float(toks[i + 3]))
            p3 = (float(toks[i + 4]), float(toks[i + 5]))
            i += 6
            for s in range(1, steps + 1):
                t = s / steps
                u = 1 - t
                pts.append((
                    u**3 * cur[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
                    u**3 * cur[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1],
                ))
            cur = p3
    return pts
