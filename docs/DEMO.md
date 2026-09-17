# Target demo

## Objective

Show one player completing a short, stateful DAIRN interaction from Telegram
without any manual edits to game state.

## Preconditions

- A configured Telegram test bot and test chat.
- One known-good, versioned `.dairn` story.
- An available `dairn-gm-engine` version compatible with that story.
- Runtime configuration and, when the AI step is enabled, valid model
  credentials stored outside the repository.

## Happy path

1. The player sends `/start` in the test chat.
2. The bot presents the available demo story and the player selects it.
3. The application creates a session that records the selected story and
   engine-backed initial state.
4. The bot sends the opening scene and available next action(s).
5. The player sends one valid action in natural language or the agreed command
   format.
6. The application obtains any constrained interpretation needed, submits the
   proposed action to `dairn-gm-engine`, and persists the resulting state.
7. The bot replies with the engine-authorized outcome and the next action(s).
8. The player sends `/status`; the bot reports the same persisted session
   state, demonstrating that the turn can be resumed.

## Success criteria

The story selected by the player is the loaded `.dairn` source; each state
change is accepted by `dairn-gm-engine`; the reply reflects persisted state;
and restarting the application does not require a manual state repair to
answer `/status`.
