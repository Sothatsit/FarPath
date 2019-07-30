package net.sothatsit.farpath.preprocessing;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Represents a connection direction between two blocks in a surface.
 *
 * @author Paddy Lamont
 */
public enum SurfaceConnection {

    NORTH(BlockFace.NORTH),
    EAST(BlockFace.EAST),
    SOUTH(BlockFace.SOUTH),
    WEST(BlockFace.WEST),

    NORTH_WEST(BlockFace.NORTH_WEST),
    NORTH_EAST(BlockFace.NORTH_EAST),
    SOUTH_WEST(BlockFace.SOUTH_WEST),
    SOUTH_EAST(BlockFace.SOUTH_EAST),

    UP_NORTH(BlockFace.NORTH, 1),
    UP_EAST(BlockFace.EAST, 1),
    UP_SOUTH(BlockFace.SOUTH, 1),
    UP_WEST(BlockFace.WEST, 1),

    UP_NORTH_WEST(BlockFace.NORTH_WEST, 1),
    UP_NORTH_EAST(BlockFace.NORTH_EAST, 1),
    UP_SOUTH_WEST(BlockFace.SOUTH_WEST, 1),
    UP_SOUTH_EAST(BlockFace.SOUTH_EAST, 1),

    DOWN_NORTH(BlockFace.NORTH, -1),
    DOWN_EAST(BlockFace.EAST, -1),
    DOWN_SOUTH(BlockFace.SOUTH, -1),
    DOWN_WEST(BlockFace.WEST, -1),

    DOWN_NORTH_WEST(BlockFace.NORTH_WEST, -1),
    DOWN_NORTH_EAST(BlockFace.NORTH_EAST, -1),
    DOWN_SOUTH_WEST(BlockFace.SOUTH_WEST, -1),
    DOWN_SOUTH_EAST(BlockFace.SOUTH_EAST, -1),

    UP(BlockFace.UP),
    DOWN(BlockFace.DOWN);

    private final int mask;
    private final int dx;
    private final int dy;
    private final int dz;
    private final double distance;
    private SurfaceConnection opposite = null;

    SurfaceConnection(BlockFace face) {
        this(face.getModX(), face.getModY(), face.getModZ());
    }


    SurfaceConnection(BlockFace face, int dy) {
        this(face.getModX(), dy, face.getModZ());
    }

    SurfaceConnection(int dx, int dy, int dz) {
        this.mask = 1 << ordinal();
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    public int getMask() {
        return mask;
    }

    public boolean inMask(int mask) {
        return (mask & this.mask) != 0;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public int getDz() {
        return dz;
    }

    public double getDistance() {
        return distance;
    }

    public Block get(Block block) {
        return block.getRelative(dx, dy, dz);
    }

    public BlockLoc get(BlockLoc loc) {
        return loc.add(dx, dy, dz);
    }

    public SurfaceConnection getOpposite() {
        if (opposite == null) {
            opposite = get(-dx, -dy, -dz);
        }

        return opposite;
    }

    public static SurfaceConnection get(int dx, int dy, int dz) {
        for (SurfaceConnection connection : values()) {
            if (connection.dx == dx && connection.dy == dy && connection.dz == dz)
                return connection;
        }
        throw new IllegalArgumentException("There are no SurfaceConnections for offset " + dx + ", " + dy + ", " + dz);
    }
}
