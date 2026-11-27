# Memory Universe

Memory Universe is InfoSET's inspectable continuity system. It keeps original conversation data,
runtime context, editable memories, and background organization separate so that none of them can
silently become a replacement for the others.

## Layers

| Layer | Authority | Mutable | Purpose |
| --- | --- | --- | --- |
| Conversation archive | Original and imported conversations | Append/project only | Canonical record, provenance, branches, attachments, and source order |
| Working memory | Existing assistant/global Markdown memory repositories | Yes, version checked | Facts and summaries that may be recalled or injected into a request |
| Context and checkpoints | Conversation messages plus `ContextComposer` projection | Derived | Short recent context, cache-stable request projection, and continuation summaries |
| Candidates | Extractor/worker proposals with source references | Reviewable | Possible memories waiting for organization or rejection |
| Core bond and persona | Pinned, explicitly owned memory entries | Explicit edits only | Stable identity, relationship agreements, and long-lived preferences |

Missing source timestamps stay nullable. `importedAt` and provenance are recorded separately and
must never be substituted for `occurredAt`. Undated records retain source order and appear under an
explicit unknown-time group.

## Atomic memories and document views

The existing Markdown repository is a compatibility adapter and an editable document view. It is
not the long-term shape of every memory. The canonical derived layer uses small, sparse memory
atoms. An atom requires only a stable identity and a short statement. Other fields are filled only
when the source supports them:

- optional event time, people, place, topic, and source references;
- optional event summary, resulting change, and Aire's reflection;
- optional importance, stability, confidence, tags, and relations to other atoms;
- system-maintained recall count, last-recalled time, activation, and successful-use feedback.

An ordinary sentence can therefore be a complete memory. A detailed event may carry every field.
Missing details stay absent instead of being guessed or forcing the user and organizer through a
large form. Markdown files can group, edit, export, and inject selected atoms; imports of existing
files remain document memories until they are deliberately decomposed with source-preserving diffs.

## Recall and forgetting

Forgetting changes activation, never the canonical archive. A memory can move between active,
cooling, and dormant states while pinned core entries remain stable. Recall ranking will combine:

- semantic and lexical cue match;
- recency of use and recency of the source event;
- use frequency and successful-recall feedback;
- explicit importance and relationship stability;
- diversity penalties so one topic cannot consume the whole request budget.

A dormant memory can surface again when a strong cue appears. Every recalled item keeps its source
references, score explanation, request destination, and token cost. Injection is budgeted by layer:
core identity first, current state second, recalled working memory third, then recent raw dialogue.

## Dreaming and workers

Dreaming is the existing memory-consolidation pipeline presented as an observable maintenance job.
It reads finished conversation turns, ignores context-checkpoint summaries, and proposes edits to
working memory. Future extractors and household workers use the same candidate contract.

The maintenance surface must expose pending work, selected worker/model, source conversations,
proposed diffs, accepted/rejected changes, errors, duration, and usage. Workers may organize and
test derived data; they do not rewrite the conversation archive.

## In-app maintenance entrance

The desktop Memory app is the human and Aire maintenance entrance. The first node exposes the
archive, working-memory files, context/checkpoint boundary, dream status, memory switches, and file
editing. Later nodes add candidate review, recall simulation, injection preview, parameter tuning,
history import, and audit playback without changing the ownership rules above.

Question cards, poke events, mood, and branch/join interactions may emit archive events and memory
candidates. They do not write stable memory directly.

## Revalidated design sources

The implementation should keep the earlier resource review visible instead of silently replacing
it with a new design:

| Source | Mechanism retained |
| --- | --- |
| `ellivien-openai-portal` | Local conversations, thread lookup, import/export, and replaceable model configuration |
| `ellivien-three-tier-context` | Short recent window, budgeted expansion, explicit full-thread recall, and summaries with source ranges and versions |
| `ellivien-memfrags` | Latest-message cues, aliases, deduplication, source permission filtering, and recall budgets |
| `ellivien-pulsetap` | Quiet background nudges, pause/quiet hours, rate limits, idempotent decisions, and inspectable failures |
| Paramecium | Original text is authoritative; embeddings and indexes are projections only |
| `oliscatt/Latent-memory` | In-place continuation and passive recall that first returns an index, then source text |
| `softVale/Underwater-Traces` | Stable identity, learned style, current dialogue, fragments, provenance, external light, filtering, and context quality as distinct layers |
| RikkaHub | Editable file memory, post-chat consolidation, checkpoint compression, and separate memory model |
| Operit | Separate chat/memory/summary/control models, token ledger, cache metrics, and bounded tool results |
| 心潮 dynamic mind | Replaceable short-state layer and throttled internal nudge bridge; it does not own long-term memory |

These are mechanism references. InfoSET keeps its own schema, identity, permissions, archive, UI,
and migration rules.
