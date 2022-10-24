package entitylocker;


import entitylocker.testobjects.TestCounter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedDataConcurrencyTests {
    private ExecutorService service;
    private EntityLocker<Integer> entityLocker;
    private TestCounter counter;

    @BeforeEach
    void setup() {
        service = Executors.newFixedThreadPool(5);
        counter = new TestCounter();
        entityLocker = new ReentrantEntityLockerImpl<>();
    }

    @Test
    void executeWithEntityExclusiveAccess_incrementCounterWithConcurrency_valueShouldBeConsistent() throws InterruptedException {
        int numberOfThreads = 3;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        ProtectedCode protectedCode = () -> {
            try {
                counter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        };

        submit(numberOfThreads, () -> entityLocker.executeWithEntityExclusiveAccess(1, protectedCode));

        latch.await();
        assertEquals(numberOfThreads, counter.getCounter());
    }

    @Test
    void executeWithTimedEntityExclusiveAccess_incrementCounterWithConcurrency_valueShouldBeConsistent() throws InterruptedException {
        int numberOfThreads = 3;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        ProtectedCode protectedCode = () -> {
            try {
                counter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        };

        Runnable task = () -> {
            try {
                entityLocker.executeWithEntityExclusiveAccess(1, protectedCode, 1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        submit(numberOfThreads, task);

        latch.await();
        assertEquals(numberOfThreads, counter.getCounter());
    }

    @Test
    void executeWithGlobalExclusiveAccess_incrementCounterWithConcurrency_valueShouldBeConsistent() throws InterruptedException {
        int numberOfThreads = 3;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        ProtectedCode protectedCode = () -> {
            try {
                counter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        };

        Runnable task = () -> {
            try {
                entityLocker.executeWithGlobalExclusiveAccess(protectedCode);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };

        submit(numberOfThreads, task);

        latch.await();
        assertEquals(numberOfThreads, counter.getCounter());
    }

    @Test
    void executeWithTimedGlobalExclusiveAccess_incrementCounterWithConcurrency_valueShouldBeConsistent() throws InterruptedException {
        int numberOfThreads = 3;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        ProtectedCode protectedCode = () -> {
            try {
                counter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        };

        Runnable task = () -> {
            try {
                entityLocker.executeWithGlobalExclusiveAccess(protectedCode, 1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };

        submit(numberOfThreads, task);

        latch.await();
        assertEquals(numberOfThreads, counter.getCounter());
    }

    @Test
    void executeWithEntityAndGlobalExclusiveAccess_incrementCounterWithConcurrency_valueShouldBeConsistent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        ProtectedCode protectedCode = () -> {
            try {
                counter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        };

        Runnable globalTask = () -> {
            try {
                entityLocker.executeWithGlobalExclusiveAccess(protectedCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable entityTask = () -> entityLocker.executeWithEntityExclusiveAccess(1, protectedCode);

        submit(1, globalTask);
        submit(1, entityTask);

        latch.await();

        assertEquals(2, counter.getCounter());
    }

    private void submit(int numberThreads, Runnable runnable) {
        for (int i = 0; i < numberThreads; i++) {
            service.submit(runnable);
        }
    }
}
