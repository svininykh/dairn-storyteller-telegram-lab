# Agent guide

## Project map

This repository is an integration lab for a future DAIRN StoryTeller
experience.  It currently contains planning documentation only.

| Path | Purpose |
| --- | --- |
| `ARCHITECTURE.md` | Boundaries, responsibilities, and sources of truth. |
| `docs/PLAN.md` | Ordered delivery plan. |
| `docs/DEMO.md` | Target end-to-end demonstration. |
| `docs/PRE_EXISTING.md` | Record of DAIRN work predating HackAlem. |
| `README.md` | Short public repository description. |

## Development rules

1. Keep this lab an integration boundary; do not reimplement DAIRN rules.
2. Treat the `dairn-gm-engine` as an external, authoritative dependency. Do
   not modify it from this repository.
3. Preserve `.dairn` story files as authored source material. Do not silently
   rewrite, infer, or discard story content.
4. Keep Telegram transport, AI orchestration, story storage, and engine calls
   separately testable. Add no layer unless it owns a real boundary.
5. Keep secrets out of source control. Use documented environment variables or
   a local, ignored configuration file when configuration is introduced.
6. Before implementing a feature, update the relevant plan and architecture
   document if the feature changes a responsibility or source of truth.
7. Telegram transport adapters are permitted integration work. Keep
   Telegram-specific code in a dedicated transport module/package and depend
   only on application-facing ports; do not place Telegram APIs or models in
   DAIRN domain or Engine code.
8. Do not reimplement DAIRN mechanics in this repository. OpenAI integration
   remains subject to its separately documented boundary and task scope.

## Working agreement

Make small, reviewable changes. Document assumptions, especially dependency
interfaces that are not yet available. A demo is complete only when it follows
the path in `docs/DEMO.md` without manual state repair.

## Human-review checkpoint

After completing an Issue, prepare a concise final report in Russian for human
review. It must cover completed work, changed files, each DONE criterion,
LIMITS compliance, only checks actually run, and known remarks or limitations;
never claim a check ran when it did not. Before human review, show the relevant
`git diff` and explain it in Russian: what changed, why, how it relates to the
task, and any side effects or debatable decisions. This explanation does not
replace a human review of the diff. Then stop and wait for a human decision.

Do not run `git add`, commit, push, close an Issue, or publish an Issue comment
through an integration without explicit human permission. Commit, push, and
Issue closure happen only after an explicit human decision.
