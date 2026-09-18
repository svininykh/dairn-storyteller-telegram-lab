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
