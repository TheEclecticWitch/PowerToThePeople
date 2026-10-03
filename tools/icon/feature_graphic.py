"""
Draws the Google Play feature graphic (1024 x 500): the app icon on deep navy with a few soft stripes of the
flag, the store name in the app's Caslon, and the tagline. Run from the project root:

    python tools/icon/feature_graphic.py docs/store/play/feature-graphic.png

Needs Pillow, and the icon made first: java tools/icon/MakeIcon.java docs/store/play/icon-512.png 512
"""
import math
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1024, 500
NAVY = (31, 58, 95)          # the icon's own navy, 0x1F3A5F
DEEP = (18, 33, 56)
RED = (122, 30, 44)          # a deep flag red; the flag never looks faded in this app
CREAM = (244, 238, 224)
GOLD = (214, 178, 102)
FONTS = "shared/src/commonMain/composeResources/font/"


def main(out):
    img = Image.new("RGB", (W, H), DEEP)
    # Waving stripes across the right side, kept dark so the words stay readable.
    stripes = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(stripes)
    band = 38
    for i in range(-2, 16):
        if i % 2:
            continue
        pts = []
        for x in range(0, W + 8, 8):
            y = i * band + 14 * math.sin(x / 140.0 + i * 0.3)
            pts.append((x, y))
        for x in range(W, -8, -8):
            y = i * band + band + 14 * math.sin(x / 140.0 + i * 0.3)
            pts.append((x, y))
        d.polygon(pts, fill=RED + (70,))
    stripes = stripes.filter(ImageFilter.GaussianBlur(1.2))
    img.paste(stripes, (0, 0), stripes)
    # A navy field behind the icon, like the flag's canton.
    canton = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(canton).rectangle((0, 0, 380, H), fill=NAVY + (235,))
    img.paste(canton, (0, 0), canton)

    icon = Image.open("docs/store/play/icon-512.png").convert("RGB").resize((280, 280), Image.LANCZOS)
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 279, 279), radius=56, fill=255)
    img.paste(icon, (50, (H - 280) // 2), mask)

    draw = ImageDraw.Draw(img)
    small = ImageFont.truetype(FONTS + "libre_caslon_text.ttf", 30)
    title = ImageFont.truetype(FONTS + "libre_caslon_display.ttf", 70)
    tagline = ImageFont.truetype(FONTS + "libre_caslon_text_italic.ttf", 32)
    x = 420
    draw.text((x, 128), "P2P", font=small, fill=GOLD)
    draw.text((x, 170), "Power to", font=title, fill=CREAM)
    draw.text((x, 248), "the People", font=title, fill=CREAM)
    draw.text((x, 346), "Your government, in plain words", font=tagline, fill=GOLD)
    img.save(out)


if __name__ == "__main__":
    main(sys.argv[1])
