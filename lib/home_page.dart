import 'package:flutter/material.dart';
import 'package:flutter_highlight/themes/github.dart';
import 'package:flutter_highlight/themes/monokai.dart';
import 'package:flutter_highlight/themes/atom-one-dark.dart';
import 'package:flutter_highlight/themes/atom-one-light.dart';
import 'package:flutter_highlight/themes/vs.dart';
import 'package:flutter_highlight/themes/vs2015.dart';
import 'package:flutter_highlight/themes/dracula.dart';
import 'package:acc_ide/settings_page.dart';
import 'package:acc_ide/welcome_page.dart';
import 'package:acc_ide/code_editor.dart';
import 'package:acc_ide/settings_manager.dart';

class HomePage extends StatefulWidget {
  final AppSettings appSettings;
  final VoidCallback onSettingsChanged;

  const HomePage({super.key, required this.appSettings, required this.onSettingsChanged});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  String _currentCode = '';
  String _currentLanguage = 'cpp'; // Default language
  final Map<String, String> _files = {};
  String _currentFileName = '';

  late AppSettings _appSettings; // App settings instance

  // Theme map moved from CodeEditor to HomePage
  final Map<String, Map<String, TextStyle>> _themes = {
    'monokai': monokaiTheme,
    'github': githubTheme,
    'atomOneDark': atomOneDarkTheme,
    'atomOneLight': atomOneLightTheme,
    'vs': vsTheme,
    'vs2015': vs2015Theme,
    'dracula': draculaTheme,
  };

  final Map<String, Map<String, String>> _localizedStrings = {
    'en': {
      'newFile': 'New File',
      'cpp': 'C++',
      'python': 'Python',
      'java': 'Java',
      'run': 'Run',
      'status': 'Status',
      'notRunning': 'Not Running',
      'input': 'Input:',
      'currentOutput': 'Current Output:',
      'answerOutput': 'Answer Output (Optional):',
      'enterInput': 'Enter input here...',
      'currentOutputWillBeDisplayedHere': 'Current output will be displayed here...',
      'enterExpectedAnswer': 'Enter expected answer...',
      'accIdeTitle': 'ACC IDE',
      'accIdeSubtitle': 'An Android IDE focused on\ncompetitive programming',
      'files': 'Files',
      'noFiles': 'No files, please create a new one',
      'newFileSidebar': 'New File',
      'settingsSidebar': 'Settings',
    },
    'zh': {
      'newFile': '新建文件',
      'cpp': 'C++',
      'python': 'Python',
      'java': 'Java',
      'run': '运行',
      'status': '状态',
      'notRunning': '未运行',
      'input': '输入:',
      'currentOutput': '当前输出:',
      'answerOutput': '答案输出 (可选):',
      'enterInput': '在这里输入...',
      'currentOutputWillBeDisplayedHere': '当前输出会显示在这里...',
      'enterExpectedAnswer': '在这里输入期望的答案...',
      'accIdeTitle': 'ACC IDE',
      'accIdeSubtitle': '一个专注于算法竞赛的安卓IDE',
      'files': '文件',
      'noFiles': '没有文件，请创建新文件',
      'newFileSidebar': '新建文件',
      'settingsSidebar': '设置',
    },
  };

  String _translate(String key) {
    return _localizedStrings[_appSettings.languageCode]?[key] ?? key;
  }

  Map<String, TextStyle> get _currentHighlightTheme {
    return _themes[_appSettings.theme] ?? monokaiTheme;
  }

  @override
  void initState() {
    super.initState();
    _appSettings = widget.appSettings; // Initialize settings
    _loadSettings(); // Load settings asynchronously
    if (_files.isNotEmpty) {
      _currentFileName = _files.keys.first;
      _currentCode = _files[_currentFileName]!;
    } else {
      _currentFileName = ''; // No file selected initially
      _currentCode = ''; // No code to display initially
    }
  }

  Future<void> _loadSettings() async {
    await _appSettings.loadSettings();
    setState(() {}); // Rebuild UI after loading settings
  }

  void _onSettingsChanged() {
    widget.onSettingsChanged(); // Reload settings when they are changed from SettingsPage
  }

  void _createNewFile(String language, [String? initialContent]) {
    int newFileIndex = _files.length + 1;
    String newFileName = 'NewFile_' + newFileIndex.toString();
    String fileExtension = '';
    String initialCode = initialContent ?? '';

    switch (language) {
      case 'cpp':
        fileExtension = '.cpp';
        initialCode = '// Write your code here\nint main() {\n\n}';
        break;
      case 'python':
        fileExtension = '.py';
        initialCode = '# Write your Python code here';
        break;
      case 'java':
        fileExtension = '.java';
        initialCode = '// Write your Java code here\npublic class Main {\n  public static void main(String[] args) {\n\n  }\n}';
        break;
      default:
        fileExtension = '.txt';
        initialCode = '// New file content';
    }

    while (_files.containsKey(newFileName + fileExtension)) {
      newFileIndex++;
      newFileName = 'NewFile_' + newFileIndex.toString();
    }
    newFileName += fileExtension;

    setState(() {
      _files[newFileName] = initialCode;
      _currentFileName = newFileName;
      _currentCode = _files[newFileName]!;
      _currentLanguage = language; // Set the language for the new file
    });
    if (Navigator.canPop(context)) {
      Navigator.pop(context); // Close the drawer if open
    }
  }

  void _showNewFileDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(_translate('newFile')),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              ListTile(
                leading: const Icon(Icons.code),
                title: Text(_translate('cpp')),
                onTap: () {
                  _createNewFile('cpp');
                  Navigator.of(context).pop(); // Close the dialog
                },
              ),
              ListTile(
                leading: const Icon(Icons.code),
                title: Text(_translate('python')),
                onTap: () {
                  _createNewFile('python');
                  Navigator.of(context).pop(); // Close the dialog
                },
              ),
              ListTile(
                leading: const Icon(Icons.code),
                title: Text(_translate('java')),
                onTap: () {
                  _createNewFile('java');
                  Navigator.of(context).pop(); // Close the dialog
                },
              ),
            ],
          ),
        );
      },
    );
  }

  void _showIoPanel() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true, // Allows the sheet to take full height
      backgroundColor: Theme.of(context).colorScheme.surface, // Use theme's surface color for the sheet background
      builder: (BuildContext context) {
        return DraggableScrollableSheet(
          initialChildSize: 0.4, // Start at 40% of screen height
          minChildSize: 0.4, // Minimum height is 40%
          maxChildSize: 0.8, // Maximum height is 80%
          expand: false, // Do not expand to full height initially
          builder: (BuildContext context, ScrollController scrollController) {
            return Container(
              padding: const EdgeInsets.all(16.0),
              child: SingleChildScrollView( // Make the entire content scrollable and draggable
                controller: scrollController, // Attach the DraggableScrollableSheet's controller
                child: Column(
                  children: <Widget>[
                    // Run button and status display
                    Row(
                      children: <Widget>[
                        ElevatedButton.icon(
                          onPressed: () {
                            // TODO: Implement actual code execution
                            print('Run button pressed');
                          },
                          icon: const Icon(Icons.play_arrow), // Add run icon
                          label: Text(_translate('run')),
                        ),
                        const SizedBox(width: 16.0),
                        // Status display (e.g., AC, WA, TLE)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 4.0),
                          decoration: BoxDecoration(
                            color: Theme.of(context).colorScheme.primaryContainer, // A subtle background color
                            borderRadius: BorderRadius.circular(4.0),
                          ),
                          child: Text(
                            '${_translate('status')}: ${_translate('notRunning')}', // Initial status
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.onPrimaryContainer, // Text color contrasting with primaryContainer
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16.0),
                    // Input area
                    Text(
                      _translate('input'),
                      style: TextStyle(color: Theme.of(context).colorScheme.onSurface, fontWeight: FontWeight.bold),
                    ),
                    SizedBox(
                      height: 160, // Increased height for input
                      child: TextField(
                        maxLines: null, // Allow multiple lines
                        expands: true, // Take all available vertical space within SizedBox
                        decoration: InputDecoration(
                          hintText: _translate('enterInput'),
                          contentPadding: EdgeInsets.zero, // Remove default content padding
                          fillColor: Theme.of(context).colorScheme.background, // Use theme's background color
                          filled: true,
                          hintStyle: TextStyle(color: Theme.of(context).colorScheme.onBackground.withOpacity(0.4)), // Adjusted opacity for more faded hint
                          isDense: true, // Reduce overall height to remove extra padding
                          // Add rounded corners to the input field
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10.0),
                            borderSide: BorderSide.none,
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10.0),
                            borderSide: BorderSide.none,
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10.0),
                            borderSide: BorderSide.none,
                          ),
                        ),
                        style: TextStyle(color: Theme.of(context).colorScheme.onBackground),
                      ),
                    ),
                    const SizedBox(height: 16.0),
                    // Current Output area
                    Text(
                      _translate('currentOutput'),
                      style: TextStyle(color: Theme.of(context).colorScheme.onSurface, fontWeight: FontWeight.bold),
                    ),
                    SizedBox(
                      height: 160, // Increased height for output area
                      child: Scrollbar(
                        child: TextField(
                          readOnly: true, // Make it read-only
                          maxLines: null, // Allow multiple lines
                          expands: true, // Take all available vertical space within SizedBox
                          decoration: InputDecoration(
                            hintText: _translate('currentOutputWillBeDisplayedHere'), // Placeholder hint text
                            contentPadding: EdgeInsets.zero, // Remove default content padding
                            fillColor: Theme.of(context).colorScheme.background,
                            filled: true,
                            hintStyle: TextStyle(color: Theme.of(context).colorScheme.onBackground.withOpacity(0.4)), // Adjusted opacity for more faded hint
                            isDense: true, // Reduce overall height to remove extra padding
                            // Add rounded corners to the current output field
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10.0),
                              borderSide: BorderSide.none,
                            ),
                            focusedBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10.0),
                              borderSide: BorderSide.none,
                            ),
                            enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10.0),
                              borderSide: BorderSide.none,
                            ),
                          ),
                          style: TextStyle(color: Theme.of(context).colorScheme.onBackground),
                        ),
                      ),
                    ),
                    const SizedBox(height: 16.0),
                    // Answer Output area
                    Text(
                      _translate('answerOutput'),
                      style: TextStyle(color: Theme.of(context).colorScheme.onSurface, fontWeight: FontWeight.bold),
                    ),
                    SizedBox(
                      height: 160, // Increased height for output area
                      child: TextField(
                        maxLines: null, // Allow multiple lines
                        expands: true, // Take all available vertical space within SizedBox
                        decoration: InputDecoration(
                          hintText: _translate('enterExpectedAnswer'), // Hint text
                          contentPadding: EdgeInsets.zero, // Remove default content padding
                          fillColor: Theme.of(context).colorScheme.background,
                          filled: true,
                          hintStyle: TextStyle(color: Theme.of(context).colorScheme.onBackground.withOpacity(0.4)), // Adjusted opacity for more faded hint
                          isDense: true, // Reduce overall height to remove extra padding
                          // Add rounded corners to the answer output field
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10.0),
                            borderSide: BorderSide.none,
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10.0),
                            borderSide: BorderSide.none,
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10.0),
                            borderSide: BorderSide.none,
                          ),
                        ),
                        style: TextStyle(color: Theme.of(context).colorScheme.onBackground),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  @override
  void didUpdateWidget(covariant HomePage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.appSettings.languageCode != oldWidget.appSettings.languageCode) {
      _appSettings = widget.appSettings; // Update settings if they change
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;
    print('HomePage build: languageCode = ${_appSettings.languageCode}'); // Debug print

    String appBarTitle;
    if (_currentFileName.isEmpty) {
      appBarTitle = _translate('newFile'); // Default title if no file selected
    } else {
      appBarTitle = _currentFileName;
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(appBarTitle,
          style: TextStyle(color: Theme.of(context).colorScheme.onPrimaryContainer),
        ),
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
        actions: _currentFileName.isEmpty
            ? []
            : <Widget>[
                IconButton(
                  icon: Icon(Icons.play_arrow, color: Theme.of(context).colorScheme.onPrimaryContainer),
                  onPressed: () {
                    _showIoPanel(); // Call the new I/O panel function
                  },
                ),
              ],
      ),
      drawer: Drawer(
        backgroundColor: Theme.of(context).colorScheme.surface, // Set Drawer background to theme's surface color
        child: Column( // New: Use a Column as the main child of Drawer
          children: <Widget>[
            SafeArea( // Wrap the custom header with SafeArea to avoid status bar
              child: Container(
                height: 200, // Explicitly define a height for the header area
                width: double.infinity, // Ensure the container takes full width
                decoration: BoxDecoration( // Directly set background image and overlay here
                  image: DecorationImage(
                    image: const AssetImage('assets/img/wallhaven-2y77jy.png'),
                    fit: BoxFit.cover,
                    colorFilter: Theme.of(context).brightness == Brightness.light
                        ? ColorFilter.mode(Color(0x15F5DEB3), BlendMode.screen) // Very subtle beige screen overlay
                        : null, // No filter in dark mode
                  ),
                ),
                child: Align( // Original text content, aligned to bottom-left
                  alignment: Alignment.bottomLeft,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      Column(
                        children: [
                          Text(
                            _translate('accIdeTitle'), // ACC IDE title
                            style: TextStyle(
                              color: Theme.of(context).brightness == Brightness.light ? Colors.black : Colors.white, // Dynamic color
                              fontSize: 24, // Increased font size
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          Text(
                            _translate('accIdeSubtitle'), // Subtitle
                            style: TextStyle(
                              color: Theme.of(context).brightness == Brightness.light ? Colors.black.withOpacity(0.8) : Colors.white.withOpacity(0.8), // Dynamic color
                              fontSize: 14, // Increased font size
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
            // The rest of the ListView items will now go into an Expanded ListView
            Expanded( // The ListView needs to be expanded within the Column
              child: ListView(
                padding: EdgeInsets.zero, // Keep zero padding for ListView itself
                children: <Widget>[
                  ListTile(
                    title: Text(_translate('files')),
                    textColor: Theme.of(context).colorScheme.onSurface,
                  ),
                  if (_files.isEmpty)
                    Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Text(_translate('noFiles')),
                    )
                  else
                    ..._files.keys.map((fileName) {
                      return ListTile(
                        leading: Icon(Icons.code, color: Theme.of(context).colorScheme.onSurface),
                        title: Text(fileName, style: TextStyle(color: Theme.of(context).colorScheme.onSurface)),
                        trailing: IconButton(
                          icon: Icon(Icons.delete, color: Theme.of(context).colorScheme.onSurface),
                          onPressed: () {
                            setState(() {
                              _files.remove(fileName);
                              if (_currentFileName == fileName && _files.isNotEmpty) {
                                _currentFileName = _files.keys.first;
                                _currentCode = _files[_currentFileName]!;
                              } else if (_files.isEmpty) {
                                _currentFileName = '';
                                _currentCode = '// Create a new file';
                              }
                            });
                          },
                        ),
                        onTap: () {
                          setState(() {
                            _currentFileName = fileName;
                            _currentCode = _files[fileName]!;
                          });
                          Navigator.pop(context); // Close the drawer
                        },
                      );
                    }).toList(),
                  ListTile(
                    leading: Icon(Icons.add, color: Theme.of(context).colorScheme.onSurface),
                    title: Text(_translate('newFileSidebar'), style: TextStyle(color: Theme.of(context).colorScheme.onSurface)),
                    onTap: () {
                      _showNewFileDialog();
                    },
                  ),
                  ListTile(
                    leading: Icon(Icons.settings, color: Theme.of(context).colorScheme.onSurface),
                    title: Text(_translate('settingsSidebar'), style: TextStyle(color: Theme.of(context).colorScheme.onSurface)),
                    onTap: () {
                      Navigator.pop(context); // Close the drawer first
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => SettingsPage(
                            appSettings: _appSettings,
                            onSettingsChanged: _onSettingsChanged,
                          ),
                        ),
                      );
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      backgroundColor: _files.isEmpty ? Colors.white : (_currentHighlightTheme['root']?.backgroundColor ?? Theme.of(context).scaffoldBackgroundColor), // Conditional Scaffold background
      body: _files.isEmpty
          ? WelcomePage(onStartCoding: () => _createNewFile('cpp', '// Write your code here'), appSettings: _appSettings)
          : CodeEditor(
              code: _currentCode,
              language: _currentLanguage,
              onChanged: (value) {
                setState(() {
                  _currentCode = value;
                  _files[_currentFileName] = value;
                });
              },
              fontSize: _appSettings.fontSize, // Pass font size
              fontWeight: _appSettings.fontWeight, // Pass font weight
              theme: _currentHighlightTheme, // Pass the theme object directly
              fontFamily: _appSettings.fontFamily, // Pass font family
            ),
    );
  }
} 