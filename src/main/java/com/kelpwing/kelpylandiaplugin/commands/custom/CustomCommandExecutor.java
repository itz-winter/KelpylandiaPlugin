package com.kelpwing.kelpylandiaplugin.commands.custom;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.commands.custom.ks.KsException;
import com.kelpwing.kelpylandiaplugin.commands.custom.ks.KsInterpreter;
import com.kelpwing.kelpylandiaplugin.commands.custom.ks.KsValue;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Bukkit {@link CommandExecutor} for a single custom command entry.
 *
 * When invoked:
 * <ol>
 *   <li>Permission is checked; if failed, the configured no-permission message is sent.</li>
 *   <li>A fresh {@link KsInterpreter} is created with Minecraft built-ins injected.</li>
 *   <li>The script (inline or file) is executed.</li>
 *   <li>
 *     {@code print} statements send coloured messages to the player immediately.<br>
 *     A top-level {@code return "message"} sends the coloured message and ends the script.
 *   </li>
 *   <li>Script runtime errors are caught and shown to the player in red.</li>
 * </ol>
 *
 * <h3>Minecraft built-ins available to scripts</h3>
 * <pre>
 * argument(n)             – nth argument (1-indexed), "" if missing
 * argument_count()        – total number of arguments passed
 * run(cmd)                – dispatch command as the player
 * run(cmd, true)          – dispatch command as console
 * broadcast(msg)          – broadcast colour-translated message to all players
 * player_name()           – player username
 * player_display()        – player display name (with nick/colour)
 * player_world()          – world name
 * player_health()         – current health as number
 * player_max_health()     – max health as number
 * player_level()          – XP level as number
 * player_gamemode()       – gamemode name string
 * has_permission(node)    – true/false
 * give(material, amount)              – give item to player
 * give(material, amount, nbt_json)    – give item with NBT (empty string = no NBT)
 * colorize(text)          – translate & colour codes  (also available from stdlib)
 * strip_colors(text)      – strip § colour sequences  (also available from stdlib)
 * </pre>
 */
public final class CustomCommandExecutor implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;
    private final CustomCommandEntry entry;

    public CustomCommandExecutor(KelpylandiaPlugin plugin, CustomCommandEntry entry) {
        this.plugin = plugin;
        this.entry  = entry;
    }

    //  CommandExecutor

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        // Permission check
        if (!entry.permission.isEmpty() && !player.hasPermission(entry.permission)) {
            String msg = entry.noPermissionMessage.isEmpty()
                ? ChatColor.RED + "You don't have permission to use this command."
                : ChatColor.translateAlternateColorCodes('&', entry.noPermissionMessage);
            player.sendMessage(msg);
            return true;
        }

        // Resolve the script source
        String source;
        try {
            source = resolveSource();
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Script file not found: " + entry.file);
            plugin.getLogger().warning("[CustomCommands] Cannot read script file '" + entry.file + "': " + e.getMessage());
            return true;
        }

        // Build the interpreter
        KsInterpreter ks = new KsInterpreter(msg -> player.sendMessage(
                ChatColor.translateAlternateColorCodes('&', msg)));

        // Inject Minecraft built-ins
        injectMinecraftBuiltins(ks, player, args);

        // Execute
        try {
            ks.exec(source);
        } catch (KsException.Return ret) {
            // top-level return → send message to player
            String msg = ChatColor.translateAlternateColorCodes('&', ret.value.toString());
            player.sendMessage(msg);
        } catch (KsException.Runtime | KsException.Throw e) {
            player.sendMessage(ChatColor.RED + "Script error: " + e.getMessage());
            if (plugin.getConfig().getBoolean("custom-commands.log-errors", true)) {
                plugin.getLogger().warning("[CustomCommands] Error in /" + entry.name + ": " + e.getMessage());
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Unexpected error running /" + entry.name + ".");
            plugin.getLogger().warning("[CustomCommands] Unexpected error in /" + entry.name + ": " + e.getMessage());
        }
        return true;
    }

        //  TabCompleter (returns empty list by default)
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }

        //  Script source resolution
    
    private String resolveSource() throws IOException {
        if (entry.script != null) return entry.script;
        // External file path relative to plugin data folder
        File f = new File(plugin.getDataFolder(), entry.file);
        return new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
    }

        //  Minecraft built-in injection
    
    @SuppressWarnings("deprecation")
    private void injectMinecraftBuiltins(KsInterpreter ks, Player player, String[] args) {

        // argument(n): 1-indexed, returns "" if beyond range
        ks.registerNative("argument", a -> {
            if (a.isEmpty()) throw new KsException.Runtime("argument() requires 1 argument");
            int n = (int) a.get(0).asNum();
            if (n < 1 || n > args.length) return KsValue.ofStr("");
            return KsValue.ofStr(args[n - 1]);
        });

        // arguments(): full list of all arguments
        ks.registerNative("arguments", a -> {
            List<KsValue> list = new ArrayList<>();
            for (String s : args) list.add(KsValue.ofStr(s));
            return KsValue.ofList(list);
        });

        // argument_count()
        ks.registerNative("argument_count", a -> KsValue.ofNum(args.length));

        // run(cmd, [asSudo=false])
        ks.registerNative("run", a -> {
            if (a.isEmpty()) throw new KsException.Runtime("run() requires at least 1 argument");
            String cmd   = a.get(0).toString();
            boolean sudo = a.size() > 1 && a.get(1).isTruthy();
            if (sudo) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } else {
                player.performCommand(cmd);
            }
            return KsValue.NULL;
        });

        // broadcast(msg)
        ks.registerNative("broadcast", a -> {
            String msg = a.isEmpty() ? "" : ChatColor.translateAlternateColorCodes('&', a.get(0).toString());
            Bukkit.broadcastMessage(msg);
            return KsValue.NULL;
        });

        // has_permission(node)
        ks.registerNative("has_permission", a -> {
            if (a.isEmpty()) throw new KsException.Runtime("has_permission() requires 1 argument");
            return KsValue.ofBool(player.hasPermission(a.get(0).toString()));
        });

        //  Player info 
        ks.registerNative("player_name",       a -> KsValue.ofStr(player.getName()));
        ks.registerNative("player_display",    a -> KsValue.ofStr(player.getDisplayName()));
        ks.registerNative("player_world",      a -> KsValue.ofStr(player.getWorld().getName()));
        ks.registerNative("player_health",     a -> KsValue.ofNum(player.getHealth()));
        ks.registerNative("player_max_health", a -> KsValue.ofNum(player.getMaxHealth()));
        ks.registerNative("player_level",      a -> KsValue.ofNum(player.getLevel()));
        ks.registerNative("player_gamemode",   a -> KsValue.ofStr(player.getGameMode().name().toLowerCase()));
        ks.registerNative("player_uuid",       a -> KsValue.ofStr(player.getUniqueId().toString()));
        ks.registerNative("player_x",          a -> KsValue.ofNum(player.getLocation().getX()));
        ks.registerNative("player_y",          a -> KsValue.ofNum(player.getLocation().getY()));
        ks.registerNative("player_z",          a -> KsValue.ofNum(player.getLocation().getZ()));

        //  Item giving 
        // give(material_name, amount [, nbt_json])
        ks.registerNative("give", a -> {
            if (a.size() < 2) throw new KsException.Runtime("give() requires at least 2 arguments: give(material, amount)");
            String matName = a.get(0).toString().toUpperCase();
            int amount = (int) a.get(1).asNum();
            Material mat = Material.matchMaterial(matName);
            if (mat == null) throw new KsException.Runtime("give(): unknown material '" + matName + "'");
            ItemStack stack = new ItemStack(mat, Math.max(1, amount));
            try {
                player.getInventory().addItem(stack);
                return KsValue.TRUE;
            } catch (Exception e) {
                return KsValue.FALSE;
            }
        });
    }
}
