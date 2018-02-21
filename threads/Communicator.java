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
    /**
     * Allocate a new communicator.
     */
	
	private Lock communicationLock; 
	private Condition2 currentSpeaker; 
	private Condition2 currentListener; 
	private int speakerCount = 0; 
	private int listenerCount = 0; 
	private int message = 0; 
	private boolean messageAvailable; 
	
	
    public Communicator() {
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
    	
    	while(listenerCount == 0) {
    		currentSpeaker.sleep();	
    	}
    	
    	//**listener becomes available** 
    	this.message = word; 
    	messageAvailable = true; 
    	currentListener.wake();
    	--speakerCount; 
    	communicationLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	
    	communicationLock.acquire();
    	listenerCount++; 
    	
    	//**message becomes available**
    	while(messageAvailable == false) {
    		currentSpeaker.wake();
    		currentListener.sleep();
    	}
    	
    	int receivedMessage = this.message; 
    	messageAvailable= false; 
    	--listenerCount; 
    	communicationLock.release();
	return receivedMessage;
    }
}
