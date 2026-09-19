# dairn-storyteller-telegram-lab

Integration lab for a DAIRN StoryTeller experience spanning Telegram, OpenAI
API assistance, `.dairn` stories, and `dairn-gm-engine`.

The repository contains a minimal Telegram demo for selecting or naming a
hero, displaying engine-generated hero state, and resolving Omens through the
external DAIRN Great Steppe module. It does not implement DAIRN mechanics or
durable session storage.

## Photo d20 recognition

The Telegram demo can ask OpenAI Vision to propose a value from a d20 photo.
`OPENAI_API_KEY` is required by the current application startup path because
the Vision recognizer is created when the bot starts; optionally set
`OPENAI_VISION_MODEL` (defaults to `gpt-4.1-mini`). The proposed value never
resolves an Omen until the player presses **Подтвердить**. The key is read only
from the environment and is not stored in this repository.

## Hero-state narration smoke test

After a generated hero's Omen is resolved, the Telegram demo invokes
`OpenAiHeroStateNarrator` when `OPENAI_API_KEY` is configured. Optionally set
`OPENAI_NARRATOR_MODEL` (default: `gpt-4.1-mini`). The Omen result is sent
first, then the two-to-four-sentence narrative proposal. A narration failure
does not change the stored hero or Omen and is reported separately.

Start with [the agent guide](AGENTS.md), then see the
[architecture](ARCHITECTURE.md), [plan](docs/PLAN.md),
[target demo](docs/DEMO.md), [Docker deployment guide](docs/DEPLOY.md), and
[technology manifesto](MANIFESTO.md), and
[pre-existing DAIRN record](docs/PRE_EXISTING.md).
