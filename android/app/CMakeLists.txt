cmake_minimum_required(VERSION 3.22)
project(acgc_android C CXX)

set(SDL_TEST OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)

set(SDL_ROOT "${CMAKE_CURRENT_LIST_DIR}/../SDL")
set(ACGC_ROOT "${CMAKE_CURRENT_LIST_DIR}/../..")

if(NOT EXISTS "${SDL_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR
        "SDL2 is missing. Run: git submodule update --init --recursive")
endif()

add_subdirectory("${SDL_ROOT}" "${CMAKE_CURRENT_BINARY_DIR}/sdl")
add_subdirectory("${ACGC_ROOT}/pc" "${CMAKE_CURRENT_BINARY_DIR}/acgc")
