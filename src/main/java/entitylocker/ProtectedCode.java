package entitylocker;

/**
 * The goal of this interface is to be instantiated with protected code that will be executed by the {@link EntityLocker}
 */
@FunctionalInterface
public interface ProtectedCode {
    /**
     * Runs the protected code
     */
    void run();
}
