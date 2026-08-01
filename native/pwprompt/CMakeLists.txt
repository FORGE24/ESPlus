cmake_minimum_required(VERSION 3.21)
project(esplus-pwprompt LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)

set(CMAKE_PREFIX_PATH "D:/Qt/6.11.1/msvc2022_64" CACHE PATH "Qt6 prefix" FORCE)

find_package(Qt6 REQUIRED COMPONENTS Widgets)

add_executable(esplus-pwprompt
    src/main.cpp
    src/PasswordDialog.cpp
    src/PasswordDialog.h
)

target_link_libraries(esplus-pwprompt PRIVATE Qt6::Widgets)
