# Privacy

Audio Reading plays audiobooks that are already on your phone. It sends nothing anywhere,
because there is no code in it that could.

This file describes what is true of the code in this repository *today*. When that changes,
this file changes in the same commit as the code that changed it.

## The library

You choose a folder. The app reads it through Android's storage access framework, which
means it is given a handle to that one folder and nothing else — not your photos, not your
downloads, not the rest of the card. There is no "read all files" permission here, and no
way for the app to look outside what you handed it.

What it keeps from that folder is a list: book names, author names, chapter names, file
sizes, durations, and where you are in each one. That list lives in a database that is
app-private, which means other apps cannot read it and uninstalling takes it away.

Nothing is copied. The audio stays where you put it.

## What leaves the phone

Nothing. The app declares no internet permission — see `app/src/main/AndroidManifest.xml`.
Not for cover art, not for metadata, not for a catalogue lookup, not for crash reports.
A book you are reading is not something anyone else needs to know about.

## What is stored

| Where | What |
|---|---|
| App database | Your books, chapters, bookmarks, and your place in each. App-private. |
| Shared preferences | Your settings, and the folder you granted. App-private. |

App-private means other apps cannot read it and it goes away when you uninstall.

## The permissions it does declare

Four, and none of them reach your data:

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE` | Playback continues with the screen off. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | The kind of foreground service it is, which Android requires it to name. |
| `POST_NOTIFICATIONS` | The player notification — the thing with the pause button in it. You may refuse it and the app still plays. |
| `WAKE_LOCK` | The processor stays awake while audio is playing, and not otherwise. |

No microphone, no location, no contacts, no storage-wide read, and **no internet**.
