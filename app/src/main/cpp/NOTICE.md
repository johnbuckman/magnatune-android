# Third-party native code (AirPlay sender)

The AirPlay-1 (RAOP) sender embedded under `raop/` vendors and adapts third-party C:

- **libraop** — RAOP client by Philippe G. (`philippe44`), https://github.com/philippe44/libraop
  (`raop_client.c/.h`, `rtsp_client.c/.h`, `aes.c/.h`). Adapted for Android: OpenSSL removed
  (RSA-OAEP is performed in Kotlin; randomness from `/dev/urandom`), AirPlay-2 pair-verify
  stubbed, and logging routed to Android logcat.
- **curve25519 / ed25519** — public-domain implementation by Mehdi Sotoodeh
  (https://github.com/msotoodeh/curve25519), vendored under `raop/curve25519/`, used for the
  X25519 key in the AirPlay-2 `/auth-setup` handshake.
- **`raop/alac_raw.c`** — `pcm_to_alac_raw` extracted from `philippe44/libcodecs`
  (`alac_wrapper.cpp`); the full ALAC encoder is stubbed out.
- **`raop/md5.c`** — compact public-domain MD5 (RFC 1321 / Alexander Peslyak reference style),
  used only for optional RTSP HTTP digest auth.

See each source file's header for its original copyright/license notice.
