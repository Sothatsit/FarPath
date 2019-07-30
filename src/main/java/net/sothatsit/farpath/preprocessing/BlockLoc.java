package net.sothatsit.farpath.preprocessing;

import org.bukkit.block.Block;

/**
 * Super simple location of a block within a region.
 *
 * @author Paddy Lamont
 */
public class BlockLoc {

    public final int x;
    public final int y;
    public final int z;

    public BlockLoc(Block block) {
        this(block.getX(), block.getY(), block.getZ());
    }

    public BlockLoc(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockLoc subtract(Block block) {
        return add(-block.getX(), -block.getY(), -block.getZ());
    }

    public BlockLoc add(Block block) {
        return add(block.getX(), block.getY(), block.getZ());
    }

    public BlockLoc add(int dx, int dy, int dz) {
        return new BlockLoc(x + dx, y + dy, z + dz);
    }

    public double distanceSquared(double x, double y, double z) {
        double dx = x - this.x;
        double dy = y - this.y;
        double dz = z - this.z;
        return dx*dx + dy*dy + dz*dz;
    }

    public double distance(BlockLoc other) {
        int dx = other.x - x;
        int dy = other.y - y;
        int dz = other.z - z;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private static int signum(int value) {
        return Integer.compare(value, 0);
    }

    public ChunkLoc toChunkLoc() {
        return new ChunkLoc(signum(x) * Math.abs(x) / 16, signum(z) * Math.abs(z) / 16);
    }

    @Override
    public String toString() {
        return "BlockLoc(" + x + ", " + y + ", " + z + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !getClass().equals(obj.getClass()))
            return false;

        BlockLoc other = (BlockLoc) obj;

        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(x) ^ (7 * Integer.hashCode(y)) ^ (47 * Integer.hashCode(z));
    }
}
