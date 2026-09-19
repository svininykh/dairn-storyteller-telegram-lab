# Docker deployment

This guide deploys the current DAIRN StoryTeller Telegram Lab from a public
checkout on a compatible Linux server. It does not require IntelliJ IDEA,
Codex, the author's VPS, or any file from a developer workstation.

## Prerequisites

- Linux server with Git, Docker Engine, and the Docker Compose plugin.
- A Telegram Bot Token for a bot you control.
- An OpenAI API key. It is required by the current startup path because the
  Vision recognizer is created when the application starts.
- A compatible `.dairn` book, stored outside this repository.

## Clone and configure

```bash
git clone https://github.com/svininykh/dairn-storyteller-telegram-lab.git
cd dairn-storyteller-telegram-lab
cp .env.example .env
chmod 600 .env
```

Edit `.env` with a local editor. It is ignored by Git and must never be
committed.

| Variable | Required | Meaning |
| --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | Yes | Token for the Telegram bot to run. |
| `OPENAI_API_KEY` | Yes | Key used by the current unconditionally-created Vision recognizer; it is also used for narration when available. |
| `DAIRN_BOOKS_DIR` | Yes | Absolute host directory containing the external `.dairn` book. |
| `DAIRN_BOOK_PATH` | Yes | Path to that book inside the container, for example `/books/reviewer-book.dairn`. |
| `OPENAI_VISION_MODEL` | No | Vision model override; default is `gpt-4.1-mini`. |
| `OPENAI_NARRATOR_MODEL` | No | Narration model override; default is `gpt-4.1-mini`. |

For example, create a host directory and place a compatible book there:

```bash
mkdir -p "$HOME/dairn-books"
# Copy your compatible .dairn book into $HOME/dairn-books/reviewer-book.dairn.
```

Then set `DAIRN_BOOKS_DIR` to the absolute host path (for example,
`/home/reviewer/dairn-books`) and `DAIRN_BOOK_PATH` to
`/books/reviewer-book.dairn`. Compose mounts `DAIRN_BOOKS_DIR` at `/books`
read-only; the book is never copied into the image.

`DAIRN_BOOK_PATH` is evaluated **inside the container**. Use the host directory
in `DAIRN_BOOKS_DIR` and its corresponding `/books/book.dairn` path in
`DAIRN_BOOK_PATH`.

## Build and start

Build the application image from the checkout:

```bash
docker compose build
```

Start it in detached mode:

```bash
docker compose up -d
```

## Verify the container and Telegram

Check status and inspect startup logs:

```bash
docker compose ps
docker compose logs --tail=100 storyteller
```

The service should be running without missing-environment or book-path errors.
In Telegram, open the bot corresponding to `TELEGRAM_BOT_TOKEN` and send
`/start`. The bot should respond with the configured book's hero-startup flow.
This verifies the full path: Telegram → adapter → StoryTeller → Telegram.

## Stop, start, restart, and logs

```bash
docker compose stop
docker compose start
docker compose restart
docker compose logs -f storyteller
```

`docker compose down` stops and removes the container but preserves the image
and the externally stored book. It does not delete the host book directory.

## Update an existing deployment

Keep `.env` and the external book directory in place, then run:

```bash
git pull
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=100 storyteller
```

Repeat the Telegram `/start` check after an update. Do not put credentials or
`.dairn` books in Git while updating.
