package net.sothatsit.farpath.preprocessing;

import net.sothatsit.farpath.FarPath;
import net.sothatsit.farpath.util.PriorityQueueLinked;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.*;

/**
 * Allows the pre-processing of worlds to allow more efficient path-finding.
 *
 * @author Paddy Lamont
 */
public class PreprocessedWorld {

    // Create a node at every boundary surfaceID that connects two or more surfaces in two adjacent chunks.
    // Use the average location of all surface blocks within these boundary surfaces as the location for the node.
    // When path-finding, add a node for the start position to the graph, and a node for the end position in the graph.
    // Then, path-find between these two nodes, using distances between nodes as the weights of the connections.
    //
    // This will give a large overview path, after which A* can be used to find paths between the nodes.
    // Still need to consider how to choose which boundary blocks in the nodes to choose to path-find to.
    // Perhaps just choose the closest.

    private final FarPath main;
    private final World world;
    private final Map<ChunkLoc, PreprocessedRegion> chunks;
    private final Map<ChunkLocPair, PreprocessedRegion> straightBoundaries;
    private final Map<ChunkLoc, Map<Integer, List<Node>>> nodes;

    public PreprocessedWorld(FarPath main, World world) {
        this.main = main;
        this.world = world;
        this.chunks = new HashMap<>();
        this.straightBoundaries = new HashMap<>();
        this.nodes = new HashMap<>();

        for (Chunk chunk : world.getLoadedChunks()) {
            add(chunk);
        }
    }

    private PreprocessedRegion createBoundary(World world, ChunkLocPair pair) {
        ChunkLoc smaller = pair.smaller;
        ChunkLoc larger = pair.larger;

        Block anchor;
        int width, depth;
        int height = world.getMaxHeight();

        if (smaller.x == larger.x) {
            anchor = smaller.getBlock(world, 0, 0, 15);
            width = 16;
            depth = 2;
        } else {
            anchor = smaller.getBlock(world, 15, 0, 0);
            width = 2;
            depth = 16;
        }

        return new PreprocessedRegion(anchor, width, height, depth);
    }

    private void generateBoundaryNodes(PreprocessedRegion boundary,
                                       PreprocessedRegion one,
                                       PreprocessedRegion two) {

        ChunkLoc chunk1 = new ChunkLoc(one.getAnchor().getChunk());
        ChunkLoc chunk2 = new ChunkLoc(two.getAnchor().getChunk());

        // TODO : Could keep track of the maximum surface ID in a PreprocessedRegion,
        //        and then this could become a simple array. Same for this.nodes.
        Map<Integer, Node> nodesByBoundaryID = new HashMap<>();

        Block anchor = boundary.getAnchor();
        for (int x = 0; x < boundary.getWidth(); ++x) {
            for (int z = 0; z < boundary.getDepth(); ++z) {
                for (int y = 0; y < boundary.getHeight(); ++y) {
                    int surfaceID = boundary.getSurfaceID(x, y, z);
                    if (surfaceID == 0)
                        continue;

                    Node node = nodesByBoundaryID.get(surfaceID);
                    if (node == null) {
                        node = new Node(chunk1, chunk2, surfaceID);
                        nodesByBoundaryID.put(surfaceID, node);
                    }

                    int rx = anchor.getX() + x;
                    int ry = anchor.getY() + y;
                    int rz = anchor.getZ() + z;

                    node.cumulativeX += rx;
                    node.cumulativeY += ry;
                    node.cumulativeZ += rz;
                    node.surfaceBlockCount += 1;

                    if (one.contains(rx, ry, rz)) {
                        int oneID = one.getSurfaceIDByWorldLoc(rx, ry, rz);
                        node.chunk1SurfaceIDs.add(oneID);
                    } else {
                        int twoID = two.getSurfaceIDByWorldLoc(rx, ry, rz);
                        node.chunk2SurfaceIDs.add(twoID);
                    }
                }
            }
        }

        // TODO : This whole idea of "representative blocks" needs to be replaced.
        //        It is a pain in the ass, and doesn't lead to worse paths.
        //        Ideally we'd be able to give a flexible goal in the A* within the region.
        for (int x = 0; x < boundary.getWidth(); ++x) {
            for (int z = 0; z < boundary.getDepth(); ++z) {
                for (int y = 0; y < boundary.getHeight(); ++y) {
                    int surfaceID = boundary.getSurfaceID(x, y, z);
                    if (surfaceID == 0)
                        continue;

                    int rx = anchor.getX() + x;
                    int ry = anchor.getY() + y;
                    int rz = anchor.getZ() + z;

                    boolean inOne = one.contains(rx, ry, rz);
                    Node node = nodesByBoundaryID.get(surfaceID);

                    if (inOne && node.representativeBlock1 == null) {
                        node.representativeBlock1 = new BlockLoc(rx, ry, rz);
                        continue;
                    } else if (!inOne && node.representativeBlock2 == null) {
                        node.representativeBlock2 = new BlockLoc(rx, ry, rz);
                        continue;
                    }

                    double nx = node.cumulativeX / node.surfaceBlockCount;
                    double ny = node.cumulativeY / node.surfaceBlockCount;
                    double nz = node.cumulativeZ / node.surfaceBlockCount;

                    double previousDistance;

                    if (inOne) {
                        previousDistance = node.representativeBlock1.distanceSquared(nx, ny, nz);
                    } else {
                        previousDistance = node.representativeBlock2.distanceSquared(nx, ny, nz);
                    }

                    double dx = rx - nx;
                    double dy = ry - ny;
                    double dz = rz - nz;
                    double newDistance = dx*dx + dy*dy + dz*dz;

                    if (newDistance >= previousDistance)
                        continue;

                    BlockLoc loc = new BlockLoc(rx, ry, rz);
                    if (inOne) {
                        node.representativeBlock1 = loc;
                    } else {
                        node.representativeBlock2 = loc;
                    }
                }
            }
        }

        Map<Integer, List<Node>> oneNodes = nodes.computeIfAbsent(chunk1, loc -> new HashMap<>());
        Map<Integer, List<Node>> twoNodes = nodes.computeIfAbsent(chunk2, loc -> new HashMap<>());

        for (Node node : nodesByBoundaryID.values()) {
            if (node.chunk1SurfaceIDs.size() == 0 || node.chunk2SurfaceIDs.size() == 0)
                continue;

            for (int surfaceID : node.chunk1SurfaceIDs) {
                List<Node> nodeList = oneNodes.computeIfAbsent(surfaceID, id -> new ArrayList<>());
                for (Node peer : nodeList) {
                    node.connect(peer);
                }
                nodeList.add(node);
            }

            for (int surfaceID : node.chunk2SurfaceIDs) {
                List<Node> nodeList = twoNodes.computeIfAbsent(surfaceID, id -> new ArrayList<>());
                for (Node peer : nodeList) {
                    node.connect(peer);
                }
                nodeList.add(node);
            }
        }
    }

    /**
     * Add {@param chunk} to be pre-processed.
     */
    public void add(Chunk chunk) {
        ChunkLoc loc = new ChunkLoc(chunk);

        // If the chunk has already been added to this world
        if (chunks.containsKey(loc))
            return;

        PreprocessedRegion chunkRegion = new PreprocessedRegion(chunk);
        chunkRegion.rebuild();

        chunks.put(loc, chunkRegion);

        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                if (dx == 0 && dz == 0)
                    continue;

                // TODO : Corner boundaries
                if (Math.abs(dx) == 1 && Math.abs(dz) == 1)
                    continue;

                ChunkLoc neighbourLoc = loc.getRelative(dx, dz);
                PreprocessedRegion neighbour = chunks.get(neighbourLoc);
                if (neighbour == null)
                    continue;

                ChunkLocPair pair = new ChunkLocPair(loc, neighbourLoc);
                PreprocessedRegion boundary = createBoundary(world, pair);
                boundary.rebuild();

                straightBoundaries.put(pair, boundary);
                generateBoundaryNodes(boundary, chunkRegion, neighbour);
            }
        }
    }

    /**
     * Remove the pre-processing of {@param chunk}.
     */
    public void remove(Chunk chunk) {
        ChunkLoc loc = new ChunkLoc(chunk);

        chunks.remove(loc);
        Map<Integer, List<Node>> surfaceNodes = nodes.remove(loc);

        if (surfaceNodes != null) {
            for (List<Node> nodeList : surfaceNodes.values()) {
                for (Node node : nodeList) {
                    node.disconnectAll();
                }
            }
        }

        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                if (dx == 0 && dz == 0)
                    continue;

                // TODO : Corner boundaries
                if (Math.abs(dx) == 1 && Math.abs(dz) == 1)
                    continue;

                ChunkLocPair pair = new ChunkLocPair(loc, loc.getRelative(dx, dz));
                straightBoundaries.remove(pair);
            }
        }
    }

    /**
     * Rebuild the pre-processing of {@param chunk}.
     */
    public void rebuild(Chunk chunk) {
        // TODO : Can definitely do this more efficiently
        remove(chunk);
        add(chunk);
    }

    private List<Node> reconstructPath(Map<Node, Node> from, Node start, Node end) {
        List<Node> path = new ArrayList<>();

        Node current = end;

        while (!current.equals(start)) {
            path.add(current);
            current = from.get(current);
        }

        path.add(start);

        Collections.reverse(path);
        return path;
    }

    private List<Node> findPath(Node start, Node end) {
        // Keeps track of which nodes have already been processed
        Set<Node> processed = new HashSet<>();

        // Contains the previous node in the shortest path currently known to each node.
        Map<Node, Node> from = new HashMap<>();

        // Contains the length of the shortest path currently known to each node.
        Map<Node, Double> foundCosts = new HashMap<>();

        // Maintains which nodes are next to be processed
        PriorityQueueLinked<Node> toProcess = new PriorityQueueLinked<>();

        foundCosts.put(start, 0d);
        toProcess.add(start, 0);

        while (!toProcess.isEmpty()) {
            Node node = toProcess.poll();

            if (node.equals(end))
                return reconstructPath(from, start, end);

            double cost = foundCosts.get(node);
            processed.add(node);

            for (Node connected : node.connectedNodes) {
                if (processed.contains(connected))
                    continue;

                double connectedCost = cost + node.distance(connected);
                Double previousCost = foundCosts.get(connected);
                boolean previouslyExplored = (previousCost != null);

                if (previouslyExplored && previousCost <= connectedCost)
                    continue;

                foundCosts.put(connected, connectedCost);
                from.put(connected, node);

                double heuristic = connected.distance(end);

                // Negative priority as the queue prioritizes higher values first,
                // whereas we need to prioritize the lowest values first
                double priority = -(connectedCost + heuristic);

                if (previouslyExplored) {
                    toProcess.reprioritize(connected, priority);
                } else {
                    toProcess.add(connected, priority);
                }
            }
        }

        // No path could be found
        return null;
    }

    /**
     * Find a path within this region from {@param start} to {@param end}.
     *
     * @return A List of blocks to traverse to get from {@param start}
     *         to {@param end}, or null if no path could be found.
     */
    public List<BlockLoc> findPath(BlockLoc start, BlockLoc end) {
        ChunkLoc startChunk = start.toChunkLoc();
        ChunkLoc endChunk = end.toChunkLoc();

        PreprocessedRegion startRegion = chunks.get(startChunk);
        PreprocessedRegion endRegion = chunks.get(endChunk);

        if (startRegion == null || endRegion == null)
            return null;

        int startSurface = startRegion.getSurfaceIDByWorldLoc(start);
        int endSurface = endRegion.getSurfaceIDByWorldLoc(end);

        if (startSurface == 0 || endSurface == 0)
            throw new IllegalArgumentException("start and end must both be on a surface in their regions");

        Node startNode = new Node(start);
        Node endNode = new Node(end);

        Map<Integer, List<Node>> startSurfaceNodeMap = nodes.get(startChunk);
        Map<Integer, List<Node>> endSurfaceNodeMap = nodes.get(endChunk);

        if (startSurfaceNodeMap == null || endSurfaceNodeMap == null)
            return null;

        List<Node> startNodes = startSurfaceNodeMap.get(startSurface);
        List<Node> endNodes = endSurfaceNodeMap.get(endSurface);

        startNodes.forEach(startNode::connect);
        endNodes.forEach(endNode::connect);

        // NOTE : When path-finding becomes asynchronous modifying the actual
        //        graph to include the start and end points is not going to work

        try {
            List<Node> nodePath = findPath(startNode, endNode);
            if (nodePath == null)
                return null;

            List<BlockLoc> path = new ArrayList<>();

            for (int index = 1; index < nodePath.size(); ++index) {
                Node prev = nodePath.get(index - 1);
                Node curr = nodePath.get(index);

                ChunkLoc loc = prev.findCommonChunk(curr);
                if (loc == null)
                    throw new IllegalStateException("Nodes do not share a chunk");

                boolean isPrevOne = Objects.equals(loc, prev.chunk1);
                boolean isCurrOne = Objects.equals(loc, curr.chunk1);

                BlockLoc from = (isPrevOne ? prev.representativeBlock1 : prev.representativeBlock2);
                BlockLoc to = (isCurrOne ? curr.representativeBlock1 : curr.representativeBlock2);

                PreprocessedRegion region = chunks.get(loc);

                List<BlockLoc> regionPath = region.findPath(
                        from.subtract(region.getAnchor()),
                        to.subtract(region.getAnchor())
                );

                if (regionPath == null)
                    throw new IllegalStateException("Could not find path between nodes at " + from + " and " + to);

                // Add all except the last location
                for (int regionPathIndex = 0; regionPathIndex < regionPath.size() - 1; ++regionPathIndex) {
                    path.add(regionPath.get(regionPathIndex).add(region.getAnchor()));
                }

                // Now we need to add the path within the boundary between the representative blocks

                // If this isn't a boundary node, then we can skip this part
                if (Objects.equals(curr.chunk1, curr.chunk2))
                    continue;

                ChunkLocPair pair = new ChunkLocPair(curr.chunk1, curr.chunk2);
                PreprocessedRegion boundary = straightBoundaries.get(pair);

                BlockLoc boundaryFrom = (isCurrOne ? curr.representativeBlock1 : curr.representativeBlock2);
                BlockLoc boundaryTo = (isCurrOne ? curr.representativeBlock2 : curr.representativeBlock1);

                List<BlockLoc> boundaryPath = boundary.findPath(
                        boundaryFrom.subtract(boundary.getAnchor()),
                        boundaryTo.subtract(boundary.getAnchor())
                );

                // Add all except the last location
                for (int boundaryPathIndex = 0; boundaryPathIndex < boundaryPath.size() - 1; ++boundaryPathIndex) {
                    path.add(boundaryPath.get(boundaryPathIndex).add(boundary.getAnchor()));
                }
            }

            path.add(end);

            return path;
        } finally {
            startNode.disconnectAll();
            endNode.disconnectAll();
        }
    }

    private static final Material[] DEBUG_CARPETS = new Material[] {
            Material.BLUE_CARPET,
            Material.RED_CARPET,
            Material.GREEN_CARPET,
            Material.CYAN_CARPET,
            Material.LIME_CARPET,
            Material.LIGHT_BLUE_CARPET,
            Material.MAGENTA_CARPET,
            Material.ORANGE_CARPET,
            Material.PINK_CARPET,
            Material.PURPLE_CARPET,
            Material.YELLOW_CARPET,
            Material.WHITE_CARPET,
            Material.BROWN_CARPET,
            Material.BLACK_CARPET,
            Material.GRAY_CARPET,
            Material.LIGHT_GRAY_CARPET,
    };

    private static final Material[] DEBUG_WOOLS = new Material[] {
            Material.BLUE_WOOL,
            Material.RED_WOOL,
            Material.GREEN_WOOL,
            Material.CYAN_WOOL,
            Material.LIME_WOOL,
            Material.LIGHT_BLUE_WOOL,
            Material.MAGENTA_WOOL,
            Material.ORANGE_WOOL,
            Material.PINK_WOOL,
            Material.PURPLE_WOOL,
            Material.YELLOW_WOOL,
            Material.WHITE_WOOL,
            Material.BROWN_WOOL,
            Material.BLACK_WOOL,
            Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL,
    };

    private void debug(PreprocessedRegion region, List<BlockState> restoreBlocks, Material[] materials) {
        for (int x = 0; x < region.getWidth(); ++x) {
            for (int z = 0; z < region.getDepth(); ++z) {
                for (int y = 0; y < region.getHeight() - 1; ++y) {
                    int surfaceID = region.getSurfaceID(x, y, z);
                    if (surfaceID == 0)
                        continue;

                    Material display = materials[surfaceID % materials.length];

                    Block block = region.getBlock(x, y + 1, z);
                    restoreBlocks.add(block.getState());
                    block.setType(display);
                }
            }
        }
    }

    private void restoreLater(List<BlockState> restoreBlocks, long ticksLater) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(main, () -> {
            Collections.reverse(restoreBlocks);
            for (BlockState block : restoreBlocks) {
                block.update(true, false);
            }
        }, ticksLater);
    }

    public void debugNodes() {
        List<BlockLoc> locs = new ArrayList<>();

        for (Map<Integer, List<Node>> surfaceMap : nodes.values()) {
            for (List<Node> nodeList : surfaceMap.values()) {
                for (Node node : nodeList) {
                    locs.add(new BlockLoc(
                            (int) (node.cumulativeX / node.surfaceBlockCount),
                            (int) (node.cumulativeY / node.surfaceBlockCount),
                            (int) (node.cumulativeZ / node.surfaceBlockCount)
                    ));
                }
            }
        }

        List<BlockState> blocks = new ArrayList<>();

        // We have to do this here as setType might trigger a chunk to
        // load, which could cause a ConcurrentModificationException
        for (BlockLoc loc : locs) {
            Block block = world.getBlockAt(loc.x, loc.y, loc.z);

            blocks.add(block.getState());
            block.setType(Material.BLUE_WOOL);
        }

        restoreLater(blocks, 20 * 10);
    }

    /**
     * @return Whether {@param block} is part of a surface.
     */
    public boolean debugConnections(Block block) {
        Chunk chunk = block.getChunk();
        ChunkLoc chunkLoc = new ChunkLoc(chunk);

        remove(chunk);
        add(chunk);

        PreprocessedRegion region = chunks.get(chunkLoc);

        if (region.getSurfaceID(block) == 0)
            return false;

        List<BlockState> blocks = new ArrayList<>();

        blocks.add(block.getState());
        block.setType(Material.BLUE_WOOL);

        // Loop through all connections from the given block
        int connectionMask = region.getConnectionMask(block);
        for (SurfaceConnection connection : SurfaceConnection.values()) {
            if (!connection.inMask(connectionMask))
                continue;

            Block connectedBlock = connection.get(block);

            blocks.add(connectedBlock.getState());
            connectedBlock.setType(Material.RED_WOOL);
        }

        restoreLater(blocks, 30);

        return true;
    }

    /**
     * @return Whether a path could be found.
     */
    public boolean debugPath(Block from, Block to) {
        List<BlockLoc> path = findPath(new BlockLoc(from), new BlockLoc(to));

        if (path == null)
            return false;

        List<BlockState> states = new ArrayList<>();

        for (BlockLoc loc : path) {
            Block block = world.getBlockAt(loc.x, loc.y, loc.z);
            states.add(block.getState());
        }

        Runnable task = new Runnable() {
            private int counter = 0;

            @Override
            public void run() {
                Material[] wools = {
                        Material.BLUE_WOOL,
                        Material.LIGHT_BLUE_WOOL,
                        Material.CYAN_WOOL,
                        Material.PURPLE_WOOL
                };

                for (int index = 0; index < path.size(); ++index) {
                    BlockLoc loc = path.get(index);
                    Block block = world.getBlockAt(loc.x, loc.y, loc.z);

                    block.setType(wools[(index + counter) % 4]);
                }

                counter += 1;
            }
        };

        int durationSecs = 15;

        for (int index = 0; index < durationSecs - 1; ++index) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(main, task, 20 * index);
        }

        restoreLater(states, 20 * durationSecs);

        return true;
    }

    public void debugDisplay(Chunk chunk) {
        World world = chunk.getWorld();

        ChunkLoc loc1 = new ChunkLoc(chunk);
        ChunkLoc loc2 = loc1.getRelative(-1, 0);
        ChunkLocPair pair = new ChunkLocPair(loc1, loc2);

        Chunk chunk2 = world.getChunkAt(loc2.x, loc2.z);
        remove(chunk);
        remove(chunk2);
        add(chunk);
        add(chunk2);

        PreprocessedRegion region1 = chunks.get(loc1);
        PreprocessedRegion region2 = chunks.get(loc2);
        PreprocessedRegion boundary = straightBoundaries.get(pair);

        // Show all surfaces in regions and boundary
        List<BlockState> regionBlocks = new ArrayList<>();

        debug(region1, regionBlocks, DEBUG_CARPETS);
        debug(region2, regionBlocks, DEBUG_CARPETS);

        Runnable debugBoundaryAndRestore = () -> {
            List<BlockState> boundaryBlocks = new ArrayList<>();

            debug(boundary, boundaryBlocks, DEBUG_WOOLS);

            restoreLater(boundaryBlocks, 20);
        };

        Bukkit.getScheduler().scheduleSyncDelayedTask(main, debugBoundaryAndRestore, 20);
        Bukkit.getScheduler().scheduleSyncDelayedTask(main, debugBoundaryAndRestore, 3 * 20);
        Bukkit.getScheduler().scheduleSyncDelayedTask(main, debugBoundaryAndRestore, 5 * 20);
        Bukkit.getScheduler().scheduleSyncDelayedTask(main, debugBoundaryAndRestore, 7 * 20);
        Bukkit.getScheduler().scheduleSyncDelayedTask(main, debugBoundaryAndRestore, 9 * 20);
        Bukkit.getScheduler().scheduleSyncDelayedTask(main, debugBoundaryAndRestore, 11 * 20);

        restoreLater(regionBlocks, 13 * 20);
    }
}
