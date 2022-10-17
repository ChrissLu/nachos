package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.Iterator;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 *
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {	this.conditionLock = conditionLock;	}
	public Condition2(Lock conditionLock, String name) {
		this.conditionLock = conditionLock;	
		this.name = name;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		conditionLock.release();
		waitQueue.add(KThread.currentThread());
		KThread.sleep();

		waitQueue.remove(KThread.currentThread());
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

		Iterator<KThread> iter = waitQueue.iterator();
		if(iter.hasNext()){
			KThread thread = iter.next();
			if(!ThreadedKernel.alarm.cancel(thread)){
				thread.ready();
			}
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		Iterator<KThread> iter = waitQueue.iterator();
		while(iter.hasNext()){
			KThread thread = iter.next();
			if(!ThreadedKernel.alarm.cancel(thread)){
				thread.ready();
			}
		}

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
	public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		conditionLock.release();
		waitQueue.add(KThread.currentThread());
		ThreadedKernel.alarm.waitUntil(timeout);

		waitQueue.remove(KThread.currentThread());
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	private Lock conditionLock;

	private LinkedHashSet<KThread> waitQueue = new LinkedHashSet<KThread>();

	private String name;

	private static class InterlockTest {
		private static Lock lock;
		private static Condition2 cv;

		private static class Interlocker implements Runnable {
			public void run () {
				lock.acquire();
				for (int i = 0; i < 10; i++) {
					System.out.println(KThread.currentThread().getName());
					cv.wake();   // signal
					cv.sleep();  // wait
				}
				lock.release();
			}
		}

		public InterlockTest () {
			lock = new Lock();
			cv = new Condition2(lock);

			KThread ping = new KThread(new Interlocker());
			ping.setName("ping");
			KThread pong = new KThread(new Interlocker());
			pong.setName("pong");

			ping.fork();
			pong.fork();

			// We need to wait for ping to finish, and the proper way
			// to do so is to join on ping.  (Note that, when ping is
			// done, pong is sleeping on the condition variable; if we
			// were also to join on pong, we would block forever.)
			// For this to work, join must be implemented.  If you
			// have not implemented join yet, then comment out the
			// call to join and instead uncomment the loop with
			// yields; the loop has the same effect, but is a kludgy
			// way to do it.
			// ping.join();
			for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
		}
	}

	// Invoke Condition2.selfTest() from ThreadedKernel.selfTest()

	public static void selfTest() {
		new InterlockTest();
	}

	public static void cvTest5() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				while(list.isEmpty()){
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while(!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wake();
				lock.release();
			}
		});

		consumer.setName("Consumer");
		producer.setName("Producer");
		consumer.fork();
		producer.fork();

		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them.  For this
		// to work, join must be implemented.  If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		// consumer.join();
		// producer.join();
		for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}

	public static void sleepForTest1 () {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
				" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}

	public static void mySleepForTest(){
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);

		KThread t1 = new KThread( new Runnable () {
			public void run () {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println("T1 sleep time: " + t0);
				cv.sleepFor(1000);
				long t1 = Machine.timer().getTime();
				System.out.println("T1 wake time: " + t1);
				cv.wake();

				t0 = Machine.timer().getTime();
				System.out.println("T1 sleep time: " + t0);
				cv.sleepFor(5000);
				t1 = Machine.timer().getTime();
				System.out.println("T1 wake time: " + t1);

				lock.release();
			}
		});

		KThread t2 = new KThread( new Runnable () {
			public void run () {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println("T2 sleep time: " + t0);
				cv.sleepFor(2000);
				long t1 = Machine.timer().getTime();
				System.out.println("T2 wake time: " + t1);

				t0 = Machine.timer().getTime();
				System.out.println("T2 sleep time: " + t0);
				cv.sleepFor(2000);
				t1 = Machine.timer().getTime();
				System.out.println("T2 wake time: " + t1);
				
				cv.wake();
				lock.release();
			}
		});

		t1.setName("T1");
		t2.setName("T2");
		t1.fork();
		t2.fork();
		for (int i = 0; i < 500; i++) { KThread.currentThread().yield(); }
	}

	public static void mySleepForTest2(){
		final Lock lock = new Lock();
		final Condition2 cv0 = new Condition2(lock, "cv0");
		final Condition2 cv1 = new Condition2(lock, "cv1");
		final LinkedList<Integer> list = new LinkedList<>();
		int waitTime[] = {1, 10, 100, 1000, 10000};

		KThread ping = new KThread( new Runnable () {
			public void run () {
				lock.acquire();
				for (int i = 0; i < waitTime.length; i++) {
					while(list.isEmpty())
					{
						long t0 = Machine.timer().getTime();
						cv1.sleepFor(waitTime[i]);  // wait
						long t1 = Machine.timer().getTime();
						System.out.println (KThread.currentThread().getName() +
								" woke up, slept for " + (t1 - t0) + " ticks");
					}
					list.remove();
					System.out.println(KThread.currentThread().getName() + " remove 1 element");

					lock.release();
					for (int j = 0; j < 50; j++) {
						KThread.currentThread().yield();
					}
					lock.acquire();
					if(list.isEmpty())
						cv0.wake();   // signal
				}
				lock.release();
			}
		});

		KThread pong = new KThread( new Runnable () {
			public void run () {
				lock.acquire();
				for (int i = 0; i < waitTime.length; i++) {
					while(!list.isEmpty())
					{
						long t0 = Machine.timer().getTime();
						cv0.sleepFor(waitTime[i]);  // wait
						long t1 = Machine.timer().getTime();
						System.out.println (KThread.currentThread().getName() +
								" woke up, slept for " + (t1 - t0) + " ticks");
					}
					list.add(1);
					System.out.println(KThread.currentThread().getName() + " add 1 element");
					cv1.wake();   // signal
				}
				lock.release();
			}
		});
		ping.setName("Ping");
		pong.setName("Pong");
		ping.fork();
		pong.fork();
		for (int i = 0; i < 50000; i++) { KThread.currentThread().yield(); }
	}
}
