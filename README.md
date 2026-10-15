# BlissGems Fabric Port (Minecraft 26.3)

An independent early Fabric reimplementation inspired by the gem types and high-level gameplay ideas present in the supplied BlissGems 5.1.7 Paper plugin. This is **not a drop-in, feature-complete port** and is not affiliated with the original plugin author.

## What this first port includes

- Fabric mod metadata for Minecraft Java Edition 26.3.
- Eight core gem types: Astra, Fire, Flux, Life, Puff, Speed, Strength, and Wealth.
- `/bliss help` and `/bliss info`.
- `/bliss give <player> <gem> <1|2>` for moderators/operators.
- `/bliss ability primary` and `/bliss ability secondary`.
- Basic passive potion effects while a gem is held in the main hand or offhand.
- A simple per-player ability cooldown.
- GitHub Actions workflow that builds the mod JAR and uploads it as a downloadable Actions artifact. Tagging a commit as `v0.1.0` (or another `v*` tag) also publishes the JAR as a GitHub Release.

## Important limitations

This is a starter port. It does not yet reproduce every original Paper feature. In particular, energy and death handling, anti-duplication and soulbinding, custom inventories and GUIs, all original abilities, mythic/Gold gems, Villager and Golden Dream events, integrations with WorldGuard/ProtocolLib/Citizens/Geyser, recipe sets, cosmetic effects, and exact config compatibility are not implemented. Gem items currently use vanilla items with a custom display name; they are not registered as separate Minecraft item types. Ability use is command-based rather than mapped to the original click controls.

## Commands

- `/bliss help`
- `/bliss info`
- `/bliss give <player> <gem> <1|2>` — moderator permission required
- `/bliss ability primary`
- `/bliss ability secondary`

Gem IDs: `astra`, `fire`, `flux`, `life`, `puff`, `speed`, `strength`, `wealth`.

Example: `/bliss give YourPlayerName fire 1`, then hold the gem in your main hand or offhand and use `/bliss ability primary`.

## Build the JAR on GitHub

1. Create a new GitHub repository and upload the **contents** of this folder (not the ZIP itself).
2. Open the repository's **Actions** tab and enable workflows if GitHub prompts you.
3. Push to `main`/`master` or choose **Build BlissGems Fabric Mod → Run workflow**.
4. Open the completed workflow run and download the `BlissGems-Fabric-26.3` artifact. The `.jar` is inside that download.
5. To publish a Release, create and push a version tag such as `v0.1.0`. The workflow attaches the built JAR to that release.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API for 26.3
- Java 25 to build and run

Minecraft/Fabric version pins follow the current Fabric example project for 26.3. As with any new-version mod, the first GitHub Actions build is the definitive compile check; this environment has Java 21 and no internet access, so the build cannot be executed locally here.
