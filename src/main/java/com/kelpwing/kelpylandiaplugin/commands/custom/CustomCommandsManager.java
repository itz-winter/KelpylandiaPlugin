package com.kelpwing.kelpylandiaplugin.commands.custom;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads {@code custom-commands.yml} from the plugin data folder and registers
 * all aliases and custom commands dynamically at startup.
 *
 * <h3>Config structure expected</h3>
 * <pre>
 * aliases:
 *   alias-name:
 *     command: "some-command arg arg"
 *     permission: "perm.node"          # optional
 *     no-permission-message: "..."     # optional
 *     description: "..."               # optional
 *
 * custom_commands:
 *   command-name:
 *     function: |                      # inline KS Lite script
 *       ...
 *     file: "scripts/foo.ks"           # OR load script from this path (relative to data folder)
 *     permission: "perm.node"
 *     no-permission-message: "..."
 *     description: "..."
 *     usage: "/command-name <arg>"
 * </pre>
 */
public final class CustomCommandsManager {

    private final KelpylandiaPlugin plugin;
    private final Logger log;

    /** All registered custom command entries (for reload use). */
    private final List<CustomCommandEntry> entries = new ArrayList<>();

    public CustomCommandsManager(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    //  Public API

    /**
     * Copies the default {@code custom-commands.yml} to the data folder (if absent),
     * then reads and registers every alias and custom command defined in it.
     */
    public void loadAndRegister() {
        File configFile = saveDefaultIfAbsent("custom-commands.yml");
        if (configFile == null) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);

        int aliasCount  = registerAliases(cfg);
        int customCount = registerCustomCommands(cfg);

        log.info("[CustomCommands] Registered " + aliasCount + " alias(es) and "
                + customCount + " custom command(s).");
    }

        //  Aliases
    
    private int registerAliases(YamlConfiguration cfg) {
        ConfigurationSection aliases = cfg.getConfigurationSection("aliases");
        if (aliases == null) return 0;

        int count = 0;
        for (String aliasName : aliases.getKeys(false)) {
            ConfigurationSection a = aliases.getConfigurationSection(aliasName);
            if (a == null) continue;

            String command          = a.getString("command", "").trim();
            String permission       = a.getString("permission", "");
            String noPerm           = a.getString("no-permission-message", "");
            String description      = a.getString("description", "An alias command.");

            final String finalCmd = command;
            final String finalPerm = permission;
            final String finalNoPerm = noPerm;

            CommandExecutor executor = (sender, cmd, label, args) -> {
                if (!finalPerm.isEmpty() && !sender.hasPermission(finalPerm)) {
                    String msg = finalNoPerm.isEmpty()
                        ? ChatColor.RED + "You don't have permission to use this command."
                        : ChatColor.translateAlternateColorCodes('&', finalNoPerm);
                    sender.sendMessage(msg);
                    return true;
                }
                if (!finalCmd.isEmpty()) {
                    // Append any arguments the user passed
                    String full = finalCmd;
                    if (args.length > 0) full += " " + String.join(" ", args);
                    if (sender instanceof org.bukkit.entity.Player) {
                        ((org.bukkit.entity.Player) sender).performCommand(full);
                    } else {
                        org.bukkit.Bukkit.dispatchCommand(sender, full);
                    }
                }
                return true;
            };

            plugin.registerCommand(aliasName, executor, description, "/" + aliasName,
                    permission.isEmpty() ? null : permission);
            count++;
        }
        return count;
    }

        //  Custom commands
    
    private int registerCustomCommands(YamlConfiguration cfg) {
        ConfigurationSection commands = cfg.getConfigurationSection("custom_commands");
        if (commands == null) return 0;

        int count = 0;
        for (String cmdName : commands.getKeys(false)) {
            ConfigurationSection c = commands.getConfigurationSection(cmdName);
            if (c == null) continue;

            // Inline script (YAML block scalar under "function:") or external file
            String inlineScript = c.getString("function", null);
            String file         = c.getString("file", null);

            // Blank function value → treat as null (no script defined)
            if (inlineScript != null && inlineScript.isBlank()) inlineScript = null;
            if (file         != null && file.isBlank())         file         = null;

            if (inlineScript == null && file == null) {
                log.warning("[CustomCommands] Command '" + cmdName
                        + "' has no 'function' or 'file' — skipping.");
                continue;
            }

            CustomCommandEntry entry = new CustomCommandEntry(
                cmdName,
                inlineScript,
                file,
                c.getString("permission", ""),
                c.getString("no-permission-message", ""),
                c.getString("description", "A custom command."),
                c.getString("usage", "/" + cmdName)
            );

            entries.add(entry);

            CustomCommandExecutor executor = new CustomCommandExecutor(plugin, entry);
            plugin.registerCommand(
                cmdName,
                executor,
                entry.description,
                entry.usage,
                entry.permission.isEmpty() ? null : entry.permission
            );
            count++;
        }
        return count;
    }

        //  Helpers
    
    /**
     * Copies the bundled resource to the plugin data folder if it does not
     * already exist there, then returns the {@link File} handle.
     */
    private File saveDefaultIfAbsent(String resourceName) {
        File target = new File(plugin.getDataFolder(), resourceName);
        if (!target.exists()) {
            target.getParentFile().mkdirs();
            try (InputStream in = plugin.getResource(resourceName)) {
                if (in != null) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("[CustomCommands] Saved default " + resourceName + " to plugin folder.");
                } else {
                    log.warning("[CustomCommands] No bundled resource found for " + resourceName
                            + " and file does not exist; skipping custom commands.");
                    return null;
                }
            } catch (IOException e) {
                log.warning("[CustomCommands] Could not save default " + resourceName + ": " + e.getMessage());
                return null;
            }
        }
        return target;
    }
}
