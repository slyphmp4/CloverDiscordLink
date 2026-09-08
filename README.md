# CloverDiscordLink

CloverDiscordLink is a Discord integration plugin for Minecraft servers running Paper or Cardboard 26.2. It combines account linking, Discord-role access control, a two-way Minecraft/Discord chat bridge, and join/quit notifications.

## Requirements

- Minecraft / Paper / Cardboard 26.2
- Java 25
- A Discord bot with `MESSAGE CONTENT INTENT` enabled
- Optional: MySQL

The project intentionally compiles against Paper API `26.2.build.110-stable` for Cardboard compatibility.

## Features

- Minecraft chat → Discord embeds
- Discord channel → Minecraft Adventure components
- Clickable Discord messages with hover details in Minecraft
- Minecraft UUID ↔ Discord account linking through expiring one-time codes
- Optional Discord role gate before a player can join
- Join and quit embeds
- Local `links.yml` storage or pooled MySQL storage
- Automatic migration from the old `plugins/DiscordChatBridge` data folder
- `&` colors, `&RRGGBB`, `&#RRGGBB`, and MiniMessage HEX colors
- Async Discord, HTTP, database, and file operations where blocking work is involved
- Configuration reload through `/dchat reload`

## Paper and Cardboard compatibility

CloverDiscordLink targets the Paper API exposed by Cardboard 26.2 and avoids NMS, CraftBukkit internals, and reflection.

Cardboard 26.2 currently dispatches Bukkit `AsyncPlayerChatEvent` for player chat instead of Paper `AsyncChatEvent`. The Minecraft → Discord bridge therefore deliberately listens to the Bukkit compatibility event so the same plugin JAR works on both Paper and Cardboard. Login access control uses `AsyncPlayerPreLoginEvent`, which is dispatched by Cardboard 26.2.

## Installation

1. Build or download `CloverDiscordLink-<version>.jar`.
2. Put it in the server `plugins/` directory.
3. Start the server once.
4. Configure `plugins/CloverDiscordLink/config.yml`.
5. Restart the server.

For secrets, environment variables can be used instead of storing values in YAML:

```text
CLOVER_DISCORD_TOKEN
CLOVER_MYSQL_PASSWORD
```

## Discord setup

Create a Discord bot and enable `MESSAGE CONTENT INTENT` in the Discord Developer Portal. Add the bot to the Discord server and configure:

```yaml
discord:
  token: "BOT_TOKEN"
  channel-id: "123456789012345678"
  role-gate: true
  required-role-id: "123456789012345678"
```

When `role-gate` is enabled, a linked player must also have the configured role in the guild that owns the bridge channel.

## Account linking

When an unlinked player joins:

1. CloverDiscordLink generates an expiring numeric code.
2. The login is rejected with instructions from `link.kick-message`.
3. The player sends the code to the Discord bot in a direct message.
4. The plugin persists the Minecraft UUID ↔ Discord ID link.
5. The player joins again.

Codes use `SecureRandom`, are unique among active codes, expire automatically, and are rate-limited on invalid Discord DM attempts.

## Storage

Local storage is used by default:

```yaml
mysql:
  enabled: false
```

MySQL can be enabled with:

```yaml
mysql:
  enabled: true
  host: localhost
  port: 3306
  database: database
  user: root
  password: ""
  pool-size: 4
  ssl: false
```

MySQL connections use HikariCP. The existing `dcb_links` table name is preserved so installations upgrading from DiscordChatBridge keep their existing links. If MySQL cannot initialize, the plugin falls back to local `links.yml`.

Local `links.yml` updates are written to a temporary file, flushed, and atomically replaced when the filesystem supports atomic moves.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/dchat reload` | `dchat.reload` | Reloads supported configuration values |

Changing the Discord bot token or MySQL connection settings requires a server restart.

## Building

```bash
./gradlew clean build
```

The production plugin JAR is created in:

```text
build/libs/CloverDiscordLink-<version>.jar
```

The build uses:

- Java 25
- Gradle 9.7.0
- Paper API 26.2 build 110 stable
- JDA 6.5.0
- HikariCP 7.1.0
- MySQL Connector/J 26.7.0
- JUnit 6.1.3

## Project structure

```text
src/main/java/com/slyph/cloverdiscordlink/
├── CloverDiscordLink.java
├── account/
├── command/
├── config/
├── discord/
├── listener/
├── storage/
├── update/
└── util/
```

## Changelog

See `CHANGELOG.md` for release changes and upgrade notes.

## License

No license is currently declared in this repository.

## Author

`slyph`
