package net.sothatsit.farpath;

import net.sothatsit.farpath.preprocessing.PreprocessedWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The main entry point to the FarPath path-finding plugin.
 *
 * @author Paddy Lamont
 */
public class FarPath extends JavaPlugin {

    private final Map<UUID, PreprocessedWorld> worlds = new HashMap<>();

    @Override
    public void onEnable() {
        for (World world : Bukkit.getWorlds()) {
            worlds.put(world.getUID(), new PreprocessedWorld(this, world));
        }

        FarPathCommand command = new FarPathCommand(this);
        FarPathListener listener = new FarPathListener(this);

        notNull(getCommand("farpath")).setExecutor(command);
        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {

    }

    public PreprocessedWorld getWorld(World world) {
        PreprocessedWorld preprocessed = worlds.get(world.getUID());

        if (preprocessed == null) {
            preprocessed = new PreprocessedWorld(this, world);
            worlds.put(world.getUID(), preprocessed);
        }

        return preprocessed;
    }

    public void removeWorld(World world) {
        worlds.remove(world.getUID());
    }

    private static <T> T notNull(T value) {
        if (value == null)
            throw new NullPointerException();
        return value;
    }
}
