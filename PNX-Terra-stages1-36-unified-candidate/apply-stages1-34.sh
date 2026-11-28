#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
"$ROOT/apply-stages1-26.sh" "$@"
# Stage 27–34 overlay is applied only after the exact Stage 1–26 chain succeeds.
# Parse the --adapter argument without evaluating or rewriting user input.
ADAPTER=""; args=("$@")
for ((i=0;i<${#args[@]};i++)); do
  if [[ "${args[$i]}" == "--adapter" ]]; then ADAPTER="${args[$((i+1))]:-}"; break; fi
done
[[ -n "$ADAPTER" ]] || { echo "Missing --adapter directory" >&2; exit 2; }
"$ROOT/apply-stages27-34-overlay.sh" --adapter "$ADAPTER"
echo "Stage 1–34 source patch application completed; this is not a build or runtime certification."
