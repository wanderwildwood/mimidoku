# Books from a Nextcloud folder: what is done, what is left

Branch `nextcloud-books` (off master = 1.3.53). Not merged, not released, no version bump.

## Done (first slice)

- Settings, **Nextcloud folder**: the address, login name, app password and folder, all typed
  (the same sign-in as Recipes, Memo, Shelf and Pictures: no browser, nothing to register).
  The app password is sealed in the Keystore like the Audiobookshelf key (`Preferences.secret`).
  Nothing is saved until the folder has answered.
- The folder is read over WebDAV (`server/Nextcloud.kt`, OkHttp, PROPFIND a level at a time, as
  Nextcloud refuses Depth infinity). Each folder of audio files is a book named after the folder
  and filed under the folder above it (Audiobooks/Author/Title); a loose file is a book of one.
  Chapters in natural order ("Part 2" before "Part 10").
- The books go into the same tables as card and Audiobookshelf books (`server/NextcloudSync.kt`,
  source `NC`), never deleted by a sync, downloaded whole before they play by the same downloader
  as Audiobookshelf books (retries, all-or-nothing, Stop, the bottom line, card or phone), now
  behind a small `BookFiles` interface. Lengths are read from the files as each arrives, since
  Nextcloud does not know them.
- Pull to refresh reads the Nextcloud folder too. Forget removes its books and downloads.
- Checked 3 Nov 2026 against Nextcloud 31.0.14 in a throwaway container, a made-up account and
  generated tone files: listing, wrong password, missing folder, the files fetched byte for byte,
  and on an API 31 emulator: connect, two books on the shelf, download, 1:13 measured, plays.

## Left

1. The empty library offers "Connect a server" (Audiobookshelf) only; it could offer the
   Nextcloud folder too. A wording question still to settle.
2. A Nextcloud book shows no length until it is downloaded (Audiobookshelf books show theirs).
   Reading lengths remotely would mean opening every file over the network; not done.
3. Tags are read only from the downloaded files, so a Nextcloud book's shelf title is its
   folder name until then (the downloaded tags win after, as for card books).
4. The folder walk stops at 5 levels and 50,000 entries; untested on a large real library.
5. No etag shortcut: every sync walks the whole folder. Fine for hundreds of books; a big
   library would want Nextcloud's folder etags (as Recipes does) to skip unchanged folders.
6. Not tried on the Kompakt itself; no screenshots made.
