package nachos.network;

import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;

public class DataMessage {

    public DataMessage(int dstLink, int dstPort, int srcLink, int srcPort, byte[] data, short msgId) throws MalformedPacketException {
        byte[] msgIdBuf = new byte[2];
        msgIdBuf[0] = (byte)(msgId >> 8);
        msgIdBuf[1] = (byte)(msgId & 0x00FF);
        byte[] contents = new byte[data.length + 3];
        contents[0] = Message.DAT;
        System.arraycopy(msgIdBuf, 0, contents, 1, 2);
        System.arraycopy(data, 0, contents, 3, data.length);
        msg = new MailMessage(dstLink, dstPort, srcLink, srcPort, contents);
    }

    MailMessage mailMsg() {
        return msg;
    }

    public static short getMsgId(MailMessage msg) {

        short retval = 0;
        if(msg.contents.length >= 3)
        {
            if(msg.contents[0] == Message.DAT)
            {
                retval |= msg.contents[1];
                retval <<= 8;
                retval |= msg.contents[2];
            }
        }
        return retval;
    }

    public static final int maxPayload = 23;
    MailMessage msg;
}
