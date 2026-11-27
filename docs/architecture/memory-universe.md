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
| Activity Journal | Actor actions, tools, duration, results, and outputs | Append/audit | Inspectable derived life and work history; never conversation source text |

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

## Passive surfacing versus active recall

InfoSET distinguishes two ways a memory can reach Aire:

- **Passive surfacing（浮现）** is automatic, lightweight delivery. The memory system continuously
  scores a small number of compact atomic memories against the current conversation and quietly
  attaches only the strongest few to the next model context. Aire does not issue a memory lookup
  call to obtain them. This should feel like a simple fact or fragment "coming to mind".
- **Active recall（召回）** is deliberate retrieval. Aire explicitly asks the memory system to search
  for a topic, person, event, phrase, project, or older context when the passive set is insufficient
  or when exact provenance/details are needed. Active recall can spend a larger search/token budget
  and may use lexical, semantic/vector, relation, and source-text expansion.

These paths must be observable separately:

- `surfaceCount`, `lastSurfacedAt`, passive score, and the exact atoms delivered automatically;
- `recallCount`, `lastRecalledAt`, explicit query/cue, candidates, source expansion, and result use.

Passive surfacing should prefer tiny atom statements rather than whole documents. It may combine
lexical/semantic similarity, activation, recency, importance, relationship stability, and diversity
penalties, while suppressing recently surfaced duplicates unless the cue is especially strong.

Active recall is the "go look it up" path. It may start from atoms/index hits and then expand to the
canonical source text, conversation archive, attachments, or document ranges. Vector indexes remain
replaceable projections and never become memory truth.

Aire can therefore receive a few simple facts without tool-like retrieval on every turn, while
still having a deliberate recall mechanism for questions such as "what happened that day?" or
"find the original wording".

## Implementation correction: surfacing is not recall

The first Room v37 node currently calls `AtomicMemoryRecallEngine` automatically from
`ChatRequestFactory` on the newest user message and injects the selected atoms into the request.
Under the product terminology above, this behavior is **passive surfacing**, even though the
prototype class, database tables, inspector, and `[Optional Recall]` block still use `recall` names.

This naming mismatch should be corrected without discarding the working v37 data path:

1. Treat the existing automatic selection path as the prototype for passive surfacing.
2. Keep its output tiny: preferably a handful of short atom statements or directory-like hints,
   not expanded source prose.
3. Introduce a separate model-invoked active-recall capability that can search atoms, exact text,
   dates, conversation IDs, and then expand into canonical archive/source text.
4. Only active retrieval should count as a deliberate recall/use event. Passive surfacing needs
   separate delivery statistics so 'the system showed it' does not mean 'Aire chose to remember it'.
5. Rename UI and internal labels incrementally after migration compatibility is defined; do not
   break Room v37 backups merely to fix terminology.

### Re-read design references (2026-11-28)

- **Paramecium** gives the clearest two-stage analogue: every turn may receive a ~150-token
  relevant-memory directory; appearing in that directory does not increment access. The model
  calls `recall` only when it chooses to fetch L1 details or L0 verbatim conversation windows.
- **Latent · 显影** explicitly separates Passive Recall from `latent_search`: the host injects
  passive material between the user message and the first model request, while deliberate search
  remains a model action. Passive recall is intentionally a hidden host path, not a normal tool.
- **Underwater Traces** separates recall eligibility from context injection and allows
  low-probability revisits; 'not recalling' is a valid state and a fish need not surface every turn.
- **Ellivien MemFrags** is a simpler trigger-based automatic injection layer: only fragments
  matching the newest user message are inserted, showing that lightweight surfacing can remain
  independent from full-library retrieval.
- **Ellivien Three-Tier Context** reinforces budget separation: recent context is always cheap,
  wider recall is triggered only when looking back is needed, and full-thread recall is reserved
  for explicit intent.

InfoSET keeps its own archive, atoms, provenance, and UI; these projects are mechanism references,
not schemas to copy.

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

Daddy Home capability grants and Activity Journal are documented in
[`daddy-home.md`](./daddy-home.md). Journal rows can link to a recall trace, PR, build, workspace
output, or room object. They remain outside Conversation Archive and never substitute for the source
message, memory atom, or provider request snapshot they reference.

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

## Atomic memory and recall v1

The first persistent node uses Room schema 37:

- `memory_atom` stores a stable ID and required statement, with nullable event time, people, place,
  topic, event, change, reflection, provenance, importance, stability, confidence, tags, and relations.
  `importedAt` is independent from nullable `occurredAt`.
- recall count, last recall, activation, feedback counters, state, and pinning are system fields;
  a strong cue can surface a dormant atom without deleting or rewriting archive data;
- `memory_recall_trace` and `memory_recall_candidate` preserve the request cue, candidate scores,
  provenance snapshot, filter reason, actual injected atom text, token cost, destination, and feedback;
- Markdown writes pass through the compatibility adapter and replace only the atoms projected from
  that source path. Deterministic IDs keep unchanged statements stable across refreshes. Once atoms
  exist, provider requests use atomic recall and do not inject the Markdown store as a second source;
- whole-house backup already snapshots the complete Room database, so atoms, stable IDs, traces,
  and feedback move through the same staged validation and restore lifecycle as conversations.

Recall v1 is deliberately lexical and deterministic. It scores cue overlap, activation, explicit
importance/stability/confidence, feedback, and pinned status, then applies dormant-cue, diversity,
and token-budget filters. The Recall Inspector exposes both test recalls and traces created by real
chat requests. Semantic/vector indexes remain a later projection after lexical misses are measured.
