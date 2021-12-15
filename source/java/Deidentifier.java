/*-----------------------------------------------------------------
*  This source software is released under the terms of the
*  Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
*------------------------------------------------------------------*/

package org.jp.deidentifier;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import org.apache.log4j.*;
import org.rsna.ui.ApplicationProperties;
import org.rsna.ui.SourcePanel;
import org.rsna.util.BrowserUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.JarUtil;
import org.rsna.util.StringUtil;

import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The DicomEditor program provides a DICOM viewer and
 * element editor plus an anonymizer that can process a
 * single file, all the files in a single directory, or
 * all the files in a directory tree.
 */
public class Deidentifier extends JFrame implements ChangeListener {

    private String					windowTitle = "DeIdentifier - version 6";
    private MainPanel				mainPanel;
    private JPanel					splitPanel;
    private SourcePanel				sourcePanel;
    private RightPanel				rightPanel;
    private Viewer 					viewerPanel;
    private Editor 					editorPanel;
    private FilterPanel				filterPanel;
    private AnonymizerPanel			anonymizerPanel;
    private SubmissionPanel			submissionPanel;
    private IndexPanel				indexPanel;
    private HtmlJPanel 				helpPanel;

	/**
	 * The main method to start the program.
	 * @param args the list of arguments from the command line.
	 */
    public static void main(String args[]) {
		Logger.getRootLogger().addAppender(
				new ConsoleAppender(
					new PatternLayout("%d{HH:mm:ss} %-5p [%c{1}] %m%n")));
		Logger.getRootLogger().setLevel(Level.INFO);
		Deidentifier deidentifier = new Deidentifier();
    }

	/**
	 * Class constructor; creates the program main class.
	 */
    public Deidentifier() {
		super();
		Configuration config = Configuration.getInstance();
		
		//************************************************************
		//                   Initialization
		//************************************************************
		//Initialize the SITEID
		String propsSITEID = config.getProps().getProperty("SITEID");
		if (propsSITEID == null) {
			long t = System.currentTimeMillis()/60L;
			propsSITEID = Long.toString(t);
			propsSITEID = propsSITEID.substring(propsSITEID.length() - 6);
			config.getProps().setProperty("SITEID", propsSITEID);
			config.store();
		}
		File daScriptFile = new File(config.dicomScriptFile);
		DAScript daScript = DAScript.getInstance(daScriptFile);
		Document daDoc = daScript.toXML();
		Element daRoot = daDoc.getDocumentElement();
		Node child = daRoot.getFirstChild();
		String scriptSITEID = null;
		Element e = null;
		while (child != null) {
			if (child.getNodeName().equals("p")) {
				e = (Element)child;
				if (e.getAttribute("t").equals("SITEID")) {
					scriptSITEID = e.getTextContent().trim();
					break;
				}
			}
			child = child.getNextSibling();
		}
		if (scriptSITEID == null) {
			//Add the SITEID param if it is missing
			Element x = daDoc.createElement("p");
			x.setAttribute("t", "SITEID");
			x.setTextContent(propsSITEID);
			daRoot.insertBefore(x, daRoot.getFirstChild());
			FileUtil.setText(daScriptFile, daScript.toXMLString());
		}
		else if (!scriptSITEID.equals(propsSITEID)) {
			//Make sure the script reflects the props SITEID
			e.setTextContent(propsSITEID);
			FileUtil.setText(daScriptFile, daScript.toXMLString());
		}
		//Force the PatientID script
		child = daRoot.getFirstChild();
		while (child != null) {
			if (child.getNodeName().equals("e")) {
				Element x = (Element)child;
				if (x.getAttribute("t").equals("00100020")) {
					String oldScript = x.getTextContent().trim();
					String newScript = "@param(@SITEID)-@integer(PatientID,\"ptid\",6)";
					if (!oldScript.equals(newScript)) {
						x.setTextContent(newScript);
						FileUtil.setText(daScriptFile, daScript.toXMLString());
						break;
					}
				}
			}
			child = child.getNextSibling();
		}
		//************************************************************
		//                   End of initialization
		//************************************************************

		//Put the build date/time in the window title
		try {
			File program = new File("Deidentifier.jar");
			String date = JarUtil.getManifestAttributes(program).get("Date");
			windowTitle += " - " + date;
		}
		catch (Exception ignore) { }
				
		setTitle(windowTitle);
		addWindowListener(new WindowCloser(this));
		mainPanel = new MainPanel();
		getContentPane().add(mainPanel, BorderLayout.CENTER);
		
		sourcePanel = new SourcePanel(config.getProps(), "Directory", config.background);
		rightPanel = new RightPanel(sourcePanel);
		JSplitPane jSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourcePanel, rightPanel);
		jSplitPane.setResizeWeight(0.5D);
		jSplitPane.setContinuousLayout(true);
		splitPanel = new JPanel(new BorderLayout());
		splitPanel.add(jSplitPane,BorderLayout.CENTER);
		
		anonymizerPanel = new AnonymizerPanel();
		viewerPanel = new Viewer();
		editorPanel = new Editor();
		filterPanel = FilterPanel.getInstance();
		submissionPanel = new SubmissionPanel();
		indexPanel = new IndexPanel();
		helpPanel = new HtmlJPanel( FileUtil.getText( new File(config.helpfile) ) );
		
		mainPanel.addTabs(
			splitPanel,
			viewerPanel,
			editorPanel,
			filterPanel,
			anonymizerPanel,
			submissionPanel,
			indexPanel,
			helpPanel);
		
		mainPanel.tabbedPane.addChangeListener(this);
		sourcePanel.addFileListener(viewerPanel);
		sourcePanel.addFileListener(editorPanel);
		pack();
		positionFrame();
		setVisible(true);
		
		//System.out.println(org.rsna.util.ImageIOTools.listAvailableReadersAndWriters());
		
    }
    
	public void stateChanged(ChangeEvent event) {
		Component comp = mainPanel.tabbedPane.getSelectedComponent();
		if (comp.equals(indexPanel)) {
			indexPanel.setFocus();
		}
	}
	
	class MainPanel extends JPanel {
		public JTabbedPane tabbedPane;
		public MainPanel() {
			super();
			this.setLayout(new BorderLayout());
			tabbedPane = new JTabbedPane();
			this.add(tabbedPane,BorderLayout.CENTER);
		}
		public void addTabs(JPanel source,
						 Viewer viewer,
						 Editor editor,
						 FilterPanel filter,
						 AnonymizerPanel script,
						 SubmissionPanel submission,
						 IndexPanel index,
						 JPanel help) {
			tabbedPane.addTab("Directory", source);
			tabbedPane.addTab("Viewer", viewer);
			tabbedPane.addTab("Elements", editor);
			tabbedPane.addTab("Filter", filter);
			tabbedPane.addTab("Script", script);
			tabbedPane.addTab("Submission", submission);
			tabbedPane.addTab("Index", index);
			tabbedPane.addTab("Help", help);
			tabbedPane.setSelectedIndex(0);
			tabbedPane.addChangeListener(viewer);
		}
	}

    class WindowCloser extends WindowAdapter {
		JFrame parent;
		public WindowCloser(JFrame parent) {
			this.parent = parent;
		}
		public void windowClosing(WindowEvent evt) {
			Configuration config = Configuration.getInstance();
			config.getIntegerTable().close();
			Index.getInstance().close();
			Point p = getLocation();
			config.put("x", Integer.toString(p.x));
			config.put("y", Integer.toString(p.y));
			Toolkit t = getToolkit();
			Dimension d = parent.getSize ();
			config.put("w", Integer.toString(d.width));
			config.put("h", Integer.toString(d.height));
			config.put("subdirectories", (sourcePanel.getSubdirectories()?"yes":"no"));
			config.store();
			System.exit(0);
		}
    }

	private void positionFrame() {
		Configuration config = Configuration.getInstance();
		int x = StringUtil.getInt( config.get("x"), 0 );
		int y = StringUtil.getInt( config.get("y"), 0 );
		int w = StringUtil.getInt( config.get("w"), 0 );
		int h = StringUtil.getInt( config.get("h"), 0 );
		boolean noProps = ((w == 0) || (h == 0));
		int wmin = 800;
		int hmin = 600;
		if ((w < wmin) || (h < hmin)) {
			w = wmin;
			h = hmin;
		}
		if ( noProps || !screensCanShow(x, y) || !screensCanShow(x+w-1, y+h-1) ) {
			Toolkit t = getToolkit();
			Dimension scr = t.getScreenSize ();
			x = (scr.width - wmin)/2;
			y = (scr.height - hmin)/2;
			w = wmin;
			h = hmin;
		}
		setSize( w, h );
		setLocation( x, y );
	}

	private boolean screensCanShow(int x, int y) {
		GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = env.getScreenDevices();
		for (GraphicsDevice screen : screens) {
			GraphicsConfiguration[] configs = screen.getConfigurations();
			for (GraphicsConfiguration gc : configs) {
				if (gc.getBounds().contains(x, y)) return true;
			}
		}
		return false;
	}
	
	private void checkImageIOTools() {
		String javaHome = System.getProperty("java.home");
		File extDir = new File(javaHome);
		extDir = new File(extDir, "lib");
		extDir = new File(extDir, "ext");
		File clib = FileUtil.getFile(extDir, "clibwrapper_jiio", ".jar");
		File jai = FileUtil.getFile(extDir, "jai_imageio", ".jar");
		boolean imageIOTools = (clib != null) && (jai != null);
		if (!imageIOTools) {
			JOptionPane.showMessageDialog(this, 
				"The ImageIOTools are not installed on this machine.\n" +
				"When you close this dialog, your browser will launch\n" +
				"and take you to a site where you can obtain them.");
			BrowserUtil.openURL(
				"http://mircwiki.rsna.org/index.php?title=Java_Advanced_Imaging_ImageIO_Tools");
		}			
	}

}
