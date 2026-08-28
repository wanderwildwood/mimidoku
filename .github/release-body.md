An audiobook player for the Mudita Kompakt, written from scratch.

Not a fork. It starts empty and takes on only what it needs —
[Media3](https://developer.android.com/media/media3) for playback, which is the part nobody
should write twice.

## What it needs

The folder of audiobooks you point it at, and nothing wider: there is no read-all-files
permission here. No microphone, no location, no contacts, and no internet — the app does not
declare the permission, so nothing it holds has a way off the phone. See [PRIVACY.md](PRIVACY.md).

## The download

`mimidoku.apk` and `mimidoku-<version>.apk` are the same file. The unversioned one is there so
a link to it keeps working after the next release. Verify either against the `.sha256` beside it
if you like.

Free software under the GPLv3.
