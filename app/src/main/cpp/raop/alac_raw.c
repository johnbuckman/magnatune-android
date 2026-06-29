/*
 * Minimal ALAC shim for the Magnatune Android RAOP sender.
 *
 * We only ever stream with RAOP_ALAC_RAW (uncompressed ALAC framing), so the only function
 * actually needed is pcm_to_alac_raw — extracted verbatim from philippe44/libcodecs
 * (addons/alac_wrapper.cpp, GPL-2.0-or-later, Copyright Philippe <philippe44@outlook.com>).
 * The real ALAC encoder/decoder (macosforge) is replaced by stubs so the heavy codec never
 * has to be compiled. raop_client.c references the encoder symbols but never calls them when
 * the codec is RAOP_ALAC_RAW.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include "platform.h"
#include "alac_wrapper.h"

/* assumes stereo and little endian, 16-bit samples (4 bytes/frame) */
bool pcm_to_alac_raw(uint8_t *sample, int frames, uint8_t **out, int *size, int bsize) {
	uint8_t *p;
	uint32_t *in = (uint32_t*) sample;
	int count;

	frames = min(frames, bsize);

	*out = (uint8_t*) malloc(bsize * 4 + 16);
	p = *out;

	*p++ = (1 << 5);
	*p++ = 0;
	*p++ = (1 << 4) | (1 << 1) | ((bsize & 0x80000000) >> 31); // b31
	*p++ = ((bsize & 0x7f800000) << 1) >> 24;	// b30--b23
	*p++ = ((bsize & 0x007f8000) << 1) >> 16;	// b22--b15
	*p++ = ((bsize & 0x00007f80) << 1) >> 8;	// b14--b7
	*p =   ((bsize & 0x0000007f) << 1);       	// b6--b0
	*p++ |= (*in &  0x00008000) >> 15;			// LB1 b7

	count = frames - 1;

	while (count--) {
		*p++ = ((*in & 0x00007f80) >> 7);
		*p++ = ((*in & 0x0000007f) << 1) | ((*in & 0x80000000) >> 31);
		*p++ = ((*in & 0x7f800000) >> 23);
		*p++ = ((*in & 0x007f0000) >> 15) | ((*(in + 1) & 0x00008000) >> 15);
		in++;
	}

	// last sample
	*p++ = ((*in & 0x00007f80) >> 7);
	*p++ = ((*in & 0x0000007f) << 1) | ((*in & 0x80000000) >> 31);
	*p++ = ((*in & 0x7f800000) >> 23);
	*p++ = ((*in & 0x007f0000) >> 15);

	// when readable size is less than bsize, fill 0 at the bottom
	count = (bsize - frames) * 4;
	while (count--)	*p++ = 0;

	*(p-1) |= 1;
	*p = (7 >> 1) << 6;

	*size = p - *out + 1;

	return true;
}

/* ---- stubs: never invoked for RAOP_ALAC_RAW, present only to satisfy the linker ---- */
struct alac_codec_s *alac_create_encoder(int max_frames, int sample_rate, int sample_size, int channels) {
	(void) max_frames; (void) sample_rate; (void) sample_size; (void) channels;
	return NULL;
}
void alac_delete_encoder(struct alac_codec_s *codec) { (void) codec; }
bool pcm_to_alac(struct alac_codec_s *codec, uint8_t *in, int frames, uint8_t **out, int *size) {
	(void) codec; (void) in; (void) frames; (void) out; (void) size; return false;
}
struct alac_codec_s *alac_create_decoder(int magic_cookie_size, uint8_t *magic_cookie,
								uint8_t *sample_size, unsigned *sample_rate,
								uint8_t *channels, unsigned int *block_size) {
	(void) magic_cookie_size; (void) magic_cookie; (void) sample_size;
	(void) sample_rate; (void) channels; (void) block_size; return NULL;
}
void alac_delete_decoder(struct alac_codec_s *codec) { (void) codec; }
bool alac_to_pcm(struct alac_codec_s *codec, uint8_t* input, uint8_t *output,
				 char channels, unsigned *out_frames) {
	(void) codec; (void) input; (void) output; (void) channels; (void) out_frames; return false;
}
