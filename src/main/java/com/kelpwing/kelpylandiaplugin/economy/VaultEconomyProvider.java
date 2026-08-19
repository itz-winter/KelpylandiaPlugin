package com.kelpwing.kelpylandiaplugin.economy;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Vault {@link Economy} provider backed by our {@link EconomyManager}.
 * Register / unregister via {@link #register()} and {@link #unregister()}.
 */
public class VaultEconomyProvider implements Economy {

    private final KelpylandiaPlugin plugin;
    private final EconomyManager economy;

    public VaultEconomyProvider(KelpylandiaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void register() {
        Bukkit.getServicesManager().register(Economy.class, this, plugin,
                org.bukkit.plugin.ServicePriority.Normal);
        plugin.getLogger().info("[Economy] Registered Vault economy provider.");
    }

    public void unregister() {
        Bukkit.getServicesManager().unregister(Economy.class, this);
    }

    // Meta 

    @Override public boolean isEnabled() { return economy.isEnabled(); }
    @Override public String getName() { return "QoLPlugin Economy"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return economy.getDecimals(); }
    @Override public String format(double amount) { return economy.formatMoney(amount); }
    @Override public String currencyNamePlural() { return economy.getCurrencyNamePlural(); }
    @Override public String currencyNameSingular() { return economy.getCurrencyNameSingular(); }

    // Account operations 

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return economy.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
        return economy.hasAccount(op.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        economy.createAccount(player.getUniqueId());
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
        economy.createAccount(op.getUniqueId());
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    // Balance 

    @Override
    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player.getUniqueId()).doubleValue();
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.hasBalance(player.getUniqueId(), BigDecimal.valueOf(amount));
    }

    @Override
    public boolean has(String playerName, double amount) {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    // Withdraw / Deposit 

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player),
                    EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amounts.");
        }
        if (!has(player, amount)) {
            return new EconomyResponse(0, getBalance(player),
                    EconomyResponse.ResponseType.FAILURE, "Insufficient funds.");
        }
        economy.withdraw(player.getUniqueId(), BigDecimal.valueOf(amount));
        return new EconomyResponse(amount, getBalance(player),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player),
                    EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amounts.");
        }
        economy.deposit(player.getUniqueId(), BigDecimal.valueOf(amount));
        return new EconomyResponse(amount, getBalance(player),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    // Bank (not supported) 

    private static final EconomyResponse NO_BANK = new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");

    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return NO_BANK; }
    @Override public EconomyResponse createBank(String name, String player) { return NO_BANK; }
    @Override public EconomyResponse deleteBank(String name) { return NO_BANK; }
    @Override public EconomyResponse bankBalance(String name) { return NO_BANK; }
    @Override public EconomyResponse bankHas(String name, double amount) { return NO_BANK; }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return NO_BANK; }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return NO_BANK; }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return NO_BANK; }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return NO_BANK; }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return NO_BANK; }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return NO_BANK; }
    @Override public List<String> getBanks() { return Collections.emptyList(); }
}
