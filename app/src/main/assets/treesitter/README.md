# Tree-sitter Query Files

This directory contains Tree-sitter query files for enhanced syntax highlighting and analysis.

## Directory Structure

```
treesitter/
├── java/
│   ├── highlights.scm  (optional - Java syntax highlighting queries)
│   └── locals.scm      (optional - Java local variable scope queries)
├── cpp/
│   ├── highlights.scm  (optional - C++ syntax highlighting queries)
│   └── locals.scm      (optional - C++ local variable scope queries)
└── python/
    ├── highlights.scm  (optional - Python syntax highlighting queries)
    └── locals.scm      (optional - Python local variable scope queries)
```

## Usage

The `TreeSitterAnalyzer` will automatically use query files from these directories if they exist. If no custom query files are found, it will fall back to built-in queries.

### Highlights Query Files (highlights.scm)

These files define syntax highlighting rules for Tree-sitter. They use Tree-sitter's query syntax to match AST nodes and assign highlighting categories.

Example Java highlights.scm:
```scm
(comment) @comment

[
  "public"
  "private"
  "class"
] @keyword

(string_literal) @string
(number_literal) @number
```

### Locals Query Files (locals.scm)

These files define scoping rules for variables and functions, used for features like "go to definition" and semantic analysis.

Example Java locals.scm:
```scm
; Scopes
[
  (class_body)
  (method_declaration)
  (block)
] @local.scope

; Definitions
(method_declaration
  name: (identifier) @local.definition)

(variable_declarator
  name: (identifier) @local.definition)

; References
(identifier) @local.reference
```

## Resources

- [Tree-sitter Query Syntax](https://tree-sitter.github.io/tree-sitter/using-parsers#query-syntax)
- [Tree-sitter Java Grammar](https://github.com/tree-sitter/tree-sitter-java)
- [Tree-sitter C++ Grammar](https://github.com/tree-sitter/tree-sitter-cpp)
- [Tree-sitter Python Grammar](https://github.com/tree-sitter/tree-sitter-python)

## Notes

- If you don't add custom query files, the analyzer will use reasonable built-in defaults
- Custom query files allow for more precise syntax highlighting and analysis
- You can copy query files from the official Tree-sitter language repositories and modify them as needed 