#include "tree_sitter_jni.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "TreeSitterJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace tree_sitter_jni {

std::string jstring_to_string(JNIEnv *env, jstring jstr) {
    if (!jstr) return "";
    
    const char *chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return "";
    
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

jstring string_to_jstring(JNIEnv *env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

} // namespace tree_sitter_jni

extern "C" {

// =================================================================
// TSLanguage implementations  
// =================================================================

JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_00024TSLanguageJava_getNativePtr(
    JNIEnv *env, jobject thiz) {
    
    LOGD("Getting Tree-sitter Java language");
    TSLanguage *language = tree_sitter_java();
    if (!language) {
        LOGE("Failed to get Java language");
        return 0;
    }
    
    LOGD("Java language obtained successfully: %p", language);
    return reinterpret_cast<jlong>(language);
}

JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_00024TSLanguageCpp_getNativePtr(
    JNIEnv *env, jobject thiz) {
    
    LOGD("Getting Tree-sitter C++ language");
    TSLanguage *language = tree_sitter_cpp();
    if (!language) {
        LOGE("Failed to get C++ language");
        return 0;
    }
    
    LOGD("C++ language obtained successfully: %p", language);
    return reinterpret_cast<jlong>(language);
}

JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_00024TSLanguagePython_getNativePtr(
    JNIEnv *env, jobject thiz) {
    
    LOGD("Getting Tree-sitter Python language");
    TSLanguage *language = tree_sitter_python();
    if (!language) {
        LOGE("Failed to get Python language");
        return 0;
    }
    
    LOGD("Python language obtained successfully: %p", language);
    return reinterpret_cast<jlong>(language);
}

// =================================================================
// TSQuery implementations
// =================================================================

JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_00024TSQuery_00024Companion_create(
    JNIEnv *env, jobject thiz, jlong language_ptr, jstring query_string) {
    
    if (language_ptr == 0) {
        LOGE("Invalid language pointer");
        return 0;
    }
    
    TSLanguage *language = reinterpret_cast<TSLanguage*>(language_ptr);
    std::string query_str = tree_sitter_jni::jstring_to_string(env, query_string);
    
    if (query_str.empty()) {
        LOGE("Empty query string");
        return 0;
    }
    
    LOGD("Creating query: %s", query_str.c_str());
    
    uint32_t error_offset;
    TSQueryError error_type;
    
    TSQuery *query = ts_query_new(
        language,
        query_str.c_str(),
        query_str.length(),
        &error_offset,
        &error_type
    );
    
    if (!query) {
        LOGE("Failed to create query, error_type: %d, error_offset: %u", error_type, error_offset);
        return 0;
    }
    
    LOGD("Query created successfully: %p", query);
    return reinterpret_cast<jlong>(query);
}

// =================================================================
// Parser implementations
// =================================================================

JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_createParser(JNIEnv *env, jobject thiz) {
    LOGD("Creating Tree-sitter parser");
    
    TSParser *parser = ts_parser_new();
    if (!parser) {
        LOGE("Failed to create parser");
        return 0;
    }
    
    LOGD("Parser created successfully: %p", parser);
    return reinterpret_cast<jlong>(parser);
}

JNIEXPORT jboolean JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_setLanguage(
    JNIEnv *env, jobject thiz, jlong parser_ptr, jlong language_ptr) {
    
    if (parser_ptr == 0 || language_ptr == 0) {
        LOGE("Invalid parser or language pointer");
        return JNI_FALSE;
    }
    
    TSParser *parser = reinterpret_cast<TSParser*>(parser_ptr);
    TSLanguage *language = reinterpret_cast<TSLanguage*>(language_ptr);
    
    LOGD("Setting language %p for parser %p", language, parser);
    
    bool result = ts_parser_set_language(parser, language);
    if (!result) {
        LOGE("Failed to set language for parser");
        return JNI_FALSE;
    }
    
    LOGD("Language set successfully");
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_parseString(
    JNIEnv *env, jobject thiz, jlong parser_ptr, jstring source_code) {
    
    if (parser_ptr == 0) {
        LOGE("Invalid parser pointer");
        return 0;
    }
    
    TSParser *parser = reinterpret_cast<TSParser*>(parser_ptr);
    std::string source = tree_sitter_jni::jstring_to_string(env, source_code);
    
    if (source.empty()) {
        LOGE("Empty source code");
        return 0;
    }
    
    LOGD("Parsing source code (%zu bytes)", source.length());
    
    TSTree *tree = ts_parser_parse_string(
        parser,
        nullptr,
        source.c_str(),
        source.length()
    );
    
    if (!tree) {
        LOGE("Failed to parse source code");
        return 0;
    }
    
    LOGD("Source parsed successfully, tree: %p", tree);
    return reinterpret_cast<jlong>(tree);
}

// =================================================================
// Cleanup functions
// =================================================================

JNIEXPORT void JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_deleteParser(
    JNIEnv *env, jobject thiz, jlong parser_ptr) {
    
    if (parser_ptr != 0) {
        TSParser *parser = reinterpret_cast<TSParser*>(parser_ptr);
        LOGD("Deleting parser: %p", parser);
        ts_parser_delete(parser);
    }
}

JNIEXPORT void JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_deleteTree(
    JNIEnv *env, jobject thiz, jlong tree_ptr) {
    
    if (tree_ptr != 0) {
        TSTree *tree = reinterpret_cast<TSTree*>(tree_ptr);
        LOGD("Deleting tree: %p", tree);
        ts_tree_delete(tree);
    }
}

JNIEXPORT void JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_deleteQuery(
    JNIEnv *env, jobject thiz, jlong query_ptr) {
    
    if (query_ptr != 0) {
        TSQuery *query = reinterpret_cast<TSQuery*>(query_ptr);
        LOGD("Deleting query: %p", query);
        ts_query_delete(query);
    }
}

} // extern "C" 