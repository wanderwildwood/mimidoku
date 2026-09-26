# Privacy

Audio Reading plays audiobooks that are on your phone. It talks to nothing on the internet
and to no one at all, unless you give it the address of your own audiobook server — and then
it talks only to that.

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

Nothing, until you add a server. Then: requests to that server, and nowhere else. Not for
cover art, not for metadata, not for a catalogue lookup, not for crash reports. There is no
analytics code here and no third party to send anything to. A book you are reading is not
something anyone else needs to know about.

## Your own server

The app can fetch books from an Audiobookshelf server you run. It is off until you fill it
in, and what it does is narrow on purpose:

- It asks that server what books it has, and downloads the ones you ask it to. Nothing else.
- **It never tells the server where you are in a book.** Your place is yours and stays on the
  phone. The server's own record of your listening is left exactly as it was — if you also use
  the web player, the two will not agree, and that is deliberate rather than a missing feature.
- It authenticates with an API key you make on the server and paste in. Not your password. You
  can revoke that key on the server at any time and the app simply stops working, which is the
  point of a key.
- The key is kept in the app's private settings, which other apps cannot read. It is kept in
  the clear, the way a key has to be to be usable. Anyone who can already read your app's
  private data can read it.
- **A server on your home network is plain http, and this app permits that** — otherwise
  Android would refuse to talk to it at all. Over plain http, the key and the audio are
  readable by anyone else on that network. On a home wifi that is a fair trade. If your server
  is reachable from outside your house, give it an https address and use that one.

Books you download are kept in the app's own folder, which means uninstalling the app takes
them with it, and no other app can read them.

## What is stored

| Where | What |
|---|---|
| App database | Your books, chapters, bookmarks, and your place in each. App-private. |
| Shared preferences | Your settings, the folder you granted, and your server's address and key. App-private. |
| App files folder | Books you downloaded from your server. Goes away when the app does. |

App-private means other apps cannot read it and it goes away when you uninstall.

## The permissions it does declare

Five, and none of them reach your data:

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE` | Playback continues with the screen off. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | The kind of foreground service it is, which Android requires it to name. |
| `POST_NOTIFICATIONS` | The player notification — the thing with the pause button in it. You may refuse it and the app still plays. |
| `WAKE_LOCK` | The processor stays awake while audio is playing, and not otherwise. |
| `INTERNET` | Talking to your own audiobook server, if you have given it one. Nothing else in this app uses the network. |

No microphone, no location, no contacts, and no storage-wide read.

## One optional service

**Lock-screen controls** are an accessibility service, off until you turn them on in Android's
Accessibility settings (Settings → Lock-screen controls goes there). The Kompakt's own lock-screen
music widget is wired to Mudita's player and shows nothing else, and only an accessibility service
may draw above the lock screen, so that is what this has to be. It draws Audio Reading's controls
at the foot of the lock screen while a book plays, listens only to the system lock screen, and
reads one thing there: whether the PIN field is showing, so the controls can make way for it. It
reads no other app, and nothing it sees leaves the phone.
