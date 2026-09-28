# Qantai for Android (APK project)

Qantai hub + Odysseus execution layer, in the cyan console theme, packaged as a
native Android app. The whole app runs on the phone (WebView around
`app/src/main/assets/index.html`). Voice orders use the phone's own speech
recognizer; spoken replies use the phone's text-to-speech.

## Get the APK (no Android Studio needed) — GitHub Actions

1. Create a free account at github.com and make a **new repository** (any name).
2. Upload **everything in this folder** to it (including the hidden `.github` folder).
   Easiest: on your computer, unzip this project, then in the repo click
   *Add file → Upload files* and drag all the contents in (make sure `.github/workflows/build-apk.yml` is included).
3. Open the repo's **Actions** tab. The "Build Qantai APK" workflow starts by itself
   (or click it → *Run workflow*). It takes about 3–6 minutes.
4. When it turns green, open the run and download **Qantai-APK** from *Artifacts*
   (a zip containing `app-debug.apk`).
5. Copy `app-debug.apk` to your phone and tap it. If Android asks, allow
   "Install unknown apps" for your file manager/browser.

## Or build with Android Studio

Open this folder in Android Studio → *Build → Build Bundle(s)/APK(s) → Build APK(s)*.
The file appears in `app/build/outputs/apk/debug/`.

## Notes

- This is a *debug-signed* APK: fine for installing on your own phone, not for Google Play.
- Order classification, routing, tickets and quantai run fully on-device.
- Voice-to-text uses Android's speech service, which on many phones needs a
  connection to transcribe. Typing always works offline.
- Order history is stored inside the app (cleared if you uninstall it).
