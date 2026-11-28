#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/dist}"
if grep -Eq 'BLOCKED|not a complete integrated build|Do not deploy' "$ROOT/STATUS.md"; then
  echo "Release refused: STATUS.md still marks production integration as blocked." >&2
  exit 1
fi
if [[ ! -f "$ROOT/validation/UPSTREAM-BUILD-PASS" || ! -f "$ROOT/validation/RUNTIME-CERTIFICATION-PASS" ]]; then
  echo "Release refused: upstream build and runtime certification markers are required." >&2
  exit 1
fi
mkdir -p "$OUT"
(cd "$ROOT" && ./validation/run-core-tests.sh)
(cd "$ROOT" && find source -name '*.java' -print0 | sort -z | xargs -0 sha256sum > "$OUT/SOURCE-SHA256SUMS.txt")
(cd "$ROOT" && zip -qr "$OUT/PNX-Terra-integrated-release.zip" source patches tests validation README.md STATUS.md)
sha256sum "$OUT/PNX-Terra-integrated-release.zip" > "$OUT/PNX-Terra-integrated-release.zip.sha256"
echo "RELEASE_PACKAGE=PASS"
