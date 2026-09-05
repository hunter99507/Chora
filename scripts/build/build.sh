#!/bin/bash

# ============================================================
#  CHORA BUILD SYSTEM  ·  Android APK Build & Deployment Tool
# ============================================================

# Prevent Git from invoking an interactive pager (e.g. less)
export PAGER=cat
export GIT_PAGER=cat

# ── Colors & Styles ──────────────────────────────────────────
RESET="\e[0m"
BOLD="\e[1m"
DIM="\e[2m"

WHITE="\e[1;37m"
RED="\e[91m"
GREEN="\e[92m"
YELLOW="\e[93m"
BLUE="\e[94m"
MAGENTA="\e[95m"
CYAN="\e[96m"

# ── Paths ───────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if git -C "${SCRIPT_DIR}" rev-parse --show-toplevel >/dev/null 2>&1; then
    PROJECT_ROOT="$(git -C "${SCRIPT_DIR}" rev-parse --show-toplevel)"
elif [[ -f "${SCRIPT_DIR}/gradlew" ]]; then
    PROJECT_ROOT="${SCRIPT_DIR}"
elif [[ -f "${SCRIPT_DIR}/../../gradlew" ]]; then
    PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
else
    PROJECT_ROOT="$(pwd)"
fi
CONFIG_FILE="${PROJECT_ROOT}/.build_config"
if [[ -f "${CONFIG_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${CONFIG_FILE}"
fi

LOG_DIR="${PROJECT_ROOT}/logs"
TRANSFER_DIR="${TRANSFER_DIR:-/media/smb-ubuntu/Server Downloads/transfer}"
RELEASE_APK="${PROJECT_ROOT}/app/build/outputs/apk/chora/release/app-chora-release.apk"
DEBUG_APK="${PROJECT_ROOT}/app/build/outputs/apk/chora/debug/app-chora-debug.apk"

mkdir -p "${LOG_DIR}"

# ── Helper: print a full-width horizontal rule ───────────────
rule() {
    local char="${1:-─}"
    local color="${2:-}"
    printf "%b" "${color}"
    printf '%*s' "${COLUMNS:-80}" '' | tr ' ' "$char"
    printf "%b\n" "${RESET}"
}

# ── Helper: centered text ────────────────────────────────────
center() {
    local text="$1"
    local color="${2:-}"
    local len=${#text}
    local width=${COLUMNS:-80}
    local pad=$(( (width - len) / 2 ))
    printf "%${pad}s%b%s%b\n" "" "${color}" "$text" "${RESET}"
}

# ── Helper: fancy label + value line ─────────────────────────
info_line() {
    printf "  %b%-24s%b  %b%s%b\n" "${DIM}${CYAN}" "$1" "${RESET}" "${WHITE}" "$2" "${RESET}"
}

# ── Helper: step indicator ────────────────────────────────────
step() {
    printf "\n  %b❯%b  %b%s%b\n" "${BOLD}${CYAN}" "${RESET}" "${BOLD}${WHITE}" "$1" "${RESET}"
}

# ── Helper: success line ──────────────────────────────────────
ok() {
    printf "     %b✔%b  %b%s%b\n" "${GREEN}" "${RESET}" "${WHITE}" "$1" "${RESET}"
}

# ── Helper: warning line ──────────────────────────────────────
warn() {
    printf "     %b⚠%b  %b%s%b\n" "${YELLOW}" "${RESET}" "${YELLOW}" "$1" "${RESET}"
}

# ── Helper: error line ────────────────────────────────────────
err() {
    printf "     %b✖%b  %b%s%b\n" "${RED}" "${RESET}" "${RED}" "$1" "${RESET}"
}

# ── Helper: prompt ────────────────────────────────────────────
prompt() {
    printf "\n  %b?%b  %b%s%b  %b" "${BOLD}${MAGENTA}" "${RESET}" "${BOLD}${WHITE}" "$1" "${RESET}" "${DIM}"
}

# ── Helper: Gradle task runner with live progress spinner ────
run_gradle_task() {
    local task_name="$1"
    local log_file="$2"
    shift 2
    local gradle_args=("$@")

    local frames=('⠋' '⠙' '⠹' '⠸' '⠼' '⠴' '⠦' '⠧' '⠇' '⠏')
    local i=0

    # Ensure gradlew is executable
    chmod +x "${PROJECT_ROOT}/gradlew"

    cd "${PROJECT_ROOT}" || exit 1

    # Activate virtual environment if present (supports both matt and hunter99507 environments)
    if [[ -f "/home/matt/Documents/venv/bin/activate" ]]; then
        # shellcheck disable=SC1091
        source "/home/matt/Documents/venv/bin/activate" 2>/dev/null
    elif [[ -f "/home/hunter99507/Documents/Scripts/venv/env/bin/activate" ]]; then
        # shellcheck disable=SC1091
        source "/home/hunter99507/Documents/Scripts/venv/env/bin/activate" 2>/dev/null
    elif [[ -n "${VIRTUAL_ENV:-}" && -f "${VIRTUAL_ENV}/bin/activate" ]]; then
        # shellcheck disable=SC1091
        source "${VIRTUAL_ENV}/bin/activate" 2>/dev/null
    fi

    # Launch gradle command in background
    ./gradlew "${gradle_args[@]}" > "${log_file}" 2>&1 &
    local pid=$!

    # Trap INT and TERM to clean up background Gradle process on abort
    trap 'if kill -0 "$pid" 2>/dev/null; then kill "$pid" 2>/dev/null; fi; printf "\r\e[K"; err "Process interrupted by user."; exit 130' INT TERM

    while kill -0 "$pid" 2>/dev/null; do
        local last_task
        last_task=$(grep -o '> Task [^ ]*' "${log_file}" 2>/dev/null | tail -1 | tr -d '\r\n')
        if [[ -n "$last_task" ]]; then
            printf "\r  %b%s%b  %bBuilding%b %b%s%b  %b(%s)%b\e[K" "${CYAN}" "${frames[$i]}" "${RESET}" "${DIM}" "${RESET}" "${WHITE}" "${task_name}" "${RESET}" "${DIM}${CYAN}" "${last_task}" "${RESET}"
        else
            printf "\r  %b%s%b  %bBuilding%b %b%s%b\e[K" "${CYAN}" "${frames[$i]}" "${RESET}" "${DIM}" "${RESET}" "${WHITE}" "${task_name}" "${RESET}"
        fi
        i=$(( (i + 1) % 10 ))
        sleep 0.15
    done

    wait "$pid"
    local exit_code=$?
    trap - INT TERM

    # Deactivate Python virtual environment if one was activated
    type deactivate >/dev/null 2>&1 && deactivate

    printf "\r\e[K"

    if [[ $exit_code -eq 0 ]]; then
        ok "${task_name} finished successfully!"
        return 0
    else
        err "${task_name} failed with exit code ${exit_code}."
        echo ""
        warn "Last 25 lines of Gradle log (${log_file}):"
        tail -n 25 "${log_file}" | while IFS= read -r line; do
            printf "       %b%s%b\n" "${DIM}${RED}" "$line" "${RESET}"
        done
        ERRORS=$((ERRORS + 1))
        return 1
    fi
}

# ── Helper: Git stage, commit, and push ───────────────────────
git_push_task() {
    local commit_msg="${1:-}"

    cd "${PROJECT_ROOT}" || exit 1

    echo ""
    rule "─" "${DIM}${BLUE}"
    echo ""
    step "Git Operations"
    printf "\n"

    local status_output
    status_output=$(git --no-pager status --short)

    if [[ -z "$status_output" ]]; then
        warn "No uncommitted local changes found."
        prompt "Push existing commits to remote anyway? (yes / no):"
        read -r PUSH_ANYWAY
        printf "%b\n" "${RESET}"
        if [[ "$PUSH_ANYWAY" != "yes" && "$PUSH_ANYWAY" != "y" ]]; then
            ok "Git push skipped."
            return 0
        fi
    else
        info_line "Modified Files:" "$(echo "$status_output" | wc -l) files detected"
        echo ""
        git --no-pager status --short | while IFS= read -r line; do
            printf "     %b%s%b\n" "${DIM}${CYAN}" "$line" "${RESET}"
        done
        echo ""

        if [[ -z "$commit_msg" ]]; then
            prompt "Enter commit message (Press Enter for default):"
            read -r USER_MSG
            printf "%b\n" "${RESET}"
            if [[ -n "$USER_MSG" ]]; then
                commit_msg="$USER_MSG"
            else
                commit_msg="Update Chora build and scripts"
            fi
        fi

        step "Staging files (git add .)..."
        if git --no-pager add .; then
            ok "Staged all changes."
        else
            err "Failed to stage changes with git add ."
            ERRORS=$((ERRORS + 1))
            return 1
        fi

        step "Committing: \"${commit_msg}\"..."
        if git --no-pager commit -m "${commit_msg}"; then
            ok "Commit created successfully."
        else
            err "Git commit failed."
            ERRORS=$((ERRORS + 1))
            return 1
        fi
    fi

    step "Pushing to GitHub..."
    local current_branch
    current_branch=$(git --no-pager branch --show-current 2>/dev/null)
    if [[ -z "$current_branch" ]]; then
        current_branch="master"
    fi

    info_line "Branch:" "${current_branch}"
    info_line "Remote:" "origin (${current_branch})"
    echo ""

    if git --no-pager push origin "${current_branch}"; then
        ok "Successfully pushed to GitHub (${current_branch})!"
        return 0
    else
        err "Failed to push to GitHub. Check network or credentials."
        ERRORS=$((ERRORS + 1))
        return 1
    fi
}

# ============================================================
#  BANNER
# ============================================================
clear

echo ""
rule "═" "${BOLD}${CYAN}"
echo ""
center "  ██████╗██╗  ██╗ ██████╗ ██████╗  █████╗   " "${CYAN}"
center " ██╔════╝██║  ██║██╔═══██╗██╔══██╗██╔══██╗  " "${CYAN}"
center " ██║     ███████║██║   ██║██████╔╝███████║  " "${BOLD}${CYAN}"
center " ██║     ██╔══██║██║   ██║██╔══██╗██╔══██║  " "${CYAN}"
center " ╚██████╗██║  ██║╚██████╔╝██║  ██║██║  ██║  " "${BOLD}${BLUE}"
center "  ╚═════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝  " "${BLUE}"
echo ""
center "A N D R O I D   B U I L D   S Y S T E M" "${DIM}${CYAN}"
echo ""
rule "═" "${BOLD}${CYAN}"
echo ""
center "Chora Music Player · Automated Build, Deploy & Git Tool" "${DIM}${WHITE}"
echo ""
rule "─" "${DIM}${BLUE}"
echo ""

ERRORS=0

# ============================================================
#  STEP 1 — System Verification
# ============================================================
step "System Verification"
printf "\n"

# Verify gradlew exists
if [[ -f "${PROJECT_ROOT}/gradlew" ]]; then
    ok "Found Gradle Wrapper at ${PROJECT_ROOT}/gradlew"
else
    err "Gradle wrapper not found in ${PROJECT_ROOT}!"
    exit 1
fi

# Verify Java
if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    ok "Java runtime detected: ${JAVA_VER}"
else
    err "Java is not installed or not in PATH!"
    exit 1
fi

# Check transfer folder
if [[ -d "${TRANSFER_DIR}" ]]; then
    ok "Deployment directory accessible: ${TRANSFER_DIR}"
else
    warn "Transfer directory not mounted: ${TRANSFER_DIR} (Deployment will be skipped)"
fi

# Check git status
if command -v git >/dev/null 2>&1; then
    GIT_BRANCH=$(cd "${PROJECT_ROOT}" && git --no-pager branch --show-current 2>/dev/null || echo "master")
    ok "Git repository detected (branch: ${GIT_BRANCH})"
fi

# ============================================================
#  STEP 2 — Target Selection
# ============================================================
echo ""
rule "─" "${DIM}${BLUE}"
echo ""
step "Select Operation"
echo ""

CHOICE="${1:-}"
[[ $# -gt 0 ]] && shift
EXTRA_ARGS=("$@")

if [[ -z "$CHOICE" ]]; then
    printf "  %b1)%b  %bBuild Release APK%b   %b(assembleRelease + Deploy to Server)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${GREEN}" "${RESET}"
    printf "  %b2)%b  %bBuild Debug APK%b     %b(assembleDebug)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${CYAN}" "${RESET}"
    printf "  %b3)%b  %bClean & Rebuild%b     %b(clean + assembleRelease + Deploy)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${YELLOW}" "${RESET}"
    printf "  %b4)%b  %bInstall to Device%b   %b(assembleDebug + adb install -r)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${MAGENTA}" "${RESET}"
    printf "  %b5)%b  %bPush to GitHub%b      %b(git add . + commit + push)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${CYAN}" "${RESET}"
    printf "  %b6)%b  %bBuild & Push Both%b   %b(Release Build + Deploy + Push to GitHub)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${GREEN}" "${RESET}"
    printf "  %b7)%b  %bSet Deploy Folder%b   %b(Current: %s)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${YELLOW}" "${TRANSFER_DIR}" "${RESET}"
    printf "  %b8)%b  %bMulti-App Flavors%b   %b(Build Sonora, Lyra, Aria with library lock)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${MAGENTA}" "${RESET}"
    printf "  %b9)%b  %bExit%b\n" "${BOLD}${RED}" "${RESET}" "${WHITE}" "${RESET}"
    echo ""
    prompt "Enter choice [1-9]:"
    read -r CHOICE
    printf "%b\n" "${RESET}"
fi

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DO_BUILD=false
DO_GIT_PUSH=false
IS_FLAVOR_BUILD=false
GRADLE_EXTRA_PROPS=()
COMMIT_MSG="${EXTRA_ARGS[*]:-}"

case "$CHOICE" in
    1|release|--release)
        DO_BUILD=true
        BUILD_TYPE="Release"
        GRADLE_TASKS=("assembleChoraRelease")
        LOG_FILE="${LOG_DIR}/build_release_${TIMESTAMP}.log"
        TARGET_APK="${RELEASE_APK}"
        DO_DEPLOY=true
        ;;
    2|debug|--debug)
        DO_BUILD=true
        BUILD_TYPE="Debug"
        GRADLE_TASKS=("assembleChoraDebug")
        LOG_FILE="${LOG_DIR}/build_debug_${TIMESTAMP}.log"
        TARGET_APK="${DEBUG_APK}"
        DO_DEPLOY=false
        ;;
    3|clean|--clean)
        DO_BUILD=true
        BUILD_TYPE="Clean & Rebuild Release"
        GRADLE_TASKS=("clean" "assembleChoraRelease")
        LOG_FILE="${LOG_DIR}/build_clean_release_${TIMESTAMP}.log"
        TARGET_APK="${RELEASE_APK}"
        DO_DEPLOY=true
        ;;
    4|install|--install)
        DO_BUILD=true
        BUILD_TYPE="Install Debug to ADB Device"
        GRADLE_TASKS=("assembleChoraDebug")
        LOG_FILE="${LOG_DIR}/build_install_${TIMESTAMP}.log"
        TARGET_APK="${DEBUG_APK}"
        DO_DEPLOY=false
        DO_ADB_INSTALL=true
        ;;
    5|push|git|--push|--git)
        DO_BUILD=false
        DO_GIT_PUSH=true
        ;;
    6|all|both|--all)
        DO_BUILD=true
        DO_GIT_PUSH=true
        BUILD_TYPE="Release"
        GRADLE_TASKS=("assembleChoraRelease")
        LOG_FILE="${LOG_DIR}/build_release_${TIMESTAMP}.log"
        TARGET_APK="${RELEASE_APK}"
        DO_DEPLOY=true
        ;;
    7|config|dir|deploy-dir|--config|--set-dir)
        echo ""
        rule "─" "${DIM}${BLUE}"
        echo ""
        step "Configure Deploy APK Directory"
        echo ""
        info_line "Current Directory:" "${TRANSFER_DIR}"
        echo ""
        NEW_DIR="${EXTRA_ARGS[0]:-}"
        if [[ -z "$NEW_DIR" ]]; then
            prompt "Enter new deployment folder path (or press Enter to cancel):"
            read -r NEW_DIR
            printf "%b\n" "${RESET}"
        fi
        if [[ -n "$NEW_DIR" ]]; then
            NEW_DIR="${NEW_DIR/#\~/$HOME}"
            if [[ ! -d "$NEW_DIR" ]]; then
                prompt "Directory does not exist. Create it now? (y / n):"
                read -r CREATE_DIR
                printf "%b\n" "${RESET}"
                if [[ "$CREATE_DIR" == "y" || "$CREATE_DIR" == "yes" ]]; then
                    mkdir -p "$NEW_DIR"
                    ok "Created directory: ${NEW_DIR}"
                fi
            fi
            echo "TRANSFER_DIR=\"${NEW_DIR}\"" > "${CONFIG_FILE}"
            TRANSFER_DIR="${NEW_DIR}"
            echo ""
            ok "Default deployment directory updated successfully!"
            info_line "Config File:" "${CONFIG_FILE}"
            info_line "New Directory:" "${TRANSFER_DIR}"
            echo ""
        else
            warn "No changes made. Deployment directory remains: ${TRANSFER_DIR}"
        fi
        exit 0
        ;;
    8|flavors|--flavors|flavor)
        echo ""
        rule "─" "${DIM}${BLUE}"
        echo ""
        step "Multi-App Flavors Builder"
        echo ""
        printf "  %bSelect flavor(s) to build:%b\n" "${BOLD}${WHITE}" "${RESET}"
        printf "    %b1)%b  %bAll 3 Standalone Editions%b  %b(Sonora, Lyra, Aria)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${CYAN}" "${RESET}"
        printf "    %b2)%b  %bSonora%b                   %b(Default: Local)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${YELLOW}" "${RESET}"
        printf "    %b3)%b  %bLyra%b                     %b(Default: Navidrome)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${CYAN}" "${RESET}"
        printf "    %b4)%b  %bAria%b                     %b(Default: Emby / Jellyfin)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${MAGENTA}" "${RESET}"
        printf "    %b5)%b  %bChora%b                    %b(Default: All Sources)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${GREEN}" "${RESET}"
        printf "    %b6)%b  %bCustom Selection%b          %b(Choose multiple apps)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${WHITE}" "${RESET}"
        echo ""
        prompt "Enter app choice [1-6]:"
        read -r FLAVOR_APP_CHOICE
        printf "%b\n" "${RESET}"

        SELECTED_FLAVORS=()
        case "$FLAVOR_APP_CHOICE" in
            1) SELECTED_FLAVORS=("sonora" "lyra" "aria") ;;
            2) SELECTED_FLAVORS=("sonora") ;;
            3) SELECTED_FLAVORS=("lyra") ;;
            4) SELECTED_FLAVORS=("aria") ;;
            5) SELECTED_FLAVORS=("chora") ;;
            6)
                echo ""
                printf "  %bEnter space-separated flavor names (sonora lyra aria chora):%b " "${BOLD}${WHITE}" "${RESET}"
                read -r -a SELECTED_FLAVORS
                ;;
            *)
                err "Invalid choice. Aborting."
                exit 1
                ;;
        esac

        declare -A FLAVOR_SOURCES
        echo ""
        rule "─" "${DIM}${BLUE}"
        echo ""
        step "Configure Library Locking"
        echo ""
        printf "  %bLibrary options:%b\n" "${DIM}${CYAN}" "${RESET}"
        printf "    %b1)%b Default for flavor\n" "${BOLD}${WHITE}" "${RESET}"
        printf "    %b2)%b Lock to %bLocal Storage%b only\n" "${BOLD}${WHITE}" "${RESET}" "${YELLOW}" "${RESET}"
        printf "    %b3)%b Lock to %bNavidrome / Subsonic%b only\n" "${BOLD}${WHITE}" "${RESET}" "${CYAN}" "${RESET}"
        printf "    %b4)%b Lock to %bEmby / Jellyfin%b only\n" "${BOLD}${WHITE}" "${RESET}" "${MAGENTA}" "${RESET}"
        printf "    %b5)%b %bAll Sources%b (Switchable)\n" "${BOLD}${WHITE}" "${RESET}" "${GREEN}" "${RESET}"
        echo ""

        for flv in "${SELECTED_FLAVORS[@]}"; do
            default_label=""
            case "$flv" in
                sonora) default_label="Local" ;;
                lyra) default_label="Navidrome" ;;
                aria) default_label="Emby / Jellyfin" ;;
                chora) default_label="All Sources" ;;
            esac

            prompt "Library for ${flv^} [1=Default (${default_label}), 2=Local, 3=Navidrome, 4=Emby, 5=All]:"
            read -r SRC_CHOICE
            printf "%b\n" "${RESET}"

            case "$SRC_CHOICE" in
                2) FLAVOR_SOURCES["$flv"]="LOCAL" ;;
                3) FLAVOR_SOURCES["$flv"]="NAVIDROME" ;;
                4) FLAVOR_SOURCES["$flv"]="EMBY" ;;
                5) FLAVOR_SOURCES["$flv"]="ALL" ;;
                *)
                    case "$flv" in
                        sonora) FLAVOR_SOURCES["$flv"]="LOCAL" ;;
                        lyra) FLAVOR_SOURCES["$flv"]="NAVIDROME" ;;
                        aria) FLAVOR_SOURCES["$flv"]="EMBY" ;;
                        chora) FLAVOR_SOURCES["$flv"]="ALL" ;;
                    esac
                    ;;
            esac
        done

        echo ""
        rule "─" "${DIM}${BLUE}"
        echo ""
        step "Select Build Target"
        echo ""
        printf "    %b1)%b  %bRelease APK%b        %b(Signed release + Deploy to Server)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${GREEN}" "${RESET}"
        printf "    %b2)%b  %bDebug APK%b          %b(Fast development build)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${CYAN}" "${RESET}"
        printf "    %b3)%b  %bInstall to Device%b  %b(Debug + adb install -r)%b\n" "${BOLD}${CYAN}" "${RESET}" "${WHITE}" "${RESET}" "${DIM}${MAGENTA}" "${RESET}"
        echo ""
        prompt "Enter build target [1-3]:"
        read -r FLAVOR_BUILD_TARGET
        printf "%b\n" "${RESET}"

        case "$FLAVOR_BUILD_TARGET" in
            1)
                FLAVOR_BUILD_TYPE="release"
                DO_DEPLOY=true
                DO_ADB_INSTALL=false
                ;;
            3)
                FLAVOR_BUILD_TYPE="debug"
                DO_DEPLOY=false
                DO_ADB_INSTALL=true
                ;;
            *)
                FLAVOR_BUILD_TYPE="debug"
                DO_DEPLOY=false
                DO_ADB_INSTALL=false
                ;;
        esac

        GRADLE_TASKS=()
        GRADLE_EXTRA_PROPS=()
        for flv in "${SELECTED_FLAVORS[@]}"; do
            capital_flv="${flv^}"
            if [ "$FLAVOR_BUILD_TYPE" = "release" ]; then
                GRADLE_TASKS+=("assemble${capital_flv}Release")
            else
                GRADLE_TASKS+=("assemble${capital_flv}Debug")
            fi
            GRADLE_EXTRA_PROPS+=("-Psource_${flv}=${FLAVOR_SOURCES[$flv]}")
        done

        BUILD_TYPE="Flavors (${FLAVOR_BUILD_TYPE^}): ${SELECTED_FLAVORS[*]}"
        LOG_FILE="${LOG_DIR}/build_flavors_${TIMESTAMP}.log"
        IS_FLAVOR_BUILD=true
        DO_BUILD=true
        ;;
    9|exit|quit|q)
        echo ""
        warn "Operation cancelled by user."
        echo ""
        exit 0
        ;;
    *)
        err "Invalid choice: ${CHOICE}"
        exit 1
        ;;
esac

# ============================================================
#  STEP 3 — Build Execution (if selected)
# ============================================================
if [[ "$DO_BUILD" == true ]]; then
    echo ""
    rule "─" "${DIM}${BLUE}"
    echo ""
    step "Compiling Target: ${BUILD_TYPE}"
    echo ""
    info_line "Gradle Tasks:" "${GRADLE_TASKS[*]}"
    info_line "Log Destination:" "${LOG_FILE}"
    echo ""

    START_TIME=$(date +%s)

    run_gradle_task "${BUILD_TYPE}" "${LOG_FILE}" "${GRADLE_TASKS[@]}" "${GRADLE_EXTRA_PROPS[@]}"
    BUILD_SUCCESS=$?

    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))

    # Post-build APK processing
    if [[ $BUILD_SUCCESS -eq 0 ]]; then
        if [[ "$IS_FLAVOR_BUILD" == true ]]; then
            echo ""
            rule "─" "${DIM}${BLUE}"
            echo ""
            step "Post-Build Processing (Flavors)"
            printf "\n"
            mkdir -p "${PROJECT_ROOT}/build_output/flavors"

            for flv in "${SELECTED_FLAVORS[@]}"; do
                capital_flv="${flv^}"
                local_src="${FLAVOR_SOURCES[$flv]:-default}"
                local_src_lower="${local_src,,}"
                capital_src="${local_src_lower^}"
                flavor_apk="${PROJECT_ROOT}/app/build/outputs/apk/${flv}/${FLAVOR_BUILD_TYPE}/app-${flv}-${FLAVOR_BUILD_TYPE}.apk"

                if [[ -f "${flavor_apk}" ]]; then
                    dest_name="${capital_flv}-${capital_src}-${FLAVOR_BUILD_TYPE}.apk"
                    dest_path="${PROJECT_ROOT}/build_output/flavors/${dest_name}"
                    cp "${flavor_apk}" "${dest_path}"
                    apk_size=$(du -h "${dest_path}" | cut -f1)
                    ok "Generated ${capital_flv} (${capital_src}): ${dest_path} (${apk_size})"

                    if [[ "$DO_DEPLOY" == true ]]; then
                        if [[ -d "${TRANSFER_DIR}" ]]; then
                            if cp "${dest_path}" "${TRANSFER_DIR}/${dest_name}"; then
                                ok "Deployed to: ${TRANSFER_DIR}/${dest_name}"
                            else
                                err "Failed to copy ${dest_name} to transfer folder."
                                ERRORS=$((ERRORS + 1))
                            fi
                        fi
                    fi

                    if [[ "$DO_ADB_INSTALL" == true ]]; then
                        if command -v adb >/dev/null 2>&1; then
                            step "Installing ${capital_flv} via ADB..."
                            if adb install -r "${dest_path}"; then
                                ok "Successfully installed ${capital_flv} to connected device!"
                            else
                                err "ADB install of ${capital_flv} failed."
                                ERRORS=$((ERRORS + 1))
                            fi
                        fi
                    fi
                else
                    err "Expected APK not found for ${capital_flv}: ${flavor_apk}"
                    ERRORS=$((ERRORS + 1))
                fi
            done
        else
            if [[ ! -f "${TARGET_APK}" ]]; then
                if [[ "${BUILD_TYPE}" =~ "Release" ]]; then
                    [[ -f "${PROJECT_ROOT}/app/build/outputs/apk/chora/release/app-chora-release.apk" ]] && TARGET_APK="${PROJECT_ROOT}/app/build/outputs/apk/chora/release/app-chora-release.apk"
                    [[ -f "${PROJECT_ROOT}/app/build/outputs/apk/release/app-release.apk" ]] && TARGET_APK="${PROJECT_ROOT}/app/build/outputs/apk/release/app-release.apk"
                else
                    [[ -f "${PROJECT_ROOT}/app/build/outputs/apk/chora/debug/app-chora-debug.apk" ]] && TARGET_APK="${PROJECT_ROOT}/app/build/outputs/apk/chora/debug/app-chora-debug.apk"
                    [[ -f "${PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk" ]] && TARGET_APK="${PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
                fi
            fi

            if [[ -f "${TARGET_APK}" ]]; then
                echo ""
                rule "─" "${DIM}${BLUE}"
                echo ""
                step "Post-Build Processing"
                printf "\n"

                APK_SIZE=$(du -h "${TARGET_APK}" | cut -f1)
                ok "Generated APK: ${TARGET_APK} (${APK_SIZE})"

                if [[ "$DO_DEPLOY" == true ]]; then
                    if [[ -d "${TRANSFER_DIR}" ]]; then
                        if cp "${TARGET_APK}" "${TRANSFER_DIR}/Chora-release.apk"; then
                            ok "Deployed to: ${TRANSFER_DIR}/Chora-release.apk"
                        else
                            err "Failed to copy APK to transfer folder."
                            ERRORS=$((ERRORS + 1))
                        fi
                    else
                        warn "Transfer directory not available, deployment skipped."
                    fi
                fi

                if [[ "$DO_ADB_INSTALL" == true ]]; then
                    if command -v adb >/dev/null 2>&1; then
                        step "Installing via ADB..."
                        if adb install -r "${TARGET_APK}"; then
                            ok "Successfully installed to connected Android device!"
                        else
                            err "ADB install failed. Check 'adb devices' output."
                            ERRORS=$((ERRORS + 1))
                        fi
                    else
                        warn "ADB not found on system PATH."
                        ERRORS=$((ERRORS + 1))
                    fi
                fi
            else
                echo ""
                rule "─" "${DIM}${RED}"
                echo ""
                err "Build reported success, but expected APK not found at:"
                warn "  ${TARGET_APK}"
                ERRORS=$((ERRORS + 1))
            fi
        fi
    fi
fi

# ============================================================
#  STEP 4 — Git Push Execution (if selected)
# ============================================================
if [[ "$DO_GIT_PUSH" == true ]]; then
    if [[ "$DO_BUILD" == true && ${BUILD_SUCCESS:-0} -ne 0 ]]; then
        echo ""
        rule "─" "${DIM}${RED}"
        echo ""
        err "Build failed! Aborting Git push to avoid deploying broken code to production."
        ERRORS=$((ERRORS + 1))
    else
        git_push_task "${COMMIT_MSG}"
    fi
fi

# ============================================================
#  COMPLETION BANNER
# ============================================================
echo ""
rule "═" "${BOLD}${CYAN}"
echo ""

if [[ $ERRORS -eq 0 ]]; then
    center "✦  OPERATION COMPLETE  ✦" "${BOLD}${GREEN}"
    echo ""
    if [[ "$DO_BUILD" == true ]]; then
        if [[ "$IS_FLAVOR_BUILD" == true ]]; then
            center "Flavor APKs compiled in ${DURATION}s" "${DIM}${WHITE}"
            info_line "Flavors Built:" "${SELECTED_FLAVORS[*]}"
            info_line "Output Directory:" "${PROJECT_ROOT}/build_output/flavors"
            if [[ "$DO_DEPLOY" == true && -d "${TRANSFER_DIR}" ]]; then
                info_line "Transfer Folder:" "${TRANSFER_DIR}"
            fi
            info_line "Log File:" "${LOG_FILE}"
        else
            center "Chora APK compiled in ${DURATION}s" "${DIM}${WHITE}"
            info_line "Build Target:" "${BUILD_TYPE}"
            info_line "APK Location:" "${TARGET_APK}"
            if [[ "$DO_DEPLOY" == true && -d "${TRANSFER_DIR}" ]]; then
                info_line "Transfer Path:" "${TRANSFER_DIR}/Chora-release.apk"
            fi
            info_line "Log File:" "${LOG_FILE}"
        fi
    fi
    if [[ "$DO_GIT_PUSH" == true ]]; then
        info_line "GitHub Status:" "Pushed to origin/${GIT_BRANCH:-master}"
    fi
else
    center "✦  COMPLETED WITH ERROR(S)  ✦" "${BOLD}${YELLOW}"
    echo ""
    center "Please inspect the terminal output or logs above for details." "${DIM}${WHITE}"
    if [[ -n "$LOG_FILE" && -f "$LOG_FILE" ]]; then
        center "${LOG_FILE}" "${DIM}${CYAN}"
    fi
fi

echo ""
rule "═" "${BOLD}${CYAN}"
echo ""
center "Chora  ·  Ready for Playback" "${DIM}${BLUE}"
echo ""

if [[ $ERRORS -ne 0 ]]; then
    exit 1
fi
