package net.sothatsit.farpath.util;

/**
 * Maintains an array of booleans backed by a byte array to save space by using one bit per value.
 *
 * @author Paddy Lamont
 */
public class PackedBooleanArray {

    private static final byte[] masks = new byte[8];
    static {
        for (int index = 0; index < masks.length; ++index) {
            masks[index] = (byte) (1 << index);
        }
    }

    private final int length;
    private final byte[] array;

    public PackedBooleanArray(int length) {
        int bytes = (length + 7) / 8;
        this.length = length;
        this.array = new byte[bytes];
    }

    public boolean get(int index) {
        return (array[index / 8] & masks[index % 8]) != 0;
    }

    public void set(int index, boolean value) {
        if (value) {
            array[index / 8] |= masks[index % 8];
        } else {
            array[index / 8] &= ~masks[index % 8];
        }
    }
}
