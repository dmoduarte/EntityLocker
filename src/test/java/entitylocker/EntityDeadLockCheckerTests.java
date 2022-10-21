package entitylocker;

import entitylocker.exceptions.DeadLockPreventionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EntityDeadLockCheckerTests {

    @Test
    void checkDeadT2DeadLockedByT1_shouldPreventDeadLock() {
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
    void checkDeadT2DeadLockedByT1_T1HasTimeout_noDeadLock() {
        /*
           T1 -----> [A] ------> [B] (is waiting for T2 to unlock, but with timeout will eventually release the lock)
           T2 -----> [B] -----------> acquiring 'A'
         */
        ThreadEntityGraph<String> threadEntityGraph = new ThreadEntityGraph<>();

        threadEntityGraph.addThreadEntityAssociation(1, "A");
        threadEntityGraph.addThreadEntityAssociation(1, "B", true);
        threadEntityGraph.addThreadEntityAssociation(2, "B");
        Assertions.assertDoesNotThrow(() -> EntityDeadLockChecker.checkForDeadLock(threadEntityGraph, 2, "A"));
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
