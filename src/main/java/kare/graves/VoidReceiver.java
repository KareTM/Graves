package kare.graves;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class VoidReceiver implements Listener{

    ArrayList<Location> voidReceivers = new ArrayList<>();
    String stringVoidReceiver = "Void Receiver";
    Component voidReceiverName = Component.text(stringVoidReceiver)
            .color(TextColor.color(0x7C0280))
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false);

    public void loadData(File voidFile) {
        if (voidFile.exists() && voidFile.length() != 0) {
            try {
                var fr = new BufferedReader(new FileReader(voidFile));
                var saved = fr.lines().collect(Collectors.joining());
                var split = saved.split("\\|");
                for (var i = 0; i < split.length; i += 4) {
                    var u = UUID.fromString(split[i]);
                    var x = Double.parseDouble(split[i+1]);
                    var y = Double.parseDouble(split[i+2]);
                    var z = Double.parseDouble(split[i+3]);
                    var w = Bukkit.getWorld(u);
                    var l = new Location(w, x, y, z);
                    voidReceivers.add(l);
                }
                fr.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void saveData(File voidFile) {
        if (!voidFile.exists()) {
            try {
                voidFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            var fw = new BufferedWriter(new FileWriter(voidFile));
            voidReceivers.forEach(l -> {
                String location = "" + l.getWorld().getUID() + '|' + l.getX() + '|' + l.getY() + '|' + l.getZ();
                try {
                    fw.write(location + '|');
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            fw.flush();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ItemStack createVoidReceiver() {
        var item = new ItemStack(Material.RESPAWN_ANCHOR);

        var meta = item.getItemMeta();


        meta.displayName(voidReceiverName);

        List<Component> l = new ArrayList<>();
        l.add(Component.text("Retrieve items from a parallel universe where")
                .color(NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD));

        l.add(Component.text("the void hasn't claimed your soul.")
                .color(NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD));
        meta.lore(l);

        item.setItemMeta(meta);
        return item;
    }

    public void registerVoidReceiver() {
        var item = createVoidReceiver();

        var key = new NamespacedKey(Graves.getInstance(), "void_receiver");

        var recipe = new ShapedRecipe(key, item);

        recipe.shape(" D ", "DED", " D ");

        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('E', Material.ENDER_CHEST);

        Bukkit.addRecipe(recipe);
    }

    public ItemStack createChargedPearl() {
        var item = new ItemStack(Material.ENDER_PEARL);

        var meta = item.getItemMeta();

        Component name = Component.text("Charged Pearl")
                .color(TextColor.color(0x7C0280))
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(name);

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);

        item.setItemMeta(meta);

        return item;
    }

    public void registerChargedPearl() {
        var item = createChargedPearl();

        var key = new NamespacedKey(Graves.getInstance(), "charged_pearl");

        var recipe = new ShapedRecipe(key, item);

        recipe.shape("EEE", "E E", "EEE");

        recipe.setIngredient('E', new RecipeChoice.ExactChoice(new ItemStack(Material.ENDER_PEARL)));

        Bukkit.addRecipe(recipe);
    }

    public void registerVoidCharge() {
        var item = new ItemStack(Material.ENDER_EYE);

        var meta = item.getItemMeta();

        Component name = Component.text("Void Charge")
                .color(TextColor.color(0x7C0280))
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(name);

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);

        item.setItemMeta(meta);

        var key = new NamespacedKey(Graves.getInstance(), "void_charge");

        var recipe = new ShapedRecipe(key, item);

        recipe.shape("EEE", "EDE", "EEE");

        var choice = new RecipeChoice.ExactChoice(createChargedPearl());

        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('E', choice);

        Bukkit.addRecipe(recipe);
    }

    public void onBlockInteract(PlayerInteractEvent e, Map<UUID, ArrayList<ItemStack>> voidDeath) {
        if (e.hasBlock() && voidReceivers.contains(e.getClickedBlock().getLocation())) {
            if (e.getAction().isRightClick()) {
                e.setCancelled(true);
                if (e.getItem() != null && e.getItem().hasItemFlag(ItemFlag.HIDE_ENCHANTS))
                    return;
                if (!voidDeath.containsKey(e.getPlayer().getUniqueId()))
                    return;

                var resp = (RespawnAnchor) e.getClickedBlock().getBlockData();
                if (resp.getCharges() == 0)
                    return;
                else
                    resp.setCharges(resp.getCharges() - 1);

                e.getClickedBlock().setBlockData(resp);

                var arr = voidDeath.get(e.getPlayer().getUniqueId());
                var inv = Bukkit.createInventory(null, 54, voidReceiverName);
                ArrayList<ItemStack> added = new ArrayList<>();
                for(var i = 0; i < inv.getSize() && i < arr.size(); i++) {
                    inv.setItem(i, arr.get(i));
                    added.add(arr.get(i));
                }

                arr.removeAll(added);
                if (arr.isEmpty())
                    voidDeath.remove(e.getPlayer().getUniqueId());
                else
                    voidDeath.put(e.getPlayer().getUniqueId(), arr);

                e.getPlayer().openInventory(inv);
            }
        }
    }

    public void onCloseInventory(InventoryCloseEvent e, Map<UUID, ArrayList<ItemStack>> voidDeath) {
        if (e.getView().title().toString().contains(stringVoidReceiver) && e.getInventory().getHolder() == null) {
            Player p = (Player) e.getPlayer();

            ArrayList<ItemStack> arr = null;
            if (voidDeath.containsKey(p.getUniqueId()))
                arr = voidDeath.get(p.getUniqueId());


            var invLeft = e.getInventory().getContents();
            if (arr == null)
                arr = new ArrayList<>();

            for (ItemStack i : invLeft)
                if (i != null)
                    arr.add(i);

            if (arr.isEmpty())
                voidDeath.remove(p.getUniqueId());
            else
                voidDeath.put(p.getUniqueId(), arr);
        }

    }

    @EventHandler
    public void onPlaceBlock(BlockPlaceEvent e) {
        if (e.getItemInHand().getType() == Material.RESPAWN_ANCHOR
                && e.getItemInHand().getItemMeta().displayName() != null
                && e.getItemInHand().getItemMeta().displayName().toString().contains(stringVoidReceiver)) {
            voidReceivers.add(e.getBlockPlaced().getLocation());
        }
    }

    @EventHandler
    public void onBreakBlock(BlockBreakEvent e) {
        if (voidReceivers.contains(e.getBlock().getLocation())) {
            e.setDropItems(false);
            e.getBlock().getWorld().dropItem(e.getBlock().getLocation(), createVoidReceiver());
            voidReceivers.remove(e.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onItemUse(PlayerInteractEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.ENDER_EYE && e.getItem().hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
            e.setCancelled(true);
            if (e.getClickedBlock() != null && voidReceivers.contains(e.getClickedBlock().getLocation())) {
                var resp = (RespawnAnchor) e.getClickedBlock().getBlockData();
                if (resp.getCharges() < resp.getMaximumCharges()) {
                    resp.setCharges(resp.getCharges() + 1);
                    e.getItem().setAmount(e.getItem().getAmount() - 1);

                    e.getClickedBlock().setBlockData(resp);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        p.discoverRecipe(new NamespacedKey(Graves.getInstance(), "void_receiver"));
        p.discoverRecipe(new NamespacedKey(Graves.getInstance(), "void_charge"));
        p.discoverRecipe(new NamespacedKey(Graves.getInstance(), "charged_pearl"));
    }
}
