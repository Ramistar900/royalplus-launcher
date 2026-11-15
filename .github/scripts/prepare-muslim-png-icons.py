#!/usr/bin/env python3
"""Fetch and validate the four approved Muslim PNG illustrations.

Only these four PNGs are shipped by the launcher. The V19 SVG catalog and
its runtime loader must remain absent from the application.
"""
from __future__ import annotations

import hashlib
import struct
import urllib.request
import zlib
from pathlib import Path

DESIGN_COMMIT = "f784e3a1b9bdf350cd30b37f18a86efba023cc7a"
DESIGN_ROOT = (
    "https://raw.githubusercontent.com/Ramistar900/royal-design/"
    f"{DESIGN_COMMIT}/preview/royal-design/v19/muslim/static"
)
DESTINATION = Path("app/src/main/res/drawable-nodpi")

ICONS = {
    "muslim_prayer.png": (
        "file_000000001218820a8a39eaae196b66e0.png",
        "16d66d5f475d2377bc8b81924eecc3227c6ae156",
    ),
    "muslim_dua.png": (
        "file_000000007dc0820aa6c0f334ad42bca4.png",
        "7a9793e559c1fe934b009313ee1a3060152c3f33",
    ),
    "muslim_quran.png": (
        "quran_icon.png",
        "109b296ad41cb8d309997fa52894242b9172ea64",
    ),
    "muslim_qaris.png": (
        "qaris_icon.png",
        "524a4aabf22999d31a66a5367259eb52857e11f3",
    ),
}


def git_blob_sha1(data: bytes) -> str:
    header = f"blob {len(data)}\\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def validate_png(data: bytes, name: str) -> None:
    signature = b"\\x89PNG\\r\\n\\x1a\\n"
    if not data.startswith(signature) or len(data) < 1024:
        raise ValueError(f"{name}: missing PNG signature or file is too small")

    offset = len(signature)
    saw_ihdr = False
    saw_idat = False
    saw_iend = False
    idat_data = bytearray()
    width = height = 0

    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError(f"{name}: truncated PNG chunk header")
        chunk_length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        chunk_end = offset + 12 + chunk_length
        if chunk_end > len(data):
            raise ValueError(f"{name}: truncated {chunk_type!r} chunk")
        chunk_payload = data[offset + 8:offset + 8 + chunk_length]
        expected_crc = struct.unpack(">I", data[offset + 8 + chunk_length:chunk_end])[0]
        actual_crc = zlib.crc32(chunk_type + chunk_payload) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            raise ValueError(f"{name}: CRC mismatch in {chunk_type!r}")

        if chunk_type == b"IHDR":
            if saw_ihdr or chunk_length != 13:
                raise ValueError(f"{name}: invalid IHDR")
            width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", chunk_payload
            )
            if not (0 < width <= 12000 and 0 < height <= 12000):
                raise ValueError(f"{name}: invalid image dimensions {width}x{height}")
            if bit_depth not in (1, 2, 4, 8, 16) or color_type not in (0, 2, 3, 4, 6):
                raise ValueError(f"{name}: unsupported PNG color format")
            if compression != 0 or filtering != 0 or interlace not in (0, 1):
                raise ValueError(f"{name}: invalid PNG encoding parameters")
            saw_ihdr = True
        elif chunk_type == b"IDAT":
            if not saw_ihdr or saw_iend:
                raise ValueError(f"{name}: misplaced image data")
            saw_idat = True
            idat_data.extend(chunk_payload)
        elif chunk_type == b"IEND":
            if chunk_length != 0 or not saw_idat:
                raise ValueError(f"{name}: invalid IEND")
            saw_iend = True
            offset = chunk_end
            break
        offset = chunk_end

    if not (saw_ihdr and saw_idat and saw_iend) or offset != len(data):
        raise ValueError(f"{name}: incomplete PNG structure")
    try:
        decoded = zlib.decompress(bytes(idat_data))
    except zlib.error as error:
        raise ValueError(f"{name}: corrupt PNG image stream") from error
    if not decoded:
        raise ValueError(f"{name}: empty PNG image stream")


def ensure_v19_loader_is_absent() -> None:
    forbidden_paths = (
        Path("app/src/main/java/com/royalplus/launcher/RoyalV19MuslimIcon.kt"),
        Path("app/src/main/assets/muslim_v19"),
    )
    for path in forbidden_paths:
        if path.exists():
            raise RuntimeError(f"V19 Muslim icon code/assets must not be shipped: {path}")

    source_root = Path("app/src/main/java/com/royalplus/launcher")
    for source in source_root.glob("*.kt"):
        contents = source.read_text(encoding="utf-8")
        if "RoyalV19MuslimIcon" in contents or "muslim_v19/static" in contents:
            raise RuntimeError(f"V19 Muslim icon reference remains in {source}")

    gradle_file = Path("app/build.gradle.kts").read_text(encoding="utf-8")
    if "androidsvg-aar" in gradle_file:
        raise RuntimeError("The V19 SVG rendering dependency must not be added")


def main() -> None:
    ensure_v19_loader_is_absent()
    DESTINATION.mkdir(parents=True, exist_ok=True)

    for resource_name, (source_name, expected_blob_sha) in ICONS.items():
        url = f"{DESIGN_ROOT}/{source_name}"
        request = urllib.request.Request(url, headers={"User-Agent": "ROYAL-Launcher-Asset-Builder"})
        with urllib.request.urlopen(request, timeout=45) as response:
            payload = response.read()

        actual_blob_sha = git_blob_sha1(payload)
        if actual_blob_sha != expected_blob_sha:
            raise RuntimeError(
                f"{resource_name}: pinned source changed; expected {expected_blob_sha}, "
                f"received {actual_blob_sha}"
            )
        validate_png(payload, resource_name)
        destination = DESTINATION / resource_name
        temporary = destination.with_suffix(destination.suffix + ".tmp")
        temporary.write_bytes(payload)
        temporary.replace(destination)
        print(f"Verified {resource_name}: {len(payload):,} bytes ({actual_blob_sha})")

    print("All four approved Muslim PNGs verified; no V19 SVG loader or catalog is present.")


if __name__ == "__main__":
    main()
