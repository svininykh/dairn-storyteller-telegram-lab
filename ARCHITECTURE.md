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
| AI adapter | Convert approved context into a model request and validate structured output. | Durable story or game state. |
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

## Explicit non-goals for the harness

This repository currently defines the design only. It does not implement a
Telegram bot, integrate OpenAI, alter `dairn-gm-engine`, or implement DAIRN
mechanics.
