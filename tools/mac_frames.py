"""Puts the Mac App Store screenshots (from MacStoreShots) in a Mac window at 2880 x 1800.

    python tools/mac_frames.py <folder of raw shots> <output folder>

Each raw shot is the app's window contents at 2560 x 1520 (1280 x 760 points on a Retina screen).
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

W, H = 2880, 1800
BAR = 56            # the title bar, 28 points
RADIUS = 24
TITLE = "Power to the People"

def background():
    # A deep navy that fades to the flag's dark red, behind the window.
    top, bottom = (22, 30, 52), (58, 24, 34)
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img

def frame(shot: Image.Image) -> Image.Image:
    cw, ch = shot.size
    ww, wh = cw, ch + BAR
    x, y = (W - ww) // 2, (H - wh) // 2 + 8
    canvas = background().convert("RGBA")

    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([x, y + 24, x + ww, y + wh + 24], RADIUS, fill=(0, 0, 0, 150))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(40)))

    window = Image.new("RGBA", (ww, wh), (30, 32, 38, 255))
    d = ImageDraw.Draw(window)
    for i, color in enumerate([(255, 95, 87), (254, 188, 46), (40, 200, 64)]):
        cx = 40 + i * 40
        d.ellipse([cx - 12, BAR // 2 - 12, cx + 12, BAR // 2 + 12], fill=color)
    try:
        from PIL import ImageFont
        font = ImageFont.truetype("C:/Windows/Fonts/seguisb.ttf", 26)
        tw = d.textlength(TITLE, font=font)
        d.text(((ww - tw) / 2, BAR / 2 - 17), TITLE, fill=(220, 220, 225), font=font)
    except OSError:
        pass
    window.paste(shot.convert("RGBA"), (0, BAR))
    mask = Image.new("L", (ww, wh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, ww - 1, wh - 1], RADIUS, fill=255)
    canvas.paste(window, (x, y), mask)
    return canvas.convert("RGB")

if __name__ == "__main__":
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    dst.mkdir(parents=True, exist_ok=True)
    for f in sorted(src.glob("*.png")):
        out = frame(Image.open(f))
        assert out.size == (W, H)
        out.save(dst / f.name)
        print(dst / f.name)
