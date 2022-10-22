package entitylocker;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a graph, where each node is an entity id or a thread id.
 * <p>
 * Each thread can be related to multiple entities and each entity with multiple threads.
 * <p>
 * The goal is to save what threads are waiting to acquire or has a lock to a specific entity.
 *
 * @param <T> data type of the entity primary key
 */
class ThreadEntityGraph<T> {
    private final Map<T, Set<Long>> entityThreads = new HashMap<>();
    private final Map<Long, Set<T>> threadEntities = new HashMap<>();

    /**
     * @param threadId id of the thread
     * @return associated entities of a thread
     */
    Set<T> getAssociatedEntities(long threadId) {
        return threadEntities.getOrDefault(threadId, Collections.emptySet());
    }

    /**
     * @param entityId id of the entity
     * @return associated threads of an entity
     */
    Set<Long> getAssociatedThreads(T entityId) {
        return entityThreads.getOrDefault(entityId, Collections.emptySet());
    }

    /**
     * Associates a thread with an entity
     *
     * @param threadId Id of the thread
     * @param entityId Id of the entity
     */
    synchronized void addThreadEntityAssociation(long threadId, T entityId) {
        entityThreads.computeIfAbsent(entityId, eId -> new HashSet<>())
                .add(threadId);

        threadEntities.computeIfAbsent(threadId, tId -> new HashSet<>())
                .add(entityId);
    }

    /**
     * Removes the association between the entity and thread holding it
     *
     * @param entityId Id of the entity
     */
    synchronized void removeThreadEntityAssociation(long threadId, T entityId) {
        if (!entityThreads.containsKey(entityId)) {
            return;
        }

        if (!threadEntities.containsKey(threadId)) {
            return;
        }

        Set<Long> threads = Optional.ofNullable(entityThreads.get(entityId))
                .orElseGet(Collections::emptySet);

        Set<T> entities = Optional.ofNullable(threadEntities.get(threadId))
                .orElseGet(Collections::emptySet);

        threads.remove(threadId);
        entities.remove(entityId);

        if (threads.isEmpty()) {
            entityThreads.remove(entityId);
        }

        if (entities.isEmpty()) {
            threadEntities.remove(threadId);
        }
    }
}