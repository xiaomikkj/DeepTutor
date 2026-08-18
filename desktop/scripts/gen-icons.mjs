// DeepTutor desktop icon generator — zero-dependency Node script.
// Generates icons/32x32.png, 128x128.png, 128x128@2x.png and icon.ico
// (multi-size ICO using embedded PNG entries).
//
// Usage: node scripts/gen-icons.mjs
import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const OUT = join(ROOT, "src-tauri", "icons");
mkdirSync(OUT, { recursive: true });

// ---------------------------------------------------------------------------
// Minimal PNG encoder (RGBA, 8-bit, single IDAT)
// ---------------------------------------------------------------------------

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function encodePNG(width, height, rgba) {
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type RGBA
  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0; // filter: none
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  const idat = deflateSync(raw, { level: 9 });
  return Buffer.concat([
    sig,
    chunk("IHDR", ihdr),
    chunk("IDAT", idat),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

// ---------------------------------------------------------------------------
// ICO encoder (PNG-compressed entries)
// ---------------------------------------------------------------------------

function encodeICO(sizes, pngs) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0); // reserved
  header.writeUInt16LE(1, 2); // type: icon
  header.writeUInt16LE(sizes.length, 4);
  const entries = [];
  let offset = 6 + 16 * sizes.length;
  const blobs = [];
  for (let i = 0; i < sizes.length; i++) {
    const size = sizes[i];
    const png = pngs[i];
    const e = Buffer.alloc(16);
    e[0] = size >= 256 ? 0 : size;
    e[1] = size >= 256 ? 0 : size;
    e.writeUInt16LE(1, 4); // planes
    e.writeUInt16LE(32, 6); // bit count
    e.writeUInt32LE(png.length, 8);
    e.writeUInt32LE(offset, 12);
    entries.push(e);
    blobs.push(png);
    offset += png.length;
  }
  return Buffer.concat([header, ...entries, ...blobs]);
}

// ---------------------------------------------------------------------------
// Logo drawing (supersampled, then downscaled)
// ---------------------------------------------------------------------------

function hex(c) {
  return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
}

const C0 = hex("#6366f1"); // indigo
const C1 = hex("#22d3ee"); // cyan

function lerp(a, b, t) {
  return a + (b - a) * t;
}

function mix3(a, b, t) {
  return [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
}

// Signed distance: inside = 1 when (px,py) lies in the rounded rect.
function roundedRect(px, py, x0, y0, x1, y1, r) {
  if (px < x0 || px > x1 || py < y0 || py > y1) return false;
  const cx = Math.max(x0 + r, Math.min(px, x1 - r));
  const cy = Math.max(y0 + r, Math.min(py, y1 - r));
  const dx = px - cx;
  const dy = py - cy;
  return dx * dx + dy * dy <= r * r + 1e-9;
}

// Open-book polygon test (point in convex-ish quadrilateral via sign tests).
function pointInQuad(px, py, a, b, c, d) {
  const sign = (p1, p2, p3) =>
    (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1]);
  const s1 = sign(px, py, a, b);
  const s2 = sign(px, py, b, c);
  const s3 = sign(px, py, c, d);
  const s4 = sign(px, py, d, a);
  const hasNeg = s1 < 0 || s2 < 0 || s3 < 0 || s4 < 0;
  const hasPos = s1 > 0 || s2 > 0 || s3 > 0 || s4 > 0;
  return !(hasNeg && hasPos);
}

// Background gradient color at (u,v) normalized [0,1].
function bgColor(u, v) {
  const t = Math.min(1, Math.max(0, v * 0.9 + u * 0.25));
  return mix3(C0, C1, t);
}

const PAGE = [1, 1, 1]; // near-white book pages
const SPINE = [0.55, 0.65, 0.95];

function samplePoint(px, py) {
  // Background: rounded rect on transparent canvas.
  const bx0 = 0.06, by0 = 0.06, bx1 = 0.94, by1 = 0.94, br = 0.21;
  if (!roundedRect(px, py, bx0, by0, bx1, by1, br)) return [0, 0, 0, 0];

  const color = bgColor((px - bx0) / (bx1 - bx0), (py - by0) / (by1 - by0));

  // Open book.
  const left = [
    [0.24, 0.43],
    [0.50, 0.39],
    [0.50, 0.68],
    [0.24, 0.73],
  ];
  const right = [
    [0.50, 0.39],
    [0.76, 0.43],
    [0.76, 0.73],
    [0.50, 0.68],
  ];
  const spine = [
    [0.478, 0.39],
    [0.522, 0.39],
    [0.522, 0.68],
    [0.478, 0.68],
  ];
  if (pointInQuad(px, py, ...left) || pointInQuad(px, py, ...right)) {
    // slight vertical gradient on pages
    const v = (py - 0.39) / (0.73 - 0.39);
    return [...mix3([0.98, 0.99, 1], [0.82, 0.87, 0.96], v), 255];
  }
  if (pointInQuad(px, py, ...spine)) {
    return [...SPINE, 255];
  }

  // Soft "knowledge spark" — small dots above the book.
  for (const [dx, dy] of [
    [0.62, 0.26],
    [0.70, 0.20],
    [0.78, 0.27],
  ]) {
    const ddx = px - dx;
    const ddy = py - dy;
    if (ddx * ddx + ddy * ddy < 0.028 * 0.028) return [...PAGE, 235];
  }

  return [...color, 255];
}

function render(size) {
  const SS = 4; // supersample factor
  const s = size * SS;
  const rgba = Buffer.alloc(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let r = 0, g = 0, b = 0, a = 0;
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const px = (x + (sx + 0.5) / SS) / size;
          const py = (y + (sy + 0.5) / SS) / size;
          const [cr, cg, cb, ca] = samplePoint(px, py);
          r += cr;
          g += cg;
          b += cb;
          a += ca;
        }
      }
      const n = SS * SS;
      const i = (y * size + x) * 4;
      rgba[i] = Math.round(r / n);
      rgba[i + 1] = Math.round(g / n);
      rgba[i + 2] = Math.round(b / n);
      rgba[i + 3] = Math.round(a / n);
    }
  }
  return rgba;
}

// ---------------------------------------------------------------------------
// Emit files
// ---------------------------------------------------------------------------

const png32 = encodePNG(32, 32, render(32));
const png128 = encodePNG(128, 128, render(128));
const png256 = encodePNG(256, 256, render(256));

writeFileSync(join(OUT, "32x32.png"), png32);
writeFileSync(join(OUT, "128x128.png"), png128);
writeFileSync(join(OUT, "128x128@2x.png"), png256);

const icoSizes = [16, 24, 32, 48, 64, 128, 256];
const icoPngs = icoSizes.map((s) => encodePNG(s, s, render(s)));
writeFileSync(join(OUT, "icon.ico"), encodeICO(icoSizes, icoPngs));

console.log(`Icons written to ${OUT}`);
