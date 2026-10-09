cmake_minimum_required(VERSION 3.22.1)
project(sealedabi C)

add_library(sealedabi SHARED abi.c)
target_link_options(sealedabi PRIVATE "-Wl,-z,max-page-size=16384")
