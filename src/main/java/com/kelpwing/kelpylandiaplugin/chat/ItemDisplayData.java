package com.kelpwing.kelpylandiaplugin.chat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of item / inventory data captured on the main thread,
 * ready to be turned into a Discord embed on an async thread.
 * <p>
 * Fields cherry-picked from InteractiveChat-DiscordSRV-Addon's
 * {@code DiscordItemStackUtils.getToolTip()} - using only standard Bukkit API.
 */
public class ItemDisplayData {

    public enum DisplayType {
        ITEM,
        INVENTORY,
        ENDER_CHEST
    }

    /**
     * Rarity of the item - determines the name colour in the tooltip.
     * Matches Minecraft 1.16 rarity values.
     */
    public enum Rarity {
        COMMON,      // white
        UNCOMMON,    // yellow
        RARE,        // aqua
        EPIC         // light purple
    }

    private final DisplayType type;

    // Item fields (type == ITEM) 
    private final String itemName;          // Display name (colour codes stripped)
    private final String materialName;      // e.g. "GOLDEN_CARROT"
    private final int amount;
    private final Map<String, Integer> enchantments; // pretty name → level
    private final List<String> lore;        // colour-stripped lore lines
    private final boolean hasCustomName;

    // Durability (cherry-picked from IC DiscordItemStackUtils) 
    /** Current remaining durability. 0 for non-damageable items. */
    private final int durability;
    /** Maximum durability (item.getType().getMaxDurability()). 0 for non-damageable. */
    private final int maxDurability;
    /** True if the item has the Unbreakable NBT flag set. */
    private final boolean unbreakable;

    // ItemFlags (hide flags) 
    private final boolean hideEnchants;
    private final boolean hideAttributes;
    private final boolean hideDurability;

    // Potion effects (cherry-picked from IC - PotionMeta) 
    /**
     * Pre-formatted potion effect lines, e.g. "Speed II (3:00)", "Instant Health".
     * Empty for non-potion items.
     */
    private final List<String> potionEffects;

    // Attribute modifier lines (cherry-picked from IC) 
    /**
     * Pre-formatted attribute lines grouped by slot header, e.g.:
     * "When in Main Hand:", " +6 Attack Damage", " -1.80 Attack Speed".
     * Empty if no modifiers.
     */
    private final List<String> attributeLines;

    // Rarity 
    /** Item rarity - controls name colour in the tooltip image. */
    private final Rarity rarity;

    // Inventory fields (type == INVENTORY | ENDER_CHEST) 
    private final int usedSlots;
    private final int totalSlots;
    private final List<String> topItems;    // "Diamond Sword x1", "Golden Carrot x64" …

    // Common 
    /** The raw message text *around* the keyword (for the main content field). */
    private final String surroundingText;

    private ItemDisplayData(Builder b) {
        this.type            = b.type;
        this.itemName        = b.itemName;
        this.materialName    = b.materialName;
        this.amount          = b.amount;
        this.enchantments    = b.enchantments == null ? Collections.emptyMap() : b.enchantments;
        this.lore            = b.lore == null ? Collections.emptyList() : b.lore;
        this.hasCustomName   = b.hasCustomName;
        this.durability      = b.durability;
        this.maxDurability   = b.maxDurability;
        this.unbreakable     = b.unbreakable;
        this.hideEnchants    = b.hideEnchants;
        this.hideAttributes  = b.hideAttributes;
        this.hideDurability  = b.hideDurability;
        this.potionEffects   = b.potionEffects == null ? Collections.emptyList() : b.potionEffects;
        this.attributeLines  = b.attributeLines == null ? Collections.emptyList() : b.attributeLines;
        this.rarity          = b.rarity == null ? Rarity.COMMON : b.rarity;
        this.usedSlots       = b.usedSlots;
        this.totalSlots      = b.totalSlots;
        this.topItems        = b.topItems == null ? Collections.emptyList() : b.topItems;
        this.surroundingText = b.surroundingText;
    }

    // Getters 

    public DisplayType getType()               { return type; }
    public String getItemName()                { return itemName; }
    public String getMaterialName()            { return materialName; }
    public int getAmount()                     { return amount; }
    public Map<String, Integer> getEnchantments() { return enchantments; }
    public List<String> getLore()              { return lore; }
    public boolean hasCustomName()             { return hasCustomName; }
    public int getDurability()                 { return durability; }
    public int getMaxDurability()              { return maxDurability; }
    public boolean isUnbreakable()             { return unbreakable; }
    public boolean isHideEnchants()            { return hideEnchants; }
    public boolean isHideAttributes()          { return hideAttributes; }
    public boolean isHideDurability()          { return hideDurability; }
    public List<String> getPotionEffects()     { return potionEffects; }
    public List<String> getAttributeLines()    { return attributeLines; }
    public Rarity getRarity()                  { return rarity; }
    public int getUsedSlots()                  { return usedSlots; }
    public int getTotalSlots()                 { return totalSlots; }
    public List<String> getTopItems()          { return topItems; }
    public String getSurroundingText()         { return surroundingText; }

    /**
     * Returns a URL to a Minecraft item icon image.
     * Uses the Minecraft Wiki's Invicon images (reliable, free, no auth).
     * Falls back to a generic icon for unknown items.
     */
    public String getItemIconUrl() {
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }
        // Convert GOLDEN_CARROT → Golden_Carrot for the wiki Invicon URL
        String wikiName = materialToWikiName(materialName);
        return "https://minecraft.wiki/images/Invicon_" + wikiName + ".png";
    }

    /**
     * Converts a Bukkit Material enum name to the Minecraft Wiki Invicon name.
     * e.g. GOLDEN_CARROT → Golden_Carrot, DIAMOND_SWORD → Diamond_Sword
     */
    private static String materialToWikiName(String material) {
        StringBuilder sb = new StringBuilder();
        boolean capitalise = true;
        for (char c : material.toCharArray()) {
            if (c == '_') {
                sb.append('_');
                capitalise = true;
            } else {
                sb.append(capitalise ? Character.toUpperCase(c) : Character.toLowerCase(c));
                capitalise = false;
            }
        }
        return sb.toString();
    }

    // Builder 

    public static Builder builder(DisplayType type) {
        return new Builder(type);
    }

    public static class Builder {
        private final DisplayType type;
        private String itemName;
        private String materialName;
        private int amount;
        private Map<String, Integer> enchantments;
        private List<String> lore;
        private boolean hasCustomName;
        private int durability;
        private int maxDurability;
        private boolean unbreakable;
        private boolean hideEnchants;
        private boolean hideAttributes;
        private boolean hideDurability;
        private List<String> potionEffects;
        private List<String> attributeLines;
        private Rarity rarity;
        private int usedSlots;
        private int totalSlots;
        private List<String> topItems;
        private String surroundingText = "";

        private Builder(DisplayType type) {
            this.type = type;
        }

        public Builder itemName(String v)                     { this.itemName = v; return this; }
        public Builder materialName(String v)                 { this.materialName = v; return this; }
        public Builder amount(int v)                          { this.amount = v; return this; }
        public Builder enchantments(Map<String, Integer> v)   { this.enchantments = v; return this; }
        public Builder lore(List<String> v)                   { this.lore = v; return this; }
        public Builder hasCustomName(boolean v)               { this.hasCustomName = v; return this; }
        public Builder durability(int v)                      { this.durability = v; return this; }
        public Builder maxDurability(int v)                   { this.maxDurability = v; return this; }
        public Builder unbreakable(boolean v)                 { this.unbreakable = v; return this; }
        public Builder hideEnchants(boolean v)                { this.hideEnchants = v; return this; }
        public Builder hideAttributes(boolean v)              { this.hideAttributes = v; return this; }
        public Builder hideDurability(boolean v)              { this.hideDurability = v; return this; }
        public Builder potionEffects(List<String> v)          { this.potionEffects = v; return this; }
        public Builder attributeLines(List<String> v)         { this.attributeLines = v; return this; }
        public Builder rarity(Rarity v)                       { this.rarity = v; return this; }
        public Builder usedSlots(int v)                       { this.usedSlots = v; return this; }
        public Builder totalSlots(int v)                      { this.totalSlots = v; return this; }
        public Builder topItems(List<String> v)               { this.topItems = v; return this; }
        public Builder surroundingText(String v)              { this.surroundingText = v; return this; }

        public ItemDisplayData build() {
            return new ItemDisplayData(this);
        }
    }
}
