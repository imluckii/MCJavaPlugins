package com.imlucky;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AbilityManager {

    public static final String GUI_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Choose Your Ability";

    private final Map<UUID, AbilityType> playerAbilities = new HashMap<>();
    private final NamespacedKey abilityKey;

    public AbilityManager(JavaPlugin plugin) {
        this.abilityKey = new NamespacedKey(plugin, "ability_type");
    }

    public void setAbility(Player player, AbilityType type) {
        playerAbilities.put(player.getUniqueId(), type);
    }

    public AbilityType getAbility(Player player) {
        return playerAbilities.get(player.getUniqueId());
    }

    public void clearAll() {
        playerAbilities.clear();
    }

    public NamespacedKey getAbilityKey() {
        return abilityKey;
    }

    /** Returns true if the given ItemStack is a TagHunt ability-use item. */
    public boolean isAbilityItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(abilityKey, PersistentDataType.STRING);
    }

    /**
     * Returns the AbilityType stored in the item's data, or null if not an ability
     * item.
     */
    public AbilityType getAbilityFromItem(ItemStack item) {
        if (!isAbilityItem(item))
            return null;
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(abilityKey, PersistentDataType.STRING);
        try {
            return AbilityType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Builds and opens the ability-selection GUI for a player. */
    public Inventory createAbilityGUI() {
        Inventory gui = Bukkit.createInventory(null, 9, GUI_TITLE);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, filler);
        }

        gui.setItem(1, buildGuiItem(AbilityType.DASH_DOUBLE_JUMP));
        gui.setItem(4, buildGuiItem(AbilityType.GRAPPLING_HOOK));
        gui.setItem(7, buildGuiItem(AbilityType.BRIDGE_EGG));

        return gui;
    }

    private ItemStack buildGuiItem(AbilityType type) {
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + type.getDisplayName());
        List<String> lore = new ArrayList<>(Arrays.asList(type.getDescription()));
        lore.add("");
        lore.add(ChatColor.GRAY + "Click to select!");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Removes any existing ability items from the player's inventory,
     * then gives them the item for the chosen ability.
     */
    public void giveAbilityItem(Player player, AbilityType type) {
        removeAbilityItems(player);
        if (type == AbilityType.DASH_DOUBLE_JUMP) {
            player.getInventory().addItem(
                    buildTaggedAbilityItem(type, Material.FEATHER, 1,
                            ChatColor.GOLD + "Dash Feather",
                            Arrays.asList(
                                    ChatColor.AQUA + "Right-click to dash forward.",
                                    ChatColor.GRAY + "Works the same on the ground or in the air.")),
                    buildTaggedAbilityItem(type, Material.SLIME_BALL, 1,
                            ChatColor.GOLD + "Hop Slime",
                            Arrays.asList(
                                    ChatColor.AQUA + "Right-click to perform a hop.",
                                    ChatColor.GRAY + "Useful for quick bursts of height.")));
        } else {
            player.getInventory().addItem(buildAbilityUseItem(type));
        }
        player.sendMessage(ChatColor.GREEN + "Ability selected: " + ChatColor.GOLD + "" + ChatColor.BOLD
                + type.getDisplayName());
    }

    /** Builds the in-hand ability-use item (with PDC marker). */
    public ItemStack buildAbilityUseItem(AbilityType type) {
        int amount = (type == AbilityType.BRIDGE_EGG) ? 16 : 1;
        return buildTaggedAbilityItem(type, type.getIcon(), amount,
                ChatColor.GOLD + type.getDisplayName(), new ArrayList<>(Arrays.asList(type.getDescription())));
    }

    private ItemStack buildTaggedAbilityItem(AbilityType type, Material material, int amount,
            String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Removes all ability-use items from a player's inventory. */
    public void removeAbilityItems(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isAbilityItem(item)) {
                player.getInventory().setItem(i, null);
            }
        }
    }
}
