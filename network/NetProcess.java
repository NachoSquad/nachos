
package nachos.network;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.LinkedList;


/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends VMProcess {
    /**
     * Allocate a new process.
     */
    public NetProcess() {
        super();

        freePorts = new LinkedList<Integer>();
        for (int i=0; i<MailMessage.portLimit; i++) {
            freePorts.offer(i);
        }

        plock = new Lock();
    }

    private static final int
	syscallConnect = 11,
	syscallAccept = 12;


    //  connect retrieves a file descriptor along with our network infromation to call a request (SYN) to a service
    private int connect(int host, int port){
        if(port < 0 || port >= MailMessage.portLimit){
            return -1;
        }
        int fd = 0;
        for (; fd < MAX_FD; fd++) {
            if(fileDescriptorTable[fd] == null) {
                break;
            }
        }
        if (fd == MAX_FD) {
            return -1;
        }

        int myPort = getPort();

        final int fileDescriptorIndex = fd;
        Runnable closer = new Runnable(){
            public void run(){
                fileDescriptorTable[fileDescriptorIndex] = null;
            }
        };
        Connection theFile = new Connection(myPort, Machine.networkLink().getLinkAddress(), port, host, closer);

        theFile.open();

        fileDescriptorTable[fd] = theFile;
        return fd;
    }

    // accept is the opposite of this as it allows us to accept a connection from somwhere
    private int accept(int port){
        if(port < 0 || port >= MailMessage.portLimit){
            return -1;
        }

        int fd = 0;

        for (; fd < MAX_FD; fd++) {
            if(fileDescriptorTable[fd] == null) {
                break;
            }
        }

        if (fd == MAX_FD) {
            return -1;
        }

        Connection theFile;
        MailMessage nm;

        try {
            nm = NetKernel.postOffice.receive(port);
        } catch (Exception e) {
            return -1;
        }

        if (!nm.getFlags()[3] || nm==null) {
            return -1;
        }

        final int fileDescriptorIndex = fd;
        // this runnable closes all the fd table
        Runnable closer = new Runnable(){
            public void run(){
                fileDescriptorTable[fileDescriptorIndex] = null;
            }
        };

        theFile = new Connection(nm,closer);

        theFile.recievedSyn();

        fileDescriptorTable[fd] = theFile;

        return fd;
    }

    // lock for ports
    private Lock plock;

    // list of unused ports
    private final LinkedList<Integer> freePorts;

    // get unused port
    private int getPort(){
        int nextPort;
        plock.acquire();
        nextPort = freePorts.poll();
        plock.release();
        return nextPort;
    }

    // release port
    private void releasePort(int port){
        plock.acquire();
        freePorts.offer(port);
        plock.release();
        return;
    }

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>11</td><td><tt>int  connect(int host, int port);</tt></td></tr>
     * <tr><td>12</td><td><tt>int  accept(int port);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
        case syscallConnect:
            return connect(a0, a1);
        case syscallAccept:
            return accept(a0);
        default:
            return super.handleSyscall(syscall, a0, a1, a2, a3);
        }
    }
}
