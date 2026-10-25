package com.fazhar.producerconsumer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerTest {
    @Test
    void skipsInvalidInputAndProcessesValidOrder() {
        Stock stock = new Stock(100, 60, 10);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Scanner input = new Scanner("abc\n0\n200\n12\nq\n");

        new Consumer(stock, input, new PrintStream(output)).run();

        assertEquals(48, stock.unitsOnHand());
        String transcript = output.toString(StandardCharsets.UTF_8);
        assertTrue(transcript.contains("Enter a whole number"));
        assertTrue(transcript.contains("Order must be between 1 and 100"));
        assertTrue(transcript.contains("Order completed"));
    }
}
