package entitylocker;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a graph, where each node is an entity id or a thread id.
 * The goal is to save info of what entities the thread is locking and what thread is locking a specific entity.
 * An entity can be locked by one thread but one thread can lock many entities.
 * @param <T> data type of the entity primary key
 */
class ThreadEntityGraph<T> {
    private final Map<T, Long> entityThreads = new HashMap<>();
    private final Map<Long, Set<T>> threadEntities = new HashMap<>();

    /**
     * @param threadId id of the thread
     * @return true if the thread has entities
     */
    boolean isAssociatedWithEntities(long threadId) {
        return !threadEntities.getOrDefault(threadId, Collections.emptySet()).isEmpty();
    }

    /**
     * @param threadId id of the thread
     * @return associated entities of a thread
     */
    Set<T> getAssociatedEntities(long threadId) {
        return threadEntities.getOrDefault(threadId, Collections.emptySet());
    }

    /**
     * @param entityId id of the entity
     * @return thread id associated with the entity
     */
    Optional<Long> getAssociatedThread(T entityId) {
        return Optional.ofNullable(entityThreads.get(entityId));
    }

    /**
     * Associates a thread with an entity
     *
     * @param threadId Id of the thread
     * @param entityId Id of the entity
     */
    synchronized void addThreadEntityAssociation(long threadId, T entityId) {
        entityThreads.put(entityId, threadId);
        threadEntities.computeIfAbsent(threadId, tId -> new HashSet<>())
                .add(entityId);
    }

    /**
     * Removes the association between the entity and thread holding it
     *
     * @param entityId Id of the entity
     */
    synchronized void removeEntityThreadAssociation(T entityId) {
        if (!entityThreads.containsKey(entityId)) {
            return;
        }

        long threadId = entityThreads.remove(entityId);
        threadEntities.get(threadId).remove(entityId);
        if (threadEntities.get(threadId).isEmpty()) {
            threadEntities.remove(threadId);
        }
    }
}