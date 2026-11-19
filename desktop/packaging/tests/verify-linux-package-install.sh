#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
mapfile -t installers < <(find "$root/desktop/build/jpackage/installer" -maxdepth 1 -name 'codalgora_*_amd64.deb' -type f)
if [[ ${#installers[@]} -ne 1 ]]; then
  echo "Expected exactly one Codalgora .deb, found ${#installers[@]}" >&2
  exit 1
fi
installer="${installers[0]}"
if dpkg-query -W -f='${Status}' codalgora 2>/dev/null | grep -q 'install ok installed'; then
  echo 'Codalgora is already installed on the disposable runner' >&2
  exit 1
fi

system_applications=/usr/local/share/applications
registered_launcher="$system_applications/codalgora-Codalgora.desktop"
sudo install -d -m 755 "$system_applications"
sudo install -d -m 755 /usr/local/share/desktop-directories
if [[ -e "$registered_launcher" ]]; then
  echo "Unexpected pre-existing launcher: $registered_launcher" >&2
  exit 1
fi

legacy_prefs="$HOME/.java/.userPrefs/io/algora/desktop"
sentinel="$legacy_prefs/codalgora-rebranding-preservation-check"
mkdir -p "$legacy_prefs"
printf '%s\n' 'preserve-existing-algora-preferences' > "$sentinel"

cleanup() {
  if dpkg-query -W -f='${Status}' codalgora 2>/dev/null | grep -q 'install ok installed'; then
    sudo env XDG_DATA_DIRS=/usr/local/share apt-get remove -y codalgora || true
  fi
}
trap cleanup EXIT

sudo env XDG_DATA_DIRS=/usr/local/share apt-get install -y "$installer"
expected_version="$(dpkg-deb -f "$installer" Version)"
actual_package="$(dpkg-query -W -f='${Package} ${Version} ${Architecture}' codalgora)"
[[ "$actual_package" == "codalgora $expected_version amd64" ]]
test -x /opt/codalgora/bin/Codalgora
desktop_entry="$(find /opt/codalgora/lib -maxdepth 1 -name '*.desktop' -print -quit)"
test -n "$desktop_entry"
grep -qx 'Name=Codalgora' "$desktop_entry"
grep -qx 'StartupWMClass=io.codalgora.desktop.CodalgoraApplication' "$desktop_entry"
test -f "$registered_launcher"
grep -qx 'Name=Codalgora' "$registered_launcher"

python3 "$root/desktop/packaging/tests/verify-linux-desktop.py" --installed
sudo env XDG_DATA_DIRS=/usr/local/share apt-get remove -y codalgora
trap - EXIT
test ! -e /opt/codalgora
test ! -e "$registered_launcher"
test -f "$sentinel"
grep -qx 'preserve-existing-algora-preferences' "$sentinel"
echo 'CODALGORA_DEB_INSTALL_LAUNCH_UNINSTALL_OK'
echo 'CODALGORA_LEGACY_PREFERENCES_PRESERVED_OK'
