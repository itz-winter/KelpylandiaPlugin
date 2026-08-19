package com.kelpwing.kelpylandiaplugin;

import com.kelpwing.kelpylandiaplugin.chat.ChannelManager;
import com.kelpwing.kelpylandiaplugin.chat.ItemDisplayManager;
import com.kelpwing.kelpylandiaplugin.chat.commands.ChatCommand;
import com.kelpwing.kelpylandiaplugin.chat.commands.ChannelCommand;
import com.kelpwing.kelpylandiaplugin.chat.commands.ChannelAliasCommand;
import com.kelpwing.kelpylandiaplugin.chat.listeners.ChatListener;
import com.kelpwing.kelpylandiaplugin.chat.listeners.SnapshotListener;
import com.kelpwing.kelpylandiaplugin.chat.commands.ViewSnapshotCommand;
import com.kelpwing.kelpylandiaplugin.commands.GamemodeCommand;
import com.kelpwing.kelpylandiaplugin.commands.NickCommand;
import com.kelpwing.kelpylandiaplugin.commands.NoclipCommand;
import com.kelpwing.kelpylandiaplugin.commands.PvpCommand;
import com.kelpwing.kelpylandiaplugin.commands.ReportCommand;
import com.kelpwing.kelpylandiaplugin.commands.RecipeCommand;
import com.kelpwing.kelpylandiaplugin.commands.RtpCommand;
import com.kelpwing.kelpylandiaplugin.commands.RulesCommand;
import com.kelpwing.kelpylandiaplugin.commands.SeenCommand;
import com.kelpwing.kelpylandiaplugin.commands.SmiteCommand;
import com.kelpwing.kelpylandiaplugin.commands.SpawnCommand;
import com.kelpwing.kelpylandiaplugin.commands.StuckCommand;
import com.kelpwing.kelpylandiaplugin.commands.WorkbenchCommand;
import com.kelpwing.kelpylandiaplugin.commands.EnchantCommand;
import com.kelpwing.kelpylandiaplugin.commands.AfkCommand;
import com.kelpwing.kelpylandiaplugin.commands.MsgCommand;
import com.kelpwing.kelpylandiaplugin.commands.ReplyCommand;
import com.kelpwing.kelpylandiaplugin.commands.WhisperToggleCommand;
import com.kelpwing.kelpylandiaplugin.commands.SuicideCommand;
import com.kelpwing.kelpylandiaplugin.commands.SkullCommand;
import com.kelpwing.kelpylandiaplugin.commands.RepairCommand;
import com.kelpwing.kelpylandiaplugin.commands.SudoCommand;
import com.kelpwing.kelpylandiaplugin.commands.RunAtCommand;
import com.kelpwing.kelpylandiaplugin.commands.SocialSpyCommand;
import com.kelpwing.kelpylandiaplugin.commands.CommandSpyCommand;
import com.kelpwing.kelpylandiaplugin.commands.FlyCommand;
import com.kelpwing.kelpylandiaplugin.commands.GodCommand;
import com.kelpwing.kelpylandiaplugin.commands.HealCommand;
import com.kelpwing.kelpylandiaplugin.commands.StarveCommand;
import com.kelpwing.kelpylandiaplugin.commands.FeedCommand;
import com.kelpwing.kelpylandiaplugin.commands.FlySpeedCommand;
import com.kelpwing.kelpylandiaplugin.commands.TrashCommand;
import com.kelpwing.kelpylandiaplugin.commands.LoreCommand;
import com.kelpwing.kelpylandiaplugin.commands.HatCommand;
import com.kelpwing.kelpylandiaplugin.commands.UngodCommand;
import com.kelpwing.kelpylandiaplugin.listeners.SpyListener;
import com.kelpwing.kelpylandiaplugin.listeners.NametagListener;
import com.kelpwing.kelpylandiaplugin.listeners.WorkbenchListener;
import com.kelpwing.kelpylandiaplugin.homes.HomeGUI;
import com.kelpwing.kelpylandiaplugin.homes.HomeManager;
import com.kelpwing.kelpylandiaplugin.homes.commands.DelHomeCommand;
import com.kelpwing.kelpylandiaplugin.homes.commands.HomeCommand;
import com.kelpwing.kelpylandiaplugin.homes.commands.HomesCommand;
import com.kelpwing.kelpylandiaplugin.homes.commands.RenameHomeCommand;
import com.kelpwing.kelpylandiaplugin.homes.commands.SetHomeCommand;
import com.kelpwing.kelpylandiaplugin.moderation.commands.*;
import com.kelpwing.kelpylandiaplugin.moderation.JailManager;
import com.kelpwing.kelpylandiaplugin.moderation.FreezeManager;
import com.kelpwing.kelpylandiaplugin.moderation.listeners.PlayerListener;
import com.kelpwing.kelpylandiaplugin.moderation.listeners.JailListener;
import com.kelpwing.kelpylandiaplugin.moderation.listeners.FreezeListener;
import com.kelpwing.kelpylandiaplugin.listeners.ConsoleListener;
import com.kelpwing.kelpylandiaplugin.listeners.NickListener;
import com.kelpwing.kelpylandiaplugin.listeners.AfkListener;
import com.kelpwing.kelpylandiaplugin.listeners.MsgListener;
import com.kelpwing.kelpylandiaplugin.integrations.DiscordIntegration;
import com.kelpwing.kelpylandiaplugin.integrations.LuckPermsIntegration;
import com.kelpwing.kelpylandiaplugin.integrations.PlaceholderAPIIntegration;
import com.kelpwing.kelpylandiaplugin.config.ConfigManager;
import com.kelpwing.kelpylandiaplugin.teleport.TeleportListener;
import com.kelpwing.kelpylandiaplugin.teleport.TpaManager;
import com.kelpwing.kelpylandiaplugin.teleport.BackManager;
import com.kelpwing.kelpylandiaplugin.teleport.BackListener;
import com.kelpwing.kelpylandiaplugin.teleport.commands.*;
import com.kelpwing.kelpylandiaplugin.commands.UpdateCommand;
import com.kelpwing.kelpylandiaplugin.listeners.UpdateNotifyListener;
import com.kelpwing.kelpylandiaplugin.utils.FileManager;
import com.kelpwing.kelpylandiaplugin.utils.NickManager;
import com.kelpwing.kelpylandiaplugin.utils.AfkManager;
import com.kelpwing.kelpylandiaplugin.utils.VanishManager;
import com.kelpwing.kelpylandiaplugin.utils.SpyManager;
import com.kelpwing.kelpylandiaplugin.utils.PlayerStateManager;
import com.kelpwing.kelpylandiaplugin.utils.DeathMessagesManager;
import com.kelpwing.kelpylandiaplugin.utils.BroadcastManager;
import com.kelpwing.kelpylandiaplugin.utils.UpdateChecker;
import com.kelpwing.kelpylandiaplugin.utils.VersionHelper;
import com.kelpwing.kelpylandiaplugin.warps.WarpManager;
import com.kelpwing.kelpylandiaplugin.warps.commands.WarpCommand;
import com.kelpwing.kelpylandiaplugin.warps.commands.WarpsCommand;
import com.kelpwing.kelpylandiaplugin.warps.commands.SetWarpCommand;
import com.kelpwing.kelpylandiaplugin.warps.commands.DelWarpCommand;
import com.kelpwing.kelpylandiaplugin.kits.KitCommand;
import com.kelpwing.kelpylandiaplugin.kits.KitGUI;
import com.kelpwing.kelpylandiaplugin.kits.KitManager;
import com.kelpwing.kelpylandiaplugin.economy.EconomyManager;
import com.kelpwing.kelpylandiaplugin.economy.VaultEconomyProvider;
import com.kelpwing.kelpylandiaplugin.economy.SellGUI;
import com.kelpwing.kelpylandiaplugin.economy.ShopEditGUI;
import com.kelpwing.kelpylandiaplugin.economy.ShopGUI;
import com.kelpwing.kelpylandiaplugin.economy.commands.*;
import com.kelpwing.kelpylandiaplugin.placeholders.KpauPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KelpylandiaPlugin extends JavaPlugin {
    
    private static KelpylandiaPlugin instance;
    
    // Chat-related components
    private ChannelManager channelManager;
    private LuckPermsIntegration luckPermsIntegration;
    private PlaceholderAPIIntegration placeholderAPIIntegration;
    private DiscordIntegration discordIntegration;
    private ItemDisplayManager itemDisplayManager;
    
    // Moderation-related components
    private ConfigManager configManager;
    private FileManager fileManager;
    private VanishManager vanishManager;
    private Map<UUID, Long> mutedPlayers;
    private PvpCommand pvpCommand;
    
    // Homes & Teleport components
    private HomeManager homeManager;
    private HomeGUI homeGUI;
    private TpaManager tpaManager;
    private BackManager backManager;
    private NickManager nickManager;
    private AfkManager afkManager;
    private MsgCommand msgCommand;
    private SpyManager spyManager;
    private GodCommand godCommand;
    private VanishCommand vanishCommand;
    private PlayerStateManager playerStateManager;
    private DeathMessagesManager deathMessagesManager;
    private BroadcastManager broadcastManager;
    private WarpManager warpManager;
    private JailManager jailManager;
    private FreezeManager freezeManager;
    private KitManager kitManager;
    private KitGUI kitGUI;
    private UpdateChecker updateChecker;
    
    // Economy components
    private EconomyManager economyManager;
    private VaultEconomyProvider vaultEconomyProvider;
    private SellGUI sellGUI;
    private ShopEditGUI shopEditGUI;
    private ShopGUI shopGUI;
    
    @Override
    public void onEnable() {
        instance = this;
        mutedPlayers = new HashMap<>();
        
        // Migrate old plugin folder: rename KelpylandiaPlugin → QoLPlugin
        migrateDataFolder();
        
        // Detect server version and platform
        VersionHelper.init(getLogger());
        
        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        
        // Initialize file manager
        fileManager = new FileManager(this);
        
        // Initialize vanish manager
        vanishManager = new VanishManager(this);
        
        // Initialize player state manager (state persistence)
        playerStateManager = new PlayerStateManager(this);
        
        // Initialize channel manager
        channelManager = new ChannelManager(this);
        
        // Initialize homes system
        if (getConfig().getBoolean("homes.enabled", true)) {
            homeManager = new HomeManager(this);
            homeGUI = new HomeGUI(this);
            getLogger().info("Homes system enabled!");
        }
        
        // Initialize teleport system
        if (getConfig().getBoolean("teleport.enabled", true)) {
            tpaManager = new TpaManager(this);
            getLogger().info("Teleport request system enabled!");
        }
        
        // Initialize back system
        if (getConfig().getBoolean("back.enabled", true)) {
            backManager = new BackManager(this);
            getLogger().info("Back system enabled!");
        }
        
        // Initialize integrations
        initializeIntegrations();
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);  // moderation.listeners (mute checks)
        getServer().getPluginManager().registerEvents(new com.kelpwing.kelpylandiaplugin.chat.listeners.PlayerListener(this), this);  // chat.listeners (channels + state persistence)
        getServer().getPluginManager().registerEvents(new com.kelpwing.kelpylandiaplugin.listeners.PlayerEventListener(this), this);
        getServer().getPluginManager().registerEvents(new com.kelpwing.kelpylandiaplugin.listeners.ConsoleEventListener(this), this);
        getServer().getPluginManager().registerEvents(new com.kelpwing.kelpylandiaplugin.listeners.AdvancementListener(this), this);
        
        // Register console listener for Discord logging
        ConsoleListener consoleListener = new ConsoleListener(this);
        getServer().getPluginManager().registerEvents(consoleListener, this);
        
        // Register chat commands
        getCommand("channel").setExecutor(new ChatCommand(this));
        getCommand("multichat").setExecutor(new ChannelCommand(this));
        
        // Register channel alias commands (e.g. /l for local, /g for global)
        for (Map.Entry<String, String> entry : channelManager.getChannelAliases().entrySet()) {
            String alias = entry.getKey();
            String channelName = entry.getValue();
            registerCommand(alias, new ChannelAliasCommand(this, channelName), 
                "Switch to the " + channelName + " channel.", "/" + alias, 
                "qol.channel.use");
            getLogger().info("Registered channel alias: /" + alias + " -> " + channelName);
        }
        
        // Register moderation commands
        vanishCommand = new VanishCommand(this);
        InvseeCommand invseeCommand = new InvseeCommand(this);
        MuteCommand muteCommand = new MuteCommand(this);
        BanCommand banCommand = new BanCommand(this);
        pvpCommand = new PvpCommand(this);
        
        getCommand("mute").setExecutor(muteCommand);
        getCommand("mute").setTabCompleter(muteCommand);
        getCommand("warn").setExecutor(new WarnCommand(this));
        getCommand("kick").setExecutor(new KickCommand(this));
        getCommand("ban").setExecutor(banCommand);
        getCommand("ban").setTabCompleter(banCommand);
        getCommand("ipban").setExecutor(new IPBanCommand(this));
        getCommand("warns").setExecutor(new WarnsCommand(this));
        getCommand("hist").setExecutor(new HistoryCommand(this));
        getCommand("unban").setExecutor(new UnbanCommand(this));
        getCommand("unwarn").setExecutor(new UnwarnCommand(this));
        getCommand("unmute").setExecutor(new UnmuteCommand(this));
        getCommand("vanish").setExecutor(vanishCommand);
        getCommand("invsee").setExecutor(invseeCommand);
        getCommand("invsee").setTabCompleter(invseeCommand);
        getCommand("pvp").setExecutor(pvpCommand);
        getCommand("kelpylandiaReload").setExecutor(new ReloadCommand(this));
        
        // Register vanish listener
        getServer().getPluginManager().registerEvents(new com.kelpwing.kelpylandiaplugin.listeners.VanishListener(this, vanishCommand), this);
        
        // Register enchantment override listener (allows custom enchant combos on anvil)
        if (getConfig().getBoolean("enchant-overrides.enabled", true)) {
            getServer().getPluginManager().registerEvents(new com.kelpwing.kelpylandiaplugin.listeners.EnchantOverrideListener(this), this);
            getLogger().info("Enchantment override system enabled!");
        }
        
        // Register silk-touch drop listener (spawners, budding amethyst, reinforced deepslate, suspicious blocks)
        getServer().getPluginManager().registerEvents(new com.kelpwing.kelpylandiaplugin.listeners.SilkListener(this), this);
        
        // Register invsee listener
        getServer().getPluginManager().registerEvents(new com.kelpwing.kelpylandiaplugin.listeners.InvseeListener(this, invseeCommand), this);
        
        // Register PvP command as listener (for damage events)
        getServer().getPluginManager().registerEvents(pvpCommand, this);
        
        // Register home commands
        if (homeManager != null) {
            SetHomeCommand setHomeCmd = new SetHomeCommand(this);
            DelHomeCommand delHomeCmd = new DelHomeCommand(this);
            HomeCommand homeCmd = new HomeCommand(this);
            HomesCommand homesCmd = new HomesCommand(this);
            RenameHomeCommand renameHomeCmd = new RenameHomeCommand(this);

            registerCommand("sethome", setHomeCmd, "Set a home at your current location.", "/sethome [name]", "qol.homes");
            registerCommand("delhome", delHomeCmd, "Delete a home.", "/delhome <name>", "qol.homes", "removehome", "remhome");
            registerCommand("home", homeCmd, "Teleport to a home. List your homes with /homes. Create a new home with /sethome.", "/home [name]", "qol.homes");
            registerCommand("homes", homesCmd, "List or browse your homes. Set a new home with /sethome.", "/homes", "qol.homes");
            registerCommand("renhome", renameHomeCmd, "Rename a home.", "/renhome <currentName> <newName>", "qol.homes.rename", "renamehome");
            
            // Register HomeGUI as listener for inventory clicks
            getServer().getPluginManager().registerEvents(homeGUI, this);
        }
        
        // Register TPA commands
        if (tpaManager != null) {
            TpaCommand tpaCmd = new TpaCommand(this);
            TpaHereCommand tpaHereCmd = new TpaHereCommand(this);
            TpAcceptCommand tpAcceptCmd = new TpAcceptCommand(this);
            TpDenyCommand tpDenyCmd = new TpDenyCommand(this);
            TpCancelCommand tpCancelCmd = new TpCancelCommand(this);
            
            registerCommand("tpa", tpaCmd, "Send a teleport request to a player. To teleport a player to you, use /tpahere.", "/tpa <player>", "qol.tpa", "tpask");
            registerCommand("tpahere", tpaHereCmd, "Request a player to teleport to you.", "/tpahere <player>", "qol.tpa.here");
            registerCommand("tpaccept", tpAcceptCmd, "Accept a pending teleport request.", "/tpaccept [player]", "qol.tpa", "tpyes");
            registerCommand("tpdeny", tpDenyCmd, "Deny a pending teleport request.", "/tpdeny [player]", "qol.tpa", "tpno");
            registerCommand("tpcancel", tpCancelCmd, "Cancel your outgoing teleport request.", "/tpcancel", "qol.tpa");
            
            // Register teleport listener for invulnerability + quit cleanup
            getServer().getPluginManager().registerEvents(new TeleportListener(this), this);
        }
        
        // Register back commands
        if (backManager != null) {
            BackCommand backCmd = new BackCommand(this);
            DeathBackCommand dbackCmd = new DeathBackCommand(this);
            
            registerCommand("back", backCmd, "Teleport to your previous location. Use /dback to return to your last death location.", "/back", "qol.back", "return");
            registerCommand("dback", dbackCmd, "Teleport to your last death location. Use /back to return to your previous (non-death) location.", "/dback", "qol.dback", "deathback", "dreturn");
            
            // Register back listener for teleport/death tracking
            getServer().getPluginManager().registerEvents(new BackListener(this), this);
        }
        
        // Register spawn command
        if (getConfig().getBoolean("spawn.enabled", true)) {
            SpawnCommand spawnCmd = new SpawnCommand(this);
            registerCommand("spawn", spawnCmd, "Teleport to the world spawn point.", "/spawn [player]", "qol.spawn");
            getLogger().info("Spawn command enabled!");
        }
        
        // Register gamemode commands
        if (getConfig().getBoolean("gamemode.enabled", true)) {
            GamemodeCommand gmCmd = new GamemodeCommand(this);
            registerCommand("gm", gmCmd, "Change your gamemode.", "/gm <mode> [player]", "qol.gamemode.*", "gamemode");
            registerCommand("gmc", gmCmd, "Switch to creative mode.", "/gmc [player]", "qol.gamemode.creative");
            registerCommand("gms", gmCmd, "Switch to survival mode.", "/gms [player]", "qol.gamemode.survival");
            registerCommand("gma", gmCmd, "Switch to adventure mode.", "/gma [player]", "qol.gamemode.adventure");
            registerCommand("gmsp", gmCmd, "Switch to spectator mode.", "/gmsp [player]", "qol.gamemode.spectator");
            getLogger().info("Gamemode commands enabled!");
        }
        
        // Register workbench commands
        if (getConfig().getBoolean("workbenches.enabled", true)) {
            WorkbenchCommand wbCmd = new WorkbenchCommand(this);
            WorkbenchListener wbListener = new WorkbenchListener(this);
            wbCmd.setWorkbenchListener(wbListener);
            getServer().getPluginManager().registerEvents(wbListener, this);
            registerCommand("workbench", wbCmd, "Open a virtual crafting table.", "/workbench [player]", "qol.workbench.craft", "wb", "craft");
            registerCommand("enderchest", wbCmd, "Open your ender chest (or another player's).", "/enderchest [player]", "qol.workbench.enderchest", "ec", "echest");
            registerCommand("anvil", wbCmd, "Open a virtual anvil.", "/anvil [player]", "qol.workbench.anvil");
            registerCommand("grindstone", wbCmd, "Open a virtual grindstone.", "/grindstone [player]", "qol.workbench.grindstone", "gstone");
            registerCommand("stonecutter", wbCmd, "Open a virtual stonecutter.", "/stonecutter [player]", "qol.workbench.stonecutter", "scutter");
            registerCommand("smithingtable", wbCmd, "Open a virtual smithing table.", "/smithingtable [player]", "qol.workbench.smithing", "smithing");
            registerCommand("cartographytable", wbCmd, "Open a virtual cartography table.", "/cartographytable [player]", "qol.workbench.cartography", "cartography");
            registerCommand("loom", wbCmd, "Open a virtual loom.", "/loom [player]", "qol.workbench.loom");
            getLogger().info("Workbench commands enabled!");
        }

        // Register nametag prefix listener
        getServer().getPluginManager().registerEvents(new NametagListener(this), this);
        
        // Register nickname command
        if (getConfig().getBoolean("nickname.enabled", true)) {
            nickManager = new NickManager(this);
            NickCommand nickCmd = new NickCommand(this);
            registerCommand("nick", nickCmd, "Set or reset your nickname.", "/nick [nickname] OR /nick <player> [nickname]", "qol.nickname", "nickname");
            getServer().getPluginManager().registerEvents(new NickListener(this), this);
            getLogger().info("Nickname command enabled!");
        }
        
        // Register enchant command
        if (getConfig().getBoolean("enchant.enabled", true)) {
            EnchantCommand enchantCmd = new EnchantCommand(this);
            registerCommand("enchant", enchantCmd, "Enchant the item in your hand.", "/enchant <enchantment> [level] [player]", "qol.enchant");
            getLogger().info("Enchant command enabled!");
        }
        
        // Register AFK system
        if (getConfig().getBoolean("afk.enabled", true)) {
            afkManager = new AfkManager(this);
            AfkCommand afkCmd = new AfkCommand(this);
            registerCommand("afk", afkCmd, "Toggle your AFK status.", "/afk", "qol.afk");
            getServer().getPluginManager().registerEvents(new AfkListener(this), this);
            getLogger().info("AFK system enabled!");
        }
        
        // Register messaging commands (/w, /r, /wt)
        if (getConfig().getBoolean("messaging.enabled", true)) {
            msgCommand = new MsgCommand(this);
            ReplyCommand replyCmd = new ReplyCommand(this);
            WhisperToggleCommand wtCmd = new WhisperToggleCommand(this);
            
            registerCommand("w", msgCommand, "Send a private message.", "/w <player> <message>", "qol.msg", "msg", "tell", "whisper");
            registerCommand("r", replyCmd, "Reply to the last private message.", "/r <message>", "qol.msg.reply", "reply");
            registerCommand("wt", wtCmd, "Set or clear your whisper target.", "/wt [player]", "qol.msg.whispertoggle", "whispertoggle");
            getServer().getPluginManager().registerEvents(new MsgListener(this), this);
            getLogger().info("Messaging commands enabled!");
        }
        
        // Register suicide command
        if (getConfig().getBoolean("suicide.enabled", true)) {
            deathMessagesManager = new DeathMessagesManager(this);
            getServer().getPluginManager().registerEvents(deathMessagesManager, this);
            SuicideCommand suicideCmd = new SuicideCommand(this);
            registerCommand("suicide", suicideCmd, "Kill yourself.", "/suicide", "qol.suicide");
            getLogger().info("Suicide command enabled!");
        }
        
        // Initialize auto-broadcast system
        if (getConfig().getBoolean("auto-broadcasts.enabled", true)) {
            broadcastManager = new BroadcastManager(this);
            getLogger().info("Auto-broadcast system enabled!");
        }
        
        // Register skull command
        if (getConfig().getBoolean("skull.enabled", true)) {
            SkullCommand skullCmd = new SkullCommand(this);
            registerCommand("skull", skullCmd, "Get a player's head.", "/skull [player]", "qol.skull", "head", "playerhead");
            getLogger().info("Skull command enabled!");
        }
        
        // Register repair command
        if (getConfig().getBoolean("repair.enabled", true)) {
            RepairCommand repairCmd = new RepairCommand(this);
            registerCommand("repair", repairCmd, "Repair items.", "/repair [hand|helmet|chestplate|pants|boots|offhand|all] [player]", "qol.repair", "fix");
            getLogger().info("Repair command enabled!");
        }
        
        // Register sudo command
        if (getConfig().getBoolean("sudo.enabled", true)) {
            SudoCommand sudoCmd = new SudoCommand(this);
            registerCommand("sudo", sudoCmd, "Force a player to run a command or send a message.", "/sudo <player> <command or message>", "qol.sudo");
            getLogger().info("Sudo command enabled!");
        }
        
        // Register runat command
        if (getConfig().getBoolean("runat.enabled", true)) {
            RunAtCommand runatCmd = new RunAtCommand(this);
            registerCommand("runat", runatCmd, "Run a command as another player with permission bypass.", "/runat <player|Console> <command>", null, "forcecmd");
            getLogger().info("RunAt command enabled!");
        }
        
        // Register spy commands (/ss, /cs) and listener
        if (getConfig().getBoolean("spy.enabled", true)) {
            spyManager = new SpyManager(this);
            SocialSpyCommand ssCmd = new SocialSpyCommand(this);
            CommandSpyCommand csCmd = new CommandSpyCommand(this);
            registerCommand("ss", ssCmd, "Toggle social spy.", "/ss", "qol.socialspy", "socialspy");
            registerCommand("cs", csCmd, "Toggle command spy.", "/cs", "qol.commandspy", "commandspy");
            getServer().getPluginManager().registerEvents(new SpyListener(this), this);
            getLogger().info("Spy commands enabled!");
        }
        
        // Register fly command
        if (getConfig().getBoolean("fly.enabled", true)) {
            FlyCommand flyCmd = new FlyCommand(this);
            registerCommand("fly", flyCmd, "Toggle flight mode.", "/fly [player]", "qol.fly", "flight");
            getLogger().info("Fly command enabled!");
        }
        
        // Register god command
        if (getConfig().getBoolean("god.enabled", true)) {
            godCommand = new GodCommand(this);
            registerCommand("god", godCommand, "Toggle god mode (invincibility).", "/god [player]", "qol.god", "godmode");
            UngodCommand ungodCmd = new UngodCommand(this);
            registerCommand("ungod", ungodCmd, "Remove god mode (invincibility).", "/ungod [player]", "qol.god");
            getLogger().info("God command enabled!");
        }
        
        // Register heal command
        if (getConfig().getBoolean("heal.enabled", true)) {
            HealCommand healCmd = new HealCommand(this);
            registerCommand("heal", healCmd, "Heal a player to full health.", "/heal [player]", "qol.heal");
            getLogger().info("Heal command enabled!");
        }
        
        // Register starve command
        if (getConfig().getBoolean("starve.enabled", true)) {
            StarveCommand starveCmd = new StarveCommand(this);
            registerCommand("starve", starveCmd, "Set a player's food to zero.", "/starve [player]", "qol.starve");
            getLogger().info("Starve command enabled!");
        }
        
        // Register feed command
        if (getConfig().getBoolean("feed.enabled", true)) {
            FeedCommand feedCmd = new FeedCommand(this);
            registerCommand("feed", feedCmd, "Fill a player's food bar.", "/feed [player]", "qol.feed");
            getLogger().info("Feed command enabled!");
        }
        
        // Register flyspeed / walkspeed commands
        if (getConfig().getBoolean("flyspeed.enabled", true)) {
            FlySpeedCommand speedCmd = new FlySpeedCommand(this);
            registerCommand("flyspeed", speedCmd, "Set fly speed (0-10).", "/flyspeed <speed> [player]", "qol.flyspeed", "fspeed");
            registerCommand("walkspeed", speedCmd, "Set walk speed (0-10).", "/walkspeed <speed> [player]", "qol.flyspeed", "wspeed");
            getLogger().info("Fly/Walk speed commands enabled!");
        }
        
        // Register trash command
        if (getConfig().getBoolean("trash.enabled", true)) {
            TrashCommand trashCmd = new TrashCommand(this);
            registerCommand("trash", trashCmd, "Open a trash can to dispose of items.", "/trash", "qol.trash", "disposal", "bin");
            getServer().getPluginManager().registerEvents(trashCmd, this);
            getLogger().info("Trash command enabled!");
        }
        
        // Register lore command
        if (getConfig().getBoolean("lore.enabled", true)) {
            LoreCommand loreCmd = new LoreCommand(this);
            registerCommand("lore", loreCmd, "Edit item lore.", "/lore <set|add|clear|remove|insert> [args]", "qol.lore", "itemlore");
            getLogger().info("Lore command enabled!");
        }
        
        // Register hat command
        if (getConfig().getBoolean("hat.enabled", true)) {
            HatCommand hatCmd = new HatCommand(this);
            registerCommand("hat", hatCmd, "Wear an item as a hat.", "/hat", "qol.hat");
            getLogger().info("Hat command enabled!");
        }
        
        // Register warp commands
        if (getConfig().getBoolean("warps.enabled", true)) {
            warpManager = new WarpManager(this);
            WarpCommand warpCmd = new WarpCommand(this);
            WarpsCommand warpsCmd = new WarpsCommand(this);
            SetWarpCommand setWarpCmd = new SetWarpCommand(this);
            DelWarpCommand delWarpCmd = new DelWarpCommand(this);
            registerCommand("warp", warpCmd, "Teleport to a warp. List available warps with /warps.", "/warp <name>", "qol.warp", "warpto");
            registerCommand("warps", warpsCmd, "List all available warps.", "/warps", "qol.warp", "warplist");
            registerCommand("setwarp", setWarpCmd, "Create a new warp.", "/setwarp <name>", "qol.setwarp");
            registerCommand("delwarp", delWarpCmd, "Delete a warp.", "/delwarp <name>", "qol.delwarp", "removewarp");
            getLogger().info("Warp commands enabled!");
        }
        
        // Register random teleport command
        if (getConfig().getBoolean("rtp.enabled", true)) {
            RtpCommand rtpCmd = new RtpCommand(this);
            registerCommand("rtp", rtpCmd, "Teleport to a random location.", "/rtp", "qol.rtp", "randomtp", "randomteleport", "wild");
            getLogger().info("Random teleport command enabled!");
        }
        
        // Register seen command
        if (getConfig().getBoolean("seen.enabled", true)) {
            SeenCommand seenCmd = new SeenCommand(this);
            registerCommand("seen", seenCmd, "Check when a player was last online.", "/seen <player>", "qol.seen", "lastonline");
            getLogger().info("Seen command enabled!");
        }
        
        // Register jail system
        if (getConfig().getBoolean("jail.enabled", true)) {
            jailManager = new JailManager(this);
            JailCommand jailCmd = new JailCommand(this);
            ReleaseCommand releaseCmd = new ReleaseCommand(this);
            getCommand("jail").setExecutor(jailCmd);
            getCommand("jail").setTabCompleter(jailCmd);
            getCommand("release").setExecutor(releaseCmd);
            getCommand("release").setTabCompleter(releaseCmd);
            getServer().getPluginManager().registerEvents(new JailListener(this), this);
            getLogger().info("Jail system enabled!");
        }
        
        // Register freeze system
        if (getConfig().getBoolean("freeze.enabled", true)) {
            freezeManager = new FreezeManager();
            FreezeCommand freezeCmd = new FreezeCommand(this);
            UnfreezeCommand unfreezeCmd = new UnfreezeCommand(this);
            getCommand("freeze").setExecutor(freezeCmd);
            getCommand("freeze").setTabCompleter(freezeCmd);
            getCommand("unfreeze").setExecutor(unfreezeCmd);
            getCommand("unfreeze").setTabCompleter(unfreezeCmd);
            getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
            getLogger().info("Freeze system enabled!");
        }
        
        // Register kits system
        if (getConfig().getBoolean("kits.enabled", true)) {
            kitManager = new KitManager(this);
            kitGUI = new KitGUI(this);
            KitCommand kitCmd = new KitCommand(this);
            registerCommand("kit", kitCmd, "Claim, preview, or manage kits.", "/kit <name|list|preview|create|edit|delete|reload>", "qol.kit", "kits");
            getServer().getPluginManager().registerEvents(kitGUI, this);
            getLogger().info("Kits system enabled! (" + kitManager.getAllKits().size() + " kit(s) loaded)");
        }
        
        // Economy system 
        {
            // Always save default economy.yml so loadConfig() can read it
            File ecoFile = new File(getDataFolder(), "economy.yml");
            if (!ecoFile.exists()) {
                saveResource("economy.yml", false);
            }
            org.bukkit.configuration.file.YamlConfiguration ecoCfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(ecoFile);
            if (ecoCfg.getBoolean("enabled", true)) {
                economyManager = new EconomyManager(this);
                sellGUI = new SellGUI(this);
                shopEditGUI = new ShopEditGUI(this);
                shopGUI = new ShopGUI(this);
                
                // Register Vault provider if Vault is present and use-vault is true
                if (economyManager.isUseVault() && getServer().getPluginManager().getPlugin("Vault") != null) {
                    vaultEconomyProvider = new VaultEconomyProvider(this, economyManager);
                    vaultEconomyProvider.register();
                    getLogger().info("[Economy] Vault economy provider registered!");
                }
                
                // Register economy commands
                BalanceCommand balCmd = new BalanceCommand(this);
                PayCommand payCmd = new PayCommand(this);
                BaltopCommand btCmd = new BaltopCommand(this);
                SellCommand sellCmd = new SellCommand(this);
                SellGUICommand sellGuiCmd = new SellGUICommand(this);
                PriceCommand priceCmd = new PriceCommand(this);
                ValueCommand valueCmd = new ValueCommand(this);
                TaxCommand taxCmd = new TaxCommand(this);
                SetPriceCommand setPriceCmd = new SetPriceCommand(this);
                DelPriceCommand delPriceCmd = new DelPriceCommand(this);
                ShopEditCommand shopEditCmd = new ShopEditCommand(this);
                PriceHistoryCommand priceHistCmd = new PriceHistoryCommand(this);
                
                registerCommand("balance", balCmd, "Check your or another player's balance.", "/balance [player]", "qol.economy.balance", "bal", "money");
                registerCommand("pay", payCmd, "Pay another player.", "/pay <player> <amount>", "qol.economy.pay", "transfer");
                registerCommand("baltop", btCmd, "View the richest players.", "/baltop [page]", "qol.economy.baltop", "bt", "balancetop", "rich");
                registerCommand("sell", sellCmd, "Sell items from your inventory.", "/sell <hand|inventory|all> [amount]", "qol.economy.sell");
                registerCommand("sellgui", sellGuiCmd, "Open the sell GUI.", "/sellgui", "qol.economy.sell", "sgui");
                registerCommand("price", priceCmd, "Check the sell price of an item.", "/price <item>", "qol.economy.price", "itemprice");
                registerCommand("value", valueCmd, "Check the total value of items.", "/value <item> [amount]", "qol.economy.value", "itemvalue");
                registerCommand("tax", taxCmd, "View or manage tax settings.", "/tax [amount|set <rate>]", "qol.economy.tax");
                registerCommand("setprice", setPriceCmd, "Set an item's sell price.", "/setprice <item|#category> <price>", "qol.economy.setprice");
                registerCommand("delprice", delPriceCmd, "Remove an item's sell price.", "/delprice <item|#category>", "qol.economy.delprice");
                registerCommand("shopedit", shopEditCmd, "Open the shop editor GUI.", "/shopedit", "qol.economy.shopedit", "se");
                registerCommand("pricehistory", priceHistCmd, "View an item's price history.", "/pricehistory <item> [length]", "qol.economy.pricehistory", "ph");
                
                ShopCommand shopCmd = new ShopCommand(this);
                registerCommand("shop", shopCmd, "Open the server shop to buy items.", "/shop", "qol.economy.shop", "buy", "store");

                EcoCommand ecoCmd = new EcoCommand(this);
                registerCommand("eco", ecoCmd, "Admin: manage player balances.", "/eco <add|subtract|set|reset|check> <player> [amount]", "qol.economy.eco", "economy");
                
                // Register GUI listeners
                getServer().getPluginManager().registerEvents(sellGUI, this);
                getServer().getPluginManager().registerEvents(shopEditGUI, this);
                getServer().getPluginManager().registerEvents(shopGUI, this);
                
                getLogger().info("Economy system enabled!");
            }
        }
        
        // Register stuck command
        if (getConfig().getBoolean("stuck.enabled", false)) {
            StuckCommand stuckCmd = new StuckCommand(this);
            registerCommand("stuck", stuckCmd, "Unstuck yourself from the nether roof or void.", "/stuck", "qol.stuck", "unstuck");
            getLogger().info("Stuck command enabled!");
        }
        
        // Register noclip command
        if (getConfig().getBoolean("noclip.enabled", true)) {
            NoclipCommand noclipCmd = new NoclipCommand(this);
            registerCommand("noclip", noclipCmd, "Toggle noclip mode (fly through blocks).", "/noclip [player]", "qol.noclip");
            getServer().getPluginManager().registerEvents(noclipCmd, this);
            getLogger().info("Noclip command enabled!");
        }
        
        // Register rules command
        if (getConfig().getBoolean("rules.enabled", true)) {
            RulesCommand rulesCmd = new RulesCommand(this);
            registerCommand("rules", rulesCmd, "Display the server rules.", "/rules", "qol.rules", "serverrules");
            getLogger().info("Rules command enabled!");
        }
        
        // Register smite command
        if (getConfig().getBoolean("smite.enabled", true)) {
            SmiteCommand smiteCmd = new SmiteCommand(this);
            registerCommand("smite", smiteCmd, "Strike lightning on a player.", "/smite <player>", "qol.smite", "lightning", "strike");
            getLogger().info("Smite command enabled!");
        }
        
        // Register report command
        if (getConfig().getBoolean("report.enabled", true)) {
            ReportCommand reportCmd = new ReportCommand(this);
            registerCommand("report", reportCmd, "Report a player or bug.", "/report <player|bug> <reason>", "qol.report");
            getLogger().info("Report command enabled!");
        }
        
        // Register recipe command
        if (getConfig().getBoolean("recipe.enabled", true)) {
            RecipeCommand recipeCmd = new RecipeCommand(this);
            registerCommand("recipe", recipeCmd, "View the crafting recipe for an item.", "/recipe <item> [page]", "qol.recipe", "recipes", "lookup");
            getServer().getPluginManager().registerEvents(recipeCmd, this);
            getLogger().info("Recipe command enabled!");
        }
        
        // Initialize Discord integration if enabled
        if (getConfig().getBoolean("discord.enabled", true)) {
            discordIntegration = new DiscordIntegration(this);
            getLogger().info("Discord integration enabled!");
        }

        // Initialize update checker
        if (getConfig().getBoolean("update-checker.enabled", true)) {
            updateChecker = new UpdateChecker(this);
            UpdateCommand updateCmd = new UpdateCommand(this);
            PluginCommand kpupdateCmd = getCommand("kpupdate");
            if (kpupdateCmd != null) {
                kpupdateCmd.setExecutor(updateCmd);
                kpupdateCmd.setTabCompleter(updateCmd);
            } else {
                // Fallback: dynamic registration (e.g. plugin.yml missing entry)
                registerCommand("kpupdate", updateCmd,
                        "Check for and download plugin updates.",
                        "/kpupdate <check|download>",
                        "qol.update",
                        "pluginupdate", "qolupdate");
            }
            getServer().getPluginManager().registerEvents(new UpdateNotifyListener(this), this);
            // Run the first check 3 seconds after startup so it never delays boot
            Bukkit.getScheduler().runTaskLater(this, () ->
                    updateChecker.checkAsync(null), 60L);
            getLogger().info("Update checker enabled!");
        }
        
        // Schedule automatic cleanup of expired warnings every hour
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            fileManager.cleanupExpiredWarnings();
        }, 20L * 60L * 60L, 20L * 60L * 60L);
        
        // Custom commands & aliases from custom-commands.yml
        if (getConfig().getBoolean("custom-commands.enabled", true)) {
            new com.kelpwing.kelpylandiaplugin.commands.custom.CustomCommandsManager(this).loadAndRegister();
        }

        getLogger().info("KelpylandiaPlugin v" + getDescription().getVersion() + " has been enabled!");
        getLogger().info("Chat and moderation systems are now active.");
        getLogger().info("Server: " + VersionHelper.getVersionSummary());
        
        // Send Discord server start message
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (discordIntegration != null && getConfig().getBoolean("discord.server-lifecycle-events.enabled", true)) {
                discordIntegration.sendServerStartEmbed();
            }
        }, 20L); // Wait 1 second to ensure Discord is fully initialized
    }
    
    @Override
    public void onDisable() {
        // Send Discord server stop message
        if (discordIntegration != null && getConfig().getBoolean("discord.server-lifecycle-events.enabled", true)) {
            discordIntegration.sendServerStopEmbed();
            // Wait a moment for the message to send
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (discordIntegration != null) {
            discordIntegration.disable();
        }
        
        if (tpaManager != null) {
            tpaManager.cleanup();
        }
        
        if (placeholderAPIIntegration != null) {
            placeholderAPIIntegration.unregister();
        }
        
        if (broadcastManager != null) {
            broadcastManager.stop();
        }
        
        // Save economy data and unregister Vault
        if (economyManager != null) {
            economyManager.shutdownDynamicPricing();
            economyManager.saveAll();
        }
        if (vaultEconomyProvider != null) {
            vaultEconomyProvider.unregister();
        }
        
        getLogger().info("KelpylandiaPlugin has been disabled!");
    }
    
    private void initializeIntegrations() {
        // LuckPerms integration
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            luckPermsIntegration = new LuckPermsIntegration();
            getLogger().info("LuckPerms integration enabled!");
        }
        
        // PlaceholderAPI integration
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIIntegration = new PlaceholderAPIIntegration(this);
            placeholderAPIIntegration.register();
            
            // Also register the moderation placeholders
            new KpauPlaceholders(this).register();
            getLogger().info("PlaceholderAPI integration enabled!");
        }
        
        // Item display system - replaces InteractiveChat dependency.
        // Handles [item], [inv], [enderchest] keywords in chat with hover tooltips
        // and clickable inventory snapshots.
        if (getConfig().getBoolean("chat-items.enabled", true)) {
            itemDisplayManager = new ItemDisplayManager(this);
            getServer().getPluginManager().registerEvents(new SnapshotListener(), this);
            registerCommand("qol:viewsnapshot", new ViewSnapshotCommand(this),
                    "View an item/inventory snapshot from chat", "/qol:viewsnapshot <id>",
                    null); // no permission - anyone can click the chat component
            getLogger().info("Chat item display enabled ([item], [inv], [enderchest]).");
        }
    }
    
    /**
     * Dynamically registers a command at runtime via the Bukkit command map.
     * Used for optional features so commands are only registered when the feature is enabled,
     * avoiding conflicts with other plugins that provide the same commands.
     */
    public void registerCommand(String name, CommandExecutor executor, String description, String usage, String permission, String... aliases) {
        try {
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand cmd = constructor.newInstance(name, this);
            
            cmd.setExecutor(executor);
            if (executor instanceof TabCompleter) {
                cmd.setTabCompleter((TabCompleter) executor);
            }
            cmd.setDescription(description);
            cmd.setUsage(usage);
            cmd.setPermission(permission);
            if (aliases.length > 0) {
                cmd.setAliases(Arrays.asList(aliases));
            }
            
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
            commandMap.register(getDescription().getName().toLowerCase(), cmd);
        } catch (Exception e) {
            getLogger().warning("Could not register command /" + name + ": " + e.getMessage());
        }
    }
    
    // Static getter
    public static KelpylandiaPlugin getInstance() {
        return instance;
    }
    
    // Chat-related getters
    public ChannelManager getChannelManager() {
        return channelManager;
    }
    
    public LuckPermsIntegration getLuckPermsIntegration() {
        return luckPermsIntegration;
    }
    
    public PlaceholderAPIIntegration getPlaceholderAPIIntegration() {
        return placeholderAPIIntegration;
    }
    
    public DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }
    
    public ItemDisplayManager getItemDisplayManager() {
        return itemDisplayManager;
    }
    
    // Moderation-related getters
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public FileManager getFileManager() {
        return fileManager;
    }
    
    public VanishManager getVanishManager() {
        return vanishManager;
    }
    
    public Map<UUID, Long> getMutedPlayers() {
        return mutedPlayers;
    }
    
    public PvpCommand getPvpCommand() {
        return pvpCommand;
    }
    
    // Homes & Teleport getters
    public HomeManager getHomeManager() {
        return homeManager;
    }
    
    public HomeGUI getHomeGUI() {
        return homeGUI;
    }
    
    public TpaManager getTpaManager() {
        return tpaManager;
    }
    
    public BackManager getBackManager() {
        return backManager;
    }
    
    public NickManager getNickManager() {
        return nickManager;
    }
    
    public AfkManager getAfkManager() {
        return afkManager;
    }
    
    public MsgCommand getMsgCommand() {
        return msgCommand;
    }
    
    public SpyManager getSpyManager() {
        return spyManager;
    }
    
    public GodCommand getGodCommand() {
        return godCommand;
    }
    
    public VanishCommand getVanishCommand() {
        return vanishCommand;
    }
    
    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }
    
    public DeathMessagesManager getDeathMessagesManager() {
        return deathMessagesManager;
    }
    
    public BroadcastManager getBroadcastManager() {
        return broadcastManager;
    }
    
    public WarpManager getWarpManager() {
        return warpManager;
    }
    
    public JailManager getJailManager() {
        return jailManager;
    }
    
    public FreezeManager getFreezeManager() {
        return freezeManager;
    }
    
    public KitManager getKitManager() {
        return kitManager;
    }
    
    public KitGUI getKitGUI() {
        return kitGUI;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    // Economy getters
    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public SellGUI getSellGUI() {
        return sellGUI;
    }

    public ShopEditGUI getShopEditGUI() {
        return shopEditGUI;
    }

    public ShopGUI getShopGUI() {
        return shopGUI;
    }

    public VaultEconomyProvider getVaultEconomyProvider() {
        return vaultEconomyProvider;
    }

    // Data folder migration 

    /**
     * If the old "KelpylandiaPlugin" data folder exists and the new
     * "QoLPlugin" folder does NOT, rename the old folder so all configs,
     * playerdata, etc. carry over automatically.
     */
    private void migrateDataFolder() {
        File pluginsDir = getDataFolder().getParentFile(); // …/plugins/
        File oldFolder = new File(pluginsDir, "KelpylandiaPlugin");
        File newFolder = getDataFolder(); // …/plugins/QoLPlugin

        if (oldFolder.exists() && !newFolder.exists()) {
            if (oldFolder.renameTo(newFolder)) {
                getLogger().info("[Migration] Renamed data folder KelpylandiaPlugin → QoLPlugin");
            } else {
                getLogger().warning("[Migration] Failed to rename KelpylandiaPlugin → QoLPlugin. " +
                        "Please rename the folder manually.");
            }
        }
    }
}
