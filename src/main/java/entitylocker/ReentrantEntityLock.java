package entitylocker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Wrapper of a concurrent hash map, has the locks acquired at entity level
 *
 * @param <T> data type of the entity id
 */
class ReentrantEntityLock<T> {
    private final Map<T, ReentrantLock> entityLocks = new ConcurrentHashMap<>();

    /**
     * Locks the entity
     *
     * @param entityId Id of the entity
     */
    void lock(T entityId) {
        acquireLockAndUpdateMapAtomically(entityId);
    }

    /**
     * Tries to acquire the lock at the entity, will time out if the thread did not acquire
     * the lock within the specified waitLockTimeout
     *
     * @param entityId        Id of the entity
     * @param waitLockTimeout max time to wait for the lock
     * @param timeUnit        time unit
     * @return true if the lock was acquired and the protected code executed, false otherwise
     */
    boolean tryLock(T entityId, long waitLockTimeout, TimeUnit timeUnit) {
        return acquireLockAndUpdateMapAtomically(entityId, waitLockTimeout, timeUnit);
    }

    /**
     * @param entityId id of the entity
     * @return the number of holds in this lock by the current thread
     */
    int getHoldCount(T entityId) {
        ReentrantLock entityLock = entityLocks.get(entityId);

        if (entityLock == null) {
            return 0;
        }

        return entityLock.getHoldCount();
    }

    /**
     * Releases the lock on the entity
     * @param entityId id of the entity
     */
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

    private void acquireLockAndUpdateMapAtomically(T entityId) {
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

    private boolean acquireLockAndUpdateMapAtomically(T entityId, long timeout, TimeUnit timeUnit) {
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