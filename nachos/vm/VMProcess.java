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
		for(int i = 0; i < numPages; i++){
			// do not allocate physical memory when init, just use -1 as ppn
			pageTable[i] = new TranslationEntry(i, -1, false, false, false,false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		mutex.acquire();
		for(int i=0; i < numPages; ++i){
			if(pageTable[i].valid){
				// it is in the main memory
				pageLock.acquire();
				VMKernel.freePhysicalPages.add(pageTable[i].ppn);
				pageLock.release();

				replacer.remove(new PageId(this, pageTable[i]));
			} else{
				// maybe in the swap file
				swapFile.clear(new PageId(this, pageTable[i]));
			}
		}
		mutex.release();
		coff.close();
	}

	private void handlePageFault(int vpn, boolean pin){
		PageId pageId = new PageId(this, pageTable[vpn]);
		byte[] memory = Machine.processor().getMemory();

		int ppn = -1;

		pageLock.acquire();
		if(UserKernel.freePhysicalPages.size() == 0){
			// all the main memory pages are occupied, we need to evict one page
			pageLock.release();
			PageId evictPageId = replacer.evict();

			evictPageId.process.mutex.acquire();
			if(evictPageId.pte.dirty){
				swapFile.write(evictPageId);
			}
			evictPageId.pte.valid = false;
			ppn = evictPageId.pte.ppn;
			evictPageId.process.mutex.release();
		}else {
			ppn = UserKernel.freePhysicalPages.remove();
			pageLock.release();
		}

		int paddr = ppn * pageSize;

		mutex.acquire();
		pageTable[vpn].valid = true;
		pageTable[vpn].ppn = ppn;
		pageTable[vpn].dirty = false;
		pageTable[vpn].used = true;

		if(vpn >= numPages-stackPages-1){
			// the stack or argument page
			if(!swapFile.isExist(pageId)){
				// zero fill at first page fault
				for(int i=0;i<pageSize;++i){
					memory[paddr + i] = 0;
				}
				pageTable[vpn].readOnly = false;
			} else {
				swapFile.read(pageId, memory, paddr);
			}
		} else{
			// code and data segment page
			if(!swapFile.isExist(pageId)){
				// load from COFF file
				int numCoffPages = 0;
				CoffSection section = null;
				for (int s = 0; s < coff.getNumSections(); s++) {
					section = coff.getSection(s);
					numCoffPages += section.getLength();
					if(numCoffPages > vpn)
						break;
				}
				section.loadPage(vpn-section.getFirstVPN(), ppn);
				pageTable[vpn].readOnly = section.isReadOnly();
			} else{
				swapFile.read(pageId, memory, paddr);
			}
		}
		mutex.release();

		if(pin)
			replacer.add(pageId);
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
					handlePageFault(vpn, false);
				break;
			default:
				super.handleException(cause);
				break;
		}
	}

	private void pin(int vpn){
		mutex.acquire();
		if(pageTable[vpn].valid == false){
			mutex.release();
			handlePageFault(vpn, true);
		} else{
			replacer.remove(new PageId(this, pageTable[vpn]));
			mutex.release();
		}
	}

	private void unpin(int vpn){
		replacer.add(new PageId(this, pageTable[vpn]));
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
			pin(vpn);
			int nextVirtualAddress = (vpn + 1) * pageSize;
			int ppn = pageTable[vpn].ppn;
			int amount = Math.min(length, nextVirtualAddress - vaddr);
			int paddr = (ppn << pageByteLength) + addrOffset;
			if (paddr < 0 || paddr >= memory.length)
				return 0;
			System.arraycopy(memory, paddr, data, offset, amount);

			pageTable[vpn].used = true;
			unpin(vpn);

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
			pin(vpn);
			int nextVirtualAddress = (vpn + 1) * pageSize;
			int ppn = pageTable[vpn].ppn;
			int amount = Math.min(length, nextVirtualAddress - vaddr);
			int paddr = (ppn << pageByteLength) + addrOffset;
			if (paddr < 0 || paddr >= memory.length)
				return 0;
			System.arraycopy(data, offset, memory, paddr, amount);
			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;
			unpin(vpn);

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

	private static class PageId{
		PageId(VMProcess p, TranslationEntry e){
			process = p;
			pte = e;
		}

		public VMProcess process;
		public TranslationEntry pte;
	}

	private static class Replacer {
		// the Replacer would be used in many processes, so we need to protect it by lock

		Replacer(){
			mutex = new Lock();
		}

		// add one page to replacer. e.g. When a page is loaded to main memory or call unpin(), it should be added to replcaer
		public void add(PageId id){

		}

		// remove one page from replacer. e.g. When function pin() called, the page should be removed from replacer, as it would not be evicted later
		public void remove(PageId id){

		}

		// evict one page, return the pageid
		public PageId evict(){
			return null;
		}

		private Lock mutex;

	}

	private static class SwapFileManager{
		// protected by lock.
		// Notice: when writing to swapfile, there is no need to use lock because OS would support thread-safe write to file. Lock is used to protect the data structures in this class

		SwapFileManager(){
			swapFile = ThreadedKernel.fileSystem.open("swapFile.sys", true);
			mutex = new Lock();
		}

		// write one page to swap file. Write should always success, otherwise the kernel should halt
		public void write(PageId id){
			// There are 2 situations
			// 1. the page is first written to swapfile, we need to find a new place to write this page
			// 2. the page is written before, then we need to write the page to its original position

		}

		// return whether the page is existed in swapFile
		public boolean isExist(PageId id) {
			return false;
		}

		// read the page to buffer[offset] and return true. If the page is not existed, return false
		public boolean read(PageId id, byte[] buffer, int offset){
			return false;
		}

		public void clear(PageId id){

		}


		// physical swapfile
		private OpenFile swapFile;

		private Lock mutex;
	}

	private static Replacer replacer = new Replacer();

	private static SwapFileManager swapFile = new SwapFileManager();
}
