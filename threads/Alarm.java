package nachos.threads;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */

//commit test case 
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
    	
    //iterate through waitingQueue and check for expired waiters 
    	for(int i = 0; i < waitingQueue.size(); i++) {
    		waitingData currentWaiter = waitingQueue.get(i); 
    		
    		//check if waiter is done waiting 
    		if(currentWaiter.wakeTime <= Machine.timer().getTime()) {
    			currentWaiter.waitLock.release();
    			waitingQueue.remove(i);  			
    		}	
    	}
    	
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
	// for now, cheat just to get something working (busy waiting is bad)
	long wakeTime = Machine.timer().getTime() + x;
	boolean status = Machine.interrupt().disable(); 
	
	waitingData waiter = new waitingData(wakeTime); 
	waitingQueue.add(waiter);
	System.out.println("Thread waiting: " + KThread.currentThread().getName() + " waiting until: " + wakeTime);
	
	while (wakeTime > Machine.timer().getTime())
	    KThread.yield();
    }
    
    
 private SynchList waitingQueue = new SynchList(); 
    
    
    private static class waitingData {
    	public long wakeTime; 
    	
    	public waitingData(long wakeTime) { 
    		this.wakeTime = wakeTime; 
    	
    	}
    	
    }
    
    
}
