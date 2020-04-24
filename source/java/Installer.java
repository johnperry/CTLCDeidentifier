/*-----------------------------------------------------------------
*  This source software is released under the terms of the
*  Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
*------------------------------------------------------------------*/

package org.jp.installer;

import org.rsna.installer.SimpleInstaller;

/**
 * The DeIdentifier program installer, consisting of just a
 * main method that instantiates a SimpleInstaller.
 */
public class Installer {

	static String windowTitle = "Deidentifier Installer";
	static String programName = "Deidentifier";
	static String introString = "<p><b>Deidentifier</b> is a stand-alone tool for viewing, examining, "
								+ "and de-identifying DICOM objects.</p>";

	public static void main(String args[]) {
		new SimpleInstaller(windowTitle,programName,introString);
	}
}
