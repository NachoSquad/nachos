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
	
	private Lock communicationLock = new Lock(); 
	private Condition2 currentSpeaker = new Condition2(communicationLock); 
	private Condition2 currentListener = new Condition2(communicationLock); 
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
    	speakerCount++; 
    	while(listenerCount == 0) {
    		
    	}
    	currentSpeaker.sleep();	
    	
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
    	listenerCount++; 
    	
    	//**message becomes available**
    	while(messageAvailable == false) {
<<<<<<< HEAD
    		//currentSpeaker.wake();
    		currentListener.sleep();
=======
    		
>>>>>>> 5a64ce6972295b6e60a1557af1a69a26b1f9e10b
    	}
    	currentSpeaker.wake();
    	
      	currentSpeaker.wake();
		currentListener.sleep();
    	
    	int receivedMessage = this.message; 
    	messageAvailable= false; 
    	--listenerCount; 
	return receivedMessage;
    }
}
