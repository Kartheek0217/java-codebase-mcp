#!/bin/bash

# Load environment variables from .env properly
if [ -f .env ]; then
    set -a            # Automatically export all variables defined in the file
    source .env       # Read the file
    set +a            # Stop automatically exporting
fi

# Run the application
# Use "$JAVA_OPTS" if you want to keep it as one arg, 
# or $JAVA_OPTS (without quotes) if you want the shell to split it into args for Java
java $JAVA_OPTS -jar jcb.jar
