package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
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

    private final ThreadLocal<LockEscalation> currentThreadLockEscalation = ThreadLocal.withInitial(LockEscalation::noEscalation);
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
    public void executeWithEntityExclusiveAccess(T entityId, ProtectedCode protectedCode) throws DeadLockPreventionException {
        acquireEntityLock(entityId);

        try {
            protectedCode.run();
        } finally {
            releaseEntityLock(entityId);
        }
    }

    @Override
    public boolean executeWithEntityExclusiveAccess(T entityId, ProtectedCode protectedCode, long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException {
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
    public void executeWithGlobalExclusiveAccess(ProtectedCode protectedCode) throws InterruptedException {
        acquireGlobalLock();

        try {
            protectedCode.run();
        } finally {
            releaseGlobalLock();
        }
    }

    @Override
    public boolean executeWithGlobalExclusiveAccess(ProtectedCode protectedCode, long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException {
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
        if (currentThreadHasLockedManyEntities()) {
            escalateCurrentThreadLocks(LockEscalation.escalateDueToMultipleEntityLock());
            return;
        }

        checkForDeadLockAndUpdateGraph(Thread.currentThread().getId(), entityId);

        entityLock.lock(entityId);
        globalReadLock.lock();
    }

    private synchronized void checkForDeadLockAndUpdateGraph(long currentThreadId, T entityId) {
        EntityDeadLockChecker.checkForDeadLock(threadEntityGraph, currentThreadId, entityId);
        threadEntityGraph.addThreadEntityAssociation(currentThreadId, entityId);
    }

    private boolean acquireEntityLock(T entityId, long timeoutLock, TimeUnit timeUnit) throws InterruptedException {
        if (currentThreadHasLockedManyEntities()) {
            return escalateCurrentThreadWithTimeout(timeoutLock, timeUnit, LockEscalation.escalateDueToMultipleEntityLock());
        }

        long t0 = System.nanoTime();

        boolean locked = entityLock.tryLock(entityId, timeoutLock, timeUnit);

        if (!locked) {
            return false;
        }

        long elapsedNanos = System.nanoTime() - t0;
        long remainingWaitingTime = getRemainingNanos(timeUnit.toNanos(timeoutLock), elapsedNanos);

        locked = globalReadLock.tryLock(remainingWaitingTime, TimeUnit.NANOSECONDS);

        if (locked) {
            threadEntityGraph.addThreadEntityAssociation(Thread.currentThread().getId(), entityId);
        }

        return locked;
    }

    private void escalateCurrentThreadLocks(LockEscalation lockEscalation) {
        if (currentThreadLockIsEscalated()) {
            globalWriteLock.lock();
            updateCurrentThreadEscalation(lockEscalation);
            return;
        }
        //used by escalatingThreadsCondition this count forces any write lock waiting while this read lock is escalated
        escalatingThreadsCount.incrementAndGet();

        releaseAllReadLocks();

        globalWriteLock.lock();

        //If this Thread current protected code is a sub-protected code, parent protected code(s) are escalated as well
        escalateParentProtectedCodes();
        currentThreadLockEscalation.set(lockEscalation);
        finishGlobalEscalation();
    }

    private boolean escalateCurrentThreadWithTimeout(long waitTimeoutForGlobalLock, TimeUnit timeUnit, LockEscalation lockEscalation) throws InterruptedException {
        if (currentThreadLockIsEscalated()) {
            globalWriteLock.lock();
            updateCurrentThreadEscalation(lockEscalation);
            return true;
        }

        //used by escalatingThreadsCondition this count forces any write lock waiting while this read lock is escalated
        escalatingThreadsCount.incrementAndGet();

        releaseAllReadLocks();

        boolean locked = globalWriteLock.tryLock(waitTimeoutForGlobalLock, timeUnit);
        if (!locked) {
            //escalation timed out so re-acquire previous lock level
            reAcquireAllReadLocks();
            finishGlobalEscalation();
            return false;
        }

        //If this Thread current protected code is a sub-protected code, parent protected code(s) are escalated as well
        escalateParentProtectedCodes();
        currentThreadLockEscalation.set(lockEscalation);
        finishGlobalEscalation();

        return true;
    }

    private void releaseEntityLock(T entityId) {
        boolean shouldReleaseGlobalWriteLock = currentThreadLockIsEscalated() && !currentThreadLockIsEscalatedTemporarily();
        if (shouldReleaseGlobalWriteLock) {
            //if was escalated, release write lock
            globalWriteLock.unlock();

            if (globalWriteLock.getHoldCount() == 0) {
                currentThreadLockEscalation.remove();
            }
        } else {
            globalReadLock.unlock();
        }

        releaseEntityLock(Thread.currentThread().getId(), entityId);
    }

    private void releaseEntityLock(long threadId, T entityId) {
        entityLock.unlock(entityId);
        if (entityLock.getHoldCount(entityId) == 0) {
            threadEntityGraph.removeThreadEntityAssociation(threadId, entityId);
        }
    }

    private void acquireGlobalLock() throws InterruptedException {
        if (currentThreadHasEntityAccess()) {
            /*
             If current thread already has entity access then it has a global read lock, temporarily escalate its global
             read to write lock to ensure reentrancy, otherwise would deadlock as ReentrantReadWriteLock does not let to
             upgrade from read to write lock.

             De-escalation should happen when this current global write lock is unlocked
             */
            escalateCurrentThreadLocks(LockEscalation.temporaryEscalation());
            return;
        }

        globalWriteLock.lock();
        while (escalatingThreadsCount.get() > 0) {
            escalatingThreadsCondition.await();
        }
    }

    private boolean acquireGlobalLock(long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException {
        if (currentThreadHasEntityAccess()) {
             /*
             If current thread already has entity access then it has a global read lock, temporarily escalate its global
             read to write lock to ensure reentrancy, otherwise would deadlock as ReentrantReadWriteLock does not let to
             upgrade from read to write lock.

             De-escalation should happen once this current global write lock is unlocked
             */
            return escalateCurrentThreadWithTimeout(waitLockTimeout, timeUnit, LockEscalation.temporaryEscalation());
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

    private boolean currentThreadHasEntityAccess() {
        return !threadEntityGraph.getAssociatedEntities(Thread.currentThread().getId()).isEmpty();
    }

    private void releaseGlobalLock() {
        globalWriteLock.unlock();

        if (currentThreadLockIsEscalatedTemporarily()) {
            //parent read locks may have been escalated to ensure reentrancy. (ReentrantReadWrite lock does not let upgrade of locks)
            deEscalateToReadLock();
        }
    }

    private boolean currentThreadHasLockedManyEntities() {
        long currentThreadId = Thread.currentThread().getId();
        return escalationThreshold != NO_ESCALATION_VALUE
                && threadEntityGraph.getAssociatedEntities(currentThreadId).size() > (escalationThreshold - 1);
    }

    private void updateCurrentThreadEscalation(LockEscalation newLockEscalation) {
        boolean shouldOverride = currentThreadLockEscalation.get().isEscalatedTemporarily()
                && newLockEscalation.isEscalated()
                && !newLockEscalation.isEscalatedTemporarily();

        if (shouldOverride) {
            currentThreadLockEscalation.set(newLockEscalation);
        }
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

    private void releaseAllWriteLocks() {
        long currentThread = Thread.currentThread().getId();
        IntStream.range(0, threadEntityGraph.getAssociatedEntities(currentThread).size())
                .forEach(i -> globalWriteLock.unlock());
    }

    private void reAcquireAllReadLocks() {
        long currentThread = Thread.currentThread().getId();
        IntStream.range(0, threadEntityGraph.getAssociatedEntities(currentThread).size())
                .forEach(i -> globalReadLock.lock());
    }

    private boolean currentThreadLockIsEscalated() {
        return currentThreadLockEscalation.get().isEscalated();
    }

    private boolean currentThreadLockIsEscalatedTemporarily() {
        return currentThreadLockEscalation.get().isEscalatedTemporarily();
    }

    private void deEscalateToReadLock() {
        reAcquireAllReadLocks();
        releaseAllWriteLocks();
        currentThreadLockEscalation.remove();
    }

    private void finishGlobalEscalation() {
        escalatingThreadsCount.decrementAndGet();
        escalatingThreadsCondition.signalAll();
    }

    /*
     * Returns remaining or zero if negative
     */
    private long getRemainingNanos(long totalNanos, long subtractNanos) {
        return Math.max(totalNanos - subtractNanos, 0);
    }

    /**
     * Has information on whether current thread lock has been escalated or not
     * And in case it is escalated, if it is a temporary escalation or not
     */
    private static class LockEscalation {
        private final boolean isEscalated;
        private final boolean isTemporary;

        private LockEscalation(boolean isEscalated, boolean isTemporary) {
            this.isEscalated = isEscalated;
            this.isTemporary = isTemporary;
        }

        private static LockEscalation escalateDueToMultipleEntityLock() {
            return new LockEscalation(true, false);
        }

        private static LockEscalation temporaryEscalation() {
            return new LockEscalation(true, true);
        }

        private static LockEscalation noEscalation() {
            return new LockEscalation(false, false);
        }

        private boolean isEscalated() {
            return isEscalated;
        }

        private boolean isEscalatedTemporarily() {
            return isEscalated && isTemporary;
        }
    }
}