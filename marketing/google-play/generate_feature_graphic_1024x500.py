#!/usr/bin/env python3
"""Generate the Google Play 1024 x 500 feature graphic for LumaNox."""
from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1024, 500
OUT_PATH = Path(__file__).resolve().parent / "feature_graphic_1024x500.png"
ICON_PATH = Path(__file__).resolve().parent / "ic_launcher_luma.png"
SNAPSHOT_DIR = Path(__file__).resolve().parents[1] / "snapshot-Android"
AVATAR_PATH = Path(__file__).resolve().parent / "virtual_avatar_lumanox.png"

BG_LEFT = (6, 10, 17)
BG_RIGHT = (12, 23, 40)
BLUE = (74, 158, 255)
BLUE_SOFT = (183, 215, 255)
GOLD = (232, 197, 71)
GOLD_SOFT = (255, 224, 138)
TEAL = (91, 192, 212)
TEXT = (240, 244, 255)
MUTED = (142, 162, 192)
PANEL = (14, 24, 39)
STROKE = (35, 50, 74)


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


F_BRAND = font(58, True)
F_SUB = font(25)
F_CHIP = font(17, True)
F_SMALL = font(19, True)


def horizontal_gradient() -> Image.Image:
    strip = Image.new("RGB", (W, 1))
    px = strip.load()
    for x in range(W):
        t = x / (W - 1)
        px[x, 0] = tuple(int(BG_LEFT[i] * (1 - t) + BG_RIGHT[i] * t) for i in range(3))
    return strip.resize((W, H), Image.Resampling.BILINEAR)


def text_size(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont) -> tuple[int, int]:
    box = draw.textbbox((0, 0), text, font=fnt)
    return box[2] - box[0], box[3] - box[1]


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


def add_glow(img: Image.Image) -> None:
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse((-130, -120, 380, 390), fill=(*BLUE, 30))
    d.ellipse((660, -210, 1240, 390), fill=(*GOLD, 24))
    d.ellipse((440, 250, 1080, 720), fill=(*TEAL, 14))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(55)))


def patch_lumavault_title(screen: Image.Image) -> Image.Image:
    fixed = screen.copy().convert("RGBA")
    d = ImageDraw.Draw(fixed)
    x0, y0, x1, y1 = 40, 104, 390, 190
    for x in range(x0, x1):
        t = (x - x0) / max(x1 - x0 - 1, 1)
        color = tuple(int((7, 13, 24)[i] * (1 - t) + (11, 22, 40)[i] * t) for i in range(3))
        d.line((x, y0, x, y1), fill=color)
    d.text((48, 114), "LumaNox", font=font(41, True), fill=(234, 241, 255))
    return fixed


def phone(screen_path: Path, size: tuple[int, int], angle: float = 0, patch_title: bool = False) -> Image.Image:
    screen = Image.open(screen_path).convert("RGBA")
    screen = anonymize_people(screen, screen_path.name)
    if patch_title:
        screen = patch_lumavault_title(screen)
    phone_w, phone_h = size
    frame_w, frame_h = phone_w + 24, phone_h + 24
    frame = Image.new("RGBA", (frame_w, frame_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle((0, 0, frame_w - 1, frame_h - 1), radius=34, fill=(3, 6, 12), outline=(49, 65, 92), width=2)
    d.rounded_rectangle((12, 12, frame_w - 13, frame_h - 13), radius=24, fill=(3, 6, 12))

    scale = max(phone_w / screen.width, phone_h / screen.height)
    resized = screen.resize((int(screen.width * scale), int(screen.height * scale)), Image.Resampling.LANCZOS)
    cx = (resized.width - phone_w) // 2
    cy = (resized.height - phone_h) // 2
    crop = resized.crop((cx, cy, cx + phone_w, cy + phone_h))
    frame.paste(crop, (12, 12), rounded_mask((phone_w, phone_h), 22))

    if angle:
        frame = frame.rotate(angle, expand=True, resample=Image.Resampling.BICUBIC)

    shadow = Image.new("RGBA", (frame.width + 42, frame.height + 42), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((21, 21, frame.width + 21, frame.height + 21), radius=38, fill=(0, 0, 0, 115))
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    shadow.alpha_composite(frame, (21, 21))
    return shadow


def chip(draw: ImageDraw.ImageDraw, x: int, y: int, label: str, fill: tuple[int, int, int]) -> int:
    pad_x = 14
    tw, th = text_size(draw, label, F_CHIP)
    draw.rounded_rectangle((x, y, x + tw + pad_x * 2, y + 34), radius=13, fill=(17, 31, 50), outline=STROKE)
    draw.ellipse((x + 12, y + 12, x + 22, y + 22), fill=fill)
    draw.text((x + pad_x + 18, y + 8), label, font=F_CHIP, fill=BLUE_SOFT)
    return x + tw + pad_x * 2 + 12


def main() -> None:
    img = horizontal_gradient().convert("RGBA")
    add_glow(img)
    draw = ImageDraw.Draw(img)

    icon = Image.open(ICON_PATH).convert("RGBA").resize((72, 72), Image.Resampling.LANCZOS)
    img.alpha_composite(icon, (42, 54))
    draw.text((132, 57), "LumaNox", font=F_BRAND, fill=TEXT)
    draw.text((46, 146), "Private Photo & Video Vault", font=F_SUB, fill=BLUE_SOFT)
    draw.text((46, 190), "Encrypted on device. AI privacy tools.", font=F_SMALL, fill=MUTED)
    draw.text((46, 221), "Offline by design.", font=F_SMALL, fill=GOLD_SOFT)

    x = 46
    y = 292
    x = chip(draw, x, y, "AES-256", GOLD)
    x = chip(draw, x, y, "Biometric unlock", BLUE)
    chip(draw, x, y, "No cloud required", TEAL)

    draw.rounded_rectangle((46, 360, 390, 414), radius=18, fill=(14, 24, 39, 230), outline=STROKE)
    draw.text((66, 375), "Keep the light. Guard the private.", font=F_SMALL, fill=TEXT)

    back = phone(SNAPSHOT_DIR / "应用市场营销截图制作 (4).png", (250, 500), angle=-4)
    mid = phone(SNAPSHOT_DIR / "应用市场营销截图制作 (5).png", (250, 500), angle=4)
    front = phone(SNAPSHOT_DIR / "应用市场营销截图制作 (1).png", (270, 540), angle=0, patch_title=True)
    img.alpha_composite(back, (520, 18))
    img.alpha_composite(mid, (760, 44))
    img.alpha_composite(front, (640, -8))

    # Subtle separator to keep phones from crowding the headline on small placements.
    fade = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fade)
    fd.rectangle((430, 0, 500, H), fill=(5, 9, 15, 34))
    img.alpha_composite(fade.filter(ImageFilter.GaussianBlur(22)))

    img.convert("RGB").save(OUT_PATH, "PNG", optimize=True)
    print(OUT_PATH, (W, H))


if __name__ == "__main__":
    main()
