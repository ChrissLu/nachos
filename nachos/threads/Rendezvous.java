package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */

    private List<String> names;
    private List<Integer> numbers;
    private Map<Integer,List> tags_names;
    private Map<Integer,List> tags_numbers;
    private Semaphore mutex; 
    
    public Rendezvous () {
        names = new ArrayList<String>();        
        numbers = new ArrayList<Integer>();
        
        tags_names = new HashMap<>();
        tags_numbers = new HashMap<>();
        mutex = new Semaphore(1); //control numbers
    }

    /*
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {
        
        mutex.P();
            if(tags_names.get(tag) == null){
                tags_names.put(tag,new ArrayList<String>());
                tags_names.get(tag).add(KThread.currentThread().getName());
            }else{
                tags_names.get(tag).add(KThread.currentThread().getName());
            }

            if(tags_numbers.get(tag) == null){
                tags_numbers.put(tag,new ArrayList<Integer>());
                tags_numbers.get(tag).add(value);
            }else{
                tags_numbers.get(tag).add(value);
            }
        
        mutex.V();

        KThread.currentThread().yield();
        mutex.P();
        int res;
        // System.out.println(tags_names.get(tag).get(0));
        // System.out.println(tags_numbers.get(tag).get(0));
        if(KThread.currentThread().getName() == tags_names.get(tag).get(0)) 
            res = (int)tags_numbers.get(tag).get(1);
        else res = (int)tags_numbers.get(tag).get(0);

        mutex.V();
        
        return res;
    }

    

    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t1.setName("t1");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t2.setName("t2");

        t1.fork(); t2.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join();
        }

        public static void rendezTest2() {
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t1.setName("t1");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t2.setName("t2");

        KThread t3 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int send = 99;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -99, "Was expecting " + -99 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t3.setName("t3");
        KThread t4 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int send = -99;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 99, "Was expecting " + 99 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t4.setName("t4");

        t1.fork(); t3.fork(); t4.fork();t2.fork();
        // assumes join is implemented correctly
        t3.join(); t1.join();t4.join();t2.join();
        }
    

    // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()

    public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        rendezTest1();
        rendezTest2();
    }
}
