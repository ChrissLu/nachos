package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		pageTable = new TranslationEntry[numPages];
		pageLock.acquire();
		for(int i = 0; i < numPages; i++){
			int ppn = VMKernel.freePhysicalPages.remove();
			pageTable[i] = new TranslationEntry(i, ppn, false, false, false,false);
		}
		pageLock.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		pageLock.acquire();
		for(int i=0; i < numPages; ++i){
			if(pageTable[i].valid){
				// it is in the main memory
				VMKernel.freePhysicalPages.add(pageTable[i].ppn);
			} else{
				// maybe in the swap file
				Lib.assertTrue(pageTable[i].ppn>=0);
				VMKernel.freePhysicalPages.add(pageTable[i].ppn);
			}
		}
		pageLock.release();
	}

	private void handlePageFault(int vpn){
		if(vpn >= numPages-stackPages-1){
			// the stack or argument page
			// 1. zero fill at the first page fault
			// 2. load from swap file if modified

			byte[] memory = Machine.processor().getMemory();
			int startIndex = pageTable[vpn].ppn * pageSize;
			// zero fill
			for(int i=0;i<pageSize;++i){
				memory[startIndex + i] = 0;
			}

			pageTable[vpn].readOnly = false;
			pageTable[vpn].valid = true;
		} else{
			// the code and data page
			// 1. if readOnly or the first page fault, then load from COFF file
			// 2. otherwise, load from swap file
			int numCoffPages = 0;
			CoffSection section = null;
			for (int s = 0; s < coff.getNumSections(); s++) {
				section = coff.getSection(s);
				numCoffPages += section.getLength();
				if(numCoffPages > vpn)
					break;
			}

			section.loadPage(vpn-section.getFirstVPN(), pageTable[vpn].ppn);

			pageTable[vpn].readOnly = section.isReadOnly();
			pageTable[vpn].valid = true;
		}
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
		case Processor.exceptionPageFault:
			int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
			int vpn = vaddr / pageSize;
			if(vpn >= numPages)
				super.handleException(cause);
			else
				handlePageFault(vpn);
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

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
			if(pageTable[vpn].valid == false){
				handlePageFault(vpn);
			}
			int nextVirtualAddress = (vpn + 1) * pageSize;
			int ppn = pageTable[vpn].ppn;
			int amount = Math.min(length, nextVirtualAddress - vaddr);
			int paddr = (ppn << pageByteLength) + addrOffset;
			if (paddr < 0 || paddr >= memory.length)
				return 0;
			System.arraycopy(memory, paddr, data, offset, amount);
			
			pageTable[vpn].used = true;

			vpn++;
			vaddr = vpn * pageSize;
			offset += amount;
			length -= amount;
			addrOffset = 0;
			totalAmount += amount;
		}
		return totalAmount;
	}

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
			if(pageTable[vpn].valid == false){
				handlePageFault(vpn);
			}
			int nextVirtualAddress = (vpn + 1) * pageSize;
			int ppn = pageTable[vpn].ppn;
			int amount = Math.min(length, nextVirtualAddress - vaddr);
			int paddr = (ppn << pageByteLength) + addrOffset;
			if (paddr < 0 || paddr >= memory.length)
				return 0;
			System.arraycopy(data, offset, memory, paddr, amount);

			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;

			vpn++;
			vaddr = vpn * pageSize;
			offset += amount;
			length -= amount;
			addrOffset = 0;
			totalAmount += amount;
		}
		return totalAmount;
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
