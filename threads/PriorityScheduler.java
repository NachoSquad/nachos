package nachos.threads;
import nachos.machine.*;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param	transferPriority	<tt>true</tt> if this queue should
	 *					transfer priority from waiting threads
	 *					to the owning thread.
	 * @return	a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum &&
				priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority-1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param	thread	the thread whose scheduling state to return.
	 * @return	the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		
		protected ArrayList threadList = new ArrayList(); // store threads 
		protected KThread mainThread = null;   // Makes a single thread the owner 
		
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			
			KThread temp = null; 
			
			for(int i = 0; i < threadList.size(); i++) {
				temp = (KThread)threadList.get(i); 
				getThreadState(thread).updateEffecitvePriority(); //reset it 	
			}
			
			threadList.add(thread);
			getThreadState(thread).waitForAccess(this);
			
		
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			mainThread = thread;   //become the main thread and gain access to resources
			getThreadState(thread).acquire(this); //acquire the thread's state 
		}

		public KThread nextThread() {
		  boolean status = Machine.interrupt().disable(); 

			if (mainThread != null) {
				getThreadState(mainThread).removeQueue(this); 
			}
			
			//check the "waiting to run" threadList , if there is run it 
			
			if(threadList.isEmpty()) {
				mainThread = null; 
				Machine.interrupt().restore(status);
				return null; 
			}
			
			mainThread = pickNextThread().thread; // if we reach here, there is something waiting on thread list and main thread will become that thread 
			
			if(mainThread != null) {
				getThreadState(mainThread).updateEffecitvePriority();
				threadList.remove(mainThread); 
				acquire(mainThread); //mainThread acquires access 
			}
			
			Machine.interrupt().restore(status);			
			return mainThread; 
		}

		
		
		
		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
			KThread result = null;
			int index = 0; 
			int maxPriority = -1;
			int currentPriority = 0; 
			long currentTime = Machine.timer().getTime(); 
			long waitedTime = 0; 
		
	         if(threadList.isEmpty()) {
	        	 return null; 
	         }
	         
	         for(int i = 0; i < threadList.size(); i++) {
	        	   KThread currentThread = (KThread) threadList.get(i); 
	        	   
	        	   
	        	   //get the current thread's priority 
	        	   if(transferPriority) {
	        		   currentPriority = getThreadState(currentThread).getEffectivePriority(); 
	        	   } else {
	        		   currentPriority = getThreadState(currentThread).getPriority(); 
	        	   }
	        	   
	        	   if(currentPriority > maxPriority) {
	        		   maxPriority = currentPriority; //found most significant thread 
	        		   index = i;                //save the position 
	        		   currentTime = waitedTime; //reset the clock 
	        	   } else if(currentPriority == maxPriority) {
	        		     waitedTime = getThreadState(currentThread).getWaitedTime();
	        		     if(waitedTime < currentTime) {
	        		     currentTime = waitedTime; 
	        		     index = i; 
	        		     }
	        	   } 
	         }	
		  return getThreadState((KThread) threadList.get(index)); //returns the appropriate thread 
		}
		
		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}
		
		public ArrayList getList() {
			return threadList; 
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		LinkedList<KThread> waitQueue = new LinkedList<KThread>();
		ThreadState lock = null;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue
	 * it's waiting for, if any.
	 *
	 * @see	nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState implements Comparable {
		
		protected ArrayList accessList = new ArrayList(); 
		protected long waitedTime = Machine.timer().getTime(); 
		protected int effectivePriority = -1; //default, subject to change 
		protected int resetPriority = -1; // will never change , used to reset effectivePriority
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		
		public long getWaitedTime() {
			return waitedTime; 
		}
		
		
		
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
		PriorityQueue waitQueue = null; 
		ArrayList currentthreadList = null; 
		int maxP = priority; 
		int currentP = 0; 
		
		
		if(effectivePriority == -1) {
			
			for(int i = 0; i < accessList.size(); i++) {
				waitQueue = (PriorityQueue) accessList.get(i); 
				currentthreadList = waitQueue.getList(); 
				
				for(int j = 0; j < currentthreadList.size(); j++) {
					currentP = ((ThreadState)((KThread)currentthreadList.get(j)).schedulingState).getEffectivePriority();
					if(currentP > maxP) {
						maxP = currentP; 
						effectivePriority = maxP; 
					}
				}
					
			}	
		}
			 if(effectivePriority == -1) 
				 effectivePriority = getPriority(); 

			return effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			if(this.priority == priority) 
				return; 
			else if (priority > PriorityScheduler.priorityMaximum)
				this.priority = PriorityScheduler.priorityMaximum; 
			 else if (priority < PriorityScheduler.priorityMinimum) // If it is under the minimum,
	   			 this.priority = PriorityScheduler.priorityMinimum;// It will be set as the minimum (0)
	   		 else
	   			 this.priority = priority;   			// Otherwise, the priority will get the priority
	   		 effectivePriority = -1;
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param	waitQueue	the queue that the associated thread is
		 *				now waiting on.
		 *
		 * @see	nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {

			
			waitedTime = Machine.timer().getTime();  // grab the current time 

			
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see	nachos.threads.ThreadQueue#acquire
		 * @see	nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			accessList.add(waitQueue); 
			effectivePriority = -1; 
		}
		
		
		public void removeQueue(PriorityQueue removedQueue) {
			accessList.remove(removedQueue); 
		}

		public void updateEffecitvePriority() {
			effectivePriority = resetPriority; // effective priority is bunk
			
		}
		

		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		
		 public int compareTo(Object obj) {   				 
	   		 long waitingTime1 = (this.waitedTime);   		 
	   		 long waitingTime2 = (((ThreadState)obj).waitedTime);   
	   		 long priority1 = (this.priority);   			 
	   		 long priority2 = (((ThreadState)obj).priority);   	 
	   		 if (priority1 > priority2)   				 
	   			 return -1;   					 
	   		 else if (priority1 < priority2)   			 
	   			 return 1;   				
	   		 else{   						
	   			 if(waitingTime1 < waitingTime2)    		
	   				 return -1;   			 	
	   			 else if (waitingTime1 > waitingTime2)   	 
	   				 return 1;
	   			 else
	   				 return 0;   				
	   		 }
	   	 }
		
		 public boolean equals(Object o) {
			 if(this.waitedTime == ((ThreadState)o).waitedTime && this.priority == ((ThreadState)o).priority){
				return true; 
			 }
			return false; 
		 }
	}
		
			
}