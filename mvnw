#!/usr/bin/env sh
# ----------------------------------------------------------------------------
# Maven Startup Script
# ----------------------------------------------------------------------------

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")"/$link"
  fi
done

# Get standard environment variables
PRGDIR=$(dirname "$PRG")

# Only set BASEDIR if not already set
[ -z "$BASEDIR" ] && BASEDIR=$(cd "$PRGDIR" && pwd)

# Base directory for the Maven wrapper
BASEDIR=$(cd "$BASEDIR" && pwd)

WRAPPER_DIR="$BASEDIR/.mvn/wrapper"
PROPERTIES_FILE="$WRAPPER_DIR/maven-wrapper.properties"

if [ ! -f "$PROPERTIES_FILE" ]; then
  echo "Error: Missing properties file: $PROPERTIES_FILE"
  exit 1
fi

# Extract properties from the file
# We'll source the file to get the variables, but we need to be careful about the format.
# Instead, we'll grep for the specific lines.
distributionUrl=$(grep 'distributionUrl=' "$PROPERTIES_FILE" | cut -d'=' -f2)
distributionSha256Sum=$(grep 'distributionSha256Sum=' "$PROPERTIES_FILE" | cut -d'=' -f2)

# If the distributionUrl is not set, check for mvnd
if [ -z "$distributionUrl" ]; then
  echo "Error: Missing distributionUrl in $PROPERTIES_FILE"
  exit 1
fi

# Determine the Maven home directory
# We'll use a fixed location under the wrapper directory
MAVEN_HOME="$BASEDIR/.mvn/wrapper/dists/apache-maven-$(basename "$distributionUrl" .zip)"

# Check if Maven is already installed
if [ ! -d "$MAVEN_HOME" ]; then
  # Create a temporary directory
  TMPDIR=$(mktemp -d)
  # Ensure we clean up the temporary directory on exit
  trap 'rm -rf "$TMPDIR"' EXIT INT TERM

  # Download the Maven distribution
  echo "Downloading Maven from $distributionUrl"
  # Use curl or wget, whichever is available
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL "$distributionUrl" -o "$TMPDIR/maven.dist"
  elif command -v wget > /dev/null 2>&1; then
    wget -q "$distributionUrl" -O "$TMPDIR/maven.dist"
  else
    echo "Error: Neither curl nor wget is available for downloading Maven"
    exit 1
  fi

  # Validate checksum if present
  if [ -n "$distributionSha256Sum" ]; then
    echo "Validating SHA-256 checksum"
    if command -v shasum > /dev/null 2>&1; then
      if [ "$(shasum -a 256 "$TMPDIR/maven.dist" | cut -d' ' -f1)" != "$distributionSha256Sum" ]; then
        echo "Error: Checksum validation failed"
        exit 1
      fi
    elif command -v sha256sum > /dev/null 2>&1; then
      if [ "$(sha256sum "$TMPDIR/maven.dist" | cut -d' ' -f1)" != "$distributionSha256Sum" ]; then
        echo "Error: Checksum validation failed"
        exit 1
      fi
    else
      echo "Warning: Neither shasum nor sha256sum is available, skipping checksum validation"
    fi
  fi

  # Determine the archive type and extract
  if echo "$distributionUrl" | grep -q '\.zip$'; then
    if command -v unzip > /dev/null 2>&1; then
      unzip -q "$TMPDIR/maven.dist" -d "$TMPDIR"
    else
      echo "Error: unzip is required to extract the Maven distribution"
      exit 1
    fi
  elif echo "$distributionUrl" | grep -q '\.tar\.gz$'; then
    if command -v tar > /dev/null 2>&1; then
      tar -xzf "$TMPDIR/maven.dist" -C "$TMPDIR"
    else
      echo "Error: tar is required to extract the Maven distribution"
      exit 1
    fi
  else
    echo "Error: Unsupported archive type for $distributionUrl"
    exit 1
  fi

  # Find the extracted directory (assuming it's the only one that starts with apache-maven-)
  MAVEN_HOME=$(ls -d "$TMPDIR"/apache-maven-* 2>/dev/null | head -n 1)
  if [ -z "$MAVEN_HOME" ]; then
    # Try without the prefix for snapshots
    MAVEN_HOME=$(ls -d "$TMPDIR"/* 2>/dev/null | head -n 1)
  fi

  if [ -z "$MAVEN_HOME" ] || [ ! -d "$MAVEN_HOME" ]; then
    echo "Error: Could not find the extracted Maven distribution"
    exit 1
  fi

  # Move the extracted directory to the final location
  mkdir -p "$(dirname "$MAVEN_HOME")"
  mv "$MAVEN_HOME" "$BASEDIR/.mvn/wrapper/"
  MAVEN_HOME="$BASEDIR/.mvn/wrapper/$(basename "$MAVEN_HOME")"

  # Clean up the temporary directory (the trap will do it, but we can do it early)
  rm -rf "$TMPDIR"
  trap - EXIT INT TERM
fi

# Set the Maven command line options
# Pass any JVM arguments via MAVEN_OPTS
# Execute Maven
exec "$MAVEN_HOME/bin/mvn" "$@"
