#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GLIT_HOME="$(dirname "$SCRIPT_DIR")"   # katalog nad bin, czyli glit/
GLIT_JAR="$GLIT_HOME/lib/glit-mini-git-1.0.jar"

# sprawdź, czy JAR istnieje
if [ ! -f "$GLIT_JAR" ]; then
    echo "ERROR: $GLIT_JAR not found."
    exit 1
fi

# dodaj alias do .bashrc
if ! grep -q "alias glit=" ~/.bashrc; then
    echo "alias glit='$SCRIPT_DIR/glit.sh'" >> ~/.bashrc
fi

# dodaj GLIT_HOME do .profile (jeśli potrzebne)
if ! grep -q "GLIT_HOME=" ~/.profile; then
    echo "export GLIT_HOME=$GLIT_HOME" >> ~/.profile
fi

chmod +x "$SCRIPT_DIR/glit.sh"
echo "Setup complete. Restart terminal or run: source ~/.bashrc"