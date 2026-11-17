# Independent Code46 release verification

The existing original publisher deployed source `577f0931e7937ffcac02dc855a540f093facdb3b` as version 46 / 0.3.45. It became LIVE at 2026-11-06T19:00:55.5398Z.

Root independently verified the actual downloaded APK at 19:05:55Z: 101876503 bytes, SHA256 `0930614bbcc8e53d75283dfe47a53a73e758430a99af3698f31b88446babc759`, original package, exactly one original certificate and a valid APK Signature Scheme v2 signature. aapt2 confirms the package and version inside the binary.

Root reused the primary workspace's completed HTTPS download, then independently rehashed and signature-checked its bytes. Fresh live catalog bytes match the downloaded catalog. The Android catalog contract and exactly six unique BUILD-INFO fields pass. A separate helper independently checked both public metadata endpoints at 19:05:38Z without downloading the APK.

## Raw evidence

- `root-verification.json`: commands, exit codes, identities and verification limits.
- `apksigner.txt`, `aapt2.txt`, `catalog-contract.txt`, `live-manifest.txt`: unmodified command outputs.
- `latest.json`, `BUILD-INFO.txt`: actual public metadata.
- `https-receipt.json`: the primary workspace's preceding completed HTTPS byte-download receipt.

The manifest-only command says APK_NOT_VERIFIED because it does not read the binary. The aggregate root result additionally checks actual APK SHA256 and apksigner/aapt2. No private signing material was retrieved. Physical Fold7 installation, actual model answers, audio and GPU acceptance remain NOT_RUN.
