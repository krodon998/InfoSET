# InfoSET

InfoSET is an independently maintained Android AI client built from the mature RikkaHub codebase.
The first milestone keeps the existing chat, providers, model configuration, local conversations,
settings, tools, and backup foundation while establishing an installable identity owned by InfoSET.

## Current baseline

- Upstream source: [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)
- Fixed upstream revision: `1fb09152942a0f3ae46bf796fe17eeaf2b022f19`
- License: GNU AGPL-3.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE)
- Android application ID: `com.infoset.app` (`com.infoset.app.debug` for debug builds)
- Version: `0.1.0` (`versionCode 1`)
- Upstream update checks and update-reminder UI: disabled
- Upstream automatic release/nightly workflows: removed

InfoSET does not automatically merge or track future upstream releases. Desired upstream changes are
reviewed and ported manually.

## Build

The project uses JDK 21, Android Studio/Android SDK, Node 22, and pnpm 11.

```bash
cd web-ui
pnpm install --frozen-lockfile
cd ..
./gradlew test assembleGithubDebug
```

The debug APK is produced under `app/build/outputs/apk/github/debug/`. The committed
`google-services.json` contains local placeholder identifiers; analytics and Crashlytics collection
are disabled in the manifest.

## Planned InfoSET capabilities

- [Unified conversation timeline](docs/architecture/conversation-archive.md)
- [Daily Gifts](docs/architecture/daily-gifts.md)

The timeline preserves each original conversation and records source, conversation, message time,
branch, model, and frontend metadata. Daily Gifts preserves the current year/month/month-letter/date
layout and keeps the 5.6 Sol source layer immutable while allowing append-only continuation gifts.
