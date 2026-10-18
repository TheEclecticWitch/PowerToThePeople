"""Makes the desktop and Microsoft Store pictures from the app icon that MakeIcon.java draws.

Run from the project root:  python tools/icon/desktop_icons.py
Writes the window icon, the Windows (.ico) and Mac (.icns) app icons, the MSIX tiles that
tools/msix/build_msix.py packs, and the Microsoft Store box art. Laid out as Consciousness Theories does it.
"""
import os, subprocess, tempfile
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
JAVA = r"C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
NAVY = (0x1F, 0x3A, 0x5F, 255)  # MakeIcon's background, so wide tiles join up with the square art


def save(img, *parts, **options):
    path = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, **options)


with tempfile.TemporaryDirectory() as tmp:
    png = os.path.join(tmp, "icon.png")
    subprocess.run([JAVA, os.path.join(ROOT, "tools", "icon", "MakeIcon.java"), png, "1024"], check=True)
    full = Image.open(png).convert("RGBA")
    full.load()


def square(px):
    return full.resize((px, px), Image.LANCZOS)


def wide(width, height):
    """The icon centred on navy, on a tile of any shape."""
    img = Image.new("RGBA", (width, height), NAVY)
    art = square(min(width, height))
    img.alpha_composite(art, ((width - art.width) // 2, (height - art.height) // 2))
    return img


# The window's own icon, and the installers' icons
save(square(512), "desktopApp", "src", "main", "resources", "icon.png")
save(full, "desktopApp", "icons", "icon.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
save(full, "desktopApp", "icons", "icon.icns")

# Microsoft Store (MSIX): Start-menu tiles and the Store logo at 100/200/400% scaling, plus the taskbar sizes
assets = ("desktopApp", "msix", "Assets")
for scale in (100, 200, 400):
    k = scale / 100
    save(square(round(150 * k)), *assets, f"Square150x150Logo.scale-{scale}.png")
    save(square(round(44 * k)), *assets, f"Square44x44Logo.scale-{scale}.png")
    save(square(round(50 * k)), *assets, f"StoreLogo.scale-{scale}.png")
    save(wide(round(310 * k), round(150 * k)), *assets, f"Wide310x150Logo.scale-{scale}.png")
for px in (16, 24, 32, 48, 256):
    save(square(px), *assets, f"Square44x44Logo.targetsize-{px}_altform-unplated.png")

# Store listing artwork: box art (1:1) and poster art (2:3)
save(wide(2160, 2160).convert("RGB"), "docs", "store", "microsoft", "box-art-2160.png")
save(wide(1440, 2160).convert("RGB"), "docs", "store", "microsoft", "poster-art-1440x2160.png")
print("desktop and Microsoft Store icons written")
