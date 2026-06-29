/*
 * JNI bridge for com.magnatune.player.peer.RaopNative — drives libraop (philippe44) to
 * stream PCM to an AirPlay-1 device. RSA-OAEP of the AES key is done in Kotlin and supplied
 * via raopcl_set_aes(); this layer just wires create/connect/accept/send/volume/disconnect.
 */
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <arpa/inet.h>
#include <netinet/in.h>

#include "platform.h"
#include "cross_log.h"
#include "raop_client.h"

// Library log levels (the lib externs these; the caller must define them).
log_level raop_loglevel = lWARN;
log_level util_loglevel = lWARN;

#define DEFAULT_LATENCY_FRAMES 44100  // ~1s at 44100 Hz (cliraop default = MS2TS(1000,44100))

static struct raopcl_s *H(jlong h) { return (struct raopcl_s *) (intptr_t) h; }

JNIEXPORT jlong JNICALL
Java_com_magnatune_player_peer_RaopNative_nativeConnect(JNIEnv *env, jclass clazz,
        jstring jhost, jint port, jbyteArray jkey, jbyteArray jiv, jstring jrsaaeskey, jfloat volume) {
    const char *host = (*env)->GetStringUTFChars(env, jhost, NULL);
    const char *rsaaeskey = (*env)->GetStringUTFChars(env, jrsaaeskey, NULL);
    uint8_t key[16], iv[16];
    (*env)->GetByteArrayRegion(env, jkey, 0, 16, (jbyte *) key);
    (*env)->GetByteArrayRegion(env, jiv, 0, 16, (jbyte *) iv);

    struct in_addr local; local.s_addr = INADDR_ANY;          // bind any local iface
    struct in_addr dev;   dev.s_addr   = inet_addr(host);     // the AirPlay device

    // Stream unencrypted ALAC (et=0, universally supported). et="0,4" makes libraop run the
    // FairPlay/MFiSAP /auth-setup handshake that AirPlay-2 receivers require before streaming.
    char et[] = "0,4";
    // RAOP_PCM (big-endian L16) — universally decodable (cn=0). ALAC_RAW's uncompressed-escape
    // frames render as silence on some decoders (e.g. Sonos), so prefer raw PCM on LAN.
    struct raopcl_s *p = raopcl_create(local, 0, 0, NULL, NULL,
            RAOP_PCM, DEFAULT_FRAMES_PER_CHUNK, DEFAULT_LATENCY_FRAMES,
            RAOP_CLEAR, false, NULL, NULL, et, NULL,
            44100, 16, 2, raopcl_float_volume((int) (volume * 100)));

    jlong handle = 0;
    if (p) {
        raopcl_set_aes(p, key, iv, rsaaeskey);
        if (raopcl_connect(p, dev, (uint16_t) port, true)) {
            handle = (jlong) (intptr_t) p;
        } else {
            raopcl_destroy(p);
        }
    }
    (*env)->ReleaseStringUTFChars(env, jhost, host);
    (*env)->ReleaseStringUTFChars(env, jrsaaeskey, rsaaeskey);
    return handle;
}

JNIEXPORT jboolean JNICALL
Java_com_magnatune_player_peer_RaopNative_nativeAcceptFrames(JNIEnv *env, jclass clazz, jlong h) {
    if (!h) return JNI_FALSE;
    return raopcl_accept_frames(H(h)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_magnatune_player_peer_RaopNative_nativeSendChunk(JNIEnv *env, jclass clazz,
        jlong h, jbyteArray jpcm, jint frames) {
    if (!h) return JNI_FALSE;
    jbyte *pcm = (*env)->GetByteArrayElements(env, jpcm, NULL);
    uint64_t playtime = 0;
    bool ok = raopcl_send_chunk(H(h), (uint8_t *) pcm, frames, &playtime);
    (*env)->ReleaseByteArrayElements(env, jpcm, pcm, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_magnatune_player_peer_RaopNative_nativeSetVolume(JNIEnv *env, jclass clazz, jlong h, jfloat vol) {
    if (h) raopcl_set_volume(H(h), raopcl_float_volume((int) (vol * 100)));
}

JNIEXPORT void JNICALL
Java_com_magnatune_player_peer_RaopNative_nativeDisconnect(JNIEnv *env, jclass clazz, jlong h) {
    if (h) { raopcl_disconnect(H(h)); raopcl_destroy(H(h)); }
}

JNIEXPORT jint JNICALL
Java_com_magnatune_player_peer_RaopNative_nativeLatencyMs(JNIEnv *env, jclass clazz, jlong h) {
    if (!h) return 0;
    uint32_t sr = raopcl_sample_rate(H(h));
    if (!sr) return 0;
    return (jint) ((uint64_t) raopcl_latency(H(h)) * 1000 / sr);
}
