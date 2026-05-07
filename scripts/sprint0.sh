#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${ROOT}/out/classes"
SRC_MAIN="${ROOT}/src/main/java"

mkdir -p "${OUT}"

mapfile -t SOURCES < <(find "${SRC_MAIN}" -name '*.java' -print)
if [[ ${#SOURCES[@]} -eq 0 ]]; then
  echo "No Java sources found under ${SRC_MAIN}" >&2
  exit 1
fi

javac --release 25 -d "${OUT}" "${SOURCES[@]}"

cmd="${1:-}"
shift || true

case "${cmd}" in
  native)
    java -cp "${OUT}" com.mcp.indexing.sprint0.NativeDependencyValidatorMain "$@"
    ;;
  mmap)
    java -cp "${OUT}" com.mcp.indexing.sprint0.LuceneMMapSpikeMain "$@"
    ;;
  vtio)
    java -cp "${OUT}" com.mcp.indexing.sprint0.VirtualThreadIoBenchmarkMain "$@"
    ;;
  *)
    echo "Usage:" >&2
    echo "  ${0##*/} native [args...]" >&2
    echo "  ${0##*/} mmap [args...]" >&2
    echo "  ${0##*/} vtio [args...]" >&2
    exit 2
    ;;
esac

