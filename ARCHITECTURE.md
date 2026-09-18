# Architecture

## Purpose

The lab will connect a Telegram conversation to a DAIRN StoryTeller session.
It is an adapter around existing DAIRN assets and `dairn-gm-engine`, not a new
game-rules implementation.

## Components and responsibilities

| Component | Responsibility | Does not own |
| --- | --- | --- |
| Telegram adapter | Receive user updates and deliver rendered replies. | Conversation policy, game rules, or story content. |
| Session orchestrator | Coordinate a turn, retain session references, and call collaborators. | Telegram API details or DAIRN mechanics. |
| Dice Vision recognizer | Propose a die type, value, and confidence from a photo via OpenAI Vision. | Omen resolution or any character state change. |
| Hero state narrator | Produce a constrained, non-authoritative description from engine state, omen, and scene context. | DAIRN rules, canon, player decisions, or durable state. |
| Story repository | Locate and load versioned `.dairn` stories. | Runtime session state. |
| Hero initialization service | Select a structured book hero, request a missing hero name, or delegate a book with no heroes to the Engine creation flow; then retain the engine-provided initial state. | Character-generation rules or extracting character data from prose. |
| `dairn-gm-engine` | Interpret DAIRN mechanics and produce authoritative state transitions. | Telegram and model integrations. |
| Session store | Persist the references and state required to resume a player session. | Authored story definitions. |

## Sources of truth

| Information | Authoritative source |
| --- | --- |
| Story definitions | Versioned `.dairn` files. |
| Hero identity | A structured `heroes` entry in the selected `.dairn` book, or an explicit player/GM name. |
| Rule interpretation and legal transitions | `dairn-gm-engine`. |
| Current player/session state | Session store, as produced by engine transitions. |
| Incoming/outgoing chat delivery | Telegram API records; the application stores only needed correlation data. |
| Prompt and response contracts | Versioned application configuration/documentation once introduced. |

## Turn boundary

`Telegram update → session orchestrator → story/session context → AI adapter
(when needed) → dairn-gm-engine → persisted session state → Telegram reply`

The engine must validate every state-changing action. The AI adapter may help
interpret or narrate a turn, but never becomes a source of truth for DAIRN
rules or state.

For a photo d20, the application stores a valid proposed value as pending and
shows the player a confirmation step. Only the explicit confirmation follows
the normal `OmenResolutionService → dairn-gm-engine` path. An uncertain,
non-d20, or out-of-range result has no Omen path and offers retry or manual
input instead.

## Telegram transport boundary

Telegram is an adapter layer. Telegram-specific handlers, update/callback
models, and API clients belong only in the dedicated Telegram transport
package/module (when introduced), for example
`org.dairn.storyteller.telegram`. That layer translates Telegram input into
application-facing commands and renders application/domain results as Telegram
responses.

`Telegram → StoryTeller application → DAIRN domain / dairn-gm-engine`

This is the only permitted dependency direction. The application and DAIRN
domain/Engine must not import or otherwise depend on Telegram APIs or models.
The adapter does not own hero-selection, naming, character-generation, or
other DAIRN rules; it delegates those decisions to the application and Engine.
For multi-message startup it keeps only transport correlation state (the
Telegram chat/session key and the expected input kind). A callback or text
message is forwarded only when it matches that state; the application remains
the authority for the hero transition and validation.

## Hero initialization boundary

`selected .dairn book → structured hero identity (or explicit name) →
dairn-gm-engine character generator / creation flow → initial engine-owned
character state → session`

The book supplies story context and, when present, a selectable hero identity.
A name supplied by the reader is session input and never changes the authored
book definition. A book with no structured heroes is delegated to the Engine's
separate initial-hero-creation flow. The book is not a character sheet: the
application never infers missing character values from prose or reimplements
engine character-generation rules.

## Narrative AI boundary

`engine-owned character state + engine-owned omen + supplied scene context →
HeroStateNarrator → non-authoritative HeroStateNarrative`

The narrator receives immutable snapshots and cannot write a session, alter an
omen, or modify book content. Its output is a player-facing proposal only.
After the application has persisted a resolved Omen, it invokes this boundary
with the session's complete engine-owned `CharacterState`, the Omen, and
transport-independent story context. Telegram then displays the Omen result
followed by the returned narrative. A narration failure is presentation-only:
it does not alter the persisted character or Omen.

## Generated character session boundary

The complete `CharacterState` returned by `dairn-gm-engine` is retained by the
active StoryTeller session. Telegram renders the engine-owned fields without
recalculating them; the reader must continue past that profile before the
existing Omen d20 flow is offered.

## Explicit non-goals for the harness

The current lab includes only a minimal Telegram demonstration adapter. It does
not alter `dairn-gm-engine` or implement DAIRN mechanics.
