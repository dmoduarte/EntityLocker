package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EntityDeadLockCheckerTests {


    @Test
    void checkDeadLock_T1AcquiringSameLock_shouldNotPreventDeadLockDueToReentrantLock() {
        /*
           T1 -----> [A] ------> acquiring 'A' (does not deadlock as lock is reentrant)
         */
        ThreadEntityGraph<String> threadEntityGraph = new ThreadEntityGraph<>();

        threadEntityGraph.addThreadEntityAssociation(1, "A");
        Assertions.assertDoesNotThrow(() -> EntityDeadLockChecker.checkForDeadLock(threadEntityGraph, 1, "A"));
    }

    @Test
    void checkDeadLock_T2AndT1_shouldNotDeadLock() {
        /*
           T1 ------>[C]----> acquiring 'A'
           T2 -----> [A] -------------------> [B]
         */
        ThreadEntityGraph<String> threadEntityGraph = new ThreadEntityGraph<>();

        threadEntityGraph.addThreadEntityAssociation(1, "C");
        threadEntityGraph.addThreadEntityAssociation(2, "A");
        threadEntityGraph.addThreadEntityAssociation(2, "B");

        Assertions.assertDoesNotThrow(
                () -> EntityDeadLockChecker.checkForDeadLock(threadEntityGraph, 1, "A")
        );
    }

    @Test
    void checkDeadLock_T2DeadLockedByT1_shouldPreventDeadLock() {
        /*
           T1 -----> [A] ------> [B]
           T2 -----> [B] -----------> acquiring 'A'
         */
        ThreadEntityGraph<String> threadEntityGraph = new ThreadEntityGraph<>();

        threadEntityGraph.addThreadEntityAssociation(1, "A");
        threadEntityGraph.addThreadEntityAssociation(1, "B");
        threadEntityGraph.addThreadEntityAssociation(2, "B");
        Assertions.assertThrows(
                DeadLockPreventionException.class,
                () -> EntityDeadLockChecker.checkForDeadLock(threadEntityGraph, 2, "A")
        );
    }

    @Test
    void checkT3DeadLockedByT1_shouldPreventDeadLock() {
        /*
           T1 -----> [A] ------> [C]
           T2 -----> [B] -----------> [A]
           T3 -----> [C] -----------------> acquiring 'A'
         */
        ThreadEntityGraph<String> threadEntityGraph = new ThreadEntityGraph<>();

        threadEntityGraph.addThreadEntityAssociation(1, "A");
        threadEntityGraph.addThreadEntityAssociation(1, "C");
        threadEntityGraph.addThreadEntityAssociation(2, "B");
        threadEntityGraph.addThreadEntityAssociation(2, "A");

        Assertions.assertDoesNotThrow(() -> EntityDeadLockChecker.checkForDeadLock(threadEntityGraph, 3, "C"));

        threadEntityGraph.addThreadEntityAssociation(3, "C");

        Assertions.assertThrows(
                DeadLockPreventionException.class,
                () -> EntityDeadLockChecker.checkForDeadLock(threadEntityGraph, 3, "A")
        );
    }
}
