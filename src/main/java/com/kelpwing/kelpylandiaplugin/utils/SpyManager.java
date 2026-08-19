package com.kelpwing.kelpylandiaplugin.utils;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages SocialSpy and CommandSpy toggles.
 * SocialSpy: see all private messages (/w) and non-global channel chat.
 * CommandSpy: see all commands executed by other players.
 */
public class SpyManager {

    private final KelpylandiaPlugin plugin;
    private final Set<UUID> socialSpies = ConcurrentHashMap.newKeySet();
    private final Set<UUID> commandSpies = ConcurrentHashMap.newKeySet();

    public SpyManager(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    // SocialSpy 

    public boolean toggleSocialSpy(UUID uuid) {
        if (socialSpies.contains(uuid)) {
            socialSpies.remove(uuid);
            persistToggle(uuid, "socialspy", false);
            return false;
        }
        socialSpies.add(uuid);
        persistToggle(uuid, "socialspy", true);
        return true;
    }

    public boolean isSocialSpy(UUID uuid) {
        return socialSpies.contains(uuid);
    }

    /** Explicitly set social spy state (used by state persistence on join - no re-save). */
    public void setSocialSpy(UUID uuid, boolean enabled) {
        if (enabled) socialSpies.add(uuid);
        else socialSpies.remove(uuid);
    }

    public Set<UUID> getSocialSpies() {
        return Collections.unmodifiableSet(socialSpies);
    }

    // CommandSpy 

    public boolean toggleCommandSpy(UUID uuid) {
        if (commandSpies.contains(uuid)) {
            commandSpies.remove(uuid);
            persistToggle(uuid, "commandspy", false);
            return false;
        }
        commandSpies.add(uuid);
        persistToggle(uuid, "commandspy", true);
        return true;
    }

    public boolean isCommandSpy(UUID uuid) {
        return commandSpies.contains(uuid);
    }

    /** Explicitly set command spy state (used by state persistence on join - no re-save). */
    public void setCommandSpy(UUID uuid, boolean enabled) {
        if (enabled) commandSpies.add(uuid);
        else commandSpies.remove(uuid);
    }

    public Set<UUID> getCommandSpies() {
        return Collections.unmodifiableSet(commandSpies);
    }

    // Cleanup 

    /** Remove in-memory state only. The file-based state is NOT cleared
     *  so that the persisted toggle survives relog. */
    public void removePlayer(UUID uuid) {
        socialSpies.remove(uuid);
        commandSpies.remove(uuid);
    }

    // Persistence helper 

    private void persistToggle(UUID uuid, String key, boolean value) {
        PlayerStateManager stateManager = plugin.getPlayerStateManager();
        if (stateManager != null) {
            stateManager.saveToggle(uuid, key, value);
        }
    }
}
