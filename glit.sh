#!/bin/bash
source ~/.profile
# check if you have .jar file ready
if [ -f $GLIT_PATH/target/glit-mini-git-1.0-SNAPSHOT.jar ]; then
    java -cp $GLIT_PATH/target/glit-mini-git-1.0-SNAPSHOT.jar glit.service.Repository
else 
    echo "You have to set up glit first. Type: "
    echo "$GLIT_PATH/glit-setup.sh"
fi

