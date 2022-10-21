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
    void executeWithEntityLock_multipleDistinctEntityLocksWithinSameThread_entityLockLevelShouldEscalate() throws InterruptedException {
        EntityLocker<Integer> entityLocker = new ReentrantEntityLockerImpl<>(2);

        CountDownLatch latch = new CountDownLatch(1);

        Runnable firstRunnable =
                () -> entityLocker.executeWithEntityExclusiveAccess(1,
                        () -> entityLocker.executeWithEntityExclusiveAccess(2,
                                () -> entityLocker.executeWithEntityExclusiveAccess(3, executeSlowTask(latch))
                        )
                );

        AtomicBoolean acquiredLock = new AtomicBoolean(true);
        Runnable secondRunnable = () -> {
            try {
                acquiredLock.set(entityLocker.executeWithEntityExclusiveAccess(4, NO_OP, 10, TimeUnit.MILLISECONDS));
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

        //could not acquire lock as 1st thread has global lock and is waiting for a long time
        Assertions.assertFalse(acquiredLock.get());
    }

    @Test
    void executeWithEntityLock_multipleDistinctEntityLocksWithTimeoutWithinSameThread_entityLockLevelShouldEscalate() throws InterruptedException {
        EntityLocker<Integer> entityLocker = new ReentrantEntityLockerImpl<>(2);

        CountDownLatch latch = new CountDownLatch(1);

        Runnable firstRunnable = () -> {
            executeWithEntityLockAndWithLockTimeout(entityLocker, 1,
                    () -> executeWithEntityLockAndWithLockTimeout(entityLocker, 2,
                            () -> executeWithEntityLockAndWithLockTimeout(entityLocker, 3, executeSlowTask(latch))
                    )
            );
        };

        AtomicBoolean acquiredLock = new AtomicBoolean(true);
        Runnable secondRunnable = () -> {
            try {
                acquiredLock.set(entityLocker.executeWithEntityExclusiveAccess(4, NO_OP, 10, TimeUnit.MILLISECONDS));
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

    private Runnable executeSlowTask(CountDownLatch latch) {
        return () -> {
            try {
                latch.countDown();
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    private void executeWithEntityLockAndWithLockTimeout(EntityLocker<Integer> entityLocker, int entityId, Runnable runnable) {
        try {
            entityLocker.executeWithEntityExclusiveAccess(entityId, runnable, 5, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
