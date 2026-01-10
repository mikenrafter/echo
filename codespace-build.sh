#!/usr/bin/env bash
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version
exec ./gradlew clean :SaidIt:assembleDebug --no-daemon
sudo apt install openjdk-17-jdk -y