package com.imlucky;

import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;

import java.util.*;

public class GameManager {

    private final Set<UUID> hunters = new HashSet<>();
    private final Set<UUID> runners = new HashSet<>();
    private final Set<UUID> frozenRunners = new HashSet<>();
    private final Map<String, Integer> points = new HashMap<>();
    private final Map<UUID, Long> lastFreezeTimes = new HashMap<>();
    private static final long FREEZE_COOLDOWN = 1000L;
    private boolean gameFinished = false;

    public void setHunter(Player p) {
        hunters.add(p.getUniqueId());
        runners.remove(p.getUniqueId());
    }

    public void setRunner(Player p) {
        runners.add(p.getUniqueId());
        hunters.remove(p.getUniqueId());
    }

    public boolean hasRole(Player p) {
        return hunters.contains(p.getUniqueId()) || runners.contains(p.getUniqueId());
    }

    public void resetAllRoles() {
        hunters.clear();
        runners.clear();
        frozenRunners.clear();
    }

    public boolean isHunter(Player p) {
        return hunters.contains(p.getUniqueId());
    }

    public boolean isRunner(Player p) {
        return runners.contains(p.getUniqueId());
    }

    public void freezeRunner(Player p) {
        UUID uid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastFreeze = lastFreezeTimes.get(uid);
        if (lastFreeze != null && now - lastFreeze < FREEZE_COOLDOWN) {
            return;
        }
        lastFreezeTimes.put(uid, now);
        frozenRunners.add(uid);
    }

    public void unfreezeRunner(Player p) {
        frozenRunners.remove(p.getUniqueId());
    }

    public boolean isFrozen(Player p) {
        return frozenRunners.contains(p.getUniqueId());
    }

    public boolean allRunnersFrozen() {
        return !runners.isEmpty() && frozenRunners.containsAll(runners);
    }

    public void addPoints(String playerName, int pts) {
        points.put(playerName, points.getOrDefault(playerName, 0) + pts);
    }

    public Map<String, Integer> getPoints() {
        return points;
    }

    public void resetGame() {
        frozenRunners.clear();
        gameFinished = false;
    }

    public void finishGame() {
        hunters.clear();
        runners.clear();
        frozenRunners.clear();
        gameFinished = true;
    }

    public boolean isGameFinished() {
        return gameFinished;
    }

    public void loadPoints(Configuration config) {
        if (config.contains("points")) {
            for (String key : config.getConfigurationSection("points").getKeys(false)) {
                points.put(key, config.getInt("points." + key));
            }
        }
    }

    public void savePoints(Configuration config) {
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            config.set("points." + entry.getKey(), entry.getValue());
        }
    }

    public void resetAllPoints() {
        points.clear();
    }

    public void resetPoints(String playerName) {
        points.put(playerName, 0);
    }

    public void penalizeHunters() {
        for (UUID uid : hunters) {
            Player hunter = Bukkit.getPlayer(uid);
            if (hunter != null) {
                addPoints(hunter.getName(), -5);
            }
        }
    }

    public long getLastFreezeTime(Player p) {
        return lastFreezeTimes.getOrDefault(p.getUniqueId(), 0L);
    }
}
