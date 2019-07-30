package net.sothatsit.farpath.preprocessing;

/**
 * Represents a pair of chunks.
 *
 * @author Paddy Lamont
 */
public class ChunkLocPair {

    public final ChunkLoc smaller;
    public final ChunkLoc larger;

    public ChunkLocPair(ChunkLoc one, ChunkLoc two) {
        if (one == null)
            throw new IllegalArgumentException("one cannot be null");
        if (two == null)
            throw new IllegalArgumentException("two cannot be null");
        if (one.equals(two))
            throw new IllegalArgumentException("one and two cannot be the same location");
        if (Math.abs(one.x - two.x) > 1 || Math.abs(one.z - two.z) > 1)
            throw new IllegalArgumentException("chunks must be adjacent");

        // The discriminating factor between the chunks
        int oneVal, twoVal;

        if (one.x == two.x) {
            oneVal = one.z;
            twoVal = two.z;
        } else if (one.z == two.z) {
            oneVal = one.x;
            twoVal = two.x;
        } else {
            throw new IllegalArgumentException("Locations must not be diagonally adjacent");
        }

        if (oneVal < twoVal) {
            smaller = one;
            larger = two;
        } else {
            smaller = two;
            larger = one;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !getClass().equals(obj.getClass()))
            return false;

        ChunkLocPair other = (ChunkLocPair) obj;

        return smaller.equals(other.smaller) && larger.equals(other.larger);
    }

    @Override
    public int hashCode() {
        return smaller.hashCode() ^ larger.hashCode();
    }
}
