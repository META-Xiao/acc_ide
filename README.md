# ACC IDE

- [English](README.md)
- [简体中文](README_cn.md)

ACC IDE is an Android-based integrated development environment specifically designed for algorithmic competitions and coding challenges. Built to enhance the competitive programming experience on mobile devices, ACC IDE provides a feature-rich environment for writing, testing, and submitting algorithmic solutions.

## Overview

ACC IDE aims to be a comprehensive mobile solution for competitive programmers who need to code and test algorithms on the go. The application provides syntax highlighting, code completion, file management, and other essential IDE features tailored for competitive programming challenges.

## Project Structure

The project follows a standard Android application architecture with a focus on modular components:

### Core Structure
```
acc_ide_android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/acc_ide/
│   │   │   │   ├── adapter/       # RecyclerView adapters
│   │   │   │   ├── dialog/        # Dialog fragments
│   │   │   │   ├── model/         # Data models
│   │   │   │   ├── util/          # Utility classes
│   │   │   │   ├── view/          # Custom views
│   │   │   │   ├── ui/            # UI components
│   │   │   │   ├── MainActivity.kt # Main application entry point
│   │   │   │   ├── EditorFragment.kt # Code editor implementation
│   │   │   │   ├── IOPanelFragment.kt # Input/output panel
│   │   │   │   ├── SettingsFragment.kt # Application settings
│   │   │   │   ├── WelcomeFragment.kt # Welcome screen
│   │   │   │   └── NewFileDialogFragment.kt # New file creation dialog
│   │   │   ├── res/              # Android resources
│   │   │   └── AndroidManifest.xml
│   ├── build.gradle             # Module-level build config
├── gradle/                      # Gradle wrapper files
└── build.gradle                # Project-level build config
```

### Key Components

#### Main Activity (`MainActivity.kt`)
The central component of the application that manages:
- File navigation drawer
- Fragment transactions
- Storage permissions
- File operations (create, open, save, rename, delete)
- Application language and theme settings

#### Editor Fragment (`EditorFragment.kt`)
A powerful code editor with:
- Syntax highlighting for multiple languages
- Code completion
- Line numbering
- Code block indication
- Font size control via gestures
- Theme-aware styling
- Automatic indentation

#### IO Panel Fragment (`IOPanelFragment.kt`)
An interface for:
- Input/output testing
- Viewing execution results
- Running code

#### Settings Fragment (`SettingsFragment.kt`)
User preferences configuration:
- Theme selection (dark/light mode)
- Font size adjustment
- Language preferences
- Editor behavior options



#### Dialog Components
Various dialog fragments for user interactions:
- `NewFileDialogFragment.kt`: For creating new code files
- Dialog classes in the dialog package for confirmation and input

#### Utility Layer
Classes in the `util` package:
- `FileStorageManager`: Manages the app's file operations
- `LocaleHelper`: Handles localization and language switching

## Implemented Features

### Editor Capabilities
- **Robust Code Editing**: Based on the Sora Editor library with performance optimizations
- **Syntax Highlighting**: Support for Java, with basic support for other languages
- **Code Completion**: Context-aware suggestions as you type
- **Theme Support**: Dark and light modes with appropriate syntax coloring
- **Gesture Controls**: Zoom in/out for font size adjustment
- **Line Numbers and Block Indentation**: Visual aids for code structure
- **Symbol Panel**: Minimalist, mobile-friendly panel for quick input of common programming symbols, auto-adapts to dark and light themes.

### File Management
- **Create, Open, Save Files**: Basic file operations through an intuitive interface
- **File Browser**: Side drawer with list of available files
- **Rename and Delete**: File management tools with confirmation dialogs
- **Automatic Saving**: Changes are automatically persisted to prevent data loss

### User Interface
- **Responsive Design**: Works across different Android device sizes
- **Navigation Drawer**: Easy access to file list and settings
- **Toolbar Actions**: Context-sensitive actions based on current fragment
- **Fragment-based Navigation**: Smooth transitions between different screens

### Customization
- **Language Selection**: Interface language can be changed in settings
- **Theme Selection**: Toggle between dark and light themes
- **Font Size Control**: Adjust editor font size from settings or with gestures
- **Cursor Width**: Enhanced visual experience on mobile devices (actually the author was too lazy to adjust the cursor😫)
- **Editor Preferences**: Customize editor behavior through settings

### Input/Output Panel (Not fully implemented yet🤫)
- **Test Input**: Enter test data to validate algorithm outputs
- **Output Display**: View execution results
- **Parallel Testing**: Test algorithm functionality directly within the IDE

## Planned Features

### Compiler Integration
- Integration with C/C++, Java, and Python compilers
- Local compilation and execution
- Support for different compiler versions
- Compilation progress indicators
- Compilation error highlighting in the editor

### Problem Status Detection
- Automatic detection of solution status:
  - AC (Accepted)
  - WA (Wrong Answer)
  - CE (Compilation Error)
  - MLE (Memory Limit Exceeded)
  - TLE (Time Limit Exceeded)
  - RE (Runtime Error)
- Execution time and memory usage statistics
- Test case result visualization

### competitive-companion Integration
- Android version of the competitive-companion
- Import test cases directly from problem statements
- Support for major competitive programming platforms:
  - Codeforces
  - AtCoder
  - LeetCode
  - Luogu
  - Niuke

## Installation

[releases](https://github.com/META-Xiao/acc_ide/releases/latest)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This software is released under an open-source license with the following conditions:

1. This software is free to use for personal and non-commercial purposes.
2. Commercial use is permitted, but requires:
   - Prior notification to the author before commercial use
   - Clear attribution to the original author in any commercial product or service
3. Modifications and redistribution are allowed, provided that:
   - The original license terms are maintained
   - Attribution to the original author is preserved
   - Changes are clearly documented

Copyright © 2024 ACC IDE Project. All rights reserved except as specified in this license.

## Acknowledgements

- [Sora Editor](https://github.com/Rosemoe/sora-editor) for the code editing capabilities
- Other open-source libraries used in this project

---

ACC IDE - Enhancing your competitive programming experience on Android. 