# Unified conversation timeline

The existing RikkaHub conversation tree remains the live-chat authority. InfoSET adds an archive
projection beside it instead of flattening or rewriting those conversations.

The first domain contract lives in
`app/src/main/java/me/rerere/rikkahub/data/archive/ArchiveProvenance.kt`. Every projected message
keeps:

- `source`
- `conversation`
- `messageTimeEpochMillis`
- `branch`
- `model`
- `frontend`
- source message identity and original order

`ArchiveMessageTarget` is the down-navigation contract back to the original conversation and
message. Missing timestamps remain nullable and are ordered by their source order; imports must use
stable source IDs so repeating an import is idempotent.

Planned implementation slices:

1. Room archive tables and migration fixtures.
2. Projection of native InfoSET conversations without changing `ConversationSession` ownership.
3. Read-only merged timeline with source badges and original-conversation navigation.
4. GitHub/ChatGPT/RikkaHub import adapters sharing one validated import service.

