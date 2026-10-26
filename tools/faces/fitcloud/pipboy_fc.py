"""Esfera "Pip-Boy" para relojes FitCloud de 368×448 (Amazfit Pop 2), en el formato .bin de fcdial.py.

Uso:  python3 -I tools/faces/fitcloud/pipboy_fc.py <esfera_oficial_del_reloj.bin>
      (la oficial solo aporta la cabecera y el layout; ver private/zepp/zepp_files/Download/ota.bin)
Salida: app/src/main/assets/faces/pipboy_pop2.bin + pipboy_pop2.png (vista para la app)

Controles (deducidos de una esfera oficial del Pop 2):
  0 fondo · 2 hora(2)/minuto(3)/fecha(14) con fuente · 3 datos con fuente: pasos(0) distancia(6) calorías(7) batería(15)
  4 imagen según valor: día de la semana(0, 14 imágenes zh/en) y AM/PM(6, 4 imágenes) · 6 bluetooth(0) y vista previa(1)
"""
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path[:0] = [HERE, os.path.join(HERE, "..", "pipboy")]
import fcdial  # noqa: E402
from build import font, glow_text, GREEN, DIM  # noqa: E402  mismo estilo que la Pip-Boy del DW26

W, H = 368, 448
BAR = (6, 48, 24)
BG = (0, 0, 0)  # negro puro: el reloj trata el negro de los glifos como fondo
NAME = "pipboy_pop2"

# posiciones
HOUR_X, TIME_Y, DIGIT_W, DIGIT_H, COLON_W = 50, 84, 60, 112, 28
MIN_X = HOUR_X + 2 * DIGIT_W + COLON_W
DATE_X, DATE_Y, SMALL_W, SMALL_H = 96, 208, 17, 30
WEEK_X, WEEK_W = 196, 66
AMPM_X, AMPM_Y = 322, 168
STAT_X = 214
STATS = [(262, "AP", 15, ""), (296, "STEP", 0, ""), (330, "KCAL", 7, ""), (364, "DIST", 6, "KM")]


def cell(text, size, w, h, color=GREEN):
    im = Image.new("RGBA", (w, h), BG + (255,))
    glow_text(im, (w / 2, h / 2), text, font(size), color, anchor="mm", glow=1.2)
    return im


def background():
    im = Image.new("RGBA", (W, H), BG + (255,))
    d = ImageDraw.Draw(im)
    tab, small = font(20), font(17)

    tabs = ["STAT", "INV", "DATA", "MAP", "RADIO"]
    gap = 14
    x = (W - (sum(tab.getlength(t) for t in tabs) + gap * (len(tabs) - 1))) / 2
    for i, t in enumerate(tabs):
        tw = tab.getlength(t)
        if i == 0:
            d.rectangle([x - 5, 16, x + tw + 5, 44], outline=GREEN, width=2)
        glow_text(im, (x, 19), t, tab, GREEN if i == 0 else DIM)
        x += tw + gap
    d.line([24, 52, W - 24, 52], fill=GREEN, width=2)
    for tx in (24, W - 25):
        d.line([tx, 52, tx, 60], fill=GREEN, width=2)
    glow_text(im, (34, 62), "STATUS", small, GREEN, glow=1)
    glow_text(im, (W // 2, 62), "SPECIAL", small, DIM, anchor="ma", glow=1)
    glow_text(im, (W - 34, 62), "PERKS", small, DIM, anchor="ra", glow=1)

    # dos puntos de la hora (los dígitos los dibuja el reloj)
    glow_text(im, (HOUR_X + 2 * DIGIT_W + COLON_W / 2, TIME_Y + DIGIT_H / 2 - 4), ":", font(100), GREEN, anchor="mm")

    d.line([16, 248, W - 16, 248], fill=DIM, width=2)

    # trébol de radiación
    cx, cy, r = 66, 326, 44
    d.ellipse([cx - r - 9, cy - r - 9, cx + r + 9, cy + r + 9], outline=DIM, width=3)
    for a in (90, 210, 330):
        d.pieslice([cx - r, cy - r, cx + r, cy + r], a - 30, a + 30, fill=GREEN)
    d.ellipse([cx - 13, cy - 13, cx + 13, cy + 13], fill=BG)
    d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=GREEN)

    for y, label, _, unit in STATS:
        glow_text(im, (134, y + 4), label, small, DIM, glow=1)
        if unit:
            glow_text(im, (W - 22, y + 4), unit, small, DIM, anchor="ra", glow=1)

    d.rectangle([40, 404, W - 40, 434], fill=BAR)
    glow_text(im, (W - 50, 409), "LVL 2", small, GREEN, anchor="ra", glow=1)

    # barrido CRT: líneas apenas más oscuras que el verde, ruido leve
    sl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sl)
    for y in range(0, H, 3):
        sd.line([0, y, W, y], fill=(0, 0, 0, 70))
    rnd = random.Random(26)
    for _ in range(900):
        sd.point((rnd.randrange(W), rnd.randrange(H)), fill=GREEN + (rnd.randrange(8, 28),))
    im.alpha_composite(sl)
    return im


def bt_icon(on):
    im = Image.new("RGBA", (24, 24), BAR + (255,))  # mismo color que la barra inferior donde va
    d = ImageDraw.Draw(im)
    c = GREEN if on else DIM
    d.line([(7, 7), (17, 16), (12, 21), (12, 3), (17, 8), (7, 17)], fill=c, width=2)
    if not on:
        d.line([(3, 21), (21, 3)], fill=GREEN, width=2)
    return im


def build(template_path):
    bitmaps, fonts, controls = [], [], []

    def add(im):
        bitmaps.append(im)
        return len(bitmaps) - 1

    def add_font(size, w, h, extra=()):
        glyphs = [(str(i), add(cell(str(i), size, w, h))) for i in range(10)]
        glyphs += [(ch, add(cell(img, size, w, h))) for ch, img in extra]
        fonts.append(glyphs)
        return len(fonts) - 1

    bg = background()
    controls.append(dict(control=0, type=0, x=0, y=0, image=add(bg)))

    f_time = add_font(108, DIGIT_W, DIGIT_H)
    f_date = add_font(28, SMALL_W, SMALL_H, [("/", "-")])
    f_stat = add_font(28, SMALL_W, SMALL_H, [("%", "%"), (".", ".")])
    controls += [
        dict(control=2, type=2, x=HOUR_X, y=TIME_Y, image=f_time),
        dict(control=2, type=3, x=MIN_X, y=TIME_Y, image=f_time),
        dict(control=2, type=14, x=DATE_X, y=DATE_Y, image=f_date, align=161),  # align igual que la oficial
    ]
    # día de la semana: lunes..domingo, cada uno en chino y en inglés (usamos el inglés en las dos)
    days = [add(cell(d, 28, WEEK_W, SMALL_H)) for d in ["MON", "MON", "TUE", "TUE", "WED", "WED", "THU", "THU", "FRI", "FRI", "SAT", "SAT", "SUN", "SUN"]][0]
    controls.append(dict(control=4, type=0, x=WEEK_X, y=DATE_Y, image=days))
    ampm = [add(cell(t, 22, 40, 24)) for t in ["AM", "AM", "PM", "PM"]][0]
    controls.append(dict(control=4, type=6, x=AMPM_X, y=AMPM_Y, image=ampm))
    for y, _, kind, _ in STATS:
        controls.append(dict(control=3, type=kind, x=STAT_X, y=y, image=f_stat))
    bt = [add(bt_icon(True)), add(bt_icon(False))][0]
    controls.append(dict(control=6, type=0, x=52, y=407, image=bt))

    # vista previa con valores de ejemplo, del tamaño de la oficial (247×300)
    prev = bg.copy()
    sample = [("10", 108, (HOUR_X + DIGIT_W, TIME_Y + DIGIT_H / 2)), ("08", 108, (MIN_X + DIGIT_W, TIME_Y + DIGIT_H / 2)),
              ("10-26", 28, (DATE_X + 2.5 * SMALL_W, DATE_Y + SMALL_H / 2)), ("MON", 28, (WEEK_X + WEEK_W / 2, DATE_Y + SMALL_H / 2)),
              ("AM", 22, (AMPM_X + 20, AMPM_Y + 12)), ("90%", 28, (STAT_X + 26, 277)), ("8417", 28, (STAT_X + 34, 311)),
              ("312", 28, (STAT_X + 26, 345)), ("5.62", 28, (STAT_X + 34, 379))]
    for t, s, xy in sample:
        glow_text(prev, xy, t, font(s), anchor="mm", glow=1.2)
    prev.alpha_composite(bt_icon(True), (52, 407))
    preview = add(prev.resize((247, 300), Image.LANCZOS))
    controls.insert(1, dict(control=6, type=1, x=60, y=74, image=preview))

    out = fcdial.build(open(template_path, "rb").read(), bitmaps, controls, fonts, preview)
    dest = os.path.join(ROOT, "app", "src", "main", "assets", "faces")
    os.makedirs(dest, exist_ok=True)
    open(os.path.join(dest, f"{NAME}.bin"), "wb").write(out)
    prev.save(os.path.join(dest, f"{NAME}.png"))
    print(f"{NAME}.bin: {len(out) // 1024} KB, {len(bitmaps)} imágenes, {len(controls)} controles, {len(fonts)} fuentes")


if __name__ == "__main__":
    build(sys.argv[1])
