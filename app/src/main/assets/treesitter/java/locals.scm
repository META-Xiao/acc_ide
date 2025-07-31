; Java Local Variables and Scoping Rules
; Enhanced variable scope analysis for Java

; Scopes
[
  (class_body)
  (interface_body)
  (constructor_body)
  (method_declaration)
  (block)
  (enhanced_for_statement)
  (for_statement)
  (while_statement)
  (if_statement)
  (try_statement)
  (catch_clause)
  (finally_clause)
  (switch_expression)
  (lambda_expression)
] @local.scope

; Definitions
(method_declaration
  name: (identifier) @local.definition)

(constructor_declaration
  name: (identifier) @local.definition)

(class_declaration
  name: (identifier) @local.definition)

(interface_declaration
  name: (identifier) @local.definition)

(enum_declaration
  name: (identifier) @local.definition)

(variable_declarator
  name: (identifier) @local.definition)

(formal_parameter
  name: (identifier) @local.definition)

(catch_formal_parameter
  name: (identifier) @local.definition)

(enhanced_for_statement
  name: (identifier) @local.definition)

(lambda_expression
  parameters: (identifier) @local.definition)

; Field definitions
(field_declaration
  declarator: (variable_declarator
    name: (identifier) @local.definition))

; References
(identifier) @local.reference

; Import statements
(import_declaration
  (scoped_identifier
    name: (identifier) @local.import))

; Package declarations
(package_declaration
  (scoped_identifier) @local.import) 