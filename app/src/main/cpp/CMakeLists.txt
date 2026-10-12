cmake_minimum_required(VERSION 3.22.1)
project(sealedabi C)

add_library(sealedabi SHARED abi.c)
target_compile_options(sealedabi PRIVATE
    -fPIC
    -fno-common
    -fstack-protector-strong
    $<$<CONFIG:Release>:-D_FORTIFY_SOURCE=2>
    $<$<CONFIG:Release>:-O2>
)
target_link_options(sealedabi PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,relro"
    "-Wl,-z,now"
    "-Wl,-z,noexecstack"
)
