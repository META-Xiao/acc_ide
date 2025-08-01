cmake_minimum_required(VERSION 3.22.1)

project("tree-sitter-jni")

# Configure C++ standard
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable debug symbols for debugging
set(CMAKE_BUILD_TYPE Debug)

# Add include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Create our JNI library
add_library(
    tree-sitter-jni
    SHARED
    tree_sitter_jni.cpp
)

# Find the log library (required for Android logging)
find_library(log-lib log)

# Find the pre-built Tree-sitter libraries
# These should be in jniLibs directory
set(TREE_SITTER_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Check if Tree-sitter libraries exist for this architecture
if(NOT EXISTS ${TREE_SITTER_LIB_DIR})
    message(WARNING "Tree-sitter library directory not found: ${TREE_SITTER_LIB_DIR}")
    message(WARNING "Skipping JNI library build for ${ANDROID_ABI}")
    return()
endif()

if(NOT EXISTS ${TREE_SITTER_LIB_DIR}/libtree-sitter.so)
    message(WARNING "Tree-sitter core library not found for ${ANDROID_ABI}")
    message(WARNING "Skipping JNI library build for ${ANDROID_ABI}")
    return()
endif()

message(STATUS "Found Tree-sitter libraries for ${ANDROID_ABI}")

# Import Tree-sitter core library
add_library(tree-sitter SHARED IMPORTED)
set_target_properties(tree-sitter PROPERTIES
    IMPORTED_LOCATION ${TREE_SITTER_LIB_DIR}/libtree-sitter.so
)

# Link just the core library initially
set(TREE_SITTER_LIBS tree-sitter ${log-lib})

# Conditionally add language libraries if they exist
if(EXISTS ${TREE_SITTER_LIB_DIR}/libtree-sitter-java.so)
    add_library(tree-sitter-java SHARED IMPORTED)
    set_target_properties(tree-sitter-java PROPERTIES
        IMPORTED_LOCATION ${TREE_SITTER_LIB_DIR}/libtree-sitter-java.so
    )
    list(APPEND TREE_SITTER_LIBS tree-sitter-java)
    message(STATUS "Found Tree-sitter Java library")
endif()

if(EXISTS ${TREE_SITTER_LIB_DIR}/libtree-sitter-cpp.so)
    add_library(tree-sitter-cpp SHARED IMPORTED)
    set_target_properties(tree-sitter-cpp PROPERTIES
        IMPORTED_LOCATION ${TREE_SITTER_LIB_DIR}/libtree-sitter-cpp.so
    )
    list(APPEND TREE_SITTER_LIBS tree-sitter-cpp)
    message(STATUS "Found Tree-sitter C++ library")
endif()

if(EXISTS ${TREE_SITTER_LIB_DIR}/libtree-sitter-python.so)
    add_library(tree-sitter-python SHARED IMPORTED)
    set_target_properties(tree-sitter-python PROPERTIES
        IMPORTED_LOCATION ${TREE_SITTER_LIB_DIR}/libtree-sitter-python.so
    )
    list(APPEND TREE_SITTER_LIBS tree-sitter-python)
    message(STATUS "Found Tree-sitter Python library")
endif()

# Link all found libraries to our JNI library
target_link_libraries(tree-sitter-jni ${TREE_SITTER_LIBS})

message(STATUS "Successfully configured Tree-sitter libraries for ${ANDROID_ABI}")

# Add compiler flags
target_compile_options(tree-sitter-jni PRIVATE
    -Wall
    -Wextra
    -fPIC
    -fvisibility=hidden
)

# Add linker flags for 16KB page size compatibility (Android 15+)
target_link_options(tree-sitter-jni PRIVATE
    -Wl,-z,max-page-size=16384
)

# Add preprocessor definitions
target_compile_definitions(tree-sitter-jni PRIVATE
    ANDROID
)

message(STATUS "CMake configuration completed for tree-sitter-jni") 