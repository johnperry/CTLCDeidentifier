/*-----------------------------------------------------------------
*  This source software is released under the terms of the
*  Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
*------------------------------------------------------------------*/

package org.rsna.installer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * A simple self-extracting installer. This class unpacks files from its own
 * jar file into a directory selected by the user, allowing an installer
 * program to be built to contain all the files needed by an application.
 *<p>
 * The program renames all *.properties files to *.properties-bak
 * in the selected directory before copying the files from the jar into
 * the directory.
 *<p>
 * To write an installer for a new application, use code like the following:
 *<p>
 *<code>
 *import org.rsna.installer.SimpleInstaller;<br>
 *public class Installer {<br>
 *&#160;public static void main(String args[]) {<br>
 *&#160;&#160;new SimpleInstaller("Window Title","ProgramName","Intro Text");<br>
 *&#160;}<br>
 *}<br>
 *</code>
 *<p>
 * Put all the files to be installed in a directory in the jar called [ProgramName].
 *<p>
 * Name the installer program [ProgramName]-installer.jar.
 */
public class SimpleInstaller extends JFrame {

	JPanel			mainPanel;
	JEditorPane		textPane;
	Color			background;
	JFileChooser	chooser;
	File			directory;

	String			windowTitle;
	String			programName;
	String			introString;
	String[] 		filelist;

	/**
	 * Class constructor; creates a new SimpleInstaller object, displays a JFrame
	 * introducing the program, allows the user to select an install directory,
	 * backs up any properties files found in the directory, and copies files
	 * from the jar into the directory.
	 *<p>
	 * If the selected directory has the same name as the program, the files are
	 * installed in the selected directory. If the selected directory has any other
	 * name, a directory with the name of the program is created in the selected
	 * directory and the files are installed there.
	 *<p>
	 * The installer program must be named [programName]-installer.jar.
	 * The files for installation must be in a directory named [programName]
	 * within the jar. If there is a directory tree of files to be installed,
	 * the root of the tree must the [programName] directory in the jar.
	 * @param windowTitle the title to be displayed in the JFrame title bar.
	 * @param programName the name of the program. This name is also the name of the
	 * directory that will be created to contain the files from the jar.
	 * @param introString the text to be displayed in the JFrame, describing the
	 * program being installed.
	 */
	public SimpleInstaller(String windowTitle, String programName, String introString) {
		this.windowTitle = windowTitle;
		this.programName = programName;
		this.introString = introString;
		this.filelist = filelist;
		this.getContentPane().setLayout(new BorderLayout());
		setTitle(windowTitle);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent evt) {exitForm(evt);} });
		mainPanel = new JPanel(new BorderLayout());
		this.getContentPane().add(mainPanel, BorderLayout.CENTER);
		background = new Color(0xEBEBEB);
		textPane = new JEditorPane("text/html",getWelcomePage());
		mainPanel.add(textPane,BorderLayout.CENTER);
		pack();
		centerFrame();
		setVisible(true);

		//Get the selected directory
		if ((directory=getDirectory()) == null) exit();

		//Point to the parent of the selected directory so the
		//copy process works correctly for directory trees.
		//If the user has selected a directory with the name
		//of the program, then assume that this is the directory
		//in which to install the program; otherwise, assume that
		//this is the parent of the directory in which to install
		//the program
		if (directory.getName().equals(programName)) {
			directory = directory.getParentFile();
		}

		//Find the installer program so we can get to the files.
		File installer = getProgramFile();
		System.out.println("InstallerFile: "+installer);

		//Copy the files
		int count = unpackZipFile(installer,programName,directory.getAbsolutePath());
		System.out.println("Unpacked file count: "+count);

		if (count > 0)
			JOptionPane.showMessageDialog(this,
					programName+" has been installed successfully.\n"
					+ count + " files were installed.",
					"Installation Complete",
					JOptionPane.INFORMATION_MESSAGE);
		else
			JOptionPane.showMessageDialog(this,
					programName+" could not be fully installed.",
					"Installation Failed",
					JOptionPane.INFORMATION_MESSAGE);
		exit();
	}

	//Very clunky function to find the installer program file
	//so we can read its contents and get the files to install.
	//The strategy is to get the java.class.path property and
	//obtain the first path element. If the filename part of that
	//element starts with [programName]-installer and ends with .jar,
	//use it as the program file. Otherwise, get the user.dir
	//property and go for [programName]-installer.jar.
	private File getProgramFile() {
		File programFile = null;
		try { 
			programFile = new File(SimpleInstaller.class.getProtectionDomain()
										.getCodeSource().getLocation().toURI());
		}
		catch (Exception unable) {
			System.out.println("Unabled to find installer program file");
		}
		return programFile;
	}

	//Take a tree of files starting in a directory in a zip file
	//and copy them to a disk directory, recreating the tree and backing up
	//any properties and script files that exist before they are overwritten.
	private int unpackZipFile(File inZipFile, String directory, String parent) {
		int count = 0;
		if (!inZipFile.exists()) return count;
		parent = parent.trim();
		if (!parent.endsWith(File.separator)) parent += File.separator;
		if (!directory.endsWith(File.separator)) directory += File.separator;
		
		System.out.println("Directory: "+directory);
		System.out.println("Parent: "+parent);
		
		File outFile = null;
		try {
			ZipFile zipFile = new ZipFile(inZipFile);
			
			//System.out.println("Created ZipFile");
			
			Enumeration zipEntries = zipFile.entries();
			while (zipEntries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry)zipEntries.nextElement();
				String name = entry.getName().replace('/',File.separatorChar);
				
				//System.out.println("Got ZipEntry: "+name);
				
				if (name.startsWith(directory)) {
					outFile = new File(parent + name);
					//Create the directory, just in case
					if (name.indexOf(File.separatorChar) >= 0) {
						String p = name.substring(0,name.lastIndexOf(File.separatorChar)+1);
						File dirFile = new File(parent + p);
						dirFile.mkdirs();
						//System.out.println("...created directory: "+dirFile);
					}
					if (!entry.isDirectory()) {
						String outName = outFile.getName();
						if (outName.endsWith(".properties") || outName.endsWith(".script")) backup(outFile);
						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));
						BufferedInputStream in = new BufferedInputStream(zipFile.getInputStream(entry));
						int size = 1024;
						int n = 0;
						byte[] b = new byte[size];
						while ((n = in.read(b,0,size)) != -1) out.write(b,0,n);
						in.close();
						out.flush();
						out.close();
						//Count the file
						count++;
					}
				}
				//System.out.println("...count = "+count);
			}
			zipFile.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this,
					"Error copying " + outFile.getName() + "\n" + e.getMessage(),
					"I/O Error", JOptionPane.INFORMATION_MESSAGE);
			return -count;
		}
		return count;
	}

	//Backup a file by copying it to name.ext-bak
	private boolean backup(File file) {
		if (!file.exists()) return false;
		File bak = new File(file.getParentFile(),file.getName()+"-bak");
		if (bak.exists()) bak.delete();
		copyFile(file,bak);
		return true;
	}

	//Copy a file to another file
	private  boolean copyFile(File inFile, File outFile) {
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		boolean result = true;
		try {
			in = new BufferedInputStream(new FileInputStream(inFile));
			out = new BufferedOutputStream(new FileOutputStream(outFile));
			byte[] buffer = new byte[4096];
			int n;
			while ( (n = in.read(buffer,0,4096)) != -1) out.write(buffer,0,n);
		}
		catch (Exception e) { result = false; }
		finally {
			try { out.flush(); out.close(); in.close(); }
			catch (Exception ignore) { }
		}
		return result;
	}

	//Let the user select an installation directory.
	private File getDirectory() {
		if (chooser == null) {
			//See if there is a directory called JavaPrograms
			//in the root of the current drive. If there is,
			//set that as the current directory; otherwise,
			//use the root of the drive as the current directory.
			File currentDirectory = new File(File.separator);
			File javaPrograms = new File(currentDirectory,"JavaPrograms");
			if (javaPrograms.exists() && javaPrograms.isDirectory())
				currentDirectory = javaPrograms;
			//Now make a new chooser and set the current directory.
			chooser = new JFileChooser();
			chooser.setCurrentDirectory(currentDirectory);
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setDialogTitle("Select a directory in which to install the program");
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File dir = chooser.getSelectedFile();
			return dir;
		}
		return null;
	}

	private String getWelcomePage() {
		return
				"<html><head></head><body>\n"
			+	"<center>\n"
			+	"<h1 style=\"color:red\">" + windowTitle + "</h1>\n"
			+	"</center>\n"
			+	introString
			+	"<p>This program allows you to upgrade the <b>"+programName+"</b> "
			+	"program or install a new one.</p>"
			+	"</body></html>";
	}

	private static void exit() {
		System.exit(0);
	}

	private void exitForm(java.awt.event.WindowEvent evt) {
		System.exit(0);
	}

	private void centerFrame() {
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(scr.width/2, scr.height/2);
		int x = (scr.width-getSize().width)/2;
		int y = (scr.height-getSize().height)/2;
		setLocation(new Point(x,y));
	}

}
