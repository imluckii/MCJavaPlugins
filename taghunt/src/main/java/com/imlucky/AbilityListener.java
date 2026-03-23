package com.imlucky;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class AbilityListener implements Listener {

    private static final long DASH_COOLDOWN_MS = 60_000L;
    private static final long HOP_COOLDOWN_MS = 30_000L;
    private static final long GRAPPLE_COOLDOWN_MS = 30_000L;
    private static final long BRIDGE_EGG_COOLDOWN_MS = 1_000L;
    private static final long BRIDGE_PLACE_DELAY_TICKS = 2L;
    private static final double DASH_HORIZONTAL_SPEED = 1.45;
    private static final double DASH_VERTICAL_BOOST = 0.28;
    private static final double HOP_HORIZONTAL_SPEED = 0.35;
    private static final double HOP_VERTICAL_BOOST = 0.74;
    private static final double GRAPPLE_STOP_DISTANCE = 2.0;
    private static final double PLAYER_GRAPPLE_DRAG = 0.65;
    private static final double PLAYER_GRAPPLE_ACCEL_BASE = 0.12;
    private static final double PLAYER_GRAPPLE_ACCEL_SCALE = 0.045;
    private static final double PLAYER_GRAPPLE_ACCEL_CAP = 0.55;
    private static final double PLAYER_GRAPPLE_VERTICAL_SCALE = 0.035;
    private static final double PLAYER_GRAPPLE_VERTICAL_BIAS = 0.06;
    private static final double GROUND_GRAPPLE_DRAG = 0.45;
    private static final double GROUND_GRAPPLE_ACCEL_BASE = 0.30;
    private static final double GROUND_GRAPPLE_ACCEL_SCALE = 0.11;
    private static final double GROUND_GRAPPLE_ACCEL_CAP = 1.30;
    private static final double GROUND_GRAPPLE_VERTICAL_SCALE = 0.10;
    private static final double GROUND_GRAPPLE_VERTICAL_BIAS = 0.16;

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final AbilityManager abilityManager;

    private final Map<UUID, BridgeTrail> bridgeEggs = new HashMap<>();
    private final Set<BridgeBlockKey> bridgeBlocks = new HashSet<>();
    private final Map<UUID, Long> dashCooldowns = new HashMap<>();
    private final Map<UUID, Long> hopCooldowns = new HashMap<>();
    private final Map<UUID, Long> grapplingCooldowns = new HashMap<>();
    private final Map<UUID, Long> bridgeEggCooldowns = new HashMap<>();
    private final Map<UUID, ActiveGrapple> activeGrapples = new HashMap<>();
    private final Map<UUID, PendingGrapple> pendingGrapples = new HashMap<>();

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
        if (abilityType == AbilityType.GRAPPLING_HOOK && activeGrapples.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            stopActiveGrapple(player, true);
            sendActionBar(player, ChatColor.YELLOW + "Grapple released.");
            return;
        }

        if (abilityType == AbilityType.GRAPPLING_HOOK && pendingGrapples.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            stopPendingGrapple(player, true);
            sendActionBar(player, ChatColor.YELLOW + "Grapple cancelled.");
            return;
        }

        if (abilityType == AbilityType.DASH_DOUBLE_JUMP) {
            event.setCancelled(true);
            if (item.getType() == Material.SLIME_BALL) {
                handleHop(player);
            } else {
                handleDash(player);
            }
        }
    }

    private void handleDash(Player player) {
        UUID uid = player.getUniqueId();
        long remaining = getRemainingCooldown(dashCooldowns, uid, DASH_COOLDOWN_MS);
        if (remaining > 0L) {
            sendActionBar(player, ChatColor.RED + "Dash on cooldown! " + formatSeconds(remaining));
            return;
        }
        dashCooldowns.put(uid, System.currentTimeMillis());

        Vector lookDirection = player.getLocation().getDirection();
        Vector horizontal = lookDirection.clone().setY(0);
        if (horizontal.lengthSquared() < 0.0001) {
            horizontal = player.getVelocity().clone().setY(0);
        }
        if (horizontal.lengthSquared() < 0.0001) {
            horizontal = new Vector(0, 0, 1);
        }

        horizontal.normalize().multiply(DASH_HORIZONTAL_SPEED);
        double dashY = Math.max(DASH_VERTICAL_BOOST, Math.min(0.42, player.getVelocity().getY() + 0.1));
        player.setVelocity(new Vector(horizontal.getX(), dashY, horizontal.getZ()));
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                player.getLocation().add(0, 1, 0), 5, 0.5, 0.3, 0.5, 0);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.3f);
        sendActionBar(player, ChatColor.AQUA + "Dashed forward! "
                + ChatColor.GRAY + "(" + ChatColor.YELLOW + "60s cooldown" + ChatColor.GRAY + ")");
    }

    private void handleHop(Player player) {
        long remaining = getRemainingCooldown(hopCooldowns, player.getUniqueId(), HOP_COOLDOWN_MS);
        if (remaining > 0L) {
            sendActionBar(player, ChatColor.RED + "Hop on cooldown! " + formatSeconds(remaining));
            return;
        }

        hopCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        Vector horizontal = player.getLocation().getDirection().clone().setY(0);
        if (horizontal.lengthSquared() < 0.0001) {
            horizontal = new Vector(0, 0, 1);
        }
        horizontal.normalize().multiply(HOP_HORIZONTAL_SPEED);
        player.setVelocity(new Vector(horizontal.getX(), HOP_VERTICAL_BOOST, horizontal.getZ()));
        player.getWorld().spawnParticle(Particle.CLOUD,
                player.getLocation().add(0, 0.5, 0), 14, 0.35, 0.15, 0.35, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.9f, 1.2f);
        sendActionBar(player, ChatColor.GREEN + "Hop used! "
                + ChatColor.GRAY + "(" + ChatColor.YELLOW + "30s cooldown" + ChatColor.GRAY + ")");
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!hasGrapplingHook(player))
            return;

        switch (event.getState()) {
            case FISHING -> beginPendingGrapple(player, event.getHook());
            case REEL_IN, FAILED_ATTEMPT -> {
                stopPendingGrapple(player, true);
                stopActiveGrapple(player, true);
            }
            default -> {
            }
        }
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

        long remaining = getRemainingCooldown(bridgeEggCooldowns, player.getUniqueId(), BRIDGE_EGG_COOLDOWN_MS);
        if (remaining > 0L) {
            event.setCancelled(true);
            event.getEntity().remove();
            return;
        }
        bridgeEggCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        sendActionBar(player, ChatColor.GREEN + "Bridge egg launched.");

        UUID eggId = event.getEntity().getUniqueId();
        Egg egg = (Egg) event.getEntity();
        BridgeTrail trail = new BridgeTrail();
        trail.lastLocation = egg.getLocation().clone();
        bridgeEggs.put(eggId, trail);
        trail.task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> trackBridgeEgg(eggId, egg), 1L, 1L);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg)) {
            return;
        }
        UUID eggId = event.getEntity().getUniqueId();
        BridgeTrail trail = bridgeEggs.get(eggId);
        if (trail != null && trail.lastLocation != null) {
            trail.patternIndex += queueBridgeSegment(trail.lastLocation, event.getEntity().getLocation(),
                    trail.patternIndex);
        }
        stopBridgeTrail(eggId);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopPendingGrapple(event.getPlayer(), true);
        stopActiveGrapple(event.getPlayer(), true);
    }

    public int clearPlacedBridgeBlocks() {
        for (BridgeTrail trail : bridgeEggs.values()) {
            if (trail.task != null) {
                trail.task.cancel();
            }
        }
        bridgeEggs.clear();

        int cleared = 0;
        for (BridgeBlockKey key : new HashSet<>(bridgeBlocks)) {
            World world = Bukkit.getWorld(key.worldName());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (block.getType() == Material.BLUE_TERRACOTTA) {
                block.setType(Material.AIR);
                cleared++;
            }
        }
        bridgeBlocks.clear();
        return cleared;
    }

    public void clearCooldowns() {
        dashCooldowns.clear();
        hopCooldowns.clear();
        grapplingCooldowns.clear();
        bridgeEggCooldowns.clear();
    }

    public void shutdown() {
        for (BridgeTrail trail : bridgeEggs.values()) {
            if (trail.task != null) {
                trail.task.cancel();
            }
        }
        bridgeEggs.clear();
        for (PendingGrapple pendingGrapple : pendingGrapples.values()) {
            pendingGrapple.task().cancel();
            if (pendingGrapple.hook().isValid()) {
                pendingGrapple.hook().remove();
            }
        }
        pendingGrapples.clear();
        for (ActiveGrapple grapple : activeGrapples.values()) {
            grapple.task().cancel();
            if (grapple.hook().isValid()) {
                grapple.hook().remove();
            }
        }
        activeGrapples.clear();
    }

    public void refreshAbilityState(Player player) {
    }

    private boolean hasGrapplingHook(Player player) {
        if (!gameManager.isRunner(player) || gameManager.isFrozen(player)) {
            return false;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return (abilityManager.isAbilityItem(mainHand)
                && abilityManager.getAbilityFromItem(mainHand) == AbilityType.GRAPPLING_HOOK)
                || (abilityManager.isAbilityItem(offHand)
                        && abilityManager.getAbilityFromItem(offHand) == AbilityType.GRAPPLING_HOOK);
    }

    private boolean consumeGrappleCooldown(Player player) {
        long remaining = getRemainingCooldown(grapplingCooldowns, player.getUniqueId(), GRAPPLE_COOLDOWN_MS);
        if (remaining > 0L) {
            sendActionBar(player, ChatColor.RED + "Grapple on cooldown! " + formatSeconds(remaining));
            return false;
        }
        grapplingCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    private void beginPendingGrapple(Player owner, FishHook hook) {
        stopPendingGrapple(owner, false);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!owner.isOnline() || !hook.isValid()) {
                stopPendingGrapple(owner, false);
                return;
            }

            Entity hookedEntity = hook.getHookedEntity();
            if (hookedEntity instanceof Player target && !target.getUniqueId().equals(owner.getUniqueId())) {
                if (!consumeGrappleCooldown(owner)) {
                    stopPendingGrapple(owner, true);
                    return;
                }

                stopPendingGrapple(owner, false);
                startGrapple(owner, hook, () -> owner.getLocation().add(0, 1.1, 0), target, true,
                        ChatColor.YELLOW + "Web attached to " + ChatColor.AQUA + target.getName()
                                + ChatColor.YELLOW + ". Pulling them in...");
                return;
            }

            if (hook.isOnGround()) {
                if (!consumeGrappleCooldown(owner)) {
                    stopPendingGrapple(owner, true);
                    return;
                }

                Location anchor = hook.getLocation().clone();
                stopPendingGrapple(owner, false);
                startGrapple(owner, hook, () -> anchor, owner, false,
                        ChatColor.YELLOW + "Web attached. Swing in and right-click to let go!");
            }
        }, 1L, 1L);

        pendingGrapples.put(owner.getUniqueId(), new PendingGrapple(hook, task));
    }

    private void startGrapple(Player owner, FishHook hook, LocationSupplier anchorSupplier, Entity pulledEntity,
            boolean pullingPlayer, String message) {
        stopActiveGrapple(owner, false);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!owner.isOnline() || !hook.isValid() || !pulledEntity.isValid()) {
                stopActiveGrapple(owner, true);
                return;
            }

            Location anchor = anchorSupplier.get();
            if (anchor == null || anchor.getWorld() == null || pulledEntity.getWorld() != anchor.getWorld()) {
                stopActiveGrapple(owner, true);
                return;
            }

            if (pullTowards(pulledEntity, anchor, pullingPlayer)) {
                stopActiveGrapple(owner, true);
            }
        }, 0L, 1L);

        activeGrapples.put(owner.getUniqueId(), new ActiveGrapple(hook, task));
        sendActionBar(owner, message);
        owner.playSound(owner.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1.3f);
    }

    private boolean pullTowards(Entity entity, Location anchor, boolean pullingPlayer) {
        Vector pull = anchor.toVector().subtract(entity.getLocation().toVector());
        double distance = pull.length();
        if (distance <= GRAPPLE_STOP_DISTANCE) {
            entity.setFallDistance(0f);
            return true;
        }

        double drag = pullingPlayer ? PLAYER_GRAPPLE_DRAG : GROUND_GRAPPLE_DRAG;
        double accelerationBase = pullingPlayer ? PLAYER_GRAPPLE_ACCEL_BASE : GROUND_GRAPPLE_ACCEL_BASE;
        double accelerationScale = pullingPlayer ? PLAYER_GRAPPLE_ACCEL_SCALE : GROUND_GRAPPLE_ACCEL_SCALE;
        double accelerationCap = pullingPlayer ? PLAYER_GRAPPLE_ACCEL_CAP : GROUND_GRAPPLE_ACCEL_CAP;
        double verticalScale = pullingPlayer ? PLAYER_GRAPPLE_VERTICAL_SCALE : GROUND_GRAPPLE_VERTICAL_SCALE;
        double verticalBias = pullingPlayer ? PLAYER_GRAPPLE_VERTICAL_BIAS : GROUND_GRAPPLE_VERTICAL_BIAS;

        Vector velocity = entity.getVelocity().multiply(drag)
                .add(pull.normalize()
                        .multiply(Math.min(accelerationCap, accelerationBase + (distance * accelerationScale))));
        velocity.setY(Math.max(-0.15, Math.min(0.9, velocity.getY() + (pull.getY() * verticalScale) + verticalBias)));
        entity.setVelocity(velocity);
        entity.setFallDistance(0f);
        entity.getWorld().spawnParticle(Particle.CLOUD,
                entity.getLocation().add(0, 0.5, 0), 3, 0.15, 0.15, 0.15, 0.02);
        return false;
    }

    private void stopPendingGrapple(Player player, boolean removeHook) {
        PendingGrapple pendingGrapple = pendingGrapples.remove(player.getUniqueId());
        if (pendingGrapple == null) {
            return;
        }

        pendingGrapple.task().cancel();
        if (removeHook && pendingGrapple.hook().isValid()) {
            pendingGrapple.hook().remove();
        }
    }

    private void stopActiveGrapple(Player player, boolean removeHook) {
        ActiveGrapple activeGrapple = activeGrapples.remove(player.getUniqueId());
        if (activeGrapple == null) {
            return;
        }

        activeGrapple.task().cancel();
        if (removeHook && activeGrapple.hook().isValid()) {
            activeGrapple.hook().remove();
        }
    }

    private void trackBridgeEgg(UUID eggId, Egg egg) {
        BridgeTrail trail = bridgeEggs.get(eggId);
        if (trail == null) {
            if (egg.isValid()) {
                egg.remove();
            }
            return;
        }

        if (!egg.isValid() || egg.isDead()) {
            stopBridgeTrail(eggId);
            return;
        }

        Location current = egg.getLocation().clone();
        if (trail.lastLocation != null) {
            trail.patternIndex += queueBridgeSegment(trail.lastLocation, current, trail.patternIndex);
        }
        trail.lastLocation = current;
    }

    private int queueBridgeSegment(Location from, Location to, int patternIndex) {
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.max(1, (int) Math.ceil(from.distance(to) * 3.0));
        for (int i = 0; i <= steps; i++) {
            double progress = i / (double) steps;
            Location sample = from.clone().add(delta.clone().multiply(progress));
            Block base = sample.clone().subtract(0, 1.0, 0).getBlock();
            queueBridgePlacement(base, delta, patternIndex + i);
        }
        return steps + 1;
    }

    private void queueBridgePlacement(Block base, Vector delta, int patternIndex) {
        String worldName = base.getWorld().getName();
        int baseX = base.getX();
        int baseY = base.getY();
        int baseZ = base.getZ();

        Vector flat = delta.clone().setY(0);
        int sideX = 0;
        int sideZ = 0;
        if (flat.lengthSquared() > 0.0001) {
            if (Math.abs(flat.getX()) > Math.abs(flat.getZ())) {
                sideZ = flat.getX() >= 0 ? 1 : -1;
            } else {
                sideX = flat.getZ() >= 0 ? -1 : 1;
            }
        }

        final int finalSideX = sideX;
        final int finalSideZ = sideZ;
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> placeBridgeSection(worldName, baseX, baseY, baseZ, finalSideX, finalSideZ, patternIndex),
                BRIDGE_PLACE_DELAY_TICKS);
    }

    private void placeBridgeSection(String worldName, int baseX, int baseY, int baseZ,
            int sideX, int sideZ, int patternIndex) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        int alongPathIndex;
        if (sideX != 0) {
            alongPathIndex = baseZ;
        } else if (sideZ != 0) {
            alongPathIndex = baseX;
        } else {
            alongPathIndex = baseX + baseZ;
        }

        int sectionPattern = Math.floorMod(alongPathIndex + baseY, 19);
        boolean placePrimary = sectionPattern != 3
                && sectionPattern != 7
                && sectionPattern != 8
                && sectionPattern != 12
                && sectionPattern != 16
                && sectionPattern != 17
                && sectionPattern != 18;

        if (placePrimary) {
            placeTrackedBridgeBlock(world, baseX, baseY, baseZ);
        }
    }

    private void placeTrackedBridgeBlock(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (!isReplaceableBridgeSpace(block.getType())) {
            return;
        }

        block.setType(Material.OAK_PLANKS);
        bridgeBlocks.add(new BridgeBlockKey(world.getName(), x, y, z));
        world.spawnParticle(Particle.BLOCK,
                block.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0,
                Material.OAK_PLANKS.createBlockData());
    }

    private boolean isReplaceableBridgeSpace(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material == Material.WATER;
    }

    private void stopBridgeTrail(UUID eggId) {
        BridgeTrail trail = bridgeEggs.remove(eggId);
        if (trail != null && trail.task != null) {
            trail.task.cancel();
        }
    }

    private long getRemainingCooldown(Map<UUID, Long> cooldowns, UUID playerId, long cooldownLength) {
        Long lastUse = cooldowns.get(playerId);
        if (lastUse == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - lastUse;
        return Math.max(0L, cooldownLength - elapsed);
    }

    private String formatSeconds(long remainingMillis) {
        return ((remainingMillis + 999L) / 1000L) + "s";
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private record ActiveGrapple(FishHook hook, BukkitTask task) {
    }

    private record PendingGrapple(FishHook hook, BukkitTask task) {
    }

    private record BridgeBlockKey(String worldName, int x, int y, int z) {
    }

    private static final class BridgeTrail {
        private Location lastLocation;
        private int patternIndex;
        private BukkitTask task;
    }

    @FunctionalInterface
    private interface LocationSupplier {
        Location get();
    }
}
