package net.sothatsit.farpath.util;

import java.util.Objects;

/**
 * A linked, first-in, first-out, priority queue implementation.
 *
 * @author Paddy Lamont
 */
public class PriorityQueueLinked<E> {

    /**
     * The front element in this queue. The element with the highest priority.
     */
    private PriorityLink<E> front;

    /**
     * Construct a new empty priority queue.
     */
    public PriorityQueueLinked() {
        this.front = null;
    }

    /**
     * @return true iff there are no elements left in this queue, false otherwise
     */
    public boolean isEmpty() {
        return front == null;
    }

    /**
     * Enqueue a new element with value {@param value} and priority {@param priority}.
     *
     * @param value    The value to enqueue
     * @param priority The priority of the value
     */
    public void add(E value, double priority) {
        if(isEmpty() || priority > front.priority) {
            front = new PriorityLink<>(value, front, priority);
            return;
        }

        PriorityLink<E> link = front;

        while(link.next != null && link.next.priority >= priority) {
            link = link.next;
        }

        link.next = new PriorityLink<>(value, link.next, priority);
    }

    /**
     * Remove {@param value} from the queue.
     *
     * Assumes that {@param value} exists at most once in the queue.
     *
     * @param value The value to remove
     *
     * @throws IllegalArgumentException if {@param value} does not appear in the queue
     */
    public void remove(E value) {
        if (isEmpty())
            throw new IllegalArgumentException("the queue is empty");

        PriorityLink<E> link = front;

        if (Objects.equals(link.value, value)) {
            front = link.next;
            return;
        }

        while(link.next != null && !Objects.equals(link.next.value, value)) {
            link = link.next;
        }

        if (link.next == null)
            throw new IllegalArgumentException("the given value could not be found in the queue");

        link.next = link.next.next;
    }

    /**
     * Changes the priority of {@param value} in the queue to {@param priority}.
     *
     * Assumes that {@param value} exists at most once in the queue.
     *
     * @param value    The value to re-prioritize
     * @param priority The new priority to give the value
     *
     * @throws IllegalArgumentException if {@param value} does not appear in the queue
     */
    public void reprioritize(E value, double priority) {
        // TODO : Could be done more efficiently if both were done at the same time
        remove(value);
        add(value, priority);
    }

    /**
     * Get the value of the next element to be dequeue'd.
     *
     * @return The value of the top-most, highest priority link in this queue
     *
     * @throws IllegalStateException If there are no elements currently in the queue
     */
    public E peek() {
        if(isEmpty())
            throw new IllegalStateException("attempting to examine empty queue");

        return front.value;
    }

    /**
     * Dequeue the top-most, highest priority link in this queue.
     *
     * @return The dequeue'd value
     *
     * @throws IllegalStateException If there are no elements currently in the queue
     */
    public E poll() {
        if(isEmpty())
            throw new IllegalStateException("attempting to examine empty queue");

        E value = front.value;
        front = front.next;

        return value;
    }

    /**
     * A Link with an associated priority.
     *
     * @param <E> The type of value held in this link
     */
    private static class PriorityLink<E> {

        /**
         * The value of this link.
         */
        private final E value;

        /**
         * The successor of this link.
         */
        private PriorityLink<E> next;

        /**
         * The priority of this value.
         */
        private final double priority;

        /**
         * Construct a new priority link.
         *
         * @param value    The value to be held in this link
         * @param next     The next link after this link
         * @param priority The priority of this link
         */
        public PriorityLink(E value, PriorityLink<E> next, double priority) {
            this.value = value;
            this.next = next;
            this.priority = priority;
        }
    }
}