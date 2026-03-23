package com.imlucky;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum AbilityType {

    DASH_DOUBLE_JUMP(
            "Dash & Hop",
            Material.FEATHER,
            ChatColor.AQUA + "Use the feather to dash forward.",
            ChatColor.AQUA + "Use the slime ball to hop upward."),
    GRAPPLING_HOOK(
            "Grappling Hook",
            Material.FISHING_ROD,
            ChatColor.YELLOW + "Hook a player to drag them to you,",
            ChatColor.YELLOW + "or hook a block to pull yourself in!"),
    BRIDGE_EGG(
            "Bridge Egg",
            Material.EGG,
            ChatColor.GREEN + "Throw the egg to leave behind",
            ChatColor.GREEN + "a jagged bridge trail!");

    private final String displayName;
    private final Material icon;
    private final String[] description;

    AbilityType(String displayName, Material icon, String... description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String[] getDescription() {
        return description;
    }
}
