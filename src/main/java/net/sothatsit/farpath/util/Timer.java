package net.sothatsit.farpath.util;

/**
 * Allows easily timing sections of code.
 *
 * @author Paddy Lamont
 */
public class Timer {

    private final long start;

    private Timer() {
        this.start = System.nanoTime();
    }

    public long getDurationNS() {
        return System.nanoTime() - start;
    }

    public double getDurationMS() {
        return getDurationNS() / 1000000d;
    }

    @Override
    public String toString() {
        return String.format("%.2f ms", getDurationMS());
    }

    public static Timer start() {
        return new Timer();
    }
}
