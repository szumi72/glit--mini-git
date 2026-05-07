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
    echo "alias glit='$SCRIPT_DIR/glit.sh'" >> ~/.bashrc
    change_flag=1
fi
# add GLIT_PATH to profile's variables
if ! grep -q "GLIT_PATH" ~/.profile; then
    
    echo "GLIT_PATH='$SCRIPT_DIR'" >> ~/.profile
    change_flag=1
fi
export GLIT_PATH=$SCRIPT_DIR
# check if you have .jar file ready
if [ ! -f "$GLIT_PATH/target/glit-mini-git-1.0-SNAPSHOT.jar" ]; then
    mvn -f $GLIT_PATH/pom.xml package
fi
# check if glit.sh is executable
if [ ! -x $GLIT_PATH/glit.sh ]; then
    chmod +x $GLIT_PATH/glit.sh
fi
# reload shell if any changes were made
if [ $change_flag -eq 1 ]; then
    exec bash
fi
