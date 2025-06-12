import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/material.dart';
import 'dart:ui'; // Import dart:ui for PlatformDispatcher

class AppSettings {
  double fontSize;
  FontWeight fontWeight;
  String? theme;
  ThemeMode themeMode;
  String fontFamily;
  String languageCode;

  // List of available font families
  static const List<String> availableFontFamilies = [
    'AgaveRegular',
    'AgaveMono',
    'AgavePropo',
  ];

  AppSettings({
    this.fontSize = 22.0,
    this.fontWeight = FontWeight.normal,
    this.theme,
    this.themeMode = ThemeMode.dark,
    this.fontFamily = 'AgaveMono',
    this.languageCode = 'en',
  });

  // Convert FontWeight to a string for SharedPreferences
  String _fontWeightToString(FontWeight weight) {
    return weight.toString().split('.').last;
  }

  // Convert string from SharedPreferences back to FontWeight
  FontWeight _stringToFontWeight(String weightString) {
    switch (weightString) {
      case 'bold':
        return FontWeight.bold;
      case 'normal':
      default:
        return FontWeight.normal;
    }
  }

  Future<void> loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    fontSize = prefs.getDouble('fontSize') ?? 22.0;
    String? fontWeightString = prefs.getString('fontWeight');
    if (fontWeightString != null) {
      fontWeight = _stringToFontWeight(fontWeightString);
    } else {
      fontWeight = FontWeight.normal;
    }

    // Load theme mode
    String? themeModeString = prefs.getString('themeMode');
    if (themeModeString != null) {
      themeMode = ThemeMode.values.firstWhere(
          (e) => e.toString() == 'ThemeMode.' + themeModeString,
          orElse: () => ThemeMode.dark);
    } else {
      themeMode = ThemeMode.dark; // Default to dark mode
    }

    // Load theme, if not set, determine based on themeMode
    theme = prefs.getString('theme');
    if (theme == null) {
      theme = (themeMode == ThemeMode.dark) ? 'monokai' : 'atomOneLight';
    }
    
    // Load font family, default to AgaveMono if not found or invalid
    String? loadedFontFamily = prefs.getString('fontFamily');
    if (loadedFontFamily != null && availableFontFamilies.contains(loadedFontFamily)) {
      fontFamily = loadedFontFamily;
    } else {
      fontFamily = 'AgaveMono'; // Default to AgaveMono
    }

    // Get system locale for default language
    final String systemLanguageCode = WidgetsBinding.instance.platformDispatcher.locale.languageCode; // Get system language
    // Load language code, default to system language if not set
    languageCode = prefs.getString('languageCode') ?? systemLanguageCode;
  }

  Future<void> saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble('fontSize', fontSize);
    await prefs.setString('fontWeight', _fontWeightToString(fontWeight));
    if (theme != null) {
      await prefs.setString('theme', theme!);
    }
    await prefs.setString('themeMode', themeMode.name);
    await prefs.setString('fontFamily', fontFamily);
    await prefs.setString('languageCode', languageCode);
  }
} 