import 'package:flutter/material.dart';
import 'package:acc_ide/settings_manager.dart';

class WelcomePage extends StatefulWidget {
  final VoidCallback? onStartCoding;
  final AppSettings appSettings; // Receive app settings

  const WelcomePage({
    super.key,
    this.onStartCoding,
    required this.appSettings, // Require app settings
  });

  @override
  State<WelcomePage> createState() => _WelcomePageState();
}

class _WelcomePageState extends State<WelcomePage> {
  // Localization map
  final Map<String, Map<String, String>> _localizedStrings = {
    'en': {
      'welcomeTitle': 'Welcome to ACC IDE',
      'welcomeSubtitle': 'An Android IDE focused on competitive programming',
      'startCoding': 'Start Coding',
    },
    'zh': {
      'welcomeTitle': '欢迎使用 ACC IDE',
      'welcomeSubtitle': '一个专注于算法竞赛的安卓IDE',
      'startCoding': '开始编码',
    },
  };

  String _translate(String key) {
    return _localizedStrings[widget.appSettings.languageCode]?[key] ?? key;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Icon(
              Icons.code,
              size: 80,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(height: 20),
            Text(
              _translate('welcomeTitle'),
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.onBackground),
            ),
            const SizedBox(height: 10),
            Text(
              _translate('welcomeSubtitle'),
              style: TextStyle(fontSize: 16, color: Theme.of(context).colorScheme.onBackground.withOpacity(0.7)),
            ),
            const SizedBox(height: 40),
            ElevatedButton.icon(
              onPressed: widget.onStartCoding,
              icon: Icon(Icons.code, color: Theme.of(context).colorScheme.primary),
              label: Text(_translate('startCoding'), style: TextStyle(color: Theme.of(context).colorScheme.primary)),
              style: ElevatedButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.surface,
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                textStyle: const TextStyle(fontSize: 18),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(30),
                  side: BorderSide(color: Theme.of(context).colorScheme.primary, width: 2.0),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
} 