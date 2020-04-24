/*-----------------------------------------------------------------
*  This source software is released under the terms of the
*  Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
*------------------------------------------------------------------*/

package org.jp.deidentifier;

import java.io.Serializable;

public class Study implements Serializable, Comparable<Study> {
	public String phiDate;
	public String phiAccession;
	public String anonDate;
	public String anonAccession;
	
	public Study (String phiDate, String phiAccession, String anonDate, String anonAccession) {
		this.phiDate = phiDate;
		this.phiAccession = phiAccession;
		this.anonDate = anonDate;
		this.anonAccession = anonAccession;
	}
	
	public int compareTo(Study ie) {
		int c ;
		if ( (c=phiDate.compareTo(ie.phiDate)) != 0) return c;
		return phiAccession.compareTo(ie.phiAccession);
	}
	
	public boolean equals(Object obj) {
		if (obj instanceof Study) {
			Study study = (Study)obj;
			if ( phiDate.equals(study.phiDate) 
					&& phiAccession.equals(study.phiAccession) ) return true;
		}
		return false;
	}
	
	public boolean equals(Study study) {
		return phiDate.equals(study.phiDate) 
				&& phiAccession.equals(study.phiAccession);
	}
	
	public int hashCode() {
		return (phiDate+phiAccession).hashCode();
	}
	
	public String toString() {
		return "Study: phiDate: "+phiDate+"; phiAN: "+phiAccession+"; anonDate: "+anonDate+"; anonAN: "+anonAccession;
	}
}
	