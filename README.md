# Cloudly

A live word cloud for the TV behind podcast hosts. Hosts type words on the phone, and each word grows as
it's mentioned. Everything runs on the phone: no cloud services, no accounts, no API keys.

## Install
Use [Obtainium](https://github.com/ImranR98/Obtainium) and add this repo's URL. Every push to `main`
publishes a signed APK to GitHub Releases, so updates install over the previous version.

## Using it
- **TV through a wireless display or HDMI adapter (Presentation API):** the TV shows only the cloud, and the
  phone shows the controls.
- **Screen mirroring only (e.g. Chromecast "Cast screen"):** open ⋮ → *Full-screen cloud (mirroring)*. The phone
  shows the cloud in landscape. Swipe from the left edge, or tap the ☰ button, for the controls.
- Tap a word to lock it in the centre, and tap it again or tap *Auto* to release it. Pinch out on a word in the
  preview to enlarge it on the TV. Long-press a word to edit, hide, reset or delete it.

## Release signing (one-time setup)
Create a keystore once and keep it safe. If it's lost, existing installs can't be updated.

```sh
keytool -genkeypair -v -keystore cloudly-release.jks -alias cloudly \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 cloudly-release.jks > cloudly-release.jks.b64   # macOS: base64 -i cloudly-release.jks
```

Then add these four entries under GitHub → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `CLOUDLY_KEYSTORE_BASE64` | contents of `cloudly-release.jks.b64` |
| `CLOUDLY_KEYSTORE_PASSWORD` | the keystore password you chose |
| `CLOUDLY_KEY_ALIAS` | `cloudly` |
| `CLOUDLY_KEY_PASSWORD` | the key password (same as the keystore password if you pressed Enter) |

## Development
```sh
./gradlew testDebugUnitTest   # regression tests
./gradlew assembleDebug       # debug APK (installs alongside release as io.github.tobijeh.cloudly.debug)
```
Fonts: Fraunces, Frank Ruhl Libre and Noto Serif TC, under the SIL Open Font License (see `licenses/`).
