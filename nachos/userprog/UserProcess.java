package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;
import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
//		int numPhysPages = Machine.processor().getNumPhysPages();
//		pageTable = new TranslationEntry[numPhysPages];
//		for (int i = 0; i < numPhysPages; i++)
//			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

		// set stdin and stdout
		openFileTable[0] = UserKernel.console.openForReading();
		openFileTable[1] = UserKernel.console.openForWriting();
		// for(int i=1;;i++){
		// 	if (pidList.contains(i)) continue;
		// 	pid = i; 
		// 	pidList.add(i);
		// 	System.out.print("pidlist ");
		// 	for(int j:pidList) System.out.print(j+ " ");
		// 	System.out.println();
		// 	break;
		// }
		lock.acquire();
		pidCounter++;
		pid = pidCounter;
		processCounter++;
		lock.release();
		childList = new ArrayList<UserProcess>();
		childstatusMap = new HashMap<Integer,Integer>();
		abnormal = false;
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
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
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int totalAmount = 0;
		int vpn = (vaddr & vpnMask) >> pageByteLength;
		if(vpn >= pageTable.length){
			return 0;
		}
		int addrOffset = vaddr & offsetMask;
		while(offset < data.length && vpn < pageTable.length && length > 0){
			int nextVirtualAddress = (vpn + 1) * pageSize;
			int ppn = pageTable[vpn].ppn;
			int amount = Math.min(length, nextVirtualAddress - vaddr);
			int paddr = (ppn << pageByteLength) + addrOffset;
			if (paddr < 0 || paddr >= memory.length)
				return 0;
			System.arraycopy(memory, paddr, data, offset, amount);
			vpn++;
			vaddr = vpn * pageSize;
			offset += amount;
			length -= amount;
			addrOffset = 0;
			totalAmount += amount;
		}
		return totalAmount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int totalAmount = 0;

		int vpn = (vaddr & vpnMask) >> pageByteLength;
		if(vpn >= pageTable.length){
			return 0;
		}
		int addrOffset = vaddr & offsetMask;
		while(offset < data.length && vpn < pageTable.length && length > 0){
			int nextVirtualAddress = (vpn + 1) * pageSize;
			int ppn = pageTable[vpn].ppn;
			int amount = Math.min(length, nextVirtualAddress - vaddr);
			int paddr = (ppn << pageByteLength) + addrOffset;
			if (paddr < 0 || paddr >= memory.length)
				return 0;
			System.arraycopy(data, offset, memory, paddr, amount);
			vpn++;
			vaddr = vpn * pageSize;
			offset += amount;
			length -= amount;
			addrOffset = 0;
			totalAmount += amount;
		}
		return totalAmount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
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
		for (int s = 0; s < coff.getNumSections(); s++) {
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
		for (int i = 0; i < args.length; i++) {
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
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		lock.acquire();
		if (numPages > UserKernel.freePhysicalPages.size()) {
			lock.release();
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		pageTable = new TranslationEntry[numPages];
		for(int i = 0; i < numPages; i++){
			pageTable[i] = new TranslationEntry(i, UserKernel.freePhysicalPages.remove(), true, false, false,false);
		}
		lock.release();
		int n = 0;
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");


			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				pageTable[vpn].readOnly = section.isReadOnly();

				// Get the ppn from pageTable
				section.loadPage(i, pageTable[vpn].ppn);
			}

		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		lock.acquire();
		if(pageTable!=null)
		for(int i = 0; i < pageTable.length; i++){
			UserKernel.freePhysicalPages.add(pageTable[i].ppn);
		}
		lock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
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
		if(KThread.currentThread().getName() == Machine.getShellProgramName()){        //is the root process
			Machine.halt();
		}else{
			System.out.println("halt not called by root");
			return -1;
		}
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");

		System.out.println("exiting pid " + pid);

		for (OpenFile f:openFileTable){
			if(f!=null) f.close();
		}
		for(UserProcess child:childList){
			child.parent = null;
		}
		if(parent!=null){
			lock.acquire();
			parent.childstatusMap.put(pid,status);
			lock.release();
			//parent.childList.remove(this); //this sentence could be deleted
		}
		
		//pidList.remove(Integer.valueOf(pid));

		unloadSections();  // free up memory 

		if(--processCounter == 0){         //the last process
			Kernel.kernel.terminate();
		}else{
			KThread.currentThread().finish();
		}		
		
		return 0;
	}



	
	/**
	 * Handle the exec() system call.
	 */
	private int handleExec(int strVaddr,int argc,int argv) {
		if(argc < 0)
			return -1;

		String fileName = readVirtualMemoryString(strVaddr, 256);
		if(fileName == null)
			return -1;
		
		UserProcess child = newUserProcess();
		int childPID = child.pid;
		child.parent = this;
		this.childList.add(child);

		String[] args = new String[argc];
		for(int i=0;i<argc;i++){
			byte[] adr = new byte[4];
			int nr = readVirtualMemory(argv+4*i,adr);
			if(nr == 0)
				return -1;
			args[i] = readVirtualMemoryString(Lib.bytesToInt(adr,0), 256);
		}

		if (!child.execute(fileName,args)) {
			System.out.println ("could not find '" + fileName + "', aborting.");
			--processCounter;
			return -1;
		}


		// System.out.print("children of pid "+ pid + " are ");
		// for(UserProcess c:childList) if(c!=null) System.out.print(" "+c.pid);
		// System.out.println();
		return childPID;
	}

	// private int bytesToInt(byte[] b) {
	// 	int value;	
	// 	value = (int) ((b[0]&0xFF) 
	// 				| ((b[1]<<8) & 0xFF00)
	// 				| ((b[2]<<16)& 0xFF0000) 
	// 				| ((b[3]<<24) & 0xFF000000));
	// 	return value;
	// }


	/**
	 * Handle the join() system call.
	 */
	private int handleJoin(int processID, int statusVaddr) {

		UserProcess child = this;
		for(UserProcess c:childList){
			if (c.pid == processID) {
				child = c;
				break;
			}
		}
		if(child == this)  return -1;

		child.thread.join();
		int re = 1;
		lock.acquire();
		if(!childstatusMap.containsKey(processID)) re = -1;
		else if(child.abnormal) re = 0;
		else if(statusVaddr != 0x0){     // not NULL pointer
			byte[] toWrite = Lib.bytesFromInt(childstatusMap.get(processID));
			int w = writeVirtualMemory(statusVaddr,toWrite);
			if(w==0) re = -1; //invalid status pointer
			childstatusMap.remove(processID);
		}
		else childstatusMap.remove(processID); // NULL pointer
		lock.release();
		return re;

	}




	private int getNextFreeFileDescripter(){
		for(int i=0;i<openFileTable.length;++i){
			if(openFileTable[i] == null)
				return i;
		}
		return -1;
	}

	private int handleCreate(int strVaddr){
		int fd = getNextFreeFileDescripter();
		if(fd == -1){
			// could not open more files
			return -1;
		}
		String fileName = readVirtualMemoryString(strVaddr, 256);
		if(fileName == null){
			// the length of filename exceeds 256 chars
			return -1;
		}
		//System.out.println("filename: " + fileName);
		OpenFile f = ThreadedKernel.fileSystem.open(fileName, true);
		if(f == null){
			// file could not be opened
			return -1;
		}
		openFileTable[fd] = f;
		return fd;
	}

	private int handleOpen(int strVaddr){
		int fd = getNextFreeFileDescripter();
		if(fd == -1){
			// could not open more files
			return -1;
		}
		String fileName = readVirtualMemoryString(strVaddr, 256);
		if(fileName == null){
			// the length of filename exceeds 256 chars
			return -1;
		}
		OpenFile f = ThreadedKernel.fileSystem.open(fileName, false);
		if(f == null){
			// file could not be opened
			return -1;
		}
		openFileTable[fd] = f;
		return fd;
	}

	private int handleRead(int fd, int bufVaddr, int count){
		if(count < 0){
			return -1;
		}
		if(fd < 0 || fd >= openFileTable.length){
			return -1;
		}
		if(openFileTable[fd] == null){
			// the file descriptor does not exist
			return -1;
		}

		OpenFile f = openFileTable[fd];
		byte[] buf = new byte[pageSize];
		int totRead = 0;
		int startPos = 0;
		while(count > 0){
			int readCnt = f.read(buf, 0, Math.min(buf.length, count));
			if(readCnt == -1){
				return -1;
			}
			if(readCnt == 0){
				break;
			}
			int nw = writeVirtualMemory(bufVaddr + totRead, buf, 0, readCnt);
			if(nw != readCnt){
				// memory overflow
				return -1;
			}
			totRead += readCnt;
			startPos += readCnt;
			count -= readCnt;
		}
		return totRead;
	}

	private int handleWrite(int fd, int bufVaddr, int count){
		if(count < 0){
			return -1;
		}
		if(fd < 0 || fd >= openFileTable.length){
			return -1;
		}
		if(openFileTable[fd] == null){
			// the file descriptor does not exist
			return -1;
		}

		OpenFile f = openFileTable[fd];
		byte[] buf = new byte[pageSize];
		int totWrite = 0;
		int startPos = 0;
		while(count > 0){
			int nr = readVirtualMemory(bufVaddr + totWrite, buf, 0, Math.min(buf.length, count));
			if(nr != Math.min(buf.length, count)){
				return -1;
			}
			int writeCnt = f.write(buf, 0, nr);

			if(writeCnt == -1){
				return -1;
			}
			totWrite += writeCnt;
			count -= writeCnt;
		}
		return totWrite;
	}

	private int handleClose(int fd){
		if(fd < 0 || fd >= openFileTable.length){
			return -1;
		}
		if(openFileTable[fd] == null){
			// the file descriptor does not exist
			return -1;
		}

		OpenFile f = openFileTable[fd];
		f.close();
		openFileTable[fd] = null;
		return 0;
	}

	private int handleUnlink(int strVaddr){
		String fileName = readVirtualMemoryString(strVaddr, 256);
		if(fileName == null){
			// the length of filename exceeds 256 chars
			return -1;
		}
		boolean ok = ThreadedKernel.fileSystem.remove(fileName);
		if(ok)
			return 0;
		else
			return -1;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
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
		case syscallUnlink:
			return handleUnlink(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			abnormal = true;
			System.out.println("unexpected exception, exit");
			handleExit(0);

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

	/** The thread that executes the user-level program. */
	protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private OpenFile[] openFileTable = new OpenFile[16];

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static Lock lock = new Lock();

	private static final int vpnMask = (~0 ^ 0x3FF);

	private static final int offsetMask = 0x3FF;

	private static final int pageByteLength = 10;

	protected int pid;

	private static ArrayList<Integer> pidList = new ArrayList<Integer>();

	protected UserProcess parent;

	protected ArrayList<UserProcess> childList; //all children in history

	protected Map<Integer,Integer> childstatusMap;

	protected boolean abnormal;

	private static int pidCounter = 0; //to simply assign pid (could be improved by pidlist or something)

	private static int processCounter = 0; //to indicate the last process
}
