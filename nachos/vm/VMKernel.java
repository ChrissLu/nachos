package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
		invertedPageTable = new VMProcess.PageId[Machine.processor().getNumPhysPages()];
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapFile = ThreadedKernel.fileSystem.open("swapFile.sys", true);
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swapFile.close();
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	public static VMProcess.PageId invertedPageTable[];

	public static void printInvertedPageTable(){
		System.out.print("PMem: [");
		for(int i=0;i<invertedPageTable.length;++i){
			if(invertedPageTable[i] == null){
				System.out.print("null, ");
			} else{
				System.out.print(invertedPageTable[i]+", ");
			}
		}
		System.out.println("]");
		System.out.println();
	}

	public static OpenFile swapFile;
}
