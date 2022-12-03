package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashSet;


/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		mutex = new Lock();
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
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

		}

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
		//System.out.println(KThread.currentThread() + " wait for lock " + KThread.currentThread());
		mutex.acquire();
		//System.out.println(KThread.currentThread() + " get lock " + KThread.currentThread());
		for(int i=0; i < numPages; ++i){
			if(pageTable[i].valid){
				// it is in the main memory
				replacer.add(pageTable[i].ppn);
				//pageLock.acquire();
				//VMKernel.freePhysicalPages.add(pageTable[i].ppn);
				//pageLock.release();
				pageTable[i].valid = false;
			} else{
				// maybe in the swap file
				swapFile.clear(new PageId(this, pageTable[i]));
			}

		}
		mutex.release();
		//System.out.println(KThread.currentThread() + " release lock " + KThread.currentThread());
		coff.close();
	}

	private void handlePageFault(int vpn, boolean pin){
		if(pin){
			mutex.acquire();
			if(pageTable[vpn].valid)
			{
				replacer.remove(pageTable[vpn].ppn);
				mutex.release();
				return;
			}
			mutex.release();
		}

		PageId pageId = new PageId(this, pageTable[vpn]);
		//System.out.println("Page fault: " + pageId);
		byte[] memory = Machine.processor().getMemory();

		int ppn = replacer.evict();
		//System.out.println("("+this.pid+", " + vpn+"):" + "get page " + ppn);

		// all the main memory pages are occupied, we need to evict one page
		//System.out.println(KThread.currentThread() + " wait for mutex " + evictPageId.process.thread);
		PageId evictPageId = VMKernel.invertedPageTable[ppn];

		if(evictPageId != null){
			evictPageId.process.mutex.acquire();
			//System.out.println(KThread.currentThread() + " get mutex " + evictPageId.process.thread);
			evictPageId.pte.valid = false;
			if(evictPageId.pte.dirty){
				swapFile.write(evictPageId);
			}
			evictPageId.process.mutex.release();
			//System.out.println("("+this.pid+", " + vpn+"):" + " evict from" + "["+evictPageId.process.pid+", " + evictPageId.pte.vpn +"]");
		}

			//System.out.println(KThread.currentThread() + " release mutex " + id.process.thread);

			//System.out.println("("+this.pid+", " + vpn+"):" + "plan to evict " + "["+evictPageId.process.pid+", " + evictPageId.pte.vpn +"]");

			//System.out.println(KThread.currentThread() + " wait for lock " + evictPageId.process.thread);
			// evictPageId.process.mutex.acquire();
			// //System.out.println(KThread.currentThread() + " get lock " + evictPageId.process.thread);
			// if(evictPageId.pte.dirty){
			// 	swapFile.write(evictPageId);
			// }
			// evictPageId.pte.valid = false;
			// ppn = evictPageId.pte.ppn;
			// evictPageId.process.mutex.release();
			//System.out.println(KThread.currentThread() + " release lock " + evictPageId.process.thread);

		//VMKernel.printInvertedPageTable();
		//VMKernel.printInvertedPageTable();

		int paddr = ppn * pageSize;

		//System.out.println(KThread.currentThread() + " wait for lock " + KThread.currentThread());
		mutex.acquire();
		//System.out.println(KThread.currentThread() + " get lock " + KThread.currentThread());
		pageTable[vpn].valid = true;
		pageTable[vpn].ppn = ppn;
		pageTable[vpn].dirty = false;
		pageTable[vpn].used = true;
		mutex.release();

		if(vpn >= numPages-stackPages-1){
			// the stack or argument page
			//System.out.println("("+this.pid+", " + vpn+"):" + "It is stack page");
			if(!swapFile.read(pageId, memory, paddr)){
				for(int i=0;i<pageSize;++i){
					memory[paddr + i] = 0;
				}
				pageTable[vpn].readOnly = false;
				//System.out.println("("+this.pid+", " + vpn+"):" + "Zero fill the stack page");
			} else {
				//System.out.println("("+this.pid+", " + vpn+"):" + "Read the stack page from swap file");
			}
		} else{
			// code and data segment page
			//System.out.println("("+this.pid+", " + vpn+"):" + "It is code page");
			if(!swapFile.read(pageId, memory, paddr)){
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
				//System.out.println("("+this.pid+", " + vpn+"):" + "read from COFF file");
			} else{
				//System.out.println("("+this.pid+", " + vpn+"):" + "Load code page from swap file");
			}
		}
		VMKernel.invertedPageTable[ppn] = pageId;
		if(!pin)
			replacer.add(ppn);


		//swapFile.printAll();


		//System.out.println(KThread.currentThread() + " release lock " + KThread.currentThread());
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
		handlePageFault(vpn, true);
	}

	private void unpin(int vpn){
		replacer.add(pageTable[vpn].ppn);
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0)
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

		////System.out.println("wVM " + vaddr + " " + length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0)
			return 0;

		int totalAmount = 0;

		int vpn = (vaddr & vpnMask) >> pageByteLength;
		if(vpn >= pageTable.length){
			return 0;
		}
		int addrOffset = vaddr & offsetMask;
		while(offset < data.length && vpn < pageTable.length && length > 0){
			////System.out.println("wVM vpn: " + vpn);
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

	public static class PageId{
		PageId(VMProcess p, TranslationEntry e){
			process = p;
			pte = e;
		}
		
		@Override
		public int hashCode() {
			return process.pid*256 + pte.vpn;
		}

		@Override
		public boolean equals(Object obj) {
			return this.process==((PageId)obj).process && this.pte==((PageId)obj).pte;
		}

		@Override
		public int hashCode() {
			return process.pid*256 + pte.vpn;
		}

		@Override
		public boolean equals(Object obj) {
			return this.process==((PageId)obj).process && this.pte==((PageId)obj).pte;
		}

		public String toString(){
			return "("+ process.pid + ", " + pte.vpn + ")";
		}

		public VMProcess process;
		public TranslationEntry pte;
	}

	private static class Replacer {
		// the Replacer would be used in many processes, so we need to protect it by lock

		Replacer(){
			mutex = new Lock();
			empty = new Condition(mutex);
			pages = new boolean[Machine.processor().getNumPhysPages()];
			for(int i=0;i<pages.length;++i){
				pages[i] = true;
			}
			numFree = pages.length;
			cursor = 0;
		}

		// add one page to replacer. e.g. When a page is loaded to main memory or call unpin(), it should be added to replcaer
		public void add(int ppn){
			////System.out.println(KThread.currentThread() + " wait for replacer lock");
			mutex.acquire();
			////System.out.println(KThread.currentThread() + " get replacer lock");
			if(pages[ppn] == false){
				//System.out.println(ppn+ " added");
				pages[ppn] = true;
				++numFree;
				empty.wakeAll();
			}
			
			//System.out.println("add " + "(" + id.process.pid + ", " + id.pte.vpn + ")" + " to repacer");
			//printAll();
			mutex.release();
			////System.out.println(KThread.currentThread() + " release replacer lock");
		}

		// remove one page from replacer. e.g. When function pin() called, the page should be removed from replacer, as it would not be evicted later
		public void remove(int ppn){
			////System.out.println(KThread.currentThread() + " wait for replacer lock");
			mutex.acquire();
			////System.out.println(KThread.currentThread() + " get replacer lock");
			if(pages[ppn] == true){
				pages[ppn] = false;
				--numFree;
			}

			//System.out.println("remove " + "(" + id.process.pid + ", " + id.pte.vpn + ")" + " from repacer");
			//printAll();
			mutex.release();
			////System.out.println(KThread.currentThread() + " release replacer lock");
		}

		// evict one page, return the pageid
		public int evict(){
			//System.out.println(KThread.currentThread() + " wait for replacer lock");
			mutex.acquire();
			//System.out.println(KThread.currentThread() + " get replacer lock");
			while(numFree == 0){
				empty.sleep();
			}
			int ret = cursor;
			for(int i=0;i<pages.length;++i){
				if(pages[cursor] == true){
					ret = cursor;
					pages[cursor] = false;
					--numFree;
					break;
				} else{
					cursor = (cursor + 1) % pages.length;
				}
			}
			cursor = (cursor + 1) % pages.length;
			//System.out.println(i+ " evicted");
			mutex.release();
			//System.out.println(KThread.currentThread() + " release replacer lock");
			return ret;
		}

		/*
		private void printAll(){
			mutex.acquire();
			Iterator<PageId> it = pages.iterator();
			System.out.print("Replacer: [");
			while (it.hasNext()) {
				PageId id = it.next();
				System.out.print(id + ", ");
			}
			mutex.release();
			System.out.println("]");
			System.out.println();
		}
		*/

		private Lock mutex;

		private Condition empty;

		private boolean pages[];

		private int numFree;

		private int cursor;

	}

	private static class SwapFileManager{
		// protected by lock.
		// Notice: when writing to swapfile, there is no need to use lock because OS would support thread-safe write to file. Lock is used to protect the data structures in this class

		SwapFileManager(){
			swapFile = ThreadedKernel.fileSystem.open("swapFile.sys", true);
			mutex = new Lock();
			locationTable = new HashMap<>();
			lastPageLocation = -1;
		}

		// write one page to swap file. Write should always success, otherwise the kernel should halt
		public void write(PageId id){
			// There are 2 situations
			// 1. the page is first written to swapfile, we need to find a new place to write this page
			// 2. the page is written before, then we need to write the page to its original position
			byte[] memory = Machine.processor().getMemory();

			//System.out.println(KThread.currentThread() + " wait for swapfile lock in write");
			mutex.acquire();
			//System.out.println(KThread.currentThread() + " get swapfile lock in write");
			if(locationTable.containsKey(id)){
				int location = locationTable.get(id);
				swapFile.seek(location*pageSize);
				swapFile.write(memory, id.pte.ppn*pageSize, pageSize);
			} else {
				++lastPageLocation;
				locationTable.put(id, lastPageLocation);
				swapFile.seek(lastPageLocation*pageSize);
				swapFile.write(memory, id.pte.ppn*pageSize, pageSize);
			}
			mutex.release();
			//System.out.println(KThread.currentThread() + " release swapfile lock in write");
		}

		// return whether the page is existed in swapFile
		public boolean isExist(PageId id) {
			//System.out.println(KThread.currentThread() + " wait for swapfile lock in exist");
			mutex.acquire();
			//System.out.println(KThread.currentThread() + " get swapfile lock in exist");
			boolean exist = locationTable.containsKey(id);
			mutex.release();
			//System.out.println(KThread.currentThread() + " release swapfile lock in exist");
			return exist;
		}

		// read the page to buffer[offset] and return true. If the page is not existed, return false
		public boolean read(PageId id, byte[] buffer, int offset){
			//System.out.println(KThread.currentThread() + " wait for swapfile lock in read");
			mutex.acquire();
			//System.out.println(KThread.currentThread() + " get swapfile lock in read");
			boolean exist = locationTable.containsKey(id);
			////System.out.println(KThread.currentThread() + " exist: " + exist);
			if(!exist){
				mutex.release();
				//System.out.println(KThread.currentThread() + " release swapfile lock in read");
				return false;
			} else{
				byte[] memory = Machine.processor().getMemory();
				int location = locationTable.get(id);
				////System.out.println(KThread.currentThread() + " location: " + location);
				swapFile.seek(location*pageSize);
				////System.out.println(KThread.currentThread() + " finish seek");
				////System.out.println(KThread.currentThread() + " offset: " + offset);
				swapFile.read(buffer, offset, pageSize);
				////System.out.println(KThread.currentThread() + " finish read");
				mutex.release();
				//System.out.println(KThread.currentThread() + " release swapfile lock in read");
			}
			return true;
		}

		public void clear(PageId id){
			mutex.acquire();
			locationTable.remove(id);
			mutex.release();
		}

		private void printAll(){
			mutex.acquire();
			Iterator<Map.Entry<PageId, Integer>> it = locationTable.entrySet().iterator();
			Map.Entry<PageId, Integer> entry = null;
			System.out.print("Swap Files: [");

			//HashSet<Integer> h = new HashSet();
			while (it.hasNext()) {
				entry = it.next();
				PageId id = entry.getKey();
				System.out.print(id + ", ");
				// if(h.contains(entry.getValue())){
				// 	System.out.println("FUCK!");
				// } else{
				// 	h.add(entry.getValue());
				// }
			}
			mutex.release();

			System.out.println("]");
			System.out.println();
		}


		// physical swapfile
		private OpenFile swapFile;

		private Lock mutex;

		private HashMap<PageId, Integer> locationTable;

		int lastPageLocation;
	}

	private static Replacer replacer = new Replacer();

	private static SwapFileManager swapFile = new SwapFileManager();

	private Lock mutex;
}
