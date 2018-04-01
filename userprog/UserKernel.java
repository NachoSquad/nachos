package nachos.userprog;

import java.util.LinkedList;
import java.util.List;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
	super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
	super.initialize(args);

	console = new SynchConsole(Machine.console());
	
	Machine.processor().setExceptionHandler(new Runnable() {
		public void run() { exceptionHandler(); }
	    });
	
	plock=new Lock();
	freePages = new LinkedList<Integer>();
	int j= Machine.processor().getNumPhysPages();
	for (int i=0;i<j;i++)
		freePages.add(i);
	
	
	
	
    }

    /**
     * Test the console device.
     */	
    public void selfTest() {
	super.selfTest();

	System.out.println("Testing the console device. Typed characters");
	System.out.println("will be echoed until q is typed.");

	char c;

	do {
	    c = (char) console.readByte(true);
	    console.writeByte(c);
	}
	while (c != 'q');

	System.out.println("");
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
	if (!(KThread.currentThread() instanceof UThread))
	    return null;
	
	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
	Lib.assertTrue(KThread.currentThread() instanceof UThread);

	UserProcess process = ((UThread) KThread.currentThread()).process;
	int cause = Machine.processor().readRegister(Processor.regCause);
	process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
	super.run();

	UserProcess process = UserProcess.newUserProcess();
	
	String shellProgram = Machine.getShellProgramName();	
	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

	KThread.currentThread().finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }
    
    //Allocate fixed pages to a process
    public  List<Integer> acquire(int pagesReq){
    		pLock.acquire();
    		boolean validReq =(pagesReq>0 && pagesReq<freePages.size());
    		
    		if (!validReq) {
				pLock.release();
				return null;
			}
    	
    			List <Integer> pAllocated = new ArrayList<Integer>();
    			for (int i=0;i<pagesReq;i++) {
    				pAllocated.add(freePages.remove());
    			}
    			pLock.release();
    			return pAllocated;
    			
    		
    			
    		
    		
    	
    }
    
    // Release Pages belonging to a process
    
    public void release(List<Integer> usedPages) {
    		pLock.acquire();
    		for(Integer page: usedPages) {
    			freePages.add(page);
    		}
    		pLock.release();
    }
    
    
    
    

	// return a number to free page
	public static int getFreePage() {                              
		int pageNumber = -1;                                       
		Machine.interrupt().disable();

		if (pageTable.isEmpty() == false) {
			pageNumber = pageTable.removeFirst();
		}

		Machine.interrupt().enable();                               
		return pageNumber;                                         
	}                                                              

	// add a free page to linkedlist
	public static void addFreePage(int pageNumber) {               
		Lib.assertTrue(pageNumber >= 0 && pageNumber < Machine.processor().getNumPhysPages());
		Machine.interrupt().disable();                               
		pageTable.add(pageNumber);                                   
		Machine.interrupt().enable();                                
	}                                                               


	// get next available pid
	public static int getNextPid() {                                
		int val;                                                
		Machine.interrupt().disable();
		val = nextPid + 1;                                         
		Machine.interrupt().enabled();                              
		return nextPid;                                            
	}                                                              

	// get process by id
	public static UserProcess getProcessByID(int pid) {
		return processMap.get(pid);
	}

	// register a process in a kernal
	public static UserProcess registerProcess(int pid, UserProcess process) {  
		UserProcess insertedProcess;                                
		Machine.interrupt().disable();                              
		insertedProcess = processMap.put(pid, process);             
		Machine.interrupt().enabled();                              
		return insertedProcess;                                     
	}                                                              

	// unregister a process
	public static UserProcess unregisterProcess(int pid) {         
		UserProcess deletedProcess;                                 
		Machine.interrupt().disable();                              

		deletedProcess = processMap.remove(pid);                    

		Machine.interrupt().enabled();                              

		return deletedProcess;                                      
	}

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;
    
    // Lock
    
    private Lock pLock;
    
    // Available Pages
    
    private LinkedList<Integer> freePages;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;


	// the next user id
	private static int nextPid = 0;                                

	// map of all processes
	private static HashMap<Integer, UserProcess> processMap = new HashMap<Integer, UserProcess>();     

}
