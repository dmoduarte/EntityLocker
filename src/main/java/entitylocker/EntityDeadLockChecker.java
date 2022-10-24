package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import java.util.HashSet;
import java.util.Set;

/**
 * Util class to check for possible deadlocks in the {@link EntityLocker}
 */
class EntityDeadLockChecker {
    private EntityDeadLockChecker() {

    }

    /**
     * Checks for possible deadlocks
     *
     * @param threadEntityGraph {@link ThreadEntityGraph}
     * @param acquiringThread   acquiringThread
     * @param entityIdToAcquire          id of the entity the thread is trying to acquire a lock to
     * @param <T>               data type of the entity id
     * @throws DeadLockPreventionException in case a deadlock is detected
     */
    static <T> void checkForDeadLock(
            ThreadEntityGraph<T> threadEntityGraph,
            long acquiringThread,
            T entityIdToAcquire
    ) throws DeadLockPreventionException {
        Set<T> entitiesLockedByCurrentThread = threadEntityGraph.getAssociatedEntities(acquiringThread);

        if (entitiesLockedByCurrentThread.isEmpty()) {
            return;
        }

        Set<Long> visitedThreads = new HashSet<>();
        visitedThreads.add(acquiringThread);

        Set<Long> threadsAssociatedWithEntity = threadEntityGraph.getAssociatedThreads(entityIdToAcquire);

        for (Long thread : threadsAssociatedWithEntity) {
            if (visitedThreads.contains(thread)) {
                continue;
            }

            boolean threadIsDeadLocked = threadEntityGraph.getAssociatedEntities(thread)
                    .stream()
                    .anyMatch(entitiesLockedByCurrentThread::contains);

            if (threadIsDeadLocked) {
                throw new DeadLockPreventionException();
            }
        }
    }

}
