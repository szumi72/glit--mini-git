#!/bin/bash
# standard protection
set -euo pipefail
# load if exist
[ -f ~/.profile ] && source ~/.profile

# special function to rebuild - delete from production code
if [ $1 == 'rebuild' ]; then
    mvn -f $GLIT_PATH/pom.xml package
    exit
fi

# check if you have .jar file ready
if [ -f $GLIT_PATH/target/glit-mini-git-1.0-SNAPSHOT.jar ]; then
    java -cp $GLIT_PATH/target/glit-mini-git-1.0-SNAPSHOT.jar glit.cli.GlitController $@
else 
    echo "You have to set up glit first. Type: "
    echo "$GLIT_PATH/glit-setup.sh"
fi

