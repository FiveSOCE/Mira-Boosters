# MiraBoosters

MiraBoosters provides timed global and personal multiplier boosts for the Mira Paper server suite. It exposes reusable multiplier channels that other Mira plugins can consume without each plugin implementing its own booster system.

## Download

[**Download MiraBoosters v0.1.1**](https://github.com/FiveSOCE/Mira-Boosters/releases/download/v0.1.1/MiraBoosters-0.1.1.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional
- MiraCore 0.2.0 or newer
- MiraShop optional integration
- MiraCrates optional integration
- MiraSpawners optional integration

## How MiraBoosters Works

Boosters can be global or assigned to a specific player. Each booster targets a named multiplier channel such as XP, mob drops, shop sell value, crate chance or spawner rate. In v0.1.1 the stacking mode is stored per booster instead of being one server-wide behavior. `MULTIPLY` boosters multiply together, `ADDITIVE` boosters contribute their bonus above 1.0, and `MAX` boosters establish a minimum effective multiplier floor. Mixed modes can safely coexist on the same channel, subject to the configured maximum effective multiplier.

MiraBoosters handles XP and mob-drop multipliers natively. Other Mira plugins can query the public `MiraBoostersApi` for the effective multiplier on their own channels. Booster state persists across restarts and PlaceholderAPI can expose booster information to displays.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/booster list` | `miraboosters.use` | Lists active boosters. |
| `/booster global <channel> <multiplier> <duration> [stacking]` | `miraboosters.admin` | Creates a timed global booster. |
| `/booster player <player> <channel> <multiplier> <duration> [stacking]` | `miraboosters.admin` | Creates a timed personal booster for a player. |
| `/booster give ...` | `miraboosters.admin` | Administrative booster-give flow supported by the command handler. |
| `/booster clear global [channel]` | `miraboosters.admin` | Clears global boosters, optionally limited to one channel. |
| `/booster clear player <player> [channel]` | `miraboosters.admin` | Clears a player's boosters, optionally limited to one channel. |
| `/booster clear all` | `miraboosters.admin` | Clears every active booster. |

Aliases: `/boosters`

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `miraboosters.use` | Everyone | Allows normal booster viewing/use. |
| `miraboosters.admin` | OP | Allows creating, giving and clearing boosters. |


## Integration

MiraBoosters registers `MiraBoostersApi` through Bukkit services and MiraCore. Existing three/four-argument activation methods remain compatible and use the configured default stacking mode, while overloads allow integrations to specify `MULTIPLY`, `MAX` or `ADDITIVE` per booster. MiraSpawners consumes the `spawner_rate` channel directly.
