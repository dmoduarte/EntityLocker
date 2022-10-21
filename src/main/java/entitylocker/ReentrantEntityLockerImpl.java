package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Reentrant implementation of {@link EntityLocker}.
 *
 * @param <T>
 */
public class ReentrantEntityLockerImpl<T> implements EntityLocker<T> {
    private static final int NO_ESCALATION_VALUE = -1;

    private final ReentrantEntityLock<T> entityLock = new ReentrantEntityLock<>();
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock globalWriteLock = globalLock.writeLock();
    private final ReentrantReadWriteLock.ReadLock globalReadLock = globalLock.readLock();
    private final ThreadEntityGraph<T> threadEntityGraph = new ThreadEntityGraph<>();
    private final ThreadLocal<Boolean> threadIsEscalated = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final int escalationThreshold;

    public ReentrantEntityLockerImpl(int escalationThreshold) {
        this.escalationThreshold = escalationThreshold;
    }

    public ReentrantEntityLockerImpl() {
        this(NO_ESCALATION_VALUE);
    }

    @Override
    public void executeWithEntityExclusiveAccess(T entityId, Runnable protectedCode) throws DeadLockPreventionException {
        acquireEntityLock(entityId);

        try {
            protectedCode.run();
        } finally {
            releaseEntityLock(entityId);
        }
    }

    @Override
    public boolean executeWithEntityExclusiveAccess(T entityId, Runnable protectedCode, long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException {
        if (!acquireEntityLock(entityId, waitLockTimeout, timeUnit)) {
            //could not acquire lock
            return false;
        }

        try {
            protectedCode.run();
            return true;
        } finally {
            releaseEntityLock(entityId);
        }
    }

    @Override
    public void executeWithGlobalExclusiveAccess(Runnable protectedCode) {
        acquireGlobalLock();

        try {
            protectedCode.run();
        } finally {
            releaseGlobalLock();
        }
    }

    private void acquireGlobalLock() {
        releaseAllEntityLocks();

        globalWriteLock.lock();
    }

    @Override
    public boolean executeWithGlobalExclusiveAccess(Runnable protectedCode, long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException {
        boolean locked = globalWriteLock.tryLock(waitLockTimeout, timeUnit);
        if (!locked) {
            return false;
        }

        try {
            protectedCode.run();
            return true;
        } finally {
            releaseGlobalLock();
        }
    }

    private void acquireEntityLock(T entityId) throws DeadLockPreventionException {
        long currentThreadId = Thread.currentThread().getId();

        checkForDeadLock(currentThreadId, entityId);

        if (escalateEntityLockIfNecessary()) {
            return;
        }

        threadEntityGraph.addThreadEntityAssociation(currentThreadId, entityId);

        acquireGlobalReadAndEntityLock(entityId);
    }

    private void checkForDeadLock(long acquiringThread, T entityId) throws DeadLockPreventionException {
        Set<T> entitiesAcquiredByThread = threadEntityGraph.getAssociatedEntities(acquiringThread);
        if (entitiesAcquiredByThread.isEmpty()) {
            return;
        }

        LinkedList<T> entityQueue = new LinkedList<>(entitiesAcquiredByThread);
        while (!entityQueue.isEmpty()) {
            T currentEntityId = entityQueue.removeFirst();

            Optional<Long> holdingThread = threadEntityGraph.getAssociatedThread(currentEntityId);

            if (holdingThread.isPresent()) {
                long holdingThreadId = holdingThread.get();
                if (currentEntityId == entityId && acquiringThread != holdingThreadId) {
                    throw new DeadLockPreventionException();
                }

                if (holdingThreadId != acquiringThread) {
                    Set<T> entities = threadEntityGraph.getAssociatedEntities(holdingThreadId);
                    entityQueue.addAll(entities);
                }
            }
        }
    }

    private boolean escalateEntityLockIfNecessary() {
        if (!shouldEscalateFromEntityToGlobalLock()) {
            return false;
        }

        releaseAllEntityLocks();

        globalWriteLock.lock();

        threadIsEscalated.set(true);

        return true;
    }

    private void releaseAllEntityLocks() {
        long currentThread = Thread.currentThread().getId();
        if (threadEntityGraph.isAssociatedWithEntities(currentThread)) {
            Set<T> entityLocks = threadEntityGraph.getAssociatedEntities(currentThread);

            for (T entity : entityLocks) {
                globalReadLock.unlock();
                entityLock.unlock(entity);
            }
        }
    }

    private void reAcquireAllEntityLocks() {
        long currentThread = Thread.currentThread().getId();
        if (threadEntityGraph.isAssociatedWithEntities(currentThread)) {
            Set<T> entityLocks = threadEntityGraph.getAssociatedEntities(currentThread);

            for (T entity : entityLocks) {
                acquireGlobalReadAndEntityLock(entity);
            }
        }
    }

    private boolean shouldEscalateFromEntityToGlobalLock() {
        long currentThreadId = Thread.currentThread().getId();
        return escalationThreshold != NO_ESCALATION_VALUE
                && threadEntityGraph.getAssociatedEntities(currentThreadId).size() > (escalationThreshold - 1);
    }

    private boolean acquireEntityLock(T entityId, long timeoutLock, TimeUnit timeUnit) throws InterruptedException {
        long t0 = System.nanoTime();

        boolean locked = globalReadLock.tryLock(timeoutLock, timeUnit);
        if (!locked) {
            return false;
        }

        long elapsedNanos = System.nanoTime() - t0;
        long remainingWaitingTime = getRemaining(timeUnit.toNanos(timeoutLock), elapsedNanos, timeUnit);

        return entityLock.tryLock(entityId, remainingWaitingTime, timeUnit);
    }

    private void releaseEntityLock(T entityId) {
        if (!threadIsEscalated.get()) {
            //check first if it is held, since it could have been escalated
            entityLock.unlock(entityId);
            globalReadLock.unlock();
            if (entityLock.getHoldCount(entityId) == 0) {
                threadEntityGraph.removeEntityThreadAssociation(entityId);
            }

            threadIsEscalated.remove();

            return;
        }

        releaseGlobalLock();
    }

    private void releaseGlobalLock() {
        globalWriteLock.unlock();
        reAcquireAllEntityLocks();
        threadIsEscalated.remove();
    }

    private void acquireGlobalReadAndEntityLock(T entityId) {
        globalReadLock.lock();
        entityLock.lock(entityId);
    }

    /*
     * Returns remaining or zero if negative
     */
    private long getRemaining(long totalNanos, long subtractNanos, TimeUnit convertResultToUnit) {
        return convertResultToUnit.convert(Math.max(totalNanos - subtractNanos, 0), TimeUnit.NANOSECONDS);
    }
}