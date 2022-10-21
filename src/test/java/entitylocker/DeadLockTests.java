package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadLockTests {
    private static final Runnable NO_OP = () -> {
    };

    @Test
    void executeWithEntityExclusiveAccess_multipleLockAcquireBySingleThread_shouldBeReentrant() throws InterruptedException {
        EntityLocker<String> entityLocker = new ReentrantEntityLockerImpl<>();
        AtomicInteger atomicInteger = new AtomicInteger(0);
        entityLocker.executeWithEntityExclusiveAccess("id", () -> {
            entityLocker.executeWithEntityExclusiveAccess("id", atomicInteger::incrementAndGet);
            atomicInteger.incrementAndGet();
        });

        assertEquals(2, atomicInteger.get());

        entityLocker.executeWithEntityExclusiveAccess("id", () -> {
            try {
                entityLocker.executeWithEntityExclusiveAccess("id", atomicInteger::incrementAndGet, 1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            atomicInteger.incrementAndGet();
        }, 1, TimeUnit.MINUTES);

        assertEquals(4, atomicInteger.get());
    }

    @Test
    void executeWithGlobalAndEntityExclusiveAccess_withNestedCallOnSingleThread_shouldBeReentrant() {
        EntityLocker<Integer> entityLocker = new ReentrantEntityLockerImpl<>();
        AtomicBoolean value = new AtomicBoolean(false);

        entityLocker.executeWithGlobalExclusiveAccess(() -> {
            entityLocker.executeWithEntityExclusiveAccess(1, () -> {
                value.set(true);
            });
        });

        assertTrue(value.get());
    }

    @Test
    void executeWithEntityAndGlobalExclusiveAccess_withNestedCallOnSingleThread_shouldBeReentrant() {
        EntityLocker<Integer> entityLocker = new ReentrantEntityLockerImpl<>();
        AtomicBoolean value = new AtomicBoolean(false);

        entityLocker.executeWithEntityExclusiveAccess(1, () -> {
            entityLocker.executeWithGlobalExclusiveAccess(() -> {
                value.set(true);
            });
        });

        assertTrue(value.get());
    }

    @Test
    void executeWithEntityExclusiveAccess_withMultipleThreads_shouldNotDeadlock() {
        EntityLocker<String> entityLocker = new ReentrantEntityLockerImpl<>();

        CountDownLatch t1AcquireE1Latch = new CountDownLatch(1);
        AtomicBoolean deadLockDetected = new AtomicBoolean(false);

        Runnable runnable = () -> {
            entityLocker.executeWithEntityExclusiveAccess("E1", () -> {
                t1AcquireE1Latch.countDown();
                sleep(250);
                entityLocker.executeWithEntityExclusiveAccess("E2", NO_OP);
            });
        };

        Runnable runnable2 = () -> {
            entityLocker.executeWithEntityExclusiveAccess("E2", () -> {
                sleep(500);
                try {
                    entityLocker.executeWithEntityExclusiveAccess("E1", NO_OP);
                } catch (DeadLockPreventionException e) {
                    deadLockDetected.set(true);
                }
            });
        };

        Thread thread1 = new Thread(runnable);
        thread1.start();

        awaitLatch(t1AcquireE1Latch);

        Thread thread2 = new Thread(runnable2);
        thread2.start();

        try {
            thread1.join();
            thread2.join();
            Assertions.assertTrue(deadLockDetected.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
