#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Try to find JavaFX from various sources
find_javafx() {
    # 1. Try Maven local repo
    local JFX_HOME="$HOME/.m2/repository/org/openjfx"
    if [ -d "$JFX_HOME" ]; then
        local controls=$(find "$JFX_HOME" -name "javafx-controls-*-linux.jar" 2>/dev/null | head -1)
        local graphics=$(find "$JFX_HOME" -name "javafx-graphics-*-linux.jar" 2>/dev/null | head -1)
        local base=$(find "$JFX_HOME" -name "javafx-base-*-linux.jar" 2>/dev/null | head -1)
        if [ -n "$controls" ] && [ -n "$graphics" ] && [ -n "$base" ]; then
            echo "$controls:$graphics:$base"
            return 0
        fi
    fi
    
    # 2. Try system JavaFX (Fedora/RHEL)
    if [ -d "/usr/share/java javafx" ]; then
        local javafx_dir="/usr/share/java/javafx"
        local controls=$(find "$javafx_dir" -name "javafx-controls*.jar" 2>/dev/null | grep linux | head -1)
        local graphics=$(find "$javafx_dir" -name "javafx-graphics*.jar" 2>/dev/null | grep linux | head -1)
        local base=$(find "$javafx_dir" -name "javafx-base*.jar" 2>/dev/null | grep linux | head -1)
        if [ -n "$controls" ] && [ -n "$graphics" ] && [ -n "$base" ]; then
            echo "$controls:$graphics:$base"
            return 0
        fi
    fi
    
    return 1
}

JFX_MODS=$(find_javafx)

if [ -z "$JFX_MODS" ]; then
    echo "Error: JavaFX not found."
    echo "Please install OpenJFX or ensure Maven dependencies are downloaded."
    echo ""
    echo "To download dependencies, run:"
    echo "  mvn dependency:copy-dependencies -DoutputDirectory=lib"
    exit 1
fi

# Run with JavaFX modules
exec java --module-path "$JFX_MODS" --add-modules javafx.controls,javafx.graphics -cp "$SCRIPT_DIR/records-and-tracking-0.1.0.jar" com.courttrack.App "$@"
