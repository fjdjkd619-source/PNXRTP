#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
usage(){ echo "Usage: $0 --pnx DIR --terra DIR --adapter DIR [--apply]" >&2; exit 2; }
PNX=""; TERRA=""; ADAPTER=""; PNX_REF="${PNX_EXPECTED_REF:-c57499365737654eca32df008751ef6bca5af142}"; APPLY=0
while (($#)); do case "$1" in
  --pnx) PNX="${2:-}"; shift 2;; --terra) TERRA="${2:-}"; shift 2;; --adapter) ADAPTER="${2:-}"; shift 2;; --pnx-ref) PNX_REF="${2:-}"; shift 2;; --apply) APPLY=1; shift;; *) usage;; esac; done
[[ -n "$PNX" && -n "$TERRA" && -n "$ADAPTER" ]] || usage
for dir in "$PNX" "$TERRA" "$ADAPTER"; do [[ -x "$dir/gradlew" ]] || { echo "Missing Gradle wrapper in $dir" >&2; exit 1; }; done
if (( APPLY )); then PNX_EXPECTED_REF="$PNX_REF" "$ROOT/apply-stages1-36.sh" --pnx "$PNX" --terra "$TERRA" --adapter "$ADAPTER" --pnx-ref "$PNX_REF"; fi
for spec in "$PNX:$PNX_REF:PowerNukkitX" "$TERRA:016961c19c41c2501737599175937ae40699beb1:Terra" "$ADAPTER:74318b4a1eae95322550f12d5eb8a76d92a93731:PNX-Terra"; do
  IFS=: read -r dir expected name <<< "$spec"
  actual="$(git -C "$dir" rev-parse HEAD)"
  [[ "$actual" == "$expected" ]] || { echo "$name ref mismatch; expected $expected got $actual" >&2; exit 1; }
done
# Fail closed: build each actual modified checkout; do not treat stub contracts as integration.
(cd "$TERRA" && ./gradlew --no-daemon --no-configuration-cache --max-workers=1 clean test build publishToMavenLocal --stacktrace)
(cd "$PNX" && ./gradlew --no-daemon --no-configuration-cache --max-workers=1 clean test build --stacktrace)
(cd "$ADAPTER" && ./gradlew --no-daemon --no-configuration-cache --max-workers=1 clean test build --stacktrace)
echo "UPSTREAM_BUILD=PASS"
