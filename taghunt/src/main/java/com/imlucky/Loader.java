package com.imlucky;

import java.io.File;
import java.util.Arrays;
import java.util.logging.Logger;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class Loader extends JavaPlugin implements Listener {

    private static final Logger LOGGER = Logger.getLogger(Loader.class.getName());

    private NamespacedKey specialBowKey;
    private NamespacedKey abilitySelectorKey;

    private GameManager gameManager;
    private GameScoreboard gameScoreboard;
    private AbilityManager abilityManager;
    private AbilityListener abilityListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("bowdrop.yml", false);

        specialBowKey = new NamespacedKey(this, "special_bow");
        abilitySelectorKey = new NamespacedKey(this, "ability_selector");

        gameManager = new GameManager();
        gameManager.loadPoints(getConfig());
        gameScoreboard = new GameScoreboard(gameManager);
        abilityManager = new AbilityManager(this);
        abilityListener = new AbilityListener(this, gameManager, abilityManager);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(abilityListener, this);

        getCommand("sethunter").setExecutor(new SetHunterCommand());
        getCommand("setrunner").setExecutor(new SetRunnerCommand());
        getCommand("startgame").setExecutor(new StartGameCommand());
        getCommand("resetall").setExecutor(new ResetAllCommand());
        getCommand("resetpoints").setExecutor(new ResetPointsCommand());
        getCommand("challenge").setExecutor(new ChallengeCommand());
        getCommand("huntkit").setExecutor(new HuntkitCommand());
        getCommand("runkit").setExecutor(new RunkitCommand());
        getCommand("itemdrop").setExecutor(new ItemDropCommand());
        getCommand("ability").setExecutor(new AbilityCommand());

        // Particle effect for frozen runners
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (gameManager.isFrozen(p)) {
                        p.getWorld().spawnParticle(Particle.SNOWFLAKE,
                                p.getLocation().add(0, 2, 0), 2, 0.5, 0.5, 0.5, 0.01);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 50L);

        // Keep infinite terracotta stacks full
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null && item.hasItemMeta()) {
                            String display = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                            if ((item.getType() == Material.RED_TERRACOTTA
                                    && display.equals("Infinite Red Terracotta"))
                                    || (item.getType() == Material.BLUE_TERRACOTTA
                                            && display.equals("Infinite Blue Terracotta"))) {
                                item.setAmount(64);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 100L);

        LOGGER.info("TagHunt enabled");
    }

    @Override
    public void onDisable() {
        if (abilityListener != null) {
            abilityListener.clearBridgeEggs();
        }
        if (gameManager != null) {
            gameManager.savePoints(getConfig());
            saveConfig();
        }
        LOGGER.info("TagHunt disabled");
    }

    // ---- Core game event handlers ----

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player))
            return;
        Player damager = (Player) event.getDamager();
        Player target = (Player) event.getEntity();

        if (gameManager.isHunter(damager) && gameManager.isRunner(target)) {
            if (!gameManager.isFrozen(target)) {
                gameManager.freezeRunner(target);
                gameManager.addPoints(damager.getName(), 5);
                gameScoreboard.update();
                damager.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.YELLOW + "You froze "
                                + ChatColor.AQUA + target.getName()
                                + ChatColor.GRAY + " [" + ChatColor.GREEN + "+5 points" + ChatColor.GRAY + "]"));
                target.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.RED + "You were frozen by "
                                + ChatColor.GOLD + damager.getName()));
                Bukkit.broadcastMessage(ChatColor.AQUA + "❄ " + target.getName()
                        + ChatColor.YELLOW + " was frozen by " + ChatColor.GOLD + "🗡 " + damager.getName()
                        + ChatColor.GRAY + " [" + ChatColor.GREEN + "+5 points" + ChatColor.GRAY + "]");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
                }
                if (gameManager.allRunnersFrozen()) {
                    gameManager.addPoints(damager.getName(), 10);
                    gameScoreboard.update();
                    Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "🏆 " + damager.getName()
                            + ChatColor.YELLOW + " froze all runners and won!"
                            + ChatColor.GRAY + " [" + ChatColor.GREEN + "+10 points" + ChatColor.GRAY + "]");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(ChatColor.YELLOW + "" + ChatColor.BOLD + "GAME OVER!",
                                ChatColor.GOLD + "The hunter has won!", 10, 70, 20);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    }
                    gameManager.finishGame();
                    gameScoreboard.update();
                }
            }
            event.setCancelled(true);
        } else if (gameManager.isRunner(damager) && !gameManager.isFrozen(damager)
                && gameManager.isRunner(target) && gameManager.isFrozen(target)) {
            gameManager.unfreezeRunner(target);
            gameManager.addPoints(damager.getName(), 5);
            gameScoreboard.update();
            damager.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.YELLOW + "You unfroze "
                            + ChatColor.AQUA + target.getName()
                            + ChatColor.GRAY + " [" + ChatColor.GREEN + "+5 points" + ChatColor.GRAY + "]"));
            target.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GREEN + "You were unfrozen by "
                            + ChatColor.AQUA + damager.getName()));
            Bukkit.broadcastMessage(ChatColor.GOLD + "🗡 " + damager.getName()
                    + ChatColor.YELLOW + " unfroze " + ChatColor.AQUA + target.getName()
                    + ChatColor.GRAY + " [" + ChatColor.GREEN + "+5 points" + ChatColor.GRAY + "]");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!gameManager.isFrozen(p))
            return;
        if (System.currentTimeMillis() - gameManager.getLastFreezeTime(p) > 3000) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.RED + "You are frozen!"));
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            event.setTo(new Location(from.getWorld(), from.getX(), to.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        ItemStack item = event.getItem().getItemStack();
        if (item.getType() == Material.BOW && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                        .has(specialBowKey, PersistentDataType.BYTE)) {
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                }
            }.runTaskLater(this, 10L);
        }
    }

    @EventHandler
    public void onAbilitySelectorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta())
            return;
        if (!item.getItemMeta().getPersistentDataContainer()
                .has(abilitySelectorKey, PersistentDataType.BYTE))
            return;
        if (!gameManager.isRunner(player)) {
            player.sendMessage(ChatColor.RED + "Only runners can choose abilities.");
            return;
        }
        event.setCancelled(true);
        player.openInventory(abilityManager.createAbilityGUI());
    }

    // ---- Helper ----

    private ItemStack buildAbilitySelectorItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "⭐ Choose Ability");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right-click to open the",
                ChatColor.GRAY + "ability selection menu!"));
        meta.getPersistentDataContainer().set(abilitySelectorKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ---- Commands ----

    class SetHunterCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length != 1)
                return false;
            Player target = getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            if (gameManager.hasRole(target)) {
                sender.sendMessage(ChatColor.RED + target.getName() + " already has a role. Unset them first.");
                return true;
            }
            gameManager.setHunter(target);
            sender.sendMessage(ChatColor.GREEN + target.getName() + " set as hunter.");
            return true;
        }
    }

    class SetRunnerCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length != 1)
                return false;
            Player target = getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            if (gameManager.hasRole(target)) {
                sender.sendMessage(ChatColor.RED + target.getName() + " already has a role. Unset them first.");
                return true;
            }
            gameManager.setRunner(target);
            sender.sendMessage(ChatColor.GREEN + target.getName() + " set as runner.");
            return true;
        }
    }

    class StartGameCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            gameManager.resetGame();
            gameScoreboard.update();
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "GAME STARTED!", "", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            return true;
        }
    }

    class ResetAllCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            gameManager.resetAllRoles();
            abilityManager.clearAll();
            gameScoreboard.update();
            sender.sendMessage(ChatColor.GREEN + "All player roles and abilities have been reset.");
            return true;
        }
    }

    class ResetPointsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /resetpoints <all|player1 [player2 ...]>");
                return true;
            }
            if (args[0].equalsIgnoreCase("all")) {
                gameManager.resetAllPoints();
                sender.sendMessage(ChatColor.GREEN + "All player points have been reset.");
            } else {
                for (String playerName : args) {
                    gameManager.resetPoints(playerName);
                    sender.sendMessage(ChatColor.GREEN + "Points reset for " + playerName);
                }
            }
            gameScoreboard.update();
            return true;
        }
    }

    class ChallengeCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            new org.bukkit.scheduler.BukkitRunnable() {
                int timeLeft = 30;

                @Override
                public void run() {
                    if (gameManager.isGameFinished()) {
                        cancel();
                        return;
                    }
                    if (gameManager.allRunnersFrozen()) {
                        Bukkit.broadcastMessage(ChatColor.GREEN + "Challenge complete! Hunter wins!");
                        cancel();
                        return;
                    }
                    if (timeLeft == 0) {
                        if (!gameManager.allRunnersFrozen()) {
                            gameManager.penalizeHunters();
                            gameScoreboard.update();
                            Bukkit.broadcastMessage(ChatColor.RED + "Time's up! Runners win!"
                                    + ChatColor.GRAY + " [" + ChatColor.RED + "-5 hunter points"
                                    + ChatColor.GRAY + "]");
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "GAME OVER!",
                                        ChatColor.YELLOW + "The runners have won!", 10, 70, 20);
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                            }
                            gameManager.finishGame();
                        }
                        cancel();
                        return;
                    }
                    if (timeLeft == 30 || timeLeft == 15 || timeLeft <= 10) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                    new TextComponent(ChatColor.YELLOW.toString() + timeLeft + " seconds left!"));
                        }
                    }
                    timeLeft--;
                }
            }.runTaskTimer(Loader.this, 0L, 20L);
            return true;
        }
    }

    class HuntkitCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            Player player = (Player) sender;

            ItemStack terracotta = new ItemStack(Material.RED_TERRACOTTA, 64);
            ItemMeta tcMeta = terracotta.getItemMeta();
            tcMeta.setDisplayName(ChatColor.RED + "Infinite Red Terracotta");
            terracotta.setItemMeta(tcMeta);
            player.getInventory().setItemInOffHand(terracotta);

            ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
            ItemMeta swordMeta = sword.getItemMeta();
            swordMeta.setUnbreakable(true);
            sword.setItemMeta(swordMeta);
            sword.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
            player.getInventory().addItem(sword);

            ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
            ItemMeta pickMeta = pickaxe.getItemMeta();
            pickMeta.setUnbreakable(true);
            pickaxe.setItemMeta(pickMeta);
            player.getInventory().addItem(pickaxe);

            player.sendMessage(ChatColor.GREEN + "You received the hunter kit!");
            return true;
        }
    }

    class RunkitCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            Player player = (Player) sender;

            ItemStack terracotta = new ItemStack(Material.BLUE_TERRACOTTA, 64);
            ItemMeta tcMeta = terracotta.getItemMeta();
            tcMeta.setDisplayName(ChatColor.BLUE + "Infinite Blue Terracotta");
            terracotta.setItemMeta(tcMeta);
            player.getInventory().setItemInOffHand(terracotta);

            ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
            ItemMeta swordMeta = sword.getItemMeta();
            swordMeta.setUnbreakable(true);
            sword.setItemMeta(swordMeta);
            sword.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
            player.getInventory().addItem(sword);

            ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
            ItemMeta pickMeta = pickaxe.getItemMeta();
            pickMeta.setUnbreakable(true);
            pickaxe.setItemMeta(pickMeta);
            player.getInventory().addItem(pickaxe);

            // Remove any previous ability items, then give the ability selector
            abilityManager.removeAbilityItems(player);
            player.getInventory().addItem(buildAbilitySelectorItem());

            player.sendMessage(ChatColor.GREEN + "You received the runner kit!");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Right-click the "
                    + ChatColor.BOLD + "⭐ Choose Ability" + ChatColor.LIGHT_PURPLE
                    + " item or use " + ChatColor.BOLD + "/ability"
                    + ChatColor.LIGHT_PURPLE + " to choose your runner ability!");
            return true;
        }
    }

    class ItemDropCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            Location dropLocation;
            if (args.length == 0) {
                FileConfiguration bowDropConfig = YamlConfiguration.loadConfiguration(
                        new File(getDataFolder(), "bowdrop.yml"));
                String world = bowDropConfig.getString("world", "world");
                double x = bowDropConfig.getDouble("x");
                double y = bowDropConfig.getDouble("y");
                double z = bowDropConfig.getDouble("z");
                dropLocation = new Location(Bukkit.getWorld(world), x, y, z);
            } else {
                Player player = (Player) sender;
                Block targetBlock = player.getTargetBlock(null, 200);
                if (targetBlock == null) {
                    sender.sendMessage(ChatColor.RED + "No target block found.");
                    return true;
                }
                dropLocation = targetBlock.getLocation().add(0, 1, 0);
            }
            dropLocation.getWorld().strikeLightningEffect(dropLocation);
            ItemStack bow = new ItemStack(Material.BOW);
            ItemMeta meta = bow.getItemMeta();
            meta.getPersistentDataContainer().set(specialBowKey, PersistentDataType.BYTE, (byte) 1);
            bow.setItemMeta(meta);
            bow.addUnsafeEnchantment(Enchantment.INFINITY, 1);
            bow.addUnsafeEnchantment(Enchantment.PUNCH, 2);
            dropLocation.getWorld().dropItem(dropLocation, bow);
            return true;
        }
    }

    class AbilityCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            Player player = (Player) sender;
            if (!gameManager.isRunner(player)) {
                player.sendMessage(ChatColor.RED + "Only runners can choose abilities.");
                return true;
            }
            player.openInventory(abilityManager.createAbilityGUI());
            return true;
        }
    }
}
