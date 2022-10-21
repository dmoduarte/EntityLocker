package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Set;

/**
 * Util class to check for possible deadlocks
 */
public class EntityDeadLockChecker {
    private EntityDeadLockChecker() {

    }

    /**
     * Checks for possible deadlocks by traversing the {@link ThreadEntityGraph}, if a cycle is detected then there is
     * the probability of occurring a deadlock.
     * <p>
     * Starting at the node of the acquiringThread it fetches the current acquired entities and traverses each
     * entity node and queries their threads.
     * <p>
     * This is done recursively until we have no more nodes to process or if we reach to an entity equal to the one
     * we are trying to acquire. If the latter happens then there is a deadlock.
     *
     * @param threadEntityGraph {@link ThreadEntityGraph}
     * @param acquiringThread   acquiringThread
     * @param entityId          id of the entity
     * @param <T>               data type of the entity id
     * @throws DeadLockPreventionException in case a deadlock is detected
     */
    public static <T> void checkForDeadLock(
            ThreadEntityGraph<T> threadEntityGraph,
            long acquiringThread,
            T entityId
    ) throws DeadLockPreventionException {
        Set<T> entitiesAcquiredByThread = threadEntityGraph.getAssociatedEntities(acquiringThread);
        if (entitiesAcquiredByThread.isEmpty()) {
            return;
        }

        LinkedList<T> entityQueue = new LinkedList<>(entitiesAcquiredByThread);
        Set<Long> visitedThreads = new HashSet<>();
        visitedThreads.add(acquiringThread);

        while (!entityQueue.isEmpty()) {
            T currentEntityId = entityQueue.removeFirst();

            Set<Long> associatedThreads = threadEntityGraph.getAssociatedThreads(currentEntityId);

            for (Long currentThreadId : associatedThreads) {
                if (currentEntityId == entityId && currentThreadId != acquiringThread) {
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
