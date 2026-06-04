#!/usr/bin/env python3
"""Generate English phone screenshots using current LumaNox iOS captures and feature scope."""
from __future__ import annotations

from pathlib import Path
from typing import Callable, Iterable
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1080, 1920
ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = Path(__file__).resolve().parent / "phone_screenshots_en_ios_latest"
IOS_PREVIEWS = ROOT / "ios" / "LumaNox" / "DesignPreviews"

BG_TOP = (5, 8, 13)
BG_BOTTOM = (11, 19, 36)
SCREEN_BG = (5, 8, 13)
SECTION = (12, 21, 35)
SECTION_2 = (14, 25, 42)
STROKE = (34, 50, 71)
STROKE_STRONG = (51, 72, 104)
BLUE = (74, 158, 255)
BLUE_2 = (38, 91, 184)
AMBER = (232, 197, 71)
TEAL = (33, 194, 119)
RED = (255, 67, 114)
TEXT = (234, 241, 255)
MUTED = (142, 162, 192)
SUBTLE = (99, 119, 148)
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
F_SCREEN_TITLE = font(48, True)
F_H2 = font(31, True)
F_H3 = font(24, True)
F_TEXT = font(22)
F_TEXT_BOLD = font(22, True)
F_SMALL = font(18)
F_TINY = font(15, True)
F_TAB = font(15)


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


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def vertical_gradient(size: tuple[int, int], top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    w, h = size
    strip = Image.new("RGB", (1, h))
    px = strip.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        px[0, y] = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
    return strip.resize((w, h), Image.Resampling.BILINEAR)


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


def source(name: str) -> Image.Image:
    return Image.open(IOS_PREVIEWS / name).convert("RGBA")


def screen_base() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = vertical_gradient((786, 1704), (9, 17, 31), SCREEN_BG).convert("RGBA")
    return img, ImageDraw.Draw(img)


def pill(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], text: str, fill: tuple[int, int, int], fg: tuple[int, int, int] = TEXT) -> None:
    draw.rounded_rectangle(box, radius=(box[3] - box[1]) // 2, fill=fill)
    tw, th = text_size(draw, text, F_TINY)
    draw.text((box[0] + (box[2] - box[0] - tw) // 2, box[1] + (box[3] - box[1] - th) // 2 - 1), text, font=F_TINY, fill=fg)


def header(draw: ImageDraw.ImageDraw, title: str, subtitle: str | None = None) -> None:
    draw.text((38, 80), title, font=F_SCREEN_TITLE, fill=TEXT)
    if subtitle:
        draw.text((40, 140), subtitle, font=F_TEXT, fill=MUTED)


def tab_bar(draw: ImageDraw.ImageDraw, selected: str) -> None:
    labels = [("Vault", "V"), ("Camera", "C"), ("AI", "A"), ("Settings", "S")]
    x0, y0, w, h = 38, 1542, 710, 112
    draw.rounded_rectangle((x0, y0, x0 + w, y0 + h), radius=30, fill=(13, 24, 40), outline=STROKE)
    for index, (label, icon) in enumerate(labels):
        cx = x0 + 64 + index * 184
        active = label == selected
        if active:
            draw.rounded_rectangle((cx - 46, y0 + 16, cx + 46, y0 + 96), radius=20, fill=BLUE_2)
        draw.ellipse((cx - 16, y0 + 26, cx + 16, y0 + 58), outline=BLUE if active else SUBTLE, width=2)
        tw, _ = text_size(draw, icon, F_TINY)
        draw.text((cx - tw // 2, y0 + 34), icon, font=F_TINY, fill=TEXT if active else MUTED)
        tw, _ = text_size(draw, label, F_TAB)
        draw.text((cx - tw // 2, y0 + 68), label, font=F_TAB, fill=TEXT if active else MUTED)


def backup_screen() -> Image.Image:
    img, d = screen_base()
    header(d, "Backup & Restore", "Encrypted packages for safe recovery")

    d.rounded_rectangle((38, 220, 748, 426), radius=28, fill=SECTION, outline=STROKE)
    d.rounded_rectangle((68, 254, 144, 330), radius=22, fill=BLUE_2)
    d.text((88, 270), "B", font=F_H2, fill=TEXT)
    d.text((170, 252), "Backup is ready", font=F_H2, fill=TEXT)
    d.text((170, 300), "Last sync: today at 14:38", font=F_TEXT, fill=MUTED)
    pill(d, (170, 352, 330, 392), "AES-256", BLUE_2)
    pill(d, (350, 352, 526, 392), "Argon2id", (47, 68, 102))
    pill(d, (546, 352, 704, 392), "Offline", (35, 83, 69))

    cards = [
        ("Manual backup", "Export a .aivb file through Files", BLUE),
        ("Auto backup", "Keep backup.dat updated locally", TEAL),
        ("Restore vault", "Recover media with your PIN", AMBER),
    ]
    y = 468
    for title, body, accent in cards:
        d.rounded_rectangle((38, y, 748, y + 150), radius=24, fill=SECTION_2, outline=STROKE)
        d.ellipse((70, y + 44, 122, y + 96), fill=accent)
        d.text((152, y + 38), title, font=F_H2, fill=TEXT)
        d.text((152, y + 86), body, font=F_TEXT, fill=MUTED)
        d.text((702, y + 56), ">", font=F_H2, fill=MUTED)
        y += 174

    d.rounded_rectangle((38, 1088, 748, 1322), radius=28, fill=(9, 18, 31), outline=STROKE)
    d.text((68, 1130), "Package format", font=F_H2, fill=TEXT)
    rows = [("Magic", "AIVAULT v1"), ("Body", "AES-GCM chunks"), ("Storage", "Files app")]
    ry = 1194
    for key, value in rows:
        d.text((68, ry), key, font=F_TEXT, fill=MUTED)
        vw, _ = text_size(d, value, F_TEXT_BOLD)
        d.text((708 - vw, ry), value, font=F_TEXT_BOLD, fill=TEXT)
        ry += 42
    tab_bar(d, "Settings")
    return img


def cleanup_screen() -> Image.Image:
    img, d = screen_base()
    header(d, "Smart Cleanup", "Local analysis of encrypted media")

    d.rounded_rectangle((38, 214, 748, 424), radius=28, fill=(41, 31, 11), outline=(116, 90, 31))
    d.text((70, 252), "12", font=font(72, True), fill=AMBER)
    d.text((172, 256), "items need review", font=F_H2, fill=TEXT)
    d.text((172, 306), "Blurry, duplicate, or oversized media", font=F_TEXT, fill=(255, 224, 138))
    pill(d, (70, 356, 204, 396), "Review", AMBER, (15, 20, 29))

    sections = [("Duplicates", "6 similar photos", BLUE), ("Blurry", "4 low-sharpness photos", AMBER), ("Screenshots", "2 items categorized", TEAL)]
    y = 470
    for title, body, accent in sections:
        d.rounded_rectangle((38, y, 748, y + 184), radius=24, fill=SECTION, outline=STROKE)
        d.rounded_rectangle((68, y + 34, 170, y + 136), radius=22, fill=(9, 18, 31), outline=STROKE)
        d.ellipse((98, y + 62, 140, y + 104), fill=accent)
        d.text((196, y + 42), title, font=F_H2, fill=TEXT)
        d.text((196, y + 92), body, font=F_TEXT, fill=MUTED)
        d.text((702, y + 70), ">", font=F_H2, fill=MUTED)
        y += 212

    d.rounded_rectangle((38, 1160, 748, 1440), radius=28, fill=SECTION_2, outline=STROKE)
    d.text((68, 1200), "Private by default", font=F_H2, fill=TEXT)
    d.text((68, 1248), "Quality, duplicate, and category signals are computed on this device.", font=F_TEXT, fill=MUTED)
    for i, color in enumerate([BLUE, AMBER, TEAL, RED, (120, 96, 255), (120, 140, 160)]):
        x = 68 + (i % 3) * 214
        y2 = 1320 + (i // 3) * 62
        d.rounded_rectangle((x, y2, x + 164, y2 + 38), radius=18, fill=(9, 18, 31), outline=STROKE)
        d.ellipse((x + 14, y2 + 10, x + 32, y2 + 28), fill=color)
    tab_bar(d, "AI")
    return img


def redaction_screen() -> Image.Image:
    img, d = screen_base()
    d.text((38, 80), "<", font=F_SCREEN_TITLE, fill=MUTED)
    tw, _ = text_size(d, "1 / 12", F_TEXT_BOLD)
    d.text((393 - tw // 2, 96), "1 / 12", font=F_TEXT_BOLD, fill=MUTED)

    preview = (38, 178, 748, 1288)
    d.rounded_rectangle(preview, radius=28, fill=(12, 18, 31), outline=STROKE)
    art = vertical_gradient((preview[2] - preview[0], preview[3] - preview[1]), (92, 55, 172), (240, 82, 106)).convert("RGBA")
    ad = ImageDraw.Draw(art)
    ad.ellipse((420, 580, 508, 668), fill=(252, 164, 48))
    ad.text((394, 692), "Browser", font=F_SMALL, fill=(252, 236, 220))
    ad.ellipse((430, 398, 526, 494), fill=(238, 255, 245))
    ad.ellipse((464, 428, 492, 456), fill=TEAL)
    ad.rounded_rectangle((452, 482, 504, 520), radius=12, fill=(238, 255, 245))
    ad.text((430, 538), "Location", font=F_SMALL, fill=(255, 255, 255))
    for x in [120, 220, 320, 420, 520]:
        ad.ellipse((x, 900, x + 42, 942), fill=(255, 255, 255, 238))
    ad.rounded_rectangle((168, 214, 544, 294), radius=28, fill=(6, 9, 16, 170))
    ad.text((218, 232), "Sensitive address", font=F_H3, fill=TEXT)
    sensitive_box = (404, 390, 538, 548)
    mosaic = art.crop(sensitive_box).resize((12, 14), Image.Resampling.BILINEAR)
    md = ImageDraw.Draw(mosaic)
    for x in range(0, mosaic.width, 2):
        for y in range(0, mosaic.height, 2):
            if (x + y) % 4 == 0:
                md.rectangle((x, y, x + 1, y + 1), fill=(255, 255, 255, 90))
    mosaic = mosaic.resize((sensitive_box[2] - sensitive_box[0], sensitive_box[3] - sensitive_box[1]), Image.Resampling.NEAREST)
    art.paste(mosaic, sensitive_box)
    ad.rounded_rectangle(sensitive_box, radius=18, outline=AMBER, width=5)
    for x in range(sensitive_box[0] + 8, sensitive_box[2] - 8, 18):
        ad.line((x, sensitive_box[1] + 4, x, sensitive_box[3] - 4), fill=(255, 255, 255, 36), width=1)
    for y in range(sensitive_box[1] + 8, sensitive_box[3] - 8, 18):
        ad.line((sensitive_box[0] + 4, y, sensitive_box[2] - 4, y), fill=(0, 0, 0, 42), width=1)
    img.paste(art, (preview[0], preview[1]), rounded_mask(art.size, 28))

    d.rounded_rectangle((38, 1330, 748, 1460), radius=28, fill=SECTION, outline=STROKE)
    controls = [("Share", BLUE), ("Redact", AMBER), ("Info", MUTED), ("Delete", RED)]
    for i, (label, color) in enumerate(controls):
        cx = 94 + i * 180
        if label == "Redact":
            d.rounded_rectangle((cx - 58, 1352, cx + 58, 1434), radius=24, fill=(44, 38, 20))
        d.ellipse((cx - 17, 1364, cx + 17, 1398), outline=color, width=3)
        tw, _ = text_size(d, label, F_TAB)
        d.text((cx - tw // 2, 1410), label, font=F_TAB, fill=TEXT if label == "Redact" else MUTED)
    return img


def offline_screen() -> Image.Image:
    img, d = screen_base()
    header(d, "Offline Privacy", "No account. No cloud upload.")

    d.rounded_rectangle((96, 232, 690, 826), radius=48, fill=SECTION, outline=STROKE_STRONG, width=2)
    d.rounded_rectangle((206, 360, 580, 720), radius=36, fill=(8, 15, 27), outline=STROKE)
    d.ellipse((318, 430, 468, 580), fill=BLUE_2)
    d.rounded_rectangle((352, 386, 434, 486), radius=34, outline=TEXT, width=8)
    d.rounded_rectangle((330, 476, 456, 606), radius=30, fill=TEXT)
    d.ellipse((384, 520, 402, 538), fill=BLUE_2)
    d.rounded_rectangle((392, 534, 398, 574), radius=3, fill=BLUE_2)
    d.text((246, 654), "Encrypted on device", font=F_H2, fill=TEXT)

    rows = [("Zero cloud storage", "Your media stays under your control", TEAL), ("Local AI scans", "Sensitive review runs on device", AMBER), ("Rebuildable metadata", "Encrypted files remain the source of truth", BLUE)]
    y = 910
    for title, body, accent in rows:
        d.rounded_rectangle((38, y, 748, y + 138), radius=24, fill=SECTION_2, outline=STROKE)
        d.ellipse((68, y + 43, 120, y + 95), fill=accent)
        d.text((148, y + 34), title, font=F_H2, fill=TEXT)
        d.text((148, y + 82), body, font=F_TEXT, fill=MUTED)
        y += 164
    tab_bar(d, "Vault")
    return img


ScreenFactory = Callable[[], Image.Image]


SLIDES: list[dict[str, object]] = [
    {
        "out": "01_encrypted_vault.png",
        "screen": lambda: source("screen_2a.png"),
        "eyebrow": "ENCRYPTED VAULT",
        "title": "Keep private photos locked",
        "body": "Import photos and videos into an offline vault with AES-256 local encryption.",
        "accent": BLUE,
    },
    {
        "out": "02_pin_face_id_unlock.png",
        "screen": lambda: source("screen_1e.png"),
        "eyebrow": "PIN & FACE ID",
        "title": "Unlock only when it is you",
        "body": "Protect every return with a 6-digit PIN and supported biometric unlock.",
        "accent": BLUE,
    },
    {
        "out": "03_encrypted_backup_restore.png",
        "screen": backup_screen,
        "eyebrow": "BACKUP & RESTORE",
        "title": "Recover without the cloud",
        "body": "Create encrypted local backups for migration, recovery, and Files app storage.",
        "accent": TEAL,
    },
    {
        "out": "04_privacy_redaction.png",
        "screen": redaction_screen,
        "eyebrow": "PRIVACY REDACTION",
        "title": "Hide details before sharing",
        "body": "Redact sensitive regions, then save the protected copy back into your vault.",
        "accent": AMBER,
    },
    {
        "out": "05_ai_sensitive_review.png",
        "screen": lambda: source("screen_1j.png"),
        "eyebrow": "AI PRIVACY SCAN",
        "title": "Find sensitive photos locally",
        "body": "Review faces, IDs, QR codes, text, and risky screenshots without uploading.",
        "accent": AMBER,
    },
    {
        "out": "06_smart_cleanup_classify.png",
        "screen": cleanup_screen,
        "eyebrow": "SMART CLEANUP",
        "title": "Organize the vault faster",
        "body": "Detect duplicates, blurry media, and local categories from encrypted items.",
        "accent": TEAL,
    },
    {
        "out": "07_private_camera.png",
        "screen": lambda: source("screen_15.png"),
        "eyebrow": "PRIVATE CAMERA",
        "title": "Capture straight into the vault",
        "body": "Take private photos and videos without saving copies to the system album.",
        "accent": BLUE,
    },
    {
        "out": "08_offline_first_privacy.png",
        "screen": offline_screen,
        "eyebrow": "OFFLINE-FIRST PRIVACY",
        "title": "No cloud required",
        "body": "LumaNox is designed for local storage, local AI, and user-controlled backups.",
        "accent": BLUE,
    },
]


def build_slide(spec: dict[str, object]) -> Image.Image:
    accent = spec["accent"]  # type: ignore[assignment]
    img = vertical_gradient((W, H), BG_TOP, BG_BOTTOM).convert("RGBA")
    add_brand_glow(img, accent)  # type: ignore[arg-type]
    draw = ImageDraw.Draw(img)

    draw_brand_mark(draw, 58, 62, accent)  # type: ignore[arg-type]
    y = 168
    draw.text((64, y), str(spec["eyebrow"]), font=F_EYEBROW, fill=accent)  # type: ignore[arg-type]
    y += 54
    y = draw_lines(draw, (60, y), wrap_text(draw, str(spec["title"]), F_TITLE, 900), F_TITLE, TEXT, 12)
    y += 22
    draw_lines(draw, (63, y), wrap_text(draw, str(spec["body"]), F_BODY, 900), F_BODY, MUTED, 9)

    screen_factory = spec["screen"]
    screen = screen_factory()  # type: ignore[operator]
    framed = phone_frame(screen)
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
