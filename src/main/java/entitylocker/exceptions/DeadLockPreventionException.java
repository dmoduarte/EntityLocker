package entitylocker.exceptions;

public class DeadLockPreventionException extends RuntimeException {
    public DeadLockPreventionException() {
        super("Possible deadlock detected");
    }
}
