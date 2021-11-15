package org.jp.deidentifier;

import java.io.Serializable;

/**
 * A class to encapsulate two ints specifying a range to 
 * be excluded from the assignment of sequential Integers.
 */
public class Range implements Serializable {
	int low;
	int high;
	
	public Range(int low, int high) {
		if (low > high) {
			int x = low;
			low = high;
			high = x;
		}
		this.low = low;
		this.high = high;
	}
	
	public Integer skip(Integer k) {
		int kk = k.intValue();
		if (kk < low) return k;
		if (kk > high) return k;
		return new Integer(high + 1);
	}
	
	public int getLowerLimit() {
		return low;
	}
	
	public int getUpperLimit() {
		return high;
	}
	
	public String toString() {
		return "("+low+"-"+high+")";
	}
}
