# Where this is, as of 2026-08-27 (evening)

Paused mid-audit. Everything below is committed; the working tree is clean and there are still no
git remotes (this repository stays local).

## What it is

`mimidoku` — an audiobook player written from scratch, matched screen-for-screen against
eInk Audiobook Player by **measuring the running app**, never by reading its source. Reference
screenshots are archived in `/opt/projects/mimidoku-reference/`. The Kompakt is 213 dpi, so
pixels ÷ 1.331 = dp.

Installed on MK20250400796 as a debug build signed with the **old debug key** - see the
warning under "Pick up here" before you install anything over it.

## Done

Seven screens, all matched to the pixel: library, one shelf's books, playback, chapter list,
bookmarks, preferences, folders, search — plus stepper / choice / confirm / About dialogs.

Everything in the playback toolbar works: sleep timer (counts down under the moon, marks the place
before pausing, a shake restarts it), volume boost, playback speed, skip silence, bookmarks, screen
lock. Auto-rewind on resume. Position and progress remembered. The app restores what was playing
after a restart and offers back the last-read book on a cold start.

## What the audit found and fixed (2026-08-26/27)

The screen-by-screen audit was worth more than everything before it. In order of seriousness:

1. **Books were missing from the library.** The scanner judged the whole tree as one shape. A real
   library is mixed — Ursula K. Le Guin has 90 book folders *and* 67 loose files — and whichever
   half it decided against was silently lost. Now every folder is asked what it is and may be both.
   The library went from 70 books to 253.
2. **No chapter marks.** A twelve-hour novel delivered as one file had one chapter and no way
   through it. `library/ChapterMarks.kt` now parses ID3 CHAP frames; marks and chapter files are
   unified as "parts" so neither kind of book needs its own transport or list. Verified against the
   reference to the second (Redwall: 35:56, 1:11:35, 1:48:03).
3. **Names came from folders, not tags.** Books are titled from the album tag, or from the title tag
   when the book is a single file (which is where a one-file book puts its name and it has no album).
   Authors come from the artist tag. Sorting is `COLLATE NOCASE`, or "alan Watts" sorts last.
4. **Whole screens missing:** Audiobook folders (multiple folders, Scan now, Add, remove) and the
   chapter list sheet.
5. **Drawing:** the transport icons were the outlined Material cut, which has hollow arrowheads —
   David caught this by eye after measurements said they matched. Use `/fill1/` for those five and
   the closed padlock. The locked screen dims its toolbar to `0xFF9F9F9F` and its body to
   `0xFF666666`.
6. Progress percentage on started cards; search shows shelves until a query is typed, then cards;
   both clocks padded to the same width; the chapter line hidden when a book has only one part.
7. **A scaling cliff:** `merge()` listed every book URI as a bound variable to decide what to keep.
   Past ~1000 books SQLite would have refused and the scan would have failed outright. Now a scan
   stamps what it found and sweeps what it did not.

## Pick up here

- **Finish the audit.** It was paused partway through a systematic visual sweep: capture every
  screen in *matched states* in both apps and compare zoomed crops side by side, not just numbers.
  Done so far: player toolbar (all three states), settings, transport, folders, bookmarks, chapter
  sheet, dialogs. Not yet swept: library and shelf screens after the tag change, search, the
  now-playing strip.
- **The sleep timer reaching zero is still untested** — it ticks correctly and the zero path is four
  lines, but confirming it wants a real ten-minute run.
- **Release build: done, except for minify.** A 4096-bit RSA keystore lives in `signing/`
  (gitignored, alias `mimidoku`, SHA-256 `DD:93:2D:A2:...:3F:0E`, valid to 2054) and signs
  *every* build type. `assembleRelease` produces a 27MB APK verified as signed by that key.
  Minify is deliberately still off: R8 cannot be signed off without a device to run the result
  on. Flip the one boolean in `app/build.gradle.kts` when there is a Kompakt to test on.
- ⚠ **The next install needs an uninstall first.** What is on the phone was signed with the
  throwaway debug key; everything built from now on carries the real one, and Android will
  refuse the update as `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstalling loses the scanned
  library (one rescan) *and* every saved position and bookmark (not recoverable) - so pull
  the Room database off the device first if any of that is worth keeping.
- **The version is still `0.1.0`, on purpose.** Stamping 1.0.0 belongs to the day the audit
  finishes and the thing is verified on the panel, not to the day it got a keystore.
- ⚠ **`~/backup-signing-keys.sh` now lists mimidoku but has not been re-run.** Until it is,
  the only copy of this key is on the Dell, which is in no automated backup. It prompts for
  a passphrase, so it has to be run by hand.
- Known deliberate differences from the reference: the playback toolbar's seven icons are evenly
  spaced (the reference's spacing is irregular); bookmark times follow the device's 24-hour setting
  where the reference hardcodes 12-hour; removing a bookmark asks first; About credits Lato and
  Material Symbols instead of linking source. On the preferences screen, the first row's icon is
  centred on the row where the reference aligns it to the title.
- **m4b chapter atoms are not parsed — and do not need to be.** The reference does not read them
  either: Dune shows no chapter line in both apps.
- The licence for mimidoku is still deliberately undecided.

## The repository itself

`.gitignore` said `/build`, anchored to the root, so it never matched `app/build`. Every commit
from "Play a file" onward carried 841 build files including a fresh 36MB APK, and a 592MB heap
dump from an out-of-memory build sat tracked at the root: 1.7MB of source in a 499MB repository.
The rule is fixed and the artifacts are untracked, so it stops here.

**The weight is still in the history.** Stripping it needs one rewrite, safe because this
repository is local-only with no remotes and no clones:

```
cd /opt/projects/mimidoku
git tag pre-rewrite-backup HEAD
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch --force --index-filter \
  'git rm -r --cached --ignore-unmatch app/build java_pid90454.hprof' -- --all
# check the 23 commits and their messages survived, then:
git tag -d pre-rewrite-backup && rm -rf .git/refs/original
git reflog expire --expire=now --all && git gc --prune=now --aggressive
```

Only the hashes change; the messages are preserved. Expect `.git` to fall from 499MB to a
few MB.
