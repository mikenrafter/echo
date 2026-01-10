#!/usr/bin/env bash
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
java -version
sudo apt install openjdk-17-jdk -y
chmod +x ./gradlew
exec ./gradlew clean :SaidIt:assembleDebug --no-daemon