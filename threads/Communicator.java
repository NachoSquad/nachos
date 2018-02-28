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
   
	
	private Lock communicationLock; 
	private Condition2 condSpeakers; 
	private Condition2 condListeners; 
	private Condition2 condHandshake; 
	private int speakerCount = 0; 
	private int listenerCount = 0; 
	private int message = 0; 
	private boolean messageAvailable; 
	
	 /**
     * Allocate a new communicator.
     */
    public Communicator() {
    	this.messageAvailable = false; 
    	this.communicationLock = new Lock();
    	this.condSpeakers = new Condition2(communicationLock); 
    	this.condListeners = new Condition2(communicationLock); 
    	this.condHandshake = new Condition2(communicationLock); 
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
    	communicationLock.acquire();
    	speakerCount++; 
    	
    	while(messageAvailable) {
    		condSpeakers.sleep(); 
    	}
    	
    	
    	this.message = word; 
    this.messageAvailable = true; 
    	condListeners.wake();
    	condHandshake.sleep(); 
    	communicationLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	int receivedMessage; 
    	communicationLock.acquire();
    
    	while(!messageAvailable) {
    		condListeners.sleep(); 
    	}
    	
    receivedMessage = this.message; 
    	this.messageAvailable= false; 
    	
    	condSpeakers.wake(); 
    	condHandshake.wake(); 
    communicationLock.release(); 
	return receivedMessage;
    }
}
