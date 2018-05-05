package nachos.threads;

import java.util.LinkedList;
import nachos.machine.*;
import nachos.threads.*;


/**
 * This file is user-generated and is used to be a lock-aligned list
 **/

public class LockList {
	public LockList() {
		list = new LinkedList<Object>();
		lock = new Lock();
	}

	//
	public void add(Object o) {
		Lib.assertTrue(o != null);

		lock.acquire();
		list.add(o);
		lock.release();
	}

	// pop first
	public Object shift() {
		Object o;

		lock.acquire();
		if (list.isEmpty()) {
			o = null;
		} else {
			o = list.removeFirst();
		}
		lock.release();

		return o;
	}


	public int size(){
		int s;

		lock.acquire();
		s = list.size();
		lock.release();

		return s;
	}

	private LinkedList<Object> list;
	private Lock lock;
}

