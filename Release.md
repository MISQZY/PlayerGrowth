# PlayerGrowth — Release Notes (v0.1.2 → v0.1.7)

Covers every change shipped between v0.1.2 and v0.1.7, newest first. Each
entry maps to the commit(s) that introduced it; see `git log` for full
technical detail on any item.

---

## v0.1.7

**New feature + a compatibility fix, no breaking config changes for existing installs.**

- **Added centralized, DB-backed playtime tracking**, modeled on
  FlectonePulse's own `fp_time` scheme (one persisted row per player:
  first-join timestamp, last checkpoint, accumulated total, session count),
  gated behind `network.sync-enabled`:
  - **With sync off** (the default, single-server setups): growth still
    reads the server's own local play-time statistic directly, exactly as
    before - no new database table is touched, no extra join/quit overhead.
  - **With sync on**: growth instead reads the shared record, so a player's
    growth progress stays consistent no matter which backend server in the
    network they're actually standing on, instead of drifting based on
    whichever server's local statistic happened to be read.
  - **New config key: `network.blocklist`** - a list of this network's
    `server` UUIDs whose in-game time should NOT count toward growth
    playtime (e.g. a hub/lobby server players idle on between real
    servers). Only consulted when `network.sync-enabled` is true.
- **Lowered `plugin.yml`'s `api-version` from `26.2` to `1.20.5`.** It was
  previously pinned to this module's own compiled Spigot API version for no
  real reason, which refused to load the plugin on any older server. Audited
  every Bukkit API symbol the plugin actually calls - the single newest one
  is the `Attribute.SCALE` entity attribute (what growth/height is built on),
  added in Minecraft 1.20.5 - and lowered the declared floor to match, widening
  supported servers with no code changes.

---

## v0.1.6

**Bug-fix release - two live-deploy startup/command crashes when FlectonePulse isn't installed.**

Both shared the same root cause in different classes: a JVM linkage failure
(`NoClassDefFoundError`) is thrown at the *caller's* call site, one frame
before the callee's own `try`/`catch` ever runs - so wrapping only the callee
wasn't enough in either case.

- **`FlectonePulseAccess.tryGetFileFacade()` crashed plugin startup**
  (`bootstrapCore` → `FlectonePulseServerIdResolver.resolve`) whenever
  FlectonePulse wasn't installed - the `FileFacade.class` literal is resolved
  by the caller before `tryGet()`'s own `try` block is ever entered.
- **`PlayerGrowthMessages.send()` crashed every message-sending command**
  (e.g. `/playergrowth help`) whenever FlectonePulse wasn't installed -
  invoking `FlectonePulseMessageDispatcher.trySend` triggers whole-method
  bytecode verification on first call, which needs FlectonePulse's `FEntity`
  type to type-check `MessageContext.Builder#sender(FEntity)`/
  `receiver(FEntity)` regardless of which runtime branch would actually
  execute.

Both reproduced and verified fixed with standalone harnesses: compiled
against FlectonePulse present, run with it absent from the runtime classpath
- the exact condition that crashed on the reporting server.

---

## v0.1.5

**Cleanup/refactor release — no user-facing behavior changes, no config/permission changes.**

- **Extracted `Notifier`**, a reusable base class for any feature that
  sends an in-game notification gated by a permission node plus a
  live-checked "is this enabled right now" flag. `UpdateNotifier` (the
  update-check feature from v0.1.4) is its first user. `Notifier` lives in
  `core`, built on the platform-agnostic `PlatformPlayer`/`PlayerGrowthCore`
  rather than Bukkit's `CommandSender` — any future platform module could
  reuse it as-is.
  - Sends via `messages().get(...)` directly rather than through
    `minecraft/bukkit`'s FlectonePulse-dispatch entry point: a plugin-update
    notice has no business being relayed through FlectonePulse's own
    pipeline (e.g. a Discord bridge) just because the recipient is online.
- **Redundancy cleanup**, following a full audit of the codebase for
  duplicated logic:
  - `GrowthEngine.effectiveScale(PlatformPlayer)` replaces a
    "current-scale-or-minimum" computation that had been copy-pasted into
    four different call sites (a command, the PlaceholderAPI hook, and the
    public API implementation).
  - `PlayerGrowthCore.genderDisplayName(...)` replaces a gender
    display-name lookup that had been copy-pasted into five call sites.
  - `CommandGuards.requirePlayer` centralizes the "this command needs an
    online player, not console" check, previously duplicated (and
    inconsistently — one copy existed as an unused private helper) across
    three command classes.
  - `SchemaVersionStore` extracts the schema-version read/compare/
    migrate/write bookkeeping that the H2 and MySQL/MariaDB storage
    backends each carried as separate, nearly-identical implementations.
    Verified against a live H2 database (fresh install and reopen of an
    existing database) after the change.
- Minor: dropped a noisy `<fcolor:N>` diagnostic log line that printed the
  full resolved color map on every startup/reload.

---

## v0.1.4

**Architecture consolidation + two new features.**

- **Merged the `minecraft/paper` module into `minecraft/bukkit`.** A full
  audit found the *only* genuine Paper-only API the separate Paper module
  used was Brigadier-native command registration (`CommandSourceStack`) —
  and Cloud's ordinary `CommandSender`-based command manager already works
  identically on Paper, so that split bought nothing but double the
  maintenance. The other apparent Paper-only behavior — sending native
  Adventure `Component`s instead of legacy-formatted strings — was never
  actually gated on the Paper API either; it's now a runtime capability
  check (does this server's `CommandSender` implement Adventure's
  `Audience`?) with no separate build required.
  - **Practical effect:** one plugin jar (`PlayerGrowth-Bukkit-<version>.jar`)
    now covers Spigot, CraftBukkit, Paper, and Purpur. If you were
    previously running the Paper-specific jar, switch to the Bukkit jar on
    your next upgrade — it behaves identically and still gets native
    message delivery on Paper/Purpur automatically.
- **Added a `/pg` alias** for `/playergrowth` — `/pg reload` and `/pg help`
  now work exactly like their `/playergrowth` equivalents.
- **Added an update checker.** On startup, PlayerGrowth checks this
  project's GitHub repository for a newer release; if one exists, it logs
  a console warning and notifies online players holding
  `playergrowth.update-notify` (default: op) the next time they join.
  - New config key: `update-checker.enabled` (default `true`) — set to
    `false` to disable both the console warning and the in-game
    notification. Existing installs pick up this key automatically on
    upgrade.
  - New permission: `playergrowth.update-notify` (default: op).

---

## v0.1.3

**Reliability and integration-depth release.**

- **`/playergrowth reload` no longer blocks the main thread.** Reading
  config/localization files and reconnecting to the database now happen
  off the main thread; only the final step (restarting the growth ticker,
  refreshing already-online players) touches Bukkit's API and runs
  synchronously. A reload that previously could stall the server for a
  moment (especially on a slow DB reconnect) no longer does.
  - The reload command now reports back how long it took, formatted as
    seconds to three decimal places (e.g. `0.042s`).
- **Database schema version unified with the plugin's own version.**
  Previously an independent integer counter; now the same semver string
  `config.yml` already tracks. A migration step widens the existing
  version column automatically for upgrading installs.
- **Localization files are now replaced wholesale on upgrade** (after a
  timestamped backup) instead of merged. Previously, only *new* message
  keys reached an upgrading install — if a release changed the wording or
  color tags of an *existing* message, that change silently never reached
  anyone who'd already installed the plugin. Now translation updates
  actually land. (`config.yml`/`gender.yml` still use the merge behavior,
  since those carry admin customization that must be preserved.)
- **Messages now route through FlectonePulse's own pipeline** when the
  recipient is an online player FlectonePulse is already tracking — so
  `<fcolor:N>` and delivery match how FlectonePulse's own messages behave,
  instead of PlayerGrowth reimplementing that formatting itself. Falls
  back to PlayerGrowth's own rendering for console senders or whenever
  FlectonePulse isn't installed/ready.

---

## v0.1.2

**FlectonePulse integration + theming + config restructure.**

- **FlectonePulse server-ID resolution now uses FlectonePulse's real Java
  API** (`FlectonePulseAPI` → `FileFacade`) instead of re-parsing its
  `config.yml` off disk, with the old file-read approach kept only as a
  fallback for older/unreachable FlectonePulse versions.
- **Added `<primary>`/`<secondary>` theme-color tags** (configurable via
  `config.yml`'s `colors.*`) and `<fcolor:N>` support (FlectonePulse's own
  configured color palette), so every bundled message can be reskinned
  from one place, and stays visually consistent with FlectonePulse's own
  colors when it's installed.
- **Fixed a real bug where color gradients collapsed to a single flat
  color** on plain Spigot/CraftBukkit (non-Paper) servers — the legacy
  text serializer used only the 16 basic legacy colors instead of full
  RGB, so a gradient like FlectonePulse's default `<fcolor:1>` rendered
  as one solid color instead of a gradient. Fixed by using full hex-color
  legacy serialization.
- **Added a diagnostics log** reporting exactly why `<fcolor:N>` isn't
  resolving when it doesn't (FlectonePulse not installed, its API not
  ready yet, or its fcolor module disabled in its own config), instead of
  failing silently with no way to tell which case applied.
- **Restructured `config.yml`:** the old separate `config-version` and
  `plugin-version` keys were merged into one `version` field (the
  plugin's own semver, which also now drives config migration), and the
  `server` identifier moved from nested under `network.*` to a top-level
  key. Existing installs are migrated automatically; nothing to do
  manually.
