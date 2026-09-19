# Pre-existing DAIRN work: StoryTeller Telegram Lab

## Scope and status labels

This document records the repository state at the end of the StoryTeller Telegram Lab. The repository, its tests, build configuration, and deployment artifacts are the source of truth for the `IMPLEMENTED` statements below.

- **IMPLEMENTED** means present and traceable to code, tests, configuration, or deployment material in this Lab.
- **EXPERIMENTAL FINDING** means a conclusion drawn from building the Lab; it is not a claim that every future DAIRN component exists.
- **PROPOSED / FUTURE** means not implemented by this Lab.

`dairn-gm-engine` is an external, authoritative dependency. This Lab calls the published Great Steppe module and does not reimplement or modify DAIRN mechanics.

## IMPLEMENTED

### Authored content and package boundary

- `.dairn` Book Package v0.1 can be written, read, and minimally validated as a ZIP-based container. Validation covers package metadata, safe paths, book, story, chapter, and local scene references. See `src/main/kotlin/org/dairn/storyteller/book/DairnBookPackage.kt` and its tests.
- The package reader exposes only structured `heroes` entries from `book.yaml`. A reader-provided name is held in the session and does not rewrite authored content. The format and package boundaries are documented in `docs/DAIRN_STORY_FORMAT.md` and `docs/DAIRN_BOOK_PACKAGE.md`.
- The repository includes a pilot `.dairn` book under `test-data/` for package tests. It is test source material, not a claim that this Lab supplies a general StoryMaker or StoryCodex product.

### Application, hero, and rules flow

- The hero-startup flow automatically selects a sole structured hero, presents multiple structured heroes, requests a missing name, and passes the selected identity to the external Great Steppe character generator. Books with no structured heroes explicitly stop at `CreationRequired` because the external Engine creation flow is not implemented in this Lab.
- The complete `CharacterState` returned by that generator is retained in an in-memory session, rendered as a profile, and must be explicitly continued before the Omen flow. The application does not derive character attributes from prose or calculate engine rules.
- The minimal Omen flow accepts a digital, manual, or confirmed-photo d20 value and resolves a value from 1 through 20 via the external Great Steppe Omen resolver. The resulting Omen is held in the in-memory session.

### AI assistance

- An OpenAI Responses API Vision adapter can recognize a physical d20 from a Telegram photo and propose a value with confidence. It cannot resolve an Omen until the player explicitly confirms the proposed value; uncertain or invalid recognition offers retry or manual input.
- An OpenAI Responses API narrator can produce a two-to-four-sentence Russian narrative proposal after a generated hero's Omen is resolved. Its input is constrained to immutable engine state, Omen, and supplied scene context; its output cannot write state or alter an Omen. A failure is reported separately.
- API keys are read from environment variables. The Vision and narrator model names are configuration values, so these adapters are not tied to one fixed model identifier.

### Telegram, deployment, and supporting infrastructure

- The Telegram long-polling adapter routes `/start`, hero selection/name input, callback actions, and photos to application-facing flows, and renders their responses. Telegram-specific APIs are confined to `org.dairn.storyteller.telegram`.
- A multi-stage `Dockerfile`, `compose.yaml`, ignored `.env` configuration, and `docs/DEPLOY.md` provide a reviewer-run deployment path. An external `.dairn` book is mounted read-only; credentials and books are not committed.
- The Gradle Kotlin/JVM project has focused automated tests for package handling, hero startup, Omen resolution, application transitions, Telegram rendering, Vision response parsing, and narrator constraints.

## EXPERIMENTAL FINDINGS

The Lab demonstrated the practical value of separating:

**Content → Rules → State → AI → Human**

Authored content remains distinct from engine rule interpretation. Engine results remain distinct from application-held session state. AI is effective as a constrained recognizer or narrator, while explicit confirmation and player choices preserve human decision points.

The AI model is one component inside a wider application harness, not the application itself. The Lab's OpenAI adapters are replaceable boundaries; they do not own DAIRN rules, authored content, or authoritative state.

### DAIRN PRODUCT

**AI proposes.**

**Rules constrain.**

**Human decides.**

### DAIRN DEVELOPMENT

**Human defines.**

**AI implements.**

**Tests verify.**

**Human decides.**

## PROPOSED / FUTURE

- Durable session storage and restart-safe `/status` support from the target demo are not implemented; current session stores are in memory.
- General story selection, opening-scene delivery, natural-language action interpretation, action routing, and full end-to-end StoryTeller progression described in `docs/DEMO.md` are not implemented by this minimal Lab.
- The Engine-owned initial-hero-creation flow for books without structured heroes is intentionally unavailable here.
- StoryTeller beyond this demonstration, StoryMaker, StoryCodex, and any additional Engine functionality are future work unless separately evidenced in another repository.

## HackAlem boundary

This repository represents experimental work performed before the HackAlem competition. This record makes the boundary between that pre-existing work and competition work explicit.

Future HackAlem repositories may use the documented concepts and principles as pre-existing input. Competition implementation must be tracked separately and must not be represented as part of this Lab merely because it follows the same principles.
