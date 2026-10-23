"""Genera la esfera "Pip-Boy" (terminal verde fósforo) para el DW26 en el formato de esferas IDO.

Uso:  python3 -I tools/faces/pipboy/build.py
Salida: app/src/main/assets/faces/pipboy.zip (+ preview.png al lado, para la app)

Formato (copiado de las esferas oficiales del DW26): pantalla 240x284, iwf.json con widgets
posicionados, font.json, y una carpeta de PNG por fuente (un archivo por glifo, misma altura).
"""
import json
import os
import random
import sys
import zipfile
from io import BytesIO

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
FONT = os.path.join(HERE, "..", "ShareTechMono-Regular.ttf")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "faces")

W, H = 240, 284
GREEN = (26, 255, 128)
DIM = (12, 110, 56)
BG = (1, 12, 5)
NAME = "pipboy"


def font(size):
    return ImageFont.truetype(FONT, size)


def glow_text(img, xy, text, f, color=GREEN, anchor="la", glow=2):
    """Texto con halo, como el fósforo de un monitor CRT."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, text, font=f, fill=color + (255,), anchor=anchor)
    halo = layer.filter(ImageFilter.GaussianBlur(glow))
    img.alpha_composite(halo)
    img.alpha_composite(layer)


def glyph(ch, size, w, h, color=GREEN):
    """Un glifo centrado en una celda fija w×h, con halo; fondo transparente."""
    f = font(size)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glow_text(im, (w / 2, h / 2), ch, f, color, anchor="mm", glow=1.2)
    return im


def text_width(text, size):
    return int(font(size).getlength(text) + 0.999)


def background():
    im = Image.new("RGBA", (W, H), BG + (255,))
    d = ImageDraw.Draw(im)
    small, tiny = font(13), font(10)

    # barra de pestañas superior: STAT resaltada
    # la pantalla tiene esquinas redondeadas: todo lo de arriba/abajo va hacia el centro
    tabs = ["STAT", "INV", "DATA", "MAP", "RADIO"]
    gap = 10
    x = (W - (sum(font(13).getlength(t) for t in tabs) + gap * (len(tabs) - 1))) / 2
    for i, t in enumerate(tabs):
        tw = font(13).getlength(t)
        if i == 0:
            d.rectangle([x - 4, 10, x + tw + 4, 28], outline=GREEN, width=1)
        glow_text(im, (x, 12), t, small, GREEN if i == 0 else DIM)
        x += tw + gap
    d.line([16, 33, W - 16, 33], fill=GREEN, width=1)
    for tx in (16, W - 17):
        d.line([tx, 33, tx, 38], fill=GREEN, width=1)

    # subpestañas
    glow_text(im, (24, 40), "STATUS", tiny, GREEN, glow=1)
    glow_text(im, (W // 2, 40), "SPECIAL", tiny, DIM, anchor="ma", glow=1)
    glow_text(im, (W - 24, 40), "PERKS", tiny, DIM, anchor="ra", glow=1)

    # separador bajo la fecha
    d.line([10, 146, W - 10, 146], fill=DIM, width=1)

    # icono de radiación (trébol) a la izquierda
    cx, cy, r = 44, 196, 30
    d.ellipse([cx - r - 6, cy - r - 6, cx + r + 6, cy + r + 6], outline=DIM, width=2)
    for a in (90, 210, 330):
        d.pieslice([cx - r, cy - r, cx + r, cy + r], a - 30, a + 30, fill=GREEN)
    d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=BG)
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=GREEN)
    im.alpha_composite(im.filter(ImageFilter.GaussianBlur(1.5)).point(lambda v: v // 3))

    # etiquetas de las estadísticas (los valores los dibuja el reloj)
    for y, label, unit in [(156, "HP", "BPM"), (178, "AP", "%"), (200, "STEP", ""), (222, "KCAL", "")]:
        glow_text(im, (88, y + 2), label, small, DIM, glow=1)
        if unit:
            glow_text(im, (W - 12, y + 2), unit, small, DIM, anchor="ra", glow=1)

    # barra inferior estilo Pip-Boy
    d.rectangle([18, 244, W - 18, 268], fill=(6, 48, 24))
    glow_text(im, (24, 249), "TEMP", small, GREEN, glow=1)
    glow_text(im, (W - 24, 249), "LVL 26", small, GREEN, anchor="ra", glow=1)

    # líneas de barrido CRT y ruido leve
    sl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sl)
    for y in range(0, H, 3):
        sd.line([0, y, W, y], fill=(0, 0, 0, 70))
    rnd = random.Random(26)
    for _ in range(500):
        x, y = rnd.randrange(W), rnd.randrange(H)
        sd.point((x, y), fill=GREEN + (rnd.randrange(8, 28),))
    im.alpha_composite(sl)
    return im


def build():
    files = {}

    def png(path, im):
        b = BytesIO()
        im.save(b, "PNG")
        files[path] = b.getvalue()

    # fuentes: (carpeta, tamaño de letra, ancho de dígito, alto, glifos extra [(texto, ancho)])
    fonts = {
        "time": (70, 40, 72, [(":", 18)]),
        "sec": (20, 12, 22, []),
        "date": (20, 11, 22, [("-", 9)]),
        "stat": (19, 11, 20, [("-", 11)]),
        "temp": (18, 11, 20, [("-", 11), ("°C", 22), ("°F", 22)]),
    }
    for name, (size, dw, h, extra) in fonts.items():
        for i in range(10):
            png(f"{name}/{i}.png", glyph(str(i), size, dw, h))
        for j, (ch, w) in enumerate(extra):
            png(f"{name}/{10 + j}.png", glyph(ch, size, w, h))
    for d, t in zip(["mon", "tue", "wed", "thur", "fri", "sat", "sun"], ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]):
        png(f"week/en_{d}.png", glyph(t, 20, 40, 22))

    bg = background()
    png("background.png", bg)

    time_w = 4 * 40 + 18
    items = [
        {"widget": "custom", "type": "time", "isShowAddwh": True, "x": (W - time_w) // 2 - 8, "y": 56, "w": time_w, "h": 72,
         "fgcolor": "0xFFFFFFFF", "fgrender": "0xFFFFFFFF", "align": "left", "font": "time", "fontnum": 11},
        {"widget": "custom", "type": "second", "isShowAddwh": True, "x": (W + time_w) // 2 - 4, "y": 98, "w": 24, "h": 22,
         "fgcolor": "0xFFFFFFFF", "fgrender": "0xFFFFFFFF", "align": "left", "font": "sec", "fontnum": 10},
        {"widget": "custom", "type": "date", "x": 60, "y": 122, "w": 4 * 11 + 9, "h": 22,
         "fgcolor": "0xFFFFFFFF", "fgrender": "0xFFFFFFFF", "align": "left", "style": 1, "font": "date", "fontnum": 11},
        {"widget": "custom", "type": "week", "x": 130, "y": 122, "w": 40, "h": 22,
         "fgcolor": "0xFFFFFFFF", "fgrender": "0xFFFFFFFF", "align": "left", "style": 0, "font": "week", "fontnum": 7},
        {"widget": "custom", "type": "heartrate", "isShowAddwh": True, "x": 132, "y": 156, "w": 33, "h": 20,
         "fgcolor": "0xFFFFFFFF", "fgrender": "0xFFFFFFFF", "align": "center", "font": "stat", "fontnum": 11},
        {"widget": "custom", "type": "battery", "isShowAddwh": True, "x": 132, "y": 178, "w": 33, "h": 20,
         "fgcolor": "0xFFFFFFFF", "fgrender": "0xFFFFFFFF", "align": "center", "font": "stat", "fontnum": 11},
        {"widget": "custom", "type": "step", "isShowAddwh": True, "x": 140, "y": 200, "w": 55, "h": 20,
         "fgcolor": "0xFFFFFFFF", "fgrender": "0xFFFFFFFF", "align": "left", "font": "stat", "fontnum": 11},
        {"widget": "custom", "type": "calorie", "isShowAddwh": True, "x": 140, "y": 222, "w": 44, "h": 20,
         "fgcolor": "0xFFFFFFFF", "fgrender": "0xFFFFFFFF", "align": "left", "font": "stat", "fontnum": 11},
        {"widget": "custom", "type": "weather", "isShowAddwh": True, "x": 66, "y": 246, "w": 3 * 11 + 22, "h": 20,
         "fgcolor": "0xFFFFFFFF", "fgrender": "0xFFFFFFFF", "align": "left", "style": 2, "font": "temp", "fontnum": 13},
    ]

    # vista previa: el fondo con valores de ejemplo, escalado a 174x196 como las oficiales
    prev = bg.copy()
    pd_ = [("10:08", 70, (W // 2 - 8, 92)), ("26", 20, ((W + time_w) // 2 + 8, 109)), ("10-23", 20, (86, 133)), ("FRI", 20, (150, 133)),
           ("72", 19, (148, 166)), ("95", 19, (148, 188)), ("8417", 19, (164, 210)), ("312", 19, (158, 232)), ("28°C", 18, (92, 256))]
    for t, s, xy in pd_:
        glow_text(prev, xy, t, font(s), anchor="mm", glow=1.2)
    preview = prev.resize((174, 196), Image.LANCZOS)
    png("preview.png", preview)

    iwf = {"version": 1, "clouddialversion": 3, "preview": "preview.png", "name": NAME, "author": "DW26 app",
           "description": "Pip-Boy", "bluetooth": False, "disturb": False, "battery": False,
           "bkground": "background.png", "item": items}
    font_json = {"item": [{"name": n, "bpp": 16, "format": "png"} for n in list(fonts) + ["week"]]}
    files["iwf.json"] = json.dumps(iwf, indent=2).encode()
    files["font.json"] = json.dumps(font_json, indent=2).encode()

    os.makedirs(OUT, exist_ok=True)
    with zipfile.ZipFile(os.path.join(OUT, f"{NAME}.zip"), "w", zipfile.ZIP_DEFLATED) as z:
        for path, data in sorted(files.items()):
            z.writestr(path, data)
    prev.save(os.path.join(OUT, f"{NAME}.png"))  # vista grande para la app
    print(f"{NAME}.zip: {len(files)} archivos, {sum(len(v) for v in files.values()) // 1024} KB sin comprimir")


if __name__ == "__main__":
    sys.exit(build())
