import 'package:flutter/material.dart';
import 'package:acc_ide/settings_manager.dart';
import 'package:acc_ide/home_page.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  late AppSettings _appSettings;

  @override
  void initState() {
    super.initState();
    _appSettings = AppSettings();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    await _appSettings.loadSettings();
    setState(() {}); // Rebuild UI after loading settings
  }

  void _onSettingsChanged() {
    _loadSettings(); // Reload settings when they are changed from SettingsPage
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ACC IDE',
      debugShowCheckedModeBanner: false,
      locale: Locale(_appSettings.languageCode), // Set the locale based on user settings
      supportedLocales: const [
        Locale('en', ''), // English
        Locale('zh', ''), // Chinese
      ],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate, // Add if using Cupertino widgets
      ],
      theme: ThemeData(
        colorScheme: ColorScheme.light(
          primaryContainer: const Color(0xFFF9F5FF),
          onPrimaryContainer: Colors.deepPurple.shade900,
          background: const Color(0xFFF9F5FF),
          onBackground: Colors.black87,
          surface: const Color(0xFFF2EDF7),
          onSurface: Colors.black87,
          primary: Colors.deepPurple,
          onPrimary: Colors.white,
          error: Colors.red,
          onError: Colors.white,
          secondary: Colors.purpleAccent,
          onSecondary: Colors.black,
        ),
        useMaterial3: true,
        inputDecorationTheme: const InputDecorationTheme( // Global input decoration theme
          border: InputBorder.none,
          focusedBorder: InputBorder.none,
          enabledBorder: InputBorder.none,
          errorBorder: InputBorder.none,
          disabledBorder: InputBorder.none,
          focusedErrorBorder: InputBorder.none,
        ),
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.dark(
          primaryContainer: const Color(0xFF1E1E1E), // Darker color for AppBar/DrawerHeader (almost black)
          onPrimaryContainer: Colors.white, // White text on dark primary container
          background: const Color(0xFF121212), // Very dark background
          onBackground: Colors.white, // White text on dark background
          surface: const Color(0xFF1E1E1E), // Slightly lighter dark surface (same as primaryContainer for subtle depth)
          onSurface: Colors.white, // White text on dark surface
          primary: Colors.deepPurple.shade200, // Lighter purple for accents
          onPrimary: Colors.white, // White text on primary
          error: Colors.red.shade300, // Lighter red for errors
          onError: Colors.white, // White text on error
          secondary: Colors.purpleAccent.shade100, // Lighter purple accent
          onSecondary: Colors.white, // White text on secondary accent
        ),
        useMaterial3: true,
        inputDecorationTheme: const InputDecorationTheme( // Global input decoration theme for dark mode
          border: InputBorder.none,
          focusedBorder: InputBorder.none,
          enabledBorder: InputBorder.none,
          errorBorder: InputBorder.none,
          disabledBorder: InputBorder.none,
          focusedErrorBorder: InputBorder.none,
        ),
      ),
      themeMode: _appSettings.themeMode, // Use themeMode from AppSettings
      home: HomePage(
        appSettings: _appSettings,
        onSettingsChanged: _onSettingsChanged, // Pass callback to HomePage
      ),
    );
  }
}
