package nachos.network;

import java.util.ArrayList;
import java.util.List;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import nachos.network.messages.*;

/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public NetProcess() {

        super();

    }

    public static final int
            syscallConnect = 11,
            syscallAccept = 12,
            syscallWrite = 7,
            syscallClose = 8;

    private int getNextFDIndex() {
        for(int i = 2; i < MAX_FD; i++){
            if(fileDescriptorTable[i] == null){
                return i;
            }
        }

        return -1;
    }

    private int handleConnect(int id, int portNum) {
        int descriptorAvail = getNextFDIndex();
        fileDescriptorsUsed.add(new Integer(descriptorAvail));

        if(descriptorAvail != -1) {
            OpenFile newConn = null;
            try
            {
                newConn = NetKernel.postOffice.handleConnect(id, portNum);
            }
            catch (MalformedPacketException e)
            {
                e.printStackTrace();
            }

            if(newConn == null)
            {
                System.err.println("Failed to get available file descriptor");
                return -1;
            }
            // Put it in the file array, give it a file descriptor (from User Process)
            fileDescriptorTable[descriptorAvail] = newConn;
            System.out.println("Node " + ((Connection)newConn).srcId  + ": successfully connectet to node " +
                    ((Connection)newConn).destId + ", port " + ((Connection)newConn).destPort + " , via port " + ((Connection)newConn).srcPort);

        }

        return descriptorAvail;
    }

    private int handleAccept(int port) {
        // Get available descriptor number
        int descriptorAvail = getNextFDIndex();
        fileDescriptorsUsed.add(new Integer(descriptorAvail));
        if ( descriptorAvail != -1 )
        {
            // Open a file for connection
            OpenFile acceptConn = null;
            try
            {
                acceptConn = NetKernel.postOffice.handleAccept(port);
            } catch (MalformedPacketException e)
            {
                e.printStackTrace();
            }
            if (acceptConn == null)
            {
                System.err.println("Failed to get available file descriptor");
                return -1;
            }
            // Put it in the file array, give it a file descriptor (from User Process)
            fileDescriptorTable[descriptorAvail] = acceptConn;
            System.out.println("Node " + ((Connection)acceptConn).srcId  + ": successfully accepted connection from node " +
                    ((Connection)acceptConn).destId + ", port " + ((Connection)acceptConn).destPort + " , via port " + ((Connection)acceptConn).srcPort);
        }
        return descriptorAvail;
    }

    /**
     * Handle the read() system call.
     * The function prototype is;
     * int read(int fd, char *buffer, int size);
     * where the arguments are fetched from registers a0, a1, and a2 respectively
     */
    protected int handleRead(int a0,int a1, int a2) {
        if (fileDescriptorsUsed.contains(new Integer(a0))) {
            //Lib.debug(dbgProcess, "NetProcess trying to read file descriptor " + a0);
            // verify the file descriptor id is legal
            if ( a0 < 0 || a0 > 17 )
            {
                Lib.debug(dbgProcess, "NetProcess::handleRead: illegal file descriptor");
                return -1;
            }

            OpenFile fd = fileDescriptorTable[a0];

            if( fd == null )
            {
                Lib.debug(dbgProcess, "NetProcess::handleRead: file descriptor " + a0 + " is null");
                return -1;
            }

            // read from network into a buffer
            byte buf [] = new byte[a2];
            int offset = 0;
            Connection ch = (Connection)fd;
            int bytesRead = ch.read(buf, offset, a2);
            if (bytesRead > 0)
            {
                String s = new String(buf);
                s = s.substring(0, bytesRead);
                System.out.print(s);

                writeVirtualMemory(a1,buf,offset,bytesRead);
            }

            return bytesRead;
        }
        else return super.handleRead(a0, a1, a2);
    }

    /**
     * Handle the write() system call.
     * int  write(int fd, char *buffer, int size);
     */
    protected int handleWrite(int a0,int a1, int a2)
    {
        if (fileDescriptorsUsed.contains(new Integer(a0)))
        {
            Lib.debug(dbgProcess, "handleWrite trying to write to file descriptor " + a0);

            // verify the file descriptor id is legal
            if ( a0 < 0 || a0 > 17 )
            {
                Lib.debug(dbgProcess, "NetProcess::handleWrite: illegal file descriptor");
                return -1;
            }

            OpenFile fd = fileDescriptorTable[a0];
            if( fd == null )
            {
                Lib.debug(dbgProcess, "NetProcess::handleWrite: file descriptor " + a0 + " is null");
                return -1;
            }

            byte buf [] = new byte[a2];
            int offset = 0;
            int bytesRead = readVirtualMemory(a1,buf);
            if(bytesRead != a2 )
            {
                Lib.debug(dbgProcess, "NetProcess::handleWrite: virual memory read less bytes than excepted " +bytesRead);
                return -1;
            }
            // write to the network
            Connection ch = (Connection)fd;
            int bytesWritten = ch.write(buf,offset,a2);
            if (bytesWritten > 0)
            {
                System.out.println("Sent "+ bytesWritten + " bytes, to node " + ch.destId + " port " + ch.destPort);
            }

            return bytesWritten;
        }
        else return super.handleRead(a0, a1, a2);
    }

    /**
     * Handle the close() system call.
     * int  close(int fd);
     */
    protected int handleClose( int a0 ) {
        if (fileDescriptorsUsed.contains(new Integer(a0))) {
            //retrieve the file descriptor
            OpenFile openfile = fileDescriptorTable[a0];

            if ( openfile == null )
                return -1;

            // Close the file
            openfile.close();

            return 0;
        }
        else return super.handleClose(a0);
    }

    public void removeFileDescriptor( int a0 ) {
        Integer integerFD = new Integer(a0);
        if (fileDescriptorsUsed.contains(integerFD))
        {

            // Remove from descriptorList
            fileDescriptorTable[a0] = null;
            System.out.println("Sucessfully closed the connection "+a0);
            fileDescriptorsUsed.remove(integerFD);
        }
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
                return handleConnect(a0,a1);
            case syscallAccept:
                return handleAccept(a0);
            case syscallRead:
                return handleRead(a0,a1,a2);
            case syscallWrite:
                return handleWrite(a0,a1,a2);
            case syscallClose:
                return handleClose( a0 );
            default:
                return super.handleSyscall(syscall, a0, a1, a2, a3);
        }
    }

    private static final char dbgProcess = 'n';
    private ArrayList<Integer> fileDescriptorsUsed = new ArrayList<Integer>();
}
