package com.fazhar.producerconsumer;

import java.util.Scanner;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Stock stock = new Stock(100, 60, 25);
        Thread producer = new Thread(new Producer(stock, 400, System.out), "producer");
        Thread consumer = new Thread(new Consumer(stock, new Scanner(System.in), System.out), "consumer");

        producer.start();
        consumer.start();

        try {
            consumer.join();
        } catch (InterruptedException e) {
            consumer.interrupt();
            Thread.currentThread().interrupt();
        } finally {
            stock.close();
            try {
                producer.join();
            } catch (InterruptedException e) {
                producer.interrupt();
                Thread.currentThread().interrupt();
            }
        }
    }
}
