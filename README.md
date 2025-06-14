# ACC IDE

## Recent Updates (2025-05)

- Switched core editor to **flutter_code_editor 0.3.x** for better mobile experience.
- Implemented:
  - Smart auto-indent (VS Code-style)  
  - Bracket/parenthesis matching & highlighting  
  - Focus/onKey interception to avoid duplicate new-lines on soft-keyboard Enter  
  - Theme / font customisation fully preserved
- Next step: decide whether to migrate to **WebView + Monaco** for desktop-grade completion & LSP.

An Android IDE focused on competitive programming.

- [中文介绍](README_zh.md)

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Lab: Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Cookbook: Useful Flutter samples](https://docs.flutter.dev/cookbook)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.

## Project Features & Progress

### Core IDE Functionality
- **Code Editor Integration**: Utilizes `flutter_highlight` for syntax highlighting.
- **File Management**: Includes drawer navigation with a file list, "New File", and "Settings" options.
- **Responsive Editing**: Integrated `HighlightView` and `TextField` for robust code editing, featuring multiline support and synchronized scroll for line numbers. The `TextField` is transparently layered over `HighlightView` for seamless editing.

### Customization & User Experience
- **Font Settings**: Supports custom "Agave" font with adjustable size and weight.
- **Theme Management**: Allows users to select various code themes (e.g., 'monokai', 'github', 'atomOneDark') and switch between light and dark application modes. Default theme and mode are determined by system settings.
- **Dynamic Color Scheme**: The app's color scheme adapts to light and dark modes, providing a visually pleasing and consistent experience.
- **Sidebar Enhancements**: The sidebar's header includes a custom background image with a dynamic color filter based on the theme, and a localized title and subtitle for a personalized touch.

### Input/Output Panel
- **Draggable I/O Panel**: A dynamic panel slides up from the bottom of the screen, initially at 40% height, and is draggable up to 80% of the screen height.
- **Execution Interface**: Contains a "Run" button, a status display (e.g., AC, WA, TLE), and distinct sections for "Input", "Current Output", and "Answer Output".
- **Enhanced UI**: Input and output fields feature rounded corners for a modern look, and improved sizing for better usability.

### Localization
- **Multi-language Support**: Features a language switch in the settings, allowing users to choose between English and Chinese.
- **Comprehensive Localization**: All key UI elements, including the settings page, new file dialog, I/O panel, sidebar title and subtitle, and the welcome page, are fully localized based on the selected language.
- **System Language Default**: The application intelligently defaults to the system's preferred language upon first launch.

## Code Editor Features

### Completed Features
- Code syntax highlighting with multiple themes:
  - GitHub
  - Monokai
  - Atom One Dark
  - Atom One Light
  - VS
  - VS2015
  - Dracula
- Customizable font settings:
  - Font size
  - Font weight
  - Font family
- Line numbers
- Horizontal and vertical scrolling
- Basic cursor position tracking

### Known Issues
1. Cursor position mismatch with editing position, especially when moving right
2. View auto-scrolling issues when switching lines with overflow text

### TODO
1. Refactor code editor using `flutter_code_editor` package for better:
   - Cursor position accuracy
   - Scroll behavior
   - Line wrapping
   - Code folding
   - Auto-indentation
   - Bracket matching
2. Add more editor features:
   - Search and replace
   - Multiple cursors
   - Code completion
   - Error highlighting
   - Git integration
   - File tree view
   - Terminal integration
