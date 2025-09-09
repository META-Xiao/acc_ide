#!/system/bin/sh
# AccIDE Compiler Installation Script
# This script sets up basic compilers for C++, Python, and Java

PREFIX="$1"
if [ -z "$PREFIX" ]; then
    echo "Usage: $0 <prefix_path>"
    exit 1
fi

echo "Setting up compilers in $PREFIX..."

# Create necessary directories
mkdir -p "$PREFIX/bin"
mkdir -p "$PREFIX/lib"
mkdir -p "$PREFIX/include"
mkdir -p "$PREFIX/share"

# Create Python3 wrapper (uses system python if available)
cat > "$PREFIX/bin/python3" << 'EOF'
#!/system/bin/sh
# AccIDE Python3 wrapper
if [ -x /system/bin/python3 ]; then
    exec /system/bin/python3 "$@"
elif [ -x /system/xbin/python3 ]; then
    exec /system/xbin/python3 "$@"
else
    echo "Python3 not found on system. Please install Python through F-Droid or other means."
    echo "You can also try 'python' instead of 'python3'"
    exit 1
fi
EOF

# Create Python wrapper
cat > "$PREFIX/bin/python" << 'EOF'
#!/system/bin/sh
# AccIDE Python wrapper
if [ -x /system/bin/python ]; then
    exec /system/bin/python "$@"
elif [ -x /system/xbin/python ]; then
    exec /system/xbin/python "$@"
else
    exec "$PREFIX/bin/python3" "$@"
fi
EOF

# Create GCC wrapper (uses clang if available)
cat > "$PREFIX/bin/gcc" << 'EOF'
#!/system/bin/sh
# AccIDE GCC wrapper using system clang
if [ -x /system/bin/clang ]; then
    exec /system/bin/clang "$@"
elif [ -x /system/xbin/clang ]; then
    exec /system/xbin/clang "$@"
else
    echo "C compiler not found. Please install NDK or a C compiler."
    echo "Try: pkg install clang"
    exit 1
fi
EOF

# Create G++ wrapper
cat > "$PREFIX/bin/g++" << 'EOF'
#!/system/bin/sh
# AccIDE G++ wrapper using system clang++
if [ -x /system/bin/clang++ ]; then
    exec /system/bin/clang++ "$@"
elif [ -x /system/xbin/clang++ ]; then
    exec /system/xbin/clang++ "$@"
else
    echo "C++ compiler not found. Please install NDK or a C++ compiler."
    echo "Try: pkg install clang"
    exit 1
fi
EOF

# Create Clang wrappers
cat > "$PREFIX/bin/clang" << 'EOF'
#!/system/bin/sh
# AccIDE Clang wrapper
if [ -x /system/bin/clang ]; then
    exec /system/bin/clang "$@"
elif [ -x /system/xbin/clang ]; then
    exec /system/xbin/clang "$@"
else
    echo "Clang not found. Please install NDK or clang."
    exit 1
fi
EOF

cat > "$PREFIX/bin/clang++" << 'EOF'
#!/system/bin/sh
# AccIDE Clang++ wrapper
if [ -x /system/bin/clang++ ]; then
    exec /system/bin/clang++ "$@"
elif [ -x /system/xbin/clang++ ]; then
    exec /system/xbin/clang++ "$@"
else
    echo "Clang++ not found. Please install NDK or clang."
    exit 1
fi
EOF

# Create Java wrapper
cat > "$PREFIX/bin/java" << 'EOF'
#!/system/bin/sh
# AccIDE Java wrapper
if [ -x /system/bin/java ]; then
    exec /system/bin/java "$@"
elif [ -x /system/xbin/java ]; then
    exec /system/xbin/java "$@"
else
    echo "Java not found. Please install OpenJDK."
    echo "Try: pkg install openjdk-17"
    exit 1
fi
EOF

# Create Javac wrapper
cat > "$PREFIX/bin/javac" << 'EOF'
#!/system/bin/sh
# AccIDE Javac wrapper
if [ -x /system/bin/javac ]; then
    exec /system/bin/javac "$@"
elif [ -x /system/xbin/javac ]; then
    exec /system/xbin/javac "$@"
else
    echo "Javac not found. Please install OpenJDK."
    echo "Try: pkg install openjdk-17"
    exit 1
fi
EOF

# Create Make wrapper
cat > "$PREFIX/bin/make" << 'EOF'
#!/system/bin/sh
# AccIDE Make wrapper
if [ -x /system/bin/make ]; then
    exec /system/bin/make "$@"
elif [ -x /system/xbin/make ]; then
    exec /system/xbin/make "$@"
else
    echo "Make not found. Please install build tools."
    echo "Try: pkg install make"
    exit 1
fi
EOF

# Create pkg command fallback
cat > "$PREFIX/bin/pkg" << 'EOF'
#!/system/bin/sh
# AccIDE pkg fallback command
echo "AccIDE Package Manager"
echo "====================="
echo
if [ "$1" = "install" ]; then
    case "$2" in
        "clang"|"gcc"|"g++")
            echo "C/C++ compiler support is built-in to AccIDE."
            echo "Try: gcc --version or g++ --version"
            ;;
        "python"|"python3")
            echo "Python support is built-in to AccIDE."
            echo "Try: python3 --version"
            ;;
        "openjdk"*|"java")
            echo "Java support is built-in to AccIDE."
            echo "Try: java -version"
            ;;
        "make")
            echo "Make support is built-in to AccIDE."
            echo "Try: make --version"
            ;;
        *)
            echo "Package '$2' is not available in AccIDE's built-in environment."
            echo "For full Termux package management, please install Termux from F-Droid."
            ;;
    esac
else
    echo "Available built-in tools:"
    echo "  python3, python, gcc, g++, clang, clang++, java, javac, make"
    echo
    echo "Usage: pkg install <package>"
    echo "       pkg list (show available tools)"
    echo
    echo "For real package management, install Termux from F-Droid."
fi
EOF

# Create apt command fallback  
cat > "$PREFIX/bin/apt" << 'EOF'
#!/system/bin/sh
# AccIDE apt fallback command
echo "AccIDE does not support apt package management."
echo "Available built-in tools: python3, gcc, g++, java, javac, make"
echo
echo "For real package management:"
echo "  1. Install Termux from F-Droid"
echo "  2. Use 'pkg install <package>' in real Termux"
echo
echo "For AccIDE development, use the built-in compilers directly."
EOF

# Create a package info command
cat > "$PREFIX/bin/pkg-info" << 'EOF'
#!/system/bin/sh
# AccIDE Package Information
echo "AccIDE Built-in Development Tools"
echo "================================="
echo
echo "Available compilers and tools:"
echo "  python3   - Python 3 interpreter"
echo "  python    - Python interpreter"
echo "  gcc       - GNU C Compiler (via clang)"
echo "  g++       - GNU C++ Compiler (via clang++)"
echo "  clang     - Clang C Compiler"
echo "  clang++   - Clang C++ Compiler"
echo "  java      - Java Runtime"
echo "  javac     - Java Compiler"
echo "  make      - Build tool"
echo
echo "Package management commands:"
echo "  pkg       - Basic package info and install simulation"
echo "  apt       - Shows information about real package management"
echo
echo "Example usage:"
echo "  gcc -o hello hello.c        # Compile C code"
echo "  g++ -o hello hello.cpp      # Compile C++ code"
echo "  python3 script.py           # Run Python script"
echo "  javac Main.java && java Main # Compile and run Java"
EOF

# Set executable permissions for all created files
chmod 755 "$PREFIX/bin/python3"
chmod 755 "$PREFIX/bin/python"
chmod 755 "$PREFIX/bin/gcc"
chmod 755 "$PREFIX/bin/g++"
chmod 755 "$PREFIX/bin/clang"
chmod 755 "$PREFIX/bin/clang++"
chmod 755 "$PREFIX/bin/java"
chmod 755 "$PREFIX/bin/javac"
chmod 755 "$PREFIX/bin/make"
chmod 755 "$PREFIX/bin/pkg"
chmod 755 "$PREFIX/bin/apt"
chmod 755 "$PREFIX/bin/pkg-info"

# Create login script - essential for shell execution
cat > "$PREFIX/bin/login" << EOF
#!/system/bin/sh
# AccIDE Login Shell

# Set environment variables
export PREFIX="$PREFIX"
export PATH="$PREFIX/bin:/system/bin:/system/xbin:\$PATH"
export HOME="$PREFIX/../home"
export TMPDIR="$PREFIX/tmp"
export LANG="en_US.UTF-8"
export TERM="xterm-256color"
export PS1="AccIDE:~$ "
export MOTD_SHOWN=1

# Create directories if they don't exist
mkdir -p "\$HOME" 2>/dev/null
mkdir -p "\$TMPDIR" 2>/dev/null

# Change to home directory
cd "\$HOME" 2>/dev/null || cd /

# Show welcome message for interactive sessions
if [ \$# -eq 0 ] || [ "\$1" = "-login" ]; then
    echo "Welcome to AccIDE Terminal!"
    echo "Type 'welcome' for available commands."
    echo ""
fi

# If called with specific command arguments (not just -login), execute them
if [ \$# -gt 0 ] && [ "\$1" != "-login" ]; then
    exec /system/bin/sh "\$@"
fi

# Start interactive shell (this handles both no args and -login cases)
exec /system/bin/sh
EOF

chmod 755 "$PREFIX/bin/login"

echo "Compiler setup completed!"
echo "Available commands: python3, python, gcc, g++, clang, clang++, java, javac, make, pkg, apt"
echo "Run 'pkg-info' for detailed information about available tools."
