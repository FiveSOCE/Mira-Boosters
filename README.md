# MiraBoosters

Timed multiplier boosters for the Mira Paper 1.21.11 / Java 21 ecosystem.

## Download

Current release: **v0.1.0**

[**Download MiraBoosters v0.1.0**](https://github.com/FiveSOCE/Mira-Boosters/releases/download/v0.1.0/MiraBoosters-0.1.0.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Boosters/releases)

## Features

- timed global boosters
- timed personal boosters
- persistent booster state
- multiplier channels for XP, mob drops, shop sell value, crate chance and spawner rate
- stacking modes: MULTIPLY, MAX and ADDITIVE
- native XP multiplier handling
- native mob-drop multiplier handling
- public `MiraBoostersApi` through Bukkit ServicesManager
- PlaceholderAPI support

Other Mira plugins can consume the generic multiplier channels instead of MiraBoosters directly modifying their internals.

## Commands

```text
/booster list
/booster global <channel> <multiplier> <duration> [stacking]
/booster player <player> <channel> <multiplier> <duration> [stacking]
/booster clear global <channel>
/booster clear player <player> <channel>
```

## Requirements

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional

## Building

```bash
gradle clean build
```

Output:

```text
build/libs/MiraBoosters-0.1.0.jar
```
