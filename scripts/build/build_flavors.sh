#!/usr/bin/env bash
# ==============================================================================
# Multi-App Flavors Build Script: Sonora, Lyra, Aria, Chora
# Launches the interactive multi-app flavor builder with custom library locking.
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_SCRIPT="${SCRIPT_DIR}/build.sh"

if [[ ! -f "${TARGET_SCRIPT}" ]]; then
    echo "Error: Build script not found at ${TARGET_SCRIPT}" >&2
    exit 1
fi

chmod +x "${TARGET_SCRIPT}"
exec "${TARGET_SCRIPT}" 8 "$@"

