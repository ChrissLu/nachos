package nachos.threads;

import java.util.*;
import java.util.function.IntSupplier;
import nachos.machine.*;

/**
 * A <i>Future</i> is a convenient mechanism for using asynchonous
 * operations.
 */
public class Future {
    /**
     * Instantiate a new <i>Future</i>.  The <i>Future</i> will invoke
     * the supplied <i>function</i> asynchronously in a KThread.  In
     * particular, the constructor should not block as a consequence
     * of invoking <i>function</i>.
     */
    public Future (IntSupplier function) {
        mutex = new Lock();
        finished = new Condition(mutex);

        isFinished = false;
        KThread thread = new KThread(new Runnable() {
            public void run() {
                retValue = function.getAsInt();
                mutex.acquire();
                isFinished = true;
                finished.wakeAll();
                mutex.release();
            }
        });
        thread.fork();
    }

    /**
     * Return the result of invoking the <i>function</i> passed in to
     * the <i>Future</i> when it was created.  If the function has not
     * completed when <i>get</i> is invoked, then the caller is
     * blocked.  If the function has completed, then <i>get</i>
     * returns the result of the function.  Note that <i>get</i> may
     * be called any number of times (potentially by multiple
     * threads), and it should always return the same value.
     */
    public int get () {
        mutex.acquire();
        while(isFinished == false)
            finished.sleep();
        mutex.release();
        return retValue;
    }

    Lock mutex;

    Condition finished;

    private boolean isFinished;

    private int retValue;

    private static int generateInt(int x){
        for(int i=0;i<50;++i) {KThread.currentThread().yield();}
        return x;
    }

    public static void selfTest() {
        int target = 1000;
        IntSupplier sup = () -> generateInt(target);
        Future async = new Future(sup);

        KThread t = new KThread(new Runnable() {
            public void run() {
                int result = async.get();
                Lib.assertTrue(result == target);
                result = async.get();
                Lib.assertTrue(result == target);
            }
        });

        t.fork();
        int result = async.get();
        Lib.assertTrue(result == target);
        result = async.get();
        Lib.assertTrue(result == target);
        t.join();
    }
}
