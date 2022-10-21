package entitylocker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TimeoutTests {
    @Test
    void executeWithEntityExclusiveAccess_withTimeoutSetting_shouldTimeoutAfter50Ms() throws InterruptedException {
        EntityLocker<Long> entityLocker = new ReentrantEntityLockerImpl<>();

        int numberOfThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicBoolean allAcquiredTheLock = new AtomicBoolean(true);

        Runnable protectedSleepyCode = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable task = () -> {
            try {
                boolean acquiredLock = entityLocker.executeWithEntityExclusiveAccess(
                        1L,
                        protectedSleepyCode,
                        50,
                        TimeUnit.MILLISECONDS
                );
                allAcquiredTheLock.set(allAcquiredTheLock.get() && acquiredLock);
            } catch (InterruptedException e) {
                allAcquiredTheLock.get();
            } finally {
                latch.countDown();
            }
        };

        for (int i = 0; i < numberOfThreads; i++) {
            service.submit(task);
        }

        latch.await();
        assertFalse(allAcquiredTheLock.get());
    }

    @Test
    void executeWithGlobalExclusiveAccess_withTimeoutSetting_shouldTimeoutAfter50Ms() throws InterruptedException {
        EntityLocker<Long> entityLocker = new ReentrantEntityLockerImpl<>();
        int numberOfThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicBoolean allAcquiredTheLock = new AtomicBoolean(true);

        Runnable protectedSleepyCode = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable task = () -> {
            try {
                boolean acquiredLock = entityLocker.executeWithGlobalExclusiveAccess(
                        protectedSleepyCode,
                        50,
                        MILLISECONDS
                );
                allAcquiredTheLock.set(allAcquiredTheLock.get() && acquiredLock);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };

        for (int i = 0; i < numberOfThreads; i++) {
            service.submit(task);
        }

        latch.await();
        assertFalse(allAcquiredTheLock.get());
    }

    @Test
    void executeWithGlobalAndEntityExclusiveAccess_withTimeoutSetting_shouldTimeoutAfter50Ms() throws InterruptedException {
        EntityLocker<Long> entityLocker = new ReentrantEntityLockerImpl<>();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean acquiredLock = new AtomicBoolean(true);

        Runnable protectedSleepyCode = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable globalTask = () -> {
            latch.countDown();
            entityLocker.executeWithGlobalExclusiveAccess(protectedSleepyCode);
        };

        Runnable entityTask = () -> {
            try {
                acquiredLock.set(entityLocker.executeWithGlobalExclusiveAccess(protectedSleepyCode, 10, MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread thread1 = new Thread(globalTask);
        Thread thread2 = new Thread(entityTask);

        thread1.start();
        latch.await();
        thread2.start();

        thread1.join();
        thread2.join();

        assertFalse(acquiredLock.get());
    }

}
