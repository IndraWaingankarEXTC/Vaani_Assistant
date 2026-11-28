# Deploying Vaani through GitHub

GitHub is not an app store. For an Android app it gives you three things, and all three are
useful:

1. **The source**, versioned and backed up.
2. **A build machine** — GitHub Actions compiles the APK for you on every push, so you never need
   Android Studio to produce an installable file.
3. **A download link** — a Release with the APK attached, which anyone can open on a phone and tap.

That is the whole deployment story. Below is the shortest path to all three.

---

## 1. Put the code in a repository

The archive is already laid out as a repository: `app/`, `build.gradle.kts`, `.github/`, `docs/`
at the top level. Do not nest it one folder deeper, or the build paths break.

**From your machine:**

```bash
cd vaani
git init -b main
git add .
git commit -m "Vaani v2.0.0: hands-free, code-mixed, on-device voice control"
git remote add origin https://github.com/<your-username>/vaani.git
git push -u origin main
```

Or with the GitHub CLI, which creates and pushes in one go:

```bash
gh repo create vaani --private --source=. --remote=origin --push
```

Use `--public` instead of `--private` if you want the release downloads to work without a GitHub
login. For a personal app, private is the sane default; see the caveat in section 5.

## 2. Let CI build it

`.github/workflows/build.yml` runs on every push to `main`, on pull requests, and on a tag. It
sets up JDK 17, the Android SDK and Gradle 8.7, then builds both variants.

Watch it under the **Actions** tab. The first run takes 4–6 minutes (Gradle downloads everything);
after that the cache makes it about 2.

When it finishes, open the run → **Artifacts** → `vaani-apk`. You get:

- `app-debug.apk` — installable immediately, signed with a throwaway debug key.
- `app-release.apk` — smaller (R8), **unsigned** until you set up signing in section 4.

## 3. Cut a release (this is your download link)

```bash
git tag v2.0.0
git push origin v2.0.0
```

The same workflow sees the `v*` tag, builds, and attaches both APKs to a new Release:

```
https://github.com/<your-username>/vaani/releases/latest
```

That URL is stable — it always points at the newest release. Open it on the phone, tap the APK,
allow "install unknown apps" for the browser, done. This is how you send the app to someone else.

## 4. Sign it once, so updates install over each other

Android refuses to update an app whose signing key changed. A CI-generated debug key is different
every run, so **auto-updates only work with a key you own**. Create one:

```bash
keytool -genkeypair -v -keystore vaani-release.jks -alias vaani \
        -keyalg RSA -keysize 4096 -validity 10000
```

Base64 it for the secret:

```bash
base64 -w0 vaani-release.jks > keystore.b64      # macOS: base64 -i vaani-release.jks -o keystore.b64
```

Add four **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | the store password you chose |
| `KEY_ALIAS` | `vaani` |
| `KEY_PASSWORD` | the key password |

Keep `vaani-release.jks` somewhere safe and backed up. Lose it and you can never update the app
in place again — only uninstall and reinstall.

The workflow writes `keystore.properties` from those secrets, and `app/build.gradle.kts` signs the
release build when it finds one. With no secrets, nothing breaks: you just get an unsigned release
APK and a debug one that installs.

Locally, the same thing without CI: put a `keystore.properties` at the repo root (it is
git-ignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, then
`gradle assembleRelease`.

## 5. Keeping it updated on the phone

Once releases exist, use **Obtainium** — an open-source Android app that watches a GitHub repo's
releases and offers the update when a new tag appears. Add `https://github.com/<you>/vaani`, and
it does the rest. This is the normal path for apps that are not on the Play Store.

Remember to bump `versionCode` in `app/build.gradle.kts` for every release; Android ignores an
APK whose `versionCode` is not higher.

**Private repo caveat:** release assets on a private repo need authentication, so a plain browser
download and Obtainium will both fail. Either make the repo public, or keep the APK in the Actions
artifact (which also needs a logged-in download).

## 6. If you want an app store later

- **Play Store**: build an `.aab` (`gradle bundleRelease`), create a Play Console account
  (one-time fee), and upload. Expect review to ask about the AccessibilityService — you will need
  to declare why it is used and show the disclosure screen. Vaani's use is legitimate and
  user-visible, but the paperwork is real.
- **F-Droid**: needs fully reproducible builds from source with no prebuilt binaries. The optional
  llama.cpp `.so` is the sticking point; the rule-only build would qualify.

## 7. What CI cannot check

The workflow proves the project compiles and packages. It cannot test what Vaani actually does:
accessibility tapping, the wake-word gate's sensitivity against a real microphone, or media
sessions. Those need a physical phone. The first CI run is also the first time this code has ever
been compiled — if it fails, the log will name the file and line.
