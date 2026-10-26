#!/usr/bin/env python3
"""Lector del formato de esferas FitCloud "GUI" (Amazfit Pop 2 y otros relojes htsmart/Realtek).

Deducido del SDK de Zepp Active (com.htsmart.wristband2.dial.WriteGUI / DialWriter):
  - Los offsets del bloque GUI son relativos a BASE = 1536.
  - 1552: addr_offset (u32) del WatchFaceOffset_t; 1568: ancho y alto de la pantalla (u16, u16).
  - WatchFaceOffset_t (76 B), controles (32 B c/u), índice de bitmaps (12 B c/u), datos de bitmaps.
  - Bitmap: tabla de offsets por fila (u32 × alto) y filas en RGB565 big-endian (bpp=1) o paleta de 8 bits (bpp=8).
  - CRC16/ARC en el offset 518 (ver crc()).
Uso: fcdial.py esfera.bin [carpeta_png]
"""
import struct, sys, os

BASE = 1536
u8 = lambda b, o: b[o]
u16 = lambda b, o: struct.unpack_from("<H", b, o)[0]
u32 = lambda b, o: struct.unpack_from("<I", b, o)[0]


def crc16(data, crc=0):
    for x in data:
        crc ^= x
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc


def crc(f: bytes) -> int:
    """CRC que el reloj valida (DialWriter.check_crc16): sobre f[512:], con f[884:916] en 0xFF."""
    d = bytearray(f[512:])
    d[372:404] = b"\xff" * 32
    c = crc16(d[1008:], crc16(d[12:752]))
    if c == 0:
        d[372] = 0
        c = crc16(d[1008:], crc16(d[12:752]))
    return c


def parse(f: bytes):
    addr = u32(f, 1552)
    w, h = u16(f, 1568), u16(f, 1570)
    o = BASE + addr
    off = dict(control_offset=u32(f, o), control_number=u16(f, o + 4), control_size=u16(f, o + 6),
               bitmap_index_offset=u32(f, o + 8), bitmap_index_number=u16(f, o + 12), bitmap_index_size=u16(f, o + 14),
               font_index_offset=u32(f, o + 16), font_index_number=u16(f, o + 20), font_ascii_number=u16(f, o + 22),
               font_index_size=u16(f, o + 24), reserved=u16(f, o + 26), bitmap_data_offset=u32(f, o + 28),
               preview_id=u16(f, o + 32), is_have_module=u8(f, o + 34), module_number=u8(f, o + 35))
    off["modules"] = [struct.unpack_from("<BBHHHH", f, o + 36 + 10 * i) for i in range(4)]
    controls = []
    for i in range(off["control_number"]):
        c = BASE + off["control_offset"] + off["control_size"] * i
        b = f[c:c + 32]
        controls.append(dict(index=b[0], control=b[1], enable=b[2], type=b[3], x=u16(b, 4), y=u16(b, 6),
                             w=u16(b, 8), h=u16(b, 10), image=u16(b, 12), align=b[14], style_lang=b[15],
                             number=b[16], module=b[17], style=b[18], reserved=b[19],
                             style_ids=list(struct.unpack_from("<6H", b, 20)), raw=b.hex()))
    bitmaps = []
    for i in range(off["bitmap_index_number"]):
        e = BASE + off["bitmap_index_offset"] + off["bitmap_index_size"] * i
        b = f[e:e + 12]
        bitmaps.append(dict(id=u16(b, 0), bpp=b[2], b3=b[3], w=u16(b, 4), h=u16(b, 6), addr=u32(b, 8)))
    return dict(addr=addr, lcd=(w, h), offset=off, controls=controls, bitmaps=bitmaps)


def rle_row(f: bytes, r: int, w: int):
    """Fila comprimida (b3=3): tokens u16 LE; 0xFnnn = repetir n veces el byte siguiente, si no n bytes literales."""
    out = []
    while len(out) < w:
        t = u16(f, r); r += 2
        if t & 0xF000 == 0xF000:
            out += [f[r]] * (t & 0xFFF); r += 1
        else:
            out += f[r:r + t]; r += t
    return out[:w]


def decode(f: bytes, bm):
    """Devuelve filas de (r,g,b,a) o None si el formato no se conoce."""
    a, w, h = BASE + bm["addr"], bm["w"], bm["h"]
    rows = [BASE + u32(f, a + 4 * y) for y in range(h)]
    out = []
    if bm["bpp"] == 1:
        for r in rows:
            line = []
            for x in range(w):
                v = (f[r + 2 * x] << 8) | f[r + 2 * x + 1]
                line.append(((v >> 11) << 3, ((v >> 5) & 63) << 2, (v & 31) << 3, 255))
            out.append(line)
        return out
    if bm["bpp"] == 8:
        p = a + (h + 1) * 4
        n = u16(f, p)
        pal = []
        for i in range(n):
            b0, b1 = f[p + 4 + 2 * i], f[p + 5 + 2 * i]
            v = (b0 << 8) | b1
            pal.append(((v >> 11) << 3, ((v >> 5) & 63) << 2, (v & 31) << 3, 255))
        for r in rows:
            idx = rle_row(f, r, w) if bm["b3"] == 3 else list(f[r:r + w])
            out.append([pal[i] if i < n else (0, 0, 0, 0) for i in idx])
        return out
    return None


if __name__ == "__main__":
    f = open(sys.argv[1], "rb").read()
    d = parse(f)
    print("len", len(f), "crc file", u16(f, 518), "crc calc", crc(f), "lcd", d["lcd"], "addr", d["addr"])
    print("custom num @1544:", f[1544:1547].hex(), " header[0:64]:", f[:64].hex())
    for k, v in d["offset"].items(): print(" ", k, v)
    for c in d["controls"]: print("C", {k: v for k, v in c.items() if k != "raw"})
    from collections import Counter
    print("bpp:", Counter(b["bpp"] for b in d["bitmaps"]))
    for b in d["bitmaps"][:400]: print("B", b)
    if len(sys.argv) > 2:
        from PIL import Image
        os.makedirs(sys.argv[2], exist_ok=True)
        for i, b in enumerate(d["bitmaps"]):
            px = decode(f, b)
            if px is None: continue
            im = Image.new("RGBA", (b["w"], b["h"]))
            im.putdata([p for row in px for p in row])
            im.save(os.path.join(sys.argv[2], f"{i:03d}_bpp{b['bpp']}.png"))


# ---------------------------------------------------------------- escritura

def rle_encode(idx):
    """Inverso de rle_row: repeticiones de 3+ como 0xFnnn+byte, el resto literal; fila alineada a 4 bytes."""
    out, i, n = bytearray(), 0, len(idx)
    lit = []

    def flush():
        while lit:
            chunk = lit[:0x7FF]
            del lit[:0x7FF]
            out.extend(struct.pack("<H", len(chunk)) + bytes(chunk))

    while i < n:
        j = i
        while j < n and idx[j] == idx[i] and j - i < 0xFFF:
            j += 1
        if j - i >= 3:
            flush()
            out.extend(struct.pack("<H", 0xF000 | (j - i)) + bytes([idx[i]]))
            i = j
        else:
            lit.append(idx[i]); i += 1
    flush()
    out.extend(b"\0" * (-len(out) % 4))
    return bytes(out)


def rgb565(r, g, b):
    return ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3)


def encode_bitmap(im, addr):
    """Imagen PIL → bytes de un bitmap bpp=8 comprimido. Píxeles con alfa ≤ 90 = negro (como hace el SDK)."""
    from PIL import Image
    im = im.convert("RGBA")
    w, h = im.size
    bg = Image.new("RGBA", im.size, (0, 0, 0, 255))
    rgb = Image.alpha_composite(bg, im.point(lambda v: v) if True else im).convert("RGB")
    # alfa bajo → negro puro
    a = im.getchannel("A").point(lambda v: 255 if v > 90 else 0)
    rgb = Image.composite(rgb, Image.new("RGB", im.size, 0), a)
    q = rgb.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    pal = q.getpalette()[:3 * 256]
    used = sorted(set(q.getdata()))
    remap = {old: new for new, old in enumerate(used)}
    colors = [rgb565(*pal[3 * u:3 * u + 3]) for u in used]
    data = list(q.getdata())
    rows = [rle_encode([remap[data[y * w + x]] for x in range(w)]) for y in range(h)]
    head = (h + 1) * 4 + 4 + len(colors) * 2
    head += -head % 4
    offs, pos = [], addr + head
    for r in rows:
        offs.append(pos); pos += len(r)
    blob = bytearray(struct.pack(f"<{h + 1}I", *offs, pos))
    blob += struct.pack("<HH", len(colors), 0) + b"".join(struct.pack(">H", c) for c in colors)
    blob += b"\0" * (addr + head - addr - len(blob))
    for r in rows:
        blob += r
    return bytes(blob)


def build(template: bytes, bitmaps, controls, fonts, preview_id):
    """Arma un .bin nuevo con la cabecera y el layout de una esfera oficial del mismo reloj.

    bitmaps: lista de imágenes PIL (el id es su posición); controls: dicts como los de parse();
    fonts: listas de (carácter ascii, id de bitmap).
    """
    t = parse(template)
    off = t["offset"]
    assert len(controls) * 32 <= off["bitmap_index_offset"] - off["control_offset"], "demasiados controles"
    assert len(bitmaps) * 12 <= off["font_index_offset"] - off["bitmap_index_offset"], "demasiados bitmaps"
    assert len(fonts) <= 32
    addr = t["addr"]
    gui = bytearray(template[BASE:BASE + off["bitmap_data_offset"]])
    gui[off["control_offset"]:off["bitmap_data_offset"]] = b"\0" * (off["bitmap_data_offset"] - off["control_offset"])
    struct.pack_into("<IHHIHHIHHHHIHBB", gui, addr, off["control_offset"], len(controls), 32,
                     off["bitmap_index_offset"], len(bitmaps), 12, off["font_index_offset"], len(fonts), 96, 4,
                     off["reserved"], off["bitmap_data_offset"], preview_id, 0, 0)
    gui[addr + 36:addr + 76] = b"\0" * 40
    for i, c in enumerate(controls):
        struct.pack_into("<BBBBHHHHHBBBBBB6H", gui, off["control_offset"] + 32 * i, i, c["control"], 1, c["type"],
                         c["x"], c["y"], c.get("w", 0), c.get("h", 0), c["image"], c.get("align", 0), 0, 0, 0, 0, 0,
                         *[0] * 6)
    for k, glyphs in enumerate(fonts):
        o = off["font_index_offset"] + 96 * 4 * k
        struct.pack_into("<HH", gui, o, len(glyphs), 0)
        for j, (ch, bid) in enumerate(glyphs):
            struct.pack_into("<HH", gui, o + 4 * (j + 1), ord(ch), bid)
    for i, im in enumerate(bitmaps):
        a = len(gui)
        data = encode_bitmap(im, a)
        struct.pack_into("<HBBHHI", gui, off["bitmap_index_offset"] + 12 * i, i, 8, 3, im.width, im.height, a)
        gui += data
        gui += b"\0" * (-len(gui) % 4)
    struct.pack_into("<I", gui, 12, len(gui))  # 1548: offset del "TBUI" final
    gui += b"TBUI"
    f = bytearray(template[:BASE]) + gui
    for o in (50, 66):
        struct.pack_into("<I", f, o, len(f) - 512)
    struct.pack_into("<I", f, 520, len(f) - BASE)
    struct.pack_into("<H", f, 518, crc(bytes(f)))
    return bytes(f)
