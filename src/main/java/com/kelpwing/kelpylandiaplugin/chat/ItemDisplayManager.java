package com.kelpwing.kelpylandiaplugin.chat;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces InteractiveChat - provides [item], [inv], and [enderchest] keywords
 * in chat using Spigot's BungeeCord Chat Component API.
 * <p>
 * When a player types one of the keywords, this manager:
 * <ul>
 *   <li>[item]  - shows a hoverable item component (name, lore, enchants) and
 *                 a clickable inventory viewer for that item</li>
 *   <li>[inv]   - shows a hoverable summary and opens a read-only snapshot of
 *                 the player's inventory on click</li>
 *   <li>[enderchest] - same but for ender chest</li>
 * </ul>
 * <p>
 * Snapshots are stored briefly so that clicking the component can open a
 * frozen view even if the player changes their inventory later.
 */
public class ItemDisplayManager {

    // Keyword patterns - loaded from config, matching IC's ItemDisplay.*.Keyword 
    // Defaults mirror IC's config.yml verbatim.
    private final Pattern itemPattern;
    private final Pattern invPattern;
    private final Pattern enderPattern;
    /** Combined OR of all three patterns, used for quick containsKeyword() checks. */
    private final Pattern combinedPattern;

    private final KelpylandiaPlugin plugin;

    /**
     * Cached inventory snapshots keyed by a random UUID.
     * Each snapshot expires after a configurable time (default 5 min).
     */
    private final ConcurrentHashMap<UUID, SnapshotEntry> snapshots = new ConcurrentHashMap<>();

    public ItemDisplayManager(KelpylandiaPlugin plugin) {
        this.plugin = plugin;

        // Load keyword patterns from config - key names match IC exactly
        String itemKw  = plugin.getConfig().getString("ItemDisplay.Item.Keyword",      "(?i)\\[item\\]|\\[i\\]");
        String invKw   = plugin.getConfig().getString("ItemDisplay.Inventory.Keyword", "(?i)\\[inv\\]|\\[inventory\\]");
        String enderKw = plugin.getConfig().getString("ItemDisplay.EnderChest.Keyword","(?i)\\[ender\\]|\\[e\\]");

        itemPattern    = Pattern.compile(itemKw);
        invPattern     = Pattern.compile(invKw);
        enderPattern   = Pattern.compile(enderKw);
        combinedPattern = Pattern.compile(itemKw + "|" + invKw + "|" + enderKw);

        // Purge expired snapshots every 60 seconds
        long ttlMinutes = plugin.getConfig().getLong("ItemDisplay.Settings.Timeout", 5);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            long ttl = ttlMinutes * 60_000L;
            snapshots.entrySet().removeIf(e -> now - e.getValue().createdAt > ttl);
        }, 20L * 60, 20L * 60);
    }

    // Public API 

    /**
     * Returns {@code true} when the given raw message contains at least one
     * recognised keyword ([item]/[i], [inv]/[inventory], [ender]/[e]).
     */
    public boolean containsKeyword(String message) {
        return combinedPattern.matcher(message).find();
    }

    /**
     * Build the full chat line as a component array, replacing any recognised
     * keywords with interactive components.
     *
     * <p><b>Formatting fix:</b> We accept the <em>complete</em> already-formatted
     * line (prefix + separator + message body, colour codes already translated) and
     * parse it in one {@link TextComponent#fromLegacyText} call.  This preserves
     * the colour inherited from the prefix across the entire message body - the same
     * approach IC uses via {@code ComponentReplacing.replace()} on the full component.
     *
     * <p>We then scan the <em>plain-text</em> representation of that line for
     * keyword/command positions and splice interactive components in place of those
     * plain-text spans.  Because all segments come from the same initial
     * {@code fromLegacyText} parse, colour inheritance is never broken.
     *
     * @param player        sender - used to snapshot held item / inventory
     * @param fullLine      the complete formatted chat line (prefix + message),
     *                      with {@code &} colour codes already translated to §
     * @return ready-to-send component array
     */
    public BaseComponent[] buildChatLine(Player player, String fullLine) {
        // Parse the entire formatted line at once - colour codes stay intact.
        BaseComponent[] parsed = TextComponent.fromLegacyText(fullLine);

        // Build a plain-text version (no colour codes) to drive regex scanning.
        // org.bukkit.ChatColor.stripColor handles § codes produced by translateAlternateColorCodes.
        String plain = org.bukkit.ChatColor.stripColor(fullLine);

        // Walk through plain text finding keyword/command positions, then rebuild the
        // component list by slicing the already-parsed components.
        ComponentBuilder builder = new ComponentBuilder();
        int lastEnd = 0;

        while (lastEnd < plain.length()) {
            // Find the earliest keyword match among all three patterns
            Matcher im = itemPattern.matcher(plain); im.region(lastEnd, plain.length());
            Matcher vm = invPattern.matcher(plain);  vm.region(lastEnd, plain.length());
            Matcher em = enderPattern.matcher(plain); em.region(lastEnd, plain.length());

            boolean iFound = im.find();
            boolean vFound = vm.find();
            boolean eFound = em.find();

            if (!iFound && !vFound && !eFound) {
                // No more keywords - emit the remaining span (checking for command boxes)
                appendParsedSpan(builder, parsed, plain, lastEnd, plain.length());
                break;
            }

            // Pick the earliest match
            Matcher first = null;
            int firstStart = Integer.MAX_VALUE;
            if (iFound && im.start() < firstStart) { first = im; firstStart = im.start(); }
            if (vFound && vm.start() < firstStart) { first = vm; firstStart = vm.start(); }
            if (eFound && em.start() < firstStart) { first = em; firstStart = em.start(); }

            // Emit the segment before this keyword (may contain command boxes)
            if (firstStart > lastEnd) {
                appendParsedSpan(builder, parsed, plain, lastEnd, firstStart);
            }

            // Emit the interactive keyword component
            if (first == im) {
                builder.append(buildItemComponent(player), ComponentBuilder.FormatRetention.NONE);
            } else if (first == vm) {
                builder.append(buildInventoryComponent(player, false), ComponentBuilder.FormatRetention.NONE);
            } else {
                builder.append(buildInventoryComponent(player, true), ComponentBuilder.FormatRetention.NONE);
            }

            lastEnd = first.end();
        }

        return builder.create();
    }

    /**
     * Emits the slice of {@code parsed[]} that corresponds to the plain-text
     * range {@code [from, to)}, then checks that slice for clickable command
     * boxes (CommandsDisplay).
     *
     * <p>The {@code parsed} array is the result of one {@link TextComponent#fromLegacyText}
     * call on the full formatted line.  Each element carries its own colour/formatting
     * but the plain-text characters map 1-to-1 with {@code plain}.  We extract the
     * plain-text substring for this span and, if it contains commands, re-process
     * only that substring through CommandsDisplay (colour codes are re-derived from
     * the full-line context by re-translating the substring of the original
     * formatted line).
     *
     * <p>To keep this efficient we reconstruct the legacy-colour string for the span
     * by walking the parsed components and re-emitting their text with § codes.
     */
    private void appendParsedSpan(ComponentBuilder builder, BaseComponent[] parsed,
                                   String plain, int from, int to) {
        if (from >= to) return;

        // Reconstruct the legacy-colour string for just this plain-text span.
        // We walk the parsed components, accumulating plain chars and tagging colours.
        String spanLegacy = extractLegacySpan(parsed, plain, from, to);

        if (CommandsDisplay.containsCommand(plugin, spanLegacy)) {
            // Let CommandsDisplay re-parse - it calls fromLegacyText internally, preserving colours.
            BaseComponent[] cmdComps = CommandsDisplay.process(plugin, spanLegacy);
            builder.append(cmdComps, ComponentBuilder.FormatRetention.NONE);
        } else {
            builder.append(TextComponent.fromLegacyText(spanLegacy),
                    ComponentBuilder.FormatRetention.NONE);
        }
    }

    /**
     * Walks the {@code parsed} component array (from one {@code fromLegacyText} call)
     * and reconstructs the legacy-colour string for the plain-text range [{@code from},{@code to}).
     *
     * <p>Each {@link TextComponent} in the array has its own colour/bold/etc. state plus
     * a plain text string.  We accumulate plain chars until we have covered {@code [from,to)},
     * prepending § codes whenever the formatting changes.
     */
    private static String extractLegacySpan(BaseComponent[] parsed, String plain, int from, int to) {
        StringBuilder sb = new StringBuilder();
        int plainPos = 0; // how many plain-text chars we have consumed so far

        for (BaseComponent bc : parsed) {
            if (!(bc instanceof TextComponent)) continue;
            String text = ((TextComponent) bc).getText();
            int textLen = text.length();

            int segEnd = plainPos + textLen;

            if (segEnd <= from) {
                // This segment is entirely before our range
                plainPos = segEnd;
                continue;
            }
            if (plainPos >= to) {
                // This segment is entirely after our range
                break;
            }

            // Overlap: [max(from,plainPos), min(to,segEnd))
            int sliceFrom = Math.max(from, plainPos) - plainPos;
            int sliceTo   = Math.min(to,   segEnd)   - plainPos;

            // Emit colour/format codes for this component
            sb.append(componentToLegacyCodes(bc));
            sb.append(text, sliceFrom, sliceTo);

            plainPos = segEnd;
        }

        return sb.toString();
    }

    /** Converts a component's colour/decoration state to § legacy codes. */
    private static String componentToLegacyCodes(BaseComponent bc) {
        StringBuilder sb = new StringBuilder();
        net.md_5.bungee.api.ChatColor color = bc.getColorRaw();
        if (color != null) {
            sb.append(color);
        }
        if (Boolean.TRUE.equals(bc.isBoldRaw()))          sb.append(net.md_5.bungee.api.ChatColor.BOLD);
        if (Boolean.TRUE.equals(bc.isItalicRaw()))        sb.append(net.md_5.bungee.api.ChatColor.ITALIC);
        if (Boolean.TRUE.equals(bc.isUnderlinedRaw()))    sb.append(net.md_5.bungee.api.ChatColor.UNDERLINE);
        if (Boolean.TRUE.equals(bc.isStrikethroughRaw())) sb.append(net.md_5.bungee.api.ChatColor.STRIKETHROUGH);
        if (Boolean.TRUE.equals(bc.isObfuscatedRaw()))    sb.append(net.md_5.bungee.api.ChatColor.MAGIC);
        return sb.toString();
    }

    /**
     * Returns a plain-text representation of the message for Discord relay.
     * Replaces item/inv/ender keywords using the IC config text templates
     * (ItemDisplay.Item.Text / SingularText, Inventory.Text, EnderChest.Text),
     * then strips Minecraft colour codes - matching IC dsrv's approach.
     */
    public String buildDiscordLine(Player player, String rawMessage) {
        // Process each keyword type sequentially (IC processes item → inv → ender in order)
        String result = replaceKeywordInText(rawMessage, itemPattern,  getItemDiscordText(player));
        result        = replaceKeywordInText(result,    invPattern,   getInvDiscordText(player));
        result        = replaceKeywordInText(result,    enderPattern, getEnderDiscordText(player));
        return result;
    }

    /** Replace all occurrences of a Pattern in text with a fixed replacement string. */
    private static String replaceKeywordInText(String text, Pattern pattern, String replacement) {
        return pattern.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    /**
     * Builds a list of {@link ItemDisplayData} objects for each keyword found
     * in the message.  Called on the main thread so player inventory is accessible.
     * The returned data is safe to use from any thread (all values are snapshots).
     * <p>
     * Also returns the message text with keywords stripped out (surroundingText).
     */
    public List<ItemDisplayData> buildDiscordData(Player player, String rawMessage) {
        List<ItemDisplayData> results = new ArrayList<>();

        // Build "surrounding text" = message with all keywords replaced by nothing
        String surrounding = combinedPattern.matcher(rawMessage).replaceAll("").trim();

        // Process item, inv, ender in order (matches IC dsrv ordering)
        if (itemPattern.matcher(rawMessage).find()) {
            results.add(buildItemDisplayData(player, surrounding));
        }
        if (invPattern.matcher(rawMessage).find()) {
            results.add(buildInventoryDisplayData(player, false, surrounding));
        }
        if (enderPattern.matcher(rawMessage).find()) {
            results.add(buildInventoryDisplayData(player, true, surrounding));
        }

        return results;
    }

    private ItemDisplayData buildItemDisplayData(Player player, String surrounding) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return ItemDisplayData.builder(ItemDisplayData.DisplayType.ITEM)
                    .itemName("Nothing")
                    .materialName("AIR")
                    .amount(0)
                    .surroundingText(surrounding)
                    .build();
        }

        // Keep § colour codes - TooltipImageRenderer.parseLegacyLine() uses them to render
        // the name with its actual formatting.  Discord embed uses stripSectionCodes() on use.
        String displayName = getItemDisplayName(item);
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        boolean hasMeta = meta != null;

        // ItemFlags 
        boolean hideEnchants   = hasMeta && meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS);
        boolean hideAttributes = hasMeta && meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES);
        // HIDE_UNBREAKABLE covers the Unbreakable tag; we reuse the flag for the durability line too
        boolean hideDurability = hasMeta && meta.hasItemFlag(ItemFlag.HIDE_UNBREAKABLE);

        // Enchantments 
        Map<String, Integer> enchants = new java.util.LinkedHashMap<>();
        if (!hideEnchants) {
            // EnchantmentStorageMeta for enchanted books - stored enchants, not applied
            Map<Enchantment, Integer> rawEnchants;
            if (hasMeta && meta instanceof EnchantmentStorageMeta) {
                rawEnchants = ((EnchantmentStorageMeta) meta).getStoredEnchants();
            } else {
                rawEnchants = new HashMap<>(item.getEnchantments());
                if (hasMeta && meta.hasEnchants()) {
                    rawEnchants.putAll(meta.getEnchants());
                }
            }
            for (Map.Entry<Enchantment, Integer> e : rawEnchants.entrySet()) {
                enchants.put(formatEnchantmentName(e.getKey()), e.getValue());
            }
        }

        // Lore (§ colour codes preserved for tooltip rendering) 
        List<String> lore = new ArrayList<>();
        if (hasMeta && meta.hasLore()) {
            for (String line : meta.getLore()) {
                lore.add(line); // keep § codes - TooltipImageRenderer parses them for colours
            }
        }

        // Durability (cherry-picked from IC DiscordItemStackUtils) 
        int durability = 0;
        int maxDurability = 0;
        boolean unbreakable = hasMeta && meta.isUnbreakable();
        if (hasMeta && meta instanceof Damageable) {
            short maxDur = item.getType().getMaxDurability();
            if (maxDur > 0) {
                int damage = ((Damageable) meta).getDamage();
                maxDurability = maxDur;
                durability = maxDur - damage;
            }
        }

        // Potion effects (cherry-picked from IC DiscordItemStackUtils) 
        List<String> potionEffects = new ArrayList<>();
        if (hasMeta && meta instanceof PotionMeta) {
            PotionMeta potionMeta = (PotionMeta) meta;
            // Base potion type effects
            org.bukkit.potion.PotionData baseData = potionMeta.getBasePotionData();
            if (baseData != null && baseData.getType() != null
                    && baseData.getType().getEffectType() != null) {
                PotionEffectType type = baseData.getType().getEffectType();
                // Duration: extended = 2x, upgraded = higher amplifier
                int amplifier = baseData.isUpgraded() ? 1 : 0;
                // Vanilla base durations for the base potion type (ticks)
                int durationTicks = baseData.isExtended() ? 9600 : 3600;
                potionEffects.add(formatPotionEffect(type, amplifier, durationTicks));
            }
            // Custom effects
            if (potionMeta.hasCustomEffects()) {
                for (PotionEffect effect : potionMeta.getCustomEffects()) {
                    potionEffects.add(formatPotionEffect(
                            effect.getType(), effect.getAmplifier(), effect.getDuration()));
                }
            }
        }

        // Attribute modifiers grouped by slot (cherry-picked from IC) 
        List<String> attributeLines = new ArrayList<>();
        if (!hideAttributes) {
            buildAttributeLines(item, meta, attributeLines);
        }

        // Rarity (cherry-picked from IC) 
        // In 1.16: COMMON = no enchant, UNCOMMON = enchanted book / golden items,
        // RARE = most enchanted tools/weapons, EPIC = enchanted netherite / rare items.
        // Simple heuristic matching IC's approach: check enchantment count and material.
        ItemDisplayData.Rarity rarity = determineRarity(item, hasMeta && meta.hasEnchants());

        return ItemDisplayData.builder(ItemDisplayData.DisplayType.ITEM)
                .itemName(displayName)
                .materialName(item.getType().name())
                .amount(item.getAmount())
                .enchantments(enchants)
                .lore(lore)
                .hasCustomName(hasMeta && meta.hasDisplayName())
                .durability(durability)
                .maxDurability(maxDurability)
                .unbreakable(unbreakable)
                .hideEnchants(hideEnchants)
                .hideAttributes(hideAttributes)
                .hideDurability(hideDurability)
                .potionEffects(potionEffects)
                .attributeLines(attributeLines)
                .rarity(rarity)
                .surroundingText(surrounding)
                .build();
    }

    /**
     * Formats a single potion effect line in the style IC uses:
     * "Speed II (3:00)" - name, roman numeral amplifier (if > 0), duration.
     * Instant effects (health, damage) show no duration.
     */
    private static String formatPotionEffect(PotionEffectType type, int amplifier, int durationTicks) {
        String name = formatPotionEffectName(type);
        String level = amplifier > 0 ? " " + toRoman(amplifier + 1) : "";

        // Instant effects have no meaningful duration
        boolean instant = type.equals(PotionEffectType.HEAL)
                || type.equals(PotionEffectType.HARM)
                || type.equals(PotionEffectType.SATURATION);
        if (instant) {
            return name + level;
        }

        // Format duration as M:SS (matching IC's formatting)
        int totalSeconds = durationTicks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String duration = String.format("%d:%02d", minutes, seconds);
        return name + level + " (" + duration + ")";
    }

    /**
     * Converts a PotionEffectType to a human-readable name.
     * e.g. INCREASE_DAMAGE → "Strength", SLOW → "Slowness"
     */
    private static String formatPotionEffectName(PotionEffectType type) {
        // Some effects have unintuitive internal names - map the common ones
        String raw = type.getName(); // e.g. "SPEED", "INCREASE_DAMAGE"
        switch (raw) {
            case "INCREASE_DAMAGE":        return "Strength";
            case "DAMAGE_RESISTANCE":      return "Resistance";
            case "SLOW":                   return "Slowness";
            case "FAST_DIGGING":           return "Haste";
            case "SLOW_DIGGING":           return "Mining Fatigue";
            case "JUMP":                   return "Jump Boost";
            case "CONFUSION":              return "Nausea";
            case "HEAL":                   return "Instant Health";
            case "HARM":                   return "Instant Damage";
            case "NIGHT_VISION":           return "Night Vision";
            case "WATER_BREATHING":        return "Water Breathing";
            case "INVISIBILITY":           return "Invisibility";
            case "FIRE_RESISTANCE":        return "Fire Resistance";
            case "HEALTH_BOOST":           return "Health Boost";
            case "ABSORPTION":             return "Absorption";
            case "SATURATION":             return "Saturation";
            case "GLOWING":                return "Glowing";
            case "LEVITATION":             return "Levitation";
            case "LUCK":                   return "Luck";
            case "UNLUCK":                 return "Bad Luck";
            case "SLOW_FALLING":           return "Slow Falling";
            case "CONDUIT_POWER":          return "Conduit Power";
            case "DOLPHINS_GRACE":         return "Dolphin's Grace";
            case "BAD_OMEN":               return "Bad Omen";
            case "HERO_OF_THE_VILLAGE":    return "Hero of the Village";
            default:
                // Fall through: title-case the raw name
                StringBuilder sb = new StringBuilder();
                boolean cap = true;
                for (char c : raw.toCharArray()) {
                    if (c == '_') { sb.append(' '); cap = true; }
                    else { sb.append(cap ? Character.toUpperCase(c) : Character.toLowerCase(c)); cap = false; }
                }
                return sb.toString();
        }
    }

    /**
     * Builds attribute modifier lines grouped by equipment slot.
     * Cherry-picked from IC's DiscordItemStackUtils attribute rendering.
     * Format: "When in [slot]:", " +8 Attack Damage", " -1.80 Attack Speed"
     */
    private void buildAttributeLines(ItemStack item, ItemMeta meta, List<String> lines) {
        com.google.common.collect.Multimap<Attribute, AttributeModifier> modifiers = null;
        if (meta != null && meta.hasAttributeModifiers()) {
            modifiers = meta.getAttributeModifiers();
        }
        if (modifiers == null || modifiers.isEmpty()) {
            modifiers = getDefaultAttributes(item);
        }
        if (modifiers == null || modifiers.isEmpty()) return;

        EquipmentSlot[] slots = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        for (EquipmentSlot slot : slots) {
            List<String> slotLines = new ArrayList<>();
            for (var entry : modifiers.entries()) {
                Attribute attr = entry.getKey();
                AttributeModifier mod = entry.getValue();
                if (mod.getSlot() != null && mod.getSlot() != slot) continue;
                if (mod.getSlot() == null && slot != EquipmentSlot.HAND) continue;

                double amount = mod.getAmount();
                // Apply base value offsets (matching IC behaviour for main-hand items)
                if (slot == EquipmentSlot.HAND || slot == EquipmentSlot.OFF_HAND) {
                    if (attr == Attribute.GENERIC_ATTACK_DAMAGE) {
                        amount += 1.0;
                    } else if (attr == Attribute.GENERIC_ATTACK_SPEED) {
                        amount += 4.0;
                    }
                }

                String attrName = formatAttributeName(attr);
                String sign = amount >= 0 ? "+" : "";
                String formatted;
                if (mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                    // Display as integer if whole number, else 1 decimal
                    formatted = amount == (long) amount
                            ? sign + (long) amount + " " + attrName
                            : sign + String.format("%.2f", amount) + " " + attrName;
                } else {
                    // Multiply operations - display as percentage
                    formatted = sign + String.format("%.0f", amount * 100) + "% " + attrName;
                }
                String prefix = amount >= 0 ? " +" : " ";
                // For ADD_NUMBER already has sign embedded, fix prefix
                slotLines.add(" " + formatted);
            }
            if (!slotLines.isEmpty()) {
                lines.add("When in " + getSlotLabel(slot) + ":");
                lines.addAll(slotLines);
            }
        }
    }

    /**
     * Determines item rarity using a simple heuristic that matches Minecraft 1.16 rarity:
     * <ul>
     *   <li>EPIC - enchanted items whose base rarity is EPIC (ender dragon egg, elytra, etc.)
     *       or any item with 3+ enchantments</li>
     *   <li>RARE - enchanted tools/weapons/armour, or items with base rarity of RARE</li>
     *   <li>UNCOMMON - golden items, enchanted books, spawn eggs</li>
     *   <li>COMMON - everything else</li>
     * </ul>
     */
    private static ItemDisplayData.Rarity determineRarity(ItemStack item, boolean hasEnchants) {
        Material mat = item.getType();
        String name = mat.name();

        // Vanilla EPIC items
        if (mat == Material.ELYTRA
                || mat == Material.DRAGON_EGG
                || mat == Material.DRAGON_HEAD) {
            return ItemDisplayData.Rarity.EPIC;
        }

        // Enchanted items get bumped up by one rarity tier
        if (hasEnchants) {
            // Netherite gear base = rare, enchanted = epic
            if (name.startsWith("NETHERITE_")) {
                return ItemDisplayData.Rarity.EPIC;
            }
            // Enchanted books = uncommon → rare
            if (mat == Material.ENCHANTED_BOOK) {
                return ItemDisplayData.Rarity.RARE;
            }
            // Any other enchanted item = rare
            return ItemDisplayData.Rarity.RARE;
        }

        // Vanilla UNCOMMON items (golden gear, spawn eggs, enchanted book unenchanted)
        if (name.startsWith("GOLDEN_")
                || mat == Material.ENCHANTED_BOOK
                || mat == Material.EXPERIENCE_BOTTLE
                || name.endsWith("_SPAWN_EGG")) {
            return ItemDisplayData.Rarity.UNCOMMON;
        }

        return ItemDisplayData.Rarity.COMMON;
    }

    private ItemDisplayData buildInventoryDisplayData(Player player, boolean enderChest, String surrounding) {
        Inventory source = enderChest ? player.getEnderChest() : player.getInventory();
        int usedSlots = 0;
        int totalSlots = source.getSize();
        Map<String, Integer> itemCounts = new java.util.LinkedHashMap<>();

        for (ItemStack stack : source.getContents()) {
            if (stack != null && stack.getType() != Material.AIR) {
                usedSlots++;
                String name = org.bukkit.ChatColor.stripColor(getItemDisplayName(stack));
                itemCounts.merge(name, stack.getAmount(), Integer::sum);
            }
        }

        // Top 8 items sorted by count
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(itemCounts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> topItems = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            if (shown >= 8) break;
            topItems.add(entry.getKey() + " x" + entry.getValue());
            shown++;
        }

        ItemDisplayData.DisplayType type = enderChest
                ? ItemDisplayData.DisplayType.ENDER_CHEST
                : ItemDisplayData.DisplayType.INVENTORY;

        return ItemDisplayData.builder(type)
                .usedSlots(usedSlots)
                .totalSlots(totalSlots)
                .topItems(topItems)
                .surroundingText(surrounding)
                .build();
    }

    /**
     * Retrieves and opens a snapshot inventory for the given snapshot UUID.
     * Returns {@code null} if the snapshot has expired or doesn't exist.
     */
    public Inventory getSnapshot(UUID snapshotId) {
        SnapshotEntry entry = snapshots.get(snapshotId);
        return entry != null ? entry.inventory : null;
    }

    /**
     * Returns the snapshot title (used when opening the GUI).
     */
    public String getSnapshotTitle(UUID snapshotId) {
        SnapshotEntry entry = snapshots.get(snapshotId);
        return entry != null ? entry.title : null;
    }

    // Item component 

    private BaseComponent[] buildItemComponent(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            // IC's itemAirAllow path - show using singular text with "Air"
            String singular = plugin.getConfig().getString(
                    "ItemDisplay.Item.SingularText", "&f[&f{Item}&f]");
            String raw = singular.replace("{Item}", "Air").replace("{Amount}", "0");
            TextComponent comp = new TextComponent(TextComponent.fromLegacyText(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', raw)));
            return new BaseComponent[]{comp};
        }

        int amount = item.getAmount();

        // Get the display name as an Adventure Component (with rarity color + italic for custom
        // names), mirroring IC's ItemStackUtils.getDisplayName() approach.
        net.kyori.adventure.text.Component nameComponent = getItemDisplayNameComponent(item);

        // Build the label from the config template.
        // Replace {Amount} as a plain string first (just a number), then parse the whole
        // template as a legacy-ampersand Adventure Component.
        String templateRaw;
        if (amount <= 1) {
            templateRaw = plugin.getConfig().getString(
                    "ItemDisplay.Item.SingularText", "&f[&f{Item}&f]");
        } else {
            templateRaw = plugin.getConfig().getString(
                    "ItemDisplay.Item.Text", "&f[&f{Item} &bx{Amount}&f]");
        }
        String templateWithAmount = templateRaw.replace("{Amount}", String.valueOf(amount));
        net.kyori.adventure.text.Component labelTemplate =
                LegacyComponentSerializer.legacyAmpersand().deserialize(templateWithAmount);

        // Replace the literal "{Item}" token in the component tree with the name component,
        // matching IC's itemDisplayComponent.replaceText({Item} → itemDisplayNameComponent).
        net.kyori.adventure.text.Component labelComponent = labelTemplate.replaceText(
                net.kyori.adventure.text.TextReplacementConfig.builder()
                        .matchLiteral("{Item}")
                        .replacement(nameComponent)
                        .build());

        // Attach hover and click, then convert to BungeeCord via the Adventure→Bungee
        // serialiser (BungeeComponentSerializer).  We intentionally avoid the two-step
        // GsonComponentSerializer.serialize() + ComponentSerializer.parse() round-trip
        // because Adventure 4.14+ emits SHOW_ITEM hover events with a "contents" key
        // while BungeeCord's parser only understands the older "value" key - meaning the
        // item hover (and therefore all enchants) would be silently dropped.
        UUID snapId = createItemSnapshot(player, item);
        net.kyori.adventure.text.event.HoverEvent<?> itemHover = buildNativeItemHover(item);
        labelComponent = labelComponent
                .hoverEvent(itemHover)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                        "/qol:viewsnapshot " + snapId));

        return net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer
                .get().serialize(labelComponent);
    }

    /**
     * Calls {@code ItemStack.asHoverEvent()} via reflection - a Paper-only API that
     * returns a fully-populated {@code SHOW_ITEM} hover event including all data
     * components (1.20.5+) or NBT tags (1.16–1.20.4), exactly matching vanilla.
     * Falls back to a minimal text hover when the method is unavailable.
     */
    @SuppressWarnings("unchecked")
    private net.kyori.adventure.text.event.HoverEvent<?> buildNativeItemHover(ItemStack item) {
        try {
            java.lang.reflect.Method m = item.getClass().getMethod("asHoverEvent");
            return (net.kyori.adventure.text.event.HoverEvent<?>) m.invoke(item);
        } catch (Exception e) {
            // Non-Paper server or old version - plain text fallback
            return net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text(getItemDisplayName(item)));
        }
    }

    /**
     * Returns the item's display name as an Adventure {@link net.kyori.adventure.text.Component},
     * mirroring IC's {@code ItemStackUtils.getDisplayName()} approach:
     * <ul>
     *   <li>Custom-named items: their display name deserialized, wrapped in italic</li>
     *   <li>Default names: rarity-coloured plain text</li>
     * </ul>
     * <p>
     * We intentionally do NOT call {@code item.displayName()} here because on Paper 1.20.5+
     * that method returns the "item pick-up component" which wraps the name in {@code [...]},
     * causing double brackets when combined with the {@code [{Item}]} label template.
     * Instead we call {@code ItemMeta.displayName()} (Paper API on the meta object) which
     * gives back only the raw custom name component without any bracket decoration.
     */
    @SuppressWarnings("unchecked")
    private net.kyori.adventure.text.Component getItemDisplayNameComponent(ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            // Try Paper's ItemMeta.displayName() - returns the custom name component, no brackets
            try {
                java.lang.reflect.Method m = meta.getClass().getMethod("displayName");
                Object result = m.invoke(meta);
                if (result instanceof net.kyori.adventure.text.Component) {
                    return (net.kyori.adventure.text.Component) result;
                }
            } catch (Exception ignored) {}
            // Fallback: deserialize the legacy string + enforce italic (custom names are italic in vanilla)
            return LegacyComponentSerializer.legacySection()
                    .deserialize(meta.getDisplayName())
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                                net.kyori.adventure.text.format.TextDecoration.State.TRUE);
        }
        // No custom name - rarity-coloured plain text component
        ItemDisplayData.Rarity rarity = determineRarity(item, meta != null && meta.hasEnchants());
        net.kyori.adventure.text.format.TextColor color;
        switch (rarity) {
            case UNCOMMON: color = net.kyori.adventure.text.format.TextColor.color(0xFFFF55); break;
            case RARE:     color = net.kyori.adventure.text.format.TextColor.color(0x55FFFF); break;
            case EPIC:     color = net.kyori.adventure.text.format.TextColor.color(0xFF55FF); break;
            default:       color = net.kyori.adventure.text.format.NamedTextColor.WHITE;      break;
        }
        return net.kyori.adventure.text.Component.text(getItemDisplayName(item)).color(color);
    }

    /**
     * Builds a hover tooltip that closely mirrors the vanilla 1.16.5 advanced
     * tooltip (F3+H mode).  Still used by the Discord data builder and
     * inventory display path; kept as a text-only fallback.
     * <ol>
     *   <li>Item name - white (normal) or italic aqua (custom renamed)</li>
     *   <li>Enchantments - gray; curses in red</li>
     *   <li>Lore - dark-purple italic</li>
     *   <li>Attribute modifiers grouped by slot</li>
     *   <li>Unbreakable <em>or</em> Durability: current / max</li>
     *   <li>{@code minecraft:material} - dark gray</li>
     *   <li>Tag count estimate - dark gray</li>
     * </ol>
     */
    private BaseComponent[] buildItemHoverTextFallback(ItemStack item) {
        ComponentBuilder hover = new ComponentBuilder();
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;

        // 1. Item name 
        boolean hasCustomName = meta != null && meta.hasDisplayName();
        if (hasCustomName) {
            // Custom names render italic aqua in vanilla
            hover.append(TextComponent.fromLegacyText(
                    org.bukkit.ChatColor.AQUA + "" + org.bukkit.ChatColor.ITALIC + meta.getDisplayName()),
                    ComponentBuilder.FormatRetention.NONE);
        } else {
            // Default name renders white
            String prettyName = getItemDisplayName(item);
            hover.append(TextComponent.fromLegacyText(
                    org.bukkit.ChatColor.WHITE + prettyName),
                    ComponentBuilder.FormatRetention.NONE);
        }

        // 2. Enchantments 
        Map<Enchantment, Integer> enchants = new java.util.LinkedHashMap<>();
        if (meta != null && meta.hasEnchants()) {
            enchants.putAll(meta.getEnchants());
        }
        // Include enchants stored directly on the stack (books, etc.)
        for (Map.Entry<Enchantment, Integer> e : item.getEnchantments().entrySet()) {
            enchants.putIfAbsent(e.getKey(), e.getValue());
        }
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            hover.append("\n", ComponentBuilder.FormatRetention.NONE);
            boolean isCurse = e.getKey().equals(Enchantment.BINDING_CURSE)
                           || e.getKey().equals(Enchantment.VANISHING_CURSE);
            String colour = isCurse
                    ? org.bukkit.ChatColor.RED.toString()
                    : org.bukkit.ChatColor.GRAY.toString();
            String enchName = formatEnchantmentName(e.getKey());
            String level = e.getValue() > 1 || enchants.size() > 0
                    ? " " + toRoman(e.getValue()) : "";
            // Vanilla only shows the level numeral when max level > 1 or level > 1
            if (e.getKey().getMaxLevel() == 1 && e.getValue() == 1) {
                level = "";
            }
            hover.append(TextComponent.fromLegacyText(colour + enchName + level),
                    ComponentBuilder.FormatRetention.NONE);
        }

        // 3. Lore 
        if (meta != null && meta.hasLore()) {
            for (String line : meta.getLore()) {
                hover.append("\n", ComponentBuilder.FormatRetention.NONE);
                // Lore lines already contain their colour codes - wrap in purple italic as fallback
                hover.append(TextComponent.fromLegacyText(
                        org.bukkit.ChatColor.DARK_PURPLE + "" + org.bukkit.ChatColor.ITALIC + line),
                        ComponentBuilder.FormatRetention.NONE);
            }
        }

        // 4. Attribute modifiers 
        appendAttributeModifiers(hover, item, meta);

        // 5. Durability 
        if (meta instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) meta;
            if (meta.isUnbreakable()) {
                hover.append("\n", ComponentBuilder.FormatRetention.NONE);
                hover.append(TextComponent.fromLegacyText(
                        org.bukkit.ChatColor.BLUE + "Unbreakable"),
                        ComponentBuilder.FormatRetention.NONE);
            } else {
                short maxDurability = item.getType().getMaxDurability();
                if (maxDurability > 0) {
                    int remaining = maxDurability - dmg.getDamage();
                    hover.append("\n", ComponentBuilder.FormatRetention.NONE);
                    hover.append(TextComponent.fromLegacyText(
                            org.bukkit.ChatColor.WHITE + "Durability: " + remaining + " / " + maxDurability),
                            ComponentBuilder.FormatRetention.NONE);
                }
            }
        }

        // 6. minecraft:id 
        hover.append("\n", ComponentBuilder.FormatRetention.NONE);
        String materialId = "minecraft:" + item.getType().getKey().getKey();
        hover.append(TextComponent.fromLegacyText(
                org.bukkit.ChatColor.DARK_GRAY + materialId),
                ComponentBuilder.FormatRetention.NONE);

        // 7. NBT tag count (estimated) 
        int tagCount = estimateNbtTagCount(item, meta, enchants);
        if (tagCount > 0) {
            hover.append("\n", ComponentBuilder.FormatRetention.NONE);
            hover.append(TextComponent.fromLegacyText(
                    org.bukkit.ChatColor.DARK_GRAY + "" + tagCount + " tag(s)"),
                    ComponentBuilder.FormatRetention.NONE);
        }

        return hover.create();
    }

    // Attribute modifier rendering (vanilla style) 

    @SuppressWarnings("deprecation")
    private void appendAttributeModifiers(ComponentBuilder hover, ItemStack item, ItemMeta meta) {
        // Collect modifiers: explicit meta overrides → then default material attributes
        com.google.common.collect.Multimap<org.bukkit.attribute.Attribute, org.bukkit.attribute.AttributeModifier> modifiers = null;
        if (meta != null && meta.hasAttributeModifiers()) {
            modifiers = meta.getAttributeModifiers();
        }

        // Vanilla groups by equipment slot.  For items without explicit modifiers
        // we show the default attributes for the item's natural slot.
        // We'll show modifiers for each slot that has at least one entry.
        org.bukkit.inventory.EquipmentSlot[] slots = {
            org.bukkit.inventory.EquipmentSlot.HAND,
            org.bukkit.inventory.EquipmentSlot.OFF_HAND,
            org.bukkit.inventory.EquipmentSlot.HEAD,
            org.bukkit.inventory.EquipmentSlot.CHEST,
            org.bukkit.inventory.EquipmentSlot.LEGS,
            org.bukkit.inventory.EquipmentSlot.FEET
        };

        // If no custom modifiers, derive defaults from material base attributes
        if (modifiers == null || modifiers.isEmpty()) {
            modifiers = getDefaultAttributes(item);
        }
        if (modifiers == null || modifiers.isEmpty()) return;

        for (org.bukkit.inventory.EquipmentSlot slot : slots) {
            com.google.common.collect.Multimap<org.bukkit.attribute.Attribute, org.bukkit.attribute.AttributeModifier> slotMods =
                    com.google.common.collect.MultimapBuilder.hashKeys().arrayListValues().build();

            for (var entry : modifiers.entries()) {
                org.bukkit.attribute.AttributeModifier mod = entry.getValue();
                if (mod.getSlot() == null || mod.getSlot() == slot) {
                    slotMods.put(entry.getKey(), mod);
                }
            }
            if (slotMods.isEmpty()) continue;

            // Slot header
            hover.append("\n", ComponentBuilder.FormatRetention.NONE);
            hover.append("\n", ComponentBuilder.FormatRetention.NONE);
            String slotLabel = getSlotLabel(slot);
            hover.append(TextComponent.fromLegacyText(
                    org.bukkit.ChatColor.GRAY + "When in " + slotLabel + ":"),
                    ComponentBuilder.FormatRetention.NONE);

            for (var entry : slotMods.entries()) {
                org.bukkit.attribute.Attribute attr = entry.getKey();
                org.bukkit.attribute.AttributeModifier mod = entry.getValue();
                double amount = mod.getAmount();

                String attrName = formatAttributeName(attr);
                String line;

                if (mod.getOperation() == org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER) {
                    // Vanilla adds base values (e.g. +8 Attack Damage includes the base 1.0)
                    if (attr == org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE) {
                        amount += 1.0; // base entity attack damage
                    } else if (attr == org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED) {
                        amount += 4.0; // base attack speed
                    }
                    String sign = amount >= 0 ? "+" : "";
                    // Format to remove trailing zeros
                    String formatted = amount == (int) amount
                            ? String.valueOf((int) amount) : String.format("%.1f", amount);
                    line = org.bukkit.ChatColor.DARK_GREEN + " " + sign + formatted + " " + attrName;
                } else if (mod.getOperation() == org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR) {
                    String sign = amount >= 0 ? "+" : "";
                    String formatted = String.format("%.0f", amount * 100);
                    line = org.bukkit.ChatColor.DARK_GREEN + " " + sign + formatted + "% " + attrName;
                } else {
                    // MULTIPLY_SCALAR_1
                    String sign = amount >= 0 ? "+" : "";
                    String formatted = String.format("%.0f", amount * 100);
                    line = org.bukkit.ChatColor.DARK_GREEN + " " + sign + formatted + "% " + attrName;
                }

                // Negative values render red in vanilla
                if (amount < 0) {
                    line = line.replace(org.bukkit.ChatColor.DARK_GREEN.toString(),
                                        org.bukkit.ChatColor.RED.toString());
                }

                hover.append("\n", ComponentBuilder.FormatRetention.NONE);
                hover.append(TextComponent.fromLegacyText(line),
                        ComponentBuilder.FormatRetention.NONE);
            }
        }
    }

    /**
     * Derive the default attribute modifiers for common item types.
     * The Bukkit API does not expose default item attributes, so we hard-code
     * the vanilla 1.16 values for weapons, tools, and armor.
     */
    @SuppressWarnings("deprecation")
    private com.google.common.collect.Multimap<org.bukkit.attribute.Attribute, org.bukkit.attribute.AttributeModifier>
            getDefaultAttributes(ItemStack item) {
        Material mat = item.getType();
        com.google.common.collect.Multimap<org.bukkit.attribute.Attribute, org.bukkit.attribute.AttributeModifier> map =
                com.google.common.collect.MultimapBuilder.hashKeys().arrayListValues().build();

        // Attack damage & speed (weapons / tools) - values are the ADDED amount on top of base
        // Base attack damage = 1.0, base attack speed = 4.0
        double dmg = 0, spd = 0;
        org.bukkit.inventory.EquipmentSlot slot = org.bukkit.inventory.EquipmentSlot.HAND;
        boolean hasAttack = true;

        switch (mat) {
            // Swords
            case WOODEN_SWORD:      dmg = 3;  spd = -2.4; break;
            case STONE_SWORD:       dmg = 4;  spd = -2.4; break;
            case IRON_SWORD:        dmg = 5;  spd = -2.4; break;
            case GOLDEN_SWORD:      dmg = 3;  spd = -2.4; break;
            case DIAMOND_SWORD:     dmg = 6;  spd = -2.4; break;
            case NETHERITE_SWORD:   dmg = 7;  spd = -2.4; break;
            // Axes
            case WOODEN_AXE:        dmg = 6;  spd = -3.2; break;
            case STONE_AXE:         dmg = 8;  spd = -3.2; break;
            case IRON_AXE:          dmg = 8;  spd = -3.1; break;
            case GOLDEN_AXE:        dmg = 6;  spd = -3.0; break;
            case DIAMOND_AXE:       dmg = 8;  spd = -3.0; break;
            case NETHERITE_AXE:     dmg = 9;  spd = -3.0; break;
            // Pickaxes
            case WOODEN_PICKAXE:    dmg = 1;  spd = -2.8; break;
            case STONE_PICKAXE:     dmg = 2;  spd = -2.8; break;
            case IRON_PICKAXE:      dmg = 3;  spd = -2.8; break;
            case GOLDEN_PICKAXE:    dmg = 1;  spd = -2.8; break;
            case DIAMOND_PICKAXE:   dmg = 4;  spd = -2.8; break;
            case NETHERITE_PICKAXE: dmg = 5;  spd = -2.8; break;
            // Shovels
            case WOODEN_SHOVEL:     dmg = 1.5; spd = -3.0; break;
            case STONE_SHOVEL:      dmg = 2.5; spd = -3.0; break;
            case IRON_SHOVEL:       dmg = 3.5; spd = -3.0; break;
            case GOLDEN_SHOVEL:     dmg = 1.5; spd = -3.0; break;
            case DIAMOND_SHOVEL:    dmg = 4.5; spd = -3.0; break;
            case NETHERITE_SHOVEL:  dmg = 5.5; spd = -3.0; break;
            // Hoes
            case WOODEN_HOE:        dmg = 0;  spd = -3.0; break;
            case STONE_HOE:         dmg = 0;  spd = -2.0; break;
            case IRON_HOE:          dmg = 0;  spd = -1.0; break;
            case GOLDEN_HOE:        dmg = 0;  spd = -3.0; break;
            case DIAMOND_HOE:       dmg = 0;  spd =  0.0; break;
            case NETHERITE_HOE:     dmg = 0;  spd =  0.0; break;
            // Trident
            case TRIDENT:           dmg = 8;  spd = -2.9; break;
            default: hasAttack = false; break;
        }

        if (hasAttack) {
            map.put(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE,
                    new org.bukkit.attribute.AttributeModifier(
                            UUID.randomUUID(), "generic.attackDamage", dmg,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, slot));
            map.put(org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED,
                    new org.bukkit.attribute.AttributeModifier(
                            UUID.randomUUID(), "generic.attackSpeed", spd,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, slot));
        }

        // Armor - defense & toughness & knockback resistance
        double armor = 0, toughness = 0, kbRes = 0;
        org.bukkit.inventory.EquipmentSlot armorSlot = null;
        boolean hasArmor = true;

        switch (mat) {
            // Helmets
            case LEATHER_HELMET:     armor = 1; armorSlot = org.bukkit.inventory.EquipmentSlot.HEAD; break;
            case CHAINMAIL_HELMET:   armor = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.HEAD; break;
            case IRON_HELMET:        armor = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.HEAD; break;
            case GOLDEN_HELMET:      armor = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.HEAD; break;
            case DIAMOND_HELMET:     armor = 3; toughness = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.HEAD; break;
            case NETHERITE_HELMET:   armor = 3; toughness = 3; kbRes = 0.1; armorSlot = org.bukkit.inventory.EquipmentSlot.HEAD; break;
            case TURTLE_HELMET:      armor = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.HEAD; break;
            // Chestplates
            case LEATHER_CHESTPLATE:   armor = 3; armorSlot = org.bukkit.inventory.EquipmentSlot.CHEST; break;
            case CHAINMAIL_CHESTPLATE: armor = 5; armorSlot = org.bukkit.inventory.EquipmentSlot.CHEST; break;
            case IRON_CHESTPLATE:      armor = 6; armorSlot = org.bukkit.inventory.EquipmentSlot.CHEST; break;
            case GOLDEN_CHESTPLATE:    armor = 5; armorSlot = org.bukkit.inventory.EquipmentSlot.CHEST; break;
            case DIAMOND_CHESTPLATE:   armor = 8; toughness = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.CHEST; break;
            case NETHERITE_CHESTPLATE: armor = 8; toughness = 3; kbRes = 0.1; armorSlot = org.bukkit.inventory.EquipmentSlot.CHEST; break;
            // Leggings
            case LEATHER_LEGGINGS:   armor = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.LEGS; break;
            case CHAINMAIL_LEGGINGS: armor = 4; armorSlot = org.bukkit.inventory.EquipmentSlot.LEGS; break;
            case IRON_LEGGINGS:      armor = 5; armorSlot = org.bukkit.inventory.EquipmentSlot.LEGS; break;
            case GOLDEN_LEGGINGS:    armor = 3; armorSlot = org.bukkit.inventory.EquipmentSlot.LEGS; break;
            case DIAMOND_LEGGINGS:   armor = 6; toughness = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.LEGS; break;
            case NETHERITE_LEGGINGS: armor = 6; toughness = 3; kbRes = 0.1; armorSlot = org.bukkit.inventory.EquipmentSlot.LEGS; break;
            // Boots
            case LEATHER_BOOTS:    armor = 1; armorSlot = org.bukkit.inventory.EquipmentSlot.FEET; break;
            case CHAINMAIL_BOOTS:  armor = 1; armorSlot = org.bukkit.inventory.EquipmentSlot.FEET; break;
            case IRON_BOOTS:       armor = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.FEET; break;
            case GOLDEN_BOOTS:     armor = 1; armorSlot = org.bukkit.inventory.EquipmentSlot.FEET; break;
            case DIAMOND_BOOTS:    armor = 3; toughness = 2; armorSlot = org.bukkit.inventory.EquipmentSlot.FEET; break;
            case NETHERITE_BOOTS:  armor = 3; toughness = 3; kbRes = 0.1; armorSlot = org.bukkit.inventory.EquipmentSlot.FEET; break;
            default: hasArmor = false; break;
        }

        if (hasArmor && armorSlot != null) {
            map.put(org.bukkit.attribute.Attribute.GENERIC_ARMOR,
                    new org.bukkit.attribute.AttributeModifier(
                            UUID.randomUUID(), "generic.armor", armor,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, armorSlot));
            if (toughness > 0) {
                map.put(org.bukkit.attribute.Attribute.GENERIC_ARMOR_TOUGHNESS,
                        new org.bukkit.attribute.AttributeModifier(
                                UUID.randomUUID(), "generic.armorToughness", toughness,
                                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, armorSlot));
            }
            if (kbRes > 0) {
                map.put(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE,
                        new org.bukkit.attribute.AttributeModifier(
                                UUID.randomUUID(), "generic.knockbackResistance", kbRes,
                                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, armorSlot));
            }
        }

        return map;
    }

    private static String getSlotLabel(org.bukkit.inventory.EquipmentSlot slot) {
        switch (slot) {
            case HAND:     return "Main Hand";
            case OFF_HAND: return "Off Hand";
            case HEAD:     return "Head";
            case CHEST:    return "Chest";
            case LEGS:     return "Legs";
            case FEET:     return "Feet";
            default:       return slot.name();
        }
    }

    private static String formatAttributeName(org.bukkit.attribute.Attribute attr) {
        // GENERIC_ATTACK_DAMAGE → "Attack Damage"
        String raw = attr.name();
        if (raw.startsWith("GENERIC_")) raw = raw.substring(8);
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : raw.toCharArray()) {
            if (c == '_') { sb.append(' '); cap = true; }
            else { sb.append(cap ? Character.toUpperCase(c) : Character.toLowerCase(c)); cap = false; }
        }
        return sb.toString();
    }

    /**
     * Estimates the number of NBT tags on the item (vanilla shows this in F3+H mode).
     * Bukkit doesn't expose raw NBT, so we count known meta properties.
     */
    private int estimateNbtTagCount(ItemStack item, ItemMeta meta,
                                    Map<Enchantment, Integer> enchants) {
        if (meta == null) return 0;
        int count = 0;
        if (meta.hasDisplayName()) count++;
        if (meta.hasLore()) count++;
        if (!enchants.isEmpty()) count++;
        if (meta.hasAttributeModifiers()) count++;
        if (meta.isUnbreakable()) count++;
        if (meta instanceof org.bukkit.inventory.meta.Damageable
                && ((org.bukkit.inventory.meta.Damageable) meta).getDamage() > 0) count++;
        if (meta.hasCustomModelData()) count++;
        // Repair cost
        if (meta instanceof org.bukkit.inventory.meta.Repairable
                && ((org.bukkit.inventory.meta.Repairable) meta).getRepairCost() > 0) count++;
        // ItemFlags (HideEnchants etc.)
        if (!meta.getItemFlags().isEmpty()) count++;
        return count;
    }

    // Inventory / Ender Chest component 

    private BaseComponent[] buildInventoryComponent(Player player, boolean enderChest) {
        String rawLabel;
        String title;
        Inventory source;

        if (enderChest) {
            // Use IC's EnderChest.Text with %player_name% substituted
            String template = plugin.getConfig().getString(
                    "ItemDisplay.EnderChest.Text", "&f[&d%player_name%'s Ender Chest&f]");
            rawLabel = template.replace("%player_name%", player.getDisplayName());
            title = plugin.getConfig().getString(
                    "ItemDisplay.EnderChest.InventoryTitle", "%player_name%'s Ender Chest")
                    .replace("%player_name%", player.getName());
            source = player.getEnderChest();
        } else {
            // Use IC's Inventory.Text with %player_name% substituted
            String template = plugin.getConfig().getString(
                    "ItemDisplay.Inventory.Text", "&f[&b%player_name%'s Inventory&f]");
            rawLabel = template.replace("%player_name%", player.getDisplayName());
            title = plugin.getConfig().getString(
                    "ItemDisplay.Inventory.InventoryTitle", "%player_name%'s Inventory")
                    .replace("%player_name%", player.getName());
            source = player.getInventory();
        }

        String label = org.bukkit.ChatColor.translateAlternateColorCodes('&', rawLabel);

        // Create a frozen snapshot
        UUID snapId = createInventorySnapshot(source, title);

        TextComponent comp = new TextComponent(TextComponent.fromLegacyText(label));

        // Hover: summary of contents (with IC HoverMessage from config)
        comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(buildInventoryHoverText(source, enderChest, player.getName()))));

        // Click: open the snapshot
        comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/qol:viewsnapshot " + snapId));

        return new BaseComponent[]{comp};
    }

    private BaseComponent[] buildInventoryHoverText(Inventory inv, boolean enderChest, String playerName) {
        ComponentBuilder hover = new ComponentBuilder();

        String headerColor = enderChest
                ? org.bukkit.ChatColor.LIGHT_PURPLE.toString()
                : org.bukkit.ChatColor.GREEN.toString();
        hover.append(TextComponent.fromLegacyText(
                headerColor + (enderChest ? "Ender Chest" : "Inventory") + " of " + playerName),
                ComponentBuilder.FormatRetention.NONE);

        // Count non-air items
        int usedSlots = 0;
        int totalSlots = inv.getSize();
        Map<String, Integer> itemCounts = new HashMap<>();

        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() != Material.AIR) {
                usedSlots++;
                String name = getItemDisplayName(stack);
                itemCounts.merge(name, stack.getAmount(), Integer::sum);
            }
        }

        hover.append("\n", ComponentBuilder.FormatRetention.NONE);
        hover.append(TextComponent.fromLegacyText(
                org.bukkit.ChatColor.GRAY + "Slots used: " + usedSlots + "/" + totalSlots),
                ComponentBuilder.FormatRetention.NONE);

        // Show top 5 items
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(itemCounts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int shown = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            if (shown >= 5) break;
            hover.append("\n", ComponentBuilder.FormatRetention.NONE);
            hover.append(TextComponent.fromLegacyText(
                    org.bukkit.ChatColor.GRAY + "  " + entry.getKey() + " x" + entry.getValue()),
                    ComponentBuilder.FormatRetention.NONE);
            shown++;
        }

        if (itemCounts.size() > 5) {
            hover.append("\n", ComponentBuilder.FormatRetention.NONE);
            hover.append(TextComponent.fromLegacyText(
                    org.bukkit.ChatColor.DARK_GRAY + "  ..." + (itemCounts.size() - 5) + " more types"),
                    ComponentBuilder.FormatRetention.NONE);
        }

        hover.append("\n\n", ComponentBuilder.FormatRetention.NONE);
        hover.append(TextComponent.fromLegacyText(
                org.bukkit.ChatColor.YELLOW + "Click to view"),
                ComponentBuilder.FormatRetention.NONE);

        return hover.create();
    }

    // Snapshot management 

    private UUID createItemSnapshot(Player player, ItemStack item) {
        UUID id = UUID.randomUUID();
        String title = getItemDisplayName(item);
        // Single-slot chest that shows the item
        Inventory inv = Bukkit.createInventory(null, 27,
                org.bukkit.ChatColor.DARK_AQUA + title);
        inv.setItem(13, item.clone()); // Centre slot
        snapshots.put(id, new SnapshotEntry(inv, title));
        return id;
    }

    private UUID createInventorySnapshot(Inventory source, String title) {
        UUID id = UUID.randomUUID();
        // Clone into a chest inventory matching the source size (rounded up to multiple of 9)
        int size = source.getSize();
        // Clamp to max 54 (double chest)
        if (size > 54) size = 54;
        // Round up to nearest 9
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        if (size < 9) size = 9;

        Inventory snapshot = Bukkit.createInventory(null, size,
                org.bukkit.ChatColor.DARK_AQUA + title);
        ItemStack[] contents = source.getContents();
        for (int i = 0; i < Math.min(contents.length, size); i++) {
            if (contents[i] != null) {
                snapshot.setItem(i, contents[i].clone());
            }
        }
        snapshots.put(id, new SnapshotEntry(snapshot, title));
        return id;
    }

    // Utility 

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        // Pretty-print material name: DIAMOND_SWORD → Diamond Sword
        String name = item.getType().name();
        StringBuilder sb = new StringBuilder();
        boolean capitalise = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                capitalise = true;
            } else {
                sb.append(capitalise ? Character.toUpperCase(c) : Character.toLowerCase(c));
                capitalise = false;
            }
        }
        return sb.toString();
    }

    /**
     * Builds the Discord replacement text for the [item] keyword.
     * Uses ItemDisplay.Item.SingularText (amount=1) or ItemDisplay.Item.Text (amount>1),
     * substitutes {Item} and {Amount}, then strips colour codes - matching IC dsrv.
     */
    private String getItemDiscordText(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            // IC's itemAirAllow path: use singular text with "Air" as item name
            String singular = plugin.getConfig().getString(
                    "ItemDisplay.Item.SingularText", "&f[&f{Item}&f]");
            return org.bukkit.ChatColor.stripColor(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&',
                            singular.replace("{Item}", "Air").replace("{Amount}", "0")));
        }
        String displayName = org.bukkit.ChatColor.stripColor(getItemDisplayName(item));
        int amount = item.getAmount();

        String template;
        if (amount <= 1) {
            template = plugin.getConfig().getString(
                    "ItemDisplay.Item.SingularText", "&f[&f{Item}&f]");
        } else {
            template = plugin.getConfig().getString(
                    "ItemDisplay.Item.Text", "&f[&f{Item} &bx{Amount}&f]");
        }
        String replaced = template
                .replace("{Item}", displayName)
                .replace("{Amount}", String.valueOf(amount));
        return org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', replaced));
    }

    /**
     * Builds the Discord replacement text for the [inv] keyword.
     * Uses ItemDisplay.Inventory.Text, substitutes %player_name%, then strips colour codes.
     */
    private String getInvDiscordText(Player player) {
        String template = plugin.getConfig().getString(
                "ItemDisplay.Inventory.Text", "&f[&b%player_name%'s Inventory&f]");
        String replaced = template.replace("%player_name%", player.getName());
        return org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', replaced));
    }

    /**
     * Builds the Discord replacement text for the [ender] keyword.
     * Uses ItemDisplay.EnderChest.Text, substitutes %player_name%, then strips colour codes.
     */
    private String getEnderDiscordText(Player player) {
        String template = plugin.getConfig().getString(
                "ItemDisplay.EnderChest.Text", "&f[&d%player_name%'s Ender Chest&f]");
        String replaced = template.replace("%player_name%", player.getName());
        return org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', replaced));
    }

    private static String formatEnchantmentName(Enchantment ench) {
        // getKey().getKey() → "sharpness", "fire_aspect"  →  "Sharpness", "Fire Aspect"
        String raw = ench.getKey().getKey();
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : raw.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                cap = true;
            } else {
                sb.append(cap ? Character.toUpperCase(c) : c);
                cap = false;
            }
        }
        return sb.toString();
    }

    private static String toRoman(int value) {
        if (value <= 0 || value > 10) return String.valueOf(value);
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return numerals[value];
    }

    // Snapshot entry 

    private static class SnapshotEntry {
        final Inventory inventory;
        final String title;
        final long createdAt;

        SnapshotEntry(Inventory inventory, String title) {
            this.inventory = inventory;
            this.title = title;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
