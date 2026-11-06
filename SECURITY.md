# Security Policy

Crunch is committed to safeguarding user privacy, device integrity, and maintaining transparent, secure open-source media extraction software.

---

## Supported Versions

Only the latest release family receives active security patches, bug fixes, and engine updates.

| Version | Supported | Notes |
| :--- | :---: | :--- |
| **1.0.7+** | :white_check_mark: | Official signed releases with permanent release keystore, Scoped Storage sync, and hardened permissions |
| **1.0.0 – 1.0.6** | :x: | Legacy developer builds with ephemeral signatures and deprecated permissions |

---

## Official Binary Verification & Signing Policy

All official Crunch APKs published under [GitHub Releases](https://github.com/cyberlog69/Crunch/releases) are cryptographically signed using a dedicated RSA-2048 release keystore (`crunch-release.jks`) supporting Android APK Signature Scheme v1, v2, and v3.

### Certificate Fingerprints

To ensure you are running an authentic binary built directly from this repository and have not downloaded a tampered APK, you can verify the signing certificate:

- **Distinguished Name (Owner/Issuer):** `CN=Crunch, OU=Crunch, O=Kappi, C=US`
- **SHA-256 Fingerprint:**  
  `32:F4:41:36:31:6E:6D:37:E7:D1:C5:04:B7:D3:D6:FA:BF:16:28:96:D4:87:D4:91:3B:E6:71:AA:64:8C:BE:96`
- **SHA-1 Fingerprint:**  
  `0D:1F:29:D5:84:95:12:A0:E2:46:26:94:C3:44:ED:63:39:77:F7:CB`
- **Public Key Algorithm:** 2048-bit RSA with `SHA384withRSA`
- **Validity:** Valid until March 24, 2054

### Verifying an APK Locally

Using Android SDK's `apksigner`:
```bash
apksigner verify --print-certs crunch-v1.0.7-arm64-v8a-release.apk
```
Check that the printed `Signer #1 certificate SHA-256 digest` matches the fingerprint above.

> [!NOTE]
> **One-Time Upgrade Notice from Pre-1.0.7 Builds:**  
> Early development builds (v1.0.5 and earlier) were signed with ephemeral debug keys. Android enforces that installed packages can only be updated by APKs with an identical signing certificate. If upgrading from an older debug build, Android may display *"App not installed as package conflicts with an existing package"*. A one-time uninstall of the old build is required before installing v1.0.7+. All future releases share this permanent certificate and will update seamlessly in-place.

---

## Android Permissions & Threat Model

Crunch follows the **Principle of Least Privilege**. It requests only the minimal set of Android permissions required to stream, process, and store media files:

| Permission | Scope | Security Justification |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Normal | Required to fetch media manifests and download video/audio streams from user-supplied URLs. |
| `android.permission.FOREGROUND_SERVICE` | Normal | Keeps long-running download and muxing tasks active when the user switches apps or turns off the screen. |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ | Categorizes the background download work strictly as `dataSync` in compliance with Google Play / AOSP policies. |
| `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Displays real-time download progress, pause/cancel controls, and completion notifications. |
| `android.permission.WRITE_EXTERNAL_STORAGE` | Legacy (maxSdk=28) | Required only on Android 9 and older for legacy external storage writing. Not requested or used on Android 10+. |

### Hardened Against Dropper Heuristics

- **No In-App Package Installer:**  
  Starting in v1.0.6, `android.permission.REQUEST_INSTALL_PACKAGES` was permanently removed from Crunch. Sideloaded apps that request package installation rights are frequently categorized by Google Play Protect heuristics as generic "droppers" or potentially harmful apps.  
  Crunch completely delegates all app update downloads to the user's default browser and GitHub's verified releases infrastructure.
- **No Broad Storage Access:**  
  Crunch never requests `MANAGE_EXTERNAL_STORAGE` or full storage bypass permissions. All downloaded media files are published to public storage via Android's standardized `MediaStore.Downloads` collection (`Downloads/Crunch`).

---

## User Data Privacy & Persistence

- **Zero Analytics / Zero Telemetry:**  
  Crunch contains no third-party tracking libraries, analytics frameworks, advertising SDKs, or crash telemetry reporters.
- **Local Media Resilience (`DownloadHistorySync`):**  
  Downloaded media is stored in the standard public directory (`Downloads/Crunch`), which is never deleted when the application is cleared, updated, or uninstalled. When installing or updating Crunch, an automated storage scanner (`DownloadHistorySync`) automatically checks `Downloads/Crunch` and MediaStore to re-index existing files into your library, preventing any loss of download history.

---

## Reporting a Vulnerability

We take the security and integrity of Crunch seriously. If you discover a security vulnerability, please report it responsibly:

1. **Do not open a public issue.**
2. Report the vulnerability privately via **[GitHub Security Advisories](https://github.com/cyberlog69/Crunch/security/advisories/new)** or by emailing the project maintainer directly.
3. Please include:
   - A detailed description of the vulnerability.
   - Steps to reproduce or a proof of concept (PoC).
   - Affected Android versions, architectures, or environments.
   - Any potential remediation suggestions.

### Response SLA
- **Initial Acknowledgment:** Within 48 hours.
- **Assessment & Triage:** Within 5 business days.
- **Remediation Release:** High and critical severity issues will receive an emergency patch release within 7 days of confirmation.
