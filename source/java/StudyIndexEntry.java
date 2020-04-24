/*-----------------------------------------------------------------
*  This source software is released under the terms of the
*  Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
*------------------------------------------------------------------*/

package org.jp.deidentifier;

import java.io.Serializable;
import java.util.HashSet;

public class StudyIndexEntry implements Serializable, Comparable<StudyIndexEntry> {
	public String key; // PHI PatientID
	public HashSet<Study> studies;
	
	public StudyIndexEntry(String key) {
		this.key = key;
		this.studies = new HashSet<Study>();
	}
	
	public void add(Study study) {
		studies.add(study);
	}
	
	public int compareTo(StudyIndexEntry ie) {
		return key.compareTo(ie.key);
	}
}