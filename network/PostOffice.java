package nachos.network;

import nachos.machine.*;
import nachos.threads.*;

/**
 * A collection of message queues, one for each local port. A
 * <tt>PostOffice</tt> interacts directly with the network hardware. Because
 * of the network hardware, we are guaranteed that messages will never be
 * corrupted, but they might get lost.
 *
 * <p>
 * The post office uses a "postal worker" thread to wait for messages to arrive
 * from the network and to place them in the appropriate queues. This cannot
 * be done in the receive interrupt handler because each queue (implemented
 * with a <tt>SynchList</tt>) is protected by a lock.
 */
public class PostOffice {
	/**
	 * Allocate a new post office, using an array of <tt>SynchList</tt>s.
	 * Register the interrupt handlers with the network hardware and start the
	 * "postal worker" thread.
	 */
	public PostOffice() {
		messageReceived = new Semaphore(0);
		messageSent = new Semaphore(0);
		sendSema = new Semaphore(BUF_SIZE);
		sendList = new SynchList();

		lock = new Lock();
		cond = new Condition(lock);

		queues = new LockList[MailMessage.portLimit];
		for (int i=0; i<queues.length; i++)
			queues[i] = new LockList();

		Runnable receiveHandler = new Runnable() {
			public void run() { receiveInterrupt(); }
		};
		Runnable sendHandler = new Runnable() {
			public void run() { sendInterrupt(); }
		};
		Machine.networkLink().setInterruptHandlers(receiveHandler,
				sendHandler);

		KThread t = new KThread(new Runnable() {
			public void run() { postalDelivery(); }
		});

		t.fork();
	}

	/**
	 * Retrieve a message on the specified port, returning null if empty.
	 *
	 * @param	port	the port on which to wait for a message.
	 *
	 * @return	the message received
	 */
	public MailMessage receive(int port) {
		Lib.assertTrue(port >= 0 && port < queues.length);

		Lib.debug(dbgNet, "waiting for mail on port " + port);

		MailMessage mail = (MailMessage) queues[port].removeFirst();

		if (Lib.test(dbgNet))
			System.out.println("got mail on port " + port + ": " + mail);

		return mail;
	}

	/**
	 * Retrieve a message on the specified port, waiting if necessary.
	 *
	 * @param	port	the port on which to wait for a message.
	 *
	 * @return	the message received
	 */
	public MailMessage bReceive(int port) {
		Lib.assertTrue(port >= 0 && port < queues.length);

		Lib.debug(dbgNet, "waiting for mail on port " + port);


		MailMessage mail = null;

		lock.acquire();
		while (mail==null) {
			cond.sleep();
			mail = (MailMessage) queues[port].removeFirst();
		}
		lock.release();

		if (Lib.test(dbgNet))
			System.out.println("got mail on port " + port + ": " + mail);

		return mail;
	}

	/**
	 * Wait for incoming messages, and then put them in the correct mailbox.
	 */
	private void postalDelivery() {
		while (true) {
			messageReceived.P();

			Packet p = Machine.networkLink().receive();

			MailMessage mail;

			try {
				mail = new MailMessage(p);
			}
			catch (MalformedPacketException e) {
				continue;
			}

			if (Lib.test(dbgNet))
				System.out.println("delivering mail to port " + mail.dstPort
						+ ": " + mail);

			if (queues[mail.dstPort].size() != BUF_SIZE) {
				// atomically add message to the mailbox and wake a waiting thread
				queues[mail.dstPort].add(mail);
			}
		}
	}

	/**
	 * Called when a packet has arrived and can be dequeued from the network
	 * link.
	 */
	private void receiveInterrupt() {
		messageReceived.V();

		lock.acquire();
		cond.wake();
		lock.release();
	}

	/**
	 * Send a message to a mailbox on a remote machine.
	 */
	public void send(MailMessage mail) {
		if (Lib.test(dbgNet))
			System.out.println("sending mail: " + mail);

		sendSema.P();
		sendList.add(mail);
	}

	/**
	 * Wait for a message to be added to the sendlist, then send it.
	 */
	private void postalSendery() {
		while(true){
			MailMessage mail = (MailMessage) sendList.removeFirst();

			if (Lib.test(dbgNet))
				System.out.println("sending mail to port " + mail.dstPort + ": " + mail);

			Machine.networkLink().send(mail.packet);
			messageSent.P();
			sendSema.V();
		}
	}


	/**
	 * Called when a packet has been sent and another can be queued to the
	 * network link. Note that this is called even if the previous packet was
	 * dropped.
	 */
	private void sendInterrupt() {
		messageSent.V();
	}

	private LockList[] queues;
	private SynchList sendList;
	private Semaphore messageReceived;	// V'd when a message can be dequeued
	private Semaphore messageSent;	// V'd when a message can be queued
	private Semaphore sendSema; // Enforce that the sendList stays under 16 items

	/** for breceive */
	private Lock lock;
	private Condition cond;

	private static final char dbgNet = 'n';
	private static final int BUF_SIZE = 16;
}
