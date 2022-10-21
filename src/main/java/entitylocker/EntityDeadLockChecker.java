package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Util class to check for possible deadlocks
 */
class EntityDeadLockChecker {
    private EntityDeadLockChecker() {

    }

    /**
     * Checks for possible deadlocks by traversing the {@link ThreadEntityGraph}, if a cycle is detected then there is
     * the probability of occurring a deadlock.
     * <p>
     * Starting at the node of the acquiringThread it fetches the current associated entities and traverses each
     * entity node to get their related threads.
     * <p>
     * This is done recursively until we have no more entity nodes to check or if we reach to an entity equal to the one
     * we are trying to acquire (entityIdToAcquire). If the latter happens then there is a deadlock.
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
        Set<T> entitiesAcquiredByThread = threadEntityGraph.getAssociatedEntities(acquiringThread);

        if (entitiesAcquiredByThread.isEmpty()) {
            return;
        }

        Queue<T> entityQueue = new LinkedList<>(entitiesAcquiredByThread);
        Set<Long> visitedThreads = new HashSet<>();
        visitedThreads.add(acquiringThread);

        while (!entityQueue.isEmpty()) {
            T currentEntityId = entityQueue.poll();

            Set<Long> associatedThreads = threadEntityGraph.getAssociatedThreads(currentEntityId);

            for (Long currentThreadId : associatedThreads) {

                if (threadEntityGraph.hasTimeoutLock(currentThreadId, currentEntityId)) {
                    //threads with timeout locks are not taken into account as they will eventually release the lock
                    continue;
                }

                if (currentEntityId == entityIdToAcquire && currentThreadId != acquiringThread) {
                    throw new DeadLockPreventionException();
                }

                if (!visitedThreads.contains(currentThreadId)) {
                    entityQueue.addAll(threadEntityGraph.getAssociatedEntities(currentThreadId));
                }

                visitedThreads.add(currentThreadId);
            }

        }
    }

}
