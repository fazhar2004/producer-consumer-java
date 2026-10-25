package com.fazhar.producerconsumer;

/**
 * Shared inventory. Every read or change to stock state uses this object's monitor.
 */
public final class Stock {
    private final int capacity;
    private final int reorderPoint;

    private int unitsOnHand;
    private boolean restockRequested;
    private boolean closed;

    public Stock(int capacity, int initialUnits, int reorderPoint) {
        if (capacity <= 0 || initialUnits < 0 || initialUnits > capacity
                || reorderPoint < 0 || reorderPoint >= capacity) {
            throw new IllegalArgumentException("Invalid stock configuration");
        }

        this.capacity = capacity;
        this.unitsOnHand = initialUnits;
        this.reorderPoint = reorderPoint;
        this.restockRequested = initialUnits <= reorderPoint;
    }

    public int capacity() {
        return capacity;
    }

    public synchronized int unitsOnHand() {
        return unitsOnHand;
    }

    public synchronized void take(int units) throws InterruptedException {
        if (units <= 0 || units > capacity) {
            throw new IllegalArgumentException("Order must be between 1 and " + capacity);
        }

        while (unitsOnHand < units && !closed) {
            requestRestock();
            wait();
        }

        if (closed) {
            throw new IllegalStateException("Stock is closed");
        }

        unitsOnHand -= units;

        if (unitsOnHand <= reorderPoint) {
            requestRestock();
        }
    }

    // Only signal on the transition. Repeated notifyAll calls from waiting
    // consumers can otherwise wake each other in a tight loop.
    private void requestRestock() {
        if (!restockRequested) {
            restockRequested = true;
            notifyAll();
        }
    }

    public synchronized boolean awaitRestockRequest() throws InterruptedException {
        while (!restockRequested && !closed) {
            wait();
        }
        return !closed;
    }

    public synchronized int restock() {
        if (closed || !restockRequested) {
            return 0;
        }

        int added = capacity - unitsOnHand;
        unitsOnHand = capacity;
        restockRequested = false;
        notifyAll();
        return added;
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }
}
