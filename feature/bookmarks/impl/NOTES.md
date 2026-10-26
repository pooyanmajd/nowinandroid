# Saved: private notes and multi-select

## Behavior

- Add, edit, or delete a private note from a Saved card. The dialog caps input at 500 UTF-16 code units, retains drafts across recreation, and previews two lines on the card.
- Long-press a card to select it. In selection mode, taps toggle selection; the contextual bar shows the count, Remove, Cancel, and Select all. Back cancels selection. Article navigation is disabled during selection.
- Removing one or more bookmarks creates one undo batch and one snackbar. Undo restores every ID; notes remain intact throughout the undo window.
- Dismissal/timeout, leaving the screen, or another removal closes the undo window. Cleanup removes notes only for IDs that are still unbookmarked, so re-saving an article protects its note.

## Design and trade-offs

Notes use a new map field (21) in the existing Proto DataStore. This reuses the repository, DI, and test-double structure without a Room migration or new library. It is appropriate for small, bounded notes; a Room table would be preferable for substantial user content or search. The only added module dependency is Activity Compose, already used by the app, for Back handling.

The Bookmarks ViewModel owns the undo lifetime and serializes mutations with a mutex. Repository/DataStore operations atomically update a whole batch and enforce storage invariants. Snackbar callbacks carry a batch token, preventing an old dismissal or Undo from affecting a newer removal. Starting a new Bookmarks ViewModel sweeps abandoned unbookmarked notes, covering process death during Undo. If storage is unavailable, errors are shown and abandoned cleanup is retried on the next visit. There is no background cleanup worker.

Other screens retain their existing bookmark interaction; removing a bookmark there has no undo window and deletes its note immediately. Saved's local card reuses the existing header, title, metadata, bookmark button, and topic components. Its long press selects instead of starting the shared card's drag-to-share gesture. No navigation or other feature UI was changed.

Note text is not passed to analytics, logging, network, or sync. Android cloud backup and device transfer exclude the preferences file containing notes. A consequence of sharing that file is that the existing preferences/bookmark IDs are also excluded from backup. Separating private content into its own no-backup store is a possible follow-up. Notes are app-private local data, not separately encrypted storage.

Selection and Undo are transient ViewModel state. As with the original screen, ON_STOP closes Undo. Drafts use rememberSaveable; confirmed notes survive restarts. The dialog remains open if saving fails.

## Verification

- Build: `./gradlew :app:assembleDemoDebug`
- Unit tests: `./gradlew :feature:bookmarks:impl:testDemoDebugUnitTest :core:datastore:testDemoDebugUnitTest :core:data:testDemoDebugUnitTest`
- UI tests: `./gradlew :feature:bookmarks:impl:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.google.samples.apps.nowinandroid.feature.bookmarks.impl.BookmarksScreenTest`
- Coverage includes batch Undo, note deletion after dismissal, stale snackbar callbacks, re-saving before cleanup, restart recovery, note length/edit/delete, write failure, selection changes, long press, Back, and dialog save/cancel/delete.

Results on the available environment: app assembly/install, Bookmarks lint, and all 65 unit tests passed. The 11 Compose UI tests compiled but could not execute successfully on the API 37 emulator: the existing Espresso setup fails with `NoSuchMethodException: android.hardware.input.InputManager.getInstance`, including the original tests. Re-run them on a compatible emulator before treating UI verification as complete. The installed Saved screen visibly shows Add note; a full manual interaction walkthrough was not completed because desktop input control returned `noWindowsAvailable`.

## Next steps

Translate the new strings; consider a separate Room/no-backup note store if content grows; add persistent expiry if cleanup must happen without revisiting Saved after process death. Existing Java 25 / JaCoCo 0.8.12 instrumentation warnings affect coverage collection; no dependency upgrade is included in this feature.
