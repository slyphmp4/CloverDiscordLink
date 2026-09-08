# Changelog

All notable changes to CloverDiscordLink are documented here.

## 2.0.0 — 2026-09-09

### Added

- Native target for Minecraft, Paper, and Cardboard 26.2 on Java 25.
- Secure expiring account-link codes generated with `SecureRandom`.
- One-to-one Minecraft UUID ↔ Discord account enforcement.
- Invalid-code rate limiting for Discord direct messages.
- HikariCP-backed MySQL connection pooling.
- Environment variable support for Discord and MySQL secrets.
- Automatic migration of legacy `config.yml` and `links.yml` from `plugins/DiscordChatBridge`.
- Configuration validation with safe reload behavior.
- JUnit tests and Java 25 GitHub Actions builds.

### Changed

- Renamed the plugin and project to CloverDiscordLink.
- Moved Java sources to `com.slyph.cloverdiscordlink`.
- Migrated the build to Gradle Kotlin DSL and Gradle 9.7.0.
- Updated JDA to 6.5.0, HikariCP to 7.1.0, and MySQL Connector/J to 26.7.0.
- Replaced legacy color processing with centralized Adventure and MiniMessage formatting.
- Reworked Discord startup, HTTP update checks, database operations, and local file writes to avoid blocking the server main thread.
- Replaced the single persistent JDBC connection with a managed connection pool.
- Made local `links.yml` writes atomic to reduce corruption risk during interrupted saves.
- Preserved the existing `dcb_links` MySQL table for upgrade compatibility.
- Preserved `/dchat reload` and the existing link data format where possible.
- Uses `AsyncPlayerChatEvent` deliberately as a compatibility fallback because Cardboard 26.2 currently emits that Bukkit event rather than Paper `AsyncChatEvent`.

### Security

- User-controlled Discord and Minecraft text is handled without MiniMessage tag injection.
- Account-link codes now expire and cannot be reused after successful consumption.
- Discord role checks fail closed when role verification is unavailable.
- SQL writes use prepared statements and pooled connections.
- Secrets no longer need to be stored directly in `config.yml`.

### Upgrade notes

- Java 25 is required.
- `api-version` is now `26.2`.
- Existing local links and the `dcb_links` MySQL table are retained.
- Discord bot token and MySQL connection changes still require a full server restart.

### Verification

- Production sources compile against Paper API 26.2 build 110 stable on Java 25.
- `./gradlew clean build` and the included JUnit test suite must pass in GitHub Actions before release publication.
- Cardboard 26.2 event compatibility was checked against its `ver/26.2` source implementation. A live Cardboard server smoke test is not part of the automated CI suite.
