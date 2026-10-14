package de.homes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class HomesPlugin extends JavaPlugin implements TabExecutor {

    private File file;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        file = new File(getDataFolder(), "homes.yml");
        data = YamlConfiguration.loadConfiguration(file);

        for (String name : List.of("sethome", "home", "delhome", "homes")) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
        getLogger().info("Homes aktiviert.");
    }

    // ------------------------------------------------------------- Hilfsmethoden

    private String base(Player p) {
        return "homes." + p.getUniqueId();
    }

    private Set<String> homeNames(Player p) {
        ConfigurationSection s = data.getConfigurationSection(base(p));
        return s == null ? new TreeSet<>() : new TreeSet<>(s.getKeys(false));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            getLogger().severe("Konnte homes.yml nicht speichern: " + e.getMessage());
        }
    }

    private void msg(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }

    // -------------------------------------------------------------------- Befehle

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diese Befehle nutzen.");
            return true;
        }
        if (!player.hasPermission("homes.use")) {
            msg(player, "Dazu hast du keine Rechte.", NamedTextColor.RED);
            return true;
        }

        switch (cmd.getName().toLowerCase()) {
            case "sethome" -> setHome(player, args);
            case "home" -> goHome(player, args);
            case "delhome" -> delHome(player, args);
            case "homes" -> listHomes(player);
        }
        return true;
    }

    private void setHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0].toLowerCase() : "home";
        if (!name.matches("[a-z0-9_-]{1,16}")) {
            msg(player, "Ungültiger Name. Erlaubt: Buchstaben, Zahlen, _ und - (max. 16 Zeichen).", NamedTextColor.RED);
            return;
        }

        Set<String> names = homeNames(player);
        int max = getConfig().getInt("max-homes", 5);
        if (!names.contains(name) && names.size() >= max && !player.hasPermission("homes.unlimited")) {
            msg(player, "Du hast schon " + max + " Homes. Lösche eins mit /delhome <name>.", NamedTextColor.RED);
            return;
        }

        Location loc = player.getLocation();
        String path = base(player) + "." + name;
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", (double) loc.getYaw());
        data.set(path + ".pitch", (double) loc.getPitch());
        save();
        msg(player, "Home \"" + name + "\" wurde gesetzt.", NamedTextColor.GREEN);
    }

    private void goHome(Player player, String[] args) {
        Set<String> names = homeNames(player);
        if (names.isEmpty()) {
            msg(player, "Du hast noch kein Home. Setze eins mit /sethome.", NamedTextColor.RED);
            return;
        }

        String name;
        if (args.length > 0) {
            name = args[0].toLowerCase();
        } else if (names.contains("home")) {
            name = "home";
        } else if (names.size() == 1) {
            name = names.iterator().next();
        } else {
            msg(player, "Welches Home? Deine Homes: " + String.join(", ", names), NamedTextColor.YELLOW);
            return;
        }

        ConfigurationSection h = data.getConfigurationSection(base(player) + "." + name);
        if (h == null) {
            msg(player, "Das Home \"" + name + "\" gibt es nicht. Deine Homes: " + String.join(", ", names), NamedTextColor.RED);
            return;
        }

        World world = Bukkit.getWorld(h.getString("world", ""));
        if (world == null) {
            msg(player, "Die Welt dieses Homes ist nicht geladen.", NamedTextColor.RED);
            return;
        }

        Location loc = new Location(world, h.getDouble("x"), h.getDouble("y"), h.getDouble("z"),
                (float) h.getDouble("yaw"), (float) h.getDouble("pitch"));
        player.teleport(loc);
        msg(player, "Willkommen zu Hause: " + name, NamedTextColor.GREEN);
    }

    private void delHome(Player player, String[] args) {
        if (args.length < 1) {
            msg(player, "Benutzung: /delhome <name>", NamedTextColor.YELLOW);
            return;
        }
        String name = args[0].toLowerCase();
        if (!homeNames(player).contains(name)) {
            msg(player, "Das Home \"" + name + "\" gibt es nicht.", NamedTextColor.RED);
            return;
        }
        data.set(base(player) + "." + name, null);
        save();
        msg(player, "Home \"" + name + "\" wurde gelöscht.", NamedTextColor.GREEN);
    }

    private void listHomes(Player player) {
        Set<String> names = homeNames(player);
        if (names.isEmpty()) {
            msg(player, "Du hast noch kein Home. Setze eins mit /sethome.", NamedTextColor.YELLOW);
            return;
        }
        int max = getConfig().getInt("max-homes", 5);
        String limit = player.hasPermission("homes.unlimited") ? "" : " (" + names.size() + "/" + max + ")";
        msg(player, "Deine Homes" + limit + ": " + String.join(", ", names), NamedTextColor.AQUA);
    }

    // ---------------------------------------------------------------- Tab-Vervollständigung

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player player) || args.length != 1) return out;
        String n = cmd.getName().toLowerCase();
        if (n.equals("home") || n.equals("delhome") || n.equals("sethome")) {
            for (String s : homeNames(player)) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        }
        return out;
    }
}
