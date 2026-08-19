# QoLPlugin

**! MAKE SURE TO USE THE LATEST VERSION !**

A lil' custom plugin with a cringe name :3

## Overview

QoLPlugin is a highly customizable, all-in-one Minecraft server plugin designed for small to mid-sized SMP servers. Originally built for my own community, it combines advanced chat, moderation, player homes, teleportation, and more-making it a great alternative to EssentialsX and similar plugins. Built on the Spigot/Bukkit API, it supports **Minecraft 1.16 – 1.21.11** (and likely newer).

## Features

### Chat System

- **Channel-Based Communication**: Multiple chat channels (Global, Local, Admin, etc.) with customizable formats and permissions.
- **Proximity Chat**: Local chat within a configurable radius.
- **Custom Channels**: Create and delete your own channels.
- **Discord Integration**: Sync in-game chat with Discord channels.
- **Customizable Formats**: Fully configurable message formats for both Minecraft and Discord.

> **Tip:** For advanced Discord features, [DiscordSRV](https://modrinth.com/plugin/discordsrv) is recommended.

### Moderation Tools

- **Broadcast Actions**: Notify players of bans, kicks, mutes, and other moderation actions.
- **Customizable Punishment Formats**: Define how moderation messages appear.
- **Join/Leave Messages**: Custom join/leave messages, with the option to hide vanilla ones.
- **Rules Command**: Display server rules to players.
- **Ban, Kick, Mute, Warn, Unban, Unmute, Unwarn**: All the essentials for server moderation.

### Player Utilities

- **Homes System**: `/home`, `/sethome`, `/delhome`, `/homes` with GUI and configurable max homes.  
  *(Disabled by default-enable in `config.yml`!)*
- **Teleportation System**: `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpcancel` for player-to-player teleport requests, with cooldowns and invulnerability options.  
  *(Disabled by default-enable in `config.yml`!)*
- **Workbench Commands**: Open crafting tables, ender chests, anvils, and more via commands like `/wb`, `/ec`, `/anvil`, etc.  
  *(Configurable permissions and can be disabled to avoid conflicts.)*

### Economy System

- **Economy Integration**: Works with popular economy plugins like Vault.
- **Sell Items**: Players can sell items for in-game currency using `/sell` and `/sellgui`.
- **Configurable Prices**: Set custom prices for items in the config or via `/shopedit`.
- **Dynamic Pricing**: (Experimental) Item prices can fluctuate based on supply and demand. Enable in `economy.yml`!
- **Shop GUI**: A user-friendly interface for buying and selling items.
- **Player Transactions**: Send money to other players with `/pay`.
- **Player Balances and Top Balances**: Check your balance with `/balance` and view the richest players with `/baltop`.
  - Assign `qol.economy.baltop.exclude` permission to exclude players from baltop rankings.

### Sitting System (pre-2.1.0 ONLY)

- **Player Sitting**: Sit on other players by right-clicking with an empty hand.
- **Block Sitting**: Sit on stairs, slabs, and other blocks.
- **Configurable**: Toggle sitting features in the config.

> **Note:** For a more robust sitting experience, use [Gsit](https://www.spigotmc.org/resources/gsit-modern-sit-seat-and-chair-lay-and-crawl-plugin-1-16-1-21-11.62325/) and set `sitting.enabled` to `false` in your config.

### General

- **Highly Configurable**: Nearly every feature can be toggled or tweaked in `config.yml`.
- **Permission-Based**: Fine-grained permissions for all commands and features.
- **No EssentialsX Dependency**: Can fully replace EssentialsX for most servers.

## Compatibility

- **Minecraft Versions:** 1.16 – Current Version
- **Java:** 17+

## Getting Started

1. Drop the plugin JAR into your `plugins` folder.
2. Start your server to generate the config.
3. Edit `config.yml` to enable/disable features as you like.
4. Reload or restart your server.

## Recommendations

- ~~For advanced Discord integration, use [DiscordSRV](https://modrinth.com/plugin/discordsrv).~~
  - **Newest versions 2.1.6+ has more advanced Discord integration, similar to that of DiscordSRV to make the switch easier.**
- Use [Gsit](https://www.spigotmc.org/resources/gsit-modern-sit-seat-and-chair-lay-and-crawl-plugin-1-16-1-21-11.62325/) ~~until the built-in system is improved.~~
  - **I am not fixing the sit system. It has been removed completely in the latest versions.**

---

*Made with love, cringe, and a lot of config options.*

\- Kelp
> [GitHub](https://github.com/itz-winter/) • [Discord](https://discord.com/users/851136131071344681) • [Youtube](https://www.youtube.com/@kelpwing)

---
