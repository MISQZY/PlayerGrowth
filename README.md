# PlayerGrowth

PlayerGrowth is a Minecraft server plugin that grows a player's in-game
height gradually over played time, instead of every player being the same
fixed size from the moment they join. Height is applied through the vanilla
scale attribute, so it works with any resource pack or skin and needs no
client-side mod.

## Features

- **Gradual growth over played time.** Each player grows from a configurable
  minimum height toward a maximum, linearly, based on minutes actually
  played — not wall-clock time, so it can't be cheesed by staying connected
  AFK. Growth can also pause automatically while a player is boxed in by
  solid blocks.
- **Fixed or randomized growth duration.** Either every player takes the
  same configured number of minutes to reach full height, or (if a min/max
  range is configured instead) each player is assigned their own random
  target on first join, persisted so it stays the same across restarts.
- **Per-gender height caps.** Gender types are fully configurable — add,
  rename, or alias as many as you like, each with its own maximum height —
  or turn the whole gender system off and use one shared cap for everyone.
- **Manual overrides.** Players (or admins, for other players) can set an
  exact custom height at any time, which is then respected as-is instead of
  being overwritten by the growth curve.
- **Optional cross-server sync.** On a proxied network sharing one central
  database, a lightweight Velocity relay propagates height/gender changes
  between backend servers immediately, instead of players seeing stale
  values until they reconnect.
- **PlaceholderAPI support.** A rich set of `%playergrowth_*%` placeholders
  covering current/min/max height (in meters and as a raw scale value),
  gender, growth percentage, time remaining, and custom-scale status —
  documented in-game via `/papi info playergrowth`.
- **API for other plugins.** A typed, read-only Java API (`PlayerGrowthAPI`)
  registered through Bukkit's `ServicesManager`, exposing the same
  height/gender/growth-progress data as the placeholders above without
  needing PlaceholderAPI installed or string parsing.
- **Configurable storage.** H2 (zero-setup, the default), MySQL, MariaDB, or
  plain YAML files.
- **Localized messages.** Bundled English and Russian translations, fully
  MiniMessage-formatted so colors and styling are easy to customize; more
  locales can be added by dropping in another `messages_<locale>.yml`.
  Every message also supports `<primary>`/`<secondary>` theme-color tags
  (configurable in `config.yml`, so the whole plugin can be reskinned from
  one place) alongside any of MiniMessage's own tags.
- **FlectonePulse-aware.** If FlectonePulse is installed, PlayerGrowth
  reuses its server identifier (through its Java API, not by re-parsing its
  config file) so the two plugins never disagree about which backend server
  they're each running on, and messages can use FlectonePulse's own
  `<fcolor:N>` color palette via the same tag it uses.

## Supported platforms

| Module | Use when... |
|---|---|
| `minecraft/paper` | You run Paper (or a fork of it, e.g. Purpur) and want full support. |
| `minecraft/bukkit` | You run plain Spigot/CraftBukkit, or Paper without needing its extras. |
| `minecraft/velocity` | You run a Velocity proxy and want cross-server sync between backend servers. Optional — skip it entirely on a single-server setup. |

Install exactly one of `minecraft/paper` / `minecraft/bukkit` on each
backend server, matching your server software — never both at once. There
is no BungeeCord support; Velocity is the only supported proxy.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/height <meters>` | Set your own custom height. | `playergrowth.height` |
| `/height remove` | Remove your custom height and resume natural growth. | `playergrowth.height` |
| `/height set <player> <meters>` | Set another player's custom height. | `playergrowth.height.others` |
| `/height remove <player>` | Remove another player's custom height. | `playergrowth.height.others` |
| `/gender <type>` | Set your own gender. | `playergrowth.gender` |
| `/gender set <player> <type>` | Set another player's gender. | `playergrowth.gender.others` |
| `/growth summary [player]` | View height, gender, and growth progress. | `playergrowth.info` / `.others` |
| `/growth height [player]` | View height and growth progress only. | `playergrowth.info` / `.others` |
| `/growth gender [player]` | View gender only. | `playergrowth.info` / `.others` |
| `/playergrowth reload` | Reload config/gender/messages without a restart. | `playergrowth.admin.reload` |
| `/playergrowth help` | List every command available to you. | `playergrowth.help` |

## Configuration

Each installed module ships with `config.yml` (growth speed, scale bounds,
storage backend, cross-server sync, locale), `gender.yml` (gender types,
aliases, per-type max height), and `localizations/messages_<locale>.yml`
(every player-facing message). All three are commented in place and
regenerate automatically if deleted; existing installs are migrated forward
automatically on version upgrades without losing customizations.

## Building from source

```bash
./gradlew build
```

Builds all four modules. Each platform module produces a shaded jar under
its own `build/libs/` (e.g. `PlayerGrowth-Paper-<version>.jar`) ready to
drop into a `plugins/` folder.
