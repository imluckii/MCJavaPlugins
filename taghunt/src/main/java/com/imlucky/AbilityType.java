package com.imlucky;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum AbilityType {

    DASH_DOUBLE_JUMP(
            "Dash & Double Jump",
            Material.FEATHER,
            ChatColor.AQUA + "Right-click on the ground to dash forward.",
            ChatColor.AQUA + "Right-click in the air for a double jump!"
    ),
    GRAPPLING_HOOK(
            "Grappling Hook",
            Material.FISHING_ROD,
            ChatColor.YELLOW + "Cast your hook at a teammate",
            ChatColor.YELLOW + "to grapple to their location!"
    ),
    BRIDGE_EGG(
            "Bridge Egg",
            Material.EGG,
            ChatColor.GREEN + "Throw the egg and it will",
            ChatColor.GREEN + "build a bridge in its trail!"
    );

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
