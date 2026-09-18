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
| Story repository | Locate and load versioned `.dairn` stories. | Runtime session state. |
| `dairn-gm-engine` | Interpret DAIRN mechanics and produce authoritative state transitions. | Telegram and model integrations. |
| Session store | Persist the references and state required to resume a player session. | Authored story definitions. |

## Sources of truth

| Information | Authoritative source |
| --- | --- |
| Story definitions | Versioned `.dairn` files. |
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

## Explicit non-goals for the harness

The current lab includes only a minimal Telegram demonstration adapter. It does
not alter `dairn-gm-engine` or implement DAIRN mechanics.
