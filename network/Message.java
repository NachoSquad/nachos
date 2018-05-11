package nachos.network;

import nachos.machine.MalformedPacketException;

public class Message {
    public static byte SYN = 0x01;
    public static byte SYN_ACK = 0x02;
    public static byte ACK = 0x03;
    public static byte DAT = 0x04;
    public static byte FIN = 0x05;
    public static byte FINACK = 0x06;

    public Message(int dstLink, int dstPort, int srcLink, int srcPort, short msgId, byte type) throws MalformedPacketException {
        byte[] msgIdBuf = new byte[2];
               msgIdBuf[0] = (byte)(msgId >> 8);
               msgIdBuf[1] = (byte)(msgId & 0x00FF);

        byte[] contents = new byte[3];
               contents[0] = type;

        System.arraycopy(msgIdBuf, 0, contents, 1, 2);
        msg = new MailMessage(dstLink, dstPort, srcLink, srcPort, contents);
    }

    public MailMessage mailMsg() {
        return msg;
    }

    public static boolean isMessageType(MailMessage msg, byte type) {
        boolean retval = false;
        if(msg.contents.length == 3) {
            if(msg.contents[0] == type) {
                retval = true;
            }
        }

        return retval;
    }

    public static short getMsgId(MailMessage msg) {
        short retval = 0;
        if(msg.contents.length >= 3) {
            retval |= msg.contents[1];
            retval <<= 8;
            retval |= msg.contents[2];
        }
        return retval;
    }

    public static final int maxPayload = 23;
    MailMessage msg;
}
