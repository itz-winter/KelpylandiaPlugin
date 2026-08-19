package com.kelpwing.kelpylandiaplugin.kits;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Interactive inventory GUI for previewing and editing kits.
 *
 * Preview mode: Read-only view of kit contents (click-proof).
 * Editor mode:  Admin can freely place/remove items; saved on close.
 */
public class KitGUI implements Listener {

    private final KelpylandiaPlugin plugin;

    private static final String PREVIEW_PREFIX = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Kit Preview: ";
    private static final String EDITOR_PREFIX  = ChatColor.RED + "" + ChatColor.BOLD + "Kit Editor: ";

    // Track which players are in which mode
    private final Map<UUID, String> previewSessions = new HashMap<>();  // UUID -> kitName
    private final Map<UUID, String> editorSessions  = new HashMap<>();  // UUID -> kitName

    public KitGUI(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    // Open preview 

    public void openPreview(Player player, Kit kit) {
        int size = roundUpRows(kit.getItems().size());
        Inventory gui = Bukkit.createInventory(null, size, PREVIEW_PREFIX + ChatColor.WHITE + kit.getName());

        for (int i = 0; i < kit.getItems().size() && i < size; i++) {
            ItemStack item = kit.getItems().get(i);
            if (item != null) gui.setItem(i, item.clone());
        }

        previewSessions.put(player.getUniqueId(), kit.getName());
        player.openInventory(gui);
    }

    // Open editor 

    public void openEditor(Player player, Kit kit) {
        // Editor is always 54 slots (6 rows) - the last row has a save indicator
        Inventory gui = Bukkit.createInventory(null, 54, EDITOR_PREFIX + ChatColor.WHITE + kit.getName());

        for (int i = 0; i < kit.getItems().size() && i < 45; i++) {
            ItemStack item = kit.getItems().get(i);
            if (item != null) gui.setItem(i, item.clone());
        }

        // Bottom row: info / decoration
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot < 54; slot++) {
            gui.setItem(slot, filler);
        }

        // Center info item
        ItemStack info = createItem(Material.WRITABLE_BOOK, ChatColor.GREEN + "Kit Editor");
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Place items in the top 5 rows.");
            lore.add(ChatColor.GRAY + "The kit will save when you close this menu.");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Items in the bottom row are decorative.");
            meta.setLore(lore);
            info.setItemMeta(meta);
        }
        gui.setItem(49, info);

        editorSessions.put(player.getUniqueId(), kit.getName());
        player.openInventory(gui);
    }

    // Events 

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();

        // Preview mode - cancel all clicks
        if (previewSessions.containsKey(uuid)) {
            String title = event.getView().getTitle();
            if (title.startsWith(PREVIEW_PREFIX)) {
                event.setCancelled(true);
            }
            return;
        }

        // Editor mode - only block bottom row clicks
        if (editorSessions.containsKey(uuid)) {
            String title = event.getView().getTitle();
            if (title.startsWith(EDITOR_PREFIX)) {
                // Block bottom row (slots 45-53)
                if (event.getRawSlot() >= 45 && event.getRawSlot() <= 53) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Clean up preview
        previewSessions.remove(uuid);

        // Save editor
        String kitName = editorSessions.remove(uuid);
        if (kitName != null) {
            KitManager km = plugin.getKitManager();
            Kit kit = km.getKit(kitName);
            if (kit == null) return;

            // Collect items from top 45 slots
            List<ItemStack> items = new ArrayList<>();
            Inventory inv = event.getInventory();
            for (int i = 0; i < 45; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    items.add(item.clone());
                }
            }

            kit.setItems(items);
            km.saveKit(kit);
            player.sendMessage(ChatColor.GREEN + "Kit '" + kitName + "' saved with " + items.size() + " item(s).");
        }
    }

    // Helpers 

    /**
     * Round item count up to the next multiple of 9 (inventory row size),
     * with a minimum of 9 and max of 54.
     */
    private int roundUpRows(int itemCount) {
        int rows = Math.max(1, (int) Math.ceil((double) Math.max(itemCount, 1) / 9.0));
        rows = Math.min(rows, 6);
        return rows * 9;
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
