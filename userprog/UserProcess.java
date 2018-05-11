// nachosquad

package nachos.userprog;

import java.io.EOFException;
import java.util.*;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;



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
		boolean status = Machine.interrupt().disable();
		fileDescriptorTable = new OpenFile[16];

		processID = processIdCounter;
		processIdCounter++;

		// do this to fill up the first fild descriptors
		if (parentProcess == null){
			stdin = UserKernel.console.openForReading();
			stdout = UserKernel.console.openForWriting();
		} else {
			stdin = parentProcess.stdin;
			stdout = parentProcess.stdout;
		}
		Machine.interrupt().restore(status);
		fileDescriptorTable[0] = stdin;
		fileDescriptorTable[1] = stdout;

		parentProcess = null;
		mapLock = new Lock();

		childProcesses = new LinkedList<UserProcess>();
		exitStatusList = new HashMap<Integer,Integer>();
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

		thread = new UThread(this);
		thread.setName(name).fork();

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
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
		if (vaddr < 0) {
			vaddr = 0;
		}
		if (length > Machine.processor().makeAddress(numPages-1, pageSize-1) - vaddr) {
			length = Machine.processor().makeAddress(numPages - 1, pageSize - 1) - vaddr;
		}

		byte[] memory = Machine.processor().getMemory();

		int firstVirtPage = Machine.processor().pageFromAddress(vaddr);
		int vadx = vaddr + length;
		int lastVirtPage = Machine.processor().pageFromAddress(vadx);
		int numBytesTransferred = 0;
		for (int i=firstVirtPage; i<=lastVirtPage; i++){
			if (!pageTable[i].valid) {
				break;
			}

			int firstVirtAddress = Machine.processor().makeAddress(i, 0);
			int lastVirtAddress = Machine.processor().makeAddress(i, pageSize-1);
			int offset1;
			int offset2;

			if (vaddr <= firstVirtAddress && vadx >= lastVirtAddress){
				offset1 = 0;
				offset2 = pageSize - 1;
			} else if (vaddr > firstVirtAddress && vadx >= lastVirtAddress){
				offset1 = vaddr - firstVirtAddress;
				offset2 = pageSize - 1;
			} else if (vaddr <= firstVirtAddress && vadx < lastVirtAddress){
				offset1 = 0;
				offset2 = (vadx) - firstVirtAddress;
			} else {
				offset1 = vaddr - firstVirtAddress;
				offset2 = (vadx) - firstVirtAddress;
			}
			int firstPhysAddress = Machine.processor().makeAddress(pageTable[i].ppn, offset1);

			System.arraycopy(memory, firstPhysAddress, data, offset+numBytesTransferred, offset2-offset1);
			numBytesTransferred += (offset2-offset1);
			pageTable[i].used = true;
		}
		return numBytesTransferred;
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
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0) {
			vaddr = 0;
		}

		if (length > Machine.processor().makeAddress(numPages-1, pageSize-1) - vaddr) {
			length = Machine.processor().makeAddress(numPages - 1, pageSize - 1) - vaddr;
		}

		int vadx = vaddr + length;
		int firstVirtPage = Machine.processor().pageFromAddress(vaddr);
		int lastVirtPage = Machine.processor().pageFromAddress(vadx);

		int numBytesTransferred = 0;
		for (int i=firstVirtPage; i <= lastVirtPage; i++){
			if (!pageTable[i].valid || pageTable[i].readOnly) {
				break;
			}

			int firstVirtAddress = Machine.processor().makeAddress(i, 0);
			int lastVirtAddress = Machine.processor().makeAddress(i, pageSize-1);
			int offset1;
			int offset2;

			if (vaddr <= firstVirtAddress && vadx >= lastVirtAddress){
				offset1 = 0;
				offset2 = pageSize - 1;
			}  else if (vaddr > firstVirtAddress && vadx >= lastVirtAddress){
				offset1 = vaddr - firstVirtAddress;
				offset2 = pageSize - 1;
			} else if (vaddr <= firstVirtAddress && vadx < lastVirtAddress){
				offset1 = 0;
				offset2 = (vadx) - firstVirtAddress;
			}  else {
				offset1 = vaddr - firstVirtAddress;
				offset2 = (vadx) - firstVirtAddress;
			}

			int firstPhysAddress = Machine.processor().makeAddress(pageTable[i].ppn, offset1);
			System.arraycopy(data, offset+numBytesTransferred, memory, firstPhysAddress, offset2-offset1);

			numBytesTransferred += (offset2 - offset1);
			pageTable[i].used = pageTable[i].dirty = true;
		}

		return numBytesTransferred;
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

		UserKernel.pageListLock.acquire();

		// allocate pages
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++){
			int nextFreePage = UserKernel.pageList.poll();
			pageTable[i] = new TranslationEntry(i,nextFreePage,true,false,false,false);
		}

		UserKernel.pageListLock.release();

		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i=0; i<section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;

				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.pageListLock.acquire();

		for (int i = 0; i < numPages; i++){
			UserKernel.pageList.add(pageTable[i].ppn);
		}

		UserKernel.pageListLock.release();

		for (int i=0; i < MAX_FD; i++) {
			if (fileDescriptorTable[i] != null){
				fileDescriptorTable[i].close();
			}
		}
		coff.close();
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
		if (this.processID != 0){
			return 0;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Part 1
	 */

	private int handleCreate(int vaddr){
		// Check name
		if (vaddr < 0){
			Lib.debug(dbgProcess, "Invalid virtual address");
			return -1;
		}

		String filename = readVirtualMemoryString(vaddr, MAX_FILE_LEN);

		if (filename == null){
			Lib.debug(dbgProcess, "Illegal Filename");
			return -1;
		}

		// check for free fileDescriptor
		int nextFDIndex = getNextFDIndex();

		if (nextFDIndex == -1){
			Lib.debug(dbgProcess, "No free fileDescriptor available");
			return -1;
		}

		OpenFile file = ThreadedKernel.fileSystem.open(filename, true);
		if (file == null){
			Lib.debug(dbgProcess, "Cannot create file");
			return -1;
		}

		fileDescriptorTable[nextFDIndex] = file;
		return nextFDIndex;
	}

	private int getNextFDIndex() {
		for(int i = 2; i < MAX_FD; i++){
			if(fileDescriptorTable[i] == null){
				return i;
			}
		}

		return -1;
	}

	private int handleOpen(int virtualAddress){
		if (virtualAddress < 0){
			Lib.debug(dbgProcess, "Bad virtual Address");
			return -1;
		}

		String filename = readVirtualMemoryString(virtualAddress, 256);

		if (filename == null){
			Lib.debug(dbgProcess, "Bad Filename");
			return -1;
		}

		int nextFDIndex = getNextFDIndex();

		if (nextFDIndex == -1) {
			Lib.debug(dbgProcess, "No free fileDescriptor available");
			return -1;
		}

		OpenFile file = ThreadedKernel.fileSystem.open(filename, false);
		if (file == null){
			Lib.debug(dbgProcess, "Cannot create file");
			return -1;
		}

		fileDescriptorTable[nextFDIndex] = file;
		return nextFDIndex;
	}

	protected int handleRead(int fileDescriptor, int bufferAddr, int count){
		int bytesRead = 0;
		int returnAmount = 0;

		if(fileDescriptor < 0 || fileDescriptor > 15) {
			Lib.debug(dbgProcess, "Invalid FD");
			return -1;
		}
		OpenFile file = fileDescriptorTable[fileDescriptor];

		if(file == null) {
			Lib.debug(dbgProcess, "No file!");
			return -1;
		}

		if(count < 0) {
			Lib.debug(dbgProcess, "No positive count");
			return -1;
		}

		byte[] buffer = new byte[count];
		bytesRead = file.read(buffer, 0, count);

		if(bytesRead == -1) {
			Lib.debug(dbgProcess, "Could not read files");
			return -1;
		}

		returnAmount = writeVirtualMemory(bufferAddr, buffer, 0, bytesRead);

		return returnAmount;
	}

	private int handleWrite(int fileDescriptor, int bufferAddress, int count){
		int bytesWritten = 0;
		int returnAmount = 0;

		if(fileDescriptor < 0 || fileDescriptor > 15) {
			Lib.debug(dbgProcess, "Invalid FD");
			return -1;
		}

		OpenFile file = fileDescriptorTable[fileDescriptor];
		if(file == null) {
			Lib.debug(dbgProcess, "No file!");
			return -1;
		}

		if(count < 0) {
			Lib.debug(dbgProcess, "No positive count");
			return -1;
		}

		byte[] buffer = new byte[count];
		bytesWritten = readVirtualMemory(bufferAddress, buffer, 0, count);

		returnAmount = file.write(buffer, 0, bytesWritten); 

		if (returnAmount != count){
			return -1;
		}

		return returnAmount;
	}

	protected int handleClose(int fileDescriptor){
		if ((fileDescriptor < 0) ||
				(fileDescriptor > 15) ||
				fileDescriptorTable[fileDescriptor] == null ) {
			return -1;
		}

		fileDescriptorTable[fileDescriptor].close();
		fileDescriptorTable[fileDescriptor] = null;
		return 0;
	}

	public int handleUnlink(String name){
		boolean succeeded = ThreadedKernel.fileSystem.remove(name);
		if (!succeeded) {
			return -1;
		}

		return 0;
	}
	
	private int handleExit(int status){
		if (parentProcess != null) {
			parentProcess.mapLock.acquire();
			parentProcess.exitStatusList.put(processID, status);
			parentProcess.mapLock.release();
		}

		this.unloadSections();
		ListIterator<UserProcess> childIterator = childProcesses.listIterator();

		while(childIterator.hasNext()) {
			childIterator.next().parentProcess = null;
		}

		childProcesses.clear();

		if (this.processID == 0) {
			Kernel.kernel.terminate();
		} else {
			KThread.finish();
		}

		return status;
	}


	private int handleExec(int file, int argc, int argv){
		if (file < 0){
			Lib.debug(dbgProcess, "Invalid name vaddr");
			return -1;
		}

		// Check string filename
		String fileName = readVirtualMemoryString(file, 256);
		if (fileName == null){
			Lib.debug(dbgProcess, "Invalid file name");
			return -1;
		}

		String[] fileNameContent = fileName.split("\\.");
		String fileExtension = fileNameContent[fileNameContent.length - 1];
		if (!fileExtension.toLowerCase().equals("coff")){
			Lib.debug(dbgProcess, "File name must end with 'coff'");
			return -1;
		}

		// Check arguments
		if (argc < 0){
			Lib.debug(dbgProcess, "Cannot take negative arguments");
			return -1;
		}

		// read arguments
		String[] arguments = new String[argc];
		for(int i = 0; i < argc; i++ ) {
			byte[] pointer = new byte[4];
			int byteRead = readVirtualMemory(argv + (i * 4), pointer);

			if (byteRead != 4) {
				Lib.debug(dbgProcess, "Bad pointers!");
				return -1;
			}

			int argVaddr = Lib.bytesToInt(pointer, 0);
			String argument = readVirtualMemoryString(argVaddr, 256);

			if (argument == null) {
				Lib.debug(dbgProcess, "Some args failed!");
				return -1;
			}

			arguments[i] = argument;
		}

		UserProcess child = UserProcess.newUserProcess();

		if (child.execute(fileName, arguments)){
			this.childProcesses.add(child);
			child.parentProcess = this;
			return child.processID;
		}

		// failed
		Lib.debug(dbgProcess, DEFAULT_ERROR_TEXT + "Cannot exeute");
		return -1;
	}

	// todo
	private int handleJoin(int processID, int statusAddress){
		UserProcess child = null;

		int children = this.childProcesses.size();
	
		for(int i = 0; i < children; i++) {
			if(this.childProcesses.get(i).processID == processID) {
				child = this.childProcesses.get(i);
				break;
			}
		}

		if (child == null) {
			return -1;
		}

		child.thread.join();

		// remove child process
		this.childProcesses.remove(child);
		child.parentProcess = null;

		mapLock.acquire();
		Integer status = exitStatusList.get(child.processID);
		mapLock.release();

		if(status == EXIT_STATUS_ERR){
			return 0;
		}

		if(status != null) {
			byte[] buffer = new byte[4];
			Lib.bytesFromInt(buffer, 0, status);
			int bytesWritten = writeVirtualMemory(statusAddress, buffer);

			if (bytesWritten == 4){
				return 1; //child exited normally
			}

			return 0;
		}

		return 0;
	}

	public static final int
			syscallHalt   = 0,
			syscallExit   = 1,
			syscallExec   = 2,
			syscallJoin   = 3,
			syscallCreate = 4,
			syscallOpen   = 5,
			syscallRead   = 6,
			syscallWrite  = 7,
			syscallClose  = 8,
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
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				return handleExit(a0);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);
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
			case syscallUnlink: {
				if (a0 < 0) {
					return -1;
				}

				String name = readVirtualMemoryString(a0, 256);
				if (name == null) {
					return -1;
				}

				return handleUnlink(name);
			}
			default: {
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
			}
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by
	 * <tt>UserKernel.exceptionHandler()</tt>. The
	 * <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param	cause	the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionSyscall: {
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3)
				);
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;
			}
			default: {
				Lib.debug(dbgProcess, "Unexpected exception: " +
						Processor.exceptionNames[cause]);
				handleExit(EXIT_STATUS_ERR); // call error
				Lib.assertNotReached("Unexpected exception");
			}
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';

	// constants
	public static final int MAX_FD = 16;
	private static final int EXIT_STATUS_ERR = -1;
	private static final int MAX_FILE_LEN = 256;
	private static final String DEFAULT_ERROR_TEXT = "[ERROR]";


	// new variables for project 2
	public OpenFile[] fileDescriptorTable;
	private LinkedList<UserProcess> childProcesses;
	private UserProcess parentProcess;
	private static int processIdCounter = 0;
	private int processID;
	private UThread thread;

	//
	private HashMap<Integer, Integer> exitStatusList;
	protected OpenFile stdin;
	protected OpenFile stdout;
	private Lock mapLock;
}
