#!/usr/bin/env sh
set -e

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_VERSION="8.10.2"
DIST_DIR="$DIR/.gradle-dist"
INSTALL_DIR="$DIST_DIR/gradle-$GRADLE_VERSION"
JDK_DIR="$DIR/.jdk17"
JDK_ARCHIVE="$JDK_DIR/jdk17.tar.gz"

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
else
  if [ ! -x "$JDK_DIR/bin/java" ]; then
    mkdir -p "$JDK_DIR"
    if [ ! -f "$JDK_ARCHIVE" ]; then
      echo "Downloading JDK 17..."
      curl -fL "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" -o "$JDK_ARCHIVE"
    fi
    echo "Extracting JDK 17..."
    tar -xzf "$JDK_ARCHIVE" -C "$JDK_DIR"
    FIRST_DIR="$(ls "$JDK_DIR" | head -n 1)"
    if [ -d "$JDK_DIR/$FIRST_DIR" ]; then
      mv "$JDK_DIR/$FIRST_DIR"/* "$JDK_DIR"/
      rmdir "$JDK_DIR/$FIRST_DIR"
    fi
  fi
  export JAVA_HOME="$JDK_DIR"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ ! -d "$INSTALL_DIR" ]; then
  mkdir -p "$DIST_DIR"
  ARCHIVE="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ARCHIVE" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ARCHIVE"
  fi
  echo "Extracting Gradle $GRADLE_VERSION..."
  unzip -q -o "$ARCHIVE" -d "$DIST_DIR"
fi

exec "$INSTALL_DIR/bin/gradle" "$@"
