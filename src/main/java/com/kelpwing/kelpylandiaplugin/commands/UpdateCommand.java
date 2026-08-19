package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.utils.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /kpupdate - check for and download plugin updates.
 *
 * Sub-commands:
 *   /kpupdate check    - show current vs. latest version
 *   /kpupdate download - download the latest jar to the plugins folder
 *
 * Permission: kelpylandia.update (default: op)
 */
public class UpdateCommand implements CommandExecutor, TabCompleter {

    private static final String MODRINTH_PAGE = "https://modrinth.com/plugin/kelpyylandia-plugin";
    private static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.AQUA + "Update" + ChatColor.GRAY + "] " + ChatColor.RESET;

    private final KelpylandiaPlugin plugin;

    public UpdateCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("qol.update")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "check";

        switch (sub) {
            case "check":
                runCheck(sender);
                break;
            case "download":
                runDownload(sender);
                break;
            default:
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /kpupdate <check|download>");
        }
        return true;
    }

    // /kpupdate check 

    private void runCheck(CommandSender sender) {
        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Update checker is disabled.");
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.GRAY + "Checking for updates...");

        checker.checkAsync(() -> {
            String current = checker.getCurrentVersion();
            if (checker.isUpdateAvailable()) {
                String latest = checker.getLatestVersion();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Update available!");
                sender.sendMessage(PREFIX + ChatColor.GRAY + "Current: " + ChatColor.RED + "v" + current);
                sender.sendMessage(PREFIX + ChatColor.GRAY + "Latest:  " + ChatColor.GREEN + "v" + latest
                        + ChatColor.GRAY + " (" + checker.getVersionName() + ")");
                sender.sendMessage(PREFIX + ChatColor.GRAY + "Modrinth: " + ChatColor.AQUA + MODRINTH_PAGE);
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Run " + ChatColor.WHITE + "/kpupdate download"
                        + ChatColor.YELLOW + " to download the latest jar to your plugins folder.");
            } else {
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Plugin is up to date! "
                        + ChatColor.GRAY + "(v" + current + ")");
            }
        });
    }

    // /kpupdate download 

    private void runDownload(CommandSender sender) {
        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Update checker is disabled.");
            return;
        }

        if (!checker.isUpdateAvailable()) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Plugin is already up to date (v"
                    + checker.getCurrentVersion() + "). Nothing to download.");
            return;
        }

        String url = checker.getDownloadUrl();
        if (url == null || url.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No download URL available. Visit: "
                    + ChatColor.AQUA + MODRINTH_PAGE);
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.GRAY + "Downloading v" + checker.getLatestVersion()
                + " from Modrinth...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File pluginsDir = plugin.getDataFolder().getParentFile();
                // Save as QoLPlugin-<version>.jar (will be picked up on next restart)
                String fileName = "QoLPlugin-" + checker.getLatestVersion() + ".jar";
                File dest = new File(pluginsDir, fileName);

                downloadFile(url, dest);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Download complete!");
                    sender.sendMessage(PREFIX + ChatColor.GRAY + "Saved to: "
                            + ChatColor.WHITE + dest.getName());
                    sender.sendMessage(PREFIX + ChatColor.YELLOW
                            + "Restart the server to apply the update. "
                            + ChatColor.GRAY + "(Replace the old jar first if needed.)");
                });

            } catch (Exception e) {
                plugin.getLogger().warning("[UpdateChecker] Download failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(PREFIX + ChatColor.RED + "Download failed: " + e.getMessage()
                                + ". Try manually: " + ChatColor.AQUA + MODRINTH_PAGE));
            }
        });
    }

    private void downloadFile(String urlStr, File dest) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("User-Agent", "KelpylandiaPlugin/UpdateChecker");
        con.setConnectTimeout(10_000);
        con.setReadTimeout(60_000);

        try (InputStream in = con.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
        }
    }

    // Tab completion 

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("qol.update")) return Collections.emptyList();
        if (args.length == 1) {
            return Arrays.asList("check", "download");
        }
        return Collections.emptyList();
    }
}
