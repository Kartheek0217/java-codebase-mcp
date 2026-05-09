#!/bin/bash

# Load environment variables from .env properly
if [ -f .env ]; then
    set -a            # Automatically export all variables defined in the file
    source .env       # Read the file
    set +a            # Stop automatically exporting
fi

# Ensure data directory exists for H2
mkdir -p data

# Find the jar file in target directory
JAR_FILE=$(ls jcb.jar 2>/dev/null | grep -v "original" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "JAR file not found in target/. Please run 'mvn package' first."
    exit 1
fi

echo "Starting application with JAR: $JAR_FILE"
echo "Server Port: ${SERVER_PORT:-8080}"

# Run the application
java $JAVA_OPTS -jar "$JAR_FILE"
