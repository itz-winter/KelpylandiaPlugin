package com.kelpwing.kelpylandiaplugin.utils;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages player nicknames — loading, saving, and applying display names.
 * Nicknames are stored in nicknames.yml keyed by player UUID.
 */
public class NickManager {

    private final KelpylandiaPlugin plugin;
    private File nicknamesFile;
    private FileConfiguration nicknamesConfig;

    // Cache: UUID -> raw nickname string (with color codes already translated)
    private final Map<UUID, String> nicknameCache = new ConcurrentHashMap<>();

    public NickManager(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        setupFile();
        loadAll();
    }

    private void setupFile() {
        nicknamesFile = new File(plugin.getDataFolder(), "nicknames.yml");
        if (!nicknamesFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                nicknamesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create nicknames.yml!");
                e.printStackTrace();
            }
        }
        nicknamesConfig = YamlConfiguration.loadConfiguration(nicknamesFile);
    }

    private void loadAll() {
        nicknameCache.clear();
        for (String uuidStr : nicknamesConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String raw = nicknamesConfig.getString(uuidStr);
                if (raw != null && !raw.isEmpty()) {
                    nicknameCache.put(uuid, raw);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        try {
            nicknamesConfig.save(nicknamesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save nicknames.yml!");
            e.printStackTrace();
        }
    }

    /**
     * Sets a nickname for a player and immediately applies it.
     *
     * @param player  the player
     * @param rawNick the nickname with {@code &} color codes and custom hex codes {@code &#33c70fS} (untranslated)
     */
    public void setNickname(Player player, String rawNick) {
        String colored = translateColors(rawNick);
        nicknameCache.put(player.getUniqueId(), rawNick);
        nicknamesConfig.set(player.getUniqueId().toString(), rawNick);
        save();
        applyNickname(player, colored);
    }

    /**
     * Removes a player's nickname and resets their display name.
     */
    public void removeNickname(Player player) {
        nicknameCache.remove(player.getUniqueId());
        nicknamesConfig.set(player.getUniqueId().toString(), null);
        save();
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
    }

    /**
     * Applies the cached nickname to a player (e.g. on join).
     * Does nothing if the player has no saved nickname.
     */
    public void applyNickname(Player player) {
        String rawNick = nicknameCache.get(player.getUniqueId());
        if (rawNick != null) {
            String colored = translateColors(rawNick);
            applyNickname(player, colored);
        }
    }

    /**
     * Translates both standard {@code &} color codes and custom hex codes {@code &#33c70fS}.
     * 
     * @param input the string with color codes
     * @return the translated string with Bukkit color codes
     */
    private String translateColors(String input) {
        if (input == null) {
            return "";
        }
        
        // First translate standard & color codes
        String translated = ChatColor.translateAlternateColorCodes('&', input);
        
        // Then translate custom hex codes &#33c70fS -> §x§3§3§c§7§0§f
        Pattern hexPattern = Pattern.compile("&#([a-fA-F0-9]{6})([a-zA-Z])");
        Matcher matcher = hexPattern.matcher(translated);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String character = matcher.group(2);
            
            // Convert &#RRGGBBC to §x§R§R§G§G§B§B§C
            String replacement = "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2) + 
                               "§" + hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5) + "§" + character;
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    private void applyNickname(Player player, String colored) {
        player.setDisplayName(colored);
        player.setPlayerListName(colored);
    }

    /**
     * Returns the raw (untranslated) saved nickname, or {@code null} if none.
     */
    public String getRawNickname(UUID uuid) {
        return nicknameCache.get(uuid);
    }

    /**
     * Checks whether a player currently has a nickname set.
     */
    public boolean hasNickname(UUID uuid) {
        return nicknameCache.containsKey(uuid);
    }
}
