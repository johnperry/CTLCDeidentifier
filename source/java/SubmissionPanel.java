/*-----------------------------------------------------------------
*  This source software is released under the terms of the
*  Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
*------------------------------------------------------------------*/

package org.jp.deidentifier;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import org.apache.log4j.*;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.util.JarUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.rsna.ui.RowLayout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A JPanel that provides a user interface for preparing a submission
 * directory set and the relevant metadata file(s).
 */
public class SubmissionPanel extends JPanel implements ActionListener {

	static final Logger logger = Logger.getLogger(SubmissionPanel.class);

	private HeaderPanel headerPanel;
	private CenterPanel centerPanel;
	private FooterPanel footerPanel;
	Color background;
	File currentSelection = null;
	JFileChooser chooser;
	DirectoryFilter dirsOnly = new DirectoryFilter();
	FilenameComparator filenameComparator = new FilenameComparator();
	
	Font sectionFont = new Font( "SansSerif", Font.BOLD, 16 );
	Font itemFont = new Font( "SansSerif", Font.PLAIN, 16 );
	Font mono = new Font( "Monospaced", Font.BOLD, 16 );
	
	String[] yesNo = {"Yes", "No"};
	String[] categories = {
		"Not applicable", 
		"0 (incomplete)",
		"1 (negative)", 
		"2 (benign appearance or behavior)", 
		"3 (probably benign)", 
		"4A (suspicious)", 
		"4B (suspicious)", 
		"4X (suspicious)"
	};
	String[] smokingStatus = {"Never", "Past", "Current"};
	String[] cancerHistory = {"None", "Adenocarcinoma", "Adenosquamous", "Large cell", "Squamous cell", "Small cell"};
	String[] cancerStatus = {"None", "Suspicious Nodule(s)", "Lung Cancer"};
	String[] noduleType = {"Solid", "Part-solid", "Nonsolid", "Other"};
	String[] noduleStatus = {"Benign", "Malignant", "Unknown"};
	String[] pathologyType = {
		"AAH", 
		"Atyp bronch prolif", 
		"AIS", 
		"MIA", 
		"Unspec. non-small cell", 
		"Adenocarcinoma", 
		"Adenosquamous", 
		"Large cell", 
		"Squamous cell", 
		"Small cell", 
		"Carcinoid - typical", 
		"Carcinoid - atypical", 
		"Not malignant", 
		"Benign specific", 
		"Other"
	};
	
	/**
	 * Class SubmissionPanel.
	 */
    public SubmissionPanel() {
		super();
		Configuration config = Configuration.getInstance();
		setLayout(new BorderLayout());
		background = config.background;
		setBackground(background);
		centerPanel = new CenterPanel();
		footerPanel = new FooterPanel();
		footerPanel.select.addActionListener(this);
		footerPanel.save.addActionListener(this);
		headerPanel = new HeaderPanel();
		add(headerPanel, BorderLayout.NORTH);
		JScrollPane sp = new JScrollPane();
		sp.setViewportView(centerPanel);
		sp.getVerticalScrollBar().setBlockIncrement(50);
		sp.getVerticalScrollBar().setUnitIncrement(15);
		add(sp, BorderLayout.CENTER);
		add(footerPanel, BorderLayout.SOUTH);
	}
	
	/**
	 * Implementation of the ActionListener for the Save Changes button.
	 * @param event the event.
	 */
    public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(footerPanel.select)) {
			File selection = getSelection();
			if (selection != null) {
				currentSelection = selection;
				headerPanel.panelTitle.setText("Create Submission Metadata for "+currentSelection.getName());
				processSelection();
			}
		}
		else if (source.equals(footerPanel.save) && (currentSelection != null)) {
			highlightFields();
			int checkResult = checkFields();
			if (checkResult == 0) {
				centerPanel.saveMetadataXML(currentSelection);
				centerPanel.saveMetadataCSV(currentSelection);
				JOptionPane.showMessageDialog(this,
						"Metadata saved.",
						"Success", JOptionPane.INFORMATION_MESSAGE);
				
			}
			else {
				StringBuffer sb = new StringBuffer();
				if ((checkResult & 256) != 0) {
					sb.append(
						"There must be at least two studies per patient.\n\n"
					);
				}
				if ((checkResult & 1) != 0) {
					sb.append(
						"The DaysFromFirstStudyToDiagnosis field\n"+
						"must contain a value. Enter 0 if no lung\n"+
						"cancer was diagnosed. Enter a negative number\n"+
						"if lung cancer was diagnosed prior to the\n"+
						"first CT study.\n\n"
					);
				}
				if ((checkResult & 8) != 0) {
					sb.append(
						"The PackYears field must contain an integer.\n"+
						"For non-smokers, enter 0.\n\n"
					);
				}
				if ((checkResult & 128) != 0) {
					sb.append(
						"The PackYears fields must be in increasing order.\n\n"
					);
				}
				if ((checkResult & 16) != 0) {
					sb.append(
						"The XPosition and YPosition fields must contain\n"+
						"only integers.\n\n"
					);
				}
				if ((checkResult & 64) != 0) {
					sb.append(
						"The ZPosition field must contain a decimal number.\n\n"
					);
				}
				if ((checkResult & 32) != 0) {
					sb.append(
						"The LongestDiameter field must contain a\n"+
						"decimal value (in mm).\n\n"
					);
				}
				if ((checkResult & 2) != 0) {
					sb.append(
						"If a NoduleType field contains 'Other', the\n"+
						"corresponding NoduleTypeOtherExplanation\n"+
						"field must not be blank.\n\n"
					);
				}
				if ((checkResult & 4) != 0) {
					sb.append(
						"If a PathologyType field contains 'Other',\n"+
						"the corresponding OtherExplanation field\n"+
						"must not be blank.\n\n"
					);
				}
				JOptionPane.showMessageDialog(this,
					sb.toString(),
					"Entry Error",
					JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	private int checkFields() {
		int result = 0;
		Document doc = centerPanel.saveMetadataXML(null);
		if (doc != null) {
			Element root = doc.getDocumentElement();
			NodeList pts = root.getElementsByTagName("Patient");
			if (pts.getLength() > 0) {
				for (int i=0; i<pts.getLength(); i++) {
					Element pt = (Element)pts.item(i);
					//First check DaysFromFirstStudyToDiagnosis
					if (!checkNumericField(pt, "DaysFromFirstStudyToDiagnosis", false, false)) result |= 1;
					//Now check all the PackYears
					NodeList studies = pt.getElementsByTagName("Study");
					if (studies.getLength() < 2) result |= 256;
					for (int j=0; j<studies.getLength(); j++) {
						Element study = (Element)studies.item(j);
						if (!checkNumericField(study, "PackYears", false, false)) result |= 8;
					}
					//If the PackYears are all there, make sure they are in order.
					if ((result & 0x8) == 0) {
						for (int j=1; j<studies.getLength(); j++) {
							Element prev = (Element)studies.item(j-1);
							Element study = (Element)studies.item(j);
							int prevyrs = getIntegerField(prev, "PackYears");
							int studyyrs = getIntegerField(study, "PackYears");
							if (studyyrs < prevyrs) result |= 128;
						}
					}
					
					//Next loop on all the nodules
					NodeList nodules = pt.getElementsByTagName("Nodule");
					for (int j=0; j<nodules.getLength(); j++) {
						Element nodule = (Element)nodules.item(j);
						Element noduleType = XmlUtil.getFirstNamedChild(nodule, "NoduleType");
						if ((noduleType != null) && noduleType.getTextContent().trim().equals("Other")) {
							Element otherExp = XmlUtil.getFirstNamedChild(nodule, "NoduleTypeOtherExplanation");
							if ((otherExp == null) || otherExp.getTextContent().trim().equals("")) result |= 2;
						}
						Element pathologyType = XmlUtil.getFirstNamedChild(nodule, "PathologyType");
						if ((pathologyType != null) && pathologyType.getTextContent().trim().equals("Other")) {
							Element otherExp = XmlUtil.getFirstNamedChild(nodule, "OtherExplanation");
							if ((otherExp == null) || otherExp.getTextContent().trim().equals("")) result |= 4;
						}
						if (!checkNumericField(nodule, "XPosition", false, false)) result |= 16;
						if (!checkNumericField(nodule, "YPosition", false, false)) result |= 16;
						if (!checkNumericField(nodule, "ZPosition", false, true)) result |= 64;
						if (!checkNumericField(nodule, "LongestDiameter", false, true)) result |= 32;
					}
				}
			}
		}
		return result;
	}
	
	private int getIntegerField(Element parent, String name) {
		Element el = XmlUtil.getFirstNamedChild(parent, name);
		if (el != null) {
			String elText = el.getTextContent().trim();
			if (elText.equals("")) return 0;
			else {
				try { return Integer.parseInt(elText); }
				catch (Exception notOK) { return 0; }
			}
		}
		return 0;
	}
		
	private float getFloatField(Element parent, String name) {
		Element el = XmlUtil.getFirstNamedChild(parent, name);
		if (el != null) {
			String elText = el.getTextContent().trim();
			if (elText.equals("")) return 0;
			else {
				try { return Float.parseFloat(elText); }
				catch (Exception notOK) { return 0; }
			}
		}
		return 0;
	}
	
	private boolean checkNumericField(Element parent, String name, boolean acceptBlank, boolean acceptFloatingPoint) {
		Element el = XmlUtil.getFirstNamedChild(parent, name);
		if (el != null) {
			String elText = el.getTextContent().trim();
			if (elText.equals("")) return acceptBlank;
			else {
				try { 
					if (acceptFloatingPoint) {
						float elFloat = Float.parseFloat(elText);
					}
					else {
						int elInt = Integer.parseInt(elText);
					}
				}
				catch (Exception notOK) { return false; }
			}
		}
		return true;
	}
	
	private void highlightFields() {
		ItemField field;
		Component[] comps = centerPanel.getComponents();
		for (int i=0; i<comps.length; i++) {
			if (comps[i] instanceof ItemLabel) {
				ItemLabel label = (ItemLabel)comps[i];
				String labelText = label.getText();
				if (labelText.equals("DaysFromFirstStudyToDiagnosis")
						 || labelText.equals("XPosition")
						 || labelText.equals("YPosition")
						 || labelText.equals("PackYears")) {
					field = (ItemField)comps[i+1];
					String fieldText = field.getText().trim();
					boolean ok = true;
					if (fieldText.equals("")) ok = false;
					else {
						try { int fieldInt = Integer.parseInt(fieldText); }
						catch (Exception notInt) { ok = false; }
					}
					if (!ok) label.setForeground(Color.RED);
					else label.setForeground(Color.BLACK);
				}
				else if (labelText.equals("ZPosition")
						 || labelText.equals("LongestDiameter")) {
					field = (ItemField)comps[i+1];
					String fieldText = field.getText().trim();
					boolean ok = true;
					if (fieldText.equals("")) ok = false;
					else {
						try { float fieldFloat = Float.parseFloat(fieldText); }
						catch (Exception notInt) { ok = false; }
					}
					if (!ok) label.setForeground(Color.RED);
					else label.setForeground(Color.BLACK);
				}
				else if (labelText.equals("PackYears")) {
					field = (ItemField)comps[i+1];
					String years = field.getText().trim();
					boolean ok = true;
					if (years.equals("")) ok = false;
					else {
						try { int d = Integer.parseInt(years); }
						catch (Exception notInt) { ok = false; }
					}
					if (!ok) label.setForeground(Color.RED);
					else label.setForeground(Color.BLACK);
				}
				else if (labelText.equals("NoduleType")) {
					ItemComboBox combo = (ItemComboBox)comps[i+1];
					String text = ((String)combo.getSelectedItem()).trim();
					if (text.equals("Other")) {
						field = (ItemField)comps[i+4];
						ItemLabel otherExpLabel = (ItemLabel)comps[i+3];
						if (field.getText().trim().equals("")) {
							otherExpLabel.setForeground(Color.RED);
						}
						else otherExpLabel.setForeground(Color.BLACK);
					}
				}
				else if (labelText.equals("PathologyType")) {
					ItemComboBox combo = (ItemComboBox)comps[i+1];
					String text = ((String)combo.getSelectedItem()).trim();
					if (text.equals("Other")) {
						field = (ItemField)comps[i+4];
						ItemLabel otherExpLabel = (ItemLabel)comps[i+3];
						if (field.getText().trim().equals("")) {
							otherExpLabel.setForeground(Color.RED);
						}
						else otherExpLabel.setForeground(Color.BLACK);
					}
				}
			}
		}
	}
	
	private File getSelection() {
		chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		File dir = Configuration.getInstance().getOutputDir();
		File[] dirs = dir.listFiles(dirsOnly);
		if (dirs.length > 0) chooser.setSelectedFile(dirs[0]);
		else chooser.setSelectedFile(dir);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile();
		}
		else return null;
	}
	
	private void processSelection() {
		try {
			reorganizeFiles(currentSelection);
			centerPanel.removeAll();
			File[] patients = currentSelection.listFiles(new DBFilter());
			DicomObject dob = null;
			for (File patient : patients) {
				int studyNumber = 1;
				int seriesNumber = 1;
				String patientSex = "";
				String patientID = "";
				dob = getFirstDicomObject(patient);
				if (dob != null) {
					patientSex = dob.getElementValue("PatientSex");
					patientID = dob.getPatientID();
				}
				
				String pn = patient.getName();
				PatientIndexEntry ie = Index.getInstance().getInvEntry(pn);
				String ies = (ie != null) ? ie.toString() : null;
				String phiPatientID = (ie != null) ? ie.id : "";
				centerPanel.addSectionRow("Patient", ies, 1);
				
				centerPanel.addItemRow("PatientName", patient.getName(), 2);
				centerPanel.addItemRow("PatientID", patientID, 2);
				centerPanel.addItemRow("PatientSex", patientSex, 2);
				centerPanel.addItemComboBoxRow("LungCancerHistory", cancerHistory, 2);
				centerPanel.addItemFieldRow("DaysFromFirstStudyToDiagnosis", "", 2);

				File[] studies = patient.listFiles(dirsOnly);
				Arrays.sort(studies, filenameComparator);
				centerPanel.addItemRow("NStudies", Integer.toString(studies.length), 2);
				for (File study : studies) {
					String patientAge = "";
					dob = getFirstDicomObject(study);
					if (dob != null) {
						patientAge = dob.getElementValue("PatientAge");
						patientAge = fixPatientAge(patientAge);
					}
					
					String phi = getStudyPHI(phiPatientID, study);
					centerPanel.addSectionRow("Study", phi, 2);
					centerPanel.addItemRow("PatientAge", patientAge, 3);
					centerPanel.addItemFieldRow("PackYears", "", 3);
					centerPanel.addItemComboBoxRow("SmokingStatus", smokingStatus, 3);
					centerPanel.addItemRow("Path", getPath(patient, study), 3);
					centerPanel.addItemComboBoxRow("LungCancerStatus", cancerStatus, 3);
					File[] series = study.listFiles(dirsOnly);
					centerPanel.addItemRow("NSeries", Integer.toString(series.length), 3);
					boolean firstSeries = true;
					for (File ser: series) {
						centerPanel.addSectionRow("Series", 3);
						ItemValue noduleCount = centerPanel.addItemRow("Nodules", "0", 4);
						centerPanel.addItemRow("Path", getPath(patient, ser), 4);
						centerPanel.addItemComboBoxRow("Category", categories, 4);
						centerPanel.addRadioPanelRow("SModifier", yesNo, 4);
						centerPanel.addRadioPanelRow("CModifier", yesNo, 4);
						centerPanel.addNoduleButtonRow(1, noduleCount);
					}
				}
			}
		}
		catch (Exception ex) { ex.printStackTrace(); }
		centerPanel.revalidate();
	}
	
	class DBFilter implements FileFilter {
		Index index;
		public DBFilter() {
			Index index = Index.getInstance();
		}
		public boolean accept(File file) {
			if (file.isDirectory()) {
				String anonPtName = file.getName();
				PatientIndexEntry phiPIE = index.getInvEntry(anonPtName);
				if (phiPIE != null)  {
					String phiPtName = phiPIE.name;
					String phiPtID = phiPIE.id;
					PatientIndexEntry anonPIE = index.getFwdEntry(phiPtName);
					if (anonPIE != null) {
						StudyIndexEntry sie = index.getFwdStudyEntry(phiPtName);
						if ((sie != null) && (sie.studies.size() != 0)) return true;
						else logger.warn("Missing or empty forward study entry for "+file);
					}
					else logger.warn("Missing forward patient entry for "+file);
				}
				else logger.warn("Missing inverse patient entry for "+file);
			}
			return false;
		}
	}
	
	private String getStudyPHI(String phiPatientID, File dir) {
		Study[] indexedStudies = Index.getInstance().listStudiesFor(phiPatientID);
		try {
			DicomObject dob = getFirstDicomObject(dir);
			String modality = dob.getModality();
			String studyDate = dob.getStudyDate();
			String accession = dob.getAccessionNumber();
			for (Study study : indexedStudies) {
				if (study.anonAccession.equals(accession)
						&& study.anonDate.equals(studyDate)) {
					studyDate = study.phiDate;
					accession = study.phiAccession;
					studyDate = studyDate.substring(0,4) + "." +
								studyDate.substring(4,6) + "." +
								studyDate.substring(6,8);
					if (accession.equals("")) {
						return studyDate + " ["+modality+"]";
					}
					else {
						return studyDate + " / " + accession + " ["+modality+"]";
					}						
				}
			}
		}
		catch (Exception ex) { }
		return "";
	}
	
	private String fixPatientAge(String ageString) {
		ageString = ageString.replaceAll("[^0-9]","");
		int age = 0;
		try { age = Integer.parseInt(ageString); }
		catch (Exception oops) { }
		return Integer.toString(age);
	}
	
	private DicomObject getFirstDicomObject(File dir) {
		DicomObject dob;
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isFile()) {
				try { 
					dob = new DicomObject(file);
					return dob;
				}
				catch (Exception notDICOM) { }
			}
		}
		for (File file : files) {
			if (file.isDirectory()) {
				return getFirstDicomObject(file);
			}
		}
		return null;
	}		
	
	//Rename the files in one submission directory
	//using the conventions in the ELIC project.
	private void reorganizeFiles(File dir) { }
	
	private String getPath(File base, File dir) {
		File root = base.getAbsoluteFile().getParentFile();
		int rootPathLength = root.getAbsolutePath().length();
		String path = dir.getAbsolutePath().substring(rootPathLength);
		return "." +path.replace("\\", "/");
	}			

	class DirectoryFilter implements FileFilter {
		public boolean accept(File f) {
			return f.isDirectory();
		}
	}
	
	class FilenameComparator implements Comparator<File> {
		public int compare(File f1, File f2) {
			return f1.getName().compareTo(f2.getName());
		}
	}
		
	class HeaderPanel extends Panel {
		public JLabel panelTitle;
		public HeaderPanel() {
			super();
			setBackground(background);
			Box box = Box.createVerticalBox();
			panelTitle = new JLabel("Create Submission Metadata");
			panelTitle.setFont( new Font( "SansSerif", Font.BOLD, 18 ) );
			box.add(Box.createVerticalStrut(10));
			panelTitle.setForeground(Color.BLUE);
			box.add(panelTitle);
			box.add(Box.createVerticalStrut(10));
			this.add(box);
		}		
	}
	
	class CenterPanel extends JPanel implements ActionListener {
		public CenterPanel() {
			super();
			setBackground(background);
			setLayout(new RowLayout());
		}
		
		public SectionLabel addSectionRow(String title, String value, int level) {
			SectionLabel c = new SectionLabel(title, level);
			add(c);
			if (value != null) add(new SectionValue(value));
			add(RowLayout.crlf());
			return c;
		}
		
		public SectionLabel addSectionRow(LinkedList<Component>list, String title, String value, int level) {
			SectionLabel c = new SectionLabel(title, level);
			list.add(c);
			if (value != null) list.add(new SectionValue(value));
			list.add(RowLayout.crlf());
			return c;
		}
		
		public SectionLabel addSectionRow(String title, int level) {
			return addSectionRow(title, null, level);
		}
		
		public SectionLabel addSectionRow(LinkedList<Component>list, String title, int level) {
			return addSectionRow(list, title, null, level);
		}
		
		public ItemValue addItemRow(String title, String text, int level) {
			add(new ItemLabel(title, level));
			ItemValue c = new ItemValue(text);
			add(c);
			add(RowLayout.crlf());
			return c;
		}
		
		public ItemValue addItemRow(LinkedList<Component>list, String title, String text, int level) {
			list.add(new ItemLabel(title, level));
			ItemValue c = new ItemValue(text);
			list.add(c);
			list.add(RowLayout.crlf());
			return c;
		}
		
		public ItemField addItemFieldRow(String title, String text, int level) {
			add(new ItemLabel(title, level));
			ItemField c = new ItemField(text);
			add(c);
			add(RowLayout.crlf());
			return c;
		}
			
		public ItemField addItemFieldRow(LinkedList<Component>list, String title, String text, int level) {
			list.add(new ItemLabel(title, level));
			ItemField c = new ItemField(text);
			list.add(c);
			list.add(RowLayout.crlf());
			return c;
		}
			
		public ItemComboBox addItemComboBoxRow(String title, String[] text, int level) {
			add(new ItemLabel(title, level));
			ItemComboBox c = new ItemComboBox(text, 0);
			add(c);
			add(RowLayout.crlf());
			return c;
		}
		
		public ItemComboBox addItemComboBoxRow(LinkedList<Component>list, String title, String[] text, int level) {
			list.add(new ItemLabel(title, level));
			ItemComboBox c = new ItemComboBox(text, 0);
			list.add(c);
			list.add(RowLayout.crlf());
			return c;
		}
		
		public RadioPanel addRadioPanelRow(String title, String[] options, int level) {
			add(new ItemLabel(title, level));
			RadioPanel rp = new RadioPanel(options);
			add(rp);
			add(RowLayout.crlf());
			return rp;
		}		
		
		public RadioPanel addRadioPanelRow(LinkedList<Component>list, String title, String[] options, int level) {
			list.add(new ItemLabel(title, level));
			RadioPanel rp = new RadioPanel(options);
			list.add(rp);
			list.add(RowLayout.crlf());
			return rp;
		}		
		
		public NoduleButton addNoduleButtonRow(int n, ItemValue noduleCount) {
			add(Box.createHorizontalStrut(10));
			NoduleButton nb = new NoduleButton(n, noduleCount);
			nb.addActionListener(this);
			add(nb);
			add(RowLayout.crlf());
			return nb;
		}		
		
		public NoduleButton addNoduleButtonRow(LinkedList<Component>list, int n, ItemValue noduleCount) {
			list.add(Box.createHorizontalStrut(10));
			NoduleButton nb = new NoduleButton(n, noduleCount);
			nb.addActionListener(this);
			list.add(nb);
			list.add(RowLayout.crlf());
			return nb;
		}		
		
		public void actionPerformed(ActionEvent event) {
			Object source = event.getSource();
			Component[] comps = getComponents();
			LinkedList<Component> list = new LinkedList<Component>();
			for (int i=0; i<comps.length; i++) {
				list.add(comps[i]);
				if (comps[i].equals(source)) {
					Component button = list.removeLast(); //remove the button
					list.removeLast();	//remove the strut from before the button
					list.add(new SectionLabel("Nodule", 4)); 
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("NoduleNumber", 5));
					ItemValue noduleNumber = new ItemValue("1");
					list.add(noduleNumber);
					list.add(RowLayout.crlf());

					list.add(new ItemLabel("NoduleType", 5));
					list.add(new ItemComboBox(noduleType, 0));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("NoduleTypeOtherExplanation", 5));
					list.add(new ItemField(""));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("XPosition", 5));
					list.add(new ItemField(""));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("YPosition", 5));
					list.add(new ItemField(""));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("ZPosition", 5));
					list.add(new ItemField(""));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("LongestDiameter", 5));
					list.add(new ItemField(""));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("Status", 5));
					list.add(new ItemComboBox(noduleStatus, 0));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("PathologyType", 5));
					list.add(new ItemComboBox(pathologyType, 0));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("OtherExplanation", 5));
					list.add(new ItemField(""));
					list.add(RowLayout.crlf());
					
					list.add(Box.createHorizontalStrut(10));
					NoduleButton oldButton = (NoduleButton)button;
					int n = oldButton.getNumber();
					String s = Integer.toString(n);
					oldButton.noduleCount.setText(s);
					noduleNumber.setText(s);
					NoduleButton nb = new NoduleButton(n+1, oldButton.noduleCount);
					nb.addActionListener(this);
					list.add(nb); //leave the crlf in place
				}
			}
			removeAll();
			for (Component c : list) add(c);
			revalidate();
		}
		
		public void saveMetadataCSV(File dir) {
			try {
				StringBuffer sb = new StringBuffer();
				Component[] comps = getComponents();
				for (int i=0; i<comps.length; i++) {
					Component c = comps[i];
					if (c instanceof SectionLabel) {
						SectionLabel lbl = (SectionLabel)c;
						String text = lbl.getText();
						if (text.equals("Patient")) {
							if (sb.length() > 0) sb.append("\n");
						}
					}
					else if (c instanceof ItemValue) {
						//Don't include PatientID in the CSV
						Component cprev = comps[i-1];
						if (!(cprev instanceof ItemLabel)
								|| !((ItemLabel)cprev).getText().equals("PatientID")) {
							ItemValue val = (ItemValue)c;
							String text = val.getText();
							sb.append(text + ",");
						}
					}
					else if (c instanceof ItemField) {
						ItemField val = (ItemField)c;
						String text = val.getText();
						if (!text.contains(",")) sb.append(text + ",");
						else sb.append("\"" + text + "\",");
					}
					else if (c instanceof ItemComboBox) {
						ItemComboBox val = (ItemComboBox)c;
						String text = (String)val.getSelectedItem();
						sb.append(text + ",");
					}
					else if (c instanceof RadioPanel) {
						RadioPanel val = (RadioPanel)c;
						String text = val.getText();
						sb.append(text + ",");
					}
				}
				File metadata = new File(dir, "metadata.csv");
				FileUtil.setText(metadata, sb.toString());
			}
			catch (Exception ex) { ex.printStackTrace(); }
		}

		public Document saveMetadataXML(File dir) {
			try {
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement( (dir!=null) ? dir.getName() : "test" );
				doc.appendChild(root);
				
				File jar = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
				Hashtable<String,String> manifest = JarUtil.getManifestAttributes(jar);
				String build = manifest.get("Date");
				root.setAttribute("program", jar.getName());
				root.setAttribute("build", build);

				Element parent = root;
				Element lastPatient = null;
				Element lastStudy = null;
				Element lastSeries = null;
				Element lastNodule = null;
				Component[] comps = getComponents();
				for (int i=0; i<comps.length; i++) {
					Component c = comps[i];
					if (c instanceof SectionLabel) {
						SectionLabel lbl = (SectionLabel)c;
						String text = lbl.getText();
						Element e = doc.createElement(text);
						if (text.equals("Patient")) {
							parent = root;
							lastPatient = e;
						}
						else if (text.equals("Study")) {
							parent = lastPatient;
							lastStudy = e;
						}
						else if (text.equals("Series")) {
							parent = lastStudy;
							lastSeries = e;
						}
						else if (text.equals("Nodule")) {
							parent = lastSeries;
							lastNodule = e;
						}
						parent.appendChild(e);
						parent = e;
					}
					else if (c instanceof ItemLabel) {
						ItemLabel lbl = (ItemLabel)c;
						String text = lbl.getText();
						Element e = doc.createElement(text);
						parent.appendChild(e);
						parent = e;
					}
					else if (c instanceof ItemValue) {
						ItemValue val = (ItemValue)c;
						String text = val.getText();
						parent.setTextContent(text);
						parent =(Element)parent.getParentNode();
					}
					else if (c instanceof ItemField) {
						ItemField val = (ItemField)c;
						String text = val.getText();
						parent.setTextContent(text);
						parent =(Element)parent.getParentNode();
					}
					else if (c instanceof ItemComboBox) {
						ItemComboBox val = (ItemComboBox)c;
						String text = (String)val.getSelectedItem();
						parent.setTextContent(text);
						parent =(Element)parent.getParentNode();
					}
					else if (c instanceof RadioPanel) {
						RadioPanel val = (RadioPanel)c;
						String text = val.getText();
						parent.setTextContent(text);
						parent =(Element)parent.getParentNode();
					}
				}
				if (dir != null) {
					File metadata = new File(dir, "metadata.xml");
					FileUtil.setText(metadata, XmlUtil.toPrettyString(doc));
				}
				return doc;
			}
			catch (Exception ex) { 
				ex.printStackTrace();
				return null;
			}
		}
	}

	class FooterPanel extends JPanel {
		public JButton select;
		public JButton save;
		public FooterPanel() {
			super();
			setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			setLayout(new FlowLayout());
			setBackground(background);
			select = new JButton("Select Submission");
			save = new JButton("Save Metadata");
			add(select);
			add(Box.createHorizontalStrut(15));
			add(save);
		}
	}
	
	class SectionLabel extends JLabel	{
		public SectionLabel(String text, int level) {
			super(text);
			setFont(sectionFont);
			setForeground(Color.BLUE);
			setBorder(new EmptyBorder(0, 20*level, 0, 0));
		}
	}
	
	//class for phi in Patient element
	class SectionValue extends JLabel {
		public SectionValue(String text) {
			super(text);
			setFont(mono);
			setForeground(Color.BLACK);
		}
	}
	class ItemLabel extends JLabel {
		public ItemLabel(String text, int level) {
			super(text);
			setFont(itemFont);
			setForeground(Color.BLACK);
			setBorder(new EmptyBorder(0, 20*level, 0, 0));
		}
	}
	
	class ItemValue extends JLabel {
		public ItemValue(String text) {
			super(text);
			setFont(mono);
			setForeground(Color.BLACK);
		}
	}
	
	class ItemField extends JTextField {
		public ItemField(String text) {
			super("", 20);
			setFont(mono);
			setForeground(Color.BLACK);
			setAlignmentX(0.0f);
			setColumns(38);
		}
	}
	
	class ItemComboBox extends JComboBox<String> {
		public ItemComboBox(String[] values, int selectedIndex) {
			super(values);
			setSelectedIndex(selectedIndex);
			setFont(mono);
			setBackground(Color.white);
			setEditable(false);
			setAlignmentX(0.0f);
			Dimension d = getPreferredSize();
			d.width = 400;
			setPreferredSize(d);
		}
	}
	
	class NoduleButton extends JButton {
		int number;
		ItemValue noduleCount;
		public NoduleButton(int number, ItemValue noduleCount) {
			super("Add Nodule");
			this.number = number;
			this.noduleCount = noduleCount;
		}
		public int getNumber() {
			return number;
		}
	}	
	
	class RadioPanel extends JPanel {
		String[] options;
		ButtonGroup group;
		public RadioPanel(String[] options) {
			super();
			setLayout(new RowLayout());
			setBackground(background);
			this.options = options;
			group = new ButtonGroup();
			JRadioButton b = null;
			for (String s : options) {
				b = new JRadioButton(s);
				b.setBackground(background);
				this.add(b);
				group.add(b);
			}
			this.add(RowLayout.crlf());
			if (b != null) b.setSelected(true);
		}
		public String getText() {
			Enumeration<AbstractButton> e = group.getElements();
			while (e.hasMoreElements()) {
				AbstractButton b = e.nextElement();
				if (b.isSelected()) {
					String value = b.getText().toLowerCase();
					if (value.equals("???")) value = "";
					return value;
				}
			}
			return "";
		}
	}
	
}