#!/usr/bin/env python3
"""Generate 8 portrait PNG phone screenshots for Google Play (PC-friendly vertical assets)."""
from __future__ import annotations

import os
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# 9:16 portrait; long edge ≤ 3840 (Play limit)
W, H = 1080, 1920

BG_TOP = (12, 16, 24)
BG_BOTTOM = (28, 36, 52)
ACCENT_GOLD = (232, 196, 84)
ACCENT_TEAL = (45, 212, 191)
MUTED = (148, 163, 184)
WHITE = (248, 250, 252)

OUT_DIR = Path(__file__).resolve().parent / "phone_portrait_en"


def font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/SFNS.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if os.path.isfile(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def gradient_background() -> Image.Image:
    strip = Image.new("RGB", (1, H))
    px = strip.load()
    for y in range(H):
        t = y / max(H - 1, 1)
        r = int(BG_TOP[0] * (1 - t) + BG_BOTTOM[0] * t)
        g = int(BG_TOP[1] * (1 - t) + BG_BOTTOM[1] * t)
        b = int(BG_TOP[2] * (1 - t) + BG_BOTTOM[2] * t)
        px[0, y] = (r, g, b)
    return strip.resize((W, H), Image.Resampling.BILINEAR)


def glow_orbs(img: Image.Image, _draw: ImageDraw.ImageDraw, seed: int) -> None:
    rnd = random.Random(seed)
    for _ in range(6):
        cx = rnd.randint(80, W - 80)
        cy = rnd.randint(120, H - 400)
        r = rnd.randint(80, 220)
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay)
        gold = (*ACCENT_GOLD, rnd.randint(8, 28))
        od.ellipse((cx - r, cy - r, cx + r, cy + r), fill=gold)
        img.alpha_composite(overlay)


def status_bar(draw: ImageDraw.ImageDraw, f_small: ImageFont.ImageFont) -> None:
    draw.rounded_rectangle((48, 36, W - 48, 92), radius=20, fill=(20, 26, 36))
    draw.text((72, 48), "9:41", font=f_small, fill=MUTED)
    draw.text((W - 200, 48), "LTE  100%", font=f_small, fill=MUTED)


def card(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    title: str,
    subtitle: str | None,
    f_title: ImageFont.ImageFont,
    f_body: ImageFont.ImageFont,
) -> None:
    x0, y0, x1, y1 = box
    draw.rounded_rectangle((x0, y0, x1, y1), radius=28, fill=(22, 30, 44), outline=(55, 65, 85))
    draw.text((x0 + 36, y0 + 36), title, font=f_title, fill=WHITE)
    if subtitle:
        draw.text((x0 + 36, y0 + 92), subtitle, font=f_body, fill=MUTED)


def pill(draw: ImageDraw.ImageDraw, xy: tuple[int, int, int, int], text: str, f: ImageFont.ImageFont) -> None:
    x0, y0, x1, y1 = xy
    draw.rounded_rectangle((x0, y0, x1, y1), radius=22, fill=(35, 48, 68))
    draw.text((x0 + 24, y0 + 14), text, font=f, fill=ACCENT_TEAL)


def thumb_grid(draw: ImageDraw.ImageDraw, top: int) -> None:
    cols, rows, gap = 3, 3, 18
    side = (W - 120 - gap * (cols - 1)) // cols
    for r in range(rows):
        for c in range(cols):
            x = 60 + c * (side + gap)
            y = top + r * (side + gap)
            shade = 30 + (r * 3 + c * 5) % 25
            draw.rounded_rectangle((x, y, x + side, y + side), radius=16, fill=(shade, shade + 8, shade + 14))


def save(name: str, builder) -> None:
    img = Image.new("RGBA", (W, H))
    base = gradient_background().convert("RGBA")
    img.paste(base, (0, 0))
    draw = ImageDraw.Draw(img)
    builder(img, draw)
    rgb = img.convert("RGB")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / name
    rgb.save(path, "PNG", optimize=True)
    print(path, rgb.size)


def main() -> None:
    f_h1 = font(52)
    f_h2 = font(36)
    f_body = font(28)
    f_small = font(24)
    f_tiny = font(20)

    def slide01(img: Image.Image, draw: ImageDraw.ImageDraw) -> None:
        glow_orbs(img, draw, 1)
        status_bar(draw, f_small)
        draw.text((60, 200), "LumaVault", font=f_h1, fill=WHITE)
        draw.text((60, 280), "Private Photo Vault", font=f_h2, fill=ACCENT_TEAL)
        draw.text((60, 400), "Keep the light,", font=f_body, fill=MUTED)
        draw.text((60, 450), "Guard the private", font=f_body, fill=ACCENT_GOLD)
        pill(draw, (60, 560, 420, 620), "  Encrypted local vault  ", f_tiny)
        thumb_grid(draw, 720)
        draw.rounded_rectangle((60, 1680, W - 60, 1840), radius=24, fill=(30, 42, 62))
        draw.text((90, 1710), "Your memories stay on your device.", font=f_small, fill=MUTED)

    def slide02(img: Image.Image, draw: ImageDraw.ImageDraw) -> None:
        glow_orbs(img, draw, 2)
        status_bar(draw, f_small)
        draw.text((60, 180), "Unlock your vault", font=f_h1, fill=WHITE)
        draw.text((60, 270), "Biometric gate for every return.", font=f_body, fill=MUTED)
        cx, cy = W // 2, 720
        r = 140
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=ACCENT_GOLD, width=6)
        draw.ellipse((cx - r + 24, cy - r + 24, cx + r - 24, cy + r - 24), fill=(25, 33, 48))
        draw.text((cx - 120, cy - 20), "Touch ID", font=f_h2, fill=ACCENT_GOLD)
        card(draw, (60, 1040, W - 60, 1280), "Face unlock ready", "Fast, on-device matching — no cloud.", f_h2, f_body)
        draw.text((60, 1720), "Keep the light  ·  Guard the private", font=f_tiny, fill=MUTED)

    def slide03(img: Image.Image, draw: ImageDraw.ImageDraw) -> None:
        glow_orbs(img, draw, 3)
        status_bar(draw, f_small)
        draw.text((60, 180), "Albums", font=f_h1, fill=WHITE)
        draw.text((60, 260), "Organize what matters, hide what must stay private.", font=f_body, fill=MUTED)
        names = [("Camera", False), ("Imports", False), ("Hidden", True), ("Travel", False)]
        y = 360
        for title, locked in names:
            card(draw, (60, y, W - 60, y + 140), title, "128 items" if not locked else "12 items  ·  locked", f_h2, f_body)
            if locked:
                draw.rounded_rectangle((W - 200, y + 40, W - 100, y + 100), radius=16, fill=(55, 40, 20))
                draw.text((W - 180, y + 58), "LOCK", font=f_tiny, fill=ACCENT_GOLD)
            y += 170

    def slide04(img: Image.Image, draw: ImageDraw.ImageDraw) -> None:
        glow_orbs(img, draw, 4)
        status_bar(draw, f_small)
        draw.text((60, 180), "Import with confidence", font=f_h1, fill=WHITE)
        draw.text((60, 270), "Bring photos in — they land inside the vault.", font=f_body, fill=MUTED)
        card(draw, (60, 380, W - 60, 620), "From gallery", "Batch import · originals preserved", f_h2, f_body)
        card(draw, (60, 660, W - 60, 900), "Secure delete (optional)", "Remove copies from public gallery after import.", f_h2, f_body)
        draw.text((60, 1720), "LumaVault — Private Photo Vault", font=f_tiny, fill=MUTED)

    def slide05(img: Image.Image, draw: ImageDraw.ImageDraw) -> None:
        glow_orbs(img, draw, 5)
        status_bar(draw, f_small)
        draw.text((60, 180), "AI that respects privacy", font=f_h1, fill=WHITE)
        draw.text((60, 270), "On-device intelligence for safer browsing.", font=f_body, fill=MUTED)
        card(draw, (60, 380, W - 60, 640), "Smart highlights", "Find moments without sending pixels to the cloud.", f_h2, f_body)
        card(draw, (60, 680, W - 60, 940), "Sensitive-aware hints", "Optional blur cues before you share a screen.", f_h2, f_body)

    def slide06(img: Image.Image, draw: ImageDraw.ImageDraw) -> None:
        glow_orbs(img, draw, 6)
        status_bar(draw, f_small)
        draw.text((60, 180), "Security center", font=f_h1, fill=WHITE)
        rows = [
            ("Auto-lock", "30 seconds after background"),
            ("Screen capture", "Warn on vault screens"),
            ("Decoy PIN", "Optional secondary entry"),
        ]
        y = 340
        for t, s in rows:
            card(draw, (60, y, W - 60, y + 150), t, s, f_h2, f_body)
            y += 180
        draw.text((60, 1720), "Guard the private — layer by layer.", font=f_tiny, fill=MUTED)

    def slide07(img: Image.Image, draw: ImageDraw.ImageDraw) -> None:
        glow_orbs(img, draw, 7)
        status_bar(draw, f_small)
        draw.text((60, 180), "Midnight gallery", font=f_h1, fill=WHITE)
        draw.text((60, 270), "Low-glare browsing for night sessions.", font=f_body, fill=MUTED)
        thumb_grid(draw, 380)
        pill(draw, (60, 1180, 380, 1240), "  OLED-friendly dimmer  ", f_tiny)
        draw.text((60, 1720), "Keep the light — comfortably.", font=f_tiny, fill=MUTED)

    def slide08(img: Image.Image, draw: ImageDraw.ImageDraw) -> None:
        glow_orbs(img, draw, 8)
        status_bar(draw, f_small)
        draw.text((60, 200), "Designed to be trusted", font=f_h1, fill=WHITE)
        draw.text((60, 290), "LumaVault keeps keys and thumbnails", font=f_body, fill=MUTED)
        draw.text((60, 340), "under your control.", font=f_body, fill=MUTED)
        card(draw, (60, 460, W - 60, 720), "Encryption at rest", "Device keystore + file-level protection.", f_h2, f_body)
        card(draw, (60, 760, W - 60, 1020), "No ad profiling", "We do not sell your library to data brokers.", f_h2, f_body)
        draw.rounded_rectangle((60, 1120, W - 60, 1320), radius=28, outline=ACCENT_GOLD, width=2)
        draw.text((90, 1160), "Keep the light,", font=f_h2, fill=WHITE)
        draw.text((90, 1220), "Guard the private.", font=f_h2, fill=ACCENT_GOLD)

    slides = [
        ("01_hero_welcome.png", slide01),
        ("02_biometric_unlock.png", slide02),
        ("03_albums_private.png", slide03),
        ("04_import_secure.png", slide04),
        ("05_ai_privacy.png", slide05),
        ("06_security_center.png", slide06),
        ("07_dark_gallery.png", slide07),
        ("08_trust_encryption.png", slide08),
    ]
    for name, fn in slides:
        save(name, fn)


if __name__ == "__main__":
    main()
