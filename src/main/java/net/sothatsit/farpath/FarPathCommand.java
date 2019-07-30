package net.sothatsit.farpath;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Executes FarPath commands entered by players.
 *
 * @author Paddy Lamont
 */
public class FarPathCommand implements CommandExecutor {

    private final FarPath main;

    public FarPathCommand(FarPath main) {
        this.main = main;
    }

    private boolean showHelp(CommandSender sender) {
        sender.sendMessage("Usage: /farpath [surfaces]");
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("You must be a player to run this command");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0)
            return showHelp(sender);

        if (args[0].equalsIgnoreCase("nodes"))
            return nodes(player);

        if (args[0].equalsIgnoreCase("path"))
            return path(player);

        if (args[0].equalsIgnoreCase("chunk"))
            return chunk(player);

        if (args[0].equalsIgnoreCase("connections"))
            return connections(player);

        return showHelp(sender);
    }

    private boolean nodes(Player player) {
        main.getWorld(player.getWorld()).debugNodes();
        return true;
    }

    private boolean path(Player player) {
        Block from = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        Block to = player.getTargetBlockExact(128);

        if (!main.getWorld(player.getWorld()).debugPath(from, to)) {
            player.sendMessage("Could not find a path between blocks");
        }
        return true;
    }

    private boolean chunk(Player player) {
        main.getWorld(player.getWorld()).debugDisplay(player.getLocation().getChunk());
        return true;
    }

    private boolean connections(Player player) {
        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        boolean onSurface = main.getWorld(player.getWorld()).debugConnections(block);
        if (!onSurface) {
            player.sendMessage("You are not standing on a walkable surface");
        }
        return true;
    }
}
