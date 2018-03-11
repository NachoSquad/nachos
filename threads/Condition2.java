package nachos.threads;




import java.util.LinkedList;
import java.util.List;

import nachos.machine.*;

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
	zzzQueue = new LinkedList<KThread>(); 
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	boolean interruptStatus = Machine.interrupt().disable(); 
	conditionLock.release();
	KThread sleepingThread = KThread.currentThread();
	zzzQueue.add(sleepingThread);
	sleepingThread.sleep(); //nighty night 
	conditionLock.acquire();
	Machine.interrupt().restore(interruptStatus);

    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
    	if( !zzzQueue.isEmpty() ) {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	boolean interruptStatus = Machine.interrupt().disable(); 
	KThread waked_thread = zzzQueue.removeFirst();  
	waked_thread.ready();
	Machine.interrupt().restore(interruptStatus);
    	}
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

	while(!zzzQueue.isEmpty()) {
		wake(); 	
	}
	
    }

    private Lock conditionLock;
    private LinkedList<KThread> zzzQueue; 
    
    //TEST 
    
    private static class Condition2Test implements Runnable {
    	Condition2Test(Lock lock, Condition2 condition) {
    	        this.condition = condition;
            this.lock = lock;
    	}
    	
    	public void run() {
            lock.acquire();
            condition.sleep();
            System.out.print(KThread.currentThread().getName() + " reacquired lock \n");	
            lock.release();
            System.out.print(KThread.currentThread().getName() + " released lock \n");	
    	}

        private Lock lock; 
        private Condition2 condition; 
        }
    
    public static void selfTest() {

        System.out.print("Condition2 Test");	

        Lock lock = new Lock();
        Condition2 conditionTest = new Condition2(lock); 

        KThread t[] = new KThread[10];
    	for (int i=0; i<10; i++) { //fork many new condition2 threads 
             t[i] = new KThread(new Condition2Test(lock, conditionTest));
             t[i].setName("Thread" + i).fork();
    	}

        KThread.yield();
        
        lock.acquire();

        System.out.print("Test Wake\n");	
        conditionTest.wake();

        System.out.print("Test Wake All\n");	
        conditionTest.wakeAll();

        lock.release();

        System.out.print("Done Condition2 Testing\n");	


            
        }
    
    
    
    
    
    
}
