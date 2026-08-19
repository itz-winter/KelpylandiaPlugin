package com.kelpwing.kelpylandiaplugin.economy.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /eco <add|subtract|set|reset|check> <player> [amount]
 *
 * <p>Admin command for managing player balances directly.
 * Default permission: op only (qol.economy.eco).
 *
 * <pre>
 *   /eco add     Steve 500       – adds $500 to Steve's balance
 *   /eco subtract Steve 100      – removes $100 from Steve's balance (floors at 0)
 *   /eco set     Steve 1000      – sets Steve's balance to exactly $1000
 *   /eco reset   Steve           – resets Steve's balance to the starting balance
 *   /eco check   Steve           – shows Steve's current balance
 * </pre>
 */
public class EcoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("add", "subtract", "set", "reset", "check");
    private static final String PERM = "qol.economy.eco";

    private final KelpylandiaPlugin plugin;

    public EcoCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null || !eco.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "The economy system is disabled.");
            return true;
        }

        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(eco.getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (!SUBCOMMANDS.contains(sub)) {
            sendUsage(sender, label);
            return true;
        }

        // Resolve target player 
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !eco.hasAccount(target.getUniqueId())
                && Bukkit.getPlayer(args[1]) == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }
        eco.createAccount(target.getUniqueId());
        String targetName = target.getName() != null ? target.getName() : args[1];

        switch (sub) {
            case "check": {
                BigDecimal bal = eco.getBalance(target.getUniqueId());
                sender.sendMessage(ChatColor.GOLD + targetName + ChatColor.YELLOW + "'s balance: "
                        + ChatColor.GREEN + eco.formatMoney(bal));
                break;
            }

            case "reset": {
                BigDecimal old = eco.getBalance(target.getUniqueId());
                eco.setBalance(target.getUniqueId(), eco.getStartingBalance());
                sender.sendMessage(ChatColor.GOLD + "Reset " + targetName + "'s balance from "
                        + ChatColor.RED + eco.formatMoney(old)
                        + ChatColor.GOLD + " to "
                        + ChatColor.GREEN + eco.formatMoney(eco.getStartingBalance()) + ChatColor.GOLD + ".");
                notifyTarget(target, ChatColor.GOLD + "Your balance has been reset to "
                        + ChatColor.GREEN + eco.formatMoney(eco.getStartingBalance()) + ChatColor.GOLD + ".");
                break;
            }

            case "add":
            case "subtract":
            case "set": {
                if (args.length < 3) {
                    sendUsage(sender, label);
                    return true;
                }
                BigDecimal amount;
                try {
                    amount = new BigDecimal(args[2]).setScale(eco.getDecimals(), RoundingMode.HALF_UP);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
                    return true;
                }
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    sender.sendMessage(ChatColor.RED + "Amount must be non-negative.");
                    return true;
                }

                BigDecimal old = eco.getBalance(target.getUniqueId());

                if (sub.equals("add")) {
                    eco.deposit(target.getUniqueId(), amount);
                    BigDecimal newBal = eco.getBalance(target.getUniqueId());
                    sender.sendMessage(ChatColor.GOLD + "Added " + ChatColor.GREEN + eco.formatMoney(amount)
                            + ChatColor.GOLD + " to " + targetName + "'s balance. ("
                            + ChatColor.GRAY + eco.formatMoney(old) + ChatColor.GOLD + " → "
                            + ChatColor.GREEN + eco.formatMoney(newBal) + ChatColor.GOLD + ")");
                    notifyTarget(target, ChatColor.GREEN + "+" + eco.formatMoney(amount)
                            + ChatColor.GOLD + " has been added to your balance by an admin.");

                } else if (sub.equals("subtract")) {
                    // Floor at zero
                    BigDecimal toRemove = amount.min(old);
                    eco.setBalance(target.getUniqueId(), old.subtract(toRemove));
                    BigDecimal newBal = eco.getBalance(target.getUniqueId());
                    sender.sendMessage(ChatColor.GOLD + "Subtracted " + ChatColor.RED + eco.formatMoney(toRemove)
                            + ChatColor.GOLD + " from " + targetName + "'s balance. ("
                            + ChatColor.GRAY + eco.formatMoney(old) + ChatColor.GOLD + " → "
                            + ChatColor.RED + eco.formatMoney(newBal) + ChatColor.GOLD + ")");
                    notifyTarget(target, ChatColor.RED + "-" + eco.formatMoney(toRemove)
                            + ChatColor.GOLD + " has been deducted from your balance by an admin.");

                } else { // set
                    eco.setBalance(target.getUniqueId(), amount);
                    sender.sendMessage(ChatColor.GOLD + "Set " + targetName + "'s balance to "
                            + ChatColor.GREEN + eco.formatMoney(amount)
                            + ChatColor.GOLD + ". (was " + ChatColor.GRAY + eco.formatMoney(old) + ChatColor.GOLD + ")");
                    notifyTarget(target, ChatColor.GOLD + "Your balance has been set to "
                            + ChatColor.GREEN + eco.formatMoney(amount) + ChatColor.GOLD + " by an admin.");
                }
                break;
            }

            default:
                sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.RED + "Usage:");
        sender.sendMessage(ChatColor.RED + "  /" + label + " add <player> <amount>");
        sender.sendMessage(ChatColor.RED + "  /" + label + " subtract <player> <amount>");
        sender.sendMessage(ChatColor.RED + "  /" + label + " set <player> <amount>");
        sender.sendMessage(ChatColor.RED + "  /" + label + " reset <player>");
        sender.sendMessage(ChatColor.RED + "  /" + label + " check <player>");
    }

    /** Sends a message to the target only if they are currently online. */
    private void notifyTarget(OfflinePlayer target, String message) {
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.sendMessage(message);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM)) return Collections.emptyList();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        // arg 3: amount hint for add/subtract/set
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("subtract") || sub.equals("set")) {
                return Arrays.asList("100", "500", "1000");
            }
        }
        return Collections.emptyList();
    }
}
