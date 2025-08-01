#ifndef TREE_SITTER_API_H_
#define TREE_SITTER_API_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

// =================================================================
// Basic Types
// =================================================================

typedef uint16_t TSSymbol;
typedef uint16_t TSFieldId;
typedef struct TSLanguage TSLanguage;
typedef struct TSParser TSParser;
typedef struct TSTree TSTree;
typedef struct TSQuery TSQuery;
typedef struct TSQueryCursor TSQueryCursor;
typedef struct TSLookaheadIterator TSLookaheadIterator;

typedef struct {
  uint32_t id;
  const TSTree *tree;
} TSNode;

typedef struct {
  const void *tree;
  const void *id;
  uint32_t index;
} TSTreeCursor;

// =================================================================
// Enums and Constants  
// =================================================================

typedef enum {
  TSInputEncodingUTF8,
  TSInputEncodingUTF16,
} TSInputEncoding;

typedef enum {
  TSQueryErrorNone = 0,
  TSQueryErrorSyntax,
  TSQueryErrorNodeType,
  TSQueryErrorField,
  TSQueryErrorCapture,
  TSQueryErrorStructure,
  TSQueryErrorLanguage,
} TSQueryError;

typedef enum {
  TSLogTypeParse,
  TSLogTypeLex,
} TSLogType;

typedef enum {
  TSQuantifierZero = 0,
  TSQuantifierZeroOrOne,
  TSQuantifierZeroOrMore,
  TSQuantifierOne,
  TSQuantifierOneOrMore,
} TSQuantifier;

// =================================================================
// Structures
// =================================================================

typedef struct {
  uint32_t row;
  uint32_t column;
} TSPoint;

typedef struct {
  TSPoint start_point;
  TSPoint end_point;
  uint32_t start_byte;
  uint32_t end_byte;
} TSRange;

typedef struct {
  void *tree;
  TSNode node;
  uint32_t alias_sequence_id;
} TSQueryMatch;

typedef struct {
  TSNode node;
  uint32_t index;
} TSQueryCapture;

typedef struct {
  uint32_t start_byte;
  uint32_t old_end_byte;
  uint32_t new_end_byte;
  TSPoint start_point;
  TSPoint old_end_point;
  TSPoint new_end_point;
} TSInputEdit;

// =================================================================
// Language Functions
// =================================================================

uint32_t ts_language_version(const TSLanguage *);
uint32_t ts_language_symbol_count(const TSLanguage *);
uint32_t ts_language_state_count(const TSLanguage *);
TSSymbol ts_language_symbol_for_name(const TSLanguage *, const char *, uint32_t, bool);
const char *ts_language_symbol_name(const TSLanguage *, TSSymbol);

// =================================================================
// Parser Functions  
// =================================================================

TSParser *ts_parser_new(void);
void ts_parser_delete(TSParser *);
bool ts_parser_set_language(TSParser *, const TSLanguage *);
const TSLanguage *ts_parser_language(const TSParser *);

TSTree *ts_parser_parse(TSParser *, const TSTree *, TSRange *);
TSTree *ts_parser_parse_string(TSParser *, const TSTree *, const char *, uint32_t);
TSTree *ts_parser_parse_string_encoding(TSParser *, const TSTree *, const char *, uint32_t, TSInputEncoding);

void ts_parser_reset(TSParser *);
void ts_parser_set_timeout_micros(TSParser *, uint64_t);
uint64_t ts_parser_timeout_micros(const TSParser *);
void ts_parser_set_cancellation_flag(TSParser *, const size_t *);
const size_t *ts_parser_cancellation_flag(const TSParser *);

// =================================================================
// Tree Functions
// =================================================================  

TSTree *ts_tree_copy(const TSTree *);
void ts_tree_delete(TSTree *);
TSNode ts_tree_root_node(const TSTree *);
const TSLanguage *ts_tree_language(const TSTree *);

void ts_tree_edit(TSTree *, const TSInputEdit *);
TSRange *ts_tree_get_changed_ranges(const TSTree *, const TSTree *, uint32_t *);
void ts_tree_print_dot_graph(const TSTree *, int);

// =================================================================
// Node Functions
// =================================================================

const char *ts_node_type(TSNode);
TSSymbol ts_node_symbol(TSNode);
uint32_t ts_node_start_byte(TSNode);
uint32_t ts_node_end_byte(TSNode);
TSPoint ts_node_start_point(TSNode);
TSPoint ts_node_end_point(TSNode);

uint32_t ts_node_child_count(TSNode);
TSNode ts_node_child(TSNode, uint32_t);
TSNode ts_node_child_by_field_name(TSNode, const char *, uint32_t);
TSNode ts_node_child_by_field_id(TSNode, TSFieldId);
TSNode ts_node_named_child(TSNode, uint32_t);
uint32_t ts_node_named_child_count(TSNode);

TSNode ts_node_next_sibling(TSNode);
TSNode ts_node_next_named_sibling(TSNode);
TSNode ts_node_prev_sibling(TSNode);
TSNode ts_node_prev_named_sibling(TSNode);

TSNode ts_node_parent(TSNode);
TSNode ts_node_descendant_for_byte_range(TSNode, uint32_t, uint32_t);
TSNode ts_node_descendant_for_point_range(TSNode, TSPoint, TSPoint);
TSNode ts_node_named_descendant_for_byte_range(TSNode, uint32_t, uint32_t);
TSNode ts_node_named_descendant_for_point_range(TSNode, TSPoint, TSPoint);

bool ts_node_eq(TSNode, TSNode);
bool ts_node_is_null(TSNode);
bool ts_node_is_named(TSNode);
bool ts_node_is_missing(TSNode);
bool ts_node_is_extra(TSNode);
bool ts_node_has_changes(TSNode);
bool ts_node_has_error(TSNode);

char *ts_node_string(TSNode);

// =================================================================
// TreeCursor Functions
// =================================================================

TSTreeCursor ts_tree_cursor_new(TSNode);
void ts_tree_cursor_delete(TSTreeCursor *);
void ts_tree_cursor_reset(TSTreeCursor *, TSNode);

TSNode ts_tree_cursor_current_node(const TSTreeCursor *);
const char *ts_tree_cursor_current_field_name(const TSTreeCursor *);
TSFieldId ts_tree_cursor_current_field_id(const TSTreeCursor *);
bool ts_tree_cursor_goto_parent(TSTreeCursor *);
bool ts_tree_cursor_goto_next_sibling(TSTreeCursor *);
bool ts_tree_cursor_goto_first_child(TSTreeCursor *);

// =================================================================
// Query Functions
// =================================================================

TSQuery *ts_query_new(const TSLanguage *, const char *, uint32_t, uint32_t *, TSQueryError *);
void ts_query_delete(TSQuery *);

uint32_t ts_query_pattern_count(const TSQuery *);
uint32_t ts_query_capture_count(const TSQuery *);
uint32_t ts_query_string_count(const TSQuery *);

const char *ts_query_capture_name_for_id(const TSQuery *, uint32_t, uint32_t *);
const char *ts_query_string_value_for_id(const TSQuery *, uint32_t, uint32_t *);

void ts_query_disable_capture(TSQuery *, const char *, uint32_t);
void ts_query_disable_pattern(TSQuery *, uint32_t);

TSQueryCursor *ts_query_cursor_new(void);
void ts_query_cursor_delete(TSQueryCursor *);
void ts_query_cursor_exec(TSQueryCursor *, const TSQuery *, TSNode);
bool ts_query_cursor_did_exceed_match_limit(const TSQueryCursor *);
uint32_t ts_query_cursor_match_limit(const TSQueryCursor *);
void ts_query_cursor_set_match_limit(TSQueryCursor *, uint32_t);
void ts_query_cursor_set_byte_range(TSQueryCursor *, uint32_t, uint32_t);
void ts_query_cursor_set_point_range(TSQueryCursor *, TSPoint, TSPoint);

bool ts_query_cursor_next_match(TSQueryCursor *, TSQueryMatch *);
void ts_query_cursor_remove_match(TSQueryCursor *, uint32_t);
bool ts_query_cursor_next_capture(TSQueryCursor *, TSQueryMatch *, uint32_t *);

// =================================================================
// Additional Types for Advanced Usage
// =================================================================

typedef struct {
  const char *(*read)(void *payload, uint32_t byte_index, TSPoint position, uint32_t *bytes_read);
  TSInputEncoding encoding;
} TSInput;

typedef void (*TSLogger)(void *payload, TSLogType, const char *);

#ifdef __cplusplus
}
#endif

#endif // TREE_SITTER_API_H_ 