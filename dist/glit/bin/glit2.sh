#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GLIT_HOME="$(dirname "$SCRIPT_DIR")"
GLIT_JAR="$GLIT_HOME/lib/glit-mini-git-1.0.jar"

if [ ! -f "$GLIT_JAR" ]; then
    echo "JAR file not found at $GLIT_JAR"
    exit 1
fi

# jeśli chcesz zachować 'rebuild' – nie zadziała bez Mavena, lepiej go usunąć
java -cp "$GLIT_JAR" glit.cli.GlitController "$@"