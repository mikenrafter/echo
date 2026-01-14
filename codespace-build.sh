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

# Set up Android SDK
echo "Setting up Android SDK..."
mkdir -p "$ANDROID_HOME"

# Download Android SDK Command Line Tools if not present
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  echo "Downloading Android SDK Command Line Tools..."
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  cd "$ANDROID_HOME/cmdline-tools"
  
  # Download the latest command line tools
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q commandlinetools-linux-11076708_latest.zip
  rm commandlinetools-linux-11076708_latest.zip
  mv cmdline-tools latest
  cd -
fi

# Accept Android SDK licenses
echo "Accepting Android SDK licenses..."
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true

# Install required Android SDK components
echo "Installing Android SDK components..."
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --no_https \
  "platforms;android-34" \
  "build-tools;34.0.0" \
  "platform-tools" \
  "tools" > /dev/null 2>&1 || true

echo "Android SDK setup complete"

# Build the application
chmod +x ./gradlew
./gradlew clean :SaidIt:assembleDebug --no-daemon
