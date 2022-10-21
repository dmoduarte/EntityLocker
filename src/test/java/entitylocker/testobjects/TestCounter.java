package entitylocker.testobjects;

public class TestCounter {
    private int counter = 0;

    public synchronized void increment() throws InterruptedException {
        int c = counter;
        wait(100);
        counter = c + 1;
    }

    public int getCounter() {
        return counter;
    }
}