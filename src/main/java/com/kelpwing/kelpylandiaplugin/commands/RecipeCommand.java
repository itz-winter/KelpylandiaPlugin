package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /recipe <item> [page] - View the crafting recipe for an item.
 * Opens a 54-slot chest GUI that shows the 3x3 crafting grid on the left,
 * an arrow in the middle, and the result on the right.
 * Supports shaped, shapeless, furnace, blasting, smoking, campfire,
 * stonecutting recipes. Multiple recipes for the same item are paginated.
 */
public class RecipeCommand implements CommandExecutor, TabCompleter, Listener {

    private final KelpylandiaPlugin plugin;

    /** Title prefix so we can identify our recipe GUIs in the click listener. */
    private static final String GUI_TITLE_PREFIX = ChatColor.DARK_GREEN + "Recipe: ";

    // GUI layout constants for a 54-slot (6-row) inventory
    // The 3x3 crafting grid is placed at rows 1-3 (0-indexed), columns 1-3
    // Grid slots: row*9 + col
    private static final int[] GRID_SLOTS = {
        10, 11, 12,   // row 1
        19, 20, 21,   // row 2
        28, 29, 30    // row 3
    };
    private static final int ARROW_SLOT = 23;   // middle-right area
    private static final int RESULT_SLOT = 25;  // right of arrow
    private static final int PREV_SLOT = 45;    // bottom-left
    private static final int NEXT_SLOT = 53;    // bottom-right
    private static final int INFO_SLOT = 49;    // bottom-center

    public RecipeCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 1) {
            // If holding an item, use that
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType() != Material.AIR) {
                openRecipeGUI(player, held.getType(), 0);
                return true;
            }
            player.sendMessage(ChatColor.RED + "Usage: /recipe <item> [page]");
            player.sendMessage(ChatColor.GRAY + "Tip: Hold an item and type /recipe to look up what you're holding.");
            return true;
        }

        String itemName = args[0].toUpperCase().replace("-", "_");
        Material material = Material.matchMaterial(itemName);
        if (material == null) {
            // Try with minecraft: prefix
            material = Material.matchMaterial("minecraft:" + args[0].toLowerCase().replace("-", "_"));
        }
        if (material == null) {
            player.sendMessage(ChatColor.RED + "Unknown item: " + ChatColor.GOLD + args[0]);
            player.sendMessage(ChatColor.GRAY + "Use the material name, e.g. " + ChatColor.WHITE + "diamond_sword" + ChatColor.GRAY + ".");
            return true;
        }

        int page = 0;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]) - 1;
                if (page < 0) page = 0;
            } catch (NumberFormatException ignored) {
            }
        }

        openRecipeGUI(player, material, page);
        return true;
    }

    /**
     * Find all recipes for a material and open the GUI for the given page.
     */
    private void openRecipeGUI(Player player, Material material, int page) {
        List<Recipe> recipes = new ArrayList<>();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe.getResult().getType() == material) {
                recipes.add(recipe);
            }
        }

        if (recipes.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No recipe found for " + ChatColor.GOLD + formatMaterialName(material) + ChatColor.RED + ".");
            return;
        }

        if (page >= recipes.size()) page = recipes.size() - 1;

        Recipe recipe = recipes.get(page);
        String title = GUI_TITLE_PREFIX + formatMaterialName(material);
        // Trim title to 32 chars (Bukkit limit)
        if (title.length() > 32) title = title.substring(0, 32);

        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Fill background with gray stained-glass panes
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        // Place the result
        gui.setItem(RESULT_SLOT, recipe.getResult());

        // Arrow indicator
        gui.setItem(ARROW_SLOT, createItem(Material.ARROW, ChatColor.GREEN + "→"));

        // Fill the grid based on recipe type
        if (recipe instanceof ShapedRecipe shaped) {
            fillShaped(gui, shaped);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            fillShapeless(gui, shapeless);
        } else if (recipe instanceof FurnaceRecipe furnace) {
            fillCooking(gui, furnace.getInputChoice(), "Furnace", furnace.getCookingTime(), furnace.getExperience());
        } else if (recipe instanceof BlastingRecipe blasting) {
            fillCooking(gui, blasting.getInputChoice(), "Blast Furnace", blasting.getCookingTime(), blasting.getExperience());
        } else if (recipe instanceof SmokingRecipe smoking) {
            fillCooking(gui, smoking.getInputChoice(), "Smoker", smoking.getCookingTime(), smoking.getExperience());
        } else if (recipe instanceof CampfireRecipe campfire) {
            fillCooking(gui, campfire.getInputChoice(), "Campfire", campfire.getCookingTime(), campfire.getExperience());
        } else if (recipe instanceof StonecuttingRecipe stonecutting) {
            fillStonecutting(gui, stonecutting);
        } else if (recipe instanceof SmithingRecipe) {
            fillSmithing(gui, recipe);
        } else {
            // Unknown recipe type - show info
            gui.setItem(GRID_SLOTS[4], createItem(Material.BARRIER, ChatColor.RED + "Recipe type not displayable"));
        }

        // Info item (page / total)
        ItemStack infoItem = createItem(Material.BOOK,
                ChatColor.YELLOW + "Page " + (page + 1) + "/" + recipes.size());
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + getRecipeTypeName(recipe));
            if (recipes.size() > 1) {
                lore.add(ChatColor.GRAY + "Use arrows to browse recipes");
            }
            // Store the material and page for navigation
            lore.add(ChatColor.BLACK + "" + ChatColor.MAGIC + material.name() + "|" + page);
            infoMeta.setLore(lore);
            infoItem.setItemMeta(infoMeta);
        }
        gui.setItem(INFO_SLOT, infoItem);

        // Navigation arrows
        if (page > 0) {
            gui.setItem(PREV_SLOT, createItem(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "← Previous Recipe"));
        }
        if (page < recipes.size() - 1) {
            gui.setItem(NEXT_SLOT, createItem(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next Recipe →"));
        }

        player.openInventory(gui);
    }

    // ===== Grid fillers =====

    private void fillShaped(Inventory gui, ShapedRecipe recipe) {
        Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();
        String[] shape = recipe.getShape();

        for (int row = 0; row < shape.length && row < 3; row++) {
            String line = shape[row];
            for (int col = 0; col < line.length() && col < 3; col++) {
                char c = line.charAt(col);
                RecipeChoice choice = choiceMap.get(c);
                int slot = GRID_SLOTS[row * 3 + col];
                if (choice != null) {
                    gui.setItem(slot, choiceToItem(choice));
                } else {
                    gui.setItem(slot, new ItemStack(Material.AIR));
                }
            }
        }
    }

    private void fillShapeless(Inventory gui, ShapelessRecipe recipe) {
        List<RecipeChoice> choices = recipe.getChoiceList();
        // Clear grid first
        for (int slot : GRID_SLOTS) {
            gui.setItem(slot, new ItemStack(Material.AIR));
        }
        for (int i = 0; i < choices.size() && i < 9; i++) {
            gui.setItem(GRID_SLOTS[i], choiceToItem(choices.get(i)));
        }
    }

    private void fillCooking(Inventory gui, RecipeChoice input, String type, int cookTime, float xp) {
        // Clear grid
        for (int slot : GRID_SLOTS) {
            gui.setItem(slot, new ItemStack(Material.AIR));
        }
        // Input in the center-top
        gui.setItem(GRID_SLOTS[1], choiceToItem(input));

        // Fuel indicator below
        ItemStack fuelItem;
        if (type.equals("Campfire")) {
            fuelItem = createItem(Material.CAMPFIRE, ChatColor.GOLD + type);
        } else if (type.equals("Blast Furnace")) {
            fuelItem = createItem(Material.BLAST_FURNACE, ChatColor.GOLD + type);
        } else if (type.equals("Smoker")) {
            fuelItem = createItem(Material.SMOKER, ChatColor.GOLD + type);
        } else {
            fuelItem = createItem(Material.FURNACE, ChatColor.GOLD + type);
        }
        ItemMeta meta = fuelItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Cook time: " + ChatColor.WHITE + (cookTime / 20) + "s");
            if (xp > 0) lore.add(ChatColor.GRAY + "XP: " + ChatColor.WHITE + xp);
            meta.setLore(lore);
            fuelItem.setItemMeta(meta);
        }
        gui.setItem(GRID_SLOTS[7], fuelItem); // bottom-center of grid
    }

    private void fillStonecutting(Inventory gui, StonecuttingRecipe recipe) {
        for (int slot : GRID_SLOTS) {
            gui.setItem(slot, new ItemStack(Material.AIR));
        }
        gui.setItem(GRID_SLOTS[1], choiceToItem(recipe.getInputChoice()));
        gui.setItem(GRID_SLOTS[7], createItem(Material.STONECUTTER, ChatColor.GOLD + "Stonecutter"));
    }

    private void fillSmithing(Inventory gui, Recipe recipe) {
        for (int slot : GRID_SLOTS) {
            gui.setItem(slot, new ItemStack(Material.AIR));
        }
        gui.setItem(GRID_SLOTS[4], createItem(Material.SMITHING_TABLE, ChatColor.GOLD + "Smithing Table"));
        // SmithingRecipe's internals vary across MC versions; just show the station
    }

    // ===== Helpers =====

    private ItemStack choiceToItem(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice matChoice) {
            List<Material> choices = matChoice.getChoices();
            ItemStack item = new ItemStack(choices.get(0));
            if (choices.size() > 1) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Any of:");
                    for (int i = 0; i < Math.min(choices.size(), 8); i++) {
                        lore.add(ChatColor.WHITE + "  " + formatMaterialName(choices.get(i)));
                    }
                    if (choices.size() > 8) {
                        lore.add(ChatColor.GRAY + "  ...and " + (choices.size() - 8) + " more");
                    }
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
            return item;
        } else if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            return exactChoice.getChoices().get(0);
        }
        return new ItemStack(Material.BARRIER);
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

    private String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String getRecipeTypeName(Recipe recipe) {
        if (recipe instanceof ShapedRecipe) return "Shaped Crafting";
        if (recipe instanceof ShapelessRecipe) return "Shapeless Crafting";
        if (recipe instanceof FurnaceRecipe) return "Furnace Smelting";
        if (recipe instanceof BlastingRecipe) return "Blast Furnace";
        if (recipe instanceof SmokingRecipe) return "Smoker";
        if (recipe instanceof CampfireRecipe) return "Campfire Cooking";
        if (recipe instanceof StonecuttingRecipe) return "Stonecutting";
        if (recipe instanceof SmithingRecipe) return "Smithing";
        return "Unknown";
    }

    // ===== Inventory click handling =====

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;

        event.setCancelled(true); // Always cancel - read-only GUI

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Read navigation data from the info item
        ItemStack infoItem = event.getInventory().getItem(INFO_SLOT);
        if (infoItem == null || infoItem.getItemMeta() == null) return;
        List<String> lore = infoItem.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return;

        // The hidden data is in the last lore line: §k<MATERIAL>|<page>
        String hidden = ChatColor.stripColor(lore.get(lore.size() - 1));
        String[] parts = hidden.split("\\|");
        if (parts.length != 2) return;

        Material material;
        int currentPage;
        try {
            material = Material.valueOf(parts[0]);
            currentPage = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return;
        }

        if (slot == PREV_SLOT && currentPage > 0) {
            openRecipeGUI(player, material, currentPage - 1);
        } else if (slot == NEXT_SLOT) {
            openRecipeGUI(player, material, currentPage + 1);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (title.startsWith(GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
        }
    }

    // ===== Tab completion =====

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            // Return matching material names (only items, not unobtainable blocks)
            return Arrays.stream(Material.values())
                    .filter(Material::isItem)
                    .map(m -> m.name().toLowerCase())
                    .filter(name -> name.startsWith(partial))
                    .sorted()
                    .limit(40) // Cap to prevent huge lists
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
