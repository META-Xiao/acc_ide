import 'package:flutter/material.dart';
import 'package:acc_ide/settings_manager.dart'; // Import AppSettings

class SettingsPage extends StatefulWidget {
  final AppSettings appSettings; // Receive current settings
  final VoidCallback onSettingsChanged; // Callback for when settings change

  const SettingsPage({
    super.key,
    required this.appSettings,
    required this.onSettingsChanged,
  });

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late AppSettings _currentSettings;

  final List<String> _availableThemes = const [
    'monokai',
    'github',
    'atomOneDark',
    'atomOneLight',
    'vs',
    'vs2015',
    'dracula',
  ];

  // Localization map
  final Map<String, Map<String, String>> _localizedStrings = {
    'en': {
      'settings': 'Settings',
      'codeFontSize': 'Code Font Size',
      'codeFontWeight': 'Code Font Weight',
      'normal': 'Normal',
      'bold': 'Bold',
      'codeTheme': 'Code Theme',
      'codeFont': 'Code Font',
      'appTheme': 'App Theme',
      'darkMode': 'Dark Mode',
      'language': 'Language',
    },
    'zh': {
      'settings': '设置',
      'codeFontSize': '代码字体大小',
      'codeFontWeight': '代码字体粗细',
      'normal': '常规',
      'bold': '粗体',
      'codeTheme': '代码主题',
      'codeFont': '代码字体',
      'appTheme': '应用主题',
      'darkMode': '深色模式',
      'language': '语言',
    },
  };

  String _translate(String key) {
    return _localizedStrings[_currentSettings.languageCode]?[key] ?? key;
  }

  @override
  void initState() {
    super.initState();
    _currentSettings = AppSettings(
      fontSize: widget.appSettings.fontSize,
      fontWeight: widget.appSettings.fontWeight,
      theme: widget.appSettings.theme,
      themeMode: widget.appSettings.themeMode, // Initialize themeMode
      languageCode: widget.appSettings.languageCode,
    );
  }

  void _saveAndNotify() async {
    await _currentSettings.saveSettings();
    widget.onSettingsChanged(); // Notify HomePage that settings have changed
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_translate('settings')),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(
              _translate('codeFontSize'),
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            Slider(
              value: _currentSettings.fontSize,
              min: 10.0,
              max: 30.0,
              divisions: 20,
              label: _currentSettings.fontSize.round().toString(),
              onChanged: (double value) {
                setState(() {
                  _currentSettings.fontSize = value;
                });
              },
              onChangeEnd: (double value) {
                _saveAndNotify();
              },
            ),
            const SizedBox(height: 20),
            Text(
              _translate('codeFontWeight'),
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            DropdownButton<FontWeight>(
              value: _currentSettings.fontWeight,
              onChanged: (FontWeight? newValue) {
                if (newValue != null) {
                  setState(() {
                    _currentSettings.fontWeight = newValue;
                  });
                  _saveAndNotify();
                }
              },
              items: <DropdownMenuItem<FontWeight>>[
                DropdownMenuItem(
                  value: FontWeight.normal,
                  child: Text(_translate('normal')),
                ),
                DropdownMenuItem(
                  value: FontWeight.bold,
                  child: Text(_translate('bold')),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Text(
              _translate('codeTheme'),
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            DropdownButton<String>(
              value: _currentSettings.theme,
              onChanged: (String? newValue) {
                if (newValue != null) {
                  setState(() {
                    _currentSettings.theme = newValue;
                  });
                  _saveAndNotify();
                }
              },
              items: _availableThemes.map<DropdownMenuItem<String>>((String value) {
                return DropdownMenuItem<String>(
                  value: value,
                  child: Text(value),
                );
              }).toList(),
            ),
            const SizedBox(height: 20),
            Text(
              _translate('codeFont'),
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            DropdownButton<String>(
              value: _currentSettings.fontFamily,
              onChanged: (String? newValue) {
                if (newValue != null) {
                  setState(() {
                    _currentSettings.fontFamily = newValue;
                  });
                  _saveAndNotify();
                }
              },
              items: <DropdownMenuItem<String>>[
                const DropdownMenuItem(
                  value: 'AgaveRegular',
                  child: Text('Agave Regular'),
                ),
                const DropdownMenuItem(
                  value: 'AgaveMono',
                  child: Text('Agave Mono'),
                ),
                const DropdownMenuItem(
                  value: 'AgavePropo',
                  child: Text('Agave Propo'),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Text(
              _translate('appTheme'),
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  _translate('darkMode'),
                  style: TextStyle(fontSize: 16),
                ),
                Switch(
                  value: _currentSettings.themeMode == ThemeMode.dark,
                  onChanged: (bool value) {
                    setState(() {
                      _currentSettings.themeMode = value ? ThemeMode.dark : ThemeMode.light;
                    });
                    _saveAndNotify();
                  },
                  activeColor: Theme.of(context).colorScheme.primary, // Active color for the switch
                  inactiveThumbColor: Theme.of(context).colorScheme.onSurface.withOpacity(0.6), // Inactive thumb color
                  inactiveTrackColor: Theme.of(context).colorScheme.surface, // Inactive track color
                ),
              ],
            ),
            const SizedBox(height: 20),
            Text(
              _translate('language'),
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            DropdownButton<String>(
              value: _currentSettings.languageCode,
              onChanged: (String? newValue) {
                if (newValue != null) {
                  setState(() {
                    _currentSettings.languageCode = newValue;
                  });
                  _saveAndNotify();
                }
              },
              items: <DropdownMenuItem<String>>[
                const DropdownMenuItem(
                  value: 'en',
                  child: Text('English'),
                ),
                const DropdownMenuItem(
                  value: 'zh',
                  child: Text('中文'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
} 