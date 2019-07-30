package net.sothatsit.farpath.preprocessing;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A connection node between two chunks.
 *
 * @author Paddy Lamont
 */
public class Node {

    public final UUID uuid = UUID.randomUUID();

    public final ChunkLoc chunk1;
    public final ChunkLoc chunk2;
    public final int boundarySurfaceID;

    public final Set<Integer> chunk1SurfaceIDs;
    public final Set<Integer> chunk2SurfaceIDs;
    public final Set<Node> connectedNodes;

    // Used to find the average location of all blocks in the boundary surface
    public double cumulativeX;
    public double cumulativeY;
    public double cumulativeZ;
    public int surfaceBlockCount;

    // Can be used to flood fill and find other node blocks
    public BlockLoc representativeBlock1;
    public BlockLoc representativeBlock2;

    public Node(BlockLoc representativeBlock) {
        this(representativeBlock.toChunkLoc(), representativeBlock.toChunkLoc(), 0);

        this.cumulativeX = representativeBlock.x;
        this.cumulativeY = representativeBlock.y;
        this.cumulativeZ = representativeBlock.z;
        this.surfaceBlockCount = 1;
        this.representativeBlock1 = representativeBlock;
        this.representativeBlock2 = representativeBlock;
    }

    public Node(ChunkLoc chunk1,
                ChunkLoc chunk2,
                int boundarySurfaceID) {

        this.chunk1 = chunk1;
        this.chunk2 = chunk2;
        this.boundarySurfaceID = boundarySurfaceID;
        this.chunk1SurfaceIDs = new HashSet<>();
        this.chunk2SurfaceIDs = new HashSet<>();
        this.connectedNodes = new HashSet<>();
    }

    public void connect(Node node) {
        connectedNodes.add(node);
        node.connectedNodes.add(this);
    }

    public void disconnect(Node node) {
        connectedNodes.add(node);
        node.connectedNodes.remove(this);
    }

    public void disconnectAll() {
        for (Node node : connectedNodes) {
            node.connectedNodes.remove(this);
        }
        connectedNodes.clear();
    }

    public double distanceSquared(double x, double y, double z) {
        if (surfaceBlockCount == 0)
            throw new IllegalStateException("The location of this node has not been initialised");

        double dx = x - cumulativeX / surfaceBlockCount;
        double dy = y - cumulativeY / surfaceBlockCount;
        double dz = z - cumulativeZ / surfaceBlockCount;
        return dx*dx + dy*dy + dz*dz;
    }

    public double distance(Node node) {
        if (node.surfaceBlockCount == 0)
            throw new IllegalStateException("The location of the other node has not been initialised");

        return Math.sqrt(distanceSquared(
                node.cumulativeX / node.surfaceBlockCount,
                node.cumulativeY / node.surfaceBlockCount,
                node.cumulativeZ / node.surfaceBlockCount
        ));
    }

    public ChunkLoc findCommonChunk(Node node) {
        if (Objects.equals(chunk1, node.chunk1))
            return chunk1;
        if (Objects.equals(chunk1, node.chunk2))
            return chunk1;
        if (Objects.equals(chunk2, node.chunk1))
            return chunk2;
        if (Objects.equals(chunk2, node.chunk2))
            return chunk2;
        return null;
    }

    @Override
    public String toString() {
        return "Node(" + representativeBlock1 + ", " + representativeBlock2 + ")";
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }
}
