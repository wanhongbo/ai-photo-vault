#!/usr/bin/env python3
"""Generate 6.9-inch iPhone App Store screenshots from apple-reg/111 captures."""
from __future__ import annotations

import os
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT_W, OUT_H = 1320, 2868
SOURCE_DIR = Path(os.environ.get("LUMANOX_SCREENSHOT_SOURCE_DIR", "/Users/wanhongbo/workspace/apple-reg/111"))
SCRIPT_DIR = Path(__file__).resolve().parent
OUT_DIR = SCRIPT_DIR / "iphone_6_9_screenshots_en_apple_reg_111"
ASSET_DIR = SCRIPT_DIR / "assets"
THUMBNAIL_SHEET = ASSET_DIR / "virtual_people_thumbnail_sheet.png"
GROUP_PHOTO = ASSET_DIR / "virtual_group_photo.png"

BG_TOP = (5, 8, 13)
BG_BOTTOM = (11, 19, 36)
CARD = (12, 21, 35)
STROKE = (42, 60, 84)
BLUE = (74, 158, 255)
TEAL = (33, 194, 119)
AMBER = (232, 197, 71)
TEXT = (234, 241, 255)
MUTED = (145, 164, 194)
DEEP = (3, 6, 11)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Helvetica Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Helvetica.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if path and os.path.isfile(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


F_KICKER = font(34, True)
F_TITLE = font(82, True)
F_BODY = font(40)
F_BRAND = font(32, True)


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def text_size(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont) -> tuple[int, int]:
    box = draw.textbbox((0, 0), text, font=fnt)
    return box[2] - box[0], box[3] - box[1]


def wrap_text(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont, max_width: int) -> list[str]:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = word if not current else f"{current} {word}"
        if text_size(draw, candidate, fnt)[0] <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def draw_lines(
    draw: ImageDraw.ImageDraw,
    xy: tuple[int, int],
    lines: Iterable[str],
    fnt: ImageFont.ImageFont,
    fill: tuple[int, int, int],
    line_gap: int,
) -> int:
    x, y = xy
    for line in lines:
        draw.text((x, y), line, font=fnt, fill=fill)
        y += text_size(draw, line, fnt)[1] + line_gap
    return y


def gradient(size: tuple[int, int], top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    w, h = size
    strip = Image.new("RGB", (1, h))
    px = strip.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        px[0, y] = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
    return strip.resize((w, h), Image.Resampling.BILINEAR)


def add_glow(img: Image.Image, accent: tuple[int, int, int]) -> None:
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse((-260, -280, 780, 770), fill=(*BLUE, 30))
    d.ellipse((700, 660, 1640, 1670), fill=(*accent, 22))
    d.ellipse((-240, 1900, 620, 3200), fill=(*TEAL, 12))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(90)))


def fit_cover(source: Image.Image, size: tuple[int, int]) -> Image.Image:
    w, h = size
    img = source.convert("RGBA")
    scale = max(w / img.width, h / img.height)
    resized = img.resize((int(img.width * scale), int(img.height * scale)), Image.Resampling.LANCZOS)
    x = max(0, (resized.width - w) // 2)
    y = max(0, (resized.height - h) // 2)
    return resized.crop((x, y, x + w, y + h))


def paste_rounded(base: Image.Image, overlay: Image.Image, box: tuple[int, int, int, int], radius: int) -> None:
    x0, y0, x1, y1 = box
    resized = overlay.convert("RGBA").resize((x1 - x0, y1 - y0), Image.Resampling.LANCZOS)
    base.paste(resized, (x0, y0), rounded_mask(resized.size, radius))


def thumbnail_tiles() -> list[Image.Image]:
    sheet = Image.open(THUMBNAIL_SHEET).convert("RGBA")
    tile = min(sheet.width, sheet.height) // 2
    boxes = [
        (0, 0, tile, tile),
        (tile, 0, tile * 2, tile),
        (0, tile, tile, tile * 2),
        (tile, tile, tile * 2, tile * 2),
    ]
    return [sheet.crop(box) for box in boxes]


def apply_mosaic(img: Image.Image, box: tuple[int, int, int, int], blocks: int = 7) -> None:
    x0, y0, x1, y1 = box
    region = img.crop(box).convert("RGBA")
    small_w = max(4, blocks)
    small_h = max(4, int(blocks * region.height / max(region.width, 1)))
    mosaic = region.resize((small_w, small_h), Image.Resampling.BOX).resize(region.size, Image.Resampling.NEAREST)
    mosaic = mosaic.filter(ImageFilter.GaussianBlur(0.35))
    mask = rounded_mask(region.size, max(8, min(region.size) // 4)).filter(ImageFilter.GaussianBlur(1.0))
    img.paste(mosaic, (x0, y0), mask)


def sanitize_capture(path: Path) -> Image.Image:
    img = Image.open(path).convert("RGBA")
    name = path.name
    if name == "IMG_7487.PNG.JPG":
        boxes = [
            (49, 530, 288, 744),
            (304, 530, 543, 744),
            (49, 812, 288, 1026),
            (304, 812, 543, 1026),
        ]
        for tile, box in zip(thumbnail_tiles(), boxes, strict=True):
            paste_rounded(img, tile, box, 24)
    elif name == "IMG_7492.PNG.JPG":
        tiles = thumbnail_tiles()
        boxes = [
            (48, 550, 160, 665),
            (48, 741, 160, 856),
            (48, 932, 160, 1047),
            (48, 1118, 160, 1234),
        ]
        d = ImageDraw.Draw(img)
        for index, box in enumerate(boxes):
            paste_rounded(img, tiles[index % len(tiles)], box, 16)
            badge = (box[0] + 8, box[1] + 8, box[0] + 78, box[1] + 42)
            d.rounded_rectangle(badge, radius=11, fill=(26, 28, 31, 232))
            d.text((badge[0] + 36, badge[1] + 7), "66" if index < 3 else "58", font=font(16, True), fill=TEXT)
            d.rounded_rectangle((badge[0] + 10, badge[1] + 9, badge[0] + 27, badge[1] + 26), radius=5, outline=AMBER, width=2)
    elif name == "IMG_7493.PNG.JPG":
        photo_box = (62, 303, 530, 654)
        group = Image.open(GROUP_PHOTO).convert("RGBA")
        paste_rounded(img, fit_cover(group, (photo_box[2] - photo_box[0], photo_box[3] - photo_box[1])), photo_box, 16)
        face_boxes = [
            (108, 435, 158, 493),
            (181, 394, 238, 459),
            (263, 358, 329, 432),
            (349, 369, 401, 436),
            (241, 454, 301, 520),
            (304, 453, 362, 520),
            (359, 460, 423, 529),
            (414, 431, 482, 505),
        ]
        for box in face_boxes:
            apply_mosaic(img, box, 6)
        d = ImageDraw.Draw(img)
        for box in face_boxes:
            d.rounded_rectangle(box, radius=10, outline=(232, 197, 71, 210), width=2)
    return img


def device_frame(screen: Image.Image) -> Image.Image:
    screen_w, screen_h = 980, 2119
    outer_w, outer_h = screen_w + 64, screen_h + 64
    shell = Image.new("RGBA", (outer_w + 96, outer_h + 96), (0, 0, 0, 0))
    shadow = Image.new("RGBA", shell.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((48, 48, outer_w + 48, outer_h + 48), radius=82, fill=(0, 0, 0, 150))
    shell.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(30)))

    frame = Image.new("RGBA", (outer_w, outer_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle((0, 0, outer_w - 1, outer_h - 1), radius=82, fill=(4, 8, 15), outline=(55, 76, 107), width=4)
    d.rounded_rectangle((30, 30, outer_w - 31, outer_h - 31), radius=62, fill=DEEP)
    fitted = screen.resize((screen_w, screen_h), Image.Resampling.LANCZOS)
    frame.paste(fitted, (32, 32), rounded_mask((screen_w, screen_h), 58))
    shell.alpha_composite(frame, (48, 48))
    return shell


SLIDES = [
    ("IMG_7487.PNG.JPG", "01_encrypted_vault.png", "ENCRYPTED VAULT", "Private albums,\nprotected locally", "AES-256 vault storage with offline media and virtual-safe thumbnails.", BLUE),
    ("IMG_7491.PNG.JPG", "02_pin_biometrics.png", "PIN & BIOMETRICS", "Unlock only\nwhen it is you", "Use a 6-digit PIN and supported biometrics to protect every return.", BLUE),
    ("IMG_7488.PNG.JPG", "03_local_ai_scan.png", "LOCAL AI SCAN", "Find sensitive items\non device", "Review hidden GPS, device metadata, IDs, and screenshots without upload.", AMBER),
    ("IMG_7493.PNG.JPG", "04_privacy_redact.png", "PRIVACY REDACTION", "Mosaic faces\nbefore sharing", "Auto-detect sensitive areas or draw your own redaction regions.", AMBER),
    ("IMG_7492.PNG.JPG", "05_sensitive_review_queue.png", "SENSITIVE REVIEW", "Process risky files\none by one", "Sort by risk, inspect candidates, and keep private review fully local.", AMBER),
    ("IMG_7490.PNG.JPG", "06_private_camera.png", "PRIVATE CAMERA", "Capture straight\ninto the vault", "Private photos and videos stay out of the public photo library.", BLUE),
    ("IMG_7494.PNG.JPG", "07_encrypted_backup.png", "ENCRYPTED BACKUP", "Backups you\ncontrol", "Choose a Files folder and keep encrypted backup.dat ready for restore.", TEAL),
    ("IMG_7489.PNG.JPG", "08_security_settings.png", "SECURITY SETTINGS", "Control every\nprivacy layer", "Subscription, unlock security, backup, storage, and support in one place.", TEAL),
]


def build_slide(spec: tuple[str, str, str, str, str, tuple[int, int, int]]) -> Image.Image:
    source_name, _out, kicker, title, body, accent = spec
    canvas = gradient((OUT_W, OUT_H), BG_TOP, BG_BOTTOM).convert("RGBA")
    add_glow(canvas, accent)
    d = ImageDraw.Draw(canvas)

    d.rounded_rectangle((72, 78, 274, 134), radius=20, fill=(18, 31, 49), outline=STROKE)
    d.ellipse((94, 96, 120, 122), fill=accent)
    d.text((134, 94), "LumaNox", font=F_BRAND, fill=TEXT)

    d.text((80, 212), kicker, font=F_KICKER, fill=accent)
    y = 268
    for line in title.splitlines():
        d.text((78, y), line, font=F_TITLE, fill=TEXT)
        y += 92
    y += 18
    draw_lines(d, (82, y), wrap_text(d, body, F_BODY, 1100), F_BODY, MUTED, 12)

    capture = sanitize_capture(SOURCE_DIR / source_name)
    framed = device_frame(capture)
    canvas.alpha_composite(framed, ((OUT_W - framed.width) // 2, 640))
    return canvas.convert("RGB")


def main() -> None:
    missing = [name for name, *_rest in SLIDES if not (SOURCE_DIR / name).exists()]
    if missing:
        raise SystemExit(f"Missing source screenshots: {', '.join(missing)}")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for stale in OUT_DIR.glob("*.png"):
        stale.unlink()
    for spec in SLIDES:
        out = build_slide(spec)
        path = OUT_DIR / spec[1]
        out.save(path, "PNG", optimize=True)
        print(path, out.size)


if __name__ == "__main__":
    main()
