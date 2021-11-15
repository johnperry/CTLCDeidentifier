/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.jp.deidentifier;

import java.io.File;
import java.util.Properties;
import jdbm.RecordManager;
import jdbm.htree.HTree;
import jdbm.helper.FastIterator;
import org.apache.log4j.Logger;
import org.rsna.util.JdbmUtil;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;

/**
 * A database for tracking assigned integer replacements for text strings.
 */
public class DeidentifierIntegerTable extends IntegerTable {

	static final Logger logger = Logger.getLogger(DeidentifierIntegerTable.class);

	/**
	 * Constructor; create an IntegerTable from a database file.
	 * @param dir the directory in which the database is to be created.
	 * @throws Exception if the table cannot be loaded.
	 */
	public DeidentifierIntegerTable(File dir) throws Exception {
		super(dir);
		//logTable();
	}

	/**
	 * Get a String containing an integer replacement for text of a specified type.
	 * @param type any String identifying the category of text being replaced, for example "ptid".
	 * @param text the text string to be replaced by an integer string.
	 * @param width the minimum width of the replacement string. If the width parameter
	 * is negative or zero, no padding is provided.
	 * @return the replacement string, with leading zeroes if necessary to pad the
	 * replacement string to the required width.
	 */
	public synchronized String getInteger(String type, String text, int width) {
		try {
			text = text.trim();
			type = type.trim();
			String key = type + "/" + text;
			Integer value = (Integer)index.get(key);
			if (value == null) {
				String lastIntKey = "__" + type + "__";
				Integer lastInt = (Integer)index.get(lastIntKey);
				if (lastInt == null) lastInt = new Integer(0);
				value = new Integer( lastInt.intValue() + 1 );
				String rangeKey = "<<" + type + ">>";
				Range range = (Range)index.get(rangeKey);
				if (range != null) {
					value = range.skip(value);
					if (value.intValue() > range.getUpperLimit()) {
						index.remove(rangeKey);
					}
				}
				index.put(lastIntKey, value);
				index.put(key, value);
				recman.commit();
			}
			int intValue = value.intValue();
			String format = (width > 0) ? ("%0"+width+"d") : ("%d");
			return String.format(format, intValue);
		}
		catch (Exception ex) { 
			logger.warn("Unable to assign integer",ex);
			return "error";
		}
	}
	
	public synchronized boolean setSkipRange(String type, int n1, int n2) {
		try {
			String rangeKey = "<<" + type + ">>";
			Range range = new Range(n1, n2);
			index.put(rangeKey, range);
			recman.commit();
			return true;
		}
		catch (Exception ex) { 
			logger.warn("Unable to set skip range for "+type+": "+n1+"-"+n2); 
			return false;
		}
	}
	
	private void logTable() {
		try {
			logger.info("IntegerTable:");
			FastIterator fit = index.keys();
			String key;
			Object value;
			while ( (key=(String)fit.next()) != null) {
				value = index.get(key);
				logger.info(key + ": " + value.toString());
			}
		}
		catch (Exception ex) {
			logger.warn("Unable to log the IntegerTable", ex);
		}
	}
	
}
