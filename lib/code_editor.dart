import 'package:flutter/material.dart';
import 'package:flutter_highlight/flutter_highlight.dart';
import 'package:flutter_highlight/themes/github.dart';
import 'package:flutter_highlight/themes/monokai.dart';
import 'package:flutter_highlight/themes/atom-one-dark.dart';
import 'package:flutter_highlight/themes/atom-one-light.dart';
import 'package:flutter_highlight/themes/vs.dart';
import 'package:flutter_highlight/themes/vs2015.dart';
import 'package:flutter_highlight/themes/dracula.dart';

class CodeEditor extends StatefulWidget {
  final String code;
  final String language;
  final ValueChanged<String> onChanged;
  final double fontSize;
  final FontWeight fontWeight;
  final Map<String, TextStyle> theme;
  final String fontFamily;

  const CodeEditor({
    super.key,
    required this.code,
    required this.language,
    required this.onChanged,
    this.fontSize = 16.0,
    this.fontWeight = FontWeight.normal,
    required this.theme,
    this.fontFamily = 'AgaveRegular',
  });

  @override
  State<CodeEditor> createState() => _CodeEditorState();
}

class _CodeEditorState extends State<CodeEditor> {
  late TextEditingController _controller;
  final ScrollController _scrollController = ScrollController(); // Single scroll controller for all parts
  final FocusNode _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.code);
  }

  @override
  void didUpdateWidget(covariant CodeEditor oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.code != widget.code) {
      final int cursorPosition = _controller.selection.baseOffset;
      _controller.text = widget.code;
      if (cursorPosition <= widget.code.length) {
        _controller.selection = TextSelection.collapsed(offset: cursorPosition);
      } else {
        _controller.selection = TextSelection.collapsed(offset: widget.code.length);
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _scrollController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  int _getLineCount() {
    if (widget.code.isEmpty) return 1;
    return widget.code.split('\n').length;
  }

  @override
  Widget build(BuildContext context) {
    // Define a consistent text style for both HighlightView and TextField
    final TextStyle editorTextStyle = TextStyle(
      fontFamily: widget.fontFamily,
      fontSize: widget.fontSize,
      fontWeight: widget.fontWeight,
      height: 1.5, // Important for consistent line spacing
    );

    final Map<String, TextStyle> currentTheme = widget.theme;

    return Container( // Wrap the entire Row to apply theme background
      color: currentTheme['root']?.backgroundColor ?? Colors.white, // Apply theme background to the whole editor area
      child: Row(
        children: <Widget>[
          // Line numbers
          Container(
            width: 40,
            padding: const EdgeInsets.only(left: 4.0, right: 4.0),
            child: ListView.builder(
              controller: _scrollController, // Use main scroll controller
              physics: const NeverScrollableScrollPhysics(), // Syncs with text field scroll
              itemCount: _getLineCount(),
              itemBuilder: (context, index) {
                return Text(
                  (index + 1).toString(),
                  textAlign: TextAlign.right,
                  style: editorTextStyle.copyWith(color: currentTheme['comment']?.color ?? Colors.grey), // Use theme comment color for line numbers
                );
              },
            ),
          ),
          // Code editor area
          Expanded(
            child: GestureDetector(
              onTap: () {
                FocusScope.of(context).requestFocus(_focusNode);
              },
              // Removed Container here, as the outer Container will handle the background
              child: Stack(
                children: <Widget>[
                  // Highlighted code (background)
                  Positioned.fill(
                    child: SingleChildScrollView( // <-- Added SingleChildScrollView here
                      controller: _scrollController, // <-- Shared controller
                      padding: const EdgeInsets.all(8.0), // Padding for code content
                      child: HighlightView(
                        widget.code,
                        language: widget.language,
                        theme: currentTheme, // Use selected theme
                        textStyle: editorTextStyle.copyWith(color: currentTheme['root']?.color ?? Colors.black), // Consistent style, use theme text color
                      ),
                    ),
                  ),
                  // Transparent TextField (foreground for input)
                  Positioned.fill(
                    child: SingleChildScrollView( // Essential for TextField to scroll with HighlightView
                      controller: _scrollController, // Use main scroll controller for TextField as well
                      padding: const EdgeInsets.all(8.0), // Match HighlightView padding exactly
                      child: TextField(
                        controller: _controller,
                        focusNode: _focusNode,
                        onChanged: widget.onChanged,
                        keyboardType: TextInputType.multiline,
                        maxLines: null, // Allow multiple lines and vertical expansion within SingleChildScrollView
                        textAlignVertical: TextAlignVertical.top, // Align text to the top
                        decoration: const InputDecoration(
                          border: InputBorder.none, // Remove border
                          contentPadding: EdgeInsets.zero, // No internal padding, padding provided by SingleChildScrollView
                          isDense: true, // Make it dense to remove extra space
                          hintText: '',
                          fillColor: Colors.transparent,
                          filled: true,
                        ),
                        style: editorTextStyle.copyWith(color: Colors.transparent), // Make text transparent, consistent style
                        cursorColor: currentTheme['root']?.color ?? Colors.black, // Ensure cursor is visible, use theme text color
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
} 