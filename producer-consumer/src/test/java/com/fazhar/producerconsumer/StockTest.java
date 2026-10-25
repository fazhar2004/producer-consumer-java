package com.fazhar.producerconsumer;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class StockTest {
    @Test
    void rejectsInvalidConfigurationAndOrderSize() {
        assertThrows(IllegalArgumentException.class, () -> new Stock(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Stock(10, 11, 3));
        assertThrows(IllegalArgumentException.class, () -> new Stock(10, 2, 10));

        Stock stock = new Stock(10, 6, 2);
        assertThrows(IllegalArgumentException.class, () -> stock.take(0));
        assertThrows(IllegalArgumentException.class, () -> stock.take(11));
        assertEquals(6, stock.unitsOnHand());
    }

    @Test
    void fulfillingOrderReducesStock() throws InterruptedException {
        Stock stock = new Stock(100, 60, 20);
        stock.take(12);
        assertEquals(48, stock.unitsOnHand());
    }

    @Test
    void fallingBelowReorderPointRequestsRestock() throws InterruptedException {
        Stock stock = new Stock(100, 60, 25);
        stock.take(40);
        assertTrue(stock.awaitRestockRequest());
        assertEquals(80, stock.restock());
        assertEquals(100, stock.unitsOnHand());
        assertEquals(0, stock.restock());
    }

    @Test
    void anOrderWaitsForRestockAndThenCompletes() throws Exception {
        Stock stock = new Stock(10, 2, 1);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);

        try {
            Future<?> order = workers.submit(() -> {
                entered.countDown();
                stock.take(8);
                return null;
            });

            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> order.get(150, TimeUnit.MILLISECONDS));
            assertTrue(stock.awaitRestockRequest());
            assertEquals(8, stock.restock());
            order.get(1, TimeUnit.SECONDS);
            assertEquals(2, stock.unitsOnHand());
        } finally {
            stock.close();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void aLargeOrderRequestsStockEvenAboveTheReorderPoint() throws Exception {
        Stock stock = new Stock(100, 60, 25);
        ExecutorService worker = Executors.newSingleThreadExecutor();

        try {
            Future<?> order = worker.submit(() -> {
                stock.take(80);
                return null;
            });
            assertThrows(TimeoutException.class, () -> order.get(150, TimeUnit.MILLISECONDS));
            assertTrue(stock.awaitRestockRequest());
            assertEquals(40, stock.restock());
            order.get(1, TimeUnit.SECONDS);
            assertEquals(20, stock.unitsOnHand());
        } finally {
            stock.close();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void simultaneousOrdersDoNotOversell() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Stock stock = new Stock(1000, 1000, 0);
            ExecutorService workers = Executors.newFixedThreadPool(8);
            List<Future<?>> orders = new ArrayList<>();

            try {
                for (int i = 0; i < 200; i++) {
                    orders.add(workers.submit(() -> {
                        stock.take(4);
                        return null;
                    }));
                }
                for (Future<?> order : orders) {
                    order.get(3, TimeUnit.SECONDS);
                }
                assertEquals(200, stock.unitsOnHand());
            } finally {
                stock.close();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
            }
        });
    }

    @Test
    void concurrentConsumersKeepMovingWhileProducerRestocks() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Stock stock = new Stock(100, 40, 15);
            ExecutorService workers = Executors.newFixedThreadPool(10);
            Thread producer = new Thread(new Producer(stock, 0,
                    new PrintStream(OutputStream.nullOutputStream())));
            List<Future<?>> orders = new ArrayList<>();
            producer.start();

            try {
                for (int i = 0; i < 200; i++) {
                    orders.add(workers.submit(() -> {
                        stock.take(4);
                        return null;
                    }));
                }
                for (Future<?> order : orders) {
                    order.get(5, TimeUnit.SECONDS);
                }
                assertTrue(stock.unitsOnHand() >= 0);
                assertTrue(stock.unitsOnHand() <= stock.capacity());
            } finally {
                stock.close();
                workers.shutdownNow();
                producer.join(2_000);
                assertFalse(producer.isAlive());
                assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
            }
        });
    }

    @Test
    void closingStockWakesWaitingConsumersAndProducer() throws Exception {
        Stock stock = new Stock(10, 0, 1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<?> order = workers.submit(() -> {
                stock.take(5);
                return null;
            });
            // Request is already pending, so a producer can start immediately.
            Future<Boolean> producer = workers.submit(stock::awaitRestockRequest);
            assertTrue(producer.get(1, TimeUnit.SECONDS));
            stock.close();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> order.get(1, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertFalse(stock.awaitRestockRequest());
            assertEquals(0, stock.restock());
        } finally {
            stock.close();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void producerWaitingForDemandCanStop() throws Exception {
        Stock stock = new Stock(10, 8, 1);
        ExecutorService worker = Executors.newSingleThreadExecutor();

        try {
            Future<Boolean> pending = worker.submit(stock::awaitRestockRequest);
            assertThrows(TimeoutException.class, () -> pending.get(150, TimeUnit.MILLISECONDS));
            stock.close();
            assertFalse(pending.get(1, TimeUnit.SECONDS));
        } finally {
            stock.close();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
