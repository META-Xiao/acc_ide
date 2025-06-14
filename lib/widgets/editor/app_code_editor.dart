import 'package:flutter/material.dart';
import 'package:flutter_code_editor/flutter_code_editor.dart';
import 'package:highlight/languages/dart.dart';
import 'package:highlight/languages/cpp.dart';
import 'package:highlight/languages/python.dart';
import 'package:flutter_highlight/themes/monokai.dart';
import 'package:flutter_highlight/themes/github.dart';
import 'package:flutter_highlight/themes/atom-one-dark.dart';
import 'package:flutter_highlight/themes/atom-one-light.dart';
import 'package:flutter_highlight/themes/vs.dart';
import 'package:flutter_highlight/themes/vs2015.dart';
import 'package:flutter_highlight/themes/dracula.dart';
import 'package:flutter/services.dart';
import 'package:flutter_code_editor/flutter_code_editor.dart';

class AppCodeEditor extends StatefulWidget {
  final String code;
  final String language;
  final ValueChanged<String> onChanged;
  final double fontSize;
  final FontWeight fontWeight;
  final Map<String, TextStyle> theme;
  final String fontFamily;

  const AppCodeEditor({
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
  State<AppCodeEditor> createState() => _AppCodeEditorState();
}

class _AppCodeEditorState extends State<AppCodeEditor> {
  late CodeController _controller;
  final FocusNode _focusNode = FocusNode();
  KeyEventResult _onKey(FocusNode node, RawKeyEvent event) {
    if (event is RawKeyDownEvent && event.logicalKey == LogicalKeyboardKey.enter) {
      _handleEnter(event);
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  // 可配置缩进宽度
  String get _indentUnit => '  '; // 2空格，可改为4空格或'\t'

  @override
  void initState() {
    super.initState();
    _controller = CodeController(
      text: widget.code,
      language: _getLanguage(widget.language),
      analyzer: const DefaultLocalAnalyzer(),
    );
    _controller.addListener(_onTextChanged);
  }

  @override
  void didUpdateWidget(covariant AppCodeEditor oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.code != _controller.text) {
      _controller.text = widget.code;
    }
    if (widget.language != oldWidget.language) {
      _controller.language = _getLanguage(widget.language);
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_onTextChanged);
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _onTextChanged() {
    if (_controller.text != widget.code) {
      widget.onChanged(_controller.text);
    }
  }

  void _handleEnter(RawKeyEvent event) {
    if (event is RawKeyDownEvent && event.logicalKey == LogicalKeyboardKey.enter) {
      final selection = _controller.selection;
      if (!selection.isValid || selection.start != selection.end) return;
      final text = _controller.text;
      final cursor = selection.start;
      final before = text.substring(0, cursor);
      final after = text.substring(cursor);
      final lines = before.split('\n');
      final prevLine = lines.isNotEmpty ? lines.last : '';
      final nextLine = after.split('\n').first;
      final lang = widget.language.toLowerCase();
      final indent = _getAutoIndent(prevLine: prevLine, nextLine: nextLine, lang: lang);
      // 智能大括号回车补全
      final left = prevLine.trimRight().isNotEmpty ? prevLine.trimRight().characters.last : '';
      final right = nextLine.trimLeft().isNotEmpty ? nextLine.trimLeft().characters.first : '';
      final isBracketPair = (left == '{' && right == '}') || (left == '[' && right == ']') || (left == '(' && right == ')');
      if (isBracketPair) {
        final baseIndent = RegExp(r'^(\s*)').firstMatch(prevLine)?.group(0) ?? '';
        final dedent = baseIndent;
        // 删除 after 的第一个右括号
        String afterRest = after;
        if (afterRest.trimLeft().startsWith(right)) {
          afterRest = afterRest.replaceFirst(RegExp(r'^\s*' + RegExp.escape(right)), '');
        }
        final insertText = '\n' + baseIndent + _indentUnit + '\n' + dedent + right;
        final newText = before + '\n' + baseIndent + _indentUnit + '\n' + dedent + right + afterRest;
        final newCursor = before.length + 1 + baseIndent.length + _indentUnit.length;
        _controller.value = TextEditingValue(
          text: newText,
          selection: TextSelection.collapsed(offset: newCursor),
        );
        return;
      }
      // 普通缩进
      String newText;
      int newCursor;
      if (after.startsWith('\n')) {
        // 已经有换行，避免重复；跳过现有换行，在其后插入缩进
        newText = before + '\n' + indent + after.substring(1);
        newCursor = before.length + 1 + indent.length;
      } else {
        final insertText = '\n' + indent;
        newText = before + insertText + after;
        newCursor = before.length + insertText.length;
      }
      _controller.value = TextEditingValue(
        text: newText,
        selection: TextSelection.collapsed(offset: newCursor),
      );
    }
  }

  String _getAutoIndent({required String prevLine, String? nextLine, required String lang}) {
    final indentMatch = RegExp(r'^(\s*)').firstMatch(prevLine);
    String baseIndent = indentMatch?.group(0) ?? '';
    String extra = '';
    String dedent = '';
    final trimmedPrev = prevLine.trimRight();
    nextLine ??= '';
    if (lang == 'python') {
      if (trimmedPrev.endsWith(':')) {
        extra = _indentUnit;
      }
      // Python dedent keywords
      if (RegExp(r'^(return|break|continue|pass|raise)\b').hasMatch(nextLine.trimLeft())) {
        dedent = _indentUnit;
      }
    } else {
      if (trimmedPrev.endsWith('{') || trimmedPrev.endsWith('[') || trimmedPrev.endsWith('(') || trimmedPrev.endsWith(':')) {
        extra = _indentUnit;
      }
      if (nextLine.trimLeft().startsWith('}') || nextLine.trimLeft().startsWith(']') || nextLine.trimLeft().startsWith(')')) {
        dedent = _indentUnit;
      }
    }
    String indent = baseIndent + extra;
    if (dedent.isNotEmpty && indent.length >= dedent.length) {
      indent = indent.substring(0, indent.length - dedent.length);
    }
    return indent;
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: _focusNode,
      onKey: _onKey,
      child: CodeTheme(
        data: CodeThemeData(styles: widget.theme),
        child: CodeField(
          controller: _controller,
          textStyle: TextStyle(
            fontFamily: widget.fontFamily,
            fontWeight: widget.fontWeight,
            fontSize: widget.fontSize,
          ),
          expands: true,
          gutterStyle: const GutterStyle(
            showLineNumbers: true,
            showErrors: false,
            showFoldingHandles: true,
          ),
        ),
      ),
    );
  }

  dynamic _getLanguage(String lang) {
    switch (lang) {
      case 'dart':
        return dart;
      case 'cpp':
      case 'c++':
        return cpp;
      case 'python':
      case 'py':
        return python;
      default:
        return dart;
    }
  }
} 