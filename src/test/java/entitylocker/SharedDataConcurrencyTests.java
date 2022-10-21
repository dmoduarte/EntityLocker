package entitylocker;


import entitylocker.testobjects.TestCounter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedDataConcurrencyTests {

    @Test
    void executeWithEntityExclusiveAccess_incrementCounterWithConcurrency_valueShouldBeConsistent() throws InterruptedException {
        int numberOfThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        TestCounter counter = new TestCounter();
        EntityLocker<Integer> entityLocker = new ReentrantEntityLockerImpl<>();
        for (int i = 0; i < numberOfThreads; i++) {
            Runnable protectedCode = () -> {
                try {
                    counter.increment();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                latch.countDown();
            };
            service.submit(() -> entityLocker.executeWithEntityExclusiveAccess(1, protectedCode));
        }
        latch.await();
        assertEquals(numberOfThreads, counter.getCounter());
    }
}
