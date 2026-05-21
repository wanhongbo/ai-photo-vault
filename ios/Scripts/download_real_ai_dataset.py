#!/usr/bin/env python3
import argparse
import csv
import json
import shutil
import ssl
import time
import urllib.request
from urllib.error import URLError
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter

OPEN_IMAGES_LABELS = "https://storage.googleapis.com/openimages/v7/oidv7-val-annotations-human-imagelabels.csv"
OPEN_IMAGES_CLASSES = "https://storage.googleapis.com/openimages/v7/oidv7-class-descriptions.csv"
OPEN_IMAGES_IMAGE = "https://open-images-dataset.s3.amazonaws.com/validation/{image_id}.jpg"

OPEN_IMAGES_GROUPS = {
    "people": {
        "labels": ["Person", "Human face"],
        "expectedCategory": "people",
        "expectedSensitive": True,
    },
    "food": {
        "labels": ["Food", "Pizza", "Hamburger", "Salad"],
        "expectedCategory": "food",
        "expectedSensitive": False,
    },
    "nature": {
        "labels": ["Mountain", "Tree", "Forest", "Landscape"],
        "expectedCategory": "nature",
        "expectedSensitive": False,
    },
    "document": {
        "labels": ["Document", "Text", "Receipt"],
        "expectedCategory": "documents",
        "expectedSensitive": False,
    },
    "id_document": {
        "labels": ["Identity document"],
        "expectedCategory": "documents",
        "expectedSensitive": True,
    },
    "screenshot": {
        "labels": ["Screenshot"],
        "expectedCategory": "screenshots",
        "expectedSensitive": False,
    },
}

BARCODE_URLS = [
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/air-travel/air-travel-ticket.jpg",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/barcodes-in-low-lights/barcodes-in-low-lights-1.jpg",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/barcodes-in-strong-light/barcodes-in-strong-light-1.jpg",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/barcodes-in-strong-light/barcodes-in-strong-light-2.jpg",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/barcode-with-shadow/barcode-with-shadow-1.jpg",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/barcode-with-shadow/barcode-with-shadow-5.png",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/blurry-barcodes/blurry-barcodes-1.jpg",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/crumpled-barcodes/crumpled-barcodes-1.jpg",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/curved-barcodes/curved-code-1.png",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/datamatrix/datamatrix-1.jpg",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/datamatrix/datamatrix-10.jpg",
    "https://www.dynamsoft.com/webres/wwwroot/images/resources/barcodes-sample-images/datamatrix/datamatrix-16.png",
]


def download(url, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.stat().st_size > 0:
        return target
    tmp = target.with_suffix(target.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": "LumaNoxAIRegression/1.0"})
    try:
        import certifi

        context = ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        context = ssl.create_default_context()
    try:
        with urllib.request.urlopen(request, timeout=90, context=context) as response:
            tmp.write_bytes(response.read())
    except URLError as error:
        if not isinstance(getattr(error, "reason", None), ssl.SSLCertVerificationError):
            raise
        context = ssl._create_unverified_context()
        with urllib.request.urlopen(request, timeout=90, context=context) as response:
            tmp.write_bytes(response.read())
    tmp.replace(target)
    return target


def load_label_map(cache):
    classes_path = download(OPEN_IMAGES_CLASSES, cache / "openimages-class-descriptions.csv")
    label_by_name = {}
    with classes_path.open(newline="", encoding="utf-8") as file:
        for label, display_name in csv.reader(file):
            label_by_name[display_name] = label
    return label_by_name


def select_open_images(cache, per_group, id_document_count):
    label_by_name = load_label_map(cache)
    wanted = {}
    for kind, config in OPEN_IMAGES_GROUPS.items():
        limit = id_document_count if kind == "id_document" else per_group
        wanted[kind] = {
            "labels": {label_by_name[name] for name in config["labels"] if name in label_by_name},
            "limit": limit,
            "ids": [],
        }

    labels_path = download(OPEN_IMAGES_LABELS, cache / "openimages-val-human-labels.csv")
    used = set()
    with labels_path.open(newline="", encoding="utf-8") as file:
        for row in csv.DictReader(file):
            if row["Confidence"] != "1.0":
                continue
            for kind, state in wanted.items():
                if len(state["ids"]) >= state["limit"]:
                    continue
                if row["ImageID"] in used:
                    continue
                if row["LabelName"] in state["labels"]:
                    state["ids"].append(row["ImageID"])
                    used.add(row["ImageID"])

    return {kind: state["ids"] for kind, state in wanted.items()}


def image_suffix(path):
    suffix = path.suffix.lower()
    return suffix if suffix in [".jpg", ".jpeg", ".png"] else ".jpg"


def add_sample(samples, kind, path, expected_category, expected_sensitive, expected_tags=None, source=None):
    samples.append(
        {
            "kind": kind,
            "fileName": path.name,
            "path": str(path.resolve()),
            "expectedCategory": expected_category,
            "expectedSensitive": expected_sensitive,
            "expectedTags": expected_tags or [],
            "source": source or "",
        }
    )


def download_open_images(output, cache, selected):
    tasks = []
    with ThreadPoolExecutor(max_workers=8) as pool:
        for kind, ids in selected.items():
            kind_dir = output / "images" / kind
            for index, image_id in enumerate(ids, start=1):
                target = kind_dir / f"{kind}_{index:03d}_{image_id}.jpg"
                tasks.append(pool.submit(download, OPEN_IMAGES_IMAGE.format(image_id=image_id), target))
        for future in as_completed(tasks):
            future.result()


def collect_open_images(output):
    samples = []
    for kind, config in OPEN_IMAGES_GROUPS.items():
        for path in sorted((output / "images" / kind).glob("*")):
            add_sample(
                samples,
                kind,
                path,
                config["expectedCategory"],
                config["expectedSensitive"],
                expected_tags=["face"] if kind == "people" else [],
                source="Open Images V7 validation",
            )
    return samples


def download_barcodes(output, count):
    samples = []
    kind_dir = output / "images" / "barcode"
    tasks = []
    urls = BARCODE_URLS[:count]
    with ThreadPoolExecutor(max_workers=6) as pool:
        for index, url in enumerate(urls, start=1):
            suffix = Path(url).suffix.lower() or ".jpg"
            target = kind_dir / f"barcode_{index:03d}{suffix}"
            tasks.append((url, target, pool.submit(download, url, target)))
        for url, target, future in tasks:
            future.result()
            add_sample(
                samples,
                "barcode",
                target,
                "documents",
                True,
                expected_tags=["barcode"],
                source=url,
            )
    return samples


def derive_duplicate_samples(output, source_samples, count):
    samples = []
    kind_dir = output / "images" / "duplicate"
    bases = [sample for sample in source_samples if sample["kind"] in {"nature", "food"}]
    for index, sample in enumerate(bases[:count], start=1):
        source = Path(sample["path"])
        target = kind_dir / f"duplicate_{index:03d}_{source.name}"
        target.parent.mkdir(parents=True, exist_ok=True)
        image = Image.open(source).convert("RGB")
        pixels = image.load()
        x = image.width - 1
        y = image.height - 1
        pixels[x, y] = ((index * 37) % 255, (index * 71) % 255, (index * 113) % 255)
        image.save(target, quality=92)
        add_sample(
            samples,
            "duplicate",
            target,
            "other",
            False,
            expected_tags=["duplicate"],
            source=f"derived from {source.name}",
        )
    return samples


def derive_low_quality_samples(output, source_samples, count):
    samples = []
    kind_dir = output / "images" / "low_quality"
    bases = [sample for sample in source_samples if sample["kind"] in {"nature", "food", "people"}]
    for index, sample in enumerate(bases[:count], start=1):
        source = Path(sample["path"])
        target = kind_dir / f"low_quality_{index:03d}_{source.stem}.jpg"
        target.parent.mkdir(parents=True, exist_ok=True)
        image = Image.open(source).convert("RGB")
        if index % 2 == 0:
            image = image.filter(ImageFilter.GaussianBlur(radius=10))
        else:
            image = ImageEnhance.Brightness(image).enhance(2.4)
            image = ImageEnhance.Contrast(image).enhance(0.65)
        image.save(target, quality=90)
        add_sample(
            samples,
            "low_quality",
            target,
            "other",
            False,
            expected_tags=["blurry" if index % 2 == 0 else "overexposed"],
            source=f"derived from {source.name}",
        )
    return samples


def main():
    parser = argparse.ArgumentParser(description="Download a small real-world AI regression image set.")
    parser.add_argument("--output", default=".tmp/real-ai-dataset")
    parser.add_argument("--per-openimages-group", type=int, default=12)
    parser.add_argument("--id-document-count", type=int, default=8)
    parser.add_argument("--barcode-count", type=int, default=12)
    parser.add_argument("--derived-count", type=int, default=12)
    args = parser.parse_args()

    output = Path(args.output)
    cache = output / "cache"
    output.mkdir(parents=True, exist_ok=True)

    selected = select_open_images(cache, args.per_openimages_group, args.id_document_count)
    download_open_images(output, cache, selected)
    open_image_samples = collect_open_images(output)
    barcode_samples = download_barcodes(output, args.barcode_count)
    duplicate_samples = derive_duplicate_samples(output, open_image_samples, args.derived_count)
    low_quality_samples = derive_low_quality_samples(output, open_image_samples, args.derived_count)

    samples = open_image_samples + barcode_samples + duplicate_samples + low_quality_samples
    manifest = {
        "createdAtMs": int(time.time() * 1000),
        "sources": [
            "Open Images V7 validation",
            "Dynamsoft barcode test images",
            "Local derivations for duplicate and low-quality cases",
        ],
        "samples": samples,
    }
    manifest_path = output / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")

    counts = {}
    for sample in samples:
        counts[sample["kind"]] = counts.get(sample["kind"], 0) + 1
    print(json.dumps({"manifest": str(manifest_path.resolve()), "total": len(samples), "counts": counts}, indent=2))


if __name__ == "__main__":
    main()
