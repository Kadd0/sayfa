# SPDX-License-Identifier: GPL-3.0-or-later
"""Draws the launcher icon: a gold open book on a dark rounded square."""
import os, sys
from PIL import Image, ImageDraw

res = sys.argv[1]
for folder, size in [("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144)]:
    S = size * 8
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    m = S * 0.06
    d.rounded_rectangle([m, m, S - m, S - m], radius=S * 0.22, fill=(24, 26, 32, 255), outline=(224, 181, 106, 90), width=int(S * 0.012))
    gold, dim = (224, 181, 106, 255), (176, 138, 74, 255)
    cx, top, bot, w = S / 2, S * 0.30, S * 0.70, S * 0.27
    # two pages with a slight curve at the spine
    d.polygon([(cx, top + S * 0.04), (cx - w, top), (cx - w, bot), (cx, bot + S * 0.04)], fill=gold)
    d.polygon([(cx, top + S * 0.04), (cx + w, top), (cx + w, bot), (cx, bot + S * 0.04)], fill=dim)
    for i in range(3):
        y = top + S * (0.09 + i * 0.075)
        d.line([(cx - w + S * 0.05, y), (cx - S * 0.05, y + S * 0.012)], fill=(24, 26, 32, 160), width=int(S * 0.018))
        d.line([(cx + S * 0.05, y + S * 0.012), (cx + w - S * 0.05, y)], fill=(24, 26, 32, 120), width=int(S * 0.018))
    out = os.path.join(res, folder)
    os.makedirs(out, exist_ok=True)
    img.resize((size, size), Image.LANCZOS).save(os.path.join(out, "ic_launcher.png"))
print("ok")
