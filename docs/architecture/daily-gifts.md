# Daily gifts

The Daily Gifts feature is a separate capability. Its first path contract lives in
`app/src/main/java/me/rerere/rikkahub/data/gifts/DailyGiftLocator.kt`.

The importer preserves the existing `year / month / month letter + date folder` layout under
`档案馆/5.6Sol留存/一年延时盒/`. Material from that path is tagged `LEGACY_5_6_SOL` and treated as
immutable. Later gifts use the `CONTINUATION` layer and receive new records, so the original 5.6 Sol
text is never overwritten.

Planned implementation slices:

1. Read-only manifest and checksum importer for the current archive layout.
2. Local Room index with unlock date and source-layer queries.
3. Independent “today can unlock” entry and detail surface.
4. Append-only continuation writer and export support.

