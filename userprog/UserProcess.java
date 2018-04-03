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
	for (int i=0; i<numPhysPages; i++) {
	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
	}
	
	for(int j = 0; j < fds.length; j++) {
		fds[j] = new FileDescriptor(); 
	}
	
	
	if(parentProcess == null) {
		stdin = UserKernel.console.openForReading(); 
		stdout = UserKernel.console.openForWriting(); 
	} else {
		stdin = parentProcess.stdin; 
		stdout = parentProcess.stdout; 
	}
	parentProcess = null; 
	
	childProcesses = new LinkedList<UserProcess>(); 
	
	fileDescriptorTable = new OpenFile[16]; 
	fileDescriptorTable[0] = stdin; 
	fileDescriptorTable[1] = stdout; 
	
	
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

		//make sure that virtual address is valid for this process' virtual address space
		if (vaddr < 0)
			vaddr = 0;
		Machine.processor();
		if (length > Processor.makeAddress(numPages-1, pageSize-1) - vaddr) {
			Machine.processor();
			length = Processor.makeAddress(numPages-1, pageSize-1) - vaddr;
		}

		byte[] memory = Machine.processor().getMemory();

		Machine.processor();
		int firstvirtualpage = Processor.pageFromAddress(vaddr);
		Machine.processor();
		int endvirtualpage = Processor.pageFromAddress(vaddr+length);
		int transferredBytes = 0;
		for (int i=firstvirtualpage; i<=endvirtualpage; i++){
			if (!pageTable[i].valid)
				break; //stop reading, return numBytesTransferred for whatever we've written so far
			Machine.processor();
			int firstvirtualaddress = Processor.makeAddress(i, 0);
			Machine.processor();
			int endvirtualaddress = Processor.makeAddress(i, pageSize-1);
			int offset1;
			int offset2;
			//virtual page is in the middle, copy entire page (most common case)
			if (vaddr <= firstvirtualaddress && vaddr+length >= endvirtualaddress){
				offset1 = 0;
				offset2 = pageSize - 1;
			}
			//virtual page is first to be transferred
			else if (vaddr > firstvirtualaddress && vaddr+length >= endvirtualaddress){
				offset1 = vaddr - firstvirtualaddress;
				offset2 = pageSize - 1;
			}
			//virtual page is last to be transferred
			else if (vaddr <= firstvirtualaddress && vaddr+length < endvirtualaddress){
				offset1 = 0;
				offset2 = (vaddr + length) - firstvirtualaddress;
			}
			//only need inner chunk of a virtual page (special case)
			else { //(vaddr > firstVirtAddress && vaddr+length < lastVirtAddress)
				offset1 = vaddr - firstvirtualaddress;
				offset2 = (vaddr + length) - firstvirtualaddress;
			}
			Machine.processor();
			int firstphysicaladdress = Processor.makeAddress(pageTable[i].ppn, offset1);
			//int lastPhysAddress = Machine.processor().makeAddress(pageTable[i].ppn, offset2);
			System.arraycopy(memory, firstphysicaladdress, data, offset+transferredBytes, offset2-offset1);
			transferredBytes += (offset2-offset1);
			pageTable[i].used = true;
		}		
		return transferredBytes;
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

		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);               
		byte[] memory = Machine.processor().getMemory();
		
		// check that virtual address is valid for this process 
		if(vaddr < 0) {
			vaddr = 0; 
		}
		if(length > Machine.processor().makeAddress(numPages-1, pageSize-1)-vaddr) {
			length = Machine.processor().makeAddress(numPages-1, pageSize-1) - vaddr; 
		}
		
		int firstvirtualpage = Machine.processor().pageFromAddress(vaddr); 
		int endvirtualpage = Machine.processor().pageFromAddress(vaddr+length); 
		int transferredBytes = 0; 
		
		for(int i = firstvirtualpage; i <= endvirtualpage; i++) {
			if(!pageTable[i].valid || pageTable[i].readOnly) {
				break; //stop writing to memory . return current amount of transferred bytes
			}
			Machine.processor();
			int firstvirtualaddress = Processor.makeAddress(i, 0); 
			Machine.processor();
			int lastvirtualaddress = Processor.makeAddress(i, pageSize-1); 
			int offset1; 
			int offset2; 
			
			//neither the first virtual page or the last virtual page 
			if(vaddr <= firstvirtualaddress && vaddr+length >= lastvirtualaddress) {
				offset1 = 0; 
				offset2 = pageSize - 1; 
			} 
			// the first virtual page 
			else if (vaddr > firstvirtualaddress && vaddr+length >= lastvirtualaddress) {
				offset1 = vaddr - firstvirtualaddress; 
				offset2 = pageSize - 1; 
			}
			// the last virtual page 
			else if(vaddr <= firstvirtualaddress && vaddr+length < lastvirtualaddress) {
				offset1 = 0; 
				offset2 = (vaddr + length) - firstvirtualaddress; 
			}
			//(special case) part of a virtual page 
			else {
				offset1 = vaddr - firstvirtualaddress; 
				offset2 = (vaddr + length) - firstvirtualaddress; 
			}
			Machine.processor();
			int firstphysicaladdress = Processor.makeAddress(pageTable[i].ppn, offset1); 
			System.arraycopy(data, offset+transferredBytes, memory, firstphysicaladdress, offset2-offset1);
			transferredBytes += (offset2-offset1); 
			pageTable[i].used = true; 
			pageTable[i].dirty = true; 
		}
		return transferredBytes; 
    	
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

        // load sections
        for (int s=0; s<coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                    + " section (" + section.getLength() + " pages)");

            for (int i=0; i<section.getLength(); i++) {
                int vpn = section.getFirstVPN()+i;

                // translate virtual page number from physical page number
                TranslationEntry entry = pageTable[vpn];                                   
                entry.readOnly = section.isReadOnly();                                     
                int ppn = entry.ppn;                                                       

                section.loadPage(i, ppn);                                                  
            }
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        for (int i = 0; i < numPages; i++) {                                       
            UserKernel.addFreePage(pageTable[i].ppn);                              
            pageTable[i].valid = false;                                            
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
    if (this.childProcessID != 1){
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
	

	private int handleExec(int filevaddr, int argc, int argv) {
		
		if(filevaddr < 0) {
			Lib.debug(dbgProcess, "File virtual address error");
			return -1; 
		}
		
		String filename = readVirtualMemoryString(filevaddr,256); 
		if(filename == null) {
			Lib.debug(dbgProcess, "Filename error");
			return -1; 
		}
		String[] filenameArray = filename.split("\\."); 
		String last = filenameArray[filenameArray.length -1]; 
		
		if(!last.toLowerCase().equals("coff")) {
			Lib.debug(dbgProcess, "File name must end with 'coff' ");
			return -1; 
		}
		
		if(argc < 0) {
			Lib.debug(dbgProcess, "Enter a positve number of arguments");
			return -1; 
		}
		
		String[] arguments = new String[argc]; 
		for(int i = 0; i < argc; i++) {
			byte[] pointer = new byte[4]; 
			int byteRead = readVirtualMemory(argv + (i*4),pointer); 
			
			//check the pointer 
			if(byteRead != 4) {
				Lib.debug(dbgProcess, "Pointer error" );
				return -1; 
			}
			int argvaddr = Lib.bytesToInt(pointer, 0); 
			String argument = readVirtualMemoryString(argvaddr,256); 
			
			if(argument == null) {
				Lib.debug(dbgProcess, "Argument not read");
				return -1; 
			}
			
			arguments[i] = argument; 
		}
		
		UserProcess child = UserProcess.newUserProcess(); 
		if(child.execute(filename, arguments)) {
			this.childProcesses.add(child); 
			child.parentProcess = this; 
			return child.childProcessID;		
		}else {
			Lib.debug(dbgProcess, "Failed to execute");
			return -1; 
		}	
	}
	
private int handleJoin(int childProcessId, int status) {
		Lib.debug(dbgProcess, "handleJoin()");
		String Type = "[UserProcess][handleJoin] ";

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
		}

		return 0;
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

		return exitStatus;

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
    	        // Check name
    			if (a0 < 0){
    				Lib.debug(dbgProcess, "Invalid virtual address");
    				return -1;
    			}
    			String filename = readVirtualMemoryString(a0, 256);
    			if (filename == null){
    				Lib.debug(dbgProcess, "Illegal Filename");
    				return -1;
    			}

    			// check for free fileDescriptor
    			int emptyIndex = -1;
    			for(int i=2; i<16; i++){
    				if(fileDescriptorTable[i] == null){
    					emptyIndex = i;
    					break;
    				}
    			}
    			if (emptyIndex == -1){
    				Lib.debug(dbgProcess, "No free fileDescriptor available");
    				return -1;
    			}

    			OpenFile file = ThreadedKernel.fileSystem.open(filename, false);
    			if (file == null){
    				Lib.debug(dbgProcess, "Cannot create file");
    				return -1;
    			}else{
    				fileDescriptorTable[emptyIndex] = file;
    				return emptyIndex;
    			}
	}
    
    private int handleCreate(int a0) {
    			// Check name
    			if (a0 < 0){
    				Lib.debug(dbgProcess, "Invalid virtual address");
    				return -1;
    			}
    			String filename = readVirtualMemoryString(a0, 256);
    			if (filename == null){
    				Lib.debug(dbgProcess, "Illegal Filename");
    				return -1;
    			}

    			// check for free fileDescriptor
    			int emptyIndex = -1;
    			for(int i=2; i<16; i++){
    				if(fileDescriptorTable[i] == null){
    					emptyIndex = i;
    					break;
    				}
    			}
    			if (emptyIndex == -1){
    				Lib.debug(dbgProcess, "No free fileDescriptor available");
    				return -1;
    			}

    			OpenFile file = ThreadedKernel.fileSystem.open(filename, true);
    			if (file == null){
    				Lib.debug(dbgProcess, "Cannot create file");
    				return -1;
    			}else{
    				fileDescriptorTable[emptyIndex] = file;
    				return emptyIndex;
    			}
	}
    
    
    private int handleRead(int a0, int a1, int a2) {                      
	   int byteCount = 0;
	   int returnValue = 0; 
	   
	   //exception 
	   if(a0 < 0 || a0 > 15) {return -1;} 
	   
	   OpenFile file = fileDescriptorTable[a0]; 
	   
	   if(file == null) {return -1;} 
	   
	   if(a2 < 0) {return -1;} 
	   
	   byte[] buffer = new byte[a2]; 
	   
	   byteCount = file.read(buffer, 0, a2); 
	   
	   if(byteCount == -1) {return -1;} 
	   
	   returnValue = writeVirtualMemory(a1,buffer,0,byteCount); 
	   
	   return returnValue; 
	                                                      
    }    
    private int handleWrite(int a0, int a1, int a2) {
	
        int fhandle = a0;                 
        int bufadd = a1;                            
        int bufsize = a2;                           
        int bytesCount = 0; 
        int returnValue = 0; 

	 
        // checks if its an invalid file
        if (fhandle < 0 || fhandle > 15 ) {                                                
            return -1;                                                    
        }     
        
        //obtain the file 
        OpenFile file = fileDescriptorTable[fhandle]; 
        
        //file descriptor returns null file 
        if(file == null) { return -1; } 
        
        
        if(bufsize < 0) { return -1; } 
        
        
        //read from this buffer 
        byte[] buffer = new byte[bufsize]; 
        
        //virtual address space -> buffer 
        bytesCount = readVirtualMemory(bufadd,buffer,0,bufsize); 
        
        //buffer -> file 
        returnValue = file.write(buffer, 0, bytesCount); 
        
        if(returnValue != bufsize) {
        		return -1; 
        }
        
        return returnValue; 
                                                                                                            
    }

    private int handleClose(int a0) {                                    
	    
        int handle = a0;                                                  
        if ( (a0 < 0)  || (a0 > 15) || (fileDescriptorTable[a0] == null) )                                        
            return -1;                                                    

        fileDescriptorTable[a0].close();
        fileDescriptorTable[a0] = null; 
        return 0; 
                                 
                                                   
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
		private  String   filename = "";   //  file name
		private  OpenFile file = null;     //file object
		private int position = 0; //  position
		private  boolean  toRemove = false;
		


	}

	public int getNextEmptyFileDescriptor() {
		for (int i = 0; i < 16; i++) {
			if (fds[i].file == null)
				return i;
		}

		return -1;
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

	// exit status
	private int exitStatus;

	/* user thread that's associated with this process                  */
	private UThread thread;   
	
	private OpenFile[] fileDescriptorTable;
	private LinkedList<UserProcess> childProcesses;
	private UserProcess parentProcess;
	protected OpenFile stdin;
	protected OpenFile stdout;
	
}
