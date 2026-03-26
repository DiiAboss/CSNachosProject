package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
	waitQueue = new LinkedList<KThread>();
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

	boolean intStatus = Machine.interrupt().disable();

	// add to wait queue then release lock and sleep
	waitQueue.add(KThread.currentThread());
	conditionLock.release();
	KThread.sleep();
	conditionLock.acquire();

	Machine.interrupt().restore(intStatus);
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

	boolean intStatus = Machine.interrupt().disable();

	if (!waitQueue.isEmpty())
	    waitQueue.removeFirst().ready();

	Machine.interrupt().restore(intStatus);
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

	boolean intStatus = Machine.interrupt().disable();

	while (!waitQueue.isEmpty())
	    waitQueue.removeFirst().ready();

	Machine.interrupt().restore(intStatus);
    }

    public static void selfTest() {
	System.out.println("--- Condition2 tests ---");

	// test 1: basic wait and signal
	final Lock lock1 = new Lock();
	final Condition2 cv1 = new Condition2(lock1);
	final boolean[] flag = {false};

	KThread waiter = new KThread(new Runnable() {
	    public void run() {
		lock1.acquire();
		while (!flag[0])
		    cv1.sleep();
		Lib.assertTrue(flag[0]);
		System.out.println("Condition2 test 1 passed");
		lock1.release();
	    }
	}).setName("waiter");

	KThread signaler = new KThread(new Runnable() {
	    public void run() {
		lock1.acquire();
		flag[0] = true;
		cv1.wake();
		lock1.release();
	    }
	}).setName("signaler");

	waiter.fork();
	signaler.fork();
	waiter.join();
	signaler.join();

	// test 2: wakeAll
	final Lock lock2 = new Lock();
	final Condition2 cv2 = new Condition2(lock2);
	final int[] count = {0};

	KThread[] sleepers = new KThread[3];
	for (int i = 0; i < 3; i++) {
	    sleepers[i] = new KThread(new Runnable() {
		public void run() {
		    lock2.acquire();
		    cv2.sleep();
		    count[0]++;
		    lock2.release();
		}
	    }).setName("sleeper");
	    sleepers[i].fork();
	}

	KThread.yield();
	KThread.yield();
	lock2.acquire();
	cv2.wakeAll();
	lock2.release();

	for (KThread s : sleepers) s.join();
	Lib.assertTrue(count[0] == 3);
	System.out.println("Condition2 test 2 passed (wakeAll)");

	// test 3: lock is held after sleep returns
	final Lock lock3 = new Lock();
	final Condition2 cv3 = new Condition2(lock3);
	final boolean[] lockOk = {false};

	KThread t3 = new KThread(new Runnable() {
	    public void run() {
		lock3.acquire();
		cv3.sleep();
		lockOk[0] = lock3.isHeldByCurrentThread();
		lock3.release();
	    }
	}).setName("lockcheck");

	t3.fork();
	KThread.yield();
	lock3.acquire();
	cv3.wake();
	lock3.release();
	t3.join();
	Lib.assertTrue(lockOk[0]);
	System.out.println("Condition2 test 3 passed (lock reacquired)");
    }

    private Lock conditionLock;
    private LinkedList<KThread> waitQueue;
}