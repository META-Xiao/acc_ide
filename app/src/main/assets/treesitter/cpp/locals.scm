; C++ Local Variables and Scoping Rules
; Enhanced variable scope analysis for C++

; Scopes
[
  (compound_statement)
  (function_definition)
  (class_specifier)
  (struct_specifier)
  (enum_specifier)
  (union_specifier)
  (namespace_definition)
  (for_statement)
  (while_statement)
  (do_statement)
  (if_statement)
  (switch_statement)
  (try_statement)
  (catch_clause)
  (lambda_expression)
  (for_range_loop)
] @local.scope

; Function definitions
(function_definition
  declarator: (function_declarator
    declarator: (identifier) @local.definition))

(function_definition
  declarator: (function_declarator
    declarator: (qualified_identifier
      name: (identifier) @local.definition)))

; Class and struct definitions
(class_specifier
  name: (type_identifier) @local.definition)

(struct_specifier
  name: (type_identifier) @local.definition)

(enum_specifier
  name: (type_identifier) @local.definition)

(union_specifier
  name: (type_identifier) @local.definition)

; Namespace definitions
(namespace_definition
  name: (identifier) @local.definition)

; Variable declarations
(declaration
  declarator: (init_declarator
    declarator: (identifier) @local.definition))

(declaration
  declarator: (identifier) @local.definition)

(parameter_declaration
  declarator: (identifier) @local.definition)

(parameter_declaration
  declarator: (abstract_declarator
    declarator: (identifier) @local.definition))

; For loop variables
(for_statement
  initializer: (declaration
    declarator: (init_declarator
      declarator: (identifier) @local.definition)))

(for_range_loop
  declarator: (identifier) @local.definition)

; Lambda parameters
(lambda_expression
  declarator: (abstract_function_declarator
    parameters: (parameter_list
      (parameter_declaration
        declarator: (identifier) @local.definition))))

; Field declarations
(field_declaration
  declarator: (field_declarator
    declarator: (identifier) @local.definition))

; References
(identifier) @local.reference

; Type references
(type_identifier) @local.reference

; Using declarations
(using_declaration
  (qualified_identifier
    name: (identifier) @local.import))

(using_declaration
  (identifier) @local.import) 