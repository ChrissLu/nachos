package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
		
		Iterator<Map.Entry<Long, KThread>> it = sleepingThreads.entrySet().iterator();
		Map.Entry<Long, KThread> entry = null;
		while (it.hasNext()) {
			entry = it.next();
			if(entry.getKey() > Machine.timer().getTime()) {
                break;
            }
			threadMap.remove(entry.getValue());
			entry.getValue().ready();
			it.remove();
		}
		Machine.interrupt().restore(intStatus);
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 *
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		//The thread does not wait if x <= 0
		if(x <= 0){
			return;
		}
		long wakeTime = Machine.timer().getTime() + x;
		boolean intStatus = Machine.interrupt().disable();
		KThread currentThread = KThread.currentThread();
		sleepingThreads.put(wakeTime, currentThread);
		threadMap.put(currentThread, wakeTime);
		KThread.sleep();
		Machine.interrupt().restore(intStatus);

	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
	public boolean cancel(KThread thread) {
		boolean intStatus = Machine.interrupt().disable();
		if(!threadMap.containsKey(thread)){
			Machine.interrupt().restore(intStatus);
			return false;
		}

		long wakeTime = threadMap.get(thread);
		sleepingThreads.remove(wakeTime);
		threadMap.remove(thread);
		thread.ready();

		Machine.interrupt().restore(intStatus);
		return true;
	}

	private Map<Long, KThread> sleepingThreads = new TreeMap<Long, KThread>();
	private Map<KThread, Long> threadMap = new HashMap<KThread, Long>();

	// Add Alarm testing code to the Alarm class

	public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
    }

	// Implement more test methods here ...

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		alarmTest1();

		// Invoke your other test methods here ...
	}
}
