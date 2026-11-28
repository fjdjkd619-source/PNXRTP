#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ADAPTER=""

for arg in "$@"; do
  case "$arg" in
    --adapter=*) ADAPTER="${arg#--adapter=}" ;;
  esac
done

if [[ -z "$ADAPTER" ]]; then
  args=("$@")
  for ((i=0; i<${#args[@]}; i++)); do
    if [[ "${args[$i]}" == "--adapter" && $((i+1)) -lt ${#args[@]} ]]; then
      ADAPTER="${args[$((i+1))]}"
      break
    fi
  done
fi

[[ -n "$ADAPTER" ]] || { echo "Missing --adapter directory" >&2; exit 2; }
[[ -d "$ADAPTER/.git" ]] || { echo "Adapter directory is not a git checkout: $ADAPTER" >&2; exit 2; }

bash "$ROOT/apply-stages1-43.sh" "$@"

PATCH="$ROOT/patches/44-wal-compaction.patch"
git -C "$ADAPTER" apply --check "$PATCH"
git -C "$ADAPTER" apply "$PATCH"
git -C "$ADAPTER" diff --check

echo "Stages 1–44 source patches applied. WAL checkpoint compaction is crash-recoverable; long-term anchor-index retention remains under audit."
