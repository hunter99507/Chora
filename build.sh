#!/bin/bash

# Main entry point delegating to scripts/build/build.sh
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_SCRIPT="${SCRIPT_DIR}/scripts/build/build.sh"

if [[ ! -f "${TARGET_SCRIPT}" ]]; then
    echo "Error: Build script not found at ${TARGET_SCRIPT}" >&2
    exit 1
fi

chmod +x "${TARGET_SCRIPT}"
exec "${TARGET_SCRIPT}" "$@"
