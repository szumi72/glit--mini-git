#!/bin/bash
# standard protection
set -euo pipefail
# load if exist
[ -f ~/.profile ] && source ~/.profile
[ -f ~/.bashrc ] && source ~/.bashrc
change_flag=0
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"


# add glit to profile's aliases
if ! grep -q "alias glit" ~/.bashrc; then
    echo "alias glit='$SCRIPT_DIR/target/glit.sh'" >> ~/.bashrc
    change_flag=1
fi
# add GLIT_PATH to profile's variables
if ! grep -q "GLIT_PATH" ~/.profile; then
    echo "GLIT_PATH='$SCRIPT_DIR/target'" >> ~/.profile
    change_flag=1
fi
export GLIT_PATH=$SCRIPT_DIR/target
# check if you have .jar file ready
if [ ! -f "$GLIT_PATH/glit-mini-git-1.0-SNAPSHOT.jar" ]; then
    echo "Fatal: cannot find .jar file."
    exit 404
fi
# check if glit.sh is readable
if [ ! -r $GLIT_PATH/glit.sh ]; then
    chmod +r $GLIT_PATH/glit.sh
fi
# check if glit.sh is executable
if [ ! -x $GLIT_PATH/glit.sh ]; then
    chmod +x $GLIT_PATH/glit.sh
fi
# reload shell if any changes were made
if [ $change_flag -eq 1 ]; then
    echo "Glit setup done."
    exec bash
fi
