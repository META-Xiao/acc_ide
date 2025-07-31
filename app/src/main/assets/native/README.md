# Tree-sitter Native Libraries

## Directory Structure
`
native/
鈹溾攢鈹€ arm64-v8a/           # 64-bit ARM
鈹溾攢鈹€ armeabi-v7a/         # 32-bit ARM  
鈹溾攢鈹€ x86_64/              # 64-bit x86 (emulator)
鈹斺攢鈹€ x86/                 # 32-bit x86 (emulator)
`

## Required Files
- libtree-sitter.so
- libandroid-tree-sitter.so
- libtree-sitter-java.so
- libtree-sitter-cpp.so
- libtree-sitter-python.so

## 16KB Page Size Compatibility
All .so files must be compiled with NDK r26+ supporting 16KB page sizes.

## Verification
Check page alignment: eadelf -l your_lib.so | grep 'LOAD'
