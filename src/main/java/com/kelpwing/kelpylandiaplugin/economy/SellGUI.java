package com.kelpwing.kelpylandiaplugin.economy;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.economy.commands.SellCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 54-slot sell GUI: players put items in, on close all sellable items are sold.
 * Shulker boxes: contents are sold, empty box is returned (or with unsellable contents).
 * Empty shulker boxes prompt a confirmation GUI before being sold.
 * Unsellable items are returned to the player. If inventory is full, items drop at feet.
 */
public class SellGUI implements Listener {

    private final KelpylandiaPlugin plugin;

    private static final String GUI_TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Sell Items";
    private static final String CONFIRM_TITLE = ChatColor.RED + "" + ChatColor.BOLD + "Sell Empty Shulker Boxes?";
    private static final int GUI_SIZE = 54; // 6 rows
    private static final int CONFIRM_SIZE = 9;

    // Track which players have the sell GUI open
    private final Set<UUID> openGUIs = new HashSet<>();

    // Track players in the shulker confirmation flow
    private final Map<UUID, PendingSale> pendingConfirmations = new HashMap<>();

    // Track players transitioning between GUIs (sell GUI -> confirm GUI)
    private final Set<UUID> transitioning = new HashSet<>();

    public SellGUI(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Open the sell GUI for a player.
     */
    public void openGUI(Player player) {
        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null || !eco.isEnabled()) {
            player.sendMessage(ChatColor.RED + "The economy system is disabled.");
            return;
        }
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        openGUIs.add(player.getUniqueId());
        player.openInventory(gui);
    }

    
    //  Sell GUI close → process items
    

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        //  Sell GUI closed 
        if (openGUIs.remove(uuid)) {
            if (!event.getView().getTitle().equals(GUI_TITLE)) return;

            EconomyManager eco = plugin.getEconomyManager();
            if (eco == null || !eco.isEnabled()) return;

            processSellGUI(player, eco, event.getInventory());
            return;
        }

        //  Confirmation GUI closed (without clicking a button) 
        if (pendingConfirmations.containsKey(uuid) && !transitioning.contains(uuid)) {
            // Player closed the confirm GUI without clicking — treat as cancel
            PendingSale pending = pendingConfirmations.remove(uuid);
            if (pending != null) {
                finaliseSale(player, eco(), pending, false);
            }
        }
    }

    /**
     * First pass: process all items in the sell GUI.
     * If empty shulker boxes are found that are sellable, open a confirmation GUI.
     * Otherwise, finalise the sale immediately.
     */
    private void processSellGUI(Player player, EconomyManager eco, Inventory gui) {
        BigDecimal totalEarnings = BigDecimal.ZERO;
        int totalItemsSold = 0;
        List<ItemStack> unsellableReturns = new ArrayList<>();
        List<ItemStack> emptyShulkers = new ArrayList<>(); // sellable empty shulkers needing confirmation
        Map<Material, Integer> soldMaterials = new HashMap<>();

        for (int slot = 0; slot < gui.getSize(); slot++) {
            ItemStack item = gui.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            //  Shulker box handling 
            if (EconomyManager.isShulkerBox(item.getType()) && item.hasItemMeta()
                    && item.getItemMeta() instanceof BlockStateMeta) {

                BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
                ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
                boolean isEmpty = isShulkerEmpty(shulker);

                if (isEmpty) {
                    // Empty shulker box — check if it's sellable
                    EconomyManager.PriceResult boxResult = eco.getPrice(item.getType());
                    if (boxResult.sellable) {
                        // Defer to confirmation
                        emptyShulkers.add(item.clone());
                    } else {
                        // Not sellable, return it
                        unsellableReturns.add(item.clone());
                    }
                } else {
                    // Non-empty shulker → sell contents, handle box
                    ShulkerResult result = processShulkerBox(eco, item);
                    totalEarnings = totalEarnings.add(result.earnings);
                    totalItemsSold += result.itemsSold;
                    if (result.returnBox != null) {
                        unsellableReturns.add(result.returnBox);
                    }
                }
                continue;
            }

            //  Normal item 
            EconomyManager.PriceResult priceResult = eco.getPrice(item.getType());
            if (priceResult.sellable) {
                BigDecimal value = priceResult.price.multiply(BigDecimal.valueOf(item.getAmount()));
                totalEarnings = totalEarnings.add(value);
                totalItemsSold += item.getAmount();
                soldMaterials.merge(item.getType(), item.getAmount(), Integer::sum);
            } else {
                unsellableReturns.add(item.clone());
            }
        }

        PendingSale pending = new PendingSale(totalEarnings, totalItemsSold, unsellableReturns, emptyShulkers, soldMaterials);

        // If there are empty shulker boxes to confirm, open confirmation GUI
        if (!emptyShulkers.isEmpty()) {
            pendingConfirmations.put(player.getUniqueId(), pending);
            transitioning.add(player.getUniqueId());
            // Open confirm GUI on next tick (can't open inventory during close event)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                transitioning.remove(player.getUniqueId());
                if (player.isOnline()) {
                    openConfirmGUI(player, eco, emptyShulkers);
                } else {
                    // Player left — cancel, return everything
                    pendingConfirmations.remove(player.getUniqueId());
                }
            }, 1L);
        } else {
            // No empty shulkers — finalise immediately
            finaliseSale(player, eco, pending, false);
        }
    }

    
    //  Confirmation GUI for empty shulker boxes
    

    private void openConfirmGUI(Player player, EconomyManager eco, List<ItemStack> emptyShulkers) {
        Inventory confirm = Bukkit.createInventory(null, CONFIRM_SIZE, CONFIRM_TITLE);

        // Calculate total value of the empty shulkers
        BigDecimal shulkerValue = BigDecimal.ZERO;
        for (ItemStack s : emptyShulkers) {
            EconomyManager.PriceResult pr = eco.getPrice(s.getType());
            if (pr.sellable) {
                shulkerValue = shulkerValue.add(pr.price.multiply(BigDecimal.valueOf(s.getAmount())));
            }
        }

        // Slot 2: Confirm (green wool)
        ItemStack confirmBtn = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirmBtn.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Sell Shulker Boxes");
        confirmMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Sell " + emptyShulkers.size() + " empty shulker box(es)",
                ChatColor.GRAY + "for " + eco.formatMoney(shulkerValue)
        ));
        confirmBtn.setItemMeta(confirmMeta);
        confirm.setItem(2, confirmBtn);

        // Slot 4: Info (shulker box icon)
        if (!emptyShulkers.isEmpty()) {
            ItemStack preview = emptyShulkers.get(0).clone();
            preview.setAmount(emptyShulkers.size());
            ItemMeta previewMeta = preview.getItemMeta();
            previewMeta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Empty Shulker Box(es)");
            previewMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "These boxes are empty.",
                    ChatColor.GRAY + "Are you sure you want to sell them?"
            ));
            preview.setItemMeta(previewMeta);
            confirm.setItem(4, preview);
        }

        // Slot 6: Cancel (red wool)
        ItemStack cancelBtn = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelBtn.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Cancel & Return");
        cancelMeta.setLore(Collections.singletonList(
                ChatColor.GRAY + "Return the empty shulker boxes"
        ));
        cancelBtn.setItemMeta(cancelMeta);
        confirm.setItem(6, cancelBtn);

        player.openInventory(confirm);
    }

    @EventHandler
    public void onConfirmClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();

        if (!pendingConfirmations.containsKey(uuid)) return;
        if (!event.getView().getTitle().equals(CONFIRM_TITLE)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= CONFIRM_SIZE) return;

        PendingSale pending = pendingConfirmations.remove(uuid);
        if (pending == null) return;

        EconomyManager eco = eco();
        if (eco == null) return;

        if (slot == 2) {
            //  Confirm: sell the empty shulkers 
            transitioning.add(uuid);
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> transitioning.remove(uuid), 2L);
            finaliseSale(player, eco, pending, true);
        } else if (slot == 6) {
            //  Cancel: return the empty shulkers 
            transitioning.add(uuid);
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> transitioning.remove(uuid), 2L);
            finaliseSale(player, eco, pending, false);
        }
        // Clicking elsewhere does nothing
    }

    
    //  Finalise the sale — deposit money, return items, send messages
    

    private void finaliseSale(Player player, EconomyManager eco, PendingSale pending, boolean sellEmptyShulkers) {
        BigDecimal totalEarnings = pending.earnings;
        int totalItemsSold = pending.itemsSold;
        List<ItemStack> unsellableReturns = new ArrayList<>(pending.unsellableReturns);

        if (sellEmptyShulkers) {
            // Add empty shulker values
            for (ItemStack s : pending.emptyShulkers) {
                EconomyManager.PriceResult pr = eco.getPrice(s.getType());
                if (pr.sellable) {
                    totalEarnings = totalEarnings.add(pr.price.multiply(BigDecimal.valueOf(s.getAmount())));
                    totalItemsSold += s.getAmount();
                }
            }
        } else {
            // Return empty shulkers to the player
            unsellableReturns.addAll(pending.emptyShulkers);
        }

        // Apply tax if configured
        boolean taxApplied = eco.isTaxEnabled() && eco.isTaxOnServerSell();
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal netEarnings = totalEarnings;
        if (taxApplied && totalEarnings.compareTo(BigDecimal.ZERO) > 0) {
            taxAmount = eco.calculateTax(totalEarnings);
            netEarnings = eco.afterTax(totalEarnings);
        }

        // Deposit earnings
        if (netEarnings.compareTo(BigDecimal.ZERO) > 0) {
            eco.deposit(player.getUniqueId(), netEarnings);
            eco.sendTransactionHUD(player, netEarnings, true);
        }

        // Record sales for dynamic pricing
        for (Map.Entry<Material, Integer> entry : pending.soldMaterials.entrySet()) {
            eco.recordSale(entry.getKey(), entry.getValue(), player.getUniqueId());
        }
        if (sellEmptyShulkers) {
            for (ItemStack s : pending.emptyShulkers) {
                eco.recordSale(s.getType(), s.getAmount(), player.getUniqueId());
            }
        }

        // Return unsellable items, track dropped count
        int returnedCount = 0;
        int droppedCount = 0;
        for (ItemStack item : unsellableReturns) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (overflow.isEmpty()) {
                returnedCount += item.getAmount();
            } else {
                // Count what fit vs what was dropped
                int dropped = 0;
                for (ItemStack leftover : overflow.values()) {
                    dropped += leftover.getAmount();
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                returnedCount += item.getAmount() - dropped;
                droppedCount += dropped;
            }
        }

        // Send summary to player
        if (totalItemsSold > 0) {
            String formatted = eco.formatMoney(netEarnings);
            player.sendMessage(eco.getMessage("sell-gui-success")
                    .replace("{amount}", String.valueOf(totalItemsSold))
                    .replace("{total}", formatted)
                    .replace("{unit}", eco.getUnit()));

            if (taxApplied && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                player.sendMessage(eco.getMessage("sell-tax")
                        .replace("{rate}", eco.getTaxRateDisplay())
                        .replace("{tax}", eco.formatMoney(taxAmount))
                        .replace("{unit}", eco.getUnit()));
            }
        } else if (!unsellableReturns.isEmpty()) {
            player.sendMessage(ChatColor.RED + "None of those items could be sold. Unsellable items returned.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "You didn't put anything in the sell GUI.");
        }

        // Notify about returned unsellable items
        if (returnedCount > 0) {
            player.sendMessage(eco.getMessage("sell-gui-returned")
                    .replace("{count}", String.valueOf(returnedCount)));
        }
        if (droppedCount > 0) {
            player.sendMessage(eco.getMessage("sell-gui-dropped")
                    .replace("{count}", String.valueOf(droppedCount)));
        }
    }

    
    //  Shulker box processing (non-empty boxes)
    

    /**
     * Process a non-empty shulker box: sell sellable contents, return box with unsellable contents.
     */
    private ShulkerResult processShulkerBox(EconomyManager eco, ItemStack shulkerItem) {
        BlockStateMeta meta = (BlockStateMeta) shulkerItem.getItemMeta();
        ShulkerBox shulker = (ShulkerBox) meta.getBlockState();

        BigDecimal earnings = BigDecimal.ZERO;
        int itemsSold = 0;
        boolean hasUnsellable = false;

        ItemStack[] contents = shulker.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack content = contents[i];
            if (content == null || content.getType() == Material.AIR) continue;

            EconomyManager.PriceResult result = eco.getPrice(content.getType());
            if (result.sellable) {
                earnings = earnings.add(result.price.multiply(BigDecimal.valueOf(content.getAmount())));
                itemsSold += content.getAmount();
                contents[i] = null; // remove sold item
            } else {
                hasUnsellable = true;
            }
        }

        // All contents sold — the box is now empty after processing
        if (!hasUnsellable && itemsSold > 0) {
            EconomyManager.PriceResult boxResult = eco.getPrice(shulkerItem.getType());
            if (boxResult.sellable) {
                // Sell the empty box too
                earnings = earnings.add(boxResult.price);
                itemsSold++;
                return new ShulkerResult(earnings, itemsSold, null);
            }
            // Box not sellable — return it empty
            shulker.getInventory().clear();
            meta.setBlockState(shulker);
            shulkerItem.setItemMeta(meta);
            return new ShulkerResult(earnings, itemsSold, shulkerItem);
        }

        if (hasUnsellable || itemsSold == 0) {
            // Put remaining contents back and return the box
            shulker.getInventory().setContents(contents);
            meta.setBlockState(shulker);
            shulkerItem.setItemMeta(meta);
            return new ShulkerResult(earnings, itemsSold, shulkerItem);
        }

        return new ShulkerResult(earnings, itemsSold, null);
    }

    private boolean isShulkerEmpty(ShulkerBox shulker) {
        for (ItemStack content : shulker.getInventory().getContents()) {
            if (content != null && content.getType() != Material.AIR) return false;
        }
        return true;
    }

    /**
     * Check if a player has the sell GUI open (for preventing exploits).
     */
    public boolean hasGUIOpen(UUID uuid) {
        return openGUIs.contains(uuid) || pendingConfirmations.containsKey(uuid);
    }

    private EconomyManager eco() {
        return plugin.getEconomyManager();
    }

    
    //  Internal data classes
    

    private static class ShulkerResult {
        final BigDecimal earnings;
        final int itemsSold;
        final ItemStack returnBox; // null if nothing to return

        ShulkerResult(BigDecimal earnings, int itemsSold, ItemStack returnBox) {
            this.earnings = earnings;
            this.itemsSold = itemsSold;
            this.returnBox = returnBox;
        }
    }

    /**
     * Holds the intermediate state between the sell GUI and the confirmation GUI.
     */
    private static class PendingSale {
        final BigDecimal earnings;          // earnings from non-shulker items + non-empty shulker contents
        final int itemsSold;                // count from above
        final List<ItemStack> unsellableReturns; // items to return regardless
        final List<ItemStack> emptyShulkers;     // empty shulker boxes awaiting confirmation
        final Map<Material, Integer> soldMaterials; // material → count for dynamic pricing tracking

        PendingSale(BigDecimal earnings, int itemsSold, List<ItemStack> unsellableReturns,
                    List<ItemStack> emptyShulkers, Map<Material, Integer> soldMaterials) {
            this.earnings = earnings;
            this.itemsSold = itemsSold;
            this.unsellableReturns = unsellableReturns;
            this.emptyShulkers = emptyShulkers;
            this.soldMaterials = soldMaterials;
        }
    }
}
