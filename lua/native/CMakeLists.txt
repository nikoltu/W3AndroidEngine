cmake_minimum_required(VERSION 3.22.1)
project(warsmashlua C)

# The engine's patched Lua 5.3.6 and its JNI bridge. Built by the Android module for every ABI and, on the
# desktop, by hand (cmake -S lua/native -B build && cmake --build build) or by the Gradle task buildNativeDesktop.
# libm: the engine's own copy of the math routines, so that sin, exp, pow, ... give the same bits on every platform.
file(GLOB LUA_SOURCES ${CMAKE_CURRENT_LIST_DIR}/lua-5.3.6/*.c ${CMAKE_CURRENT_LIST_DIR}/libm/*.c)
add_library(warsmashlua SHARED ${LUA_SOURCES} ${CMAKE_CURRENT_LIST_DIR}/bridge/luabridge.c)
target_include_directories(warsmashlua PRIVATE ${CMAKE_CURRENT_LIST_DIR}/lua-5.3.6)

if(NOT ANDROID)
  find_package(JNI REQUIRED)
  target_include_directories(warsmashlua PRIVATE ${JNI_INCLUDE_DIRS})
endif()

if(MSVC)
  # Strict IEEE arithmetic: no fused or reordered operations.
  target_compile_options(warsmashlua PRIVATE /O2 /fp:strict)
else()
  # The scripts' arithmetic must give the same bits on every CPU: no fused multiply-add, no fast-math.
  target_compile_options(warsmashlua PRIVATE -std=gnu99 -O2 -ffp-contract=off -fno-fast-math -fvisibility=hidden)
endif()
if(ANDROID)
  # Devices with 16 KB pages load only libraries aligned to them.
  target_link_options(warsmashlua PRIVATE -Wl,-z,max-page-size=16384)
endif()
