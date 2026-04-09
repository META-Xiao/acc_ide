# ACC IDE Release Notes

This document records all official release information for ACC IDE.

## Version 1.4.1 (2026-04-09)

### Key Updates

- upgrade AGP to 9.0.0 && correct sora-editor group id && upgrade to 0.24.4
- enable c++17 for wasm cpp compilation
- link wasm clang builtins for cpp

## Version 1.4.0 (2025-10-02)

### Key Updates

- Fixed `GitHub Action` unable to read `" "` and error highlighting issues in IO panel when `CE` occurs
- Implemented `WebAssembly` as local compiler

## Version 1.3.1 (2025-08-22)

### Key Updates

- Reduced symbol panel height
- Added haptic feedback for symbol panel
- Fixed bug where `Github Action` couldn't execute `Java` and `Python`

## Version 1.3.0 (2025-08-21)

### Key Updates

- Added syntax completion based on `treesitter(CST)`
- Fixed various bugs


## Version 1.2.1 (2025-07-28)

### Key Updates

- Added TextMate support and extended syntax highlighting
- Introduced dark/light themes matching UI color scheme
- Removed previous Java interpreter-based syntax highlighting
- Added AgaveNerdFont support in editor
- Added auto-completion toggle

### TODO

- Fix UI response bugs for language switching and theme switching
- Add syntax tree
- Add treesitter and LSP
- Fix Java and Python execution issues in GitHub Action

## Version 1.2.0 (2025-07-15)

### Key Updates
- Added undo and redo functionality
- Added symbol panel for quick symbol input on Android
- Implemented C++ IO functionality using `Github Action`

### Bug Fixes
- Fixed bug where temporary files couldn't be properly deleted after closing
- Fixed new file naming bug
- Fixed startup screen white screen issue

## Version 1.1.1 (2025-06-20)

### Key Updates
- Added new icon, improved overall visual experience
- Optimized symbol panel layout, reduced panel height
- Fixed multiple UI rendering issues
- Improved overall app performance

## Version 1.1.0 (2025-06-20)

### Key Updates
- Added customizable cursor width (2dp-14dp)
- Improved symbol panel layout
- Optimized editor performance
- Improved auto-completion stability

## Version 1.0.0 (2025-06-01)

Migrated from Flutter version, built using [sora-editor](https://github.com/Rosemoe/sora-editor) library as template

### Initial Release Features
- File management system
- Light/Dark theme support
- Input/Output test panel 