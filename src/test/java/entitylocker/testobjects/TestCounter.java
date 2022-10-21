package entitylocker.testobjects;

public class TestCounter {
    private int counter = 0;

    public synchronized void increment() throws InterruptedException {
        int c = counter;
        //wait releases the synchronized lock, so this gives time to other threads advance and try to read the 'counter' in the line above
        wait(100);
        counter = c + 1;
    }

    public int getCounter() {
        return counter;
    }
}