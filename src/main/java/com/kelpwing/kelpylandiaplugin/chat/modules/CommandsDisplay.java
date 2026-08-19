/*
 * This file is part of InteractiveChat4.
 *
 * Copyright (C) 2020 - 2025. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2020 - 2025. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.kelpwing.kelpylandiaplugin.chat.modules;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.chat.utils.ComponentReplacing;
import com.kelpwing.kelpylandiaplugin.chat.utils.ComponentUtils;
import com.kelpwing.kelpylandiaplugin.chat.utils.CustomStringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

public class CommandsDisplay {

    private static final String PATTERN_PREFIX = "(?i)(?:(?<!\\\\)(\\\\)\\\\|(?<!\\\\))";
    private static final String PATTERN_COMMAND = "(/(?:[^\\\\%s]|(?:\\\\[\\\\%s]))*)";
    private static final String ESCAPING_PATTERN = "\\\\([\\\\%s])";

    private static final String ESCAPE_CLEARUP_PREFIX = "(?i)\\\\(";
    private static final String ESCAPE_CLEARUP_COMMAND = "/(?:[^\\\\%s]|(?:\\\\[\\\\%s]))*";
    private static final String ESCAPE_CLEARUP_SUFFIX = ")";

    private static final String JOINT_PATTERN = "(%c)|(%e)";

    // Helpers to read our config (same key names as IC) 

    private static String getFormat() {
        JavaPlugin plugin = KelpylandiaPlugin.getInstance();
        return plugin.getConfig().getString("Commands.Format", "[{Command}]");
    }

    private static String getDisplayText() {
        JavaPlugin plugin = KelpylandiaPlugin.getInstance();
        return plugin.getConfig().getString("Commands.Text", "&b[&e{Command}&b]");
    }

    private static ClickEvent.Action getAction() {
        JavaPlugin plugin = KelpylandiaPlugin.getInstance();
        String raw = plugin.getConfig().getString("Commands.Action", "SUGGEST_COMMAND");
        try {
            return ClickEvent.Action.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ClickEvent.Action.SUGGEST_COMMAND;
        }
    }

    private static Component getHoverText() {
        JavaPlugin plugin = KelpylandiaPlugin.getInstance();
        List<String> lines = plugin.getConfig().getStringList("Commands.HoverMessage");
        if (lines.isEmpty()) return null;
        String joined = String.join("\n", lines);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(joined);
    }

    // Enabled check 

    public static boolean isEnabled() {
        return KelpylandiaPlugin.getInstance().getConfig().getBoolean("Commands.Enabled", true);
    }

    // Core process method (logic identical to IC) 

    public static Component process(Component component) {
        String format = getFormat();
        Set<Character> chars = CustomStringUtils.getCharacterSet(format.replace("{Command}", ""));
        StringBuilder sb = new StringBuilder();
        for (Character c : chars) {
            sb.append(CustomStringUtils.escapeMetaCharacters(c.toString()));
        }
        String escapeChars = sb.toString();
        String commandMatchingPattern = PATTERN_PREFIX + CustomStringUtils.escapeMetaCharacters(format.replace("{Command}", "\0\0\0")).replace("\0\0\0", PATTERN_COMMAND.replace("%s", escapeChars));
        String escapeMatchingPattern = ESCAPE_CLEARUP_PREFIX + CustomStringUtils.escapeMetaCharacters(format.replace("{Command}", "\0\0\0")).replace("\0\0\0", ESCAPE_CLEARUP_COMMAND.replace("%s", escapeChars)) + ESCAPE_CLEARUP_SUFFIX;

        String pattern = JOINT_PATTERN.replace("%c", commandMatchingPattern).replace("%e", escapeMatchingPattern);

        final String displayText = getDisplayText();
        final ClickEvent.Action clickAction = getAction();
        final Component hoverText = getHoverText();

        return ComponentReplacing.replace(component, pattern, result -> {
            if (result.group(1) != null) {
                String escape = result.group(2);
                String command = result.group(3).replaceAll(ESCAPING_PATTERN.replace("%s", escapeChars), "$1");
                String componentText = displayText.replace("{Command}", command);
                if (escape != null) {
                    componentText = escape + componentText;
                }
                Component commandComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(componentText);
                commandComponent = commandComponent.clickEvent(ClickEvent.clickEvent(clickAction, command));
                if (hoverText != null && !ComponentUtils.isEmpty(hoverText)) {
                    commandComponent = commandComponent.hoverEvent(HoverEvent.showText(hoverText));
                }
                return commandComponent;
            } else if (result.group(4) != null) {
                return result.componentGroup(5);
            } else {
                return result.componentGroup();
            }
        });
    }

}
