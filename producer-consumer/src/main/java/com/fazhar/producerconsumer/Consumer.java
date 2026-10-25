package com.fazhar.producerconsumer;

import java.io.PrintStream;
import java.util.Scanner;

public final class Consumer implements Runnable {
    private final Stock stock;
    private final Scanner input;
    private final PrintStream out;

    public Consumer(Stock stock, Scanner input, PrintStream out) {
        this.stock = stock;
        this.input = input;
        this.out = out;
    }

    @Override
    public void run() {
        out.printf("Inventory started with %d units. Enter q to quit.%n", stock.unitsOnHand());

        while (!Thread.currentThread().isInterrupted()) {
            out.print("Units to buy: ");
            out.flush();

            if (!input.hasNextLine()) {
                break;
            }

            String line = input.nextLine().trim();
            if (line.equalsIgnoreCase("q")) {
                break;
            }

            try {
                int units = Integer.parseInt(line);
                out.printf("[Consumer] Requesting %d units%n", units);
                stock.take(units);
                out.printf("[Consumer] Order completed. On hand: %d%n", stock.unitsOnHand());
            } catch (NumberFormatException e) {
                out.println("Enter a whole number or q.");
            } catch (IllegalArgumentException e) {
                out.println(e.getMessage());
            } catch (IllegalStateException e) {
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        out.println("Session ended.");
    }
}
