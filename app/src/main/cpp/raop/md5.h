/* Minimal MD5 with an OpenSSL-compatible one-shot MD5() entry point.
 * Only used for optional RTSP HTTP digest auth (password-protected AirPlay devices). */
#ifndef __MD5_COMPAT_H_
#define __MD5_COMPAT_H_
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Computes MD5 of `len` bytes at `data` into the 16-byte `out`; returns `out`. */
unsigned char *MD5(const unsigned char *data, size_t len, unsigned char *out);

#ifdef __cplusplus
}
#endif
#endif
