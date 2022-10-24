package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadLockAndReentrancyTests {
    private static final ProtectedCode NO_OP = () -> {
    };

    private EntityLocker<String> entityLocker;

    @BeforeEach
    void setup() {
        entityLocker = new ReentrantEntityLockerImpl<>();
    }

    @Test
    void executeWithEntityExclusiveAccess_multipleLockAcquireBySingleThread_shouldBeReentrant() {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        entityLocker.executeWithEntityExclusiveAccess("id", () -> {
            entityLocker.executeWithEntityExclusiveAccess("id", atomicInteger::incrementAndGet);
            atomicInteger.incrementAndGet();
        });

        assertEquals(2, atomicInteger.get());
    }

    @Test
    void executeWithTimedEntityExclusiveAccess_multipleLockAcquireBySingleThread_shouldBeReentrant() throws InterruptedException {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        entityLocker.executeWithEntityExclusiveAccess("id", () -> {
            try {
                entityLocker.executeWithEntityExclusiveAccess("id", atomicInteger::incrementAndGet, 1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            atomicInteger.incrementAndGet();
        }, 1, TimeUnit.MINUTES);

        assertEquals(2, atomicInteger.get());
    }

    @Test
    void executeWithGlobalExclusiveAccess_withNestedCallOnSingleThread_shouldBeReentrant() {
        AtomicBoolean value = new AtomicBoolean(false);

        executeWithGlobalExclusiveAccess(() -> {
            executeWithGlobalExclusiveAccess(() -> {
                value.set(true);
            });
        });

        assertTrue(value.get());
    }

    @Test
    void executeWithTimedGlobalExclusiveAccess_withNestedCallOnSingleThread_shouldBeReentrant()throws InterruptedException {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        entityLocker.executeWithGlobalExclusiveAccess(() -> {
            try {
                entityLocker.executeWithGlobalExclusiveAccess(atomicInteger::incrementAndGet, 1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            atomicInteger.incrementAndGet();
        }, 1, TimeUnit.MINUTES);

        assertEquals(2, atomicInteger.get());
    }

    @Test
    void executeWithGlobalAndEntityExclusiveAccess_withNestedCallOnSingleThread_shouldBeReentrant() {
        AtomicBoolean value = new AtomicBoolean(false);

        executeWithGlobalExclusiveAccess(() -> {
            entityLocker.executeWithEntityExclusiveAccess("1", () -> {
                value.set(true);
            });
        });

        assertTrue(value.get());
    }

    @Test
    void executeWithTimedGlobalAndEntityExclusiveAccess_withNestedCallOnSingleThread_shouldBeReentrant()throws InterruptedException {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        entityLocker.executeWithGlobalExclusiveAccess(() -> {
            try {
                entityLocker.executeWithEntityExclusiveAccess("1", atomicInteger::incrementAndGet, 1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            atomicInteger.incrementAndGet();
        }, 1, TimeUnit.MINUTES);

        assertEquals(2, atomicInteger.get());
    }

    @Test
    void executeWithEntityAndGlobalExclusiveAccess_withNestedCallOnSingleThread_shouldBeReentrant() {
        AtomicBoolean value = new AtomicBoolean(false);

        entityLocker.executeWithEntityExclusiveAccess("1", () -> {
            executeWithGlobalExclusiveAccess(() -> value.set(true));
        });

        assertTrue(value.get());
    }

    @Test
    void executeWithTimedEntityAndGlobalExclusiveAccess_withNestedCallOnSingleThread_shouldBeReentrant()throws InterruptedException {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        entityLocker.executeWithEntityExclusiveAccess("1", () -> {
            try {
                entityLocker.executeWithGlobalExclusiveAccess(atomicInteger::incrementAndGet, 1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            atomicInteger.incrementAndGet();
        }, 1, TimeUnit.MINUTES);

        assertEquals(2, atomicInteger.get());
    }

    @Test
    void executeWithEntityExclusiveAccess_withMultipleThreads_shouldNotDeadlock() {
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

    private void executeWithGlobalExclusiveAccess(ProtectedCode protectedCode) {
        try {
            entityLocker.executeWithGlobalExclusiveAccess(protectedCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
