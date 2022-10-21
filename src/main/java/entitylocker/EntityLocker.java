package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import java.util.concurrent.TimeUnit;

/**
 * Provides exclusive access to entities with primary keys of type T
 *
 * @param <T> Entity id data type
 */
public interface EntityLocker<T> {

    /**
     * Executes the protected code with exclusive access to the entity.
     *
     * @param entityId The id of the entity
     * @param protectedCode protected code to be executed
     * @throws DeadLockPreventionException in case of possible deadlock detection detected in the internal locks
     */
    void executeWithEntityExclusiveAccess(T entityId, Runnable protectedCode) throws DeadLockPreventionException;

    boolean executeWithEntityExclusiveAccess(T entityId, Runnable protectedCode, long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException;

    void executeWithGlobalExclusiveAccess(Runnable protectedCode);

    boolean executeWithGlobalExclusiveAccess(Runnable protectedCode, long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException;
}
