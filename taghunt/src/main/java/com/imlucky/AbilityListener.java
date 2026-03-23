package com.imlucky;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.Location;

import java.util.*;

public class AbilityListener implements Listener {

    private static final long DASH_COOLDOWN_MS = 3000L;
    private static final long GRAPPLE_COOLDOWN_MS = 8000L;
    private static final double DASH_SPEED = 1.2;
    private static final double JUMP_POWER = 0.8;
    private static final double GROUND_CHECK_OFFSET = 0.1;

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final AbilityManager abilityManager;

    private final Set<UUID> bridgeEggs = new HashSet<>();
    private final Map<UUID, Long> dashCooldowns = new HashMap<>();
    private final Map<UUID, Long> grapplingCooldowns = new HashMap<>();

    public AbilityListener(JavaPlugin plugin, GameManager gameManager, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.abilityManager = abilityManager;
    }

    // ---- GUI handling ----

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!AbilityManager.GUI_TITLE.equals(event.getView().getTitle()))
            return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE)
            return;

        AbilityType selected = null;
        for (AbilityType type : AbilityType.values()) {
            if (clicked.getType() == type.getIcon()
                    && clicked.hasItemMeta()
                    && clicked.getItemMeta().hasDisplayName()
                    && ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                            .equals(type.getDisplayName())) {
                selected = type;
                break;
            }
        }
        if (selected == null)
            return;

        abilityManager.setAbility(player, selected);
        abilityManager.giveAbilityItem(player, selected);
        player.closeInventory();
    }

    // ---- Ability usage ----

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        if (!gameManager.isRunner(player) || gameManager.isFrozen(player))
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!abilityManager.isAbilityItem(item))
            return;

        AbilityType abilityType = abilityManager.getAbilityFromItem(item);
        if (abilityType == AbilityType.DASH_DOUBLE_JUMP) {
            event.setCancelled(true);
            handleDash(player);
        }
        // GRAPPLING_HOOK is handled via PlayerFishEvent.
        // BRIDGE_EGG is handled via ProjectileLaunchEvent (egg auto-thrown by the
        // server).
    }

    private void handleDash(Player player) {
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = dashCooldowns.get(uid);
        if (last != null && now - last < DASH_COOLDOWN_MS) {
            long remaining = (DASH_COOLDOWN_MS - (now - last)) / 1000 + 1;
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.RED + "Ability on cooldown! " + remaining + "s"));
            return;
        }
        dashCooldowns.put(uid, now);

        Vector dir = player.getLocation().getDirection();
        if (isStandingOnSolidBlock(player)) {
            // Dash forward
            dir.setY(0.2).normalize().multiply(DASH_SPEED);
            player.setVelocity(dir);
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                    player.getLocation().add(0, 1, 0), 5, 0.5, 0.3, 0.5, 0);
        } else {
            // Double jump
            Vector vel = player.getVelocity();
            vel.setY(JUMP_POWER);
            player.setVelocity(vel);
            player.getWorld().spawnParticle(Particle.CLOUD,
                    player.getLocation().add(0, 0.5, 0), 12, 0.3, 0.1, 0.3, 0.05);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.3f);
    }

    private boolean isStandingOnSolidBlock(Player player) {
        return player.getLocation().clone().subtract(0, GROUND_CHECK_OFFSET, 0)
                .getBlock().getType().isSolid();
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY)
            return;

        Player player = event.getPlayer();
        if (!gameManager.isRunner(player) || gameManager.isFrozen(player))
            return;

        // Check either hand for the grappling-hook ability item
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean hasGrapple = (abilityManager.isAbilityItem(mainHand)
                && abilityManager.getAbilityFromItem(mainHand) == AbilityType.GRAPPLING_HOOK)
                || (abilityManager.isAbilityItem(offHand)
                        && abilityManager.getAbilityFromItem(offHand) == AbilityType.GRAPPLING_HOOK);
        if (!hasGrapple)
            return;

        Entity caught = event.getCaught();
        if (!(caught instanceof Player))
            return;
        Player target = (Player) caught;
        if (!gameManager.isRunner(target))
            return; // can only grapple teammates

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = grapplingCooldowns.get(uid);
        if (last != null && now - last < GRAPPLE_COOLDOWN_MS) {
            long remaining = (GRAPPLE_COOLDOWN_MS - (now - last)) / 1000 + 1;
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.RED + "Grapple on cooldown! " + remaining + "s"));
            event.setCancelled(true);
            return;
        }
        grapplingCooldowns.put(uid, now);

        // Pull the caster towards the target
        Location from = player.getLocation();
        Location to = target.getLocation();
        Vector pull = to.toVector().subtract(from.toVector()).normalize().multiply(1.6);
        pull.setY(Math.min(pull.getY() + 0.4, 1.0));
        player.setVelocity(pull);

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.YELLOW + "Grappled to "
                        + ChatColor.AQUA + target.getName() + ChatColor.YELLOW + "!"));
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.CLOUD, from, 15, 0.3, 0.3, 0.3, 0.08);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg))
            return;
        if (!(event.getEntity().getShooter() instanceof Player))
            return;

        Player player = (Player) event.getEntity().getShooter();
        if (!gameManager.isRunner(player) || gameManager.isFrozen(player))
            return;
        if (abilityManager.getAbility(player) != AbilityType.BRIDGE_EGG)
            return;

        UUID eggId = event.getEntity().getUniqueId();
        Egg egg = (Egg) event.getEntity();
        bridgeEggs.add(eggId);

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!egg.isValid() || !bridgeEggs.contains(eggId)) {
                    bridgeEggs.remove(eggId);
                    cancel();
                    return;
                }
                Location loc = egg.getLocation();
                Block below = loc.getBlock().getRelative(0, -1, 0);
                if (below.getType() == Material.AIR
                        || below.getType() == Material.CAVE_AIR
                        || below.getType() == Material.VOID_AIR) {
                    below.setType(Material.OAK_PLANKS);
                    loc.getWorld().spawnParticle(Particle.BLOCK,
                            below.getLocation().add(0.5, 1.0, 0.5), 4, 0.3, 0.1, 0.3, 0,
                            Material.OAK_PLANKS.createBlockData());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        bridgeEggs.remove(event.getEntity().getUniqueId());
    }

    /** Called on plugin shutdown to release tracked state. */
    public void clearBridgeEggs() {
        bridgeEggs.clear();
    }
}
