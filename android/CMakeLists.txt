cmake_minimum_required(VERSION 3.22.1)
project(w3aproject C)

add_library(w3aproject SHARED jni/project_info.c)
target_compile_options(w3aproject PRIVATE -Os -fvisibility=hidden)
# Devices with 16 KB pages load only libraries aligned to them.
target_link_options(w3aproject PRIVATE -Wl,-z,max-page-size=16384)

# The patched Lua of the scripted maps, with its JNI bridge.
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../lua/native lua)
