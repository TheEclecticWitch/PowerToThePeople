r"""Packs the Windows app as an MSIX for the Microsoft Store (the Store signs it, free).
Copied from Consciousness Theories.

Run from the project root after `gradlew :desktopApp:createDistributable`:  python tools/msix/build_msix.py
Uses Microsoft's packaging tools from C:\Users\rodkc\StoreTools\sdk (the Windows SDK Build Tools NuGet package).
Writes desktopApp/build/msix/PowerToThePeople-<version>.msix, ready to upload in Partner Center.
To try it on this PC (needs Windows Developer Mode), copy desktopApp/build/msix/pack somewhere else and run
`Add-AppxPackage -Register <that folder>\AppxManifest.xml`; `pack` itself is cleared by the next build.
"""
import glob, os, re, shutil, struct, subprocess

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TOOLS = sorted(glob.glob(r"C:\Users\rodkc\StoreTools\sdk\pkg\bin\*\x64"))[-1]
APP = os.path.join(ROOT, "desktopApp", "build", "compose", "binaries", "main", "app", "Power to the People")
OUT = os.path.join(ROOT, "desktopApp", "build", "msix")
STAGE = os.path.join(OUT, "pack")

gradle = open(os.path.join(ROOT, "desktopApp", "build.gradle.kts"), encoding="utf-8").read()
version = re.search(r'packageVersion = "([\d.]+)"', gradle).group(1)
version4 = ".".join((version.split(".") + ["0", "0", "0"])[:3]) + ".0"  # the Store wants the last number to be 0

def writable_then_retry(func, path, _):
    os.chmod(path, 0o666)  # the launcher is read-only, which stops a plain delete
    func(path)

if os.path.exists(STAGE):
    shutil.rmtree(STAGE, onexc=writable_then_retry)
for old in glob.glob(os.path.join(OUT, "*.msix")):
    os.remove(old)
os.makedirs(OUT, exist_ok=True)
shutil.copytree(APP, STAGE)
shutil.copytree(os.path.join(ROOT, "desktopApp", "msix", "Assets"), os.path.join(STAGE, "Assets"))
manifest = open(os.path.join(ROOT, "desktopApp", "msix", "AppxManifest.xml"), encoding="utf-8").read()
if "IDENTITY_NAME" in manifest:
    raise SystemExit("Put the Package/Identity/Name from Partner Center (Product identity) in desktopApp/msix/AppxManifest.xml first.")
with open(os.path.join(STAGE, "AppxManifest.xml"), "w", encoding="utf-8", newline="\r\n") as f:
    f.write(manifest.replace('Version="VERSION"', f'Version="{version4}"'))

def clear_stale_signature(path):
    """jpackage's launcher .exe starts as the JDK's signed launcher; after it writes our icon and version into it, the
    signature's location in the PE header points into the middle of the resources. Windows then calls the file
    invalid and signing fails (Store error "Sign returned error: 0x800700C1"). That old signature is broken anyway,
    so drop the pointer (and the checksum, which is only checked for drivers) when it doesn't sit at the file's end."""
    b = bytearray(open(path, "rb").read())
    pe = struct.unpack_from("<I", b, 0x3C)[0]
    opt = pe + 24
    security = opt + (112 if struct.unpack_from("<H", b, opt)[0] == 0x20B else 96) + 8 * 4
    where, size = struct.unpack_from("<II", b, security)
    if size and where + size != len(b):
        struct.pack_into("<II", b, security, 0, 0)
        struct.pack_into("<I", b, opt + 64, 0)
        os.chmod(path, 0o666)  # jpackage leaves the launcher read-only
        open(path, "wb").write(b)
        print("cleared a stale signature in", os.path.basename(path))

for exe in glob.glob(os.path.join(STAGE, "*.exe")):
    clear_stale_signature(exe)

def run(*args):
    subprocess.run([os.path.join(TOOLS, args[0]), *args[1:]], check=True, cwd=OUT, stdout=subprocess.DEVNULL)

# resources.pri lets Windows pick the right picture size (scale-200, targetsize-48 and so on).
run("makepri.exe", "createconfig", "/cf", "priconfig.xml", "/dq", "en-US", "/o")
run("makepri.exe", "new", "/pr", STAGE, "/cf", "priconfig.xml", "/of", os.path.join(STAGE, "resources.pri"), "/o")
msix = os.path.join(OUT, f"PowerToThePeople-{version4}.msix")
run("makeappx.exe", "pack", "/d", STAGE, "/p", msix, "/o")
print("wrote", os.path.relpath(msix, ROOT), f"({os.path.getsize(msix) // 1_000_000} MB)")
