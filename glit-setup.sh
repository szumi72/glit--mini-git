#!/bin/bash
source ~/.profile
source ~/.bashrc
if ! grep -q "alias glit" ~/.bashrc; then
    echo "alias glit='$(pwd)/glit.sh'" >> ~/.bashrc
fi
if ! grep -q "GLIT_PATH" ~/.profile; then
    echo "GLIT_PATH='$(pwd)'" >> ~/.profile
fi
# check if you have .jar file ready
if [ ! -f $GLIT_PATH/target/glit-mini-git-1.0-SNAPSHOT.jar ]; then
    mvn -f $GLIT_PATH/pom.xml package
fi
# check if glit.sh is executable
if [ ! -x $GLIT_PATH/glit.sh ]; then
    chmod +x $GLIT_PATH/glit.sh
fi
