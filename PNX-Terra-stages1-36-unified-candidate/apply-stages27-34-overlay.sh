#!/usr/bin/env bash
set -euo pipefail
usage(){ echo "Usage: $0 --adapter DIR" >&2; exit 2; }
ADAPTER=""
while (($#)); do case "$1" in --adapter) ADAPTER="${2:-}"; shift 2;; *) usage;; esac; done
[[ -n "$ADAPTER" && -d "$ADAPTER/.git" ]] || usage
ROOT="$(cd "$(dirname "$0")" && pwd)"
PATCH="$ROOT/patches/27-34-stage27-34-core.patch"
git -C "$ADAPTER" apply --check "$PATCH"
git -C "$ADAPTER" apply "$PATCH"
git -C "$ADAPTER" diff --check
echo "Stage 27–34 core overlay applied. PNX-specific entity/physics delegates and full upstream build/runtime certification remain separate gates."
