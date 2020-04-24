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
import org.rsna.ctp.objects.DicomObject;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.rsna.ui.RowLayout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A JPanel that provides a user interface for preparing a submission
 * directory set and the relevant metadata file(s).
 */
public class SubmissionPanel extends JPanel implements ActionListener {

	private HeaderPanel headerPanel;
	private CenterPanel centerPanel;
	private FooterPanel footerPanel;
	Color background;
	File currentSelection = null;
	JFileChooser chooser;
	DirectoryFilter dirsOnly = new DirectoryFilter();
	
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
	String[] cancerHistory = {"None", "Adenocarcenoma", "Adenosquamous", "Large cell", "Squamous cell", "Small cell"};
	String[] cancerStatus = {"None", "Suspicious Nodule(s)", "Lung Cancer"};
	String[] noduleType = {"Solid", "Part-solid", "Nonsolid", "Other"};
	String[] noduleStatus = {"Benign", "Malignant", "Unknown"};
	String[] pathologyType = {
		"AAH", 
		"Atyp bronc prolif", 
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
			centerPanel.saveMetadataXML(currentSelection);
			centerPanel.saveMetadataCSV(currentSelection);
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
			File[] patients = currentSelection.listFiles(dirsOnly);
			DicomObject dob = null;
			for (File patient : patients) {
				int studyNumber = 1;
				int seriesNumber = 1;
				
				String pn = patient.getName();
				PatientIndexEntry ie = Index.getInstance().getInvEntry(pn);
				String ies = (ie != null) ? ie.toString() : null;
				//System.out.println("pn: \"" + pn + "\"; ies: "+(ies!=null ? ies.toString() : "null"));				
				centerPanel.addSectionRow("Patient", ies, 1);
				
				centerPanel.addItemRow("PatientName", patient.getName(), 2);
				ItemValue ptsex = centerPanel.addItemRow("PatientSex", "", 2);
				centerPanel.addItemComboBoxRow("LungCancerHistory", cancerHistory, 2);
				centerPanel.addItemFieldRow("DaysFromFirstStudyToDiagnosis", "", 2);
				File[] studies = patient.listFiles(dirsOnly);
				centerPanel.addItemRow("NStudies", Integer.toString(studies.length), 2);
				for (File study : studies) {
					centerPanel.addSectionRow("Study", 2);
					ItemValue ptage = centerPanel.addItemRow("PatientAge", "", 3);
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
						centerPanel.addItemComboBoxRow("SModifier", yesNo, 4);
						centerPanel.addItemComboBoxRow("CModifier", yesNo, 4);
						centerPanel.addNoduleButtonRow(1, noduleCount);
						if (firstSeries) {
							File img = ser.listFiles()[0];
							try {
								dob = new DicomObject(img);
								ptsex.setText(dob.getElementValue("PatientSex"));
								ptage.setText(dob.getElementValue("PatientAge"));
								firstSeries = false;
							}
							catch (Exception tryAgain) { }
						}
					}
				}
			}
		}
		catch (Exception ex) { ex.printStackTrace(); }
		centerPanel.revalidate();
	}
	
	//Rename the files in one submission directory
	//using the conventions in the ELIC project.
	private void reorganizeFiles(File dir) {
		
	}
	
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
		
		public SectionLabel addSectionRow(String title, int level) {
			return addSectionRow(title, null, level);
		}
		
		public ItemValue addItemRow(String title, String text, int level) {
			add(new ItemLabel(title, level));
			ItemValue c = new ItemValue(text);
			add(c);
			add(RowLayout.crlf());
			return c;
		}
		
		public ItemField addItemFieldRow(String title, String text, int level) {
			add(new ItemLabel(title, level));
			ItemField c = new ItemField(text);
			add(c);
			add(RowLayout.crlf());
			return c;
		}
			
		public ItemComboBox addItemComboBoxRow(String title, String[] text, int level) {
			add(new ItemLabel(title, level));
			ItemComboBox c = new ItemComboBox(text, 0);
			add(c);
			add(RowLayout.crlf());
			return c;
		}
		
		public NoduleButton addNoduleButtonRow(int n, ItemValue noduleCount) {
			add(Box.createHorizontalStrut(10));
			NoduleButton nb = new NoduleButton(n, noduleCount);
			nb.addActionListener(this);
			add(nb);
			add(RowLayout.crlf());
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
					
					list.add(new ItemLabel("NoduleType", 5));
					list.add(new ItemField(""));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("RPosition", 5));
					list.add(new ItemField(""));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("APosition", 5));
					list.add(new ItemField(""));
					list.add(RowLayout.crlf());
					
					list.add(new ItemLabel("SPosition", 5));
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
						ItemValue val = (ItemValue)c;
						String text = val.getText();
						sb.append(text + ",");
					}
					else if (c instanceof ItemField) {
						ItemField val = (ItemField)c;
						String text = val.getText();
						sb.append(text + ",");
					}
					else if (c instanceof ItemComboBox) {
						ItemComboBox val = (ItemComboBox)c;
						String text = (String)val.getSelectedItem();
						sb.append(text + ",");
					}
				}
				File metadata = new File(dir, "metadata.csv");
				FileUtil.setText(metadata, sb.toString());
			}
			catch (Exception ex) { ex.printStackTrace(); }
		}

		public void saveMetadataXML(File dir) {
			try {
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement(dir.getName());
				doc.appendChild(root);
				
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
				}
				File metadata = new File(dir, "metadata.xml");
				FileUtil.setText(metadata, XmlUtil.toPrettyString(doc));
			}
			catch (Exception ex) { ex.printStackTrace(); }
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
	
}