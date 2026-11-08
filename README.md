# Hush

Blocks ads, trackers and sites you choose across your whole Android phone, and closes distracting apps during quiet hours. Everything runs on the device: no account, no analytics, no servers of its own.

## Features

- **Site blocking.** A local VPN carries only DNS lookups. Names on your lists get an empty answer, so ads and trackers never load in any app or browser. All other traffic goes straight out as usual.
- **Encrypted DNS.** Allowed lookups go to Google, Cloudflare or Quad9 over TLS (Google by default), never in the clear.
- **Filter lists.** Pick from well-known lists; they download over HTTPS on first launch and refresh weekly. Add your own sites, allow-rules, patterns and social-media presets.
- **Night lock.** During quiet hours, chosen apps are closed and night-only sites are blocked. Settings can only get stricter while it runs.
- **Stats.** Blocked and total lookups today, this week and all time. Counts only; no domain names are ever stored.
- **Widget and quick settings tile.** See today's blocked count and turn blocking on or off from the home screen or the status-bar pull-down.
- **Backup.** Save and load your settings as a plain text file.

## Install

Download the signed APK from the [Releases page](https://github.com/altf4-games/Hush/releases) and open it on your phone. Android 8.0 or newer. On first launch, the setup screens walk through what to switch on.

## Build

Needs JDK 17, the Android SDK and Gradle 8.9.

```bash
gradle testDebugUnitTest   # unit tests
gradle assembleRelease     # app/build/outputs/apk/release
```

To sign a release build, create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=C:/path/to/hush-release.jks
storePassword=...
keyAlias=hush
keyPassword=...
```

Without it, the release APK is built unsigned.

## Limits

DNS blocking cannot remove ads served from the same address as the content, and apps with their own DNS skip Hush. Set Android's Private DNS to Off or Automatic, and turn off Secure DNS in your browser.
