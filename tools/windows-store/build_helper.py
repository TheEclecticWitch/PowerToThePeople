r"""Builds desktopApp/resources/windows/StoreHelper.exe - the Microsoft Store donations helper - with the
C# compiler that comes with Windows (.NET Framework 4), so no SDK or Visual Studio is needed.
Copied from Granny's Oldtime Cookbook.

Run from the project root:  python tools/windows-store/build_helper.py
The exe goes into desktopApp/resources/windows/, which only the Windows app carries (appResourcesRootDir);
the Mac build never gets it. Rebuild it after any change to desktopApp/windows-store/StoreHelper.cs.
"""
import os
import subprocess

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CSC = r"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
FACADES = r"C:\Program Files (x86)\Reference Assemblies\Microsoft\Framework\.NETFramework\v4.5.1\Facades"
WINMD = r"C:\Windows\System32\WinMetadata"
OUT_DIR = os.path.join(ROOT, "desktopApp", "resources", "windows")
os.makedirs(OUT_DIR, exist_ok=True)

refs = [
    os.path.join(WINMD, "Windows.Foundation.winmd"),
    os.path.join(WINMD, "Windows.Services.winmd"),
    os.path.join(WINMD, "Windows.ApplicationModel.winmd"),
    os.path.join(FACADES, "System.Runtime.dll"),
]
args = [CSC, "/nologo", "/target:exe", "/platform:anycpu", "/optimize+",
        "/out:" + os.path.join(OUT_DIR, "StoreHelper.exe")]
args += ["/reference:" + r for r in refs]
args.append(os.path.join(ROOT, "desktopApp", "windows-store", "StoreHelper.cs"))
subprocess.run(args, check=True)
print("built", os.path.relpath(os.path.join(OUT_DIR, "StoreHelper.exe"), ROOT))
