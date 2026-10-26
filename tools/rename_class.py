#!/usr/bin/env python3
"""Renombra una clase en un árbol de .class (solo el constant pool: nombres y descriptores).

Uso: rename_class.py <carpeta> <viejo/Nombre> <nuevo/Nombre>
Lo usa tools/extract_zepp.sh: D8 rechaza clases "$$ExternalSynthetic…" que R8 de otra app dejó en paquetes ajenos.
"""
import os, struct, sys

# tamaño en bytes de cada tipo de entrada del constant pool (sin el tag); 1 = Utf8 (largo variable)
SIZES = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}


def rename(data: bytes, old: bytes, new: bytes) -> bytes:
    count = struct.unpack_from(">H", data, 8)[0]
    out, i, n = bytearray(data[:10]), 10, 1
    while n < count:
        tag = data[i]
        if tag == 1:
            length = struct.unpack_from(">H", data, i + 1)[0]
            s = data[i + 3:i + 3 + length]
            if s == old or b"L" + old + b";" in s:
                s = new if s == old else s.replace(b"L" + old + b";", b"L" + new + b";")
            out += bytes([1]) + struct.pack(">H", len(s)) + s
            i += 3 + length
        else:
            out += data[i:i + 1 + SIZES[tag]]
            i += 1 + SIZES[tag]
        n += 2 if tag in (5, 6) else 1  # long/double ocupan dos ranuras
    return bytes(out + data[i:])


def main(root, old, new):
    old_b, new_b = old.encode(), new.encode()
    for d, _, files in os.walk(root):
        for f in files:
            if not f.endswith(".class"):
                continue
            p = os.path.join(d, f)
            data = open(p, "rb").read()
            if old_b not in data:
                continue
            open(p, "wb").write(rename(data, old_b, new_b))
    src = os.path.join(root, old + ".class")
    if os.path.exists(src):
        dst = os.path.join(root, new + ".class")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        os.replace(src, dst)


if __name__ == "__main__":
    main(*sys.argv[1:4])
