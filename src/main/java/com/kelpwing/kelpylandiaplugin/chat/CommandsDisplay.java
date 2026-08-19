package com.kelpwing.kelpylandiaplugin.chat;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/**
 * Adapter facade over {@link com.kelpwing.kelpylandiaplugin.chat.modules.CommandsDisplay}
 * that exposes BungeeCord-compatible helpers for callers (e.g. ItemDisplayManager) that
 * are already operating in the BungeeCord BaseComponent world.
 *
 * <p>Click and hover events are preserved via an Adventure JSON → BungeeCord round-trip,
 * which is lossless because both serializers use the same underlying Minecraft chat-component
 * JSON format.
 */
public final class CommandsDisplay {

    private CommandsDisplay() {}

    /**
     * Returns {@code true} if the legacy-colour string contains a command pattern
     * that CommandsDisplay would replace.
     */
    public static boolean containsCommand(KelpylandiaPlugin plugin, String legacy) {
        if (!com.kelpwing.kelpylandiaplugin.chat.modules.CommandsDisplay.isEnabled()) return false;
        Component c = LegacyComponentSerializer.legacySection().deserialize(legacy);
        Component processed = com.kelpwing.kelpylandiaplugin.chat.modules.CommandsDisplay.process(c);
        return !LegacyComponentSerializer.legacySection().serialize(c)
                .equals(LegacyComponentSerializer.legacySection().serialize(processed));
    }

    /**
     * Processes command-box patterns in a legacy-colour string and returns
     * BungeeCord {@link BaseComponent}[] with click and hover events intact.
     *
     * <p>The conversion path is:
     * legacy string → Adventure Component → CommandsDisplay.process → Adventure JSON
     * → BungeeCord ComponentSerializer.parse - which is lossless for click/hover events.
     */
    public static BaseComponent[] process(KelpylandiaPlugin plugin, String legacy) {
        Component component = LegacyComponentSerializer.legacySection().deserialize(legacy);
        Component processed = com.kelpwing.kelpylandiaplugin.chat.modules.CommandsDisplay.process(component);
        String json = GsonComponentSerializer.gson().serialize(processed);
        return ComponentSerializer.parse(json);
    }
}
