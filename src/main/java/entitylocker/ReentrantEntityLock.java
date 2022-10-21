package entitylocker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

class ReentrantEntityLock<T> {
    private final Map<T, ReentrantLock> entityLocks = new ConcurrentHashMap<>();

    void lock(T entityId) {
        acquireLockAndUpdateMapAtomically(entityId);
    }

    boolean tryLock(T entityId, long timeout, TimeUnit timeUnit) {
        return acquireLockAndUpdateMapAtomically(entityId, timeUnit, timeout);
    }

    int getHoldCount(T entityId) {
        ReentrantLock entityLock = entityLocks.get(entityId);

        if (entityLock == null) {
            return 0;
        }

        return entityLock.getHoldCount();
    }

    void unlock(T entityId) {
        ReentrantLock entityLock = entityLocks.get(entityId);

        if (entityLock == null) {
            return;
        }

        entityLock.unlock();

        if (!entityLock.hasQueuedThreads() && entityLock.getHoldCount() == 0) {
            entityLocks.remove(entityId);
        }
    }

    void acquireLockAndUpdateMapAtomically(T entityId) {
        entityLocks.compute(entityId, (eId, existingLock) -> {
            if (existingLock == null) {
                ReentrantLock newLock = new ReentrantLock();
                newLock.lock();
                return newLock;
            }

            existingLock.lock();

            return existingLock;
        });
    }

    boolean acquireLockAndUpdateMapAtomically(T entityId, TimeUnit timeUnit, long timeout) {
        ReentrantLock newOrExistingLock = entityLocks.compute(entityId, (eId, existingLock) -> {
            try {
                if (existingLock == null) {
                    ReentrantLock newLock = new ReentrantLock();
                    newLock.tryLock(timeout, timeUnit);
                    return newLock;
                }

                existingLock.tryLock(timeout, timeUnit);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return existingLock;
        });

        return newOrExistingLock != null && newOrExistingLock.isHeldByCurrentThread();
    }
}