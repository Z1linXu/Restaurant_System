#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAD_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${PAD_ROOT}/.." && pwd)"
BUILD_VARIANT="${1:-debug}"
MERCHANT="${2:-lanzhou}"
case "${MERCHANT}" in
  lanzhou) GRADLE_FLAVOR="Lanzhou" ;;
  allo) GRADLE_FLAVOR="Allo" ;;
  *) echo "Unknown merchant: ${MERCHANT}" >&2; exit 1 ;;
esac
export BUNDLED_ANDROID_FLAVOR="${MERCHANT}"

case "${BUILD_VARIANT}" in
  debug)
    GRADLE_TASK=":app:assemble${GRADLE_FLAVOR}Debug"
    ;;
  release)
    GRADLE_TASK=":app:assemble${GRADLE_FLAVOR}Release"
    ;;
  *)
    echo "Usage: $0 [debug|release] [lanzhou|allo]" >&2
    exit 1
    ;;
esac

if [ -z "${BUNDLED_BUILD_VERSION:-}" ]; then
  GIT_REVISION="$(git -C "${REPO_ROOT}" rev-parse --short HEAD 2>/dev/null || printf 'unknown')"
  BUNDLED_BUILD_VERSION="offline-pr7-${GIT_REVISION}-$(date -u +%Y%m%dT%H%M%SZ)"
fi
export BUNDLED_BUILD_VERSION
export VITE_APP_BUILD_VERSION="${BUNDLED_BUILD_VERSION}"

echo "Building frontend version ${BUNDLED_BUILD_VERSION}"
(
  cd "${REPO_ROOT}/frontend"
  if [ "${MERCHANT}" = allo ]; then
    export VITE_DEPLOYMENT_NAMESPACE=allo-nouilles
    npm run build -- --outDir "${PAD_ROOT}/web/allo-dist"
  else
    npm run build
  fi
)

"${SCRIPT_DIR}/copy-frontend-dist.sh"
"${SCRIPT_DIR}/verify-frontend-assets.sh"

(
  cd "${PAD_ROOT}/android"
  ./gradlew "${GRADLE_TASK}"
)

echo "Bundled Android ${BUILD_VARIANT} build completed."
