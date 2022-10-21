package entitylocker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EscalationTests {

    private static final Runnable NO_OP = () -> {
    };

    @Test
    void executeWithEntityExclusiveAccess_multipleDistinctEntityLockAcquisition_lockLevelShouldEscalate() throws InterruptedException {
        EntityLocker<Integer> entityLocker = new ReentrantEntityLockerImpl<>(3);

        CountDownLatch latch = new CountDownLatch(1);

        Runnable firstRunnable = () -> {
            entityLocker.executeWithEntityExclusiveAccess(1, () -> {
                entityLocker.executeWithEntityExclusiveAccess(2, () -> {
                    entityLocker.executeWithEntityExclusiveAccess(3, () -> {
                        entityLocker.executeWithEntityExclusiveAccess(4, () -> {
                            try {
                                latch.countDown();
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                    });
                });
            });
        };

        AtomicBoolean acquiredLock = new AtomicBoolean(true);
        Runnable secondRunnable = () -> {
            try {
                acquiredLock.set(
                        entityLocker.executeWithEntityExclusiveAccess(
                                5,
                                NO_OP,
                                10,
                                TimeUnit.MILLISECONDS
                        )
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread thread1 = new Thread(firstRunnable);
        Thread thread2 = new Thread(secondRunnable);

        thread1.start();
        latch.await();
        thread2.start();

        thread1.join();
        thread2.join();

        Assertions.assertFalse(acquiredLock.get());
    }
}
