# Producer–Consumer: stock replenishment

A small Java concurrency project built around an inventory counter.

A consumer places orders. If there isn't enough stock, the consumer waits. A producer is responsible for replenishing the inventory when an order cannot be fulfilled or the remaining stock falls to the reorder point.

The interesting part is not the menu. It's how the two threads coordinate without losing updates or wasting CPU time while they wait.

## Try it

Requirements: JDK 17+ and Maven 3.9+.

```sh
mvn test
mvn compile
java -cp target/classes com.fazhar.producerconsumer.Main
```

The demo starts with **60 units**, a maximum of **100**, and a reorder point of **25**. Enter an integer to buy that many units; enter `q` to exit.

For example, ordering 50 units leaves 10. That triggers a restock. If you then order 80 while fewer than 80 units are available, the consumer waits until the producer finishes restocking. Messages from the two threads may appear in a different order on different runs.

## How it works

```text
                  Stock (one shared object)
             +------------------------------+
Consumer --->| take(units)                  |
             |  - wait if insufficient      |
             |  - decrease units on hand    |
             |  - request restock if low    |
             |                              |
Producer <---| awaitRestockRequest()        |
Producer --->| restock() -> fill to capacity|
             +------------------------------+
```

- `Stock` owns the inventory and the synchronization rules.
- `Consumer` handles console orders and calls `Stock.take()`.
- `Producer` waits for a restock request, simulates a delivery delay, then refills stock.
- `Main` starts the threads and closes the stock when input ends.

`Stock` uses Java's intrinsic monitor: `synchronized`, `wait()` and `notifyAll()`. It checks the condition in a `while` loop every time it wakes up. No thread sleeps while holding the stock monitor.

