package entitylocker;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ThreadEntityGraph<T> {
    private final Map<T, Long> entityThreads = new HashMap<>();
    private final Map<Long, Set<T>> threadEntities = new HashMap<>();

    boolean isAssociatedWithEntities(long threadId) {
        return !threadEntities.getOrDefault(threadId, Collections.emptySet()).isEmpty();
    }

    Set<T> getAssociatedEntities(long threadId) {
        return threadEntities.getOrDefault(threadId, Collections.emptySet());
    }

    Optional<Long> getAssociatedThread(T entityId) {
        return Optional.ofNullable(entityThreads.get(entityId));
    }

    synchronized void addThreadEntityAssociation(long threadId, T entityId) {
        entityThreads.put(entityId, threadId);
        threadEntities.computeIfAbsent(threadId, tId -> new HashSet<>())
                .add(entityId);
    }

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