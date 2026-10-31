package com.chaossmp.chaosspawn;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChaosSpawn extends JavaPlugin implements Listener {

private String worldName;
private int minX, minY, minZ;
private int maxX, maxY, maxZ;
private boolean protectionEnabled;

private String color(String text) {
    return ChatColor.translateAlternateColorCodes('&', text);
}

@Override
public void onEnable() {
    saveDefaultConfig();
    loadRegion();

    getServer().getPluginManager().registerEvents(this, this);
    getCommand("chaosspawn").setExecutor(this);

    getLogger().info("ChaosSpawn enabled!");
}

private void loadRegion() {
    worldName = getConfig().getString("region.world", "");
    protectionEnabled = getConfig().getBoolean("region.enabled", false);

    minX = getConfig().getInt("region.min.x");
    minY = getConfig().getInt("region.min.y");
    minZ = getConfig().getInt("region.min.z");

    maxX = getConfig().getInt("region.max.x");
    maxY = getConfig().getInt("region.max.y");
    maxZ = getConfig().getInt("region.max.z");
}

private boolean isProtected(Location location) {
    if (!protectionEnabled || location.getWorld() == null) {
        return false;
    }

    if (!location.getWorld().getName().equals(worldName)) {
        return false;
    }

    int x = location.getBlockX();
    int y = location.getBlockY();
    int z = location.getBlockZ();

    return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
}

private boolean canBypass(Player player) {
    return player.hasPermission("chaosspawn.bypass");
}

private void deny(Player player) {
    player.sendMessage(color(
            getConfig().getString(
                    "messages.protected",
                    "&cЗдесь действует защита спавна!"
            )
    ));
}

private void saveRegion(Player player, Region region) {
    var min = region.getMinimumPoint();
    var max = region.getMaximumPoint();

    worldName = player.getWorld().getName();

    minX = min.getBlockX();
    minY = min.getBlockY();
    minZ = min.getBlockZ();

    maxX = max.getBlockX();
    maxY = max.getBlockY();
    maxZ = max.getBlockZ();

    protectionEnabled = true;

    getConfig().set("region.enabled", true);
    getConfig().set("region.world", worldName);

    getConfig().set("region.min.x", minX);
    getConfig().set("region.min.y", minY);
    getConfig().set("region.min.z", minZ);

    getConfig().set("region.max.x", maxX);
    getConfig().set("region.max.y", maxY);
    getConfig().set("region.max.z", maxZ);

    saveConfig();
}

@Override
public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
) {
    if (!(sender instanceof Player player)) {
        sender.sendMessage("Эту команду может использовать только игрок.");
        return true;
    }

    if (args.length == 1 && args[0].equalsIgnoreCase("set")) {
        if (!player.hasPermission("chaosspawn.admin")) {
            player.sendMessage(color("&cУ тебя нет прав."));
            return true;
        }

        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            player.sendMessage(color("&cWorldEdit не установлен."));
            return true;
        }

        try {
            var session = WorldEdit.getInstance()
                    .getSessionManager()
                    .get(BukkitAdapter.adapt(player));

            Region region = session.getSelection(
                    BukkitAdapter.adapt(player.getWorld())
            );

            saveRegion(player, region);

            player.sendMessage(color("&aТерритория спавна сохранена!"));
            player.sendMessage(color("&7Мир: &f" + worldName));
            player.sendMessage(color("&7От: &f" + minX + " " + minY + " " + minZ));
            player.sendMessage(color("&7До: &f" + maxX + " " + maxY + " " + maxZ));

        } catch (Exception exception) {
            player.sendMessage(color("&cНе удалось получить выделение WorldEdit."));
            player.sendMessage(color("&7Сначала выдели два угла через //wand."));
        }

        return true;
    }

    if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
        if (!player.hasPermission("chaosspawn.admin")) {
            player.sendMessage(color("&cУ тебя нет прав."));
            return true;
        }

        reloadConfig();
        loadRegion();

        player.sendMessage(color("&aКонфигурация ChaosSpawn перезагружена."));
        return true;
    }

    player.sendMessage(color("&bChaosSpawn &7— команды:"));
    player.sendMessage(color("&f/chaosspawn set &7— сохранить выделение"));
    player.sendMessage(color("&f/chaosspawn reload &7— перезагрузить конфиг"));

    return true;
}

@EventHandler
public void onPvP(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player victim)) {
        return;
    }

    Entity damager = event.getDamager();
    Player attacker = null;

    if (damager instanceof Player player) {
        attacker = player;
    } else if (damager instanceof Projectile projectile
            && projectile.getShooter() instanceof Player player) {
        attacker = player;
    }

    if (attacker != null
            && isProtected(victim.getLocation())
            && !canBypass(attacker)) {
        event.setCancelled(true);
        deny(attacker);
    }
}

@EventHandler
public void onBreak(BlockBreakEvent event) {
    if (isProtected(event.getBlock().getLocation())
            && !canBypass(event.getPlayer())) {
        event.setCancelled(true);
        deny(event.getPlayer());
    }
}

@EventHandler
public void onPlace(BlockPlaceEvent event) {
    if (isProtected(event.getBlock().getLocation())
            && !canBypass(event.getPlayer())) {
        event.setCancelled(true);
        deny(event.getPlayer());
    }
}

@EventHandler
public void onInteract(PlayerInteractEvent event) {
    if (!getConfig().getBoolean("protection.interact", true)) {
        return;
    }

    Location location = event.getClickedBlock() == null
            ? event.getPlayer().getLocation()
            : event.getClickedBlock().getLocation();

    if (isProtected(location) && !canBypass(event.getPlayer())) {
        event.setCancelled(true);
        deny(event.getPlayer());
    }
}

@EventHandler
public void onBucketEmpty(PlayerBucketEmptyEvent event) {
    if (isProtected(event.getBlock().getLocation())
            && !canBypass(event.getPlayer())) {
        event.setCancelled(true);
        deny(event.getPlayer());
    }
}

@EventHandler
public void onBucketFill(PlayerBucketFillEvent event) {
    if (isProtected(event.getBlock().getLocation())
            && !canBypass(event.getPlayer())) {
        event.setCancelled(true);
        deny(event.getPlayer());
    }
}

@EventHandler
public void onEntityExplode(EntityExplodeEvent event) {
    event.blockList().removeIf(
            block -> isProtected(block.getLocation())
    );
}

@EventHandler
public void onBlockExplode(BlockExplodeEvent event) {
    event.blockList().removeIf(
            block -> isProtected(block.getLocation())
    );
}

}
