package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.network.*;
import java.util.ArrayList;

/**
 * This file is user-generated and is used to be for connections
 */

public class Connection extends OpenFile {
    /** Constant flag variables for convinience */
    private static final boolean[] DATA_FLAGS = {false,false,false,false};
    private static final boolean[] SYN_FLAGS = {false,false,false,true};
    private static final boolean[] ACK_FLAGS = {false,false,true,false};
    private static final boolean[] STP_FLAGS = {false,true,false,false};
    private static final boolean[] FIN_FLAGS = {true,false,false,false};

    /** for adding flags to presets */
    private static final boolean[] addFlag(boolean[] flags, int toAdd){
        boolean[] ret = new boolean[4];
        for (int i=0; i<4; i++) {
            ret[i]=flags[i]||(i==toAdd);
        }
        return ret;
    }

    /** Constructor!
     * This will be called when creating a new connection
     * The source and destination ports and nodes are
     * stored as members for future use.
     * The current and expected sequence numbers are set to 0
     */
    public Connection(int srcPort, int srcNode, int dstPort, int dstNode, Runnable closer){
        super(null, "Connection");
        this.srcPort = srcPort;
        this.srcNode = srcNode;
        this.dstPort = dstPort;
        this.dstNode = dstNode;
        this.closer = closer;
        this.currSeqno = 0;
        this.expectedSeqno = 0;
        this.isConnected = false;
        this.readCache = new ArrayList<Byte>();
        this.PO = NetKernel.postOffice;
    }

    /**
     * This will be called when creating a new connection
     * The source and destination ports and nodes are
     * taken from the SYN packet then stored as members for future use.
     * The current and expected sequence numbers are set to 0
     */
    public Connection(MailMessage SYN, Runnable closer){
        this(SYN.dstPort,SYN.packet.dstLink,SYN.srcPort,SYN.packet.srcLink,closer);
    }

    /**
     * Initiates the 2-way open handshake by sending a SYN
     */
    public void open() {
        if (isConnected) { return; }
        MailMessage SYN;
        try{ SYN = new MailMessage(dstNode,dstPort,srcNode,srcPort,SYN_FLAGS,0,new byte[0]);}
        catch (Exception e) { System.out.println(e); closer.run(); return;}
        PO.send(SYN);
        MailMessage nm;
        do {
            nm = PO.bReceive(srcPort);
        } while (nm.getFlags()[2]);
        isConnected = true;
    }

    /**
     * Closes the connection with a FIN-ACK
     */
    public void close() {
        if (!isConnected) {
            return;
        }

        MailMessage STP;
        try{ STP = new MailMessage(dstNode,dstPort,srcNode,srcPort,STP_FLAGS,0,new byte[0]);}
        catch (Exception e) { System.out.println(e); closer.run(); return;}
        PO.send(STP);

        while (isConnected) {
            if (PO.bReceive(srcPort).getFlags()[0]) {
                isConnected = false;
            }
        }

        closer.run();
    }

    /**
     * read()
     * Takes a given buffer and returns the bytes read
     */
    public int read(byte[] buf, int offset, int length) {
        int toRead = Math.min(offset+buf.length,length);
        int read = 0;

        if(!readCache.isEmpty()) {
            // read the data since it's not empty
            int data = Math.min(toRead, readCache.size());

            System.arraycopy(readCache.toArray(new Byte[t]), 0, buf, offset, data);
            read += t;
            for(int i=0; i < t; i++) {
                readCache.remove(0);
            }
        }

        // close if everything is read
        if (!isConnected) {
            return read;
        }

        while (read < toRead) {
            MailMessage nm = PO.receive(srcPort);

            // die is we have no message
            if (nm == null) {
                break;
            }

            if (nm.getFlags()[1]) {
                // start sending a fin
                MailMessage FIN;
                try{
                    FIN = new MailMessage(
                            dstNode,
                            dstPort,
                            srcNode,
                            srcPort,
                            FIN_FLAGS,
                            0,
                            new byte[0])
                } catch (Exception e) {
                    // if we fail we fail
                    System.out.println(e);
                    closer.run();
                    return -1;
                }

                PO.send(FIN);

                // close connection
                isConnected = false;
                closer.run();
                break;
            }


            // check if our packet sequence number is not correct
            if (nm.getSeqno() != expectedSeqno) {
                System.out.println("Out of order packet!");
            }

            // get next sequence number
            expectedSeqno = nm.getSeqno() + 1;

            byte[] contents = nm.contents;

            // read
            for (int i=0; i < contents.length; i++) {
                if (toRead != read) {
                    buf[offset+read] = contents[i];
                    read++;
                } else {
                    readCache.add(contents[i]);
                }
            }
        }

        return read;
    }


    /**
     * This will send an ACK back to the user
     */
    public void recievedSyn(){
        MailMessage ACK;
        try{
            ACK = new MailMessage(
                    dstNode,
                    dstPort,
                    srcNode,
                    srcPort,
                    ACK_FLAGS,
                    0,
                    new byte[0]);
        } catch (Exception e) {
            System.out.println(e);
            closer.run();
            return;
        }

        // die
        PO.send(ACK);
        isConnected = true;
    }

    /**
     * Sends bytes via a DATA_FLAG
     */
    public int write(byte[] buf, int offset, int length) {
        if (!isConnected) { return -1; }
        int toWrite = Math.min(length,buf.length-offset);
        int written = 0;

        while(written!=toWrite){
            byte[] contents = new byte[Math.min(toWrite-written,maxContentSize)];

            System.arraycopy(buf,offset+written,contents,0,contents.length);

            MailMessage nm;
            try{
                nm = new MailMessage(
                        dstNode,
                        dstPort,
                        srcNode,
                        srcPort,
                        DATA_FLAGS,
                        currSeqno,
                        contents);
            } catch (Exception e) {
                System.out.println(e);
                closer.run();
                return -1;
            }

            PO.send(nm);

            currSeqno++;
            written += contents.length;
        }
        return written;
    }

    /** Keeps track of the connectedness */
    public boolean isConnected;

    /** Addresses of source, destination */
    private int srcPort, srcNode, dstNode, dstPort;

    /** Curr is what you send, expected is what you receive */
    private int currSeqno, expectedSeqno;

    /** This will cache the extra bytes read */
    private ArrayList<Byte> readCache;

    /** This will close the connection */
    private Runnable closer;

    /** this is just a reference to the NetKernel PO */
    private PostOffice PO;

    public static final int maxContentSize = Packet.maxContentsLength - MailMessage.headerLength;
}
