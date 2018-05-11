package nachos.network;

import java.util.LinkedList;

public class Window {
    public Window() {
        sentMsgIdList = new LinkedList<Short>();
    }

    boolean isWindowFull() {
        return sentMsgIdList.size() == 16;
    }
    public LinkedList<Short> sentMsgIdList;
    public static final int maxWindowCapacity = 16;
}
