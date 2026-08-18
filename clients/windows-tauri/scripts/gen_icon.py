#!/usr/bin/env python3
"""Generate a simple DeepTutor app icon (512x512 PNG) without external deps.

Pure-python PNG writer: a rounded-square blue->violet gradient tile with a
white speech-bubble dot motif, suitable as the source for `tauri icon`.
"""

import struct
import zlib
from pathlib import Path

SIZE = 512
OUT = Path(__file__).resolve().parent / "icon-source.png"


def blend(c1, c2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))


def in_rounded_rect(x, y, x0, y0, x1, y1, r):
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    # corners
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= r * r


def in_circle(x, y, cx, cy, r):
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def main():
    rows = []
    for y in range(SIZE):
        row = bytearray([0])
        for x in range(SIZE):
            if in_rounded_rect(x, y, 40, 40, SIZE - 40, SIZE - 40, 96):
                t = (x + y) / (2 * SIZE)
                r, g, b = blend((59, 130, 246), (139, 92, 246), t)
                # white speech bubble: two dots + tail
                if in_circle(x, y, 208, 228, 64) or in_circle(x, y, 332, 228, 64):
                    r, g, b = 255, 255, 255
                elif in_circle(x, y, 270, 336, 40):
                    r, g, b = 255, 255, 255
                row += bytes((r, g, b, 255))
            else:
                row += bytes((0, 0, 0, 0))
        rows.append(bytes(row))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        c += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c

    raw = b"".join(rows)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    OUT.write_bytes(png)
    print(f"wrote {OUT} ({len(png)} bytes)")


if __name__ == "__main__":
    main()
