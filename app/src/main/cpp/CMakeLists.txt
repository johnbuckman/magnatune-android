cmake_minimum_required(VERSION 3.22.1)
project(raopjni C)

# libraop (philippe44) AirPlay-1 sender, trimmed for Android:
#  - OpenSSL removed (RSA-OAEP done in Kotlin, RAND via /dev/urandom)
#  - AirPlay-2 pairing (ed25519/aes_ctr) stubbed
#  - ALAC encoder replaced by the self-contained RAOP_ALAC_RAW packer
#  - MD5 vendored for optional RTSP digest auth
add_library(raopjni SHARED
        raop/raop_jni.c
        raop/raop_client.c
        raop/rtsp_client.c
        raop/aes.c
        raop/alac_raw.c
        raop/md5.c
        raop/cross_net.c
        raop/cross_log.c
        raop/cross_util.c
        raop/cross_thread.c
        # bundled pure-C X25519 for AirPlay-2 /auth-setup (no OpenSSL)
        raop/curve25519/curve25519_dh.c
        raop/curve25519/curve25519_mehdi.c
        raop/curve25519/curve25519_order.c
        raop/curve25519/curve25519_utils.c
        raop/curve25519/custom_blind.c
        raop/curve25519/ed25519_sign.c
        raop/curve25519/ed25519_verify.c
        raop/curve25519/sha512.c)

target_include_directories(raopjni PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/raop
        ${CMAKE_CURRENT_SOURCE_DIR}/raop/curve25519)

# _GNU_SOURCE: asprintf / strcasestr used by the RTSP layer.
target_compile_definitions(raopjni PRIVATE _GNU_SOURCE)
target_compile_options(raopjni PRIVATE
        -Wno-unused-parameter -Wno-unused-variable -Wno-unused-function
        -Wno-pointer-sign -Wno-format -Wno-implicit-function-declaration)

find_library(log-lib log)
target_link_libraries(raopjni ${log-lib})
