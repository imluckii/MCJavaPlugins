package com.imlucky;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class GameScoreboard {

    private final Scoreboard scoreboard;
    private final Objective objective;
    private final GameManager gameManager;

    public GameScoreboard(GameManager gameManager) {
        this.gameManager = gameManager;
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective("points", Criteria.DUMMY, ChatColor.BOLD + "POINTS");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void update() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            gameManager.getPoints().putIfAbsent(p.getName(), 0);
        }
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
        gameManager.getPoints().entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> {
                    String playerName = e.getKey();
                    Player player = Bukkit.getPlayer(playerName);
                    if (player != null) {
                        if (gameManager.isHunter(player)) {
                            playerName = ChatColor.RED + "" + ChatColor.BOLD + playerName;
                        } else if (gameManager.isRunner(player)) {
                            playerName = ChatColor.WHITE + playerName;
                        }
                    }
                    objective.getScore(playerName).setScore(e.getValue());
                });
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(scoreboard);
        }
    }
}
