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

    /**
     * Executed protected code with exclusive access to the entity, will time out if the thread did not acquire
     * the locked within the specified waitLockTimeout
     *
     * @param entityId Id of the entity
     * @param protectedCode protected code to be executed
     * @param waitLockTimeout max time to wait for the lock
     * @param timeUnit time unit
     * @return true if the lock was acquired and the protected code executed, false otherwise
     * @throws InterruptedException if the current thread is interrupted
     */
    boolean executeWithEntityExclusiveAccess(T entityId, Runnable protectedCode, long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException;

    /**
     * Executes the protected code with global exclusive access.
     *
     * @param protectedCode protected code to be executed
     * @throws DeadLockPreventionException in case of possible deadlock detection detected in the internal locks
     */
    void executeWithGlobalExclusiveAccess(Runnable protectedCode);

    /**
     * Executed protected code with global exclusive access, will time out if the thread did not acquire
     * the locked within the specified waitLockTimeout
     *
     * @param protectedCode protected code to be executed
     * @param waitLockTimeout max time to wait for the lock
     * @param timeUnit time unit
     * @return true if the lock was acquired and the protected code executed, false otherwise
     * @throws InterruptedException if the current thread is interrupted
     */
    boolean executeWithGlobalExclusiveAccess(Runnable protectedCode, long waitLockTimeout, TimeUnit timeUnit) throws InterruptedException;
}
