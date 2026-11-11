# Mesh Chat v1.1 (Native Android, Java)
Offline Bluetooth (BLE) mesh messenger. Open this folder in Android Studio (Hedgehog+/JDK 17), sync Gradle, run on 2+ real phones (BLE advertising does not work on emulators).

- Relay Mode: Automatic (toggle in Settings)  |  Store & Forward: on (toggle in Settings)
- Message: id, from, to, hops, timestamp, expiry (24h), path, status Sent -> Relayed -> Delivered (ACK)
- Android 11 and below: turn Location ON (needed for BLE scan).

## GitHub Actions
`.github/workflows/build.yml` is included. Push to `main` -> Actions tab -> Artifacts -> `MeshChat-debug-apk`.

## v1.1 additions
- Foreground service (mesh keeps running in background) + notifications for new messages
- End-to-end encryption (ECDH P-256 + AES-256-GCM); public keys spread through the mesh (KEY broadcast); ID = hash of public key
- Unread badges, last-message time, block / unblock users, dark mode (follows system), live mesh animation, coloured avatars
