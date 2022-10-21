package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Set;

/**
 * Util class to check for possible dead locks
 */
public class EntityDeadLockChecker {
    private EntityDeadLockChecker() {

    }

    /**
     * Checks for possible deadlocks
     *
     * @param threadEntityGraph
     * @param acquiringThread
     * @param entityId
     * @param <T>
     * @throws DeadLockPreventionException
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

}
