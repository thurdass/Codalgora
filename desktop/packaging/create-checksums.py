"""Write SHA256SUMS.txt for generated installer files in a package directory."""

import hashlib
import pathlib
import sys

directory = pathlib.Path(sys.argv[1])
installers = sorted(
    path
    for path in directory.iterdir()
    if path.is_file() and path.suffix.lower() in {".deb", ".exe", ".msi"}
)
if not installers:
    raise SystemExit(f"No installers found in {directory}")

lines = []
for installer in installers:
    digest = hashlib.sha256(installer.read_bytes()).hexdigest()
    lines.append(f"{digest}  {installer.name}")
(directory / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n", encoding="ascii")
print("\n".join(lines))
