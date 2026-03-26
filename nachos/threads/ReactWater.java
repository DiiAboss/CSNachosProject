package nachos.threads;

import nachos.machine.*;

/**
 * ReactWater -- synchronizes H and O atom threads to form water molecules.
 * Need 2 H atoms and 1 O atom to make a molecule.
 */
public class ReactWater {
    private Lock lock;
    private Condition2 hCV, oCV;
    private int hCount, oCount;

    public ReactWater() {
        lock = new Lock();
        hCV = new Condition2(lock);
        oCV = new Condition2(lock);
        hCount = 0;
        oCount = 0;
    }

    public void hReady() {
        lock.acquire();
        hCount++;
        oCV.wakeAll();          // a new H atom arrived -- wake O threads to check
        hCV.sleep();            // sleep unconditionally; O will wake us when reacting
        lock.release();
    }

    public void oReady() {
        lock.acquire();
        oCount++;

        while (hCount < 2)
            oCV.sleep();        // not enough H atoms yet -- wait

        // O thread owns the reaction
        hCount -= 2;
        oCount -= 1;
        makeWater();

        hCV.wake();             // wake exactly the two H atoms for this molecule
        hCV.wake();

        lock.release();
    }

    public void makeWater() {
        System.out.println("Made water!");
    }

    public static void selfTest() {
        System.out.println("--- ReactWater tests ---");

        // test 1: smallest case (2H + 1O)
        final int[] wc1 = {0};
        ReactWater rw1 = new ReactWater() {
            public void makeWater() { wc1[0]++; }
        };
        final boolean[] ret1 = {false, false, false};

        KThread h1 = new KThread(new Runnable() {
            public void run() { rw1.hReady(); ret1[0] = true; }
        }).setName("h1");
        KThread h2 = new KThread(new Runnable() {
            public void run() { rw1.hReady(); ret1[1] = true; }
        }).setName("h2");
        KThread o1 = new KThread(new Runnable() {
            public void run() { rw1.oReady(); ret1[2] = true; }
        }).setName("o1");

        h1.fork(); h2.fork(); o1.fork();
        h1.join(); h2.join(); o1.join();

        Lib.assertTrue(wc1[0] == 1);
        Lib.assertTrue(ret1[0] && ret1[1] && ret1[2]);
        System.out.println("ReactWater test 1 passed (2H+1O)");

        // test 2: extra H atoms (3H + 1O)
        final int[] wc2 = {0};
        ReactWater rw2 = new ReactWater() {
            public void makeWater() { wc2[0]++; }
        };
        final boolean[] ret2 = {false, false, false, false};

        KThread h2a = new KThread(new Runnable() {
            public void run() { rw2.hReady(); ret2[0] = true; }
        }).setName("h2a");
        KThread h2b = new KThread(new Runnable() {
            public void run() { rw2.hReady(); ret2[1] = true; }
        }).setName("h2b");
        KThread h2c = new KThread(new Runnable() {
            public void run() { rw2.hReady(); ret2[2] = true; }
        }).setName("h2c");
        KThread o2a = new KThread(new Runnable() {
            public void run() { rw2.oReady(); ret2[3] = true; }
        }).setName("o2a");

        h2a.fork(); h2b.fork(); h2c.fork(); o2a.fork();
        // only join the O and 2 H that should complete; third H stays blocked
        o2a.join();
        KThread.yield(); KThread.yield();
        Lib.assertTrue(wc2[0] == 1);
        int retCount2 = 0;
        for (boolean b : ret2) if (b) retCount2++;
        Lib.assertTrue(retCount2 == 3); // 2 H + 1 O returned
        System.out.println("ReactWater test 2 passed (3H+1O)");

        // test 3: two molecules (4H + 2O)
        final int[] wc3 = {0};
        ReactWater rw3 = new ReactWater() {
            public void makeWater() { wc3[0]++; }
        };
        final boolean[] ret3 = new boolean[6];

        KThread[] t3 = new KThread[6];
        for (int i = 0; i < 4; i++) {
            final int ii = i;
            t3[i] = new KThread(new Runnable() {
                public void run() { rw3.hReady(); ret3[ii] = true; }
            }).setName("h3-" + i);
        }
        for (int i = 0; i < 2; i++) {
            final int ii = 4 + i;
            t3[ii] = new KThread(new Runnable() {
                public void run() { rw3.oReady(); ret3[ii] = true; }
            }).setName("o3-" + i);
        }
        for (KThread t : t3) t.fork();
        for (KThread t : t3) t.join();
        Lib.assertTrue(wc3[0] == 2);
        for (boolean b : ret3) Lib.assertTrue(b);
        System.out.println("ReactWater test 3 passed (4H+2O)");

        // test 4: O gets there first
        final int[] wc4 = {0};
        ReactWater rw4 = new ReactWater() {
            public void makeWater() { wc4[0]++; }
        };
        final boolean[] ret4 = {false, false, false};

        KThread o4 = new KThread(new Runnable() {
            public void run() { rw4.oReady(); ret4[2] = true; }
        }).setName("o4");
        o4.fork();
        KThread.yield(); KThread.yield();

        KThread h4a = new KThread(new Runnable() {
            public void run() { rw4.hReady(); ret4[0] = true; }
        }).setName("h4a");
        KThread h4b = new KThread(new Runnable() {
            public void run() { rw4.hReady(); ret4[1] = true; }
        }).setName("h4b");
        h4a.fork(); h4b.fork();
        h4a.join(); h4b.join(); o4.join();
        Lib.assertTrue(wc4[0] == 1);
        Lib.assertTrue(ret4[0] && ret4[1] && ret4[2]);
        System.out.println("ReactWater test 4 passed (O first)");

        // test 5: bigger test (6H + 3O)
        final int[] wc5 = {0};
        ReactWater rw5 = new ReactWater() {
            public void makeWater() { wc5[0]++; }
        };
        final boolean[] ret5 = new boolean[9];

        KThread[] t5 = new KThread[9];
        for (int i = 0; i < 6; i++) {
            final int ii = i;
            t5[i] = new KThread(new Runnable() {
                public void run() { rw5.hReady(); ret5[ii] = true; }
            }).setName("h5-" + i);
        }
        for (int i = 0; i < 3; i++) {
            final int ii = 6 + i;
            t5[ii] = new KThread(new Runnable() {
                public void run() { rw5.oReady(); ret5[ii] = true; }
            }).setName("o5-" + i);
        }
        for (KThread t : t5) t.fork();
        for (KThread t : t5) t.join();
        Lib.assertTrue(wc5[0] == 3);
        for (boolean b : ret5) Lib.assertTrue(b);
        System.out.println("ReactWater test 5 passed (6H+3O)");
    }
}
