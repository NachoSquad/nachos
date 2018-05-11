package nachos.network;

import java.util.LinkedList;
import java.util.Vector;

import nachos.machine.MalformedPacketException;
import nachos.machine.OpenFile;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.Semaphore;
import nachos.threads.SynchList;
import nachos.network.Message;

public class Connection extends OpenFile {



    public Connection(int srcId, int srcPort, int destId, int destPort)
    {
        this.srcId = srcId;
        this.srcPort = srcPort;
        this.destId = destId;
        this.destPort = destPort;
        state = ConnectionState.CLOSED;
        slidingWnd = new Window();
    }
    public static void changeState(ConnectionState newState) {
        state = newState;
    }

    public OpenFile connectToSrv() throws MalformedPacketException {
        if(state != ConnectionState.CLOSED) {
            return null;
        }

        byte[] content = new byte[1];
        content[0] = Message.SYN;
        MailMessage msg = new MailMessage (destId, destPort, srcId, srcPort, content);
        NetKernel.postOffice.send(msg);
        state = ConnectionState.SYN_SENT;

        /**
         * Wait for SYN-ACK
         */
        MailMessage responseMessage = NetKernel.postOffice.receive(srcPort);

        byte responseFlag = responseMessage.contents[0];
        if(responseFlag ==  Message.SYN_ACK) {
            state = ConnectionState.ESTABLISHED;
            startRecieverThread();
            return this;
        }

        return null;
    }

    public void close() {
        if(state == ConnectionState.CLOSED) {
            return;
        }

        byte[] content = new byte[1];
        content[0] = Message.FIN;
        MailMessage msg;
        try {
            msg = new MailMessage (destId, destPort, srcId, srcPort, content);

            NetKernel.postOffice.send(msg);
            state = ConnectionState.CLOSING;
        } catch (MalformedPacketException e) {
            e.printStackTrace();
        }
        return;
    }


    public int read(byte[] buf, int offset, int length) {
        if(state != ConnectionState.ESTABLISHED) {
            return -1;
        }

        int bytesRecieved = 0;

        QueueLock.acquire();
        while(MessageInQueue.size() > 0) {
            MailMessage msg = MessageInQueue.removeFirst();
            try {
                System.arraycopy(msg.contents, 3, buf, bytesRecieved, msg.contents.length - 3);
            } catch(Exception e) {

            }

            bytesRecieved += msg.contents.length - 3;
        }
        QueueLock.release();

        return bytesRecieved;
    }


    public int write(byte[] buf, int offset, int length) {
        if(state != ConnectionState.ESTABLISHED) {
            return -1;
        }

        int netPayload = DataMessage.maxPayload;
        int numMessages = calcNumMessages(length, MailMessage.maxContentsLength - 3);
        int lastMessageLength = lastMessageLen(length, MailMessage.maxContentsLength - 3);

        int idx = 0;
        int bytesSent = 0;
        for(int i = 0; i < numMessages - 1; ++i) {
            byte[] contents = new byte[netPayload];

            System.arraycopy(buf, idx, contents, 0, netPayload);

            idx += netPayload;
            MailMessage msg = null;

            try {
                DataMessage dm = new DataMessage(destId, destPort, srcId, srcPort, contents, sendMsgId);
                sendMsgId++;
                msg = dm.mailMsg();

            } catch (MalformedPacketException e) {
                e.printStackTrace();
            }

            /**
             * Check if sliding window is full, if it is we wait for the reciever to wake up the system
             */
            QueueLock.acquire();
            if(slidingWnd.isWindowFull()) {
                QueueLock.release();
                sema.P();
            } else {
                slidingWnd.sentMsgIdList.add(new Short((short)(sendMsgId - 1)));
                QueueLock.release();
            }

            // Sends data
            NetKernel.postOffice.send(msg);
            bytesSent += netPayload;
        }

        if(lastMessageLength > 0) {
            // last msg
            byte[] contents = new byte[lastMessageLength];
            System.arraycopy(buf, idx, contents, 0, lastMessageLength);
            MailMessage msg = null;
            try {
                DataMessage dm = new DataMessage(destId, destPort, srcId, srcPort, contents, sendMsgId);
                sendMsgId++;
                msg = dm.mailMsg();
            } catch (MalformedPacketException e) {
                e.printStackTrace();
            }
            QueueLock.acquire();

            if(slidingWnd.isWindowFull()) {
                QueueLock.release();
                sema.P();
            } else {
                slidingWnd.sentMsgIdList.add(new Short((short)(sendMsgId - 1)));
                QueueLock.release();
            }

            NetKernel.postOffice.send(msg);
            bytesSent += lastMessageLength;
        }


        return bytesSent;
    }

    int calcNumMessages(int contentLen, int netPayload) {
        return (contentLen / netPayload) + 1;
    }

    int lastMessageLen(int contentLen, int netPayLoad) {
        return contentLen % netPayLoad;
    }

    void startRecieverThread() {
        if(rcvLoopThread == null) {
            rcvLoopThread = new KThread(new Reciever(srcPort, MessageInQueue, QueueLock, sema, slidingWnd));
            rcvLoopThread.setName("connection rcv loop # " + connectionId++);
            rcvLoopThread.fork();
        }
    }

    private static class Reciever implements Runnable {
        Reciever(int port, LinkedList<MailMessage> q, Lock lk, Semaphore s, Window w) {
            this.port = port;
            this.q = q;
            this.lk = lk;
            this.windowClearSema = s;
            this.wnd = w;
        }

        public void run() {
            while(true) {
                MailMessage msg = NetKernel.postOffice.receive(port);

                if (Message.isMessageType(msg, Message.FIN)) {
                    sendFinAck(msg);
                } else if (Message.isMessageType(msg, Message.FINACK)) {
                    handleFinAck(msg);
                } else if(Message.isMessageType(msg, Message.ACK)) {
                    updateWindow(msg);
                } else {
                    sendAck(msg);
                }

                lk.acquire();
                q.add(msg);
                lk.release();
            }
        }

        void sendAck(MailMessage msg) {
            short msgId = DataMessage.getMsgId(msg);
            try {
                generateACK ack = new generateACK(msg.packet.srcLink, msg.srcPort, msg.packet.dstLink, msg.dstPort, msgId);
                NetKernel.postOffice.send(ack.mailMsg());
            } catch (MalformedPacketException e) {
                e.printStackTrace();
            }
        }

        void sendFinAck(MailMessage msg) {
            changeState(ConnectionState.CLOSING);
            short msgId = DataMessage.getMsgId(msg);

            try {
                Message finack = new Message(msg.packet.srcLink, msg.srcPort, msg.packet.dstLink, msg.dstPort, msgId, Message.FINACK);
                NetKernel.postOffice.send(finack.mailMsg());

            } catch (MalformedPacketException e) {
                e.printStackTrace();
            }
            changeState(ConnectionState.CLOSED);
        }

        void handleFinAck(MailMessage msg) {
            changeState(ConnectionState.CLOSED);
        }

        void updateWindow(MailMessage msg) {
            int originSize = wnd.sentMsgIdList.size();
            Short s = new Short(generateACK.getMsgId(msg));
            lk.acquire();

            if(wnd.sentMsgIdList.contains(s)) {
                wnd.sentMsgIdList.remove(s);
                if(originSize == Window.maxWindowCapacity) {
                    // wake sender to send messages
                    windowClearSema.V();
                }
            }
            lk.release();
        }

        private int port;
        private LinkedList<MailMessage> q;
        private Lock lk;
        private Semaphore windowClearSema;
        private Window wnd;
    }

    public int srcId, srcPort, destId, destPort;
    private short sendMsgId = 0;
    KThread rcvLoopThread = null;
    LinkedList<MailMessage> MessageInQueue = new LinkedList<MailMessage>();
    Lock QueueLock = new Lock();
    Semaphore sema = new Semaphore(0);
    Window slidingWnd;

    public static int connectionId = 0;
    static ConnectionState state;
    public static enum ConnectionState {SYN_SENT, SYN_RCVD, ESTABLISHED, STP_RCVD, STP_SENT, CLOSING, CLOSED}
}
