#ifndef TREE_SITTER_JNI_H
#define TREE_SITTER_JNI_H

#include <jni.h>
#include <string>
#include <memory>

// Tree-sitter core includes
extern "C" {
    #include "tree_sitter/api.h"
}

// Tree-sitter language declarations
extern "C" {
    TSLanguage *tree_sitter_java(void);
    TSLanguage *tree_sitter_cpp(void);
    TSLanguage *tree_sitter_python(void);
}

// JNI method signatures for TreeSitterWrapper classes
extern "C" {
    
// TSLanguageJava methods
JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_00024TSLanguageJava_00024Companion_getNativePtr(
    JNIEnv *env, jobject thiz);

// TSLanguageCpp methods  
JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_00024TSLanguageCpp_00024Companion_getNativePtr(
    JNIEnv *env, jobject thiz);

// TSLanguagePython methods
JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_00024TSLanguagePython_00024Companion_getNativePtr(
    JNIEnv *env, jobject thiz);

// TSQuery methods
JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_00024TSQuery_00024Companion_create(
    JNIEnv *env, jobject thiz, jlong language_ptr, jstring query_string);

// Additional Tree-sitter utility methods
JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_createParser(JNIEnv *env, jobject thiz);

JNIEXPORT jboolean JNICALL  
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_setLanguage(
    JNIEnv *env, jobject thiz, jlong parser_ptr, jlong language_ptr);

JNIEXPORT jlong JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_parseString(
    JNIEnv *env, jobject thiz, jlong parser_ptr, jstring source_code);

JNIEXPORT void JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_deleteParser(
    JNIEnv *env, jobject thiz, jlong parser_ptr);

JNIEXPORT void JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_deleteTree(
    JNIEnv *env, jobject thiz, jlong tree_ptr);

JNIEXPORT void JNICALL
Java_com_acc_1ide_lsp_model_TreeSitterWrapper_deleteQuery(
    JNIEnv *env, jobject thiz, jlong query_ptr);

}

namespace tree_sitter_jni {
    std::string jstring_to_string(JNIEnv *env, jstring jstr);
    jstring string_to_jstring(JNIEnv *env, const std::string& str);
}

#endif // TREE_SITTER_JNI_H 