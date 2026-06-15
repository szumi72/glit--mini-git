#!/bin/bash
# standard protection
set -euo pipefail
# load if exist
[ -f ~/.profile ] && source ~/.profile

# check if you have .jar file ready
if [ -f $GLIT_PATH/glit-mini-git-1.0-SNAPSHOT.jar ]; then
    java -cp $GLIT_PATH/glit-mini-git-1.0-SNAPSHOT.jar glit.cli.GlitController $@
else 
    echo "Fatal: cannot find .jar file."
    exit 404
fi

