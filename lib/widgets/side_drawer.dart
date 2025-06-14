import 'package:flutter/material.dart';
import '../settings_manager.dart';

class SideDrawer extends StatelessWidget {
  final AppSettings appSettings;
  final Map<String, Map<String, String>> localizedStrings;
  final List<String> fileList;
  final Function(String) onFileSelected;
  final VoidCallback onNewFile;
  final VoidCallback onSettings;

  const SideDrawer({
    Key? key,
    required this.appSettings,
    required this.localizedStrings,
    required this.fileList,
    required this.onFileSelected,
    required this.onNewFile,
    required this.onSettings,
  }) : super(key: key);

  String _translate(String key) {
    return localizedStrings[appSettings.languageCode]?[key] ?? key;
  }

  @override
  Widget build(BuildContext context) {
    return Drawer(
      child: Column(
        children: <Widget>[
          // Drawer header with background image
          DrawerHeader(
            decoration: BoxDecoration(
              image: DecorationImage(
                image: const AssetImage('assets/images/drawer_header_bg.png'),
                fit: BoxFit.cover,
                colorFilter: ColorFilter.mode(
                  Theme.of(context).colorScheme.primary.withOpacity(0.7),
                  BlendMode.srcOver,
                ),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.end,
              children: <Widget>[
                Text(
                  _translate('accIdeTitle'),
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onPrimary,
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  _translate('accIdeSubtitle'),
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onPrimary,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
          // File list
          Expanded(
            child: fileList.isEmpty
                ? Center(
                    child: Text(
                      _translate('noFiles'),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.onSurface.withOpacity(0.6),
                      ),
                    ),
                  )
                : ListView.builder(
                    itemCount: fileList.length,
                    itemBuilder: (context, index) {
                      return ListTile(
                        title: Text(fileList[index]),
                        onTap: () => onFileSelected(fileList[index]),
                      );
                    },
                  ),
          ),
          // Bottom buttons
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Column(
              children: <Widget>[
                ListTile(
                  leading: const Icon(Icons.add),
                  title: Text(_translate('newFileSidebar')),
                  onTap: onNewFile,
                ),
                ListTile(
                  leading: const Icon(Icons.settings),
                  title: Text(_translate('settingsSidebar')),
                  onTap: onSettings,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
} 