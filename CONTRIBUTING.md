# Contributing to Dialtone

## Documentation

Every change keeps the code readable by someone who didn't write it.

- **Every Kotlin file starts with a description.** Before the main class, object or
  composable, a `/** … */` comment says in plain words what it is, why it exists, and anything
  surprising about how it works (an Android rule it works around, a reason it runs where it
  does). New files get one; changed files keep theirs accurate.
- **Explain why, not what.** Comment the reasons a reader couldn't guess from the code:
  Android behaviours, carrier quirks, choices between alternatives. Don't narrate each line.
- **Keep the plain-language guide current.** [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md)
  explains, without code, what Dialtone does and which file does it. When a change adds,
  removes or moves a feature, screen or file, update the guide (and the screens table) in the
  same change.
- **Keep the README current** for anything about building, installing, permissions or
  how a feature behaves.
- **Only describe what's there.** Check claims against the code; don't document planned or
  imagined features.
- The visual voicemail engine in `vvm/` is Google's AOSP code. Keep its comments, and keep
  changes there small and commented, so upstream fixes can still be copied across.

## Tests

Unit tests live in `app/src/test/` and run on every build (`./gradlew testDebugUnitTest`);
a failing test stops the APK from being built. Add or update tests when changing logic that
can be checked without a phone (matching, grouping, filtering, parsing).

## Commits

- Commit messages: a short summary line, then a few lines on what changed and why.
- Commits are authored by the project owner. Don't add co-author or tool attribution lines.

## Builds and releases

Every push to `main` builds a signed, optimized test APK on GitHub Actions. Releases: push
`release-notes/vX.Y.Z.md`, or run **Actions › Build release**. See the README for details.
