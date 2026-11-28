#!/usr/bin/env bash
set -euo pipefail
# Backward-compatible entry point; the active chain now includes Stage 26.
exec "$(cd "$(dirname "$0")" && pwd)/apply-stages1-26.sh" "$@"
