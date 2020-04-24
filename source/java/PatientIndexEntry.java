/*-----------------------------------------------------------------
*  This source software is released under the terms of the
*  Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
*------------------------------------------------------------------*/

package org.jp.deidentifier;

import java.io.Serializable;

public class PatientIndexEntry implements Serializable, Comparable<PatientIndexEntry> {
	public String key;
	public String name;
	public String id;
	
	public PatientIndexEntry (String key, String name, String id) {
		this.key = key;
		this.name = name;
		this.id = id;
	}
	
	public int compareTo(PatientIndexEntry ie) {
		return key.compareTo(ie.key);
	}
	
	public String toString() {
		return name + "[" + id + "]";
	}
}
	