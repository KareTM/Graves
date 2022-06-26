package kare.graves;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static kare.graves.Utils.*;

public final class Graves extends JavaPlugin implements CommandExecutor, Listener {

    public record Pair<L, R>(L left, R right) {
        public static <L, R> Pair<L, R> of(L left, R right) {
            return new Pair<>(left, right);
        }
    }

    public HashMap<Location, Inventory> gravesMap = new HashMap<>();
    public HashMap<Location, Pair<String, Inventory>> gravesMapForFile = new HashMap<>();
    public HashMap<Player, Location> openedGraveMap = new HashMap<>();
    HashMap<UUID, ArrayList<ItemStack>> voidDeath = new HashMap<>();

    File graveFile = new File(this.getDataFolder(), "graves");
    File voidFile = new File(this.getDataFolder(), "voidLocations");
    File voidDataFile = new File(this.getDataFolder(), "voidInventories");

    public static Graves instance;
    public VoidReceiver voidReceiverInstance;

    public static Graves getInstance() {
        return instance;
    }

    void loadGraves() {
        if (graveFile.exists() && graveFile.length() != 0) {
            try {
                var fr = new BufferedReader(new FileReader(graveFile));
                var saved = fr.lines().collect(Collectors.joining());
                var split = saved.split("\\|");
                for (int i = 0; i < split.length; i += 6) {
                    UUID u = UUID.fromString(split[i]);
                    double x = Double.parseDouble(split[i+1]);
                    double y = Double.parseDouble(split[i+2]);
                    double z = Double.parseDouble(split[i+3]);
                    World w = Bukkit.getWorld(u);
                    Location l = new Location(w, x, y, z);
                    String name = split[i + 4];
                    Inventory inv = inventoryFromBase64(split[i+5]);
                    gravesMap.put(l, inv);
                    gravesMapForFile.put(l, new Pair<>(name, inv));
                }

                fr.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void loadVoid() {
        if (voidDataFile.exists() && voidDataFile.length() != 0) {
            try {
                var fr = new BufferedReader(new FileReader(voidDataFile));
                var saved = fr.lines().collect(Collectors.joining());
                var split = saved.split("\\|");
                for (int i = 0; i < split.length; i += 2) {
                    UUID u = UUID.fromString(split[i]);
                    ArrayList<ItemStack> arr = itemListFromBase64(split[i+1]);
                    voidDeath.put(u, arr);
                }

                fr.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;

        if (!this.getDataFolder().exists())
            this.getDataFolder().mkdir();

        Objects.requireNonNull(this.getCommand("gravegui")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        voidReceiverInstance = new VoidReceiver();
        getServer().getPluginManager().registerEvents(voidReceiverInstance, this);

        voidReceiverInstance.registerVoidReceiver();
        voidReceiverInstance.registerVoidCharge();
        voidReceiverInstance.registerChargedPearl();


        loadGraves();
        loadVoid();
        voidReceiverInstance.loadData(voidFile);
    }

    void saveGraves() {
        if (!graveFile.exists()) {
            try {
                graveFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            var fw = new BufferedWriter(new FileWriter(graveFile));
            gravesMapForFile.forEach((l, t) -> {
                try {
                    String location = "" + l.getWorld().getUID() + '|' + l.getX() + '|' + l.getY() + '|' + l.getZ();
                    fw.write(location + '|');
                    fw.write(t.left() + '|');
                    var deathInv = t.right();
                    fw.write(inventoryToBase64(deathInv, t.left()) + '|');
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            fw.flush();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void saveVoid() {
        if (!voidDataFile.exists()) {
            try {
                voidDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            var fw = new BufferedWriter(new FileWriter(voidDataFile));
            voidDeath.forEach((u, s) -> {
                try {
                    fw.write(u.toString()+'|');
                    fw.write(itemListToBase64(s)+'|');
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            fw.flush();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        saveGraves();
        saveVoid();
        voidReceiverInstance.saveData(voidFile);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (e.getDrops().isEmpty())
            return;

        var deathInv = new ArrayList<>(e.getDrops());
        e.getDrops().clear();

        if (e.getEntity().getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID) {
            if (voidDeath.containsKey(e.getPlayer().getUniqueId())) {
                var arr = voidDeath.get(e.getPlayer().getUniqueId());
                arr.addAll(deathInv);
                voidDeath.put(e.getPlayer().getUniqueId(), arr);
            } else
                voidDeath.put(e.getPlayer().getUniqueId(), deathInv);
            return;
        }


        var l = e.getPlayer().getLocation().toBlockLocation();
        l.setPitch(0);
        l.setYaw(0);
        while (!l.getBlock().isEmpty())
            l.add(0, 1, 0);

        l.getBlock().setType(Material.ENDER_CHEST);
        if (l.getWorld().getMaxHeight() <= l.getY()) {
            deathInv.forEach((i) -> e.getDrops().add(i));
            var deathLoc = e.getEntity().getLocation();
            String location = String.valueOf(Math.round(deathLoc.getX())) + ' ' + Math.round(deathLoc.getY()) + ' ' + Math.round(deathLoc.getZ());
            e.getEntity().sendMessage(Component.text("No grave could be spawned, your items dropped at ")
                    .append(Component.text("[" + location + "]")
                            .hoverEvent(HoverEvent.showText(Component.text("Click to copy!")))
                            .color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.copyToClipboard(location))));
            return;
        }

        var name = "Grave of " + e.getPlayer().getName();

        var inv = Bukkit.createInventory(null, 54, Component.text(name).color(NamedTextColor.BLACK));
        for (ItemStack i : deathInv)
            inv.addItem(i);
        e.getPlayer().openInventory(inv);

        gravesMap.put(l, inv);
        gravesMapForFile.put(l, new Pair<>(name, inv));
        String location = String.valueOf(l.getX()) + ' ' + l.getY() + ' ' + l.getZ();
        e.getEntity().sendMessage(Component.text("Your grave is located at ")
                                            .append(Component.text("[" + location + "]")
                                                             .hoverEvent(HoverEvent.showText(Component.text("Click to copy!")))
                                                             .color(NamedTextColor.GREEN)
                                                             .clickEvent(ClickEvent.copyToClipboard(location))));
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent e) {
        voidReceiverInstance.onBlockInteract(e, voidDeath);
        if (e.hasBlock() && e.getClickedBlock().getType() == Material.ENDER_CHEST) {
            var l = e.getClickedBlock().getLocation();
            if (gravesMap.containsKey(l)) {
                e.setCancelled(true);
                e.getPlayer().openInventory(gravesMap.get(l));
                openedGraveMap.put(e.getPlayer(), l);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        voidReceiverInstance.onCloseInventory(e, voidDeath);
        if (e.getView().title().toString().contains("Grave") && e.getView().title().color() == NamedTextColor.BLACK) {
                Player p = (Player) e.getPlayer();
                var l = openedGraveMap.get(p);
                openedGraveMap.remove(p);

                if (e.getInventory().isEmpty()) {
                    l.getBlock().setType(Material.AIR);
                    gravesMap.remove(l);
                    gravesMapForFile.remove(l);
                }
            }
    }

    @EventHandler
    public void onBlockDestroy(BlockDestroyEvent e) {
        if (gravesMap.containsKey(e.getBlock().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (gravesMap.containsKey(e.getBlock().getLocation()))
            e.setCancelled(true);
    }

    @NotNull
    private ArrayList<Block> getGravesContained(BlockExplodeEvent e) {
        var found = new ArrayList<Block>();
        for (Block b : e.blockList()) {
            if (gravesMap.containsKey(b.getLocation())) {
                found.add(b);
                break;
            }
        }
        return found;
    }

    @NotNull
    private ArrayList<Block> getGravesContained(EntityExplodeEvent e) {
        var found = new ArrayList<Block>();
        for (Block b : e.blockList()) {
            if (gravesMap.containsKey(b.getLocation())) {
                found.add(b);
                break;
            }
        }
        return found;
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        ArrayList<Block> found = getGravesContained(e);
        e.blockList().removeIf(found::contains);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        ArrayList<Block> found = getGravesContained(e);
        e.blockList().removeIf(found::contains);
    }
}
