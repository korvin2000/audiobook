# Audiobooks for Android — combined and reconciled technical audit

**Project:** `com.fanok.audiobooks`, version 7.3 (95), commit `2f0a631`, `C:\work\audiobook`. **Stack:** Java 8 + desugaring, minSdk 23 / targetSdk 34 / compileSdk 36, Media3 1.9.0, legacy `MediaSessionCompat`/`MediaBrowserServiceCompat`, Room 2.8.4, RxJava 2, Moxy 1.7.0, Jsoup 1.21.2, OkHttp 5.3.2, Picasso + Glide, Gson. **Date of this reconciliation:** 2026-10-22.

**Sources merged:** `analyze.md` (referred to as **A**: 75 numbered findings plus build, lint, provider-probe, emulator and instrumentation evidence) and `analyze-opus.md` (referred to as **B**: about 180 numbered findings, broader source-parser and UI coverage, framework facts checked against the shipped binaries). Nothing in the source tree was changed by either review or by this pass.

## Verdict

**The application is not ready for a dependable release.** Both reviews reach this conclusion independently, and the reconciliation confirms every headline finding of each. Ordinary operations can delete unrelated files and other books (DATA-01, DATA-02), destroy the library on restore (DATA-05), leave the app unable to open its database after an upgrade (DATA-03), export corrupt audio (DL-01), show one finished chapter as a complete book (DL-06), return corrupted or endless search results (SRCH-02, SRCH-04), and leave the player stuck after any error (PLAY-05). TLS validation is disabled for the whole process and reusable credentials and private keys ship in the APK (SEC-01, SEC-02). The playback service crashes on teardown (PLAY-01), can never be fully stopped because its binding is never released (PLAY-02), and with default settings violates the foreground-service contract on slow or failing sources (PLAY-06).

The most urgent work is Phase 0 of §15: revoke secrets, restore TLS, make deletion path-based, fix restore and migration, and fix service teardown and foreground start. That removes the data-loss and exposure risks in days. The playback, caching and download work is best done as a migration to Media3 `MediaSessionService` with one player, stable cache keys and two caches, rather than by patching the current service. Cosmetic redesign should follow those repairs.

## How this document was produced

1. Both reviews were read in full and every finding was mapped to its counterpart (Appendix A). Nearly every finding in A has a counterpart in B; roughly half of B's findings have one in A. B's parser section (§8) and some thirty playback, security, persistence and UI/platform items exist only in B, while A's runtime reproductions, APK/manifest artifact checks and provider probes exist only in A.
2. Every disagreement, and every finding present in only one review, was re-read in the source at commit `2f0a631`: about 145 claims in total, partly directly and partly through five independent read-only verification passes over the parser, search, UI, playback/download/storage and persistence areas. Outcomes: roughly two thirds confirmed as written, one third confirmed with a correction to scope, mechanism or line numbers, and a small number of sub-claims withdrawn. §13 lists every correction. No headline finding of either review was refuted.
3. Where the reviews agree, the entry below cites both and keeps the stronger evidence (A's runtime reproduction where it exists). Where they differ, the entry has a **Reconciliation** note stating what was decided and why.
4. Severity was re-assessed on the merged evidence: **Critical** = realistic data loss, security exposure or an app-wide crash/lock-out; **High** = a core flow broken or unreliable for many users, or a release blocker; **Medium** = incorrect behaviour under specific conditions, notable cost, or a maintainability trap; **Low** = hygiene.
5. **Status** vocabulary: *runtime-reproduced* (A executed it on Android), *confirmed* (mechanism demonstrated in source by at least two independent readings), *plausible* (reasoned from code, not reproduced; the trigger is stated). *Verified* in an entry means this pass re-read the cited lines.
6. The user reports in `other_findings.md` (with `images/screenshot_01.png` and `log-report.txt`), added after the two reviews, were each traced in the code and matched against the findings; §16 records the result, and the causes that neither review had described were added as new findings (DL-10, PLAY-19, APP-03 and the search/voice-search items in §16).

**Path conventions:** `J/` = `app/src/main/java/com/fanok/audiobooks/`; `res/` = `app/src/main/res/`; other paths are relative to the repository root. Line numbers refer to commit `2f0a631`.

**Combined identifiers:** SEC (security), DATA (data loss and persistence), PLAY (playback service), SESS (media session, notification, Auto), NET (transport, caching, storage initialisation), DL (downloads and export), OFF (offline library and storage), SRC (provider parsers), SRCH (search and listings), UI (UI, lifecycle, accessibility), BUILD (build, release, platform), APP/ARCH (process and architecture). Each heading carries the original A and B identifiers.

## Headline findings

| Priority | Finding | ID | Status |
|---|---|---|---|
| Immediate | Deleting a book recursively erases every name-matching folder under the download and SAF roots; deleting chapter "10" deletes chapter "1" | DATA-01, DATA-02 | confirmed; predicates reproduced |
| Immediate | Restore accepts `{}` or unrelated JSON and clears favorites, history, positions and chapter lists before validating | DATA-05 | runtime-reproduced |
| Immediate | Migration 16→17 drops unique indexes; Room refuses to open the upgraded database and rolls back on every start | DATA-03, DATA-04 | runtime-reproduced |
| Immediate | TLS certificate and hostname validation disabled process-wide, including site login; bundled trust anchor is corrupt | SEC-01, SEC-06 | confirmed |
| Immediate | Telegram bot token, SOCKS proxy credentials and three OpenVPN private keys ship in the APK; global `Authenticator` leaks the proxy credentials | SEC-02, SEC-03 | confirmed |
| Before release | Exported chapter files are concatenated from sibling cache entries; failed exports are recorded as success; HLS export is a partial parser | DL-01, DL-03, DL-04 | runtime-reproduced (DL-01) |
| Before release | One finished chapter shows the book 100 % complete; row identity changes on refresh; positional selection can delete the neighbour | DL-06 | confirmed |
| Before release | Service teardown throws before releasing resources; the crash handler keeps a dead process alive | PLAY-01, APP-01 | runtime-reproduced |
| Before release | The service binding is never released (app-context bind, Activity unbind), so the player can never be fully stopped | PLAY-02 | confirmed (B only) |
| Before release | `startForegroundService` without a timely `startForeground` when autoplay is off (default): ANR / crash on slow or failing sources | PLAY-06 | confirmed control flow (B only) |
| Before release | Pause toggles, buffering cannot be paused, focus loss during buffering lets audio start during a call; any error is terminal | PLAY-04, PLAY-05 | runtime-reproduced |
| Before release | Rotating or leaving the book screen during buffering stops playback; Favorites/Downloads break after Back/rotation | PLAY-03, UI-01 | confirmed |
| Before release | Resume picks the chapter by title and keeps the old offset; speed `0` crashes the process via an exported receiver | PLAY-07, PLAY-12, SEC-04 | confirmed (B only) |
| Before release | Pagination rewrites query text and ids; new queries ignored while one runs; sentinel "⚠" books never end the list; audioboo/baza-knig/akniga paging broken | SRCH-01…SRCH-07 | confirmed; transformations reproduced |
| Before release | Errors returned as fake chapters disable the offline fallback; `substring(indexOf)` and throwing setters fail whole lists | SRC-01, SRC-03, SRC-05 | confirmed (B only) |
| Before release | Folder change releases a cache and manager still in use; Media3's static helper keeps the released manager; scheduler service undeclared | NET-05, NET-06, DL-02 | confirmed |
| Before release | Offline fallback can play another book's chapter; online refresh overwrites exported local paths | OFF-01, DATA-09 | confirmed |
| Before release | Parental control: key mismatch means the allow-list never works and genre-less books bypass the PIN; external intent bypass | SEC-05 | confirmed |
| Before release | Release signed with the debug key; targetSdk 34; Conscrypt libraries 4 KB-aligned; `MANAGE_EXTERNAL_STORAGE` | BUILD-01…BUILD-03, SEC-09 | confirmed; artifacts inspected |
| Before release | `LocaleManager` overwrites `uiMode`, breaking TV/night resource selection; equalizer crashes on non-5-band devices | UI-04, UI-06 | confirmed; TV not rendered |
| Before release | Deleting selected chapters removes only the first: the live selection set is cleared while a worker iterates it (user report) | DL-10 | confirmed (new) |
| Before release | A chapter boundary can stop playback: stale `buttonClick` after "previous" on chapter 1; foreground status dropped and re-requested per chapter (user report) | PLAY-19 | confirmed / plausible (new) |
| Before release | Search can spin until the screen is closed when an exception in the commit is swallowed by the crash handler; the voice-search button is hidden by package visibility and cold voice queries are dropped (user reports) | SRCH-16, SRCH-17 | confirmed mechanism (new) |
| Subsequent | The custom ANR watcher reports impossible "blocked 49 min" stalls and shows raw stacks to users (user report) | APP-03 | confirmed (new) |
| Subsequent | Duplicate timer/progress chains, undisposed Rx work, unbounded responses, 107 copy-pasted Jsoup set-ups, accessibility labels, empty states, hard-coded strings | PLAY-10, SRCH-11, NET-07, UI-05…UI-19 | confirmed |

## Where the reviews differed, and what was decided

| Topic | A | B | Decision |
|---|---|---|---|
| Migration index loss | High; certain for `saved`, conditional for the other tables | Critical; all four tables | **Critical.** `saved` alone makes Room refuse to open; the other tables cannot be proven without 15/16 schemas (DATA-03) |
| `closeDB()` calls | deliberate no-op, not a leak | "closed DB models" after rotation | **A.** Withdrawn from B's PB-01/PB-03; the service-stop consequence stands (PLAY-01, PLAY-03) |
| Read-only streaming cache | deliberate design, optional improvement | High defect | **Fact agreed; reframed** as a High product gap because combined with no back-buffer and no retry it is the main cause of fragile listening (NET-04) |
| `DocumentsContract.isTreeUri` on API 23 | hidden method exists, no proven crash | crash on Android 6 | **A** (BUILD-06) |
| Buffer values that crash | `1`, `00`, `000` (reproduced) | `0`…`2`, plus non-numeric | **A**, with B's missing back-buffer point kept (PLAY-09) |
| Service binding, foreground-start timing, title-based resume, speed `0`, corrupt trust anchor, VPN keys, `pref_proxy` type change, `MainActivity.onDestroy` | not covered | covered | **Verified and included** (PLAY-02, PLAY-06, PLAY-07, PLAY-12, SEC-06, SEC-02, DATA-12, UI-02) |
| Provider parsers (§8), listing pagination details, TV/tablet/keyboard items | partially (S11, S12) | 37 numbered findings | **Verified line by line**; about a quarter corrected in scope (§8, §13) |
| Runtime, artifact and provider evidence | executed | cited from A | **Retained as the only executed evidence** (§14) |
| Parental control: search path | not stated | "not filtered" | **Search is filtered; MediaBrowser is not**; allow-list never works (SEC-05) |
| Receivers registered for the Activity lifetime | not a leak (unregistered) | wasted work while stopped | **Both**: not a leak, still wasteful (UI-13) |
| Count of unbounded Jsoup set-ups | "throughout" | "~70"/"≈107" | **107** measured (NET-07) |

## Contents

1. [Security, secrets and network trust](#1-security-secrets-and-network-trust)
2. [Data loss: deletion, restore, migration and the database](#2-data-loss-deletion-restore-migration-and-the-database)
3. [Playback service](#3-playback-service-mediaplayerservice-bookpresenter)
4. [Media session, notification, audio focus, Android Auto](#4-media-session-notification-audio-focus-android-auto)
5. [Network transport, caching and storage initialisation](#5-network-transport-caching-and-storage-initialisation)
6. [Downloads and export](#6-downloads-and-export-exodownloadservice-downloadstoragehelper-downloads-screen)
7. [Offline library, folder import and storage access](#7-offline-library-folder-import-and-storage-access)
8. [Provider parsers](#8-provider-parsers-book-page-chapters-series-other-readers-comments)
9. [Search, listings and pagination](#9-search-listings-and-pagination)
10. [UI, lifecycle, accessibility, TV and settings](#10-ui-lifecycle-accessibility-tv-and-settings)
11. [Build, release, manifest and platform](#11-build-release-manifest-and-platform)
12. [Application process and architecture](#12-application-process-and-architecture)
13. [Claims corrected, narrowed or withdrawn](#13-claims-corrected-narrowed-or-withdrawn-during-reconciliation)
14. [Evidence](#14-evidence-what-was-actually-executed-and-what-this-reconciliation-re-checked)
15. [Remediation plan and regression gate](#15-remediation-plan-and-regression-gate)
16. [User-reported issues cross-checked against the code](#16-user-reported-issues-cross-checked-against-the-code)
- [Appendix A. Cross-reference of review identifiers](#appendix-a-cross-reference-of-review-identifiers)

---
## 1. Security, secrets and network trust

### SEC-01 — Critical — TLS certificate and hostname validation are disabled process-wide (A: S01 · B: SEC-01)

**Status:** Confirmed in source by both reviews; lint corroborates (`TrustAllX509TrustManager` ×2, `CustomX509TrustManager`, `BadHostnameVerifier`).

**Evidence:** `J/Consts.java:312-339` builds an `SSLContext` whose `X509TrustManager` has empty `checkServerTrusted`/`checkClientTrusted`. `J/App.java:282-289` installs it as the `HttpsURLConnection` default together with a hostname verifier that returns `true`; `J/App.java:291-307` repeats trust-all on the shared OkHttp client that Media3 and Picasso use. Every Jsoup request passes `Consts.socketFactory()` explicitly. `J/model/ImportModel.java:182-193,219-233` POSTs the user's site login and password through the same path and reuses the session cookies (`:45-51,128-134`).

**Impact:** anyone on the network path (public Wi-Fi, ISP, the bundled proxy of SEC-02) can read and alter catalog HTML, chapter lists and audio URLs, and capture the baza-knig `PHPSESSID`, the akniga security key and the import login/password.

**Context, not justification:** four of the nine provider roots failed strict TLS with *expired-certificate* errors in A's probe (2026-10-22). The comment in the code describes a workaround for an expired CDN certificate. That explains the origin of the hack; it does not make a global trust-all acceptable.

**Fix:** delete the trust manager and verifier; rely on platform trust plus `res/xml/network_security_config.xml` anchors (after SEC-06); use one shared OkHttp client. If a provider's chain is genuinely broken, add that provider's real certificate as a `<domain-config>` anchor or pin, never a global bypass.

**Verify:** local HTTPS servers with valid, expired, self-signed and wrong-host certificates, exercised separately through Jsoup, OkHttp/Media3 and Picasso; only the valid server may receive login data.

### SEC-02 — Critical — Reusable credentials and private keys ship in the APK (A: C13, C14 · B: SEC-02)

**Status:** Confirmed (values deliberately not reproduced here; presence re-checked by line count only).

**Evidence:**
- Telegram bot token and owner chat id: `J/activity/ActivitySendEmail.java:169-171`.
- SOCKS proxy host, port, user name and password: `J/Consts.java:136-139`, used by `J/App.java:309-313,375-384` and `J/fragment/SettingsFragment.java:275-283`.
- Three OpenVPN profiles, each with an embedded private-key block, in `app/src/main/assets/*.ovpn` (one uses `cipher none`, one `@SECLEVEL=0`). No code references them (`grep` for `.ovpn`/`getAssets(` finds nothing). B-only; verified.
- akniga AES passphrase hard-coded in `J/model/AudioModel.java:683` (duplicated in the unused `res/raw/config.js`). B-only; pending the parser verification pass.
- Unused `YANDEX_CLIENT_ID` placeholder (`app/build.gradle:37`), AdMob `APPLICATION_ID` meta-data and `com.android.vending.BILLING` permission with no corresponding library (`app/src/main/AndroidManifest.xml:24,117-119`). Verified.

**Impact:** anyone unpacking the APK controls the bot and can read every feedback message (user e-mail, device info). Proxy credentials plus SEC-01 give the proxy operator a full MITM position on proxied users. Removing the values from the latest source does not revoke them in already distributed APKs or repository history.

**Related defects in the feedback form (A: C14):** user text is inserted unescaped into a `parse_mode=HTML` message (`:159-177`), so `<`/`&` break or reject the submission; the full response is logged (`:189`); the send button stays enabled while a request is pending (`:142-153`); the screen presents an e-mail flow but sends device details to a Telegram bot.

**Fix:** revoke and rotate the token and proxy credentials now; delete the `.ovpn` assets; move feedback behind a backend (or `ACTION_SENDTO`); escape user text or send plain text; stop logging bodies; disclose where feedback goes.

### SEC-03 — High — The global `Authenticator` hands proxy credentials to any challenger (A: C13 · B: SEC-03)

**Evidence:** `J/App.java:375-384` and `J/fragment/SettingsFragment.java:277-283` install a default `Authenticator` whose `getPasswordAuthentication()` checks neither `getRequestorType()` nor the host. Jsoup uses `HttpURLConnection`, which consults the default authenticator for server `401 WWW-Authenticate: Basic` challenges as well as proxy `407`.

**Status:** Confirmed in source (standard JDK behaviour); not exercised against a live server.

**Fix:** answer only `RequestorType.PROXY` for the configured proxy host, or configure proxy authentication on the OkHttp client alone.

### SEC-04 — High — Exported, unauthenticated control surfaces (A: C07 · B: MS-07, SEC-04)

**Evidence:** about twenty app-private actions (`Play`, `SeekTo`, `SET_SPEED`, …) are registered `RECEIVER_EXPORTED` on API 33+ and without a permission below (`J/service/MediaPlayerService.java:1233-1269`; activity receivers `J/activity/BookActivity.java:443-478`; `J/activity/ClearSavedActivity.java:70-74`). Handlers assume initialised state (`getPosition` dereferences `mediaPlayer`, `:297-303`); `onGetRoot()` accepts any client (`:641-646`); `onPlayFromMediaId()` parses and indexes an untrusted id without range checks (`:1201-1205`). Status broadcasts (`Broadcast_SET_PROGRESS`, `SET_TITLE`, …) are sent without `setPackage` (`:772,1502,1579`). The service is exported in the manifest (`AndroidManifest.xml:245-259`).

**Impact:** any installed app can play, pause, seek, stop, set speed `0` (→ PLAY-12 crash) or toggle the equalizer, and can observe what and when the user listens.

**Reconciliation:** B lists `ClearSavedActivity.java:73` as "no flag below 33"; that line is the normal pre-33 overload (A). The design concern is the exported flag on 33+, not that branch.

**Fix:** `RECEIVER_NOT_EXPORTED` and `setPackage(getPackageName())` as a stop-gap; replace broadcasts with a `MediaController`/binder API; validate `clientPackageName`/uid in `onGetRoot`; validate every media id.

### SEC-05 — High — Parental control is bypassable and inconsistent (A: UI-04 · B: SEC-11, SEC-10)

**Evidence:** the allow-list is keyed by genre **URL** (`J/adapter/ParentalControlAddapter.java:45-53`); the list adapter masks covers by `getUrlGenre()` and treats `null` as blocked (`J/adapter/BooksListAddapter.java:140-143`); `BookActivity` checks the genre **name**, only when non-null, and skips the check when `notificationClick` is set (`J/activity/BookActivity.java:251-255,329-330`). `LoadBook` is exported (`AndroidManifest.xml:174-179`) and forwards a caller-supplied `notificationClick` boolean (`J/activity/LoadBook.java:49-54,87-88`), so an external intent bypasses the PIN (A, independently confirmed by GPT-6.1). Only six of nine sources have category loaders (`J/model/ParentalControlModel.java:25-62`) and loader failures are swallowed. Search **is** filtered (it reuses the same adapter and launch path, `J/activity/SearchableActivity.java:142,484,487`); the MediaBrowser path is not. The PIN is a plain int with no lockout (`J/activity/ParentalControlActivity.java:116,147,245,280`); B's "default `0` unlocks with `0000`" is theoretical because the UI cannot enable the control without first setting a PIN (`:186-206`).

**Impact:** because the keys never match, the allow-list never works: every book **with** a genre always prompts for the PIN, every book **without** one (deep links, sources without genres) never does, and any app can launch a book with the PIN skipped.

**Fix:** one `isAllowed(book)` decision keyed by a normalised source/category id, used by list rendering, launch, playback and the browser tree; define the unknown-genre policy explicitly (block); never trust `notificationClick` from an intent; store a hashed PIN with lockout.

### SEC-06 — Medium — The bundled ISRG Root X1 trust anchor is not a valid certificate (B: SEC-06; verified)

**Evidence:** `app/src/main/res/raw/isrg_root_x1.txt` (1,869 bytes) is rejected by `openssl x509` ("Could not find certificate"); it is referenced by `res/xml/network_security_config.xml:6`. The file is masked today by SEC-01. Once trust-all is removed, the broken anchor matters on API 23–24, where ISRG Root X1 is absent from the system store.

**Fix:** replace with the genuine PEM and check its fingerprint in CI.

### SEC-07 — Medium — Cleartext permitted for every host (B: SEC-05; verified)

`res/xml/network_security_config.xml:3` sets `cleartextTrafficPermitted="true"` on `base-config`, which makes the `mdpu.mcdir.ru` domain exception (`:9-11`) redundant; any `http://` playlist or audio URL from a scraped page is accepted. Set the base to `false` and list exceptions.

### SEC-08 — Medium — Device backups export sessions, PIN and paths (B: SEC-07; verified)

`AndroidManifest.xml:59,63`: `allowBackup="true"`, `fullBackupContent="true"`, no `dataExtractionRules` (lint `DataExtractionRules`). Default preferences hold the baza-knig cookie (`J/App.java:366`), the parental PIN and absolute download paths. Add backup/extraction rules that exclude secrets, or disable system backup and rely on the in-app backup once DATA-05/DATA-06 are fixed.

### SEC-09 — Medium — Storage permissions and store policy (B: SEC-08; A: C16 context; verified)

`MANAGE_EXTERNAL_STORAGE` is declared (`AndroidManifest.xml:20`) and requested before folder selection (`J/fragment/SettingsFragment.java:499-505`) although SAF is implemented; `requestLegacyExternalStorage` (`:67`) is ignored from API 30; `READ/WRITE_EXTERNAL_STORAGE` and `BLUETOOTH` carry no `maxSdkVersion`. All-files access is not grantable for an audiobook player on Play. Drop it; use SAF plus app-specific storage.

### SEC-10 — Low — Release logging and entry-point hygiene (B: SEC-10 · A: C14, S13)

Picasso `loggingEnabled(true)` (`J/App.java:340`); search queries and URLs logged in release (`J/presenter/SearchbalePresenter.java:545,565,591`, `J/model/BooksModel.java:121,1960-1990`); `ActivityCookies` is exported with an izibuk link filter but always loads baza-knig (`AndroidManifest.xml:79-105`, `J/activity/ActivityCookies.java:64`); deep links lack `autoVerify`.

---
## 2. Data loss: deletion, restore, migration and the database

### DATA-01 — Critical — Deleting a book recursively erases every name-matching folder under the roots (A: D01 · B: OF-01)

**Status:** Confirmed in source by both reviews and by A's two independent GPT cross-checks. Not executed against real media.

**Evidence:** `J/util/DownloadStorageHelper.java:444-471` deletes the **parent directory** of every track path recursively; `:504-509` deletes the whole title folder above the narrator folder; `:514-546` then walks the entire selected filesystem root **and** the SAF root and deletes every directory or file whose normalised name equals the book title or the last URL segment (matchers `:569-631`). Entry points: confirmed deletion in Downloads (`J/presenter/ClearSavedPresenter.java:97-127,135-162`), swipe/menu removal of a saved book (`J/presenter/FavoritePresenter.java:337`), and automatically when the last chapter of a book is deleted (`:1375-1382`, B).

**Impact:** importing a folder that also holds personal files and later deleting that book deletes the files; two narrations or two sources of one title delete each other; a user folder with the same name anywhere under the root (public *Download*, or the storage root under all-files access) is removed. Normalisation collapses punctuation and non-Latin/Cyrillic characters, widening collisions.

**Fix:** persist the exact URIs the app wrote for each book/rendition; delete only those, then remove verified-empty app-created directories; never search and delete by display name; distinguish *remove from library* from *delete files* for imported media.

**Regression:** two sources with the same title, two narrators, an unrelated sibling file, and a selected root whose name matches the title, on filesystem and SAF; every unrelated byte must survive.

### DATA-02 — Critical — Deleting one chapter deletes other chapters (substring and LIKE matches) (A: D14 · B: OF-02, DB-06)

**Status:** Predicate reproduced by A in isolation (deleting `10` selected `1.mp3` and `10.mp3`).

**Evidence:** `J/util/DownloadStorageHelper.java:1315-1328,1404-1427`: a file is deleted when its normalised stem is **contained in** the target chapter name, recursively into subfolders, and the SAF root is walked with `deleteDocMatching(rootDoc, chapterName, …)`. The SQL path `DELETE … url_audio LIKE '%…%'` (`J/room/BooksAudioDao.java:20-21`, `J/model/AudioListDBModel.java:41-46`) does not escape `%`/`_`; percent-encoded Cyrillic URLs are full of `%`, so one delete can hit several rows (B's point, confirmed: the DAO uses `LIKE` for "exact" lookup at `:29-30` as well).

**Fix:** delete by persisted row id and URI only; remove name matching from destructive paths; if pattern matching survives anywhere, escape SQL wildcards.

### DATA-03 — Critical — Migration 16→17 silently drops unique indexes; Room then refuses to open the upgraded database (A: D03 · B: DB-00)

**Status:** Confirmed; **runtime-reproduced by A** with the real migration objects (`index_saved_url_book` count 1→0) and the real generated Room implementation (`IllegalStateException: Migration didn't properly handle: favorite(...)`, transaction rolled back to version 15).

**Evidence:** `J/room/AppDatabase.java:86-108` (`migrateTable`): `ALTER TABLE t RENAME TO t_old` keeps the existing `index_t_url_book` attached to `t_old`; `CREATE UNIQUE INDEX IF NOT EXISTS index_t_url_book` (`:88-90`) is therefore skipped; `DROP TABLE t_old` (`:108`) removes the only index. Room expects the unique indexes declared in `app/schemas/com.fanok.audiobooks.room.AppDatabase/17.json`.

**Reconciliation:** A limited the *certain* case to `saved` (its index is created by `MIGRATION_15_16`, `:48`) and made favorite/history/audio conditional on their pre-17 schemas; B stated that all four tables lose their index. No version-15/16 schema JSON exists in the repository, so favorite/history/audio cannot be proven either way. It does not change the outcome: `saved` alone fails Room's validation on every 15→17 **and** 16→17 upgrade, Room rolls back, and `fallbackToDestructiveMigration()` does not apply to a validation failure, so the app cannot open its database until the user clears app data. Severity is therefore **Critical** (B) rather than High (A).

**Fix:** `DROP INDEX IF EXISTS` before re-creating, or create the new table under a temporary name and rename it last; export and commit every schema version; add `MigrationTestHelper` tests that assert rows and `PRAGMA index_list`.

### DATA-04 — High — The migration swallows copy errors and drops the old table anyway; destructive fallback for every other path (A: D04 · B: DB-01, DB-02)

**Evidence:** `J/room/AppDatabase.java:103-108`: `catch (Exception e) { /* we might lose data */ }` then `DROP TABLE t_old`. The unique index is created **before** the copy (`:88-90`), so an old table without a unique index that contains duplicate `url_book` rows makes the `INSERT … SELECT` fail and empties the table. Only `15→16` and `16→17` are registered with `fallbackToDestructiveMigration()` (`:34-35`): installs at version ≤14, and any downgrade, lose everything.

**Fix:** let copy errors roll the transaction back; de-duplicate or `INSERT OR IGNORE` explicitly; register every historical path; test retained user data, not just schema shape.

### DATA-05 — Critical — Restore wipes all four tables before validating the file (A: UI-01 · B: BK-01)

**Status:** **Runtime-reproduced by A**: the real `SettingsFragment.performRestoreFromStream` with `{}` returned `true` and emptied favorite, history, audio and books_audio.

**Evidence:** `J/fragment/SettingsFragment.java:457-462` accepts any file (`*/*`); `:786-830` parses `BackupPOJO` then clears favorites (`:794`), history (`:802`), positions (`:810`) and track lists (`:817`) as each list is read, with no transaction. `J/pojo/BackupPOJO.java:6-9` has nullable fields and no format/version marker. An empty file makes `fromJson` return `null` → NPE after favorites are already gone; Gson bypasses setters, so one record with a null NOT-NULL field aborts half-way (`J/room/BookBaseEntity.java:80-81`, B).

**Fix:** parse → validate every collection and entity → version check → replace in one `runInTransaction`; keep a recoverable snapshot; show counts and merge/replace semantics; treat a missing collection differently from an explicitly empty one.

### DATA-06 — Medium — The in-app backup is incomplete, unversioned and fails on one odd row (A: "Backup scope" · B: BK-02)

`J/pojo/BackupPOJO.java:5-9` and `J/fragment/SettingsFragment.java:647-650,689-692` cover favorites, history, positions and audio lists only: no `saved`, settings, parental list, cookies or schema version. `J/pojo/TimeStartPOJO.java:9-12` throws for URLs that fail `REGEXP_URL` and is called from `J/model/AudioDBModel.java:77`, so one malformed row aborts the whole backup (B; the regex accepts `http(s)`, `content` and `file` URLs, so such rows are rare — verifier). State the scope in the UI; version the document; make serialisation tolerant.

### DATA-07 — High — Main-thread database work, including Room queries inside a sort comparator (A: "Main-thread database work" · B: DB-03, DB-04)

`J/room/AppDatabase.java:36` enables `allowMainThreadQueries()`. Confirmed main-thread callers: swipe-remove and clear dialogs (`J/presenter/FavoritePresenter.java:330-349`, `J/fragment/SettingsFragment.java:353-386`), `J/presenter/BooksPresenter.java:187-202`, `J/MenuBSBooks.java:74-89`, `J/presenter/BookPresenter.java:199-200,218,256-263,272-279,359-376`, and the pause path in the service (`J/service/MediaPlayerService.java:1044-1050`, verified). Sorting the library by "saved" calls `getSavedAudioNumber()` (one Room query plus one `DownloadIndex` lookup per track) for **both** items of every comparison on the main thread (`J/presenter/FavoritePresenter.java:611-656`, sorted at `:150,426`; the saved filter loops the same way `:389-416`): a few hundred books with tens of tracks is tens of thousands of lookups per sort. Move to a worker with one transaction; enable StrictMode in debug.

### DATA-08 — Medium — `books_audio` has no index, no unique key and no stable order (A: "No unique track identity", "No stable playlist order" · B: DB-05)

`J/room/BooksAudioEntity.java:8-34` has only an auto-generated id; `17.json:374-375` shows no index although every query filters on `url_book` (`J/room/BooksAudioDao.java:14-30`); `@Insert(onConflict = REPLACE)` therefore never replaces, so repeated `add()` duplicates tracks; `getByBookUrl` has no `ORDER BY`, so playlist order depends on rowid. Add `(url_book, ordinal)` with a unique index and an explicit chapter ordinal.

### DATA-09 — High — The chapter list is rewritten non-transactionally on every online open and overwrites exported local paths (A: D12 · B: DB-07, DL-06)

`J/presenter/BookPresenter.java:362-377` (verified): `remove(bookUrl)` then N single `add()` calls with web URLs whenever the list loads from the network. The export's `url_audio` update (`J/service/ExoDownloadService.java:280-297,442-453`) is lost, and a crash between delete and insert leaves the book without tracks. The export updater itself matches only the query-stripped request id or the `__ref` form, so rows holding the original signed URL match neither (A). Keep `remoteUrl` and `localUri` in separate columns; update, do not delete-and-reinsert; wrap in a transaction.

### DATA-10 — Medium — Listening progress is coupled to history; "clear saved" leaves rows behind (B: DB-08, DB-09)

Removing one history item or clearing history also deletes listening positions (`J/presenter/FavoritePresenter.java:331-333`, `J/fragment/SettingsFragment.java:353-361`) even for books still in Favorites/Saved (verified). "Clear saved" (`:388-393`) removes Media3 downloads, and `onDownloadRemoved` then removes most `saved`/`books_audio` rows; what it leaves behind are imported books, requests without `__ref` data, and the exported files on disk, which the next scan re-imports (B's wording narrowed by the verifier). `addFavorite` with REPLACE on the unique `url_book` index deletes and re-inserts, so the row id changes and the list reorders (`J/model/BooksDBModel.java:51-55`); `addSaved` never refreshes metadata (`:86-92`); `addHistory` is count+delete+insert in three auto-commits (`:68-73`); `getAll()` relies on implicit rowid order (`J/room/FavoriteDao.java:24-25`).

### DATA-11 — Medium — Website-favorites import: stuck spinner, partial writes, unbounded paging (A: D20, "Additional persistence" · B: BK-03)

`J/presenter/ImportPresenter.java:38-65`: `loading` and the progress indicator are reset only in `onComplete`; `J/model/ImportModel.java:72-103` calls `onError` and then `onComplete` in `finally`, and the second terminal signal is ignored, so after any failure the screen stays "loading" and `if (!loading)` rejects retries. `ImportModel.java:127-175` loops `while(true)` without cancellation, page cap or repeated-page detection and requests one page past the confirmed last one; `:239-255` dereferences `sMsg`/`url` without null checks; the disposable is dropped (`ImportPresenter.java:48-49`). Fix the terminal-state handling first, then bound and cancel the loop.

### DATA-12 — High (Plausible) — Start-up crash loop after upgrading from a version where `pref_proxy` was a string (B: PR-02; source verified)

`J/App.java:303` (inside the `try` that starts at `:277`) and `:375` (**outside** any `try`) both call `pref.getBoolean("pref_proxy", false)`. `res/xml/preferences.xml:237-242` still contains a commented-out `EditTextPreference` with key `pref_proxy`, and the live preference is a `CheckBoxPreference` (`:91-96`). If any shipped version persisted a `String` under that key, `Application.onCreate` throws `ClassCastException` on every launch. The release history is not in the repository, so the trigger is unproven; the guard (read as `Object`, convert, rewrite) is cheap.

---
## 3. Playback service (`MediaPlayerService`, `BookPresenter`)

### PLAY-01 — Critical — Service teardown throws `RejectedExecutionException` before releasing anything (A: C01 · B: PB-01)

**Status:** **Runtime-reproduced by A** (`am stopservice` during real playback: `RejectedExecutionException at stopMedia:1387 / onDestroy:544`; the process survived with a dead main thread, see APP-01).

**Evidence:** `J/service/MediaPlayerService.java:538-544` calls `dbExecutor.shutdown()` and then `stopMedia()`, which submits the final position write to that executor (`:1380-1396`); the `try/catch` is inside the task, so submission rejection escapes `onDestroy`. Player release, audio-focus abandonment, effect cleanup and receiver unregistration (`:545-576`) never run. Reachable from session `onStop()` (`:1172-1178`), `OnCancelBroadcastReceiver` → `stopService` (`J/broadcasts/OnCancelBroadcastReceiver.java:15-16`) and the invalid-index `stopSelf()`.

**Reconciliation:** B adds that `mAudioDBModel.closeDB()` (`:548-550`) runs while writes may be queued. `BooksDBAbstract.closeDB()` is an intentional no-op around the Room singleton (`J/interface_pacatge/books/BooksDBAbstract.java:17-19`), so that sub-point is withdrawn (A had already noted this).

**Fix:** capture an immutable `(bookUrl, chapter, positionMs)` snapshot and persist it while the executor still accepts work (or synchronously), release player, session, effects and receivers in `finally`-style blocks, then shut the executor down.

### PLAY-02 — High — The service binding is never released: bound with the application context, unbound through the Activity (B: PB-02; verified)

**Evidence:** `BookActivity.provideBookPresenter()` passes `getApplicationContext()` (`J/activity/BookActivity.java:351`); the presenter binds with it (`J/presenter/BookPresenter.java:838`, `mContext.bindService(...)`); `onStop()` unbinds through the view (`:223-227` → `BookActivity.myUnbindService`, `:1214-1219`), i.e. `Activity.unbindService`, which throws `IllegalArgumentException: Service not registered` for a connection owned by another context; the exception is swallowed (`catch (Exception ignored)`). `serviceBound` is never reset.

**Impact:** the application context stays bound with `BIND_AUTO_CREATE` for the life of the process, so `stopSelf()`/`stopService()` cannot destroy the service: stop buttons, the sleep timer and notification dismissal only half-work; `onUnbind` never runs; player, notification and receivers linger. This was missed by A; it also explains why A's instrumentation had to unbind explicitly.

**Fix:** bind and unbind with the same context from one owner; better, replace the local binder with `MediaController`/`MediaBrowser`.

### PLAY-03 — High — Activities drive presenter destruction: rotating or leaving during buffering stops playback (B: PB-03, UI-01 · A: UI-02, Q02; verified)

**Evidence:** `BookActivity.onDestroy()` calls `mPresenter.onDestroy()` unconditionally (`J/activity/BookActivity.java:978`); the manifest declares `configChanges="locale|uiMode"` only, so rotation recreates the Activity while Moxy keeps the presenter. `BookPresenter.onDestroy()` broadcasts `CloseNotPrepered` (`J/presenter/BookPresenter.java:305-313`); the service stops itself when the player is not `STATE_READY` (`J/service/MediaPlayerService.java:357-366`). `onUnbind` does the same for "not playing", which includes buffering (`:728-734`, `:578-585`). The same manual call exists in ten view classes (`ClearSavedActivity:99`, `MainActivity:495`, `SearchableActivity:255`, `BooksFragment:394`, `ComentsBookFragment:117`, `DescriptionBookFragment:200`, `FavoriteFragment:264`, `OtherArtistFragment:88`, `SeriesBookFragment:101`); the Favorites and Downloads consequences are UI-01 in §10.

**Reconciliation:** B's "the presenter then uses closed DB models" is withdrawn for the same reason as in PLAY-01 (`closeDB()` is a no-op). The service-stop consequence stands.

**Fix:** never call presenter lifecycle methods from views; let Moxy own presenter destruction; decide service lifetime inside the service from `playWhenReady`.

### PLAY-04 — High — Pause is a toggle, buffering cannot be paused, and focus loss during buffering lets audio start later (A: C03 · B: PB-04, MS-04)

**Status:** **Runtime-reproduced by A** twice: two `ACTION_PAUSE` commands went PLAYING→PAUSED→PLAYING; a pause during a held HTTP response left `playWhenReady=true` and playback started when the response completed.

**Evidence:** session `onPause()` resumes when already paused (`J/service/MediaPlayerService.java:1144-1150`); `pauseMedia()` only clears `playWhenReady` when `isPlaying()` (= `STATE_READY && playWhenReady`, `:578-584`) is true (`:1225-1229`); the in-app play receiver toasts "loading not finished" while buffering (`:449-460`); `AUDIOFOCUS_LOSS_TRANSIENT` sets `pause = true` but leaves `playWhenReady` (`:611-617`), so an incoming call during buffering ends with audio playing during the call (B).

**Fix:** separate intent from state: pause = `setPlayWhenReady(false)` in every state; distinct play/pause/toggle commands; use `Player.isPlaying()`/`getPlaybackState()` explicitly; let ExoPlayer handle focus (SESS-05).

### PLAY-05 — High — Any playback error is terminal: toast only, no retry, Play does nothing (A: C04 · B: ER-01, ER-04)

**Status:** **Runtime-reproduced by A** against a loopback server: four HTTP 503 responses → `STATE_IDLE`, `ERROR_CODE_IO_BAD_HTTP_STATUS`; after the endpoint recovered, both the app Play broadcast and the session Play callback left the player idle; an explicit `prepare()` on the same instance recovered.

**Evidence:** `onPlayerError` only logs and toasts the raw error code (`J/service/MediaPlayerService.java:1059-1066`); the play receiver calls `resumeMedia()` only when READY and initialises only when the player is null (`:449-460`); `resumeMedia()` requires READY (`:1462-1465`). There is no `STATE_ERROR` on the session, no URL re-resolution, no fallback to a downloaded file, and the book screen never retries on connectivity return.

**Fix:** classify `PlaybackException`: IO → `prepare()` with backoff and a connectivity callback; 401/403/404/410 → re-fetch the chapter list once and retry; decoder/clipping → skip with a message; expose an error state with a retry action in UI and session; reconcile `isPlay`, notification and timers on IDLE.

### PLAY-06 — High — `startForegroundService()` without a timely `startForeground()` when autoplay is off (the default) (B: PB-06; control flow verified)

**Evidence:** `J/presenter/BookPresenter.java:825-826` always uses `startForegroundService()` on API 26+. `onStartCommand` builds a `PAUSED` notification when `BookPresenter.start` is false (`J/service/MediaPlayerService.java:712-716`); the `PAUSED` branch calls `stopForeground(false)` and `notify()` only (`:853-862`). `startForeground()` is called only from the `PLAYING` branch (`:839-851`), which runs when the player reaches READY (`:1008-1035`, after which the service immediately pauses itself when `start` is false, `:1032-1035`). `start` is false whenever `autoPlayPref` (default `false`) is off and the service is not already playing (`J/presenter/BookPresenter.java:379-397`).

**Impact:** with default settings, opening a book starts a foreground service that satisfies the foreground contract only when the stream becomes READY. A slow source or any error before READY (PLAY-05) means `startForeground` is never called: ANR on API 26–30, `ForegroundServiceDidNotStartInTimeException` crash on API 31+ after about 10 s. Not reproduced at runtime (A's probes set `start`/used local fixtures); the control flow is unambiguous.

**Fix:** call `startForeground` immediately in `onStartCommand` with a "preparing" notification of type `mediaPlayback`, then update it; never swallow a failed `startForeground` and continue as a background service (`:846-850`).

### PLAY-07 — High — Resume selects the chapter by title and keeps the old offset when it does not match (B: PB-07; verified)

**Evidence:** the current chapter is stored by **name** (`J/presenter/BookPresenter.java:256-263`); on open the loop keeps the **last** chapter whose name equals the stored one (`:345-352`); if none matches it falls back to index 0 but still calls `playAudio(curentTrack, mAudioDBModel.getTime(bookUrl))` with the book-level position (`:353-360,379-397`). Progress is per book URL (`J/service/MediaPlayerService.java:1050,1390,1529`).

**Impact:** repeated titles ("Часть 1", re-used track numbers) select the wrong chapter; renamed chapters play chapter 1 at the old minute.

**Fix:** persist `(bookId, chapterIndex, chapterKey, positionMs)`; match by key, then index; reset the offset when nothing matches.

### PLAY-08 — High — `stopSelf()` is not a return; dependencies are initialised in `onStartCommand` (A: C11 · B: PB-05)

`J/service/MediaPlayerService.java:688-702`: invalid index or denied audio focus calls `stopSelf()` and continues into `initMediaSession()`/`initMediaPlayer()`, which dereference `activeAudio` (`:897`) and `buildNotification` (`:801`); `NullPointerException` is caught as control flow (`:694`); `initMediaPlayer` (`:893-897`) and `resumeMedia` (`:1465-1467`) repeat the no-return focus check; the index check rejects only `-1`. `storage`, `mPreferences` and `mAudioDBModel` are created in `onStartCommand` (`:670,679,704`) although receivers registered in `onCreate` use them (`:411,467,964`) and `onLoadChildren` iterates a null `audioList` for a cold MediaBrowser bind (`:649-663`, see SESS-02). Return early; initialise in `onCreate`; validate all persisted input.

### PLAY-09 — High — Accepted buffer-size values crash player construction (A: C05 · B: PB-13)

**Status:** **Runtime-reproduced by A** by invoking the real `initMediaPlayer` with `1`, `00` and `000`: `IllegalArgumentException: minBufferMs cannot be less than bufferForPlaybackAfterRebufferMs` / `…bufferForPlaybackMs`.

**Evidence:** `J/fragment/SettingsFragment.java:331-334` rejects only the exact string `"0"` and otherwise accepts one to three digits; `J/service/MediaPlayerService.java:964-967` uses the value as both min and max buffer while Media3 1.9.0 keeps its 2,000 ms after-rebuffer default. **Reconciliation:** B's "`0`…`2` throw" is slightly wide: `2` (2,000 ms) passes; `0`-equivalents and `1` throw. B's `NumberFormatException` for non-numeric input is blocked by the regex for UI entry and remains possible only for restored or hand-edited preferences. Also: min == max leaves no headroom and no back-buffer is configured (B). Validate against the real `DefaultLoadControl` constraints at both ends.

### PLAY-10 — Medium — Duplicate progress loops and a sleep timer that counts down too fast (A: C08 · B: PB-09, UI-09)

**Status:** **Runtime-reproduced by A** on the real `Hourglass`: nine immediate `resumeTimer()` calls shortened a 1,109 ms countdown to 102 ms.

**Evidence:** every READY-while-playing transition starts new `handler1`/`handler2` runnables (`J/service/MediaPlayerService.java:1025-1026,1495-1547`) without `removeCallbacks` (never called, not even in `onDestroy`); `Hourglass.resumeTimer()` posts a new tick without removing the pending one (`J/Hourglass.java:100-104`) and each chain advances `localTime` by the nominal interval (`:193-198`). Rebuffers/seeks multiply broadcasts (2×, 3× per 500 ms), DB writes and timer ticks. Keep one scheduled task per purpose; compute the deadline from `SystemClock.elapsedRealtime()`.

### PLAY-11 — Medium — Queued position writes read a mutable book identity (A: C10 · B: PB-16)

Executor lambdas read the field `urlBook` when they run (`J/service/MediaPlayerService.java:736-745,1387-1391,1523-1529`); `onStartCommand` reassigns it for the next book (`:674`), so a write queued for book A can be stored under book B; chapter replacement is a separate remove/add (`:742-745`). Capture an immutable snapshot before submitting.

### PLAY-12 — Medium — Speed `0` crashes the process; changing speed while paused starts playback (B: PB-12 · A: C12; verified)

The speed dialog's `SeekBar` runs 0…300 in steps of 10 with no minimum (`J/presenter/BookPresenter.java:411-424`); confirming `0.0` calls `setSpeed(0)` (`:452-457`); the receiver constructs `new PlaybackParameters(0)` (`J/service/MediaPlayerService.java:491`), which Media3 rejects with `IllegalArgumentException` inside `onReceive` → uncaught on the main thread. The same receiver calls `resumeMedia()` first (`:490`), so editing speed while paused resumes. Any app can trigger both through the exported receiver (SEC-04). Clamp to ≥ 0.25 and change speed without changing play intent.

### PLAY-13 — Medium — Seek edge cases do nothing; the finished book cannot be replayed from the notification (A: C12 · B: PB-14)

`seekTo()` requires `0 ≤ pos < duration` and a seekable window (`J/service/MediaPlayerService.java:1550-1570`), so rewind in the first 30 s, forward in the last 30 s and any seek while duration is unset are ignored, and exceptions become "loading not finished" (`:1566-1569`). After the last chapter `skipToNext()` releases the player (`:1339-1342`) and the notification/session Play calls `resumeMedia()`, which cannot recreate it (`:1138-1140,1462-1465`). `resumeMedia()` also always seeks to `resumePosition` (`:1473-1486`), discarding buffer. Clamp seeks; share one play command that handles READY, ENDED, IDLE and no-player states.

### PLAY-14 — Medium — A new `ExoPlayer` per chapter; effects rebuilt on every READY; resume seeks after READY (B: PB-08, PB-10, PB-11; verified)

Each chapter change releases and rebuilds the player, load control and media-source factory (`J/service/MediaPlayerService.java:957-977`), abandons and re-requests audio focus (`:1402,891-893`) and rebuilds the equalizer on the next READY with the new session id, without releasing the old effects (`:209`). Every BUFFERING→READY and every resume re-parses equalizer prefs and re-creates three `AudioEffect`s when enabled (`:1029-1042,207-246`) and rebuilds the notification. The saved position is applied by `seekTo()` after READY (`:1008-1016`) instead of before `prepare()`, so playback buffers from 0 then seeks; with autoplay off the player starts and is paused at READY (`:1032-1035`), so a brief audible start is possible. Use one long-lived player with the book as a playlist and `setMediaItems(items, index, positionMs)`.

### PLAY-15 — Medium — Main-thread I/O inside the service (B: PB-15 · A)

Every `initMediaPlayer` iterates all completed downloads with a DB cursor on the main thread (`J/service/MediaPlayerService.java:900-913`) and may block on the `App` monitor while `SimpleCache` initialises (`J/App.java:397-409`); pause writes the position synchronously (`:1050`) although `stopMedia` uses `dbExecutor` (`:1388-1396`).

### PLAY-16 — Medium — No wake mode, back-buffer, load-error policy or audio-session handling (B: ER-02; verified by grep)

No `setWakeMode(C.WAKE_MODE_NETWORK)`, `setBackBuffer`, custom `LoadErrorHandlingPolicy`, `onAudioSessionIdChanged` or `LeastRecentlyUsedCacheEvictor` anywhere in the tree; the only cache wiring is the read-only `setCacheWriteDataSinkFactory(null)` (`J/service/MediaPlayerService.java:929`). With the screen off, CPU/Wi-Fi can sleep during a rebuffer; every 30 s rewind on a progressive stream re-requests data.

### PLAY-17 — Medium — Errors swallowed on control paths; receivers unregistered in one `try` (B: ER-03 · A: C09)

`resumeMedia()` ignores every exception (`J/service/MediaPlayerService.java:1462-1493`); all eighteen receivers are unregistered inside one `try` (`:553-574`), so the first failure leaks the rest; `App.onCreate` swallows TLS/client set-up failures (`J/App.java:344-346`) leaving `okHttpClientInstance` null, which then fails at the first HTTP open through `OkHttpDataSource` (`J/util/DownloadUtil.java:50-52`, `J/service/ExoDownloadService.java:194`).

### PLAY-18 — Low — Miscellany (B: PB-17, ER-05)

`onPlayWhenReadyChanged` re-dispatches `onPlaybackStateChanged` (`:989-1005`): a double-ENDED is mostly theoretical because ENDED releases the player synchronously, but `:991` calls `mediaPlayer.getPlaybackState()` without a null check. `onPlayFromMediaId` indexes without bounds checks (`:1201-1205`); the rating counter increments on every player creation including retries (`:1083-1092`); `REWIND` is 30 s while the receiver is named `seekToPrevious10Sec` (`:133,480-485`). Invalid clipping (`timeFinish < timeStart`) ends in `onPlayerError` with a toast only; `timeFinish == timeStart` plays a zero-length clip and auto-skips (`:943-951`).

---
## 4. Media session, notification, audio focus, Android Auto

### SESS-01 — High — `MediaSessionCompat`, timer and handler callbacks are never released (A: C09 · B: MS-01)

No `mediaSession.release()` exists in the tree (grep); `onDestroy` (`J/service/MediaPlayerService.java:537-576`) never cancels the `Hourglass` (only another timer command does, `:324,351`) nor clears `handler1`/`handler2`. Lock-screen and Bluetooth buttons can route to a dead session; a timer callback can run `stopMedia()`/`stopSelf()` against a destroyed service. Release the session, cancel timer and callbacks, cancel artwork requests, and make the same cleanup run after partial initialisation (after PLAY-01 is fixed so teardown actually reaches it).

### SESS-02 — High — A cold MediaBrowser connection gets no session token; the browse tree is a stub (A: C06 · B: MS-06)

`onCreate` only registers receivers (`J/service/MediaPlayerService.java:524-535`); the session, token, storage and audio list are created in `onStartCommand` (`:668-724,1100-1120`). A browser client (Android Auto, Wear, system media resumption) that binds to a stopped service therefore cannot obtain a token; `onGetRoot` accepts every caller (`:641-646`); `onLoadChildren` ignores `parentId`, returns only the current book's chapters and dereferences a null `audioList` before the first start (`:648-663`). No browsable library, no `onPlayFromSearch` (lint `MissingOnPlayFromSearch`/`MissingIntentFilterForMediaSearch`), no recent-root. Initialise an empty session in `onCreate`; validate clients; build a real browse hierarchy.

### SESS-03 — Medium — Playback state is published only on API ≥ 29 and starts as PLAYING (B: MS-02; verified)

`setPlaybackState` is called only in the `VERSION_CODES.Q` branch of `buildNotification` (`J/service/MediaPlayerService.java:826-835`); the session is created with `STATE_PLAYING` at position 0 (`:1118-1119`); there is no `STATE_BUFFERING`/`STATE_ERROR` and no position update on seek. Bluetooth/AVRCP, lock-screen and Wear controls show wrong state and progress on API 23–28 and incomplete state elsewhere.

### SESS-04 — Medium — Session metadata has no artwork, author or reader (B: MS-03; verified)

Only `TITLE` (plus `DURATION` on Q+) is set (`J/service/MediaPlayerService.java:1415-1430`); artwork, artist and album are commented out (`:1418-1420`); on Q+ the duration is read right after `initMediaPlayer`, before prepare, so it can be `C.TIME_UNSET` (`:999,1117`). Android 13+ media controls and Auto therefore show no cover or book information.

### SESS-05 — Medium — Deprecated audio-focus API, duplicated with ExoPlayer and abandoned at every chapter (B: MS-04 · A: C03; verified)

`requestAudioFocus(listener, STREAM_MUSIC, AUDIOFOCUS_GAIN)` (`J/service/MediaPlayerService.java:1325-1331`) instead of `AudioFocusRequest`; ExoPlayer's focus handling is disabled (`setAudioAttributes(attrs, false)`, `:1074`); focus is abandoned at every chapter change (`:1402`); ducking sets `pause = true` (`:619-626`). Together with PLAY-04 this yields playback during calls. Use `setAudioAttributes(attrs, true)` and remove the manual listener.

### SESS-06 — Medium — Notification construction and artwork handling (A: C18 · B: MS-05; verified)

The channel is re-created on every update with vibration and lights enabled on an `IMPORTANCE_LOW` channel (`J/service/MediaPlayerService.java:778-787`); `setAutoCancel(true)` on a media notification (`:804`); action titles `"rewind"`, `"pause"`, `"delete"` are English literals (`:812-824`) and do not change when paused; API <29 and ≥29 expose different action sets; the small icon is the generic `stat_sys_headset`. The Picasso `Target` is an anonymous object (`:865-885`) that Picasso holds weakly, so the cover may never arrive; every rebuild while `image` is null starts another load; a late result re-posts the **captured, older** builder, so an old book's title or play state can replace the current notification (A's C18 and B's MS-05 agree). Hold one strong `Target` per media identity, cancel it on replacement/teardown, and check the active identity before publishing.

### SESS-07 — Medium — Tapping the notification re-fetches the book page; nothing opens offline or for imported books (B: OF-06; verified with a correction)

The content intent targets `LoadBook` (`J/service/MediaPlayerService.java:789-793`), which always fetches the book page (`J/activity/LoadBook.java:59-91`) and `finish()`es on error. Offline the player screen never opens. For imported books whose synthetic host matches no server, `BookPOJO.getDescription` returns an empty POJO (`J/pojo/BookPOJO.java:569-571`) and a broken `BookActivity` opens instead (verifier correction). Open the player from persisted state; fetch metadata lazily.

---
## 5. Network transport, caching and storage initialisation

### NET-01 — High — The proxy toggle changes Jsoup requests at runtime but not the OkHttp client used by Media3 and images (A: T01 · B: SEC-09)

`J/fragment/SettingsFragment.java:273-286` flips `App.useProxy` and the `Authenticator`; `J/App.java:300-313` builds the single OkHttp client once at start-up with `.proxy(...)` only if the preference was already on; `J/util/DownloadUtil.java:48-56` retains one `ResolvingDataSource.Factory` around it. Jsoup paths check `App.useProxy` live (e.g. `J/model/AudioModel.java:870-888`), and only 56 of 107 Jsoup set-ups apply the proxy at all (knigavuhe, akniga, knigoblud, otrub and poleknig listings go direct, `J/model/BooksModel.java:339,605,1548,1742,2143`). Page and audio requests can therefore leave from different IPs, which breaks IP-bound signed URLs (izibuk `sign`, akniga token; plausible 403). Own one versioned network configuration; rebuild client and factory for new requests when the setting changes.

### NET-02 — High (provider-dependent) — HLS child requests lose book context; the baza-knig host check is unreachable (A: T02 · B: CA-07)

`J/util/DownloadUtil.java:126-137` appends `__ref` only to the top-level URL; Media3 resolves playlist, segment and key URLs as their own URIs, so children have no `__ref` and the resolver's no-ref branch substitutes a host-root `Referer` (`:94-102`). `u.getHost().contains(Url.SERVER_BAZA_KNIG)` (`:87`, and the same sub-expression at `:75`) compares a host (`baza-knig.top`) with a full URL (`https://baza-knig.top`, `J/Url.java:60`) and can never match, so a baza child request without `__ref` gets no `PHPSESSID`. The akniga flow really does return one HLS URL for many chapters (`J/model/AudioModel.java:481-518,529-575`), so the topology is real; the CDN's exact header policy was not measured. Pass an immutable request context to every HLS request via a scoped factory; compare parsed hosts with parsed hosts.

### NET-03 — Medium — The network interceptor overrides the resolver's User-Agent; `withRequestHeaders` replaces; no `CookieJar` (A: T03 · B: CA-06)

The resolver chooses a desktop UA for ABMP3/baza-knig (`J/util/DownloadUtil.java:70-80`) but the shared OkHttp network interceptor unconditionally replaces `User-Agent` (and some `Referer`s) with `Consts.USER_AGENT` (`J/App.java:315-333`), as does `OkHttpDataSource.Factory.setUserAgent` (`:52`). `dataSpec.withRequestHeaders(map)` (`:114`) replaces headers already on the `DataSpec` instead of merging (no current call site adds any, so this is a contract hole, not a measured failure; `Range` is derived separately and unaffected — A). The client has no `CookieJar`; only baza-knig's `PHPSESSID` is injected manually (`:79-81,91-93`). Move header policy to one layer and verify on-wire values per provider.

### NET-04 — High — No streaming cache; cache keys depend on `__ref` and volatile query tokens; download ids collide (B: CA-01, CA-02 · A: D13, T-section)

**Facts (agreed):** for network URIs the player uses `CacheDataSource` over the **download** cache with `setCacheWriteDataSinkFactory(null)`, i.e. read-only (`J/service/MediaPlayerService.java:922-931`), and the download cache uses `NoOpCacheEvictor` (`J/App.java:457`). The annotated media URI carries `__ref=<book URL>` (`J/util/DownloadUtil.java:126-138`) and no custom cache key is set (the line is commented out at `J/presenter/BookPresenter.java:572`), so the cache key depends on the book page URL and every query token: the same file reached through a mirror, redirect, `http`/`https`, trailing slash or re-signed URL gets a new key. The download **id** is `scheme+authority+path` (`:561-573`, `J/pojo/AudioPOJO.java:39-50`): sources that address files by query (`get.php?id=…`) collapse every chapter to one id, and `DownloadManager` merges requests with an existing id (A reproduced the collision in isolation; provider exposure unmeasured).

**Reconciliation:** B rates the absence of a streaming cache High (re-listening, seeking back, chapter restarts and retries re-download; short network loss stops playback even for audio heard a minute ago). A treats the read-only use as a deliberate design choice and recommends a separate bounded LRU streaming cache only if the product promises repeat-stream caching. Both are right: the read-only wiring is not a bug by itself, but combined with PLAY-16 (no back-buffer) and PLAY-05 (no retry) it is the main reason ordinary listening is fragile. Treat it as a High product gap with a clear fix rather than a correctness defect.

**Fix:** a second `SimpleCache` for streaming with `LeastRecentlyUsedCacheEvictor` in app-specific storage; a `CacheKeyFactory` that produces a stable `bookId/chapterIndex` key independent of `__ref` and tokens; `setCustomCacheKey` on both `DownloadRequest` and `MediaItem`; keep the no-eviction download cache for explicit offline items; stop appending app metadata to the provider's signed URL.

### NET-05 — High — The download store lives under the user's folder, can fall back into that folder or into the system-clearable cache dir, and a folder change orphans everything (A: D18, D19 · B: CA-03; verified)

`J/App.java:445-455`: `SimpleCache` is created in `<download path>/.audiobooks_cache`; if that directory cannot be created, **the whole user download folder** becomes the cache directory (`:452-454`), which `SimpleCache` treats as its own (it removes unrecognised files, per its API contract — A); if the constructor then throws, the cache moves to `getCacheDir()/downloads` (`:458-464`), which Android may purge under storage pressure while the `DownloadIndex` still reports `STATE_COMPLETED`. Changing the folder in Settings (`J/fragment/SettingsFragment.java:529-535,559-565,588-595,609-615`) re-initialises an empty cache under the new path without migrating content or the index. Completed media remains in the non-evicting cache **and** is exported as a second copy (DL-07). Keep the Media3 store in app-specific storage; treat export as a separate copy step; migrate or re-validate on path change.

### NET-06 — High — Re-initialising the download manager releases a cache and manager that are still in use; Media3's static helper keeps the released manager; thread pools leak; the lazy initialisation races (A: Q01, Q03, T04 · B: CA-04, CA-05, AP-02)

`App.reinitDownloadManager()` (`J/App.java:421-476`) releases `downloadCache` and `downloadManager` while a playing `MediaPlayerService` holds the same `SimpleCache` in its `CacheDataSource.Factory` (`J/service/MediaPlayerService.java:923-930`; `SimpleCache` must not be used after release) and while export threads may still read it (DL-03). Media3 1.9.0 `DownloadService` keeps a `private static HashMap<Class, DownloadManagerHelper>` (B verified with `javap`; A verified against the 1.9.0 source): `onCreate` reuses the helper and `onStartCommand` sends commands through the helper's manager, and the app never calls `DownloadService.clearDownloadManagerHelpers()`, so after a folder change the base service keeps driving the **released** manager while the subclass listens to the new one (`J/service/ExoDownloadService.java:53-74`). Each re-init creates `Executors.newFixedThreadPool(3)` (`:468-474`) that nothing shuts down (Media3 does use it for parallel HLS segments — verifier correction to B's "unused"). `downloadManager`/`downloadCache` are non-volatile fields read outside the lock (`:33-34,397-409,478-485`), and `onCreate` launches an **async** re-init (`:350,411-419`) that stops `ExoDownloadService` and releases any manager an earlier `getDownloadManager()` already created (e.g. the system restarting the download service after process death, `J/service/ExoDownloadService.java:55`). The manager is also constructed and configured from a looper-less background thread, which Media3 says requires main-thread access afterwards (A). One coordinated owner: never re-create the manager at runtime; migrate content on folder change; marshal manager calls to one looper; shut down owned executors.

### NET-07 — Medium — Unbounded responses, a new `SSLContext` per request and 107 copy-pasted Jsoup set-ups (A: S12 · B: SRC-35, SRC-37, SEC-09; counts verified)

`Consts.socketFactory()` (`J/Consts.java:312-339`) builds a new `SSLContext` (and installs the trust-all manager) at **107** call sites; `Jsoup.connect(` appears 108 times, `.maxBodySize(0)` 107 times (unbounded HTML into the DOM parser, across nine providers and up to four pages per search), `.proxy(` 56 times. There is no connection pooling, HTTP/2, disk cache or cookie jar for Jsoup traffic; `ignoreHttpErrors(true)` lets challenge/error pages into the parsers (SRC-03). Fetch through the shared OkHttp client with timeouts and size limits and parse with `Jsoup.parse(body, baseUrl)`.

### NET-08 — Low — `__ref` stripping leaves a dangling `?`; double encoding (B: CA-08; verified)

`buildAnnotatedUri` adds `__ref` as the only parameter to query-less URLs, and removing it (`J/util/DownloadUtil.java:105-112`) leaves an empty encoded query that still emits `?`; `Uri.encode` plus `appendQueryParameter` double-encodes, and the `%2F`/`%3A` heuristic (`:58-64`) over-decodes a literal `%2F` in the book URL. Goes away with NET-04's stable keys.

---
## 6. Downloads and export (`ExoDownloadService`, `DownloadStorageHelper`, Downloads screen)

### DL-01 — Critical — Exported chapter files are concatenated from sibling cache entries (A: D02 · B: DL-01)

**Status:** **Runtime-reproduced by A** with the real exporter against a disposable Media3 cache: a 32-byte second resource exported as 63 bytes containing both siblings. Predicate also reproduced in isolation.

**Evidence:** `J/service/ExoDownloadService.java:223-236` strips the query and final path segment to a directory prefix and accepts every cache key that `equals`/`contains` the request id, `contains` the directory prefix, or is contained in the request URI; `:245,261-275` sort all spans by byte offset only and concatenate them. Chapters of one book normally share a directory, so every later export mixes earlier chapters in interleaved order. **Fix:** read the exact cache key through `CacheDataSource` with a `DataSpec` (or `CacheWriter`), never by key substring; Media3's download guide warns against reading cache files directly.

### DL-02 — High — `PlatformScheduler` is used but its job service and the RESTART filter are not declared (A: N02 · B: DL-02)

`getScheduler()` returns `new PlatformScheduler(this, JOB_ID)` (`J/service/ExoDownloadService.java:89-92`). A searched both merged manifests: no `androidx.media3.exoplayer.scheduler.PlatformScheduler$PlatformSchedulerService`, the only `BIND_JOB_SERVICE` service is WorkManager's, and the download service element has no `…downloadService.action.RESTART` filter. B confirmed the Media3 AAR manifest does not supply it either. When downloads stop for unmet requirements (network lost) and the service schedules a restart, `JobScheduler.schedule` can throw `IllegalArgumentException` for an undeclared service (plausible crash, not exercised); auto-resume after network loss, process death or reboot certainly cannot work. Foreground downloads still work, so a happy-path test misses this. Declare the service with `BIND_JOB_SERVICE` and the RESTART filter, or switch to `WorkManagerScheduler` (WorkManager is already a dependency).

### DL-03 — High — Failed or partial exports are persisted as success; export runs on unmanaged raw threads (A: D05, D17 · B: DL-05; verified)

`openOutputStreamForExport` fills the output path before the write succeeds (`J/util/DownloadStorageHelper.java:206-218,273-277`); files are written directly under their final name with no temp file, rename or length check against `download.contentLength` (`:206-250`); the non-HLS caller catches write exceptions and still replaces the DB URL when the path string is non-empty (`J/service/ExoDownloadService.java:263-291`); HLS export skips missing segments and `writeHlsFile` swallows write failures (`:386-400,638-661`); the `saved` row is added before export begins (`:138-150`); exceptions are only logged (`:276-278,299-301,460-462`). Each completion starts a raw `Thread` that captures the service (`:138-155`), each cover another (`:187-204`); the cover `exists()` check precedes the async write, so concurrent exports can truncate the same `cover.jpg`, and a failed write leaves a partial file that blocks retries (verifier). Media3 marks the download complete before either finishes; there is no durable export job, cancellation or restart reconciliation, and NET-06 can release the cache under a running export. **Fix:** temp file → validate length → atomic rename → then persist the local URI; keep `remoteUrl` and `localUri` separately; a managed, serialised, resumable export stage.

### DL-04 — High — HLS export is a partial re-implementation that can output the wrong stream or corrupt audio (A: D06 · B: DL-08)

`J/service/ExoDownloadService.java:320-439,530-568,579-605,638-701`: master playlists are not resolved through their rendition URIs (`findMediaPlaylistKey` takes the first cached media playlist under a similar base, then any in the cache); `resolveUrl` string-concatenates, so `/absolute` and `../relative` segments are wrong; only the last `EXT-X-KEY`/IV is kept and applied to every segment; `EXT-X-BYTERANGE` is ignored (repeated segment URLs append the whole object repeatedly); cache-span files are treated as segment and AES-sequence boundaries; missing segments are skipped and decryption failures can emit ciphertext as success; the NoPadding fallback strips legitimate trailing zero bytes (`:690-698`). Keep HLS downloads as Media3-managed assets, or use a real parser and remux pipeline.

### DL-05 — High — One HLS URL represents many chapters, so selection, download and export lose chapter identity; query-stripped ids collide (A: T05, D13 · B: DL-03, CA-02; verified)

The akniga parser assigns the same `hlsUrl` to every chapter and varies only `timeStart`/`timeFinish` (`J/model/AudioModel.java:547-576`); playback clips correctly (`J/service/MediaPlayerService.java:943-950`), but `AudioAdapter` keys selection by raw URL (`J/adapter/AudioAdapter.java:137-139,347-392`), so selecting one chapter selects every row; `BookPresenter` queues one `DownloadRequest` for the set (`:559-575`); the exporter names the single file after the **first** matching chapter (`J/service/ExoDownloadService.java:167-175`) and updates one DB row (`:282-292`). Items without `time_finish` keep `timeFinish = -1`, which skips clipping entirely, so such a chapter plays the whole stream from 0 (verifier). Query-defined resources collapse to one id (NET-04). Model chapter identity separately from resource identity; offer an explicit whole-stream download with per-chapter clipping metadata.

### DL-06 — High — The Downloads screen: one finished chapter marks the book 100 %, rows change identity between load and refresh, selection is positional, and the list is mutated from two threads (A: D07, D08, D09, D10 · B: DL-11, UI-16)

`ClearSavedModel.getAllSaved()` turns every `saved` book into `STATE_COMPLETED`/100 % and then suppresses index entries sharing that book URL (`J/model/ClearSavedModel.java:41-55,76-78`), so queued, failed and running chapters are hidden (a failed export is not represented either). Initial rows are keyed by book URL from `__ref` (`:76-79`), live updates by `request.id` (`:130-131`), and the pause/resume/restart receiver expects an individual download id (`J/broadcasts/DownloadActionReceiver.java:72-97,129-143`); refreshed chapter rows then fail `getSomewhere(item.getFileName())` (`J/activity/ClearSavedActivity.java:172-196`), so opening does nothing and whole-book cleanup receives a chapter URL. Selection lives in a `SparseBooleanArray` of positions (`J/adapter/DownloadAdapter.java:61-99,287-297`) that is never remapped when the list refreshes; the single-delete dialog remembers a position and the bulk dialog resolves old positions on OK (`J/activity/ClearSavedActivity.java:260-278,318-329`), so a refresh while a dialog is open deletes the neighbour (and DATA-01 makes that irreversible). Each broadcast launches another refresh that iterates and mutates the shared `downloads` list on IO while UI deletion mutates it on main; `isLoading` is never set true, so it serialises nothing (`J/presenter/ClearSavedPresenter.java:63-90,97-104,135-139,205-237,241-291`); `areContentsTheSame` ignores title/cover/author (`J/adapter/DownloadAdapter.java:424-430`). `ClearSavedActivity.java:343-345` does clone rows, so A's rejected "shallow-copy" claim stays rejected. Rebuild as per-book rows with explicit chapter ids and aggregate state, immutable snapshots replaced on main, selection by stable id.

### DL-07 — High — Every downloaded book is stored twice with no reconciliation or budget (A: D19 · B: DL-04)

Completed media stays in the non-evicting cache and is copied to the export folder (`J/service/ExoDownloadService.java:261-275`); nothing removes the cache copy; replacing `urlAudio` with a local path means chapter deletion later sends a local path as the Media3 download id, which matches nothing (`J/util/DownloadStorageHelper.java:1215-1239`, OFF-06); whole-book removal has a separate URL-enumeration workaround. No free-space preflight, no storage accounting. Decide which copy is authoritative and reconcile explicitly.

### DL-08 — Medium — MIME types and extensions are wrong; listener and receiver work on the main thread; no requirements or feedback; substring book association (B: DL-07, DL-09, DL-10, DL-12; verified with corrections)

Export always declares `audio/mpeg` (`J/service/ExoDownloadService.java:265,438`) and appends `.mp3` to anything not ending in mp3/ts/aac/m4a (`:208-211`); through SAF, `createFile("audio/mpeg", "x.m4a")` normally yields `x.m4a.mp3`, and HLS `.ts`/`.aac` output is also labelled `audio/mpeg` (verifier). `DownloadManager.Listener` callbacks run on the main looper; `onDownloadRemoved` scans every download and performs Room writes there (`:721-795`); `DownloadActionReceiver` can trigger synchronous `SimpleCache` initialisation and, for CANCEL, cursor scans on main (`J/broadcasts/DownloadActionReceiver.java:34-35,59-66,91-122`). No `setRequirements` (Media3's default NETWORK applies), no free-space check; `RESTART` removes then re-adds, which discards partial data (`:123-139`); the book screen shows a spinner per chapter without percentage (`J/adapter/AudioAdapter.java:258-266`), although the foreground notification does show progress (`J/service/ExoDownloadService.java:96-105`). Each request carries the full `BookPOJO` JSON including description as `data` (`J/presenter/BookPresenter.java:570-574`); book association on removal uses `contains` in both directions (`J/service/ExoDownloadService.java:769`), so `/book/12` vs `/book/123` keeps a stale `saved` row (it does not wrongly delete one — verifier).

### DL-09 — Medium — An MP3 whose URL (or whose `__ref` book URL) contains "hls" is routed to the HLS exporter and never exported (B: CA-07 at the export site; verifier correction)

`J/service/ExoDownloadService.java:217-221` tests `download.request.uri` (which carries `__ref=<book URL>`) for `m3u8`/`hls`; `exportHlsDownload` returns early when no playlist key exists (`:309-311`), so the completed download silently has no exported file. The player applies the same substring test to force `APPLICATION_M3U8` (`J/service/MediaPlayerService.java:937-941`). Use `Util.inferContentType`/the response content type.

---
## 7. Offline library, folder import and storage access

### OFF-01 — High — Offline fallback can play another book's chapter (A: D11 · B: OF-04)

`loadOfflineLocalAudioSync()` resolves each chapter by searching the book directory and then the **entire download root** for a file with the same normalised name, accepting the first match with no book, narrator, source or duration check (`J/presenter/BookPresenter.java:750-763`, `findExportedFileByName` `:863-889`). Generic names (`01`, `Пролог`, `Часть 1`) collide across most libraries; cost is O(chapters × files) recursive listings. Resolve only persisted owned URIs scoped to a stable book/chapter id; a repair scan must prove an exact match.

### OFF-02 — High — Folder scanning: lexical chapter order, narrator folder ignored, name-slug de-duplication deletes real saved books, name heuristics (A: D15 · B: OF-05; verified with corrections)

Tracks are sorted with `String.compareTo` (`J/util/DownloadStorageHelper.java:860-867,973-978`): `1, 10, 2` (A reproduced). The synthetic URL `https://<source>.org/imported/<book>` ignores the reader folder (`:832,945`), so two narrations overwrite each other's track list (`audioListDBModel.remove(fakeUrl)`, `:869,980`). De-duplication (`:1023-1064`) deletes a **real** saved book's row and its `books_audio` rows when its URL slug equals the folder name (name matches are pre-empted by `hasRealSavedBookWithName`, so the trigger is narrower than B stated; positions in `audio` are orphaned rather than deleted — verifier); the source URL, and with it streaming/re-download, is lost. Folder roles depend on names such as `Download`, `0`, `sdcard`, `озвуч…` (`:760-777,904-922`); a fresh scan after a DB reset shows `Source/Title/Narrator` as a book named `Narrator`. Repeated scans delete all rows before reinserting without a transaction; `lastScanStartMs` is unused (A). Persist import metadata at export time; identify imports by tree URI plus rendition; natural sort; one transaction per playlist.

### OFF-03 — High — File naming causes collisions and silent failures (A: "No filename collision/length policy" · B: OF-03; verified)

Exported files are named after the chapter title (`J/service/ExoDownloadService.java:208-212`) inside `<source>/<book title>/<reader>` (`J/util/DownloadStorageHelper.java:373-383`): duplicate chapter titles overwrite each other; same title and reader share a folder; `sanitizeFileName` (`:366-370`) replaces reserved punctuation and trims, but does not cap length (255-byte limit, Cyrillic is two bytes per character), strip embedded control characters or trailing dots, or reject `.`/`..` and reserved names; `A/B` and `A:B` both become `A_B`; output streams overwrite silently. Use `NNN - title.ext` inside a stable book-id directory and persist the actual destination URI.

### OFF-04 — Medium — SAF and filesystem handling diverge: guessed paths, silent destination changes, ghost availability, O(n²) provider IPC (A: D16 · B: OF-08; verified)

Tree URIs are converted to raw paths (`J/util/DownloadStorageHelper.java:301-347`) that are wrong for the Downloads/`msf:` providers and unwritable under scoped storage, and `SimpleCache` then uses that raw path (NET-05). Export tries the path first and silently falls back to app storage when SAF fails, while the stored preference still names the chosen folder (`:198-277`; the fallback is only logged). Repair tests `content://` by constructing a `File` and can rewrite a valid content URI to a guessed path (`:1124-1132`). Offline/remaining-download checks treat every `content://` or `file://` string as present without opening it (`:1343-1347`, `J/presenter/BookPresenter.java:733-737`). Cover download uses `FileOutputStream` only (`J/service/ExoDownloadService.java:182-204`). `DocumentFile.findFile/listFiles` are called in loops (`:233-245,280-287,582-593`), one provider query each. Treat File and SAF as separate storage implementations behind one interface; validate grants; surface fallbacks.

### OFF-05 — Medium — Library maintenance runs concurrently on raw threads without transactions (B: OF-09 · A: D15, D17; verified)

Scan (`J/util/DownloadStorageHelper.java:704-735`), repair (`:1098-1193`, started on every Saved-tab load, `J/presenter/FavoritePresenter.java:120-122`), export (`J/service/ExoDownloadService.java:141`) and delete (`J/presenter/BookPresenter.java:595-604`) read-modify-write the same tables with no transaction or mutual exclusion; `getAllSaved()` is reloaded up to three times per scanned directory (`:995-1016`). Deletion also reports success via toast before the thread finishes (`J/presenter/BookPresenter.java:595-605`, A). Serialise through one worker with transactions and structured results.

### OFF-06 — Low — Local-path chapters cannot remove their Media3 download; `.mp3` assumptions; a 600 ms rebroadcast as synchronisation (B: OF-10 · A: D19; verified)

`sendRemoveDownload(localPath)` matches no Media3 id and the clean URL is never removed (`J/util/DownloadStorageHelper.java:1215-1239`); the "remaining" heuristic assumes `.mp3` (`:1364-1370`); a 600 ms delayed duplicate broadcast stands in for waiting on `onDownloadRemoved` (`:1386-1393`). After deleting a local chapter the row can be set back to the just-deleted local path (`:1297-1311`, DATA-09).

---
## 8. Provider parsers (book page, chapters, series, other readers, comments)

Nine sites are supported (knigavuhe, izibuk, ABMP3, akniga, baza-knig plus an archive.org mirror, knigoblud, audioboo, otrub, poleknig). Each model picks a parser by `url.contains(Url.SERVER_X)` and repeats its own Jsoup set-up. All findings in this section come from B and were verified line by line in this pass; corrections are noted inline.

### SRC-01 — High — Failures are returned as fake chapters, which disables the offline fallback (B: SRC-01; verified)

akniga converts any exception or missing security key into an `AudioPOJO` named `Ошибка AJAX: …`/`Ошибка: не найден securityKey…` with an empty URL (`J/model/AudioModel.java:624-634,656-659`; baza-knig `:1019-1024`). The list is non-empty, so `onNext` shows it (`J/presenter/BookPresenter.java:676-680`) and the offline fallback in `onError` (`:663-673`) never runs. **Correction:** with no network at all the presenter goes offline before calling the model (`:644-652`), so the damage is specific to "network up, site broken or blocked": a fully downloaded book becomes unplayable. If akniga throws after some items were added, the error item is appended to a partial list, passes the single-item guard (`:340-342`) and is persisted with an empty URL (`:366-375`) (verifier). `EncodingExeption` exists and is imported but never thrown. Throw typed errors; drop items without a URL; fall back offline on any failure.

### SRC-02 — High — Description errors are swallowed; the baza-knig cookie prompt is unreachable; challenge pages are parsed as content (B: SRC-02, SRC-03 · A: S12)

`BookDescriptionModel` catches everything (`J/model/BookDescriptionModel.java:104-106`), including the `CookesExeption` thrown at `:987-989`, and always emits `onNext`, so the cookie toast in `J/presenter/BookDescriptionPresenter.java:74-79` never shows. `ignoreHttpErrors(true)` is used widely (`:1375`; `J/model/AudioModel.java:207,283,456,876,1099,1162`; `J/model/SeriesModel.java:116,225`; `J/model/OtherSourceModel.java:352`); only baza-knig checks the challenge title (`:987`, `J/pojo/BookPOJO.java:699`); calls without it throw a bare `HttpStatusException`. The user sees "no tracks"/"Ошибка загрузки", never "site blocked, solve the challenge". One fetch helper that classifies status, `cf-mitigated`/challenge markers and auth, plus a WebView challenge flow for every source.

### SRC-03 — High — `substring(indexOf(...))` crashes on any script that is not the player (B: SRC-07; verified)

knigavuhe: `value.substring(value.indexOf("var player = new BookPlayer"))`, then `indexOf("\n")` and `indexOf("], ")` (`J/model/AudioModel.java:173-175`); any `-1` throws for the whole book; `get("duration").getAsInt()` NPEs when absent (`:185`). izibuk uses a different marker (`new XSPlayer(`…`");`, `:1056-1058`) but fails the same way. **Correction:** the failure goes to `onError` → offline fallback → error toast, not merely "no chapters". Skip scripts without the marker; extract with a bracket-matching scanner; make fields optional.

### SRC-04 — High — Copy-paste host/selector bugs produce wrong links (B: SRC-18; verified, severity Low–Medium)

knigoblud series URLs are prefixed with knigavuhe's `Url.SERVER` (`J/model/SeriesModel.java:461`); akniga's description uses audioboo's `/xfsearch/cikl/` selector and host, so akniga series are never found (`J/pojo/BookPOJO.java:375-381`); audioboo relative series links get the otrub host (`:901,919`); the source label says `baza-knig.ink` while the constant is `.top` (`J/model/OtherSourceModel.java:407`). Impact is limited to the series link in the book menu (`J/MenuBSBooks.java:125`) and the series tab.

### SRC-05 — High — POJO setters throw inside parsers, so one bad item fails the whole list (B: SRC-19, SRC-20 · A: S12; verified)

`OtherArtistPOJO.setName` does `substring(0,1)` (throws on `""`) and `setUrl` throws for non-absolute URLs (`J/pojo/OtherArtistPOJO.java:15-28`, `J/Consts.java:38-39`; inherited by `SeriesPOJO`); raw hrefs are passed at `J/model/SeriesModel.java:135,192,319`, `J/model/OtherSourceModel.java:406,930`, `J/model/OtherArtistModel.java:136,301,442`, each inside an outer-only `catch`. `text.substring(0, text.indexOf(" - "))` throws when the `h1` has no ` - ` (`J/model/BookDescriptionModel.java:1111`, `J/pojo/BookPOJO.java:821`); `BookPOJO.setName` throws on empty (`:1164-1166`), aborting the whole book load (LoadBook just finishes). Whether the sites currently emit such hrefs is not provable offline; the parsers must tolerate them.

### SRC-06 — Medium — `Element.child(i)` null checks never run; Jsoup throws instead (B: SRC-21 · A: S12; verified)

Dead `!= null` after `child()` at `J/model/BookDescriptionModel.java:1166-1167`, `J/pojo/BookPOJO.java:522-523`, `J/model/OtherSourceModel.java:533-552,651-652,760-767`, `J/model/ComentsModel.java:174-181`, `J/model/AutorsModel.java:102-103,264-265`; unguarded `child()` at `J/model/ComentsModel.java:531-533`, `J/model/AutorsModel.java:150`. The real defect is the uncaught `IndexOutOfBoundsException` aborting the whole list. A's examples (`J/model/BooksModel.java:521-526,1070-1073`, `J/model/GenreModel.java:395-419`) are the listing-side instances.

### SRC-07 — Medium — The shared `mDocument` is raced and re-fetched (B: SRC-22; verified)

`getBooks()` and `getDescription()` are subscribed concurrently on `io()` (`J/presenter/BookDescriptionPresenter.java:47-48,65-66,106-108`) and both see the non-volatile `mDocument == null` (`J/model/BookDescriptionModel.java:39,45-47,1368-1387`) → two downloads and a racy write. `getBooks` has branches only for knigavuhe, ABMP3, akniga, baza and audioboo (`:55-66`); otrub/poleknig fetch once per load (`:620,1293`).

### SRC-08 — Medium — Operator-precedence NPE in the knigavuhe other-readers parser (B: SRC-23; verified)

`!title.isEmpty() && A || B` (`J/model/OtherArtistModel.java:582-583`): when the block has no title, `B` evaluates `requireNonNull(title.first())` → NPE. The same loop also adds entries with an empty name and URL when the item has no `<a>` (`:584-594`, verifier).

### SRC-09 — Medium — The other-readers tab is wrong for several sources (B: SRC-24; verified with corrections)

No branch for audioboo/otrub/poleknig (`J/model/OtherArtistModel.java:488-509`) although audioboo sets `otherReader` and shows the tab (`J/model/BookDescriptionModel.java:764-770`, `J/fragment/DescriptionBookFragment.java:351-355`) → an always-empty tab; otrub/poleknig never set the flag, so the missing branch is harmless there. izibuk does no author check, adds the same POJO once per reader block (`:558`) and excludes the current book only by reader name; `otherReader` is set whenever a search returns more than one result (`:1279-1282`).

### SRC-10 — Medium — baza-knig series and search paging (B: SRC-25; verified, understated)

`while(true)` loops (`J/model/SeriesModel.java:266-369`) drop the proxy and `PHPSESSID` on **every** series page, including page 1 (`:268-274` vs `:228-236`); an empty `urlSeries` makes `Jsoup.connect("/page/1/")` throw; an empty result throws a deliberate NPE (`:366-367`); `parseInt` of the last nav item (`:283`, `J/model/OtherSourceModel.java:374`) throws on a text label; no page cap; search pages through all results sequentially (`J/model/OtherSourceModel.java:338-493`).

### SRC-11 — Medium — Comments parsing (B: SRC-26, SRC-31; verified)

`substring` on URLs without `-` or ending in `/` (`J/model/ComentsModel.java:412-413`); unbounded paging (`:416-507`); `get("comments")` returns null → NPE (`:441`); akniga "load more" falls back to an RSS feed parsed as HTML with a hard-coded host and no TLS/proxy set-up, and the HTML parser drops CDATA descriptions (`:371-404`); audioboo avatars point at a third-party placeholder service (`:542`). `ComentsModel` has no branches for izibuk, knigoblud, otrub or poleknig (`:43-55`), so those sources always show no comments.

### SRC-12 — Medium — Cross-source matching dereferences null author/reader; `"null"` ends up in queries (B: SRC-27; verified)

`BookPOJO.autor/artist` default to `null` (`J/pojo/BookPOJO.java:34-35`) but `J/model/OtherSourceModel.java` calls `.length()` on them at 18 sites (`:180…1107`), all inside the outer `try` (`:104`), so one null author discards every other-source result (A's S11 all-or-nothing finding, same mechanism); `"null"` is concatenated into search queries (`:221-222,500-501`). The matching heuristic is copy-pasted nine times.

### SRC-13 — Medium — Queries are hand-concatenated into URLs; relative URLs hand-joined (B: SRC-29, SRC-30 · A: S11; verified with correction)

Titles are concatenated into query strings (`J/model/OtherSourceModel.java:114,221-222,500-501,621,723,941,1041`; `J/model/OtherArtistModel.java:40,119,320,523`; `J/model/BookDescriptionModel.java:1262-1263`). **Correction:** Jsoup 1.21 percent-encodes spaces and non-ASCII itself, so only `&`, `#`, `+` and `?` in titles actually break the query. `okhttp3.HttpUrl` is imported and unused (`:18`). `Url.X + href` is used even where hrefs may be absolute (`J/model/BookDescriptionModel.java:242,387`, `J/model/SeriesModel.java:98`, `J/model/OtherSourceModel.java:653`) and `//host` would become `https://site//host` (`J/model/SeriesModel.java:498,522,593`, `J/model/BookDescriptionModel.java:659`); latent robustness issues — use `absUrl("href")`.

### SRC-14 — Medium — A domain change silently breaks saved books (B: SRC-31; verified)

Parser selection by `contains(Url.SERVER_X)` (`J/model/AudioModel.java:101-121`, `J/model/BookDescriptionModel.java:55-66,83-103`, `J/model/SeriesModel.java:39-57`, `J/model/ComentsModel.java:43-55`, `J/pojo/BookPOJO.java:551-571`) returns empty for an old or unknown host with no message; favorites, history and downloads keyed by old-host URLs stop working. These sites do move (`.ink` vs `.top`). Host-alias table, URL rewrite on load, DB migration.

### SRC-15 — Medium — Per-source chapter extraction defects (B: SRC-08…SRC-17; verified, corrections inline)

- **knigavuhe/izibuk:** `deleteComnets` searches `*/` from index 0, handles an unterminated comment with `substring(…, length+1)` (throws), strips `/*` inside string literals and `replace`s every identical occurrence (`J/model/AudioModel.java:139-151`). izibuk relies on positional tuple indexes and appends `sign` raw (`:1076-1078`).
- **ABMP3:** the proxy is set on `connection` while `connection1` makes the playlist request (`:415-419`); the file URL is cut at the last quote of the whole script (`:403-404`); the JSON playlist is fetched with `.get()` without `ignoreContentType` and read via `body().text()` (`:421-423`) — if the server sends `application/json`, Jsoup throws `UnsupportedMimeTypeException` and the whole load fails (understated by B); relative URLs not resolved; no durations (`:427-433`).
- **poleknig:** `execute()` without `ignoreContentType` on a JSON playlist (`:1214`); `indexOf('"', keyEnd)` takes the next double quote anywhere, so single-quoted values are cut wrongly (`:1179-1193`); `Url.SERVER_POLEKNIG` has no trailing slash, so relative paths without `/` are wrong (`:1203-1205`); nested `folder` playlists not flattened (`:1216-1229`); parsing stops at the first script that mentions `Playerjs` even if it has no `file` key, so a loader script hides the real playlist (`:1167-1196`, verifier); no book name or durations.
- **otrub/audioboo:** the array is sliced to `lastIndexOf(']')` of the whole script (`:241-248,301-308`), so a later array breaks JSON; `fileStart == -1` makes `indexOf('[', -1)` pick the first array; folders not flattened (items with `file ""` unplayable); the `//_//` de-obfuscation removes every marker but leaves the junk segments after them and only fires when the string starts with the marker (`:321-328`); otrub sets no book name (`:215-262`).
- **akniga:** a single bad item (e.g. `getAsInt()` on the string `"12.5"`) truncates the list because the `try` wraps the whole loop (`:539-578`); items without `duration`/`time_finish` keep `timeFinish = -1` and then skip clipping entirely (DL-05); the regex accepts only single quotes around `LIVESTREET_SECURITY_KEY` (`:470`); the first `data-bid` on the page is used (`:476`); `https://akniga.org` is hard-coded (`:485,488,507,510,714`) although `Url.SERVER_AKNIGA` exists, and all akniga requests skip the proxy (`:447-455`); the AES passphrase is hard-coded (`:683`; `res/raw/config.js:54,58`); `hexToBytes` reads `charAt(i+1)` without a length check (`:740-746`); the token-signed `pl.m3u8` URL (`:693-715`) is persisted (`J/presenter/BookPresenter.java:371`) and reused for download/stream without re-signing (expiry behaviour needs runtime).
- **baza-knig:** the custom base64 buffer is `len/4*3` (`:806-815`); unpadded input with `len % 4 ∈ {1,2,3}` writes past the end → `ArrayIndexOutOfBoundsException`, swallowed (`:778,925`) → the single error POJO of SRC-01; the escape→percent-decode round trip (`:817-845`) is redundant; `[`…first `]` slicing truncates nested arrays (`:756-759`); every script with `strDecode("` is decoded and appended regardless of the selected voice (`:903-929`); the fallback is skipped when `#fr` exists but yields nothing (`:1009-1017`); `Integer.parseInt` of everything after `?sorce=` (`:865`, `J/model/BookDescriptionModel.java:978`, `J/pojo/BookPOJO.java:675`) throws on extra parameters; `?sorce=N` is appended blindly to scraped hrefs (`J/model/OtherArtistModel.java:276,289`); the `strDecode` path sets no durations (`:916-921`; the `js-play8` path does, `:988`).
- **archive.org mirror:** no proxy (`:946-950`; default 30 s timeout and platform TLS apply, so B's "no timeout/TLS" is withdrawn); `meta.get("identifier")` NPE for dark items (`:953`); `contains("MP3")` also accepts `VBR MP3`/`64Kbps MP3` derivatives → duplicates (`:960-961`); files unsorted; names concatenated without percent-encoding (`:966`); `array.get(0)` on empty `sources` (`:993`).
- **knigoblud:** `getAsJsonArray("playlist")` NPE and the first `);` ends the JSON (`:1118-1123`); no proxy; no comments.

### SRC-16 — Medium — Nothing is disposed; the same page is fetched up to seven times; other-source search is serial with default timeouts (B: SRC-32, SRC-33, SRC-34 · A: S09, S11, UI-08; verified with corrections)

Empty `onSubscribe`/`onDestroy` in all book-content presenters (`J/presenter/BookDescriptionPresenter.java:52-54,70-71,130-131`, `J/presenter/BookComentsPresenter.java:47-49,65-66`, `J/presenter/BookSeriesPresenter.java:46-48,64-65`, `J/presenter/OtherArtistPresenter.java:46-48,64-65`); models use `Observable.create` without `isDisposed()` checks; `try{onNext}catch{onError}finally{onComplete}` emits `onComplete` after `onError`. A knigavuhe book page is fetched by `LoadBook` (`J/activity/LoadBook.java:62`), twice by `BookDescriptionModel` (SRC-07), and by `AudioModel` (`:155-161`), `SeriesModel` (`:69-74`), `ComentsModel` (`:111-116`) and `OtherArtistModel` (`:572-577`) — five to seven fetches for knigavuhe, fewer for other sources (verifier). The other-source search is **eight** serial Jsoup requests (the current source is skipped) with no `.timeout()` (`J/model/OtherSourceModel.java:36-101`), run on first attach and after deleting an imported book (`J/presenter/BookPresenter.java:212,599`); B's "re-run on every retry" is withdrawn (no retry caller). A per-URL `Document` cache behind one fetch helper; parallel per-source `Single`s with timeouts and `onErrorReturn`.

### SRC-17 — Low — Minor parser items (B: SRC-28, SRC-37, SRC-06; verified)

`AutorsModel` rating parse turns `1.2K` into 12000 (`J/model/AutorsModel.java:217-221`); `end` is never reset (`:24,184-188`; instance created per presenter, so it matters on refresh); `get(size-2)` with one pagination child (`:101`); akniga ld+json description cut at an escaped `\"` keeping raw escapes (`J/model/BookDescriptionModel.java:903-912`); `Node.toString()` used for text (`:376-378`, `J/pojo/BookPOJO.java:73-77,95-99`); `!favorite.equals(0)` compares String with Integer, always true (`J/pojo/DescriptionPOJO.java:64`); `BookPOJO.getDescription(null)` never calls `onComplete` (`J/pojo/BookPOJO.java:546-579`). `ABMP3HttpClient.java` is an empty file; `AudioModel.runScript` (`J/model/AudioModel.java:57-92`) and `BookActivity.decode()` (`J/activity/BookActivity.java:1110-1124`) have no callers; `res/raw/decode.js`, `config.js`, Rhino and `js-evaluator` exist only for them. Presenters hide error reasons (`J/presenter/OtherArtistPresenter.java:73-75`, `J/presenter/BookSeriesPresenter.java:73-76`).

### Coverage per source (B §8.6, verified where cited above)

| Source | Notable weaknesses |
|---|---|
| knigavuhe | player JSON cut with `indexOf` (SRC-03, SRC-15); hand-joined hrefs (SRC-13); other-readers NPE (SRC-08); page fetched 5–7× (SRC-16) |
| izibuk | positional tuples; other-readers list mostly false positives (SRC-09); `child(0)` throws (SRC-06); no comments |
| ABMP3 | playlist bypasses proxy and may fail on content type (SRC-15); `substring(indexOf(" ("))` in matching (`J/model/OtherSourceModel.java:154`); no series |
| akniga | hard-coded passphrase/host, first `data-bid`, error-as-track, partial lists, no chapter ends (SRC-01, SRC-15, DL-05); wrong series selector (SRC-04) |
| baza-knig | base64 overflow, voice-overs concatenated, `?sorce` parsing (SRC-15); archive.org duplicates; paging loses proxy/cookie (SRC-10); only source with challenge detection — then swallowed (SRC-02) |
| knigoblud | series links point to knigavuhe (SRC-04); `child(3)` throws; no proxy; no comments |
| audioboo | Playerjs slicing/obfuscation (SRC-15); series host wrong (SRC-04); other-readers tab always empty (SRC-09); `Consts.getSorceName` returns "Unknown" (`J/Consts.java:343-353`) |
| otrub | Playerjs slicing (SRC-15); hashed class selectors (`._5e0b77`) break on every site rebuild; no book name; no comments |
| poleknig | content-type and quote handling (SRC-15); photo selector requires absolute host (`J/pojo/BookPOJO.java:1218`); no proxy; no comments |

---
## 9. Search, listings and pagination

The multi-source search (`J/presenter/SearchbalePresenter.java`) fans out to up to nine sources through one shared `BooksModel` (`J/model/BooksModel.java`, 2,261 lines); catalog listings go through `BooksPresenter`/`BooksModel`/`GenreModel`/`AutorsModel`. "Fast search" is undermined less by raw speed than by orchestration correctness: stale results, endless paging, corrupted URLs and swallowed errors. A's runtime note stands: a `1984` search on the emulator did return results from six sources, so the first page of a simple query works.

### SRCH-01 — High — A new query is ignored while a search runs, and stale results leak into it (A: S03 · B: SR-01)

`loadBoks` clears the lists and calls `loadNext`, which is guarded by `!isEnd && !isLoading` while the previous run keeps `isLoading` true (`J/presenter/SearchbalePresenter.java:275-323`); previous observers append into the shared `books` field (`:611-613`) and the author/series callback overwrites the chips without checking the query (`:758-759`); every `Disposable` is dropped (`:616-617,762-764`). Submit A, then B within ~20 s: B's request never starts and A's late rows fill B's cleared list. One generation-scoped run with cancellation and generation-tagged callbacks.

### SRCH-02 — High — Pagination rewrites the query text and numeric ids by global digit replacement (A: S02 · B: SR-04; verified)

`url.replace(String.valueOf(page), String.valueOf(temp))` (`J/model/BooksModel.java:61,123`; `J/model/GenreModel.java:59,62`) is applied to URLs that already contain the URL-encoded query (`J/presenter/SearchbalePresenter.java:116-119,327-328,495-500`). A reproduced: `?q=1984&page=1` → `?q=2984&page=2`; encoded `Толстой` → `Тол҂҂ой` (`%D1%81%D1%82` → `%D2%82%D2%82`); `/author/123/?page=1/` → `/author/223/?page=2/`. The knigoblud variant `url.replace("/" + page, "/" + temp)` (`:77-79`) rewrites UUID path segments (`/120a1664-…` → `/220a1664-…`, verifier). One UI page loads four provider pages, so this hits the first search. Build URLs from templates with an explicit page parameter.

### SRCH-03 — High — Routing by `url.contains(String.valueOf(page))` sends Cyrillic ABMP3 author searches to the wrong parser (B: SR-05; verified)

`J/model/GenreModel.java:55-72,79-82` (inherited unchanged by `AutorsModel`, `:22`): an encoded Cyrillic query contains `1`, so on page 1 the request takes the four-iteration listing branch; its `"?page=1/"` replace never matches, so the same URL is fetched four times and the author-search parser never runs.

### SRCH-04 — High — Every source returns sentinel "⚠" books; the list never ends, errors look like books, and the empty state never shows (A: S04 · B: SR-02, SR-03; verified with corrections)

Each empty or failed source emits a non-empty sentinel `BookPOJO` with URL `about:blank` (`J/model/BooksModel.java:144-149,168-173,189-194,244-249,264-269,286-291,308-313` and inside parsers `:492-497,808-813,1285-1290,1418-1423,1535-1540,1729-1734,1859-1864,2024-2030,2252-2257`); `.timeout(25 s).onErrorResumeNext(...)` (`J/presenter/SearchbalePresenter.java:549-553`) swallows every remaining error, so the observer's `onError` with the cookie prompt and "disable broken source" logic (`:589-608`) is dead code; source exhaustion flags are only cleared there, so an enabled source stays enabled forever and every scroll near the bottom (`J/activity/SearchableActivity.java:181-184`) queries all nine sites again and appends more warning rows. Sentinels are clickable and favouritable (`:266-272,411-424`; `J/presenter/BooksPresenter.java:195-208,659-661`); the empty state uses `books.isEmpty()` (`:488-491,745-749`), so it never shows; filtering by provider removes them (`:786-792`). **Correction (verifier):** a baza-knig challenge does **not** show a timeout badge — its `///qery=` branch catches, logs and returns an empty list (`:205-213`); the timeout badge appears only on a real timeout; other sources build their own "⚠ Source: msg" badges inside the model. Return typed per-source results (`books`, `hasMore`, `status`); render errors outside the data list.

### SRCH-05 — High — Audioboo listings always return page 1; audioboo authors/performers are always empty (A: S05 · B: SR-22; verified)

`/page/N/` is stripped (`J/model/BooksModel.java:1915-1921`) and `parceAudioboo` ignores `page` (`:2021-2138`); A reproduced that `/page/1/`…`/page/4/` all become `https://audioboo.org/`, so every card appears four times per scroll, forever. The authors/performers screens route through the four-iteration branch of `GenreModel` (`J/presenter/MainPresenter.java:127-129,155-157` → `J/model/GenreModel.java:55-72`), which has no audioboo case and returns empty lists; `AutorsModel.loadBooksListAudioboo` (`:341`) is never reached.

### SRCH-06 — High — baza-knig search ignores the page, duplicates results and truncates queries at `&` (A: S06 · B: SR-07; verified)

Four filter URLs (`/f/nazvanie=…`, `/f/cikl=…`, reader, author) carry no page and are always fetched as page 1 (`J/model/BooksModel.java:197-215`); the `?do=search` POST (`:839-849`) and `&page=` branch (`:216-229`) are unreachable because the template always contains `///qery=` (`J/presenter/SearchbalePresenter.java:121`); the raw query is cut at `&` (`:199`; A: `War & Peace` → `War `). One book can appear four times on page 1 and again on every scroll.

### SRCH-07 — High — akniga author/performer search double-encodes the query (A: S07 · B: SR-08)

`URLEncoder.encode(query)` is placed in the URL (`J/presenter/SearchbalePresenter.java:333,495-500`), extracted without decoding (`J/model/GenreModel.java:50-53`) and posted via `.data("sText", …)` (`:388`), which form-encodes it again: non-ASCII queries arrive as literal `%D0%A1…` and find nothing.

### SRCH-08 — High — Listing pagination: NPE means "end of list"; partial pages are duplicated; one unparsable card makes a page permanently unloadable; failed refresh disables scrolling (B: SR-19, SR-20, SR-21, SR-23 · A: S12; verified with corrections)

An NPE silently sets `isEnd` (`J/presenter/BooksPresenter.java:721-722,768-769`), so a real parser NPE (`.first()` returning null) ends the list silently, while real empty pages emit empty lists and never end (poleknig has no end detection at all, `J/model/BooksModel.java:2152-2156`). **Correction:** `J/model/BooksModel.java:321-323` belongs to the search `getBooks` and is unreachable from `BooksPresenter`; `.child(0)` throws `IndexOutOfBoundsException`, not NPE. Four sub-pages are emitted one by one and then `onError` (`:58-97`); the presenter decrements `page` (`:723-726`) and the next scroll refetches all four → duplicates. `BookPOJO.setName("")` throws (`J/pojo/BookPOJO.java:1163-1166`) and no listing has a per-book `try` (`J/model/BooksModel.java:402,579,1186,1334,2042,2048`), so one bad card fails the page, the presenter rolls back and the same page is requested again forever. `isRefreshing` is reset only in `onNext` (`:666,716-738`) and `loadBoks` requires `!isRefreshing` (`:106`), so a failed pull-to-refresh disables infinite scroll until a later refresh succeeds. On `CookesExeption` the presenter neither decrements `page` nor sets `isEnd`, so that page is skipped (`:717-720`, verifier).

### SRCH-09 — Medium — akniga's `end` flag survives refresh and unrelated searches (A: S08 · B: SR-06)

`J/model/BooksModel.java:44,613-623` sets an instance flag when the navigation block lacks a next button and returns before parsing on the next such response; neither `getBooks` overload resets it (`:49-55,108-115`); search reuses one model for the screen (`J/presenter/SearchbalePresenter.java:155`), so after one akniga search reaches its last page, later single-page queries return nothing from akniga. `AutorsModel` repeats the pattern (`:24,183-188`).

### SRCH-10 — Medium — First results wait for the slowest provider; all-disabled waits 60 s; timeouts do not bound latency; case-retry triples the work (A: S10, T06 · B: SR-10, SR-11, SR-12; verified)

Results are appended without publishing (`J/presenter/SearchbalePresenter.java:611-613`) and committed only when every source has finished (`:568-575`) or the 60 s watchdog fires (`:52-61,559-565`); with all sources disabled nothing completes synchronously (`:543-548,629-643`), so the spinner runs 60 s and ends with a misleading "took too long" toast. `timeout(25 s)` is per emission and disposes only the emitter — the blocking `create` lambda keeps running (`J/model/BooksModel.java:117-131,318`; Jsoup default 30 s, no `.timeout()` at `:339-344`); knigavuhe emits after each of four sequential fetches (~100 s worst case); with `search_multithreading=false` sources run serially on `Schedulers.single()` (`:623-627`), so the watchdog usually decides. "No real result" (which includes all-timeout) triggers up to two more full nine-source searches, re-runs the author/series fetch and rewrites `query` (`:492,506-541`). Late sources of round N increment round N+1's `exit` counter. The retry re-shows the progress indicator (`:322`) in the same main-thread message in which the commit hid it (`:486`), so bounded retries look like one endless spinner; and a sentinel-only list long enough to scroll re-triggers the retry on every scroll, resetting `page` to 0 (`:313`). An exception inside the commit freezes the state machine permanently (SRCH-16 in §16). Publish per-source results incrementally with a bounded executor; complete empty fan-out synchronously.

### SRCH-11 — Medium — Work outlives the screen; the watchdog retains the presenter (A: S09 · B: SR-13)

`onDestroy` only closes the database (`J/presenter/SearchbalePresenter.java:175-178`); the watchdog runnable captures the presenter (`:52-62,563-564`) and the chain watchdog → commit → retry → `loadBoks` (`:492,540`) can start a new nine-source search 60 s after the user left; models loop without `isDisposed()` checks (`J/model/BooksModel.java:55-103,114-330`); `BooksPresenter.java:744-746,791-793` also discards subscriptions; no `RxJavaPlugins.setErrorHandler`, so late errors after disposal reach the uncaught handler (APP-01).

### SRCH-12 — Medium — No deduplication, round-robin re-sort of the whole list, adapter list mutated by reference (B: SR-09, SR-14 · A: S04; verified)

`addAll` without a key (`J/presenter/SearchbalePresenter.java:612`; `J/presenter/BooksPresenter.java:736`); the accumulated list is re-interleaved on every commit (`:484,663-693`), so rows above the viewport move when page 2 arrives; no relevance ranking. `BooksListAddapter` holds the presenter's list by reference (`J/adapter/BooksListAddapter.java:389-392`), which grows without notification until commit; an append-only change is unlikely to raise "Inconsistency detected", so B's crash claim is downgraded (verifier).

### SRCH-13 — Medium — ABMP3 search makes N+1 requests; author/series chips are all-or-nothing and duplicate the book fetch; unsupported sources silently fall back to izibuk (B: SR-15, SR-16, SR-17 · A: S11; verified)

Default `speed_up_search_abmp3=false` path: search page plus seven book pages sequentially, re-sliced on the next page (`J/model/BooksModel.java:1309,1320-1349`; `J/AutorsSearchABMP3.java:46`). Chips: sequential knigavuhe→ABMP3 (`J/model/SearchebleModel.java:35-64`), one `catch` aborts both (`:60-62`), throwing setters in `J/pojo/SearchebleArrayPOJO.java:13-19`, and the same pages are fetched again by the book search (`J/model/BooksModel.java:123,157`). Author/performer search for unsupported sources (including ABMP3 performers) falls back to izibuk URLs (`J/presenter/SearchbalePresenter.java:127-146`).

### SRCH-14 — Medium — Fragile listing parsers and inconsistent per-source menus (B: SR-25, SR-26, SR-27 · A: S12; verified)

`substring(0, indexOf(" "))` on akniga comment counts like `5` (`J/model/BooksModel.java:787`); uncaught `parseInt` on `1.2K` ratings and page text (`:526,865`; `J/model/GenreModel.java:131,222,419`; `J/model/AutorsModel.java:46,104,275`); end-of-pagination heuristics that empty page 1 when the pager's last child is `→` or has one child (`:350-358,1074-1101,1563-1583`); hashed CSS class selectors for izibuk/otrub (`:1120`, `:1782-1848,1878`; `J/model/GenreModel.java:251,585-600`); throwing `GenrePOJO` setters (`J/pojo/GenrePOJO.java:26-52`); navigation URLs built from the current page URL including its page parameter (`:371,467,1142,1229,1255`). ABMP3 inflates the izibuk menu and `onPrepareMenu` re-shows items by global source, undoing the URL-based hide (`J/fragment/BooksFragment.java:193-194,213-224,280-282`; `res/menu/books_izibuk_options_menu.xml`); poleknig performers do nothing (`J/presenter/MainPresenter.java:138-162`); hard-coded genre maps (`J/model/GenreModel.java:329-353,474-565`).

### SRCH-15 — Medium (raised from Low after user report 7, see SRCH-17) — `ACTION_SEARCH` delivered to a new activity instance is ignored; queries logged; Moxy strategies replay one-shot events (B: SR-18, SR-28 · A: S13; verified)

Only `onNewIntent` handles the search intent (`J/activity/SearchableActivity.java:80-241,273-289`); the activity is `singleTop`, so a cold launch or an instance that is not on top loses the query. This is one of the two reasons voice search does nothing (SRCH-17 in §16). Queries/URLs are logged in release (`J/presenter/SearchbalePresenter.java:545,565,591`; `J/model/BooksModel.java:121,1960-1990`; `:1064` serialises the DOM to log its length) and no `assumenosideeffects` rule strips them. `showToast`/scroll-to-top use `AddToEndSingle` and replay after rotation (`J/interface_pacatge/books/BooksView.java:35-39,44-45`; `J/interface_pacatge/searchable/SearchableView.java:32-36`); `showProgres`/`showTitle` default to `AddToEnd` (`J/interface_pacatge/book_content/Activity.java:30,35`).

### Missing for "fast search" (B §9.3, agreed)

No HTTP cache; genres scraped on every open (`J/model/GenreModel.java:111-327`); no local search over favorites/history/downloads; no debounce or suggest-as-you-type (`MySuggestionProvider` exists but is history-only); no per-source health or mirror state; no relevance ranking; no merge of the same book across sources.

---
## 10. UI, lifecycle, accessibility, TV and settings

### UI-01 — High — Views destroy retained Moxy presenters: Favorites and Downloads break after Back or rotation (A: UI-02, Q02 · B: UI-01)

Ten view classes call `onDestroy()` on their presenter manually (PLAY-03). `FavoriteFragment` does it from `onDestroyView` (`J/fragment/FavoriteFragment.java:263-270`) and `FavoritePresenter.onDestroy()` nulls `mContext` (`J/presenter/FavoritePresenter.java:177-194`); the fragment survives on the back stack, so after Favorites → Settings → Back the Saved filter/sort crashes on `mContext.getString` (`:393,416,500`) and saved deletion skips the `mContext != null` block while still removing the row (`:335-350`). `ClearSavedActivity` does it on every destruction (`J/activity/ClearSavedActivity.java:91-99`); `ClearSavedPresenter.onDestroy()` disposes and nulls `mModel`/`mContext` (`J/presenter/ClearSavedPresenter.java:174-181`), and the exact Moxy 1.7.0 `MvpAppCompatActivity` retains the presenter across a configuration change (A verified against the dependency source), so after rotation updates stop (`:205-209`), pause/resume/restart NPE on `new Intent(null, …)` (`:183-201`) and `remove()` hides a row without deleting anything (`:106`). Let Moxy own presenter lifetime; keep only view cleanup in `onDestroyView`/`onDestroy`.

### UI-02 — Medium — `MainActivity.onDestroy` stops paused playback on rotation and when a stacked instance closes (B: UI-02; verified)

`J/activity/MainActivity.java:487-497` broadcasts `CloseIfPause` and cancels the player notification with no `isFinishing()`/`isChangingConfigurations()` guard; the manifest has no `orientation` in `configChanges` (`:144-148`); `DescriptionBookFragment` opens extra `MainActivity` instances without launch flags (`J/fragment/DescriptionBookFragment.java:420-435`, `:99-115`), so pressing Back out of a stacked instance stops a paused session (`J/service/MediaPlayerService.java:368-376`).

### UI-03 — Medium — Navigation state is kept in an unsaved parallel stack; "service bound" is reset through the wrong `onSaveInstanceState` (A: UI-05 · B: UI-03, UI-04; verified)

`fragmentsTag` is re-created empty in each `onCreate` (`J/activity/MainActivity.java:305-306`) while the real back stack is restored; it decides whether a transaction is added to the back stack (`:419-432`) and which tags are popped (`:344-350`); Settings/About push untracked entries (`J/fragment/SettingsFragment.java:184-198`); `commit()` after state save can throw on API < 28 (`:432`, B). `BookActivity` overrides the `PersistableBundle` variant of `onSaveInstanceState`, which is never called for ordinary activities (`J/activity/BookActivity.java:1178-1183`), while `onRestoreInstanceState` writes `false` into the retained presenter (`:1185-1189`); the next chapter tap takes the start-and-bind path again, accumulating bindings with PLAY-02. One source of truth for navigation; drop the manual flag.

### UI-04 — High — `LocaleManager` overwrites `Configuration.uiMode` with AppCompat night constants and mutates the live configuration (A: UI-03 · B: UI-23; verified)

`J/LocaleManager.java:37-60` takes `context.getResources().getConfiguration()` (the live object), calls `Locale.setDefault`, and sets `configuration.uiMode = AppCompatDelegate.MODE_NIGHT_YES/NO` (2/1), which as `uiMode` means type DESK/NORMAL with night undefined, destroying the television type bits. It is applied through `createConfigurationContext` in every activity's `attachBaseContext` and in `App.attachBaseContext` (`J/App.java:41-47`), while code still branches on `UiModeManager == TELEVISION` (`J/activity/BookActivity.java:485-486,718`). `layout-television/*` and `-night` resource selection can disagree with the Java branches. Not rendered on a TV in either review. Modify only the night bits on a copy, or use `AppCompatDelegate.setDefaultNightMode` and `setApplicationLocales`.

### UI-05 — Medium — The language setting is a no-op; the app is Russian-only with hard-coded strings (B: UI-24 · A: "Unused language machinery"; verified)

`LocaleManager.getPersistedData` ignores its `defaultLanguage` parameter and always falls back to `ru` (`J/LocaleManager.java:30-35`); `res/xml/preferences.xml` has no `pref_lang`; `settings_lang_*` arrays are unreferenced (`res/values/arrays.xml:16-26`); only `values`, `values-sw600dp`, `values-sw720dp` and `values-w820dp` exist. Russian literals in Java at `J/activity/BookActivity.java:653-659`, `J/activity/ClearSavedActivity.java:270-367`, `J/activity/MainActivity.java:382-393` (`:314` is an English `OK`), `J/fragment/SettingsFragment.java:520-598`, `J/adapter/DownloadAdapter.java:189-217`, `J/util/DownloadStorageHelper.java:76-90`. Decide Russian-only or implement locales properly.

### UI-06 — High — The equalizer assumes five bands and always-available effects (A: UI-10 · B: UI-11)

`Settings.seekbarpos = new int[5]` (`J/android_equalizer/Settings.java:6`) while loops run to `getNumberOfBands()` (`J/android_equalizer/DialogEqualizerFragment.java:160-169`) → `ArrayIndexOutOfBoundsException` on 6+ band devices; `getCenterFreq(4)` (`:321-322`) throws on fewer; effect construction (`:143-155`) is unguarded (the caller's `try` in `J/activity/BookActivity.java:216-226` runs before the fragment's `onCreate`); `:305-312` hard-codes five UI bands; opening the dialog unconditionally enables equalization (`:133`). The service's own loop has the same range risk, caught there (`J/service/MediaPlayerService.java:236-238`). The dialog disables the service's effects by broadcast, creates its own on a session id captured once and re-enables on destroy (`:126-128,134,527-532`); `EqualizerFragment`'s UI is never instantiated although its statics are used; labels are hard-coded (`:221-222,322,476,509`). Query capabilities; size bands dynamically; catch and degrade.

### UI-07 — Medium — Sleep timer: "end of chapter" ignores playback speed; timer can show "running" with no service; display wraps at 24 h (B: UI-10; verified)

Remaining media time (`J/service/MediaPlayerService.java:265-276`) is counted in play-time wall clock (`J/presenter/SleepTimerPresenter.java:63-70`; the `Hourglass` pauses with playback but ignores speed, `:318-347,486-492`), so at 1.5× it stops a third into the next chapter; starting the timer while the service is not running flips a static flag and shows the stop icon while nothing receives the broadcast (`:57-84`); hours wrap at 24 although six digits can be entered (`J/activity/SleepTimerActivity.java:184,47`); the "end chapter" button is identified by label text (`:165`). The fast-countdown defect is PLAY-10.

### UI-08 — Medium — Lists and adapters: full refreshes, per-bind I/O, proxy covers into recycled rows, positional clicks, unchecked `NO_POSITION` (A: UI-07, UI-11 · B: UI-13, UI-14, UI-15, UI-18; verified)

`notifyDataSetChanged` without DiffUtil (`J/adapter/BooksListAddapter.java:389-397,537-541`; `J/adapter/AudioAdapter.java:146-155,188-197,369-395`); `preloadStatuses` re-scans every book (DB plus `File.exists`) on every `setItem`, on an executor (`:400-543`). `File.exists()` per bind (`J/adapter/AudioAdapter.java:252-255`, `J/adapter/DownloadAdapter.java:149-150`), a new `Bitmap` per bind (`:300-339`), double inflation and DB models per ViewHolder (`J/adapter/BooksListAddapter.java:130-131,369-383`), a raw thread per `refresh()` on every `UpdateAdapter` broadcast and on `onResume` (`J/adapter/AudioAdapter.java:109-135`; `J/activity/BookActivity.java:1088-1091,1305`). Proxied baza-knig covers are loaded manually with no cancellation, caching, downsampling, stream close or binding check (`J/adapter/BooksListAddapter.java:147-164`; `J/adapter/BooksOtherAdapter.java:81-93`; `J/fragment/DescriptionBookFragment.java:242-259`), and the adapter's single executor also runs status preloads, so a stalled cover blocks them; both Picasso and Glide are dependencies. Click listeners capture bind-time positions (`J/adapter/AudioAdapter.java:225-237,321,349`); `getBindingAdapterPosition()` is forwarded unchecked into list indexing (`J/adapter/BooksListAddapter.java:114-125` → `J/presenter/FavoritePresenter.java:197-202`, `J/presenter/BooksPresenter.java:659-660`; `J/adapter/BooksOtherAdapter.java:103-120` → `get(-1)` at `:133-137`); one item with a null name/URL crashes the list (`:136-138`); `DownloadAdapter` and `FilterAdapter` already check `NO_POSITION` (A).

### UI-09 — Medium — Comment expansion is measured before layout; recycled state not reset (A: UI-12 · B: UI-18; verified)

`J/adapter/ComentsListAddapter.java:90-109` and `J/adapter/ComentsListBazaKnigAddapter.java:91-110` set text then immediately read `getLineCount()` (zero or stale before layout), hide "Read more" accordingly, and reset `showMore` without resetting the label or the avatar (`:69-71`). A recycled row can say "Show less" while collapsed; the full text can be unreachable.

### UI-10 — Medium — Swipe-to-delete is immediate, un-undoable and deletes files on the main thread; no empty/error/loading states (A: UI-14 · B: UI-18, UI-20; verified)

`J/fragment/FavoriteFragment.java:214-219` removes a swiped book and toasts success immediately; `J/presenter/FavoritePresenter.java:321-349` runs `deleteBookFiles` (DATA-01) synchronously on the main thread; the same gesture ranges from un-favouriting to deleting local audio. `res/layout/fragment_favorite.xml`, `fragment_books.xml` and `activity_clear_saved.xml` have no empty/error views; `showProgres` and `setTimeEnd` are empty in `BookActivity` (`J/activity/BookActivity.java:1105-1107,1166-1168`); the rating flow is a stub although `ReviewManager` is imported (`:93-94,1100-1102`); pull-to-refresh is disabled on TV (`J/fragment/BooksFragment.java:330-334`); an empty other-readers result is shown as a load error (`J/fragment/OtherArtistFragment.java:126`, `J/fragment/OtherSourceFragment.java:66-67`); errors are toasts with raw exception text and no retry.

### UI-11 — Medium — ViewPager2 tabs are inserted by position without stable ids (A: UI-06 · B: UI-17)

`J/adapter/SectionsPagerAdapter.java:20-89` does not override `getItemId`/`containsItem`; `J/activity/BookActivity.java:914-934` inserts Series/Other-reader tabs before an already-created Other-source tab when the description request finishes after the other-source request (`J/fragment/DescriptionBookFragment.java:335-354`, `J/presenter/BookPresenter.java:687-695`); `setArtistPOJO` changes adapter data without updating the existing fragment's arguments; manual `addTab` duplicates `TabLayoutMediator`. Conditional on request order.

### UI-12 — Medium — `DescriptionBookFragment` keeps its destroyed view tree and can loop on `recreate()` (A: UI-09 · B: UI-06; verified)

Numerous view fields plus the adapter (`J/fragment/DescriptionBookFragment.java:61-83`, populated `:122-144`) are never cleared in `onDestroyView` (`:198-202`). Any exception while binding the description calls `recreate()` (`:444-448`) and the `AddToEndSingle` strategy re-delivers the same POJO (`J/interface_pacatge/book_content/Description.java:19-20`), so a deterministic exception loops; B's specific `getFavorite().equals` NPE cannot occur (fields default to `"0"`, setters reject null — verifier).

### UI-13 — Medium — Activity receivers live from `onCreate` to `onDestroy`; the equalizer dialog fails silently while stopped; Moxy command queues grow (B: UI-07, UI-05 · A: "Findings excluded"; verified)

`J/activity/BookActivity.java:442-479,969-976` and `J/activity/SleepTimerActivity.java:84-100,220-258` keep receiving two broadcasts per second while stopped; stacked `BookActivity` instances all update; the equalizer receiver's `DialogFragment.show()` after `onSaveInstanceState` throws `IllegalStateException`, which is swallowed (`:211-229`). They are unregistered (A), so this is wasted work, not a leak. `showProgres`/`showTitle` use the default `AddToEnd` strategy (`J/interface_pacatge/book_content/Activity.java:30,35`), so the command queue grows per chapter and is replayed on each `onStart` (low impact: `showProgres` is a no-op).

### UI-14 — Medium — Accessibility: play/pause and timer buttons always announce "Play"; download-state icons unlabeled; equalizer close button unnamed; equalizer labels in `dp` (A: UI-13, LINT-UI-01, LINT-UI-02, LINT-UI-03 · B: UI-21; verified)

`res/layout/player.xml:102-106,203-207` label both play controls `@string/play` while `J/activity/BookActivity.java:1170-1176` swaps only the icon; `J/activity/SleepTimerActivity.java:118-123` likewise; `res/layout/books_recycler_item.xml:89-98` suppresses `ContentDescription` on the download-status icon that distinguishes partial/complete; `res/layout/dialog_fragment_equalizer.xml:17-26` declares a clickable close `ImageView` with no description (`J/android_equalizer/DialogEqualizerFragment.java:186-187`); its five frequency labels use `android:textSize="10dp"` (`:118,147,176,205,234`), ignoring font scale; the title is not constrained between the switch and the close action (`:28-35`); player `ImageButton`s are `wrap_content` borderless with 24 dp drawables and no `TouchDelegate` (A: measure before claiming a 48 dp failure); notification action titles are English literals (SESS-06).

### UI-15 — Medium — Streamed-only listening progress is hidden by the download gate (A: UI-15; verified)

`J/adapter/BooksListAddapter.java:420-422` computes progress only inside `if (procent && saved && audioListModel != null)`, where `saved = dbModel.inSaved(url)`; streamed books do have audio-list rows (`J/presenter/BookPresenter.java:362-376`), so the Saved gate is the only reason history shows no progress. Derive progress from position and duration regardless of download membership.

### UI-16 — Medium — TV, tablet and keyboard specifics (B: UI-19, UI-25, UI-26; verified)

`KEYCODE_3` means "next chapter" and PLAY/PAUSE both toggle (`J/Consts.java:207-232`), applied in `dispatchKeyEvent` before views (`J/activity/MainActivity.java:500-508`, `J/activity/SearchableActivity.java:502-510`, `J/activity/BookActivity.java:1323-1336`), so typing `Метро 2033` on a hardware/TV keyboard skips a chapter, and every key press on the book screen calls the `getRunningServices` IPC (`:234-245`). `isTablet` is 1 for all `sw600dp` and 2 for `sw720dp` (`res/values-sw600dp/dimens.xml:3`, `values-sw720dp/dimens.xml:3`) but only `layout-sw600dp-land` has a non-sheet player and sheet callbacks are wired only when `isTablet == 0` (`J/activity/BookActivity.java:374,485-632`): tablets in portrait get an unwired bottom sheet. `TvSeekBar` discards left/right seeks unless Center is pressed and keeps a stale `userProgress` (`J/TvSeekBar.java:34-77`, contrary to the comment at `J/activity/BookActivity.java:718-729`); focus fixes match the Russian label `Перейти вверх` (`J/activity/SearchableActivity.java:349-353`) and ViewPager2's internal `"f"+id` tag (`:1263`); unguarded `ACTION_VIEW` for a browser crashes on devices without one (`J/fragment/AboutFragment.java:79-88,138-147`; `J/presenter/BookPresenter.java:287-290` → `J/activity/BookActivity.java:1208-1211`).

### UI-17 — Medium — Settings entry points missing; the cookie screen reports success on every destroy and can erase a working session (A: S13 · B: UI-28; verified)

Click handlers for `cookies` and `download` exist (`J/fragment/SettingsFragment.java:416-419,479-482`) but `res/xml/preferences.xml` has no such keys: the Downloads screen is reachable only from the download notification (`J/NotificationDownload.java:69`), the Cookies screen only through an izibuk link filter — and it loads baza-knig (`J/activity/ActivityCookies.java:51,64`). `onDestroy` reads, persists and toasts "Cookies обновлены" regardless of page load or challenge success, including on rotation (`:67-78`); `getCookie` returns `""` when `PHPSESSID` is missing, so leaving early overwrites the stored working session with an empty value; the parser uses substring name matching and unrestricted `split("=")` (`:113-117`, throws on an empty value); the WebView is never destroyed; only `PHPSESSID` is copied into Jsoup (`J/model/BooksModel.java:835-837`). Save only after verified access; preserve the previous session on failure.

### UI-18 — Medium — First-launch dialog pile-up and permission requests without rationale (A: visual supplement · B: UI-29; A runtime-observed, B partially corrected)

A's emulator run showed the notification permission prompt layered over the Terms dialog, followed by parental-control and battery-optimisation dialogs: four decisions before the catalog. `POST_NOTIFICATIONS` is requested in `onCreate` with no result handling (`J/activity/MainActivity.java:233-239`); the battery nag has a "don't show" option (`:312-313`, so B's "every cold start" is overstated); a non-cancelable privacy dialog with only "Yes" (`:145-180`) and the crash-report dialog (`:243,365-401`) compete; all-files access is requested to choose a folder (`J/fragment/SettingsFragment.java:499-516`; B's `:201-212` is the restore launcher). Sequence one decision at a time and request permissions in context.

### UI-19 — Low — Theme set after `setContentView`; prefs re-read in `getTheme()`; presenters own dialogs; Picasso targets held weakly; dead classes; deprecated system-UI flags (B: UI-08, UI-22, UI-30; verified with corrections)

`setTheme()` after `setContentView()` (`J/activity/BookActivity.java:376` vs `405-409`; `J/activity/MainActivity.java:241` vs `258-262`; `J/activity/SearchableActivity.java:84` vs `91-95`; `J/activity/LoadBook.java:34` vs `41-45`) is redundant rather than broken because the `getTheme()` overrides apply the style during inflation anyway (`:1222-1240`, `:455-473`), but they re-read SharedPreferences on every call; a theme change both sets night mode and calls `recreate()` (`J/fragment/SettingsFragment.java:320-329`). Presenters build `BottomSheetDialog`s (`J/presenter/BooksPresenter.java:150`, `J/presenter/SearchbalePresenter.java:361`, `J/presenter/FavoritePresenter.java:209`) → `WindowLeaked` on rotation. Anonymous Picasso `Target`s for the home-screen shortcut (`J/activity/BookActivity.java:1025-1072`; `setDynamicShortcuts(singletonList)` wipes other shortcuts at `:1045`) and the full-screen image (`J/activity/ImageFullScreenActivity.java:107`, `Palette.generate()` on main at `:181-182`) can be collected before delivery. `PopupClearSaved` and `MenuBSBooks` are unreferenced; `setSystemUiVisibility` is deprecated (`:1302-1304`, `:66-69,145-168`); about ten layouts use `fitsSystemWindows`, so B's "only `ClearSavedActivity` handles insets" is overstated, but edge-to-edge under target 35 still needs a pass; the crash file is read on the main thread at start (`J/activity/MainActivity.java:365-379`); the storage scan restarts on every `ClearSavedActivity` creation including rotation (`J/activity/ClearSavedActivity.java:158`); the feedback form's send button is never disabled and `executor.shutdown()` still lets the callback run after destroy, and a background exception leaves the spinner forever (`J/activity/ActivitySendEmail.java:141-238,216-221`).

### Design observations (B §12.6 · A visual supplement; agreed)

- **Information architecture:** downloads manager, cookies and storage maintenance are hidden; "Saved" is a filter inside Favorites; no single Library with *Continue listening*, *Downloaded*, *Favorites*, *History*.
- **Player:** no chapter progress in the list, no total-book progress, no bookmarks, no buffering indicator distinct from "loading"; the speed picker is a raw `SeekBar` with no presets or lower bound.
- **Catalog rows (A, from screenshots):** unexplained bare numbers (`1.2K`, `774`) next to labeled comment counts; genre/narrator/author/series styled as links while only the whole row is clickable (`J/adapter/BooksListAddapter.java:114-125`); fixed 115 dp rows with 12–14 sp text and many metadata fields (`res/layout/books_recycler_item.xml:6`, `res/values/dimens.xml:9`) — test 200 % font scale before redesigning.
- **Feedback:** errors are toasts with technical text; success toasts appear even when an operation silently failed (deletes, cookies, restore).
- **Positive (both):** OnBackPressedDispatcher and Activity Result API are used; no `AsyncTask`/`ProgressDialog`/`LocalBroadcastManager`; newer screens use ViewBinding and DiffUtil; TV layouts set `nextFocus*`; every `PendingIntent` is `FLAG_IMMUTABLE`; the Akniga catalog and a six-source search rendered correctly on A's emulator.

---
## 11. Build, release, manifest and platform

### BUILD-01 — High (release blocker) — Release and debug builds are signed with the developer's debug keystore (A: C15 · B: §16)

`app/build.gradle:17-28` defines a `release` signing config pointing at `~/.android/debug.keystore` with the standard debug alias/password; `:46`, `:58` and `:61` apply it to the default config, release and debug. v3/v4 signing is disabled (`:25-26`). A's executed check: `apksigner verify --print-certs` on the locally built release APK reports `CN=Android Debug, O=Android, C=US`. The published signing identity is not in the repository. Supply a protected upload key through local/CI secrets and fail the release build without it.

### BUILD-02 — High (if publishing a phone update) — `targetSdk 34` is below the current Play requirement (A: C16 · B: §16)

`app/build.gradle:40-41`: minSdk 23, targetSdk 34, compileSdk 36. Google Play requires API 36 for new phone apps and updates from 31 August 2026 (A, official policy page; TV has a different threshold). Moving to 35/36 is not a number change: edge-to-edge enforcement (only `ClearSavedActivity` handles insets, B: UI-30), the 6-hour daily limit for `dataSync` foreground services used by `ExoDownloadService` (B), receiver export flags and background-start restrictions all need work and testing.

### BUILD-03 — High — The packaged Conscrypt native libraries are 4 KB-aligned (A: N01 · B: lint `Aligned16KB`)

A inspected the built debug and release APKs: all three `libconscrypt_jni.so` (arm64-v8a, armeabi-v7a, x86_64, from `org.conscrypt:conscrypt-android:2.5.2`, `app/build.gradle:123`) have `p_align = 0x1000`, and the 64-bit ABIs' second `PT_LOAD` segments are not congruent modulo 16 KB; `zipalign -c -P 16` passes because ZIP alignment is a different property. `J/App.java:277-280` wraps provider installation in `catch (Throwable)`, so an unconditional start-up crash on a 16 KB device is **not** established. Conscrypt 2.8.0 is available (B). Reassess whether an explicit Conscrypt provider is needed at all once SEC-01 is fixed; otherwise update and re-verify ELF alignment on the final artifact.

### BUILD-04 — High — The merged manifest lacks `PlatformScheduler`'s job service and the download-service RESTART filter (A: N02 · B: DL-02)

Covered in DL-02. A verified both merged manifests (debug and release); B verified that the Media3 AAR manifest does not declare the service either, so no transitive merge supplies it.

### BUILD-05 — Medium — Dependency, verification and reproducibility gaps (A: C19 · B: §16, SRC-37)

No unit or instrumentation tests (`testDebugUnitTest` NO-SOURCE), no CI, only the version-17 Room schema exported; broad keep rules and suppressed warnings in `app/proguard-rules.pro:5-49`; no Gradle distribution checksum or dependency verification; JitPack enabled for all groups without a content filter (`build.gradle:16`); `exclude group: 'androidx'` on `material` and the deprecated `lifecycle-extensions` (`app/build.gradle:76-83`) — the build resolves today, so B's `NoClassDefFoundError` is not demonstrated, but the configuration is fragile; `legacy-support-v4` still present; Rhino and `js-evaluator-for-android` (`:131-132`) exist only for dead code (SRC-37); Jetifier on (`gradle.properties:15`); LeakCanary is present in debug but no StrictMode. A found no `TODO/FIXME/HACK` markers in the tree; that is not evidence of completeness.

### BUILD-06 — Medium — The lint gate fails and needs triage, not suppression (A: lint section · B: §16)

Debug lint: 84 errors, 386 warnings, 1 hint. 79 errors are `UnsafeOptInUsageError` (Media3 unstable API; annotate the boundary with `@OptIn`). Real items: `MissingOnPlayFromSearch`/`MissingIntentFilterForMediaSearch` (Android Auto voice search unsupported, see SESS-02); `DefaultUncaughtExceptionDelegation` (APP-01); the security diagnostics (SEC-01); `Aligned16KB` ×3 (one library, BUILD-03); 148 `UnusedResources`; 65 `DefaultLocale`. Reconciled diagnostics: `NewApi` at `J/util/DownloadStorageHelper.java:306` (`DocumentsContract.isTreeUri`) became public in API 24 but exists as a hidden method on API 23, so B's "crash on Android 6" is overstated (A); fix the guard anyway. `WrongConstant` at `:107` is a lint data-flow false positive, since `:105` masks the flags correctly (A). `UnspecifiedRegisterReceiverFlag` at `J/activity/ClearSavedActivity.java:73` is the pre-33 branch.

### BUILD-07 — Medium — Deep links and the external search entry point do not work (A: C17 · B: UI-27, SR-18)

`AndroidManifest.xml:173-229` advertises browsable HTTPS links to `LoadBook`, but `J/activity/LoadBook.java:49-56` reads only the string extra `url` and finishes when it is absent; `getIntent().getData()` is never read. `android:noHistory="true"` is present, so A's earlier suspicion of a Back loop is withdrawn. A search started by the initial `ACTION_SEARCH` intent is ignored because only `onNewIntent` handles it (`J/activity/SearchableActivity.java:80-241,273-289`, B; see the search verification table). Normalise extras and data URIs into one validated request; audit the advertised hosts against `Url.java`.

---
## 12. Application process and architecture

### APP-01 — High — The crash handler replaces Android's without delegating; the ANR watcher misses services (A: C02 · B: AP-01; verified)

`J/App.java:270-275` installs a default uncaught-exception handler that writes a file and returns, neither chaining the previous handler nor terminating. **Runtime-corroborated by A:** after the PLAY-01 main-thread exception the process stayed alive with the same PID and a dead UI thread. Crash files are rewritten by reading the whole file on the crashing thread (`:61-90`); the ANR watcher runs only while an activity is started (`:129-170`). Retain and call the previous handler in `finally`.

### APP-02 — Medium — Preferences used as IPC and as a state store (B: A2, PR-01 · A: C06, C11; verified with corrections)

The presenter serialises the whole chapter list to `SharedPreferences` and the service parses it on the main thread at every start (`J/presenter/BookPresenter.java:814-820`, `J/pojo/StorageUtil.java:24-38`, `J/service/MediaPlayerService.java:668-694`); `JsonSyntaxException` is not caught (only NPE, `:694`); `loadEqualizerSettings` would NPE on `"null"` but the writer never produces it (latent, verifier). Pass `MediaItem`s to the session; persist in Room.

### ARCH — Systemic causes (A: C20, architectural follow-ups · B: §1, §14; agreed)

The same ten root causes recur behind most entries above. Fixing symptoms without them will keep producing regressions.

| # | Systemic problem | Where it shows | Findings |
|---|---|---|---|
| A1 | Global implicit broadcasts as the player API, `RECEIVER_EXPORTED` | `J/service/MediaPlayerService.java:1234-1270`, `J/activity/BookActivity.java:443-478` | SEC-04, PLAY-12 |
| A2 | SharedPreferences as IPC | `J/pojo/StorageUtil.java`, `onStartCommand` | APP-02, PLAY-08 |
| A3 | Mutable statics as the state machine (`BookPresenter.start/resume/speed`, `MediaPlayerService.isPlay`, `SleepTimerPresenter`, equalizer `Settings`, `App.useProxy`, `Consts` cookies/source) | `J/presenter/BookPresenter.java:96-118`, `J/service/MediaPlayerService.java:131` | PLAY-06, PLAY-11, UI-07 |
| A4 | A legacy media stack glued to Media3 by hand (own notification, focus, session state, one player per chapter) | whole `MediaPlayerService` | §3, §4 |
| A5 | Three sources of truth for downloads (DownloadIndex + cache, exported files, `books_audio.url_audio`) reconciled by name/substring matching | `ExoDownloadService`, `DownloadStorageHelper`, `BookPresenter:714-807` | §6, §7, DATA-09 |
| A6 | Identity by display name for chapters, files and deletion targets | `J/presenter/BookPresenter.java:345-352`, `J/util/DownloadStorageHelper.java:564-635,1404-1431` | DATA-01, DATA-02, PLAY-07, OFF-01 |
| A7 | Unmanaged concurrency: raw threads, undisposed Rx, main-thread DB, no transactions | `J/presenter/BookPresenter.java:143-150,595-604,645-685`, `J/util/DownloadStorageHelper.java:704-735,1098-1193` | DATA-07, DL-03, OFF-05, SRC-16, SRCH-11 |
| A8 | Catch-all-and-ignore error handling; raw exception text as UI | `J/service/MediaPlayerService.java:1060-1066,1490,1566`, `J/App.java:89,344` | PLAY-05, PLAY-17, SRC-01, SRCH-04 |
| A9 | Copy-pasted per-source logic instead of a source abstraction (107 Jsoup set-ups, seven `contains` routers, nine matching blocks) | §8, §9 | SRC-04, SRC-14, NET-07 |
| A10 | No automated tests; no CI | build | BUILD-05 |

**Recommended direction (both reviews agree):** a small explicit `BookId`/`ChapterId` model; provider adapters returning typed pages and errors (`ProviderSearchRequest(rawQuery, cursor)` → `ProviderPage(books, nextCursor, status)`); one owner of download state with per-file ownership records; one service-owned playback state machine (Media3 `MediaSessionService`/`MediaLibraryService` with a single player and the book as a playlist); lifecycle-scoped views; transactional persistence off the UI thread; one shared HTTP client. A wholesale rewrite is not required: repair the destructive and trust-boundary defects first, add the tests that prove the repair, then extract the seams.

### Resource and lifecycle hazards (B §13 · A C09, S09, UI-08, Q03; consolidated and verified)

| Resource | Acquired | Why it leaks or is unsafe | Ref |
|---|---|---|---|
| Service binding | `J/presenter/BookPresenter.java:838` (app context) | unbound through the Activity → swallowed `IllegalArgumentException`; lives until process death | PLAY-02 |
| `MediaSessionCompat` | `J/service/MediaPlayerService.java:1107` | never released | SESS-01 |
| Progress runnables on `handler1/handler2` | `:1505-1511,1536-1542` | new chains per READY, never removed | PLAY-10 |
| `Hourglass` sleep timer | `:327-344` | not cancelled in `onDestroy`; duplicate ticks; can stop a destroyed service | PLAY-10, SESS-01 |
| `AudioEffect`s | `:219-231` | re-created per READY; old ones not released with the player | PLAY-14 |
| `dbExecutor` | `:139` | shut down before the last submit | PLAY-01 |
| 18 broadcast receivers | `:553-574` | one `try`; first failure leaks the rest | PLAY-17 |
| Picasso `Target`s | `:865-885`, `J/activity/BookActivity.java:1026-1072`, `J/activity/ImageFullScreenActivity.java:107` | anonymous, weakly held | SESS-06, UI-19 |
| `ExecutorService` for `DownloadManager` | `J/App.java:473` | new pool per re-init, never shut down | NET-06 |
| Released `DownloadManager`/`SimpleCache` | `J/App.java:421-443` | still referenced by Media3's static helper and the player's `CacheDataSource` | NET-06 |
| Raw threads | `J/presenter/BookPresenter.java:143,595,645,667`; `J/util/DownloadStorageHelper.java:705,1099`; `J/service/ExoDownloadService.java:141,190`; `J/adapter/AudioAdapter.java:110` | unbounded, capture context/presenter/service, no cancellation | DL-03, OFF-05 |
| Rx subscriptions | every presenter with an empty `onSubscribe` | never disposed; scraping loops outlive the screen | SRC-16, SRCH-11 |
| Search watchdog | `J/presenter/SearchbalePresenter.java:53-62,175-178` | not removed; can start a new search later | SRCH-11 |
| `ActivityCookies` WebView | `J/activity/ActivityCookies.java` | never destroyed | UI-17 |
| `DescriptionBookFragment` views | `J/fragment/DescriptionBookFragment.java:61-83` | not cleared in `onDestroyView` | UI-12 |
| Presenter-owned dialogs | `J/presenter/BooksPresenter.java:150`, `SearchbalePresenter.java:361`, `FavoritePresenter.java:209` | `WindowLeaked` on rotation | UI-19 |
| Partial export files | `J/util/DownloadStorageHelper.java:206-250` | no temp file; truncated files look valid | DL-03 |
| Orphaned cache content | `J/App.java:457` | never evicted after export/delete/path change | DL-07, NET-05 |

---
## 13. Claims corrected, narrowed or withdrawn during reconciliation

Nothing below is a reason to ignore the parent finding; each entry says what part of a review's wording did not survive a read of the code. Items are listed by the review that made the claim.

### From analyze-opus.md (B)

| Claim | Outcome |
|---|---|
| PB-01/PB-03: `closeDB()` closes the DB while writes are queued; the presenter later uses closed models | **Withdrawn.** `BooksDBAbstract.closeDB()` is a deliberate no-op around the Room singleton (`J/interface_pacatge/books/BooksDBAbstract.java:17-19`). The teardown crash and the service-stop-on-rotation consequences stand (PLAY-01, PLAY-03). |
| PB-13: buffer values `0`…`2` throw; non-numeric → `NumberFormatException` | **Narrowed.** `2` s passes Media3's 2,000 ms after-rebuffer default; `0`-equivalents and `1` throw (A reproduced). The settings regex blocks non-numeric UI input; only restored/hand-edited prefs can carry it (PLAY-09). |
| DB-00: migration 16→17 loses the unique index on all four tables | **Cannot be proven for favorite/history/audio** (no 15/16 schema in the repo); certain for `saved`, and that alone makes Room refuse to open. Outcome and severity unchanged (DATA-03). |
| §16: `DocumentsContract.isTreeUri` → crash on Android 6 | **Overstated.** The method exists as a hidden API on API 23; fix the guard, but no `NoSuchMethodError` is demonstrated (BUILD-06, A's position). |
| §16: `exclude group: 'androidx'` → plausible `NoClassDefFoundError` | **Not demonstrated.** Both APKs build and run; the configuration is fragile, not broken (BUILD-05). |
| SR-02: a baza-knig challenge shows a "timeout" badge | **Wrong mechanism.** The `///qery=` branch catches, logs and returns an empty list; the timeout badge appears only on a real timeout. The dead `onError` code is confirmed (SRCH-04). |
| SR-19: `BooksModel.java:321-323` rethrows timeouts as NPE; `.child(0)` NPE | `:321-323` belongs to the search path and is unreachable from `BooksPresenter`; `.child(0)` throws `IndexOutOfBoundsException`, not NPE (SRCH-08). |
| SR-14: adapter list growth → "Inconsistency detected" crash | **Downgraded.** Append-only growth rarely triggers it; the one shrink is followed by `setItem` on the same main-thread pass (SRCH-12). |
| SRC-05: `AudioModel` NPE sites are end-of-list control flow | Those sites go to `onError`; only the listing models treat NPE as end of list (SRCH-08, SRC-17). |
| SRC-16: archive.org mirror has no TLS/timeout set-up | Jsoup's default 30 s timeout and platform TLS apply; only the proxy is missing (SRC-15). |
| SRC-29: search queries are not URL-encoded | Jsoup percent-encodes spaces and non-ASCII; only `&`, `#`, `+`, `?` break the query (SRC-13). |
| SRC-34: nine serial requests, re-run on every retry | Eight (the current source is skipped); there is no retry caller (SRC-16). |
| SRC-35: `socketFactory()` called at ~70 sites | 107 sites; `Jsoup.connect` 108, `maxBodySize(0)` 107, `.proxy(` 56 (NET-07). |
| SRC-37: `BookActivity.decode()` runs `res/raw/decode.js`; `config.js:56-57` | `decode()` has no callers; the JS files are referenced only by the equally dead `runScript`; passphrase lines are `config.js:54,58` (SRC-17). |
| CA-07: an MP3 URL containing "hls" **fails** at export | It is routed to `exportHlsDownload`, which returns early, so the file is **never exported**; the check also sees the `__ref` book URL (DL-09). |
| DL-10: `setMaxParallelDownloads(1)` with an **unused** 3-thread pool | Media3 uses the pool for parallel HLS segment downloads; it is re-created per re-init and never shut down (NET-06). |
| DL-12: `/book/12` matches `/book/123` on removal | Keeps a stale `saved` row; does not delete the wrong one (DL-08). |
| OF-03: `sanitizeFileName` does not strip leading/trailing spaces | `trim()` does; length cap, embedded control characters, trailing dots and reserved names are still missing (OFF-03). |
| OF-05: de-duplication deletes real saved books by name | Only when the URL slug equals the folder name (name matches are pre-empted); positions are orphaned, not deleted (OFF-02). |
| OF-06: tapping the notification for an imported book "does nothing" | For a synthetic host that matches no server an empty `BookPOJO` opens a broken `BookActivity` instead (SESS-07). |
| DB-08: "clear saved" leaves `saved`/`books_audio` rows | Mostly removed via `onDownloadRemoved`; what remains are imported books, requests without `__ref`, and exported files that the next scan re-imports (DATA-10). |
| UI-06: `getFavorite().equals`/`getDisLike().equals` NPE | Impossible: fields default to `"0"` and setters reject null; the `recreate()` loop for persistent exceptions stands (UI-12). |
| UI-08: `setTheme()` after `setContentView()` has no effect | Redundant rather than broken: the `getTheme()` overrides apply the style during inflation (UI-19). |
| UI-12: `EqualizerFragment` is dead code | Its UI is never instantiated, but its statics are used by `AnalogController`, `DialogEqualizerFragment` and the service (UI-06). |
| UI-13: `preloadStatuses` on the UI thread | It runs on an executor; the quadratic rescan is real (UI-08). |
| UI-24: `MainActivity.java:314` hard-coded Russian | That literal is an English `OK`. |
| UI-25: `BookActivity.java:437-439` | Those lines are `mButtonCollapse`; the gate is `:374,485-632` (UI-16). |
| UI-29: battery nag on every cold start; `SettingsFragment.java:201-212` requests all-files access | The nag has a "don't show" option; `:201-212` is the restore launcher (UI-18). |
| UI-30: only `ClearSavedActivity` handles insets | About ten layouts use `fitsSystemWindows`; edge-to-edge still needs a pass under target 35 (UI-19). |
| PR-01: `loadEqualizerSettings` NPEs on `"null"` | Latent: the writer never produces that value (APP-02). |
| SEC-10: default PIN `0` unlocks with `0000` | Theoretical: the UI cannot enable parental control without first setting a PIN (SEC-05). |
| SEC-11: search and MediaBrowser paths are not filtered | Search **is** filtered (same adapter and launch path); MediaBrowser is not. Because of the key mismatch the allow-list never works: every book with a genre always prompts, books without a genre never do (SEC-05). |
| SEC-04: `ClearSavedActivity.java:73` registers "without any flag" | That line is the normal pre-33 overload; the exported flag on 33+ is the concern. |

### From analyze.md (A)

| Claim | Outcome |
|---|---|
| D03 rated High | Raised to **Critical**: Room's rollback leaves the app unable to open its database after an ordinary upgrade (DATA-03). |
| Read-only use of the download cache for streaming "is deliberate and not itself a defect" | Kept as fact, reframed as a High product gap with a clear fix, because with no back-buffer and no retry it is the main cause of fragile ordinary listening (NET-04). |
| `.maxBodySize(0)` "throughout" | Quantified: 107 sites (NET-07). |
| S11: other-source failure isolation | Mechanism sharpened: the 18 unguarded `.length()` calls on a null author inside the single `try` are the most likely trigger (SRC-12). |
| No finding for service binding, foreground-start timing, title-based resume, speed `0`, the corrupt trust anchor, VPN keys, backup/cleartext/storage-permission policy, `pref_proxy` type change, `MainActivity.onDestroy`, or the parser defects of §8 | **Omissions, not errors.** All were present in B, verified in this pass, and are included above (PLAY-02, PLAY-06, PLAY-07, PLAY-12, SEC-06…SEC-09, DATA-12, UI-02, §8). |
| A's explicitly excluded items (LoadBook Back loop; stale `showingView`; shallow DiffUtil copy; missing boot/wake permissions; blanket receiver-leak claims; `closeDB()` as a connection leak) | **Remain excluded**; B does not contradict them, and B's receiver point (UI-13) is about wasted work while stopped, not a leak. |

### Line-reference slips found in either review

Small offsets were found in both documents (for example B's `AudioModel.java:655-660` → `:656-659`, `OtherSourceModel.java:871` → `:930`, `SearchebleModel.java:72-77` → `:60-62`, `DialogEqualizerFragment.java:532-534` → `:527-532`; A's `ComentsListAddapter` ranges are off by one). The line numbers in this document are the ones verified in this pass at commit `2f0a631`; where a verifier gave a corrected range, that range is used.

---
## 14. Evidence: what was actually executed, and what this reconciliation re-checked

### Runtime and build evidence (from analyze.md; retained as the only executed evidence)

| Check | Result | Findings it supports |
|---|---|---|
| Gradle 8.13, JDK 17; `assembleDebug` and minified `assembleRelease` | Both pass after generating a local debug keystore; `testDebugUnitTest` NO-SOURCE; `lintDebug` fails (84 errors / 386 warnings / 1 hint) | BUILD-01, BUILD-05, BUILD-06 |
| `apksigner verify --print-certs` on the release APK | Signer `CN=Android Debug, O=Android, C=US` | BUILD-01 |
| ELF/ZIP inspection of both APKs; `zipalign -c -P 16` | Conscrypt libraries 4 KB-aligned; ZIP alignment passes | BUILD-03 |
| Merged debug and release manifests searched | No `PlatformScheduler$PlatformSchedulerService`, no RESTART filter | DL-02 |
| Nine strict-TLS homepage probes (2026-10-22) | 4 × HTTP 200, 4 × expired-certificate failures (izib.uk, baza-knig.top, knigoblud.club, otrub.in), 1 × Cloudflare 403 (audioboo.org) | SEC-01 context, SRC-02, SRCH-04 |
| Emulator (Android 14, API 34, x86_64; AEHD acceleration recovered) smoke run | Install, onboarding, Akniga catalog and a six-source `1984` search work; four onboarding dialogs including an overlapping system prompt; minified release launches | UI-18, positive observations |
| Real playback service with a generated WAV; two `ACTION_PAUSE` commands | PLAYING → PAUSED → PLAYING | PLAY-04 |
| `am stopservice` during playback | `RejectedExecutionException at stopMedia:1387 / onDestroy:544`; process survived with the same PID | PLAY-01, APP-01 |
| Instrumentation of real production methods (6 probes, 0 harness failures) | Export of a 32-byte resource produced 63 bytes (DL-01); `index_saved_url_book` 1→0 and Room open rejected with rollback (DATA-03); restore of `{}` returned true and emptied four tables (DATA-05); buffer values `1`/`00`/`000` threw in `initMediaPlayer` (PLAY-09); `Hourglass` countdown 1,109 ms → 102 ms after nine resumes (PLAY-10) | as listed |
| Bound real service against a loopback HTTP server (`-e mode http`) | Four 503s → terminal IDLE; app Play and session Play made no new request; explicit `prepare()` recovered (PLAY-05). A pause during a held response left `playWhenReady=true` and playback started when the response completed (PLAY-04) | as listed |
| Pure-function reproductions (Python/PowerShell) | Pagination digit replacement, sibling cache-key predicate, chapter-deletion substring predicate, query-id collision, lexical chapter order | SRCH-02, DL-01, DATA-02, NET-04, OFF-02 |

Evidence files remain under `C:\Users\user\AppData\Local\audiobook-audit-tools\` (build logs, probe JSON, emulator logs, instrumentation output, crash report, screenshots) and `app/build/reports/lint-results-debug.*`; see analyze.md for the exact paths and re-run commands.

**Not executed by either review:** TalkBack/Switch Access sessions, heap profiling, long-duration battery/playback tests, an Android Auto host, a 16 KB-page device, a TV device, the full SAF/OEM matrix, live provider downloads or HLS export, signed-URL expiry, foreground-start timeout (PLAY-06), the JobScheduler exception path (DL-02), the TV `uiMode` resource selection (UI-04).

### Static verification performed for this reconciliation

- Both documents were read in full and every finding was mapped to its counterpart (Appendix A).
- Every disagreement and every finding that appeared in only one review was re-read in the source at commit `2f0a631` (working tree identical apart from the two review files). About 145 individual claims were checked: roughly two thirds confirmed as written, one third confirmed with a correction to scope, mechanism or line numbers, and a small number of sub-claims withdrawn (§13). No headline finding of either review was refuted.
- Framework facts relied on: B's `javap` inspection of Media3 1.9.0 (`DownloadService` static helper map, AAR manifest), A's inspection of Media3 1.9.0 `DefaultLoadControl`/`DownloadService` sources and the Moxy 1.7.0 sources jar, and the `openssl x509` check of the bundled certificate (re-run here: still fails).
- Counts re-measured here: `socketFactory()` 107 sites, `Jsoup.connect(` 108, `.maxBodySize(0)` 107, `.proxy(` 56, manual presenter `onDestroy()` calls from views 10, `.length()` on nullable author/reader in `OtherSourceModel` 18.

---
## 15. Remediation plan and regression gate

Both reviews propose the same order; this merges them. Each step names the findings it closes so progress can be tracked against this document.

### Phase 0 — Stop data loss and exposure (days)

1. Revoke and rotate the Telegram token and proxy credentials; delete the `.ovpn` assets; remove the AdMob/BILLING/Yandex leftovers (SEC-02). Restrict the `Authenticator` to proxy challenges (SEC-03).
2. Delete the trust-all manager and hostname verifier; replace the corrupt ISRG PEM; set `cleartextTrafficPermitted="false"`; one shared OkHttp client (SEC-01, SEC-06, SEC-07).
3. Make deletion path-based: delete only recorded URIs of app-owned files; escape or remove `LIKE` in destructive queries (DATA-01, DATA-02).
4. Restore: parse → validate → version → one transaction (DATA-05). Migration: drop/recreate indexes correctly, stop swallowing copy errors, commit all schema versions, add `MigrationTestHelper` tests (DATA-03, DATA-04).
5. Teardown order and executor lifetime (PLAY-01); `startForeground` immediately in `onStartCommand` (PLAY-06); delegate the crash handler (APP-01); clamp speed ≥ 0.25 and validate buffer values against `DefaultLoadControl` (PLAY-12, PLAY-09); guard the `pref_proxy` type (DATA-12).
6. `RECEIVER_NOT_EXPORTED` plus `setPackage` on every internal broadcast; validate media ids and browser clients (SEC-04). Fix the parental-control key mismatch and the `notificationClick` bypass (SEC-05).
7. Pass a snapshot of the chapter selection to the delete/download presenters and make per-chapter deletion fail independently (DL-10); reset `buttonClick` on every user path and stop dropping foreground status between chapters (PLAY-19); wrap the search commit in `try/finally` and install an RxJava error handler (SRCH-16); add the `RECOGNIZE_SPEECH` `<queries>` entry and handle `ACTION_SEARCH` in `onCreate` (SRCH-17); remove or redesign the ANR watcher and the raw diagnostics dialog (APP-03).

### Phase 1 — Playback reliability (1–2 weeks)

8. Migrate to Media3 `MediaSessionService` (or `MediaLibraryService` for Auto) with **one** `ExoPlayer`, the book as a playlist with `setMediaItems(items, index, positionMs)`, `handleAudioFocus=true`, `setHandleAudioBecomingNoisy(true)`, `setWakeMode(C.WAKE_MODE_NETWORK)`, a back-buffer, `DefaultMediaNotificationProvider`, and `MediaController` instead of broadcasts and the local binder (PLAY-02, PLAY-03, PLAY-04, PLAY-08, PLAY-13…PLAY-17, SESS-01…SESS-07).
9. Error policy: classify `PlaybackException`; retry with backoff and on connectivity regain; re-resolve URLs on 401/403/410; skip broken chapters visibly; surface `STATE_ERROR` with a retry action (PLAY-05).
10. Persist `(bookId, chapterIndex, chapterKey, positionMs)` and start at that position; immutable snapshots for queued writes; one scheduled task per purpose and an elapsed-time deadline for the sleep timer (PLAY-07, PLAY-10, PLAY-11, UI-07).
11. Let Moxy own presenter lifetime; remove the ten manual `onDestroy()` calls and the `MainActivity.onDestroy` broadcast; fix the `onSaveInstanceState` override (UI-01, UI-02, UI-03).

### Phase 2 — Cache, downloads and offline (1–2 weeks)

12. Two caches in app-specific storage (LRU streaming, no-eviction download); a `CacheKeyFactory` with stable `bookId/chapterIndex` keys; `customCacheKey` on requests and items; never re-create the `DownloadManager` at runtime; migrate content on folder change; shut down owned executors (NET-04, NET-05, NET-06, NET-08).
13. Declare `PlatformSchedulerService` or switch to `WorkManagerScheduler`; set download requirements and a free-space check (DL-02, DL-08).
14. Export only on explicit request, by exact cache key through `CacheDataSource`, into a temp file renamed on success, named `NNN - title.ext` with a sniffed type, inside a stable book-id directory; keep `localUri` in its own column; a managed, resumable export stage; retire the custom HLS exporter or back it with a real parser (DL-01, DL-03, DL-04, DL-05, DL-07, DL-09, OFF-03, DATA-09).
15. Downloads screen per book with aggregate and per-chapter state, stable ids, immutable snapshots (DL-06). Offline resolution and import by persisted URIs only; natural sort; transactional scans; separate SAF and File implementations (OFF-01, OFF-02, OFF-04, OFF-05, OFF-06).
16. One request-context policy for catalog, HLS child, progressive, download and playback requests; proxy toggle rebuilds the client (NET-01, NET-02, NET-03).

### Phase 3 — Sources and search (2–4 weeks, incremental)

17. One `SiteHttp` (shared OkHttp: pool, disk cache, cookie jar, 10 s timeouts, size limits, uniform proxy, challenge detection) and `Jsoup.parse(body, baseUrl)`; a `SourceParser` per site with capability flags; a host-alias table for moved domains (NET-07, SRC-02, SRC-14, ARCH A9).
18. Search: generation-scoped subscriptions, per-source `Single` with timeout and typed `ProviderPage(books, nextCursor, status)`, URL templates instead of digit replacement, per-category cursors, deduplication, progressive rendering, synchronous completion when nothing is enabled (SRCH-01…SRCH-13).
19. Parsers: fixture-based unit tests per source (book page, chapter list, search, listing pages, error/challenge pages), bracket-matching JSON extraction, `absUrl`, tolerant POJOs, typed errors instead of fake rows, per-item `try` in listings (SRC-01, SRC-03…SRC-17, SRCH-08, SRCH-14).

### Phase 4 — UX, platform and hygiene

20. Target SDK 35/36 with edge-to-edge and the `dataSync` limits; real release signing; dependency updates including 16 KB-aligned Conscrypt or its removal; drop `MANAGE_EXTERNAL_STORAGE`; backup rules; lint opt-in annotations (BUILD-01…BUILD-06, SEC-08, SEC-09).
21. Deep links and `ACTION_SEARCH` (BUILD-07); settings entry points and the cookie screen (UI-17); onboarding sequence (UI-18); equalizer capability handling (UI-06); accessibility labels and `sp` sizes (UI-14); comments and list adapters (UI-08, UI-09, UI-10); streamed progress (UI-15); locale decision (UI-05); TV/tablet items (UI-16); StrictMode and crash reporting in debug/release.

### Regression gate (minimum)

- Debug and minified release assembly, lint with individually reviewed exceptions, signing-identity check, final-artifact ELF and ZIP alignment.
- URL construction and parser fixtures for every provider, including HTTP error, challenge and empty pages, with bounded response sizes.
- Search aggregation and cancellation tests with controlled out-of-order responses; all-sources-disabled completes immediately.
- Room migration tests for each supported historical schema (rows retained, `PRAGMA index_list`); restore rejection and mid-failure tests that leave original rows intact.
- Byte-for-byte progressive export checks; representative HLS fixtures (master with two renditions, root- and parent-relative segments, byte ranges, rotating keys, missing segments); no success state on failure.
- Deletion tests proving unrelated files survive: same title from two sources, two narrators, unrelated sibling file, root named like the title, `1`/`10`/`01` chapters, on filesystem and SAF.
- Playback: duplicate pause, pause during buffering, headphone removal during buffering, focus loss, 503 then recovery, speed change while paused, boundary seeks, final-chapter replay, rotation and Back during buffering, service stop while playing/paused/buffering, cold start with autoplay off against a slow and a failing source.
- Lifecycle: Favorites → other destination → Back → filter/delete; Downloads rotate → controls/updates; repeated start/stop leak profiling.
- Downloads: 1/20 and 19/20 complete, retry before/after refresh, folder switch in one process, full disk, revoked SAF grant, interrupted export, queue offline → background → reconnect → process death → reboot.
- Device matrix: API 23, 29/30, 33/34, 35/36; phone portrait/landscape, narrow window, tablet portrait and landscape, Android TV D-pad, an Auto host, 200 % font scale, TalkBack.

### Missing or partial capabilities to track as product work (A §15 table and B §15, merged)

| Capability | Current gap | Acceptance criterion |
|---|---|---|
| Reliable whole-book offline status | one chapter marks a book complete; export state not modelled | exact completed/selected/total chapter counts with verified readable local media |
| Search provider health and retry | errors shown as books or hidden | per-source failure state with bounded retry and partial successes; cached genres; local search over the library; relevance ordering; cross-source merge |
| Cold-start Android Auto / voice search / media resumption | session only on start command; no `onPlayFromSearch`; no browse tree | browser connects, browses and plays from a stopped process; `EXTRA_RECENT` root |
| Deep links and external search | data URI ignored; cold `ACTION_SEARCH` ignored | every advertised host opens the book or an explicit error |
| Portable backup | unversioned, partial, destructive restore | described scope, validated version, atomic restore, counts and failure result |
| Storage relocation | no migration of active/cache/export ownership | atomic move/reset without process restart |
| User-controlled deletion | name heuristics and stale selections | exact identity, confirmation/undo appropriate to the operation, honest partial-failure reporting |
| Parental controls | key mismatch, unknown-genre bypass, six of nine sources | one policy decision across every entry point |
| Playback features | single player/playlist, streaming cache, retry, gapless, per-book speed, bookmarks, speed presets, artwork in session, chapter progress in notification | product decisions; absence is not a defect until specified |
| Downloads | whole-book progress, per-chapter percentage, Wi-Fi-only/charging/storage requirements, failure reasons, storage usage screen, offline-first book screen | as specified |
| Localisation | Russian-only with unused language machinery | decide and either remove or implement |
| Quality | no tests, no CI, device-only crash files | parser fixtures, migration tests, playback/download instrumentation, crash reporting backend |

---
## 16. User-reported issues cross-checked against the code

`other_findings.md` (user reports, with `images/screenshot_01.png` and `log-report.txt`) was added to the repository after the two reviews. Each report was re-checked in the source and matched against the findings above. "Covered" means the mechanism was already in this document; "extended" means the report added a trigger or corroboration; "new" means a cause not previously described.

| # | User report (translated) | What the code shows | Status | Findings |
|---|---|---|---|---|
| 1 | Search often finds nothing; the loading spinner spins endlessly | Several already-described mechanisms stack up: a query submitted while a search is running is dropped and the old run's rows fill the new query's list (SRCH-01); the per-emission 25 s timeout restarts on each of a source's four emissions and serial mode queues sources, so almost every run ends only when the 60 s watchdog fires (SRCH-10); the case-retry chain then re-runs the whole fan-out up to three times, hiding and re-showing the spinner inside the same main-thread message so it never visibly stops (SRCH-10); late completions of an old run increment the new run's counter and commit partial results early (SRCH-11); and a sentinel-only list long enough to scroll re-triggers retry on every scroll, which resets pagination to page 1 (SRCH-04). One mechanism was not described before: an exception thrown inside the commit (sort/filter/view update) on the Rx observer is routed to the app's crash handler, which only writes a file and does not terminate, leaving `isLoading = true` with no watchdog armed, so the spinner runs until the screen is closed and the app never crashes. | Extended + new | SRCH-01, SRCH-04, SRCH-10, SRCH-11, SRCH-16 (new), APP-01 |
| 2 | Searching "Приручитель женщин-монстров" on the first three sources shows error rows (screenshot: "⚠ Izibuk: unexpected end of stream on com.android.okhttp.Address@…", "⚠ Сервер: нет результатов / ошибка подключения", "akniga.org — нет ответа (таймаут)") | Exactly the sentinel-row mechanism: every empty or failed source is rendered as a fake book with the raw exception message, and the list never reports "no results". The izibuk text is a raw `HttpURLConnection` error (connection closed by the server or a TLS/anti-bot reset through the trust-all socket factory); knigavuhe's row is the generic badge; akniga's is the 25 s per-emission timeout. Which of the sites was actually blocking or slow at that moment cannot be determined from the code. | Covered | SRCH-04, SRCH-10, NET-07, SRC-02, SEC-01 |
| 3 | "Strange logs" at start-up: a dialog "Найден отчёт о сбое/зависании" with `ANR main-thread blocked 2934382 ms` and a stack ending in `MainActivity.<init>` | A false report from the app's own ANR watcher, shown by the diagnostics dialog on the next launch. A foreground main thread cannot be blocked for 49 minutes without the system killing the app, and the single captured sample shows the thread running an ordinary activity constructor and going idle right after. The watcher compares `uptimeMillis` with a heartbeat stamp whose refresh depends on activity-lifecycle bookkeeping; any gap (a process freeze/thaw, a lifecycle-flag mismatch, or a previous stall that has already ended) is attributed to whatever the main thread happens to be doing when sampled. | New | APP-03 |
| 4 | Selecting the chapters of a downloaded book and pressing delete removes only the first chapter; the rest stay | A distinct defect, not previously described: the delete handler passes the adapter's **live** selection set to the presenter and then clears that same set on the UI thread, while the presenter's worker thread is iterating it. The iteration throws `ConcurrentModificationException` after the first chapter, a catch-all swallows it, the remaining chapters are never removed, and the "deleted" toast is shown unconditionally. Depending on timing the result is "only the first", "nothing", or "files gone but still shown as downloaded". | New | DL-10 |
| 5a | (older version) With a book split into chapters, playback stops after every chapter (every ~5 min) | Two mechanisms are still in the current code: (1) the `buttonClick` flag stays set after "previous" is pressed on the first chapter, so the natural end of that chapter stops the service instead of continuing (confirmed); (2) every chapter change drops and re-acquires foreground status, and the re-promotion is wrapped in a catch for `ForegroundServiceStartNotAllowedException` that silently continues as a background service, which the system may then stop while the screen is locked (plausible, the developer's own catch block shows it happens). The per-chapter player rebuild and audio-focus release/re-request add a gap at every boundary. | Extended | PLAY-19 (new), PLAY-14, PLAY-06, PLAY-08 |
| 5b | (older version) After the screen is locked the sleep timer runs 3–4× faster than real time | Runtime-reproduced by A: every READY-while-playing transition calls `resumeTimer()`, which posts a new tick chain without cancelling the old one, and each chain advances the countdown. With the screen off and no network wake mode, rebuffers are frequent, and each rebuffer→READY adds a chain: three or four chains give the reported 3–4× speed. Still present. | Covered (trigger added) | PLAY-10, PLAY-16 |
| 6 | Audioboo downloads: 12-minute chapters weigh hundreds of MB and the file named "chapter 1" plays the thirteenth chapter | Exactly DL-01, runtime-reproduced by A: the exporter concatenates every cached span whose key shares the chapter's directory, sorted by byte offset only. Spans of all cached chapters are merged into every exported file (hundreds of MB), and the file starts with whichever chapter's offset-0 span sorts first (chapter 13 under the name of chapter 1). Audioboo listings are also duplicated four times per page (SRCH-05), and the Playerjs parser does not flatten folders, which can misalign names (SRC-15). | Covered | DL-01, DL-03, SRCH-05, SRC-15 |
| 7 | Voice search stopped working (no crash) | Two causes, one new: (1) on Android 11+ the microphone button is hidden because the manifest declares no `<queries>` for `android.speech.action.RECOGNIZE_SPEECH`; appcompat's `SearchView` shows the button only when `resolveActivity` finds a recognizer, and package-visibility filtering (target 30+) hides it, which is exactly "stopped working after an update" (new); (2) when the recognizer's result arrives as an `ACTION_SEARCH` intent to a **new** `SearchableActivity` instance, `onCreate` never reads `SearchManager.QUERY`, so the query is dropped silently (previously rated Low as SRCH-15; raised). Android 14+ background-launch rules can additionally block the recognizer's pending intent, and a voice query arriving during a running search is dropped by SRCH-01. Android Auto voice search is a separate gap (SESS-02). | New + extended | SRCH-17 (new), SRCH-15, SRCH-01, SESS-02 |

### SRCH-16 — High (mechanism confirmed, trigger not isolated) — An exception inside the search commit is swallowed by the app's crash handler and freezes the search state machine with the spinner on (user report 1; verified)

**Evidence:** `commitData()` (`J/presenter/SearchbalePresenter.java:482-492`) runs `sort`, `filterBooks()`, `showProgres(false)` and `isLoading = false` in that order, on the Rx observer's main-thread callback; the watchdog has already been removed (`:573`) before it runs (`:574`). An exception from `sort`/`filterBooks`/the view call propagates out of the observer; RxAndroid's `HandlerScheduler` passes it to `RxJavaPlugins.onError`, and because no RxJava error handler is installed it reaches the thread's uncaught-exception handler, which `J/App.java:270-275` replaced with a lambda that only writes a crash file (APP-01). Nothing rethrows, the process survives, `isLoading` stays `true`, no watchdog is armed, and every later `loadNext()`/`loadBoks()` returns at the `isLoading` guard (`:321`). The spinner stays until the screen is closed, and the user sees no crash. The same exception thrown from the watchdog runnable itself (a plain `Handler`, `:59`) escapes `Looper.loop()` and leaves a frozen UI. The only candidate trigger found statically is an NPE in `setBooksFilter` for a book with a null URL (`:789`), which parsers normally filter out; the crash files written by the handler on affected devices would show the actual exception. Independently confirmed: the retry path re-shows progress at `:322` in the same message in which the commit hid it at `:486`, so even bounded retries look like an endless spinner.

**Fix:** wrap the commit in `try/finally` that always hides progress and resets `isLoading`; install `RxJavaPlugins.setErrorHandler`; make the app's crash handler delegate (APP-01); generation-scoped disposables (SRCH-01); remove the watchdog in `onDestroy` (SRCH-11).

### SRCH-17 — Medium — The voice-search button is hidden by package-visibility filtering, and a voice query delivered to a new activity instance is dropped (user report 7; verified)

**Evidence:** `res/xml/searchable.xml:7-8` declares `voiceSearchMode="showVoiceSearchButton|launchRecognizer"`; the manifest has no `<queries>` element at all (grep) and `targetSdkVersion 34` (`app/build.gradle:41`). appcompat 1.7.1's `SearchView.hasVoiceSearch()` shows the microphone only when `PackageManager.resolveActivity(ACTION_RECOGNIZE_SPEECH, MATCH_DEFAULT_ONLY)` returns a result (verified in the library bytecode); from target 30 on, that call returns null for an undeclared package, so the button disappears on Android 11+ without any error. When the button is visible (older devices, TV without a recognizer) `onVoiceClicked()` catches `ActivityNotFoundException` and only logs. The recognizer returns the query through a pending `ACTION_SEARCH` intent; `SearchableActivity` is `singleTop` (`AndroidManifest.xml:156-172`, SEARCH filter without `DEFAULT`), so an instance on top receives it in `onNewIntent` (`J/activity/SearchableActivity.java:273-289`), but `onCreate` (`:80-241`) reads only `ARG_MODEL` (`:99`) and never `SearchManager.QUERY`, so a cold start or an instance behind a `BookActivity` drops the query. Typed search works because it is a same-app explicit `ACTION_SEARCH` to the instance on top.

**Fix:** add `<queries><intent><action android:name="android.speech.action.RECOGNIZE_SPEECH"/></intent></queries>`; factor the `onNewIntent` logic into `handleIntent()` and call it from `onCreate` too; add `DEFAULT` to the SEARCH filter; or launch the recognizer directly with `registerForActivityResult`, which avoids the pending-intent and background-launch path entirely.

### APP-03 — Medium — The custom ANR watcher produces false "main thread blocked" reports and shows raw stack traces to the user (user report 3; verified)

**Evidence:** `J/App.java:128-174`: a main-looper heartbeat re-posts itself every second only while `isAppInForeground` is true (`:132-136`); the flag is maintained by activity start/stop counting (`:240-261`) and the heartbeat is restarted from `onActivityStarted`/`onActivityResumed`; a daemon thread compares `SystemClock.uptimeMillis()` with the stamp every 3 s and writes a report when the difference exceeds 6 s (`:150-170`, `appendAnr` `:183-224`), sampling the main thread's stack up to five times and skipping only when the top frame is `nativePollOnce`. `uptimeMillis` keeps advancing while a process is frozen by the system, and the stamp is refreshed only by the heartbeat or by the watcher's own background branch, so after a freeze/thaw, a missed lifecycle transition, or a genuine stall that has already ended, the computed duration is wrong and the captured stack is whatever the main thread is executing at that instant. The report in `log-report.txt` (2,934,382 ms, one sample in `MainActivity.<init>`, thread idle immediately after) is of that kind. `MainActivity.showDiagnosticReportsIfExists()` (`J/activity/MainActivity.java:365-401`) then reads crash and ANR files on the main thread and shows the raw text in a "Диагностика" dialog asking the user to pass it to support. The watcher also ignores stalls in services and receivers (B).

**Impact:** users see alarming, misleading diagnostics; real stalls (DATA-07's main-thread sorts, PLAY-15) are reported with the wrong duration or missed; the dialog itself reads files on the main thread.

**Fix:** remove the watcher or measure looper latency directly (post a message and record its dispatch delay), discard measurements that span a lifecycle change or process freeze, cap the reported duration at the sampling interval, and never show raw stacks to users; upload diagnostics silently with consent instead.

### DL-10 — High — Deleting selected chapters removes only the first one: the live selection set is cleared while a worker thread iterates it (user report 4; verified)

**Evidence:** `J/activity/BookActivity.java:650-661`: the confirmation dialog calls `mPresenter.delete(mAudioAdapter.getSelectedItems())` (`:656`) and immediately `mAudioAdapter.clearSelected()` (`:657`). `getSelectedItems()` returns the adapter's own `HashSet` (`J/adapter/AudioAdapter.java:137-139,188`), and `clearSelected()` calls `mSelectedItems.clear()` on it (`:191-197`). `BookPresenter.delete()` builds a safe copy of the chapter *names* (`J/presenter/BookPresenter.java:582-592`) but hands the live set `data` to a new `Thread` (`:595-604`) and shows the "delete complete" toast unconditionally (`:605`). `DownloadStorageHelper.deleteChapterFiles()` iterates that set (`J/util/DownloadStorageHelper.java:1212`); each pass does one or two `startService` IPCs, a DB lookup and a write, so the UI thread's `clear()` lands during the first pass; the next `next()` throws `ConcurrentModificationException`, which the method's catch-all swallows (`:1395-1397`). The remaining chapters, the by-name file deletion (`:1316-1329`), the "remaining" check and the update broadcasts (`:1386-1393`) are skipped; the presenter still posts `updateAdapter()`.

**Outcomes by timing (all from the same race):** `clear()` during pass 1 → only chapter 1 loses its download (the user's report; its exported file stays on disk because the name-based step was skipped); `clear()` before the loop → nothing is removed but the toast says deleted; `clear()` between the emptiness check and the loop → the loop runs zero times while the name-based step still deletes every selected file from disk, so files vanish while the UI shows them as downloaded. Each adds to DL-06/OFF-06. The same aliasing exists for the download button (`:648` → `BookPresenter.dowland`, `:561`), which is currently safe only because its loop runs synchronously on the UI thread. The Downloads screen copies its selection into a new list before clearing, so it does not share this bug. For akniga books all chapters share one HLS URL, so the set has one element and deletion is all-or-nothing instead (DL-05). In offline-fallback mode chapter URLs are local paths, so `sendRemoveDownload(localPath)` matches nothing and the remote download survives (OFF-06).

**Fix:** pass a snapshot (`new HashSet<>(selected)`) from the activity and copy again in the presenter; per-chapter `try/catch` so one failure cannot abort the rest; always run the remaining-check and broadcasts; report a success/failure count instead of an unconditional toast; resolve the Media3 download id from the DB track, not from a local path. A logcat line "Error deleting chapter files" with `ConcurrentModificationException` after a multi-chapter delete confirms this on a device.

### PLAY-19 — High — A chapter boundary can stop playback: stale `buttonClick` after "previous" on the first chapter, and foreground status dropped and re-requested at every chapter (user report 5a; verified)

**Evidence:**
- `buttonClick` is set to `true` by the previous/next receivers and session callbacks (`J/service/MediaPlayerService.java:408,432,441,1157,1166`) and reset only inside `initMediaPlayer` (`:955`) or in the `STATE_ENDED` branch (`:1004`). `skipToPrevious()` does nothing when `audioIndex == 0` (`:1364-1367`), so the flag stays set; when that chapter later ends naturally, the `STATE_ENDED` branch sees `buttonClick == true` and calls `stopMedia(); stopSelf()` instead of `skipToNext()` (`:996-1004`). Pressing "previous" on the first chapter (a common way to restart it, also via headset buttons) therefore makes playback stop at the end of that chapter. **Confirmed control flow.**
- Every automatic chapter change runs `stopMediaNotSave()` → `buildNotification(PAUSED)` → `stopForeground(false)` (`:1340-1347,1398-1412,853-862`) and then `initMediaPlayer()` → `buildNotification(PLAYING)` → `startForeground(...)` inside a `catch (Exception e)` that logs "ForegroundServiceStartNotAllowedException" and falls back to a plain notification (`:839-851`). On Android 12+ a service that has dropped foreground status while the app is in the background (screen locked) is not always allowed to re-promote itself; the developer's own catch shows this is hit in practice. After the fallback the service is an ordinary background service that the system can stop, so the next chapter plays until the process is trimmed. **Plausible; not reproduced.**
- At each boundary audio focus is abandoned and re-requested (`:1399,893-895`), and a denied request calls `stopSelf()` without returning (PLAY-08).

**Fix:** keep one player with the book as a playlist (PLAY-14) so no boundary exists; until then, never drop foreground status between chapters, reset `buttonClick` on every user action path, and treat `STATE_ENDED` uniformly.

---
## Appendix A. Cross-reference of review identifiers

### analyze.md (A) → combined

| A | Combined | A | Combined | A | Combined |
|---|---|---|---|---|---|
| C01 | PLAY-01 | S01 | SEC-01 | D01 | DATA-01 |
| C02 | APP-01 | S02 | SRCH-02 | D02 | DL-01 |
| C03 | PLAY-04 | S03 | SRCH-01 | D03 | DATA-03 |
| C04 | PLAY-05 | S04 | SRCH-04 | D04 | DATA-04 |
| C05 | PLAY-09 | S05 | SRCH-05 | D05 | DL-03 |
| C06 | SESS-02 | S06 | SRCH-06 | D06 | DL-04 |
| C07 | SEC-04 | S07 | SRCH-07 | D07 | DL-06 |
| C08 | PLAY-10 | S08 | SRCH-09 | D08 | DL-06 |
| C09 | SESS-01, PLAY-17 | S09 | SRCH-11 | D09 | DL-06 |
| C10 | PLAY-11 | S10 | SRCH-10 | D10 | DL-06 |
| C11 | PLAY-08 | S11 | SRC-12, SRC-13, SRCH-13 | D11 | OFF-01 |
| C12 | PLAY-12, PLAY-13 | S12 | NET-07, SRC-06, SRCH-14 | D12 | DATA-09 |
| C13 | SEC-02, SEC-03 | S13 | UI-17 | D13 | NET-04, DL-05 |
| C14 | SEC-02 | UI-01 | DATA-05 | D14 | DATA-02 |
| C15 | BUILD-01 | UI-02 | UI-01 | D15 | OFF-02 |
| C16 | BUILD-02 | UI-03 | UI-04 | D16 | OFF-04 |
| C17 | BUILD-07 | UI-04 | SEC-05 | D17 | DL-03 |
| C18 | SESS-06 | UI-05 | UI-03 | D18 | NET-05 |
| C19 | BUILD-05 | UI-06 | UI-11 | D19 | NET-05, DL-07 |
| C20 | ARCH | UI-07 | UI-08 | D20 | DATA-11 |
| Q01 | NET-06 | UI-08 | SRC-16 | N01 | BUILD-03 |
| Q02 | UI-01 | UI-09 | UI-12 | N02 | DL-02 |
| Q03 | NET-06 | UI-10 | UI-06 | T01 | NET-01 |
| LINT-UI-01 | UI-14 | UI-11 | UI-08 | T02 | NET-02 |
| LINT-UI-02 | UI-14 | UI-12 | UI-09 | T03 | NET-03 |
| LINT-UI-03 | UI-14 | UI-13 | UI-14 | T04 | NET-06 |
| Visual supplement | UI-18, §10 design | UI-14 | UI-10 | T05 | DL-05 |
| Additional persistence concerns | DATA-07, DATA-08, DATA-11, OFF-03, OFF-05 | UI-15 | UI-15 | T06 | SRCH-10 |

### analyze-opus.md (B) → combined

| B | Combined | B | Combined | B | Combined |
|---|---|---|---|---|---|
| A1–A10 | ARCH | SRC-01 | SRC-01 | SR-01 | SRCH-01 |
| PB-01 | PLAY-01 | SRC-02 | SRC-02 | SR-02 | SRCH-04 |
| PB-02 | PLAY-02 | SRC-03 | SRC-02 | SR-03 | SRCH-04 |
| PB-03 | PLAY-03 | SRC-04 | SRC-12 | SR-04 | SRCH-02 |
| PB-04 | PLAY-04 | SRC-05 | SRCH-08, SRC-17 | SR-05 | SRCH-03 |
| PB-05 | PLAY-08 | SRC-06 | SRC-17 | SR-06 | SRCH-09 |
| PB-06 | PLAY-06 | SRC-07 | SRC-03 | SR-07 | SRCH-06 |
| PB-07 | PLAY-07 | SRC-08–SRC-17 | SRC-15 (SRC-12 also DL-05; SRC-13 also SEC-02) | SR-08 | SRCH-07 |
| PB-08 | PLAY-14 | SRC-18 | SRC-04 | SR-09 | SRCH-12 |
| PB-09 | PLAY-10 | SRC-19 | SRC-05 | SR-10 | SRCH-10 |
| PB-10 | PLAY-14 | SRC-20 | SRC-05 | SR-11 | SRCH-10 |
| PB-11 | PLAY-14 | SRC-21 | SRC-06 | SR-12 | SRCH-10 |
| PB-12 | PLAY-12 | SRC-22 | SRC-07 | SR-13 | SRCH-11 |
| PB-13 | PLAY-09 | SRC-23 | SRC-08 | SR-14 | SRCH-12 |
| PB-14 | PLAY-13 | SRC-24 | SRC-09 | SR-15 | SRCH-13 |
| PB-15 | PLAY-15 | SRC-25 | SRC-10 | SR-16 | SRCH-13 |
| PB-16 | PLAY-11 | SRC-26 | SRC-11 | SR-17 | SRCH-13 |
| PB-17 | PLAY-18 | SRC-27 | SRC-12 | SR-18 | SRCH-15, BUILD-07 |
| CA-01 | NET-04 | SRC-28 | SRC-17 | SR-19 | SRCH-08 |
| CA-02 | NET-04, DL-05 | SRC-29 | SRC-13 | SR-20 | SRCH-08 |
| CA-03 | NET-05 | SRC-30 | SRC-13 | SR-21 | SRCH-08 |
| CA-04 | NET-06 | SRC-31 | SRC-14 | SR-22 | SRCH-05 |
| CA-05 | NET-06 | SRC-32 | SRC-16 | SR-23 | SRCH-08 |
| CA-06 | NET-03 | SRC-33 | SRC-16 | SR-24 | SRCH-10 |
| CA-07 | NET-02, DL-09 | SRC-34 | SRC-16 | SR-25 | SRCH-14 |
| CA-08 | NET-08 | SRC-35 | NET-07 | SR-26 | SRCH-14 |
| ER-01 | PLAY-05 | SRC-36 | ARCH A9 | SR-27 | SRCH-14 |
| ER-02 | PLAY-16 | SRC-37 | SRC-17, NET-07 | SR-28 | SRCH-15 |
| ER-03 | PLAY-17 | SEC-01 | SEC-01 | DB-00 | DATA-03 |
| ER-04 | PLAY-05 | SEC-02 | SEC-02 | DB-01 | DATA-04 |
| ER-05 | PLAY-18 | SEC-03 | SEC-03 | DB-02 | DATA-04 |
| MS-01 | SESS-01 | SEC-04 | SEC-04 | DB-03 | DATA-07 |
| MS-02 | SESS-03 | SEC-05 | SEC-07 | DB-04 | DATA-07 |
| MS-03 | SESS-04 | SEC-06 | SEC-06 | DB-05 | DATA-08 |
| MS-04 | SESS-05 | SEC-07 | SEC-08 | DB-06 | DATA-02, DATA-09 |
| MS-05 | SESS-06 | SEC-08 | SEC-09 | DB-07 | DATA-09 |
| MS-06 | SESS-02 | SEC-09 | NET-01, NET-07 | DB-08 | DATA-10 |
| MS-07 | SEC-04 | SEC-10 | SEC-10, SEC-05 | DB-09 | DATA-10 |
| DL-01 | DL-01 | SEC-11 | SEC-05 | BK-01 | DATA-05 |
| DL-02 | DL-02 | UI-01 | UI-01, PLAY-03 | BK-02 | DATA-06 |
| DL-03 | DL-05 | UI-02 | UI-02 | BK-03 | DATA-11 |
| DL-04 | DL-07 | UI-03 | UI-03 | PR-01 | APP-02, PLAY-09 |
| DL-05 | DL-03 | UI-04 | UI-03 | PR-02 | DATA-12 |
| DL-06 | DATA-09 | UI-05 | UI-13 | AP-01 | APP-01 |
| DL-07 | DL-08 | UI-06 | UI-12 | AP-02 | NET-06 |
| DL-08 | DL-04 | UI-07 | UI-13 | UI-19 | UI-16 |
| DL-09 | DL-08 | UI-08 | UI-19 | UI-20 | UI-10 |
| DL-10 | DL-08 | UI-09 | PLAY-10 | UI-21 | UI-14 |
| DL-11 | DL-06 | UI-10 | UI-07 | UI-22 | UI-19 |
| DL-12 | DL-08 | UI-11 | UI-06 | UI-23 | UI-04 |
| OF-01 | DATA-01 | UI-12 | UI-06 | UI-24 | UI-05 |
| OF-02 | DATA-02 | UI-13 | UI-08 | UI-25 | UI-16 |
| OF-03 | OFF-03 | UI-14 | UI-08 | UI-26 | UI-16 |
| OF-04 | OFF-01 | UI-15 | UI-08 | UI-27 | BUILD-07 |
| OF-05 | OFF-02 | UI-16 | DL-06 | UI-28 | UI-17 |
| OF-06 | SESS-07 | UI-17 | UI-11 | UI-29 | UI-18 |
| OF-07 | DATA-09, OFF-06 | UI-18 | UI-08, UI-09, UI-10 | UI-30 | UI-19 |
| OF-08 | OFF-04 | §16 | BUILD-01…BUILD-06 | §17, §18 | §15 |
| OF-09 | OFF-05 | §13 | §12 hazards table | §14, §15 | ARCH, §15 |
| OF-10 | OFF-06 | | | | |
