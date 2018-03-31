
package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.TreeSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }
    
    
    public void setPriority(KThread thread, int priority) { 
        Lib.assertTrue(Machine.interrupt().disabled()); 
        getThreadState(thread).setPriority(priority); 
    } 
 
    public boolean increasePriority() { 
        boolean intStatus = Machine.interrupt().disable(); 
 
        KThread thread = KThread.currentThread(); 
        setPriority(thread, getPriority(thread) + 1); 
 
        Machine.interrupt().restore(intStatus); 
        return true; 
    } 
 
    public boolean decreasePriority() { 
        boolean intStatus = Machine.interrupt().disable(); 
 
        KThread thread = KThread.currentThread(); 
        setPriority(thread, getPriority(thread) - 1); 
 
        Machine.interrupt().restore(intStatus); 
        return true; 
    } 
 
    protected LotteryThreadState getThreadState(KThread thread) { 
        if (thread.schedulingState == null) 
            thread.schedulingState = new LotteryThreadState(thread); 
 
        return (LotteryThreadState) thread.schedulingState; 
    } 
    
    public static final int priorityDefault = 1;

	public static final int priorityMinimum = 1;

	public static final int priorityMaximum = Integer.MAX_VALUE;
   
    
   
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	// implement me
    return new LotteryQueue(transferPriority);
    }   
    
    
    
    protected class LotteryQueue extends PriorityQueue {
    	
    	LotteryQueue(boolean transferPriority) {
			super(transferPriority);
		}
    	    
    	    @Override
    	 	protected LotteryThreadState pickNextThread() {
     	 
     	
     
     		if(threadList.isEmpty()) {
     			return null; 
     		} else {
     			
     		  int totalTickets = 0; 
     		  
     		  for(int i = 0; i < threadList.size(); i++) {
     			  KThread currentThread = (KThread) threadList.get(i); 
     			  totalTickets += getThreadState(currentThread).getEffectivePriority(); 
     		  }
     		  
     		  int randomNum = (int) (Math.random() * totalTickets); 
     		  
     		  LotteryThreadState winnerState = null; 
     		  
     		  for(int i = 0; i < threadList.size(); i++) {
     			  KThread currentThread = (KThread) threadList.get(i); 
     			  if(randomNum < getThreadState(currentThread).getEffectivePriority()) {
     				  winnerState = (LotteryThreadState) getThreadState(currentThread); 
     			  	  break; 
     			  }
     			  randomNum -= getThreadState(currentThread).getEffectivePriority(); 
     		  }
     			return winnerState; 		
     	}
     		
     		
     	}
    	    
    	    
    	   
    	
    }  
    	   
    
    protected class LotteryThreadState extends ThreadState {
    
    	
    			public LotteryThreadState(KThread thread) {
    					super(thread);
		    }
    			
    			
    			 /* return the sum of all tickets 
        	     * 
        	     */
    			/*
        	        public int getSum() {
        	        	PriorityQueue waiteQueue = null; 
        	    		ArrayList currentthreadList = null; 
        	    	    LinkedList<PriorityQueue>  currentAccessList = 
        	        	     for(int i = 0; i < accessList.size(); i++) {
        	        	    	 waiteQueue = accessList.get(i); 
        	        	    	  
        	        	
        	    	         for(int j = 0; j < threadList.size(); j++) {
        	    		   	 int currentP = getThreadState((KThread)threadList.get(i)).getPriority(); 
        	    	         total += currentP; 
        	    	         }
        	        	     }
        	    	    return total; 
              }
              */
    			
    			
    			@Override
    			public int getEffectivePriority() {
    				int ticketSum = this.priority; 
    				
    				for(int i = 0; i < this.accessList.size(); i++) {
				PriorityQueue waitQueue = (PriorityQueue) accessList.get(i); 
				 		if(waitQueue.transferPriority) {
				 			ArrayList<KThread> currentList = waitQueue.getList(); 
				 			for(int j = 0; j < currentList.size(); j++) {
				 				KThread currentThread = (KThread) currentList.get(j); 
				 				ticketSum += getThreadState(currentThread).getEffectivePriority(); 
				 			}	 			
				 		}
    				}
    				
    				
    				return ticketSum; 
            }
            

    
     }

   }



