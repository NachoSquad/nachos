package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.LinkedList;

import java.util.LinkedList; 
import java.util.Iterator; 
import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */

public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
	int numPhysPages = Machine.processor().getNumPhysPages();
	pageTable = new TranslationEntry[numPhysPages];
	for (int i=0; i<numPhysPages; i++)
	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
		new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
    	Lib.assertTrue(maxLength >= 0);

    	byte[] bytes = new byte[maxLength+1];

    	int bytesRead = readVirtualMemory(vaddr, bytes);

    	for (int length=0; length<bytesRead; length++) {
    		if (bytes[length] == 0)
    			return new String(bytes, 0, length);
	}

		return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
    	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

    	byte[] memory = Machine.processor().getMemory();
	
    	// for now, just assume that virtual addresses equal physical addresses
    	if (vaddr < 0 || vaddr >= memory.length)
    		return 0;

    	int amount = Math.min(length, memory.length-vaddr);
    	System.arraycopy(memory, vaddr, data, offset, amount);

    	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
    	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
    	
    	int count,soffSet,doffSet,amountLeft=0;
    	int vpn = (vaddr/pageSize);
    	int poffset = vaddr % pageSize;
    	int physMem = pageTable[vpn]*pageSize+offset;
    	byte [] machineMem= Machine.processor().getMemory();
    	boolean validAddress=((data.length>0) && (length>0) && data.(length-offset) && (!(length<data.length)) && (inVaddressSpace(vaddr)) && inPhysAddressSpace(physMem) && !pageTable[vpn].readOnly );
    	
    	if(validAddress) {
    	do {
    		amountLeft=Math.min(writeBytes, Math.min(pageSize - pageOffset, length - numBytesWritten));
    		soffSet=offset+count;
    		doffset=pageTable[vpn].ppn*pageSize)+offset;
    		System.arraycopy(data, soffSet, machineMem, doffSet, length);
    		    count += amountLeft;
             writeBytes -= count;
             poffset = 0;
             pageTable[vpn].dirty = true; 
             pageTable[vpn].used = true; 
             vpn++;
    	}while( (count < length) && (vpn <= pageTable.length) && (pageTable[vpn].valid)
                && (!pageTable[vpn].readOnly) && inPhysAddressSpace(doffset)
                && (writeBytes>0) );
    	}	
    	return count;	
    	
    }
    
    
    
    
    
    
    
    

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}
	
	pageTable = new TranslationEntry[numPages];
	
	for (int i = 0; i < numPages; i++) {
		int physPage = UserKernel.allocatePage();
		if (physPage < 0) {
			Lib.debug(dbgProcess,  "\tunable to allocate pages; tried" + numPages + ", did" + i);
			for (int j = 0; j < i; j++){
				if (pageTable[j].valid){
					UserKernel.deallocatePage(pageTable[j].ppn);
					pageTable[j].valid = false;					
				}
			}
			coff.close();
			return false;
		}
		pageTable[i] = new TranslationEntry(i, physPage, true, false, false);
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;

		// for now, just assume virtual addresses=physical addresses
		section.loadPage(i, vpn);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    	for (int i = 0; i < pageTable.length; i++){
    		if (pageTable[i].valid) {
    			UserKernel.deallocatePage(pageTable[i].ppn);
    			pageTable[i].valid = false;
    		}
    	}
    		
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {
    if (this.process_id != 1){
    	return 0;
    }

	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }


    private static final int
	    syscallHalt = 0,
		syscallExit = 1,
		syscallExec = 2,
		syscallJoin = 3,
		syscallCreate = 4,
		syscallOpen = 5,
		syscallRead = 6,
		syscallWrite = 7,
		syscallClose = 8,
		syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
	

	private int handleExec(int file, int argc, int argv) {
		Lib.debug(dbgProcess, "handleExec()");
		String Type = "[UserProcess][handleExec] ";

		if (argc < 1) {                                                    
			Lib.debug(dbgProcess, Type + "Error no arguments provided!");
			return -1;                                                     
		}                                                                  

		String filename = readVirtualMemoryString(file, 256);
		if (filename == null) {                                            
			Lib.debug(dbgProcess, Type + "Error: filename not defined");
			return -1;                                                     
		}                                                                  

		String fileType = filename.substring(filename.length() - 4, filename.length());
		if (fileType.equals(".coff")) {                                      
			Lib.debug(dbgProcess, Type + "Error: Invalid file extension, please provide .coff");
			return -1;                                                     
		}                                                                  

		// get arguments
		String args[] = new String[argc];
		byte temp[] = new byte[4];

		for (int i = 0; i < argc; i++) {                                   
			int cntBytes = readVirtualMemory(argv + i * 4, temp);

			if (cntBytes != 4) {                                           
				return -1;                                                 
			}                                                              

			int argAddress = Lib.bytesToInt(temp, 0);                      
			args[i] = readVirtualMemoryString(argAddress, 256);
		}                                                                  

		UserProcess childProcess = UserProcess.newUserProcess();
		childProcess.parentID = this.pid;

		this.children.add(childProcess.pid);
		Lib.debug(dbgProcess, Type + "Created new child process with id " + childProcess.parentID);

		// execute child process and create new thread
		boolean retval = childProcess.execute(filename, args);             

		if (retval) {                                                      
			return childProcess.pid;
		} else {
			return -1;                                                     
		}
	}
	
private int handleJoin(int childProcessId, int status) {
		Lib.debug(dbgProcess, "handleJoin()");
		String Type = "[UserProcess][handleJoin] ";

		boolean flag = false;
		int tmp = 0;                                                 
		Iterator<int> it = this.children.iterator();

		while(it.hasNext()) {                                             
			tmp = it.next();                                         
			if (tmp == childProcessId) {
				it.remove();                                              
				flag = true;
				break;                                                    
			}                                                             
		}                                                                 

		if (flag == false) {
			Lib.debug(dbgProcess, Type + "Process" + this.pid + " does not have a child with id (" + childProcessId + ")");
			return -1;                                                    
		}                                                                 

		// check if child has exited
		UserProcess childProcess = UserKernel.getProcessByID(childProcessId);

		if (childProcess == null) {
			Lib.debug(dbgProcess, Type + "Child process " + childProcessId + " has already been joined!");
			return -2;                                                    
		}                                                                 

		// join thread
		childProcess.thread.join();

		// unregister the child
		UserKernel.unregisterProcess(childProcessId);

		// store exit status
		byte temp[] = new byte[4];
		temp = Lib.bytesFromInt(childProcess.exitStatus);
		int cntBytes = writeVirtualMemory(status, temp);

		if (cntBytes != 4) {
			return 1;
		} else {
			return 0;
		}
	}
	private int handleExit(int exitStatus) {
		Lib.debug(dbgProcess, "handleExit()");
		String Type = "[UserProcess][handleExit] ";
		for(int i =0; i< 16; i++)
		{
			if(fds[i].file != null)
				handleClose(i);
		}
		while(children != null && !children.isEmpty())
		{
			int childProcessID=children.removeFirst();
			UserProcess childProcess = UserKernel.getProcessByID(childProcessID);
			childProcess.parentID = 1;
			
		}
		this.exitStatus = exitStatus;
		Lib.debug(dbgProcess, "exitStates : " + exitStatus);
		this.unloadSections();
		if(this.pid == 0) {
			Kernel.kernel.terminate();
		} else {
			UThread.finish();
		}
		Lib.assertNotReached();
	}

    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {

    	switch (syscall) {
    	case syscallHalt:
    		return handleHalt();
    	case syscallCreate:
    		return handleCreate(a0);
    	case syscallOpen: 
    		return handleOpen(a0);
    	case syscallRead:
    		return handleRead(a0, a1, a2);
    	case syscallWrite:
    		return handleWrite(a0, a1, a2);
    	case syscallClose:
    		return handleClose(a0);
    	case syscallUnlink:
    		return handleUnlink(a0);
    case syscallExit:
    		return handleExit(a0);
    case syscallExec:
     	return handleExec(a0,a1,a2);
    case syscallJoin:
    	return handleJoin(a0, a1);
    		
	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");

	}
	return 0;
    }

   
    private int handleOpen(int a0) {
    	String filename = this.readVirtualMemoryString(a0, 255);
    	if (filename == null) {
    		return -1;
    	}
    	
    	OpenFile file = ThreadedKernel.filesystem.open(filename, false);
    	if (file == null){
    		return -1;
    	}
    	
    	Integer filedescriptor = available_descriptors.remove(0);
    	openfiles.put(filedescriptor, file);
    	return filedescriptor;
    	
    	
    }
    
    private int handleCreate(int a0) {
    	String filename = this.readVirtualMemoryString(a0,  255);
    	if (filename == null) {
    		return -1;
    	}
    	
    	OpenFile file = ThreadedKernel.fileSystem.open(filename, true);
    	if (file == null) {
    		return -1;
    	}
    	
		if (available_descriptors.size() < 1) {
    		return -1;
    	}
		
    	Integer filedescriptor = available_descriptors.remove(0);
    	openfiles.put(filedescriptor, file);
    	return filedescriptor;
    }
    
    
    private int handleRead(int a0, int a1, int a2) {                      
	   
        int handle = a0;                    
        int vaddr = a1;                                
        int bufsize = a2;                                    

        // get data about file descriptor
        if (handle < 0 || handle > 16|| fds[handle].file == null)                              
            return -1;                                                    

        if (bufsize < 0) {                                                          
            return  -1;                                                   
        }                                                                
        else if (bufsize == 0) {                                                               
            return 0;                                                    
        }                                                                 

        FileDescriptor fd = fds[handle];                                 
        byte[] buf = new byte[bufsize];                                   

        // invoke read through classOpenFileWithPosition                        
        //rather than class StubFileSystem                                     
        int rnum = fd.file.read(buf, 0, bufsize);                      

        if (rnum < 0) {                                                
            return -1;                                                    
        }                                                                 
        else {                                                            
            int wnum = writeVirtualMemory(vaddr, buf, 0, rnum);    
            if (wnum < 0) {                                           
                return -1;                                                
            }                                                             
            else {                                                        
            //fd.position = fd.position + writenum;                         
               return wnum;                                           
            }                                                             
        }                                                                 
    }    
    private int handleWrite(int a0, int a1, int a2) {
	    Lib.debug(dbgProcess, "handleWrite()");                           
         
        int fhandle = a0;                 //a0 is file descriptor handle 
        int bufadd = a1;                  // a1 is buf address            
        int bufsize = a2;                // a2 is buf size               
        int retval;                                                         

	 
        // if its an invalid file
        if (fhandle < 0 || fhandle >16                                  
                || fds[fhandle].file == null) {                                                
            return -1;                                                    
        }                                                                 

        FileDescriptor fd = fds[fhandle];                                  

        if (bufsize < 0) {                                                              
            return  -1;                                                   
        }                                                                 
        else if (bufsize == 0) {                                                                   
            return 0;                                                     
        }                                                                 


        byte[] buf = new byte[bufsize];                                     

        int bytesRead = readVirtualMemory(bufadd, buf);                       

        if (bytesRead < 0) {                                              
            return -1;                                                    
        }                                                                 

        // invoke write through stubFilesystem                            
        retval = fd.file.write(buf, 0, bytesRead);                        
        
        if (retval < 0) {                                                 
            return -1;                                                    
        }                                                                 
        else {                                                            
            // classOpenFileWithPostion will maintain a position                
            // fd.position = fd.position + retval;                        
            return retval;                                               
            }                                                              
    }

    private int handleClose(int a0) {                                    
	    
        int handle = a0;                                                  
        if (a0 < 0 || a0 >= 16)                                        
            return -1;                                                    

        boolean retval = true;                                           

        FileDescriptor fd = fds[handle];                                  

        //fd.position = 0;                                                 
        fd.file.close();                                                  
        fd.file = null;                                                   

        // remove this file if necessary                                  
        if (fd.toRemove) {                                               
        retval = UserKernel.fileSystem.remove(fd.filename);           
            fd.toRemove = false;                                            
        }                                                                 

        fd.filename = "";                                                 

        return retval ? 0 : -1;                                           
    }     
    
    private int handleUnlink(int a0) {
	  

        boolean retval = true;

        // a0 is address of filename 
        String filename = readVirtualMemoryString(a0, 256);        

	             

        int fileHandle = findFileDescriptorByName(filename);             
        if (fileHandle < 0) {                                            
         //if its not being used remove it from filesystem
            retval = UserKernel.fileSystem.remove(filename);             
        }                                                                  
        else {// else close the file first then remove it                                                           
            fds[fileHandle].toRemove = true;                             
            handleClose(fileHandle);                                     
            retval = UserKernel.fileSystem.remove(filename);             
        }                                                                 

        return retval ? 0 : -1;                                           
    }

    
    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     *
     */
    
    public void handleException(int cause) {
    	Processor processor = Machine.processor();

    	switch (cause) {
    	case Processor.exceptionSyscall:
    		int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
    		processor.writeRegister(Processor.regV0, result);
    		processor.advancePC();
    		break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    handleExit(-1);
	    Lib.assertNotReached("Unexpected exception");
		}
    }
    
    private int findFileDescriptorByName(String filename) { 
        
        for (int i = 0; i < 16; i++) {                
            if (fds[i].filename.equals(filename))           
                return i;                                   
        }                                                 

        return -1;                                         
    }  

public class FileDescriptor {                                 
    public FileDescriptor() {                                
    }                                                         
    private  String   filename = "";   // opened file name    
    private  OpenFile file = null;     // opened file object  
    //private  int      position = 0;  // IO position           

    private  boolean  toRemove = false;// if need to remove   
                                       // this file           
}
private FileDescriptor fds[] = new FileDescriptor[16];
    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    public static final int MAXSTRLEN = 256; 
    
    //pid of root prcocess 
    public static final int ROOT = 1;   
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

	// process id
	private int pid;
	
	//private process's ID
	private int ppid;

	//child process ID
	private int childProcessID;
	// parent's process id
	private int parentID;

	// child processes

	private LinkedList<Integer> children 
						= new LinkedList<Integer>();

	private LinkedList<Integer> children = new LinkedList<Integer>();


	// exit status
	private int exitStatus;

	/* user thread that's associated with this process                  */
	private UThread thread;                                       
}
