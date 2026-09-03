package com.mira.boosters;

import com.mira.boosters.api.MiraBoostersApi;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MiraBoostersPlugin extends JavaPlugin implements Listener, TabExecutor, MiraBoostersApi {
    private final Map<UUID, Booster> boosters = new LinkedHashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "boosters.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
        cleanupExpired();
        var command = Objects.requireNonNull(getCommand("booster"), "booster command missing");
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getServicesManager().register(MiraBoostersApi.class, this, this, ServicePriority.Normal);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) new BoosterExpansion().register();
        Bukkit.getScheduler().runTaskTimer(this, this::cleanupExpired, 1200L, 1200L);
        getLogger().info("MiraBoosters v" + getPluginMeta().getVersion() + " enabled with " + boosters.size() + " active booster(s).");
    }

    @Override
    public void onDisable() {
        saveData();
        getServer().getServicesManager().unregisterAll(this);
    }

    @Override
    public double multiplier(String channel, UUID player) {
        String key = channel(channel);
        List<Double> values = boosters.values().stream()
                .filter(this::active)
                .filter(b -> b.channel().equals(key))
                .filter(b -> b.owner() == null || Objects.equals(b.owner(), player))
                .map(Booster::multiplier)
                .toList();
        return combine(values);
    }

    @Override
    public double globalMultiplier(String channel) {
        String key = channel(channel);
        return combine(boosters.values().stream().filter(this::active).filter(b -> b.owner() == null && b.channel().equals(key)).map(Booster::multiplier).toList());
    }

    @Override
    public boolean activateGlobal(String channel, double multiplier, long durationSeconds) {
        return activate(null, channel, multiplier, durationSeconds);
    }

    @Override
    public boolean activatePersonal(UUID player, String channel, double multiplier, long durationSeconds) {
        if (player == null) return false;
        return activate(player, channel, multiplier, durationSeconds);
    }

    @Override
    public Set<String> activeChannels(UUID player) {
        Set<String> out = new TreeSet<>();
        for (Booster booster : boosters.values()) if (active(booster) && (booster.owner() == null || Objects.equals(booster.owner(), player))) out.add(booster.channel());
        return Collections.unmodifiableSet(out);
    }

    private boolean activate(UUID owner, String rawChannel, double multiplier, long durationSeconds) {
        String channel = channel(rawChannel);
        if (channel.isBlank() || !Double.isFinite(multiplier) || multiplier <= 0 || durationSeconds <= 0) return false;
        double cap = Math.max(1.0, getConfig().getDouble("max-effective-multiplier", 10.0));
        if (multiplier > cap) return false;
        long expires = System.currentTimeMillis() + durationSeconds * 1000L;
        Booster booster = new Booster(UUID.randomUUID(), channel, multiplier, expires, owner);
        boosters.put(booster.id(), booster);
        saveData();
        return true;
    }

    private double combine(Collection<Double> values) {
        if (values.isEmpty()) return 1.0;
        String mode = getConfig().getString("stack-mode", "MULTIPLY").toUpperCase(Locale.ROOT);
        double result;
        switch (mode) {
            case "MAX" -> result = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            case "ADDITIVE", "ADD" -> result = 1.0 + values.stream().mapToDouble(v -> v - 1.0).sum();
            default -> {
                result = 1.0;
                for (double value : values) result *= value;
            }
        }
        return Math.min(Math.max(0.0, result), Math.max(1.0, getConfig().getDouble("max-effective-multiplier", 10.0)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent event) {
        double multiplier = multiplier("xp", event.getPlayer().getUniqueId());
        if (multiplier == 1.0 || event.getAmount() <= 0) return;
        event.setAmount(Math.max(0, (int) Math.round(event.getAmount() * multiplier)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player) return;
        double multiplier = multiplier("mob_drops", killer.getUniqueId());
        if (multiplier == 1.0 || event.getDrops().isEmpty()) return;
        List<ItemStack> replacement = new ArrayList<>();
        for (ItemStack original : event.getDrops()) {
            int total = Math.max(0, (int) Math.round(original.getAmount() * multiplier));
            int max = Math.max(1, original.getMaxStackSize());
            while (total > 0) {
                ItemStack copy = original.clone();
                int amount = Math.min(max, total);
                copy.setAmount(amount);
                replacement.add(copy);
                total -= amount;
            }
        }
        event.getDrops().clear();
        event.getDrops().addAll(replacement);
    }

    private void cleanupExpired() {
        int before = boosters.size();
        boosters.values().removeIf(b -> !active(b));
        if (boosters.size() != before) saveData();
    }

    private boolean active(Booster booster) { return booster.expiresAt() > System.currentTimeMillis(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            msg(sender, "&dActive Boosters:");
            UUID viewer = sender instanceof Player player ? player.getUniqueId() : null;
            int shown = 0;
            for (Booster booster : boosters.values()) {
                if (!active(booster)) continue;
                if (booster.owner() != null && !Objects.equals(booster.owner(), viewer) && !sender.hasPermission("miraboosters.admin")) continue;
                String scope = booster.owner() == null ? "Global" : name(booster.owner());
                msg(sender, "&7- &f" + booster.channel() + " &ax" + format(booster.multiplier()) + " &7" + scope + " &8(" + formatDuration((booster.expiresAt() - System.currentTimeMillis()) / 1000L) + ")");
                shown++;
            }
            if (shown == 0) msg(sender, "&7None.");
            return true;
        }
        if (!sender.hasPermission("miraboosters.admin")) { msg(sender, "&cYou do not have permission."); return true; }
        if (args[0].equalsIgnoreCase("global")) {
            if (args.length < 4) { msg(sender, "&eUsage: /booster global <channel> <multiplier> <duration>"); return true; }
            double multiplier = number(args[2], -1);
            long seconds = duration(args[3]);
            if (!activateGlobal(args[1], multiplier, seconds)) { msg(sender, "&cInvalid booster values."); return true; }
            if (getConfig().getBoolean("broadcast-global", true)) Bukkit.broadcastMessage(color("&6&lBOOSTER &eGlobal &f" + channel(args[1]) + " &ebooster activated at &ax" + format(multiplier) + " &efor &f" + formatDuration(seconds) + "&e!"));
            else msg(sender, "&aGlobal booster activated.");
            return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 5) { msg(sender, "&eUsage: /booster give <player> <channel> <multiplier> <duration>"); return true; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            double multiplier = number(args[3], -1);
            long seconds = duration(args[4]);
            if (!activatePersonal(target.getUniqueId(), args[2], multiplier, seconds)) { msg(sender, "&cInvalid booster values."); return true; }
            msg(sender, "&aActivated &f" + channel(args[2]) + " x" + format(multiplier) + " &afor &f" + name(target.getUniqueId()) + " &afor &f" + formatDuration(seconds) + "&a.");
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            if (args.length < 2) { msg(sender, "&eUsage: /booster clear <all|global|player> [player]"); return true; }
            int before = boosters.size();
            if (args[1].equalsIgnoreCase("all")) boosters.clear();
            else if (args[1].equalsIgnoreCase("global")) boosters.values().removeIf(b -> b.owner() == null);
            else if (args[1].equalsIgnoreCase("player") && args.length >= 3) {
                UUID uuid = Bukkit.getOfflinePlayer(args[2]).getUniqueId();
                boosters.values().removeIf(b -> Objects.equals(b.owner(), uuid));
            } else { msg(sender, "&cUnknown clear target."); return true; }
            saveData();
            msg(sender, "&aCleared &f" + (before - boosters.size()) + " &abooster(s).");
            return true;
        }
        msg(sender, "&eUsage: /booster <list|global|give|clear>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("list"));
            if (sender.hasPermission("miraboosters.admin")) values.addAll(List.of("global", "give", "clear"));
            return match(args[0], values);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return match(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if ((args.length == 2 && args[0].equalsIgnoreCase("global")) || (args.length == 3 && args[0].equalsIgnoreCase("give"))) return match(args[args.length - 1], List.of("xp", "mob_drops", "shop_sell", "crate_chance", "spawner_rate"));
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) return match(args[1], List.of("all", "global", "player"));
        if (args.length == 3 && args[0].equalsIgnoreCase("clear") && args[1].equalsIgnoreCase("player")) return match(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        return List.of();
    }

    private void loadData() {
        boosters.clear();
        ConfigurationSection root = data.getConfigurationSection("boosters");
        if (root == null) return;
        for (String idRaw : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idRaw);
                String channel = channel(root.getString(idRaw + ".channel", ""));
                double multiplier = root.getDouble(idRaw + ".multiplier", 1.0);
                long expires = root.getLong(idRaw + ".expires-at", 0L);
                String ownerRaw = root.getString(idRaw + ".owner");
                UUID owner = ownerRaw == null || ownerRaw.isBlank() ? null : UUID.fromString(ownerRaw);
                if (!channel.isBlank() && multiplier > 0 && expires > 0) boosters.put(id, new Booster(id, channel, multiplier, expires, owner));
            } catch (Exception ignored) { }
        }
    }

    private void saveData() {
        if (data == null) return;
        data.set("boosters", null);
        for (Booster booster : boosters.values()) {
            String path = "boosters." + booster.id();
            data.set(path + ".channel", booster.channel());
            data.set(path + ".multiplier", booster.multiplier());
            data.set(path + ".expires-at", booster.expiresAt());
            data.set(path + ".owner", booster.owner() == null ? null : booster.owner().toString());
        }
        try { data.save(dataFile); } catch (IOException ex) { getLogger().severe("Could not save boosters.yml: " + ex.getMessage()); }
    }

    private String channel(String raw) { return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_'); }
    private double number(String raw, double fallback) { try { return Double.parseDouble(raw); } catch (Exception ex) { return fallback; } }
    private long duration(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String s = raw.toLowerCase(Locale.ROOT).trim();
        long factor = 1;
        char last = s.charAt(s.length() - 1);
        if (Character.isLetter(last)) {
            factor = switch (last) { case 's' -> 1L; case 'm' -> 60L; case 'h' -> 3600L; case 'd' -> 86400L; default -> -1L; };
            s = s.substring(0, s.length() - 1);
        }
        try { return factor < 0 ? -1 : Long.parseLong(s) * factor; } catch (Exception ex) { return -1; }
    }
    private String formatDuration(long seconds) { if (seconds >= 86400) return (seconds / 86400) + "d"; if (seconds >= 3600) return (seconds / 3600) + "h"; if (seconds >= 60) return (seconds / 60) + "m"; return Math.max(0, seconds) + "s"; }
    private String format(double value) { return String.format(Locale.US, value == Math.rint(value) ? "%.0f" : "%.2f", value); }
    private String name(UUID uuid) { String name = Bukkit.getOfflinePlayer(uuid).getName(); return name == null ? uuid.toString() : name; }
    private void msg(CommandSender sender, String raw) { sender.sendMessage(color(getConfig().getString("messages.prefix", "&5&lMira &8>> &r") + raw)); }
    private String color(String raw) { return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw); }
    private static List<String> match(String prefix, Collection<String> values) { String lower = prefix.toLowerCase(Locale.ROOT); return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList(); }

    private record Booster(UUID id, String channel, double multiplier, long expiresAt, UUID owner) { }

    private final class BoosterExpansion extends PlaceholderExpansion {
        @Override public String getIdentifier() { return "miraboosters"; }
        @Override public String getAuthor() { return "FiveS"; }
        @Override public String getVersion() { return getPluginMeta().getVersion(); }
        @Override public boolean persist() { return true; }
        @Override public String onRequest(OfflinePlayer player, String params) {
            String lower = params.toLowerCase(Locale.ROOT);
            if (lower.startsWith("global_")) return format(globalMultiplier(lower.substring(7)));
            if (lower.startsWith("channel_")) return format(multiplier(lower.substring(8), player == null ? null : player.getUniqueId()));
            if (lower.equals("active_count")) return Integer.toString(activeChannels(player == null ? null : player.getUniqueId()).size());
            return null;
        }
    }
}
