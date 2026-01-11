#!/usr/bin/env bash
set -e

# Set up Java environment
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# Install Java 17 if not already installed
if [ ! -d "$JAVA_HOME" ]; then
    echo "Installing OpenJDK 17..."
    sudo apt-get update -qq
    sudo apt-get install -y openjdk-17-jdk
else
    echo "OpenJDK 17 already installed at $JAVA_HOME"
fi

# Verify Java installation
echo "Checking Java version..."
java -version

# Build the application
chmod +x ./gradlew
./gradlew clean :SaidIt:assembleDebug --no-daemon
