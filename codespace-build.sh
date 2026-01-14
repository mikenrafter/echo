#!/usr/bin/env bash
set -e

# Set up Java environment
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

sudo apt-get update -qq
sudo apt-get install -y openjdk-17-jdk

# Verify Java installation
echo "Checking Java version..."
java -version

# Build the application
chmod +x ./gradlew
./gradlew clean :SaidIt:assembleDebug --no-daemon
