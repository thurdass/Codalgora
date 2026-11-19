"""Check the packaged Codalgora window, icon and desktop launcher identity.

Run under a display: xvfb-run -a python3 desktop/packaging/tests/verify-linux-desktop.py
Pass --installed to check /opt/codalgora after installing the .deb.
Requires dpkg-deb and X11 utilities (xwininfo, xprop).
"""

import pathlib
import re
import subprocess
import sys
import tempfile
import time

root = pathlib.Path(__file__).resolve().parents[3]
expected_icon = root / "desktop/src/main/resources/io/codalgora/desktop/icons/codalgora.png"


def verify_window(app: pathlib.Path, package_root: pathlib.Path) -> None:
    desktop = next((app / "lib").glob("*.desktop"))
    entry = dict(line.split("=", 1) for line in desktop.read_text().splitlines() if "=" in line)
    icon = package_root / entry["Icon"].lstrip("/")
    assert icon.read_bytes() == expected_icon.read_bytes(), "Packaged icon differs from official logo"

    with tempfile.TemporaryFile() as log:
        process = subprocess.Popen([str(app / "bin/Codalgora")], stdout=log, stderr=log)
        try:
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    log.seek(0)
                    raise AssertionError("Installed application exited: " + log.read().decode())
                tree = subprocess.check_output(["xwininfo", "-root", "-tree"], text=True)
                match = re.search(r'(0x[0-9a-fA-F]+) "Codalgora —', tree)
                if match:
                    window = match.group(1)
                    class_property = subprocess.check_output(
                        ["xprop", "-id", window, "WM_CLASS"], text=True
                    )
                    classes = re.findall(r'"([^"]+)"', class_property)
                    assert entry["StartupWMClass"] in classes, class_property
                    icon_property = ""
                    icon_deadline = time.monotonic() + 5
                    while time.monotonic() < icon_deadline:
                        icon_property = subprocess.check_output(
                            ["xprop", "-id", window, "_NET_WM_ICON"], text=True
                        )
                        if "_NET_WM_ICON(CARDINAL) =" in icon_property:
                            break
                        time.sleep(0.1)
                    assert "_NET_WM_ICON(CARDINAL) =" in icon_property, icon_property
                    print("CODALGORA_DESKTOP_IDENTITY_OK: " + entry["StartupWMClass"])
                    break
                time.sleep(0.25)
            else:
                raise AssertionError("Installed window did not appear")
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if len(sys.argv) > 1 and sys.argv[1] == "--installed":
    verify_window(pathlib.Path("/opt/codalgora"), pathlib.Path("/"))
else:
    installers = list((root / "desktop/build/jpackage/installer").glob("codalgora_*_amd64.deb"))
    assert len(installers) == 1, f"Expected one Codalgora .deb, found {installers}"
    with tempfile.TemporaryDirectory(prefix="codalgora-desktop-") as directory:
        package_root = pathlib.Path(directory)
        subprocess.run(["dpkg-deb", "-x", str(installers[0]), directory], check=True)
        verify_window(package_root / "opt/codalgora", package_root)
