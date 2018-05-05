package nachos.network;

import nachos.machine.*;

/**
 * This file is user-generated and is used for messaging
 **/

public class MailMessage {
    /**
     * Allocate a new net message to be sent, using the specified parameters.
     *
     * @param	dstLink		the destination link address.
     * @param	dstPort		the destination port.
     * @param	srcLink		the source link address.
     * @param	srcPort		the source port.
     * @param	flags		the FIN,STP,ACK,SYN flags.
     * @param	seqno		the sequence number.
     * @param	contents	the contents of the packet.
     */
    public MailMessage(int dstLink, int dstPort, int srcLink, int srcPort,
                       boolean[] flags, int seqno, byte[] contents) throws MalformedPacketException {

        if (dstPort < 0 || dstPort >= portLimit || srcPort < 0 || srcPort >= portLimit || contents.length > maxContentsLength) {
            throw new MalformedPacketException();
        }

        this.dstPort = (byte) dstPort;
        this.srcPort = (byte) srcPort;
        short temp = 0;
        
        for (int i=0; i<4; i++) {
            if(flags[i]) temp += 1<<(3-i);
        }

        this.flags = (byte)temp;
        this.seqno = Lib.bytesFromInt(seqno);
        this.contents = contents;

        byte[] packetContents = new byte[headerLength + contents.length];

        packetContents[0] = (byte) dstPort;
        packetContents[1] = (byte) srcPort;
        packetContents[2] = (byte) 0;
        packetContents[3] = this.flags;

        System.arraycopy(this.seqno, 0, packetContents, 4, this.seqno.length);

        System.arraycopy(contents, 0, packetContents, headerLength, contents.length);

        packet = new Packet(dstLink, srcLink, packetContents);
    }

    /**
     * Allocate a new net message using the specified packet from the network.
     *
     * @param	packet	the packet containg the mail message.
     */
    public MailMessage(Packet packet) throws MalformedPacketException {
        this.packet = packet;

        // make sure we have a valid header
        if (packet.contents.length < headerLength ||
                packet.contents[0] < 0 || packet.contents[0] >= portLimit ||
                packet.contents[1] < 0 || packet.contents[1] >= portLimit ||
                packet.contents[2] != 0 || packet.contents[3] > 15)
            throw new MalformedPacketException();

        dstPort = packet.contents[0];
        srcPort = packet.contents[1];
        flags = packet.contents[3];


        seqno = new byte[4];
        System.arraycopy(packet.contents, 4, seqno, 0, seqno.length);

        contents = new byte[packet.contents.length - headerLength];
        System.arraycopy(packet.contents, headerLength, contents, 0, contents.length);
    }

    /**
     *	Returns the flags as a boolean array.
     */
    public boolean[] getFlags(){
        boolean[] ret = new boolean[4];
        for (int i=0; i<4; i++) {
            ret[i] = ((flags>>(3-i))&1) != 0;
        }
        return ret;
    }

    /**
     * Returns the sequence number.
     */
    public int getSeqno(){
        return Lib.bytesToInt(seqno,0);
    }

    /**
     * Return a string representation of the message headers.
     */
    public String toString() {
        boolean[] bflags = getFlags();
        return "from (" + packet.srcLink + ":" + srcPort +
                ") to (" + packet.dstLink + ":" + dstPort +
                "), " + (bflags[0]?"FIN ":"") + (bflags[0]?"STP ":"") +
                (bflags[0]?"ACK ":"") + (bflags[0]?"SYN ":"") + "seqno: " +
                getSeqno() + ", " + contents.length + " byte" + (contents.length==1?"":"s");
    }

    /** This message, as a packet that can be sent through a network link. */
    public Packet packet;
    /** The port used by this message on the destination machine. */
    public int dstPort;
    /** The port used by this message on the source machine. */
    public int srcPort;
    /** 0, 0, 0, 0, FIN, STP, ACK, SYN */
    public byte flags;
    /** The sequence number */
    public byte[] seqno;
    /** The contents of this message, excluding the mail message header. */
    public byte[] contents;

    /**
     * The number of bytes in a mail header. The header is formatted as
     * follows:
     *
     * <table>
     * <tr><td>offset</td><td>size</td><td>value</td></tr>
     * <tr><td>0</td><td>1</td><td>destination port</td></tr>
     * <tr><td>1</td><td>1</td><td>source port</td></tr>
     * </table>
     */
    public static final int headerLength = 8;

    /** Maximum payload (real data) that can be included in a single mesage. */
    public static final int maxContentsLength =
            Packet.maxContentsLength - headerLength;

    /**
     * The upper limit on mail ports. All ports fall between <tt>0</tt> and
     * <tt>portLimit - 1</tt>.
     */
    public static final int portLimit = 128;
}
