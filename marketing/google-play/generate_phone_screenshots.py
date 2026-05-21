#!/usr/bin/env python3
"""Generate 8 English Google Play portrait screenshots from real LumaNox captures."""
from __future__ import annotations

import os
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1080, 1920
OUT_DIR = Path(__file__).resolve().parent / "phone_screenshots_en"
SNAPSHOT_DIR = Path(__file__).resolve().parents[1] / "snapshot-Android"
AVATAR_PATH = Path(__file__).resolve().parent / "virtual_avatar_lumanox.png"

BG_TOP = (7, 10, 16)
BG_BOTTOM = (11, 19, 34)
PANEL = (14, 24, 39)
PANEL_2 = (16, 28, 46)
STROKE = (35, 50, 74)
BLUE = (74, 158, 255)
BLUE_SOFT = (183, 215, 255)
GOLD = (232, 197, 71)
GOLD_SOFT = (255, 224, 138)
TEAL = (91, 192, 212)
TEXT = (234, 241, 255)
MUTED = (142, 162, 192)
DEEP = (3, 6, 11)


SLIDES = [
    {
        "file": "应用市场营销截图制作 (1).png",
        "out": "01_encrypted_vault.png",
        "eyebrow": "ENCRYPTED VAULT",
        "title": "Keep private photos locked",
        "body": "AES-256 local encryption helps protect photos and videos on your device.",
        "accent": GOLD,
        "patch_title": True,
    },
    {
        "file": "应用市场营销截图制作 (2).png",
        "out": "02_pin_biometric_unlock.png",
        "eyebrow": "PIN & BIOMETRICS",
        "title": "Unlock only when it is you",
        "body": "Protect every return with your app PIN and supported biometric unlock.",
        "accent": BLUE,
    },
    {
        "file": "应用市场营销截图制作 (3).png",
        "out": "03_backup_restore.png",
        "eyebrow": "BACKUP & RESTORE",
        "title": "Move safely between devices",
        "body": "Create encrypted local backups for migration and recovery.",
        "accent": TEAL,
    },
    {
        "file": "应用市场营销截图制作 (4).png",
        "out": "04_privacy_redaction.png",
        "eyebrow": "PRIVACY REDACTION",
        "title": "Hide sensitive areas before sharing",
        "body": "Blur, mosaic, or cover faces, cards, documents, and text.",
        "accent": GOLD,
    },
    {
        "file": "应用市场营销截图制作 (5).png",
        "out": "05_ai_sensitive_review.png",
        "eyebrow": "AI PRIVACY TOOLS",
        "title": "Review sensitive content locally",
        "body": "Find IDs, cards, QR codes, faces, and text without cloud upload.",
        "accent": GOLD,
    },
    {
        "file": "应用市场营销截图制作 (6).png",
        "out": "06_smart_cleanup.png",
        "eyebrow": "SMART CLEANUP",
        "title": "Keep your vault organized",
        "body": "Detect similar, duplicate, and blurry media so cleanup stays simple.",
        "accent": TEAL,
    },
    {
        "file": "应用市场营销截图制作 (7).png",
        "out": "07_private_camera.png",
        "eyebrow": "PRIVATE CAMERA",
        "title": "Capture straight into the vault",
        "body": "Take private photos and videos without leaving copies in public view.",
        "accent": BLUE,
    },
    {
        "file": "应用市场营销截图制作.png",
        "out": "08_offline_privacy.png",
        "eyebrow": "OFFLINE-FIRST PRIVACY",
        "title": "No cloud required",
        "body": "LumaNox is designed to run locally. Your vault stays under your control.",
        "accent": GOLD,
    },
]


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Helvetica Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Helvetica.ttf",
        "/System/Library/Fonts/SFNS.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if path and os.path.isfile(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


F_EYEBROW = font(25, True)
F_TITLE = font(62, True)
F_BODY = font(31)
F_CHIP = font(24, True)
F_BRAND = font(31, True)
F_SCREEN_TITLE = font(41, True)


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


def vertical_gradient() -> Image.Image:
    strip = Image.new("RGB", (1, H))
    px = strip.load()
    for y in range(H):
        t = y / (H - 1)
        px[0, y] = tuple(int(BG_TOP[i] * (1 - t) + BG_BOTTOM[i] * t) for i in range(3))
    return strip.resize((W, H), Image.Resampling.BILINEAR)


def add_brand_glow(img: Image.Image, accent: tuple[int, int, int]) -> None:
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-180, -210, 560, 530), fill=(*BLUE, 34))
    gd.ellipse((540, 420, 1330, 1210), fill=(*accent, 20))
    gd.ellipse((-230, 1270, 470, 2040), fill=(*TEAL, 16))
    glow = glow.filter(ImageFilter.GaussianBlur(70))
    img.alpha_composite(glow)


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def abstract_tile(size: tuple[int, int], accent: tuple[int, int, int], seed: int) -> Image.Image:
    w, h = size
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    top = (18 + seed * 7 % 18, 32 + seed * 5 % 18, 54 + seed * 3 % 28)
    bottom = tuple(max(0, min(255, accent[i] // 2 + seed * 9 % 30)) for i in range(3))
    for y in range(h):
        t = y / max(h - 1, 1)
        color = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
        d.line((0, y, w, y), fill=color)
    d.ellipse((w * 0.55, -h * 0.15, w * 1.22, h * 0.45), fill=(*accent, 38))
    d.rounded_rectangle((w * 0.14, h * 0.58, w * 0.86, h * 0.72), radius=max(5, w // 22), fill=(255, 255, 255, 36))
    d.rounded_rectangle((w * 0.23, h * 0.78, w * 0.70, h * 0.88), radius=max(4, w // 28), fill=(255, 255, 255, 22))
    return img


def paste_rounded(base: Image.Image, overlay: Image.Image, box: tuple[int, int, int, int], radius: int) -> None:
    x0, y0, x1, y1 = box
    resized = overlay.resize((x1 - x0, y1 - y0), Image.Resampling.LANCZOS)
    base.paste(resized, (x0, y0), rounded_mask(resized.size, radius))


def avatar_crop(size: tuple[int, int]) -> Image.Image:
    avatar = Image.open(AVATAR_PATH).convert("RGBA")
    w, h = size
    scale = max(w / avatar.width, h / avatar.height)
    resized = avatar.resize((int(avatar.width * scale), int(avatar.height * scale)), Image.Resampling.LANCZOS)
    cx = (resized.width - w) // 2
    cy = (resized.height - h) // 2
    return resized.crop((cx, cy, cx + w, cy + h))


def mosaic_avatar(size: tuple[int, int]) -> Image.Image:
    base = avatar_crop(size)
    d = ImageDraw.Draw(base)
    face_box = (size[0] // 2 - 118, size[1] // 2 - 150, size[0] // 2 + 118, size[1] // 2 + 92)
    crop = base.crop(face_box).resize((18, 18), Image.Resampling.BILINEAR).resize((face_box[2] - face_box[0], face_box[3] - face_box[1]), Image.Resampling.NEAREST)
    base.paste(crop, face_box)
    d.rounded_rectangle(face_box, radius=18, outline=GOLD, width=5)
    return base


def anonymize_people(screen: Image.Image, source_name: str) -> Image.Image:
    """Remove real-person captures from marketing screenshots while preserving UI context."""
    fixed = screen.copy().convert("RGBA")
    if source_name == "应用市场营销截图制作 (1).png":
        d = ImageDraw.Draw(fixed)
        d.rectangle((0, 680, 1080, 1794), fill=(7, 13, 24))
        d.rounded_rectangle((128, 690, 954, 1268), radius=42, fill=(10, 21, 35), outline=(35, 50, 74), width=3)
        d.rounded_rectangle((158, 742, 486, 1162), radius=42, fill=(3, 8, 14), outline=(35, 50, 74), width=3)
        paste_rounded(fixed, avatar_crop((278, 250)), (183, 778, 461, 1028), 28)
        d.text((184, 1062), "Private", font=font(38, True), fill=(248, 250, 252))
        d.text((184, 1122), "3 photos", font=font(27), fill=(183, 215, 255))
        d.rounded_rectangle((540, 742, 870, 1162), radius=42, fill=(6, 17, 30), outline=(35, 50, 74), width=3)
        d.text((675, 860), "+", font=font(76), fill=(183, 215, 255))
        d.text((590, 1062), "Create album", font=font(36), fill=(248, 250, 252))

        d.rounded_rectangle((128, 1290, 954, 1782), radius=42, fill=(10, 21, 35), outline=(35, 50, 74), width=3)
        d.text((166, 1340), "Recent", font=font(40, True), fill=(248, 250, 252))
        d.text((746, 1340), "View more", font=font(31), fill=(183, 215, 255))
        recent_boxes = [
            (168, 1428, 366, 1644),
            (418, 1428, 616, 1644),
            (668, 1428, 866, 1644),
        ]
        for idx, box in enumerate(recent_boxes, start=1):
            tile = avatar_crop((box[2] - box[0], box[3] - box[1])) if idx == 1 else abstract_tile((box[2] - box[0], box[3] - box[1]), BLUE if idx % 2 else TEAL, idx)
            paste_rounded(fixed, tile, box, 28)
    elif source_name == "应用市场营销截图制作 (4).png":
        # Main redaction preview: replace the real portrait with a synthetic privacy illustration.
        box = (44, 190, 1035, 1578)
        art = mosaic_avatar((box[2] - box[0], box[3] - box[1]))
        overlay = Image.new("RGBA", art.size, (13, 22, 35, 120))
        art.alpha_composite(overlay)
        d = ImageDraw.Draw(art)
        d.rounded_rectangle((120, 110, art.width - 120, art.height - 130), radius=42, outline=(58, 79, 112), width=4)
        d.rounded_rectangle((art.width // 2 - 178, art.height // 2 - 190, art.width // 2 + 178, art.height // 2 + 18), radius=28, fill=(*GOLD, 172))
        d.text((art.width // 2 - 104, art.height // 2 + 74), "Redacted", font=font(46, True), fill=(236, 245, 255))
        paste_rounded(fixed, art, box, 18)
    return fixed


def replace_lumavault_title(screen: Image.Image) -> Image.Image:
    """Patch old capture title text so every visible brand mention says LumaNox."""
    fixed = screen.copy().convert("RGBA")
    d = ImageDraw.Draw(fixed)
    # Matches the dark top bar area on the captured 1080x2160 screenshots.
    x0, y0, x1, y1 = 40, 104, 390, 190
    for x in range(x0, x1):
        t = (x - x0) / max(x1 - x0 - 1, 1)
        color = tuple(int((7, 13, 24)[i] * (1 - t) + (11, 22, 40)[i] * t) for i in range(3))
        d.line((x, y0, x, y1), fill=color)
    d.text((48, 114), "LumaNox", font=F_SCREEN_TITLE, fill=TEXT)
    return fixed


def phone_frame(screen: Image.Image, patch_title: bool = False) -> Image.Image:
    if patch_title:
        screen = replace_lumavault_title(screen)

    phone_w, phone_h = 664, 1328
    outer_w, outer_h = phone_w + 42, phone_h + 42
    frame = Image.new("RGBA", (outer_w, outer_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    shadow = Image.new("RGBA", (outer_w + 80, outer_h + 80), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((40, 40, outer_w + 40, outer_h + 40), radius=58, fill=(0, 0, 0, 132))
    shadow = shadow.filter(ImageFilter.GaussianBlur(24))
    base = Image.new("RGBA", shadow.size, (0, 0, 0, 0))
    base.alpha_composite(shadow)
    base.alpha_composite(frame, (40, 40))

    d.rounded_rectangle((0, 0, outer_w - 1, outer_h - 1), radius=58, fill=(4, 8, 15), outline=(49, 65, 92), width=3)
    d.rounded_rectangle((20, 20, outer_w - 21, outer_h - 21), radius=42, fill=DEEP)

    fitted = Image.new("RGBA", (phone_w, phone_h), (0, 0, 0, 0))
    source = screen.convert("RGBA")
    scale = max(phone_w / source.width, phone_h / source.height)
    resized = source.resize((int(source.width * scale), int(source.height * scale)), Image.Resampling.LANCZOS)
    crop_x = max(0, (resized.width - phone_w) // 2)
    crop_y = max(0, (resized.height - phone_h) // 2)
    fitted.alpha_composite(resized.crop((crop_x, crop_y, crop_x + phone_w, crop_y + phone_h)))
    frame.paste(fitted, (21, 21), rounded_mask((phone_w, phone_h), 38))

    base.alpha_composite(frame, (40, 40))
    return base


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


def draw_brand_mark(draw: ImageDraw.ImageDraw, x: int, y: int, accent: tuple[int, int, int]) -> None:
    draw.rounded_rectangle((x, y, x + 196, y + 52), radius=18, fill=(18, 31, 49), outline=STROKE)
    draw.ellipse((x + 18, y + 14, x + 42, y + 38), fill=accent)
    draw.text((x + 54, y + 12), "LumaNox", font=F_BRAND, fill=TEXT)


def build_slide(spec: dict[str, object]) -> Image.Image:
    screen = Image.open(SNAPSHOT_DIR / str(spec["file"])).convert("RGBA")
    screen = anonymize_people(screen, str(spec["file"]))
    accent = spec["accent"]  # type: ignore[assignment]

    img = vertical_gradient().convert("RGBA")
    add_brand_glow(img, accent)  # type: ignore[arg-type]
    draw = ImageDraw.Draw(img)

    draw_brand_mark(draw, 58, 62, accent)  # type: ignore[arg-type]

    y = 168
    draw.text((64, y), str(spec["eyebrow"]), font=F_EYEBROW, fill=accent)  # type: ignore[arg-type]
    y += 54
    title_lines = wrap_text(draw, str(spec["title"]), F_TITLE, 900)
    y = draw_lines(draw, (60, y), title_lines, F_TITLE, TEXT, 12)
    y += 22
    body_lines = wrap_text(draw, str(spec["body"]), F_BODY, 900)
    draw_lines(draw, (63, y), body_lines, F_BODY, MUTED, 9)

    # The phone sits low enough to show real UI while leaving generous copy space above.
    framed = phone_frame(screen, bool(spec.get("patch_title", False)))
    img.alpha_composite(framed, ((W - framed.width) // 2, 474))

    return img.convert("RGB")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for spec in SLIDES:
        out = build_slide(spec)
        path = OUT_DIR / str(spec["out"])
        out.save(path, "PNG", optimize=True)
        print(path, out.size)


if __name__ == "__main__":
    main()
