# UmamiT

**Besserer Tee. Tiefere Momente.**

UmamiT is a personal Android app for brewing tea well, especially umami-rich
Japanese green teas such as gyokuro, kabusecha, sencha and matcha. It brings
together:

- **Steep timer:** handles several infusions in a row, with a pre-alert tick
  before the end. It shows as a Live Update on the lock screen and rings
  reliably on a locked Samsung phone.
- **Catalog:** every green tea from
  [Tee-Kontor Kiel](https://www.tee-kontor-kiel.de/), with descriptions,
  photos and brewing guides turned into timer recipes. It can be refreshed
  from inside the app.
- **Collection:** the teas you own, with stock, cost per cup and your own
  recipes.
- **Journal and statistics:** sessions are logged automatically, and you can
  add notes, ratings and a taste profile whenever you like.

The app is German-language and for personal use only. It isn't published in
any store.

## Status

🛠️ **Milestone M1 (timer first) in progress.** Specs:
[`docs/specs/`](docs/specs/README.md), plan:
[`docs/plans/M1-timer.md`](docs/plans/M1-timer.md).

## Release signing

Release APKs are signed with UmamiT's own key (alias `umamit`). The public
certificate SHA-256 fingerprint is:

```
A5:00:E2:C6:7C:73:C2:1F:4F:FA:39:61:ED:DE:17:34:D3:75:2D:FC:F2:E4:23:36:82:35:95:66:E2:D8:2F:67
```

The same value (lowercase, without colons) is in
[`signing/release-cert-sha256.txt`](signing/release-cert-sha256.txt); the
release workflow refuses to publish an APK signed with any other key.

## Planned tech stack

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Hilt · OkHttp ·
Jsoup · min SDK 35. Builds are signed APKs published on GitHub Releases.
