#!/usr/bin/env python3
"""Google Play feature graphic: 1024 x 500 PNG (top banner)."""
from __future__ import annotations

import os
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
BG_LEFT = (14, 18, 28)
BG_RIGHT = (32, 42, 62)
ACCENT_GOLD = (232, 196, 84)
ACCENT_TEAL = (45, 212, 191)
MUTED = (148, 163, 184)
WHITE = (248, 250, 252)

OUT_PATH = Path(__file__).resolve().parent / "feature_graphic_1024x500.png"


def font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in (
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        if os.path.isfile(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def horizontal_gradient() -> Image.Image:
    strip = Image.new("RGB", (W, 1))
    px = strip.load()
    for x in range(W):
        t = x / max(W - 1, 1)
        r = int(BG_LEFT[0] * (1 - t) + BG_RIGHT[0] * t)
        g = int(BG_LEFT[1] * (1 - t) + BG_RIGHT[1] * t)
        b = int(BG_LEFT[2] * (1 - t) + BG_RIGHT[2] * t)
        px[x, 0] = (r, g, b)
    return strip.resize((W, H), Image.Resampling.BILINEAR)


def soft_glows(img: Image.Image) -> None:
    rnd = random.Random(42)
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    for _ in range(5):
        cx = rnd.randint(W // 2 - 80, W - 120)
        cy = rnd.randint(80, H - 80)
        r = rnd.randint(50, 160)
        a = rnd.randint(10, 35)
        od.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(*ACCENT_GOLD, a))
    img.alpha_composite(overlay)


def shield_shape(draw: ImageDraw.ImageDraw, cx: int, cy: int, scale: float) -> None:
    s = scale
    pts = [
        (cx, cy - int(95 * s)),
        (cx + int(78 * s), cy - int(45 * s)),
        (cx + int(78 * s), cy + int(35 * s)),
        (cx, cy + int(88 * s)),
        (cx - int(78 * s), cy + int(35 * s)),
        (cx - int(78 * s), cy - int(45 * s)),
    ]
    draw.polygon(pts, outline=ACCENT_GOLD, width=max(2, int(3 * s)))
    draw.line([(cx, cy - int(55 * s)), (cx, cy + int(45 * s))], fill=ACCENT_TEAL, width=max(2, int(3 * s)))
    draw.line([(cx - int(28 * s), cy), (cx + int(28 * s), cy)], fill=ACCENT_TEAL, width=max(2, int(3 * s)))


def main() -> None:
    f_brand = font(46)
    f_sub = font(22)
    f_tag = font(19)
    f_chip = font(16)

    img = horizontal_gradient().convert("RGBA")
    soft_glows(img)
    draw = ImageDraw.Draw(img)

    draw.text((40, 88), "LumaNox", font=f_brand, fill=WHITE)
    draw.text((40, 152), "Private Photo Vault", font=f_sub, fill=ACCENT_TEAL)
    draw.text((40, 210), "Keep the light  ·  ", font=f_tag, fill=MUTED)
    draw.text((40, 238), "Guard the private", font=f_tag, fill=ACCENT_GOLD)

    chip_y = 310
    draw.rounded_rectangle((40, chip_y, 340, chip_y + 36), radius=10, fill=(28, 38, 56))
    draw.text((52, chip_y + 8), "Encrypted · On-device · No ads", font=f_chip, fill=MUTED)

    shield_shape(draw, W - 200, H // 2 + 10, 0.55)

    rgb = img.convert("RGB")
    rgb.save(OUT_PATH, "PNG", optimize=True)
    print(OUT_PATH, rgb.size)


if __name__ == "__main__":
    main()
