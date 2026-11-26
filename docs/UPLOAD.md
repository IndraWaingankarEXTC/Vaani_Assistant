# Putting it on GitHub so the history shows the work

You asked for a repository that *shows the progress* — the first version, then a change, then
another — rather than one dump of every file. Here is the honest situation, then two ways to do it.

## What is already in the zip

`hands-off-assistant-v3.2.0.zip` is not a folder of files. It is a **git repository** with eleven
commits and eleven tags:

```
v1.0.0  v2.0.0  v2.1.0  v2.2.0  v2.3.0  v2.4.0  v2.5.0  v3.0.0  v3.1.0  v3.1.1  v3.2.0
```

Each one is a real milestone with a message explaining *why* it changed. Pushing it puts that whole
story on GitHub in one go — which is exactly the "show how we evolved" you want, and it is better
than anything that can be rebuilt by hand.

**What a web upload does instead:** every "Upload files" action creates **one** commit. Upload all
95 files in six batches and you get six flat commits, no tags, no per-version record, and every
file dated today. You lose the history that already exists.

That is the trade. Read the two paths below and pick.

| | Path A — push the zip | Path B — upload by hand |
|---|---|---|
| History | 11 commits, 11 tags | 6 commits, no tags |
| Time | ~5 minutes | 1–2 hours, on a phone |
| Needs | Termux (free app) | nothing but the browser |
| Folders | preserved | must be created one at a time |
| Your own commits after | easy, via the browser | same |

---

# Path A — push the zip, then add your own commits (recommended)

**Step 1.** Follow `docs/PHONE.md` steps 1–6: create the repo (choose **Public**), make a token,
Termux, unzip, push. That is the "initial" — eleven commits and every tag appear at once.

**Step 2.** Push the tags too, so every version gets a release with an APK:

```bash
cd ~/storage/downloads/hands-off-assistant
git push --tags
```

**Step 3.** From then on, make your changes **through the browser**, one at a time. Each edit is a
commit under your name, dated the day you make it. See "Your own commits" below.

This gives you the real history *and* your own contributions on top.

---

# Path B — upload by hand from the browser, in stages

Only if you cannot use Termux. You will get six commits instead of eleven, and no tags.

The awkward part: **the browser uploads into the folder you are currently looking at**, so each
folder must exist first. Create a folder by creating one file inside it.

### Batch 1 — the repository root

Repository → **Add file** → **Create new file**. In the name box type `README.md`, put one line in
it, commit with the message `Start the project`. That creates the root.

Then **Add file** → **Upload files**, and add these from the zip's root:
`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `.gitignore`, `LICENSE`, `CHANGELOG.md`.
Commit: `Build configuration`.

### Batch 2 — the app shell

**Create new file**, name it `app/build.gradle.kts`, paste the file's contents, commit.
That creates `app/`. Then **Create new file** again with
`app/src/main/AndroidManifest.xml` — the path creates every folder in between.

Then upload into `app/src/main/res/...` the four resource files (two layouts, two values, one xml
config, two drawables) — you will need to create each folder the same way first.

Commit: `App shell: manifest, theme, layouts`.

### Batch 3 — the brain

Upload into `app/src/main/java/com/vaani/assistant/core/` and `.../nlu/` (create the folders with a
placeholder file first, then upload). Commit: `Rules, languages and time parsing`.

### Batch 4 — the hands

Upload `.../ui/` and `.../exec/`. Commit: `Accessibility, planner, executor, safety guard`.

### Batch 5 — the senses

Upload `.../voice/`, `.../llm/`, `.../lens/`. Commit: `Wake word, dictation, camera lens`.

### Batch 6 — tests, CI and docs

Upload `app/src/test/...`, `.github/workflows/build.yml`, and everything in `docs/`.
Commit: `Tests, CI and documentation`.

### Then tag it

Releases → Draft a new release → tag `v3.2.0` → Publish. The build runs once, on the complete tree.

> The workflow now checks whether the project is complete before building. Partial uploads show a
> green tick with the message *"This commit is a partial upload. Nothing to build yet."* — so your
> history does not fill up with red Xs while you are still uploading.

---

# Your own commits — the part that actually builds a profile

Each of these is **one file, edited in the browser**, and each becomes a commit with your name on
it, dated that day. Do them on different days and the graph fills in.

1. **Add a command you actually used.** Edit `docs/COMMANDS.md`, add a line to the table.
   *Commit: "Add a command I use daily"*
2. **Change the wake phrase to your own word.** Edit
   `app/src/main/java/com/vaani/assistant/core/Prefs.kt`, find `"vaani"` in `wakePhrase`, change it.
   *Commit: "My own wake word"*
3. **Add a recipe for your own flow.** Edit `app/src/main/assets/recipes.sample.json`, add an entry
   for an app you use. *Commit: "Recipe: my morning routine"*
4. **Screenshots.** Upload two photos into `docs/` (the lens with callouts; the order summary card)
   and add them to `README.md` under the title. *Commit: "Screenshots"* — this is the single
   biggest thing for making people take it seriously.
5. **Write what you learned.** New file `docs/NOTES.md` — what broke, what surprised you, what you
   would do next. *Commit: "Notes from using it"*
6. **Fix something small.** Any typo, any wording. *Commit: "Fix typo in docs"*
7. **Bump the version.** Edit `app/build.gradle.kts`, raise `versionName`. *Commit: "v3.2.1"*
8. **Add a test.** New file in `app/src/test/java/com/vaani/assistant/`. *Commit: "Test for …"*

After each one, GitHub re-runs the workflow. If the build is green, the change was safe.

---

# How the contribution graph actually works

Worth knowing before you plan around it:

- **It counts days, not commits.** Eleven commits pushed today is **one** square, a darker green.
- **Only commits to the default branch** count (`main`), and they are dated by when they were
  authored.
- **A graph that shows a month of work needs a month of commits.** There is no honest shortcut —
  backdating or padding commits is visible to anyone who opens the log, and it reads worse than a
  short honest history.

So: push the zip today for the real history, then add one small change every few days. In a month
that is a graph that means something, and a README that has grown with real notes and screenshots.

---

# If you get stuck

| Problem | Fix |
|---|---|
| `Repository not found` on push | The token needs the `repo` tick; the URL must match your username exactly |
| Password prompt rejects everything | Use the **token**, not your account password |
| Browser upload puts files in the wrong folder | Navigate into the folder first; create it with "Create new file" and a full path |
| Build never runs | Settings → Actions → General → make sure workflows are enabled |
| Build is red | Tap the run → failed job → copy the last 20 lines and send them |
