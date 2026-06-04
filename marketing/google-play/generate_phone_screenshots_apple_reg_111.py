#!/usr/bin/env python3
"""Generate English marketing screenshots from the apple-reg/111 iOS captures."""
from __future__ import annotations

import os
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1080, 1920
DEFAULT_SOURCE_DIR = Path("/Users/wanhongbo/workspace/apple-reg/111")
OUT_DIR = Path(__file__).resolve().parent / "phone_screenshots_en_apple_reg_111"
AI_THUMBNAIL_SHEET = Path(__file__).resolve().parent / "assets" / "ai_album_thumbnails_sheet.png"

BG_TOP = (5, 8, 13)
BG_BOTTOM = (11, 19, 36)
STROKE = (34, 50, 71)
BLUE = (74, 158, 255)
TEAL = (33, 194, 119)
AMBER = (232, 197, 71)
TEXT = (234, 241, 255)
MUTED = (142, 162, 192)
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


F_BRAND = font(31, True)
F_EYEBROW = font(25, True)
F_TITLE = font(62, True)
F_BODY = font(31)


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


def vertical_gradient() -> Image.Image:
    strip = Image.new("RGB", (1, H))
    px = strip.load()
    for y in range(H):
        t = y / max(H - 1, 1)
        px[0, y] = tuple(int(BG_TOP[i] * (1 - t) + BG_BOTTOM[i] * t) for i in range(3))
    return strip.resize((W, H), Image.Resampling.BILINEAR)


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def add_brand_glow(img: Image.Image, accent: tuple[int, int, int]) -> None:
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-180, -230, 560, 530), fill=(*BLUE, 36))
    gd.ellipse((560, 390, 1340, 1200), fill=(*accent, 23))
    gd.ellipse((-220, 1320, 450, 2040), fill=(*TEAL, 14))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(74)))


def draw_brand_mark(draw: ImageDraw.ImageDraw, x: int, y: int, accent: tuple[int, int, int]) -> None:
    draw.rounded_rectangle((x, y, x + 196, y + 52), radius=18, fill=(18, 31, 49), outline=STROKE)
    draw.ellipse((x + 18, y + 14, x + 42, y + 38), fill=accent)
    draw.text((x + 54, y + 12), "LumaNox", font=F_BRAND, fill=TEXT)


def fit_image(source: Image.Image, size: tuple[int, int]) -> Image.Image:
    w, h = size
    img = source.convert("RGBA")
    scale = min(w / img.width, h / img.height)
    resized = img.resize((int(img.width * scale), int(img.height * scale)), Image.Resampling.LANCZOS)
    fitted = Image.new("RGBA", (w, h), DEEP)
    fitted.alpha_composite(resized, ((w - resized.width) // 2, (h - resized.height) // 2))
    return fitted


def paste_rounded(base: Image.Image, overlay: Image.Image, box: tuple[int, int, int, int], radius: int) -> None:
    x0, y0, x1, y1 = box
    resized = overlay.convert("RGBA").resize((x1 - x0, y1 - y0), Image.Resampling.LANCZOS)
    base.paste(resized, (x0, y0), rounded_mask(resized.size, radius))


def ai_album_tiles() -> list[Image.Image]:
    sheet = Image.open(AI_THUMBNAIL_SHEET).convert("RGBA")
    tile = min(sheet.width, sheet.height) // 2
    crops = [
        (0, 0, tile, tile),
        (tile, 0, tile * 2, tile),
        (0, tile, tile, tile * 2),
        (tile, tile, tile * 2, tile * 2),
    ]
    return [sheet.crop(box) for box in crops]


def sanitize_capture(screen: Image.Image, source_name: str) -> Image.Image:
    fixed = screen.convert("RGBA").copy()
    if source_name == "IMG_7487.PNG.JPG":
        boxes = [
            (49, 530, 288, 744),
            (304, 530, 543, 744),
            (49, 812, 288, 1026),
            (304, 812, 543, 1026),
        ]
        for tile, box in zip(ai_album_tiles(), boxes, strict=True):
            paste_rounded(fixed, tile, box, 24)
    return fixed


def phone_frame(screen: Image.Image) -> Image.Image:
    phone_w, phone_h = 664, 1328
    outer_w, outer_h = phone_w + 42, phone_h + 42
    shell = Image.new("RGBA", (outer_w + 80, outer_h + 80), (0, 0, 0, 0))
    shadow = Image.new("RGBA", shell.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((40, 40, outer_w + 40, outer_h + 40), radius=58, fill=(0, 0, 0, 135))
    shell.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(25)))

    frame = Image.new("RGBA", (outer_w, outer_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle((0, 0, outer_w - 1, outer_h - 1), radius=58, fill=(4, 8, 15), outline=(49, 65, 92), width=3)
    d.rounded_rectangle((20, 20, outer_w - 21, outer_h - 21), radius=42, fill=DEEP)
    fitted = fit_image(screen, (phone_w, phone_h))
    frame.paste(fitted, (21, 21), rounded_mask((phone_w, phone_h), 38))
    shell.alpha_composite(frame, (40, 40))
    return shell


def build_slide(spec: dict[str, object]) -> Image.Image:
    accent = spec["accent"]  # type: ignore[assignment]
    img = vertical_gradient().convert("RGBA")
    add_brand_glow(img, accent)  # type: ignore[arg-type]
    draw = ImageDraw.Draw(img)

    draw_brand_mark(draw, 58, 62, accent)  # type: ignore[arg-type]
    y = 168
    draw.text((64, y), str(spec["eyebrow"]), font=F_EYEBROW, fill=accent)  # type: ignore[arg-type]
    y += 54
    y = draw_lines(draw, (60, y), wrap_text(draw, str(spec["title"]), F_TITLE, 900), F_TITLE, TEXT, 12)
    y += 22
    draw_lines(draw, (63, y), wrap_text(draw, str(spec["body"]), F_BODY, 900), F_BODY, MUTED, 9)

    source_path = Path(spec["source"])  # type: ignore[arg-type]
    screen = sanitize_capture(Image.open(source_path), source_path.name)
    framed = phone_frame(screen)
    img.alpha_composite(framed, ((W - framed.width) // 2, 474))
    return img.convert("RGB")


def main() -> None:
    source_dir = Path(os.environ.get("LUMANOX_SCREENSHOT_SOURCE_DIR", DEFAULT_SOURCE_DIR))
    screenshots = sorted(
        path for path in source_dir.iterdir()
        if path.suffix.lower() in {".png", ".jpg", ".jpeg"}
    )
    if len(screenshots) < 3:
        raise SystemExit(f"Expected at least 3 screenshots in {source_dir}, found {len(screenshots)}")

    slides = [
        {
            "source": screenshots[0],
            "out": "01_encrypted_vault.png",
            "eyebrow": "ENCRYPTED VAULT",
            "title": "Keep private photos locked",
            "body": "Store photos and videos in an offline vault with local AES-256 encryption.",
            "accent": BLUE,
        },
        {
            "source": screenshots[1],
            "out": "02_local_ai_privacy_scan.png",
            "eyebrow": "LOCAL AI SCAN",
            "title": "Find sensitive items on device",
            "body": "Review hidden GPS, device metadata, IDs, and screenshots without cloud upload.",
            "accent": AMBER,
        },
        {
            "source": screenshots[2],
            "out": "03_security_and_backup_controls.png",
            "eyebrow": "SECURITY SETTINGS",
            "title": "Control every privacy layer",
            "body": "Manage subscription, unlock security, encrypted backup, storage, and support.",
            "accent": TEAL,
        },
    ]

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for spec in slides:
        out = build_slide(spec)
        path = OUT_DIR / str(spec["out"])
        out.save(path, "PNG", optimize=True)
        print(path, out.size)


if __name__ == "__main__":
    main()
