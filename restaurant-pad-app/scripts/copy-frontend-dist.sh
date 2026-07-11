#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAD_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${PAD_ROOT}/.." && pwd)"
FRONTEND_DIST="${REPO_ROOT}/frontend/dist"
ANDROID_ASSETS="${PAD_ROOT}/android/app/src/main/assets/web"

if [ ! -d "${FRONTEND_DIST}" ]; then
  echo "frontend/dist not found. Run: cd frontend && npm run build" >&2
  exit 1
fi

rm -rf "${ANDROID_ASSETS}"
mkdir -p "${ANDROID_ASSETS}"
cp -R "${FRONTEND_DIST}/." "${ANDROID_ASSETS}/"

echo "Copied ${FRONTEND_DIST} -> ${ANDROID_ASSETS}"
