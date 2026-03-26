package nachos.threads;

import nachos.machine.*;
import java.util.TreeSet;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
	boolean intStatus = Machine.interrupt().disable();

	long now = Machine.timer().getTime();

	// wake up any threads whose time has come
	while (!waitQueue.isEmpty() && waitQueue.first().wakeTime <= now) {
	    waitQueue.pollFirst().thread.ready();
	}

	Machine.interrupt().restore(intStatus);
	KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	if (x <= 0) return;

	long wakeTime = Machine.timer().getTime() + x;

	boolean intStatus = Machine.interrupt().disable();
	waitQueue.add(new WaitEntry(KThread.currentThread(), wakeTime));
	KThread.sleep();
	Machine.interrupt().restore(intStatus);
    }

    public static void selfTest() {
	System.out.println("--- Alarm tests ---");

	Alarm alarm = ThreadedKernel.alarm;

	// test 1: make sure we actually wait long enough
	long start = Machine.timer().getTime();
	alarm.waitUntil(1000);
	long elapsed = Machine.timer().getTime() - start;
	Lib.assertTrue(elapsed >= 1000);
	System.out.println("Alarm test 1 passed, waited " + elapsed + " ticks");

	// test 2: threads should wake up in order of their wait time
	final int[] order = {-1, -1, -1};
	final int[] idx = {0};

	KThread t1 = new KThread(new Runnable() {
	    public void run() {
		alarm.waitUntil(3000);
		order[idx[0]++] = 1;
	    }
	}).setName("t1");
	KThread t2 = new KThread(new Runnable() {
	    public void run() {
		alarm.waitUntil(1000);
		order[idx[0]++] = 2;
	    }
	}).setName("t2");
	KThread t3 = new KThread(new Runnable() {
	    public void run() {
		alarm.waitUntil(2000);
		order[idx[0]++] = 3;
	    }
	}).setName("t3");

	t1.fork(); t2.fork(); t3.fork();
	t1.join(); t2.join(); t3.join();

	Lib.assertTrue(order[0] == 2 && order[1] == 3 && order[2] == 1);
	System.out.println("Alarm test 2 passed (wake order correct)");

	// test 3: zero or negative x shouldn't sleep at all
	long before = Machine.timer().getTime();
	alarm.waitUntil(0);
	alarm.waitUntil(-100);
	Lib.assertTrue(Machine.timer().getTime() - before < 500);
	System.out.println("Alarm test 3 passed (zero/negative x)");
    }

    // stores a thread and when it should wake up
    private class WaitEntry implements Comparable<WaitEntry> {
	KThread thread;
	long wakeTime;

	WaitEntry(KThread t, long w) {
	    thread = t;
	    wakeTime = w;
	}

	public int compareTo(WaitEntry other) {
	    if (this.wakeTime < other.wakeTime) return -1;
	    if (this.wakeTime > other.wakeTime) return 1;
	    return 0;
	}
    }

    private TreeSet<WaitEntry> waitQueue = new TreeSet<WaitEntry>();
}