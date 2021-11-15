/*-----------------------------------------------------------------
*  This source software is released under the terms of the
*  Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
*------------------------------------------------------------------*/

package org.jp.deidentifier;

import java.awt.Color;
import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ui.ApplicationProperties;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;

/**
 * The singleton class that encapsulates the configuration of the program.
 */
public class Configuration {

	static final Logger logger = Logger.getLogger(Configuration.class);

    public static final String propfile 		= "program.properties";
    public static final String idtablepropfile 	= "idtable.properties";
    public static final String dicomScriptFile	= "dicom-anonymizer.script";
    public static final String lookupTableFile	= "lookup-table.properties";
    public static final String helpfile 		= "help.html";
    
    public static DeidentifierIntegerTable integerTable = null;
    public static File outputDir				= null;
    public static File databaseDir				= null;

	public static final Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	static Configuration configuration = null;
    private ApplicationProperties props;

	/**
	 * Get the singleton instance of the Configuration.
	 * @return the Configuration.
	 */
	public static synchronized Configuration getInstance() {
		if (configuration == null) configuration = new Configuration();
		return configuration;
	}

	//The protected constructor.
	protected Configuration() {
		props = new ApplicationProperties(new File(propfile));
		File home = new File(System.getProperty("user.dir"));
		databaseDir = new File(home, "data");
		databaseDir.mkdirs();
		try { 
			integerTable = new DeidentifierIntegerTable(databaseDir);
			outputDir = home;
			String odString = props.getProperty("outputDir");
			if (odString != null) outputDir = new File(odString);
			else outputDir = new File(home, "Submissions");
			props.setProperty("outputDir", outputDir.getAbsolutePath());
			String ext = props.getProperty("extensions", "*"); //was ".dcm,[dcm]"
			props.setProperty("extensions", ext);
			installIntegerRange();
			props.store();
		}
		catch (Exception ex) { }
	}
	
	//If there is a range property, install the range in the
	//IntegerTable and then delete the property.
	private void installIntegerRange() {
		String range = props.getProperty("range");
		if (range != null) {
			try {
				String[] limits = range.trim().split("[,-]");
				if (limits.length == 2) {
					int low = Integer.parseInt(limits[0].trim());
					int high = Integer.parseInt(limits[1].trim());
					if (integerTable.setSkipRange("\"ptid\"", low, high)) {
						props.remove("range");
						logger.warn("Integer skip range set: "+low+"-"+high);
					}
				}
			}
			catch (Exception ex) {
				logger.warn("Unable to process range property: \""+range+"\"");
			}
		}
	}
	
	public DeidentifierIntegerTable getIntegerTable() {
		return integerTable;
	}
	
	public File getOutputDir() {
		return outputDir;
	}

	public File getDatabaseDir() {
		return databaseDir;
	}

	public void setOutputDir(File outputDir) {
		props.put("outputDir", outputDir.getAbsolutePath());
		this.outputDir = outputDir;
	}

	public ApplicationProperties getProps() {
		return props;
	}

	public void put(String key, String value) {
		props.setProperty(key, value);
	}

	public String get(String key) {
		return props.getProperty(key);
	}

	public void store() {
		props.store();
	}

}
