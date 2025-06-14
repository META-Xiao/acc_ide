import 'package:flutter/material.dart';

class IoPanel extends StatelessWidget {
  final String languageCode;
  final Map<String, Map<String, String>> localizedStrings;
  final VoidCallback onRun;
  final ScrollController? scrollController;

  const IoPanel({
    Key? key,
    required this.languageCode,
    required this.localizedStrings,
    required this.onRun,
    this.scrollController,
  }) : super(key: key);

  String _translate(String key) {
    return localizedStrings[languageCode]?[key] ?? key;
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16.0),
      child: SingleChildScrollView(
        controller: scrollController,
        child: Column(
          children: <Widget>[
            // Run button and status display
            Row(
              children: <Widget>[
                ElevatedButton.icon(
                  onPressed: onRun,
                  icon: const Icon(Icons.play_arrow),
                  label: Text(_translate('run')),
                ),
                const SizedBox(width: 16.0),
                // Status display (e.g., AC, WA, TLE)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 4.0),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.primaryContainer,
                    borderRadius: BorderRadius.circular(4.0),
                  ),
                  child: Text(
                    '${_translate('status')}: ${_translate('notRunning')}',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.onPrimaryContainer,
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
              height: 160,
              child: TextField(
                maxLines: null,
                expands: true,
                decoration: InputDecoration(
                  hintText: _translate('enterInput'),
                  contentPadding: EdgeInsets.zero,
                  fillColor: Theme.of(context).colorScheme.background,
                  filled: true,
                  hintStyle: TextStyle(color: Theme.of(context).colorScheme.onBackground.withOpacity(0.4)),
                  isDense: true,
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
              height: 160,
              child: Scrollbar(
                child: TextField(
                  readOnly: true,
                  maxLines: null,
                  expands: true,
                  decoration: InputDecoration(
                    hintText: _translate('currentOutputWillBeDisplayedHere'),
                    contentPadding: EdgeInsets.zero,
                    fillColor: Theme.of(context).colorScheme.background,
                    filled: true,
                    hintStyle: TextStyle(color: Theme.of(context).colorScheme.onBackground.withOpacity(0.4)),
                    isDense: true,
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
              height: 160,
              child: TextField(
                maxLines: null,
                expands: true,
                decoration: InputDecoration(
                  hintText: _translate('enterExpectedAnswer'),
                  contentPadding: EdgeInsets.zero,
                  fillColor: Theme.of(context).colorScheme.background,
                  filled: true,
                  hintStyle: TextStyle(color: Theme.of(context).colorScheme.onBackground.withOpacity(0.4)),
                  isDense: true,
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
  }
} 