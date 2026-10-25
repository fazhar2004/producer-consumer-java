package com.fazhar.producerconsumer;

import java.io.PrintStream;

public final class Producer implements Runnable {
    private final Stock stock;
    private final long restockDelayMillis;
    private final PrintStream out;

    public Producer(Stock stock, long restockDelayMillis, PrintStream out) {
        if (restockDelayMillis < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        this.stock = stock;
        this.restockDelayMillis = restockDelayMillis;
        this.out = out;
    }

    @Override
    public void run() {
        try {
            while (stock.awaitRestockRequest()) {
                // Simulate delivery time without holding the stock monitor.
                Thread.sleep(restockDelayMillis);

                int added = stock.restock();
                if (added > 0) {
                    out.printf("%n[Producer] Added %d units. On hand: %d%n",
                            added, stock.unitsOnHand());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stock.close();
        }
    }
}
