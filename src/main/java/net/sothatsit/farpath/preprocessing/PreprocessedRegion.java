package net.sothatsit.farpath.preprocessing;

import net.sothatsit.farpath.util.PackedBooleanArray;
import net.sothatsit.farpath.util.PriorityQueueLinked;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.*;

/**
 * Allows the pre-processing of a chunk to improve efficiency of path-finding.
 *
 * @author Paddy Lamont
 */
public class PreprocessedRegion {

    private final Block anchor;
    private final int width;
    private final int height;
    private final int depth;
    private final int blockCount;

    private PackedBooleanArray passable;
    private PackedBooleanArray solid;
    private PackedBooleanArray freeSpace;
    private PackedBooleanArray walkable;
    private short[] surfaces;
    private int[] connectionMasks;

    public PreprocessedRegion(Chunk chunk) {
        this(chunk.getBlock(0, 0, 0), 16, chunk.getWorld().getMaxHeight(), 16);
    }

    public PreprocessedRegion(Block anchor, int width, int height, int depth) {
        this.anchor = anchor;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.blockCount = width * height * depth;
    }

    public Block getAnchor() {
        return anchor;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public BlockLoc toBlockLoc(Block block) {
        return new BlockLoc(
                block.getX() - anchor.getX(),
                block.getY() - anchor.getY(),
                block.getZ() - anchor.getZ()
        );
    }

    public Block getBlock(BlockLoc loc) {
        return getBlock(loc.x, loc.y, loc.z);
    }

    public Block getBlock(int x, int y, int z) {
        return anchor.getRelative(x, y, z);
    }

    /**
     * It is assumed that both blocks have already been checked to be walkable.
     *
     * @return Whether it is possible to walk directly between the two given locations.
     *         i.e. Can walk between them without crossing other blocks.
     */
    private boolean determineCanWalkBetween(int x1, int y1, int z1,
                                            int x2, int y2, int z2) {

        int relX = x2 - x1;
        int relY = y2 - y1;
        int relZ = z2 - z1;

        int absRelX = Math.abs(relX);
        int absRelY = Math.abs(relY);
        int absRelZ = Math.abs(relZ);

        // Can't be connected if they are not next to each other
        if (absRelX > 1 || absRelY > 1 || absRelZ > 1)
            return false;

        // Check for vertical traversal
        if (relX == 0 && relZ == 0)
            return true;

        // If they are on the same vertical level
        if (relY == 0) {
            // Check for straight line walk
            if ((absRelX == 1 && relZ == 0) || (absRelZ == 1 && relX == 0))
                return true;

            // Check that we have space for walking diagonally
            return freeSpace.get(index(x1, y1, z2)) && freeSpace.get(index(x2, y2, z1));
        }

        // Swap 1 & 2 to ensure that x1, y1, z1 is always at a lower elevation
        if (y1 > y2) {
            relX *= -1;
            relY *= -1;
            relZ *= -1;

            x1 = x2;
            y1 = y2;
            z1 = z2;

            x2 = x1 + relX;
            y2 = y1 + relY;
            z2 = z1 + relZ;
        }

        // Check that we're not going to hit our head
        if (!freeSpace.get(index(x1, y1 + 1, z1)))
            return false;

        // Check for straight line walk
        if ((absRelX == 1 && relZ == 0) || (absRelZ == 1 && relX == 0))
            return true;

        // Check that we have space for walking diagonally
        return freeSpace.get(index(x1, y2, z2)) && freeSpace.get(index(x2, y2, z1));
    }

    /**
     * Starts at the given location and flood fills {@param surfaceID} throughout
     * the surfaces array as long as blocks are connected to the surface.
     */
    private void floodFillSurface(short surfaceID, int x, int y, int z) {

        Queue<BlockLoc> toProcess = new LinkedList<>();
        int[] checkedConnectionMasks = new int[blockCount];

        toProcess.add(new BlockLoc(x, y, z));
        surfaces[index(x, y, z)] = surfaceID;

        while (!toProcess.isEmpty()) {
            BlockLoc loc = toProcess.poll();

            x = loc.x;
            y = loc.y;
            z = loc.z;

            int index = index(x, y, z);

            for (SurfaceConnection connection : SurfaceConnection.values()) {
                int x2 = x + connection.getDx();
                int y2 = y + connection.getDy();
                int z2 = z + connection.getDz();

                // We only want to process blocks within this region
                if (x2 < 0 || y2 < 0 || z2 < 0 || x2 >= width || y2 >= height || z2 >= depth)
                    continue;

                int index2 = index(x2, y2, z2);
                if (!walkable.get(index2))
                    continue;

                // Check that we haven't already checked for a connection between the blocks
                if ((checkedConnectionMasks[index] & connection.getMask()) != 0)
                    continue;

                // It will be the opposite connection for the other block to this block
                SurfaceConnection connection2 = connection.getOpposite();

                // Mark that we no longer need to check the connection between these two blocks
                checkedConnectionMasks[index] |= connection.getMask();
                checkedConnectionMasks[index2] |= connection2.getMask();

                // Check that we can walk between the blocks
                if (!determineCanWalkBetween(x, y, z, x2, y2, z2))
                    continue;

                // Mark that there is a connection between the two blocks
                connectionMasks[index] |= connection.getMask();
                connectionMasks[index2] |= connection2.getMask();

                // If its already been registered to a surface we don't need to process it again
                if (surfaces[index2] != 0)
                    continue;

                // Register the connected block as part of the surface
                surfaces[index2] = surfaceID;

                // Mark that we need to check the connected block for further connections
                toProcess.add(new BlockLoc(x2, y2, z2));
            }
        }
    }

    /**
     * Rebuild all the pre-computed information about this chunk.
     */
    public void rebuild() {
        // Build a couple arrays containing the characteristics of blocks
        this.passable = new PackedBooleanArray(blockCount);
        this.solid = new PackedBooleanArray(blockCount);
        for (int x = 0; x < width; ++x) {
            for (int z = 0; z < depth; ++z) {
                for (int y = 0; y < height; ++y) {
                    int index = index(x, y, z);
                    Block block = getBlock(x, y, z);

                    passable.set(index, block.isPassable() && !block.isLiquid());
                    solid.set(index, !block.isPassable());
                }
            }
        }

        // Build an array of which blocks are free for the player to pass above,
        // and build an array of which blocks can be walked upon
        this.freeSpace = new PackedBooleanArray(blockCount);
        this.walkable = new PackedBooleanArray(blockCount);

        for (int x = 0; x < width; ++x) {
            for (int z = 0; z < depth; ++z) {
                // TODO : This assumes that the region goes up to max height and that above max height is free space.
                int index2Above = -1;
                int index1Above = -1;

                for (int y = height - 1; y >= 0; --y) {
                    int index = index(x, y, z);

                    boolean above1Free = (index1Above < 0 || passable.get(index1Above));
                    boolean above2Free = (index2Above < 0 || passable.get(index2Above));

                    freeSpace.set(index, above1Free && above2Free);
                    walkable.set(index, freeSpace.get(index) && solid.get(index));

                    index2Above = index1Above;
                    index1Above = index;
                }
            }
        }

        // Build an array containing unique surface IDs for each group of walkable blocks that are connected
        short nextSurfaceID = 1;
        this.surfaces = new short[blockCount];
        this.connectionMasks = new int[blockCount];

        for (int x = 0; x < width; ++x) {
            for (int z = 0; z < depth; ++z) {
                for (int y = 0; y < height; ++y) {
                    int index = index(x, y, z);

                    // If we've already assigned this block a surface ID, skip it
                    if (!walkable.get(index) || surfaces[index] != 0)
                        continue;

                    floodFillSurface(nextSurfaceID++, x, y, z);
                }
            }
        }
    }

    /**
     * @return An ID unique to the surface of blocks that the given location belongs to in this chunk.
     *         If the location does not belong to a surface, 0 will be returned.
     */
    public int getSurfaceID(Block block) {
        return getSurfaceIDByWorldLoc(
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    /**
     * @return An ID unique to the surface of blocks that the given location belongs to in this chunk.
     *         If the location does not belong to a surface, 0 will be returned.
     */
    public int getSurfaceID(Location location) {
        return getSurfaceIDByWorldLoc(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    /**
     * Get a surface ID using a location relative to the world, not relative to this region.
     *
     * @return An ID unique to the surface of blocks that the given location belongs to in this chunk.
     *         If the location does not belong to a surface, 0 will be returned.
     */
    public int getSurfaceIDByWorldLoc(int x, int y, int z) {
        x -= anchor.getX();
        y -= anchor.getY();
        z -= anchor.getZ();
        return getSurfaceID(x, y, z);
    }

    /**
     * Get a surface ID using a location relative to the world, not relative to this region.
     *
     * @return An ID unique to the surface of blocks that the given location belongs to in this chunk.
     *         If the location does not belong to a surface, 0 will be returned.
     */
    public int getSurfaceIDByWorldLoc(BlockLoc loc) {
        return getSurfaceIDByWorldLoc(loc.x, loc.y, loc.z);
    }

    /**
     * @return An ID unique to the surface of blocks that the given location belongs to in this chunk.
     *         If the location does not belong to a surface, 0 will be returned.
     */
    public int getSurfaceID(BlockLoc loc) {
        return getSurfaceID(loc.x, loc.y, loc.z);
    }

    /**
     * @return An ID unique to the surface of blocks that the given location belongs to in this chunk.
     *         If the location does not belong to a surface, 0 will be returned.
     */
    public int getSurfaceID(int x, int y, int z) {
        return surfaces[index(x, y, z)];
    }

    /**
     * @return A mask to be used to check for surface connections.
     */
    public int getConnectionMask(Block block) {
        return getConnectionMask(
                block.getX() - anchor.getX(),
                block.getY() - anchor.getY(),
                block.getZ() - anchor.getZ()
        );
    }

    /**
     * @return A mask to be used to check for surface connections.
     */
    public int getConnectionMask(int x, int y, int z) {
        return connectionMasks[index(x, y, z)];
    }

    /**
     * @return Whether the given world location falls within this region.
     */
    public boolean contains(int x, int y, int z) {
        x -= anchor.getX();
        y -= anchor.getY();
        z -= anchor.getZ();
        return x >= 0 && y >= 0 && z >= 0 && x < width && y < height && z < depth;
    }

    /**
     * @return A unique one-dimensional index representing {@param loc} in this region.
     */
    private int index(BlockLoc loc) {
        return index(loc.x, loc.y, loc.z);
    }

    /**
     * @return A unique one-dimensional index representing given location in this region.
     */
    private int index(int x, int y, int z) {
        // TODO : Need a fast and slow version of this
        // i.e. one version that ensures the location falls within this region,
        //      and one that doesn't for speed when we know the check is unnecessary
        return x + z * width + y * width * depth;
    }

    /**
     * @return The location that {@param index} refers to in this region.
     */
    public BlockLoc reverseIndex(int index) {
        int x = index % width;
        int z = (index / width) % depth;
        int y = index / width / depth;
        return new BlockLoc(x, y, z);
    }

    /**
     * Reconstructs the shortest path from start to end using the array
     * of previous nodes in the shortest paths, {@param from}.
     *
     * @return A List of BlockLocs on the path from {@param start} to {@param end}, including the start and end points.
     */
    private List<BlockLoc> reconstructPath(int[] from, BlockLoc start, BlockLoc end) {
        List<BlockLoc> path = new ArrayList<>();

        int currentIndex = index(end);
        BlockLoc currentLoc = end;
        path.add(end);

        while (!start.equals(currentLoc)) {
            int fromIndex = from[currentIndex];
            BlockLoc fromLoc = reverseIndex(fromIndex);

            path.add(fromLoc);

            currentIndex = fromIndex;
            currentLoc = fromLoc;
        }

        Collections.reverse(path);
        return path;
    }

    /**
     * Find a path within this region from {@param start} to {@param end}.
     *
     * @return A List of blocks to traverse to get from {@param start}
     *         to {@param end}, or null if no path could be found.
     */
    public List<BlockLoc> findPath(BlockLoc start, BlockLoc end) {
        int startSurface = getSurfaceID(start);
        int endSurface = getSurfaceID(end);

        if (startSurface == 0 || endSurface == 0)
            throw new IllegalArgumentException("start and end must both be on a surface in the region");

        if (startSurface != endSurface)
            return null;

        // TODO : These arrays can likely be re-used, and additionally are likely a lot larger
        //        than they need to be, as often surfaces won't span the whole of the chunk.

        // Keeps track of which nodes have been marked to be processed later
        boolean[] explored = new boolean[blockCount];

        // Keeps track of which nodes have already been processed
        boolean[] processed = new boolean[blockCount];

        // Contains the previous node in the shortest path currently known to each node, plus 1.
        // 0 represents that no path to the node has been discovered.
        int[] from = new int[blockCount];

        // Contains the length of the shortest path currently known to each node.
        // 0 represents that no path to the node has been discovered.
        double[] foundCosts = new double[blockCount];

        // Maintains which nodes are next to be processed
        PriorityQueueLinked<BlockLoc> toProcess = new PriorityQueueLinked<>();

        toProcess.add(start, 0);

        while (!toProcess.isEmpty()) {
            BlockLoc loc = toProcess.poll();

            if (loc.equals(end))
                return reconstructPath(from, start, end);

            int index = index(loc);
            int connectionMask = connectionMasks[index];
            double cost = foundCosts[index];

            processed[index] = true;

            for (SurfaceConnection connection : SurfaceConnection.values()) {
                if (!connection.inMask(connectionMask))
                    continue;

                int x = loc.x + connection.getDx();
                int y = loc.y + connection.getDy();
                int z = loc.z + connection.getDz();
                int connectedIndex = index(x, y, z);

                if (processed[connectedIndex])
                    continue;

                boolean previouslyExplored = explored[connectedIndex];
                explored[connectedIndex] = true;

                double connectedCost = cost + connection.getDistance();
                double oldConnectedCost = foundCosts[connectedIndex];
                if (previouslyExplored && oldConnectedCost <= connectedCost)
                    continue;

                foundCosts[connectedIndex] = connectedCost;
                from[connectedIndex] = index;

                BlockLoc connectedLoc = new BlockLoc(x, y, z);
                double heuristic = connectedLoc.distance(end);

                // Negative priority as the queue prioritizes higher values first,
                // whereas we need to prioritize the lowest values first
                double priority = -(connectedCost + heuristic);

                if (previouslyExplored) {
                    toProcess.reprioritize(connectedLoc, priority);
                } else {
                    toProcess.add(connectedLoc, priority);
                }
            }
        }

        throw new IllegalStateException("Couldn't find path between two points on the same surface");
    }
}
