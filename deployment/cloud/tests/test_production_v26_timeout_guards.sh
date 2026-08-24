#!/usr/bin/env bash
set -euo pipefail

if ! command -v timeout >/dev/null 2>&1; then
  printf 'Production V26 GNU timeout runtime test: N/A (GNU timeout unavailable on this host)\n'
  exit 0
fi

started="$(date +%s)"
set +e
timeout --foreground --kill-after=1s 1s sh -c 'sleep 30'
status=$?
set -e
elapsed=$(( $(date +%s) - started ))
[[ "$status" -eq 124 && "$elapsed" -lt 5 ]] || {
  printf 'bounded-command primitive failed: status=%s elapsed=%s\n' "$status" "$elapsed" >&2
  exit 1
}
printf 'Production V26 GNU timeout runtime test: PASS\n'
