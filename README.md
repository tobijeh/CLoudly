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
Releases are signed with one fixed key so updates install over the previous version. Only one repository
secret is needed: `CLOUDLY_KEYSTORE_BASE64`, holding the keystore file as base64 text. The keystore password
(`cloudly-release`) and alias (`cloudly`) are fixed in `release.yml`; the keystore file itself is the secret.
Keep a backup copy of the base64 text. If it's lost, existing installs can't be updated.

To make a new key (only if starting over):
```sh
keytool -genkeypair -keystore cloudly-release.jks -storetype PKCS12 -storepass cloudly-release \
  -keypass cloudly-release -alias cloudly -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Cloudly"
base64 -w0 cloudly-release.jks   # paste the output into the CLOUDLY_KEYSTORE_BASE64 secret
```

## Development
```sh
./gradlew testDebugUnitTest   # regression tests
./gradlew assembleDebug       # debug APK (installs alongside release as io.github.tobijeh.cloudly.debug)
```
Fonts: Fraunces, Frank Ruhl Libre and Noto Serif TC, under the SIL Open Font License (see `licenses/`).
