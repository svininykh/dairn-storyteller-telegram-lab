# Implementation plan

## Sequence

1. **Freeze contracts.** Confirm the `.dairn` loading contract and the public
   `dairn-gm-engine` interface. Record versions, inputs, outputs, and errors.
2. **Create the minimal runtime skeleton.** Add configuration, logging, a
   session-store interface, and focused contract tests—without duplicating
   engine behavior.
3. **Add story access.** Implement read-only selection and loading of `.dairn`
   stories, with validation and a stable story identifier.
4. **Add engine orchestration.** Start/resume a session and route player
   actions to `dairn-gm-engine`; persist only engine-authorized state.
5. **Add Telegram transport.** Map Telegram updates to sessions and send
   rendered outcomes, handling retries/idempotency at the transport boundary.
6. **Add AI assistance.** Introduce an OpenAI adapter for constrained
   interpretation/narration, using structured responses and engine validation.
7. **Exercise the demo.** Run the complete scenario in `DEMO.md`, then add
   regression coverage for the demonstrated path and failure cases.

## Exit criteria

Each step has a documented interface, tests appropriate to its boundary, and
does not move ownership of DAIRN rules away from `dairn-gm-engine`. The first
end-to-end milestone is the successful, repeatable demo in `DEMO.md`.

## Issue #5 baseline — DAIRN Book Package v0.1

Issue #5 formalizes the pre-existing Markdown/YAML practice in
`DAIRN_STORY_FORMAT.md` and defines `.dairn` as a ZIP-based distribution
container in `DAIRN_BOOK_PACKAGE.md`. The package reader/writer/validator is a
small interoperable boundary, not an engine or Telegram feature. A future
opening-image flow resolves only the package's start chapter and stops after
image generation; it does not advance or read later chapters.

## Issue #8 — load and select a hero from a DAIRN book

At book startup, read only structured `heroes` entries from the selected
`.dairn` package. Select a sole hero automatically; present multiple heroes
to the reader; and request a non-blank name only when the selected hero has no
authored name. Keep that supplied name in the current session without changing
the book. When `heroes` is empty, delegate to the separately implemented
Engine initial-hero-creation flow; StoryTeller does not invent a hero or its
parameters. Pass the selected identity and any supplied name to the current
`dairn-gm-engine` API, retaining only its returned, engine-owned initial
character state. Do not derive character attributes from book prose or
duplicate engine rules. Narrative AI is not part of this step.

## Issue #9 — enable Telegram transport development

Telegram transport work is an allowed future integration layer. Telegram
handlers, callback/update models, and API clients will be isolated in a
dedicated transport package/module and may depend on StoryTeller
application-facing ports only. The dependency direction remains `Telegram →
Application → Domain / Engine`; neither DAIRN domain code nor
`dairn-gm-engine` may depend on Telegram. This issue changes documentation
only: it does not introduce Telegram handlers, `/start`, session logic, or
DAIRN mechanics.

## Issue #10 — integrate hero startup with Telegram

The Telegram adapter will load the configured `.dairn` book on `/start` and
delegate hero startup to the application flow from Issue #8. It will render a
stable-ID inline choice for `SelectHero`, collect a name only while the
application is awaiting one, continue the existing StoryTeller flow after
`Started`, and report `CreationRequired` explicitly until an Engine creation
flow is available. Per-chat transport state will reject stale, repeated, or
unexpected input without mutating hero state. Telegram remains an adapter;
hero selection and name validation stay in the application/Engine.

## Issue #6 — hero-state narration

Use the OpenAI Responses API behind a `HeroStateNarrator` application port.
Supply separate structured sections for DAIRN constraints, engine-generated
character state, engine-owned Omen result, and provided scene context. Accept
only a 2–4 sentence `HeroStateNarrative`; it is a proposal and cannot update
any session, engine state, Omen, or `.dairn` content. API credentials remain
environment-only. Automated tests use fake narrators and local response
fixtures; a manual smoke test is documented in the README.

## Issue #11 — persist generated character state

Pass the complete engine-generated `CharacterState` from hero startup into the
active StoryTeller session. Render its existing engine-owned fields as a
profile, then require an explicit continue action before offering the existing
Omen d20 choices. No character-generation or Omen rules are added to the
application or Telegram layers.

## Issue #12 — narrate after Omen resolution

After persisting a valid Omen result, StoryTeller reuses the Issue #6
`HeroStateNarrator` with the active session's complete `CharacterState`, the
resolved Omen, and transport-independent `SceneContext`. It returns the Omen
result and the non-authoritative `HeroStateNarrative` as distinct presentation
steps; Telegram only renders these application results. Narration failure is
reported without changing the stored character, Omen, or roll. No narrator may
run before an Omen exists, and it never writes engine or session state.

## Issue #13 — constrain hero-state narration

Strengthen the existing `HeroStateNarrator` contract without adding another
narrator boundary. Authoritative `CharacterState`, resolved Omen, and story
context are immutable facts; the narrator may select relevant facts and offer
atmosphere, tension, uncertain thematic connections, or questions, but may
not invent a current hero action or decision, change Omen participants, assert
causality, or merge independent character fields into a new relationship. The
scope and subject of every selected field must remain intact: for example,
`Clothing = bloodstained` must not become that the hero is bloodstained. The
threatening situation also does not establish a hero's feelings, memories,
suspicions, beliefs, or intentions. The
next meaningful action remains with the reader. Keep these constraints
provider- and Telegram-independent; an AI adapter renders them as provider
instructions. Preserve the Aibike + Pale Rider input as a local regression
fixture and cover the semantic failure classes with automated contract
tests.
