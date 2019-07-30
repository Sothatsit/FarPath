package net.sothatsit.farpath.preprocessing;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Super simple location of a chunk within a world.
 *
 * @author Paddy Lamont
 */
public class ChunkLoc {

    public final int x;
    public final int z;

    public ChunkLoc(Chunk chunk) {
        this(chunk.getX(), chunk.getZ());
    }

    public ChunkLoc(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public ChunkLoc getRelative(int dx, int dz) {
        return new ChunkLoc(x + dx, z + dz);
    }

    public int getBlockX() {
        return x * 16;
    }

    public int getBlockZ() {
        return z * 16;
    }

    /**
     * @return A Block in this chunk at the given location.
     */
    public Block getBlock(World world, int x, int y, int z) {
        return world.getBlockAt(getBlockX() + x, y, getBlockZ() + z);
    }

    @Override
    public String toString() {
        return "ChunkLoc(" + x + ", " + z + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !getClass().equals(obj.getClass()))
            return false;

        ChunkLoc other = (ChunkLoc) obj;

        return x == other.x && z == other.z;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(x) ^ (7 * Integer.hashCode(z));
    }
}
