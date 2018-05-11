package nachos.network;

import java.util.Random;
import java.util.Vector;

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
		linkAddress = Machine.networkLink().getLinkAddress();

		messageReceived = new Semaphore(0);
		messageSent = new Semaphore(0);
		sendLock = new Lock();

		queues = new SynchList[MailMessage.portLimit];
		for (int i=0; i<queues.length; i++)
			queues[i] = new SynchList();

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
	 * Retrieve a message on the specified port, waiting if necessary.
	 *
	 * @param	port	the port on which to wait for a message.
	 *
	 * @return	the message received.
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

			// atomically add message to the mailbox and wake a waiting thread
			queues[mail.dstPort].add(mail);
		}
	}

	/**
	 * Called when a packet has arrived and can be dequeued from the network
	 * link.
	 */
	private void receiveInterrupt() {
		messageReceived.V();
	}

	/**
	 * Send a message to a mailbox on a remote machine.
	 */
	public void send(MailMessage mail) {
		if (Lib.test(dbgNet))
			System.out.println("sending mail: " + mail);

		sendLock.acquire();

		Machine.networkLink().send(mail.packet);
		messageSent.P();

		sendLock.release();
	}

	/**
	 * Called when a packet has been sent and another can be queued to the
	 * network link. Note that this is called even if the previous packet was
	 * dropped.
	 */
	private void sendInterrupt() {
		messageSent.V();
	}


	public OpenFile handleConnect(int id, int port) throws MalformedPacketException
	{
		int newPort =-1;
		boolean status = true;
		int i=0;
		while(i < maxPorts + 30)
		{
			newPort = (new Random()).nextInt(maxPorts);
			if (! activeConnections.contains (linkAddress + ":" + newPort + ":" + id + ":" + port) )
				break;

			if (i == maxPorts - 1)
			{
				status = false;
				break;
			}
			++i;
		}

		if(status == false)
		{
			return null;
		}

		Connection ch = new Connection(linkAddress, newPort, id, port);

		activeConnections.add(linkAddress + ":" + newPort + ":" + id + ":" + port);
		connectionList.add(ch);

		OpenFile retval = ch.connectToSrv();
		if(retval == null)
		{
			activeConnections.remove(linkAddress + ":" + newPort + ":" + id + ":" + port);
			connectionList.remove(ch);
		}
		return retval;
	}

	public OpenFile handleAccept(int port) throws MalformedPacketException
	{
		MailMessage msg = receive(port);
		if(msg == null)
		{
			return null;
		}
		byte flag = msg.contents[0];
		if(flag == Message.SYN)
		{
			// create a new connection
			Connection ch = new Connection(linkAddress, port, msg.packet.srcLink, msg.srcPort);
			ch.state = Connection.ConnectionState.SYN_RCVD;
			activeConnections.add(linkAddress + ":" + port + ":" + msg.packet.srcLink + ":" + msg.srcPort);
			connectionList.add(ch);
			// send a SYN_ACK
			byte[] contents = new byte[1];
			contents[0] = Message.SYN_ACK;
			MailMessage syngenerateACK = new MailMessage(msg.packet.srcLink, msg.srcPort, linkAddress, port, contents);
			send(syngenerateACK);
			ch.state = Connection.ConnectionState.ESTABLISHED;
			ch.startRecieverThread();
			return ch;
		}
		return null;
	}


	private SynchList[] queues;
	private Semaphore messageReceived;	// V'd when a message can be dequeued
	private Semaphore messageSent;	// V'd when a message can be queued
	private Lock sendLock;
	public final static int maxPorts = 128;
	private int linkAddress;
	private Vector<String> activeConnections = new Vector<String> ();
	private Vector<Connection> connectionList = new Vector<Connection> ();

	private static final char dbgNet = 'n';
}
