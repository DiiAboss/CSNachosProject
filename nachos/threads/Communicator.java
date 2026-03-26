package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
	lock = new Lock();
	speakerCV = new Condition2(lock);
	listenerCV = new Condition2(lock);
	messageWaiting = false;
	waitingListeners = 0;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
	lock.acquire();

	// wait until there's a listener and no message already sitting there
	while (waitingListeners == 0 || messageWaiting)
	    speakerCV.sleep();

	this.word = word;
	messageWaiting = true;
	listenerCV.wake();

	lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
	lock.acquire();

	waitingListeners++;
	speakerCV.wake();

	while (!messageWaiting)
	    listenerCV.sleep();

	int result = this.word;
	messageWaiting = false;
	waitingListeners--;

	// wake another speaker in case one is waiting on messageWaiting
	speakerCV.wake();

	lock.release();
	return result;
    }

    public static void selfTest() {
	System.out.println("--- Communicator tests ---");

	// test 1: speaker goes first
	final Communicator c1 = new Communicator();
	final int[] res1 = {-1};
	KThread spk1 = new KThread(new Runnable() {
	    public void run() { c1.speak(42); }
	}).setName("spk1");
	spk1.fork();
	res1[0] = c1.listen();
	spk1.join();
	Lib.assertTrue(res1[0] == 42);
	System.out.println("Communicator test 1 passed, got " + res1[0]);

	// test 2: listener goes first
	final Communicator c2 = new Communicator();
	final int[] res2 = {-1};
	KThread lst2 = new KThread(new Runnable() {
	    public void run() { res2[0] = c2.listen(); }
	}).setName("lst2");
	lst2.fork();
	c2.speak(99);
	lst2.join();
	Lib.assertTrue(res2[0] == 99);
	System.out.println("Communicator test 2 passed, got " + res2[0]);

	// test 3: multiple pairs, make sure all words get transferred
	final int N = 5;
	final Communicator c3 = new Communicator();
	final int[] results = new int[N];
	KThread[] spks = new KThread[N];
	KThread[] lsts = new KThread[N];

	for (int i = 0; i < N; i++) {
	    final int w = i;
	    final int ii = i;
	    spks[i] = new KThread(new Runnable() {
		public void run() { c3.speak(w); }
	    }).setName("spk" + i);
	    lsts[i] = new KThread(new Runnable() {
		public void run() { results[ii] = c3.listen(); }
	    }).setName("lst" + i);
	}
	for (int i = 0; i < N; i++) { spks[i].fork(); lsts[i].fork(); }
	for (int i = 0; i < N; i++) { spks[i].join(); lsts[i].join(); }

	java.util.Arrays.sort(results);
	for (int i = 0; i < N; i++) Lib.assertTrue(results[i] == i);
	System.out.println("Communicator test 3 passed (" + N + " pairs)");

	// test 4: more speakers than listeners, only one should come back
	final Communicator c4 = new Communicator();
	final boolean[] returned = {false, false, false};
	for (int i = 0; i < 3; i++) {
	    final int ii = i;
	    new KThread(new Runnable() {
		public void run() {
		    c4.speak(ii);
		    returned[ii] = true;
		}
	    }).setName("excess" + i).fork();
	}
	c4.listen();
	KThread.yield(); KThread.yield();
	int cnt = 0;
	for (boolean b : returned) if (b) cnt++;
	Lib.assertTrue(cnt == 1);
	System.out.println("Communicator test 4 passed (excess speakers)");

	// test 5: more listeners than speakers, only one should receive
	final Communicator c5 = new Communicator();
	final boolean[] lReturned = {false, false, false};
	final int[] lResult = {-1, -1, -1};
	for (int i = 0; i < 3; i++) {
	    final int ii = i;
	    new KThread(new Runnable() {
		public void run() {
		    lResult[ii] = c5.listen();
		    lReturned[ii] = true;
		}
	    }).setName("excessLst" + i).fork();
	}
	KThread.yield(); KThread.yield();
	c5.speak(77);
	KThread.yield(); KThread.yield();
	int lCnt = 0;
	for (boolean b : lReturned) if (b) lCnt++;
	Lib.assertTrue(lCnt == 1);
	System.out.println("Communicator test 5 passed (excess listeners)");
    }

    private Lock lock;
    private Condition2 speakerCV;
    private Condition2 listenerCV;
    private int word;
    private boolean messageWaiting;
    private int waitingListeners;
}