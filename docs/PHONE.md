# Doing all of this from your phone

No laptop. Two parts: getting the code onto GitHub, then getting the APK onto the phone and using
it.

---

# Part 1 — put the code on GitHub

You need: the `hands-off-assistant-v3.1.1.zip` file (already downloaded on your phone), and the
**Termux** app.

### Step 1 — create the empty repository

1. Open Chrome, go to **github.com/new**.
2. Repository name: `hands-off-assistant` (no spaces — GitHub does not allow them).
3. Choose **Public** — that is what lets anyone else download the APK without a GitHub login, and
   it makes the repository readable by anyone who finds it. (Private works too; you just cannot
   share the download link, and people would need an account.)
4. **Do not** tick "Add a README", ".gitignore" or "licence". It must be completely empty.
5. Tap **Create repository**. Leave this page open — you need the URL on it.

> Going public with a repository that was private: Settings → General → scroll to **Danger zone** →
> **Change visibility** → Public.

### Step 2 — make a token (this is your password)

GitHub will not accept your account password over git any more.

1. Go to **github.com/settings/tokens** → **Generate new token (classic)**.
2. Note: `vaani`. Expiry: 90 days is fine.
3. Tick the **`repo`** checkbox. Nothing else is needed.
4. Tap **Generate token** and **copy it now** — it is shown once. Paste it into a note on your
   phone; you will use it twice.

### Step 3 — install Termux

Install **Termux** from **F-Droid** (f-droid.org) or the Play Store. F-Droid's version is better
maintained; either works.

### Step 4 — get the zip where Termux can see it

The zip you downloaded from this chat is in your phone's **Downloads** folder. If you are not sure,
open **Files** (or your file manager) → Downloads, and check that
`hands-off-assistant-v3.1.1.zip` is there.

### Step 5 — push the code

Open Termux and paste this block **one line at a time** (long-press in Termux → Paste). Change the
URL on line 5 if your username is different.

```bash
pkg update -y && pkg install -y git unzip
termux-setup-storage
cd ~/storage/downloads
unzip -o hands-off-assistant-v3.1.1.zip
cd hands-off-assistant
git remote add origin https://github.com/YOUR-USERNAME/hands-off-assistant.git
git push -u origin main
```

When it asks:

```
Username for 'https://github.com':   your GitHub username
Password for 'https://...':          paste the TOKEN from step 2
```

The password prompt shows **nothing** as you paste — no dots, no stars. That is normal. Paste and
press Enter.

You should end with `branch 'main' set up to track 'origin/main'`. The code — with its whole
history, v1.0.0 through v3.1.1 — is now on GitHub.

### Step 6 — tag it, which builds the APK

Two ways. Pick one.

**From the phone browser (easiest):**

1. Open your repo → **Releases** (right-hand side) → **Draft a new release**.
2. Tap **Choose a tag** → type `v3.1.1` → **Create new tag on publish**.
3. Title: anything. Tap **Publish release**.

**From Termux:**

```bash
cd ~/storage/downloads/hands-off-assistant
git tag v3.1.1
git push --tags
```

**To publish every past version at once** — so the release list shows the whole evolution, one APK
per version — push all the tags instead:

```bash
git push --tags
```

Each tag from `v2.0.0` onward has the build workflow in it, so each one produces its own release
with its own APK. That takes a while (a build per tag) but it makes the history visible to anyone
browsing. `v1.0.0` predates the workflow, so it has source but no APK.

Either way this starts the build. Nothing else to do.

---

# Part 2 — get the APK onto the phone

### Step 7 — wait for the build

In your repo, open the **Actions** tab. A run called *Build APK* appears and takes about **5
minutes** the first time (it downloads Android's build tools), then about 2 minutes after that.

A green tick means it worked.

### Step 8 — download the APK

1. Open **github.com/YOUR-USERNAME/hands-off-assistant/releases/latest**
2. Under **Assets**, tap **`vaani-v3.1.1-debug.apk`**.

> **Download the file with `debug` in the name.** The one ending `-unsigned.apk` cannot be
> installed — Android refuses an APK with no signature. The debug one is signed and installs fine.

### Step 9 — install it

1. Chrome will warn "this type of file can harm your device" — tap **Download anyway**. That
   warning appears for every APK; it is not a judgement about this one.
2. Open the download (Chrome → Downloads → the `.apk`), or tap the notification.
3. Android says *"For your security, your phone is not allowed to install unknown apps from this
   source"* → tap **Settings** → turn on **Allow from this source** → back → **Install**.
4. Play Protect may say "Unsafe app blocked" or "app was not scanned". Tap **More details** →
   **Install anyway**. It is a personal build that Play has never seen.

---

# Part 3 — first run

Open **Vaani** from your app drawer. Then, in this order:

### Step 10 — permissions

Tap **"Grant microphone, contacts, phone, SMS"** and accept all of them. If you skip this, the app
can hear nothing.

### Step 11 — screen control (this is the important one)

1. Tap **Screen control**.
2. Android opens Accessibility settings → **Vaani screen control** → turn it **on** → **Allow**.

This is what lets Vaani tap, type and scroll inside other apps. Without it, only app launching,
music, alarms and navigation work.

### Step 12 — media control

Tap **Media control access** → Android opens Notification access → **Vaani media access** → on →
Allow. Without it, "pause" and "next song" do nothing.

### Step 13 — the floating mic (optional but useful)

Tap **Floating mic permission** → allow **Display over other apps**. This gives you a draggable mic
you can tap from anywhere, and it is also what lets "open lens" launch the camera from the
background.

### Step 14 — switch it on

- **Keep Vaani running** → on
- **Hands-free** → on
- **Ask me before an order or payment** → leave on

### Step 15 — try it

Type a command in the box at the bottom and tap **Run** first — it is the quickest way to see the
log work without speaking:

```
open spotify
```

Then hold the phone up and say **"Vaani, open lens"** — the camera should open full screen. Point it
at a device, or press **Select area**, drag the four corners around its label, and tap **Read this**.

Then try talking: **"Vaani, amma ko call karo"**, **"Vaani, play lofi on Spotify"**.

---

# The one annoying thing about updating

Every build from GitHub Actions is signed with a **different throwaway key**, and Android will not
install an update signed by a different key. So each time you download a new build:

> **Uninstall the old Vaani first, then install the new APK.**

To stop that, create your own signing key once and add four secrets to the repo
(Settings → Secrets and variables → Actions) — the exact steps are in `docs/DEPLOY.md`, section 4.
After that, updates install straight over the top.

---

# If something goes wrong

| What you see | What it means |
|---|---|
| `Repository not found` on push | Wrong URL, or the token was made without the `repo` tick. Redo step 2. |
| `Authentication failed` | You typed your account password. It must be the token. |
| Actions run has a red ✗ | Tap the run → tap the failed job → read the last 20 lines. Send them to me. |
| "App not installed" | An older Vaani with a different signature is still there. Uninstall, then install. |
| Accessibility turns itself off after a while | Battery optimisation is killing it. Settings → Apps → Vaani → Battery → **Unrestricted**. |
| "Vaani is ready" notification disappears | The service was stopped. Open the app and toggle **Keep Vaani running** off and on. |
| Lens opens but shows no boxes | Nothing is detected yet — point at a plain background, or move closer. |
| Voice commands do nothing | Microphone permission, or the speech service has no offline model on your phone. The log in the app shows what was heard. |
