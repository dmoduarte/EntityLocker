package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;

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
    private final AtomicLong escalatingThreadsCount = new AtomicLong(0L);
    private final Condition escalatingThreadsCondition = globalWriteLock.newCondition();

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
    public void executeWithGlobalExclusiveAccess(Runnable protectedCode) throws InterruptedException {
        acquireGlobalLock();

        try {
            protectedCode.run();
        } finally {
            releaseGlobalLock();
        }
    }

    @Override
    public boolean executeWithGlobalExclusiveAccess(Runnable protectedCode, long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException {
        if (!acquireGlobalLock(waitLockTimeout, timeUnit)) {
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
        if (shouldEscalateToGlobalLock()) {
            escalateToGlobalLock();
            return;
        }

        long currentThreadId = Thread.currentThread().getId();

        EntityDeadLockChecker.checkForDeadLock(threadEntityGraph, currentThreadId, entityId);

        threadEntityGraph.addThreadEntityAssociation(currentThreadId, entityId);

        globalReadLock.lock();
        entityLock.lock(entityId);
    }

    private void escalateToGlobalLock() {
        //used by escalatingThreadsCondition this count forces any write lock waiting while this read lock is escalated
        escalatingThreadsCount.incrementAndGet();

        releaseAllReadLocks();

        globalWriteLock.lock();

        //If this Thread current protected code is a sub-protected code, parent protected code(s) are escalated as well
        escalateParentProtectedCodes();

        threadIsEscalated.set(true);
    }

    private boolean escalateToGlobalLockWithTimeout(long waitTimeoutForGlobalLock, TimeUnit timeUnit) throws InterruptedException {
        //used by escalatingThreadsCondition this count forces any write lock waiting while this read lock is escalated
        escalatingThreadsCount.incrementAndGet();

        releaseAllReadLocks();

        boolean locked = globalWriteLock.tryLock(waitTimeoutForGlobalLock, timeUnit);
        if (!locked) {
            //escalation timed out so re-acquire previous lock level
            reAcquireAllReadLocks();
            return false;
        }

        //If this Thread current protected code is a sub-protected code, parent protected code(s) are escalated as well
        escalateParentProtectedCodes();

        threadIsEscalated.set(true);
        return true;
    }

    private void escalateParentProtectedCodes() {
        int holdingEntities = threadEntityGraph.getAssociatedEntities(Thread.currentThread().getId()).size();
        IntStream.range(0, holdingEntities).forEach(i -> globalWriteLock.lock());
    }

    private void releaseAllReadLocks() {
        long currentThread = Thread.currentThread().getId();
        IntStream.range(0, threadEntityGraph.getAssociatedEntities(currentThread).size())
                .forEach(i -> globalReadLock.unlock());
    }

    private void reAcquireAllReadLocks() {
        long currentThread = Thread.currentThread().getId();
        IntStream.range(0, threadEntityGraph.getAssociatedEntities(currentThread).size())
                .forEach(i -> globalReadLock.lock());
    }

    private boolean lockIsEscalated() {
        return threadIsEscalated.get();
    }

    private boolean shouldEscalateToGlobalLock() {
        long currentThreadId = Thread.currentThread().getId();
        return escalationThreshold != NO_ESCALATION_VALUE
                && threadEntityGraph.getAssociatedEntities(currentThreadId).size() > (escalationThreshold - 1);
    }

    private boolean acquireEntityLock(T entityId, long timeoutLock, TimeUnit timeUnit) throws InterruptedException {
        if (shouldEscalateToGlobalLock()) {
            return escalateToGlobalLockWithTimeout(timeoutLock, timeUnit);
        }

        long t0 = System.nanoTime();

        boolean locked = globalReadLock.tryLock(timeoutLock, timeUnit);
        if (!locked) {
            return false;
        }

        long elapsedNanos = System.nanoTime() - t0;
        long remainingWaitingTime = getRemainingNanos(timeUnit.toNanos(timeoutLock), elapsedNanos);

        locked = entityLock.tryLock(entityId, remainingWaitingTime, TimeUnit.NANOSECONDS);

        if (locked) {
            threadEntityGraph.addThreadEntityAssociation(Thread.currentThread().getId(), entityId);
        }

        return locked;
    }

    private void releaseEntityLock(T entityId) {
        if (lockIsEscalated()) {
            if (globalWriteLock.getHoldCount() == 1) {
                threadIsEscalated.remove();
                escalatingThreadsCount.decrementAndGet();
                escalatingThreadsCondition.signalAll();
            }

            globalWriteLock.unlock();

            releaseEntityLock(Thread.currentThread().getId(), entityId);

            return;
        }

        globalReadLock.unlock();
        releaseEntityLock(Thread.currentThread().getId(), entityId);
    }

    private void releaseEntityLock(long threadId, T entityId) {
        entityLock.unlock(entityId);
        if (entityLock.getHoldCount(entityId) == 0) {
            threadEntityGraph.removeThreadEntityAssociation(threadId, entityId);
        }
    }

    private void releaseGlobalLock() {
        globalWriteLock.unlock();

        //read locks may have been unlocked to ensure reentrancy. Happens when the same thread has global read locks, and at the same time it acquires write lock
        reAcquireAllReadLocks();
    }

    private void acquireGlobalLock() throws InterruptedException {
        if (!threadEntityGraph.getAssociatedEntities(Thread.currentThread().getId()).isEmpty()) {
            releaseAllReadLocks();
        }

        globalWriteLock.lock();
        while (escalatingThreadsCount.get() > 0) {
            escalatingThreadsCondition.await();
        }
    }

    private boolean acquireGlobalLock(long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException {
        if (!threadEntityGraph.getAssociatedEntities(Thread.currentThread().getId()).isEmpty()) {
            releaseAllReadLocks();
        }

        boolean locked = globalWriteLock.tryLock(waitLockTimeout, timeUnit);
        if (!locked) {
            return false;
        }

        while (escalatingThreadsCount.get() > 0) {
            escalatingThreadsCondition.await();
        }

        return true;
    }

    /*
     * Returns remaining or zero if negative
     */
    private long getRemainingNanos(long totalNanos, long subtractNanos) {
        return Math.max(totalNanos - subtractNanos, 0);
    }
}