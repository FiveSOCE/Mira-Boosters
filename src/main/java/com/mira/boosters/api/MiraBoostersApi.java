package com.mira.boosters.api;

import java.util.Set;
import java.util.UUID;

public interface MiraBoostersApi {
    double multiplier(String channel, UUID player);
    double globalMultiplier(String channel);

    boolean activateGlobal(String channel, double multiplier, long durationSeconds);
    boolean activateGlobal(String channel, double multiplier, long durationSeconds, String stackingMode);

    boolean activatePersonal(UUID player, String channel, double multiplier, long durationSeconds);
    boolean activatePersonal(UUID player, String channel, double multiplier, long durationSeconds, String stackingMode);

    int clearGlobal(String channel);
    int clearPersonal(UUID player, String channel);

    Set<String> activeChannels(UUID player);
}
