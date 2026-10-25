cmake_minimum_required(VERSION 3.20)
project(melodia_uninstaller LANGUAGES CXX RC)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# 静态 CRT，脱离安装目录运行时不依赖外部 VC++ 运行库
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

add_executable(uninstall WIN32 uninstaller.cpp uninstaller.rc uninstaller.manifest)
target_compile_definitions(uninstall PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE)
target_compile_options(uninstall PRIVATE /EHsc /permissive- /utf-8 /W4)
target_link_libraries(uninstall PRIVATE ws2_32 msi dwmapi gdiplus shell32 advapi32 user32 gdi32 shlwapi)
