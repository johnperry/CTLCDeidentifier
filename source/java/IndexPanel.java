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
import org.rsna.util.FileUtil;
import org.rsna.ui.RowLayout;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;  

/**
 * A JPanel that provides a user interface for searching the Index.
 */
public class IndexPanel extends JPanel implements ActionListener {

	private SearchPanel searchPanel;
	private ListPanel listPanel;
	private FooterPanel footerPanel;
	Color background;
	JPanel currentPanel = null;

	/**
	 * Class constructor.
	 */
    public IndexPanel() {
		super();
		Configuration config = Configuration.getInstance();
		setLayout(new BorderLayout());
		background = config.background;
		setBackground(background);

		searchPanel = new SearchPanel();
		listPanel = new ListPanel();
		footerPanel = new FooterPanel();
		footerPanel.search.addActionListener(this);
		footerPanel.list.addActionListener(this);
		footerPanel.save.addActionListener(this);
		searchPanel.searchField.addActionListener(this);
		this.add(searchPanel, BorderLayout.CENTER);
		this.add(footerPanel, BorderLayout.SOUTH);
		currentPanel = searchPanel;
	}
	
	public void setFocus() {
		searchPanel.searchField.requestFocus();
	}		

	/**
	 * Implementation of the ActionListener for the Save Changes button.
	 * @param event the event.
	 */
    public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(footerPanel.search) || source.equals(searchPanel.searchField)) {
			if (!currentPanel.equals(searchPanel)) {
				this.remove(listPanel);
				this.add(searchPanel, BorderLayout.CENTER);
				currentPanel = searchPanel;
				footerPanel.save.setEnabled(false);
				revalidate();
			}
			searchPanel.search();
		}
		else if (source.equals(footerPanel.list)) {
			if (!currentPanel.equals(listPanel)) {
				this.remove(searchPanel);
				this.add(listPanel, BorderLayout.CENTER);
				currentPanel = listPanel;
				footerPanel.save.setEnabled(true);
				revalidate();
			}
			listPanel.list();
		}
		else if (source.equals(footerPanel.save)) {
			listPanel.save();
		}
	}
	
	class HeaderPanel extends Panel {
		public HeaderPanel(String title, int marginTop, int marginBottom) {
			super();
			setBackground(background);
			Box box = Box.createVerticalBox();
			JLabel panelTitle = new JLabel(title);
			panelTitle.setFont( new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 18 ) );
			panelTitle.setForeground(Color.BLUE);
			box.add(Box.createVerticalStrut(marginTop));
			box.add(panelTitle);
			box.add(Box.createVerticalStrut(marginBottom));
			this.add(box);
		}		
	}
	
	class SearchPanel extends JPanel {
		public JTextField searchField = new JTextField("", 20);
		JLabel searchLabel = new JLabel("Trial PatientName: ");
		JLabel nameLabel = new JLabel("Original PatientName: ");
		JLabel idLabel = new JLabel("Original PatientID: ");
		JLabel nameResult = new JLabel("???");
		JLabel idResult = new JLabel("???");
		JPanel centerPanel;
		public SearchPanel() {
			super();
			setBackground(background);
			setLayout(new BorderLayout());
			
			add(new HeaderPanel("Search the Patient Index", 50, 30), BorderLayout.NORTH);
			java.awt.Font sans = new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 16 );
			java.awt.Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 16 );
			searchField.setFont( mono );
			searchLabel.setFont( sans );
			nameLabel.setFont( sans );
			idLabel.setFont( sans );
			nameResult.setFont( mono );
			idResult.setFont( mono );

			centerPanel = new JPanel();
			centerPanel.setLayout(new RowLayout());
			centerPanel.setBackground(background);
			
			centerPanel.add(searchLabel);
			centerPanel.add(searchField);
			centerPanel.add(RowLayout.crlf());

			centerPanel.add(Box.createVerticalStrut(15));
			centerPanel.add(RowLayout.crlf());
			
			centerPanel.add(nameLabel);
			centerPanel.add(nameResult);
			centerPanel.add(RowLayout.crlf());

			centerPanel.add(idLabel);
			centerPanel.add(idResult);
			centerPanel.add(RowLayout.crlf());
			
			Box hBox = Box.createHorizontalBox();			
			hBox.add(Box.createHorizontalGlue());
			hBox.add(centerPanel);
			hBox.add(Box.createHorizontalGlue());
			add(hBox, BorderLayout.CENTER);
		}
		public void search() {
			String key = searchField.getText();
			PatientIndexEntry entry = Index.getInstance().getInvEntry(key);
			if (entry != null) {
				nameResult.setText(entry.name);
				idResult.setText(entry.id);
			}
			else {
				nameResult.setText("???");
				idResult.setText("???");
			}				
		}
	}

	class ListPanel extends JPanel {
		JScrollPane jsp;
		java.awt.Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 12 );
		java.awt.Font mono18 = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 18 );
		String[] columnNames = new String[] {
			"Trial-PatientName",
			"Trial-PatientID",
			"PHI-PatientName",
			"PHI-PatientID"
		};
		String[] spreadsheetColumnNames = new String[] {
			"Trial-PatientName",
			"Trial-PatientID",
			"PHI-PatientName",
			"PHI-PatientID",
			"Trial-StudyDate",
			"Trial-Accession",
			"PHI-StudyDate",
			"PHI-Accession"
		};
		JFileChooser chooser = null;
		int margin = 15;

		public ListPanel() {
			super();
			setBackground(background);
			setLayout(new BorderLayout());
			add(new HeaderPanel("Patient Index List", 10, 10), BorderLayout.NORTH);
			jsp = new JScrollPane();
			add(jsp, BorderLayout.CENTER);
			chooser = new JFileChooser();
			File dir = new File(System.getProperty("user.dir"));
			chooser.setSelectedFile(new File(dir,"Index.xlsx"));
		}
		public void list() {
			PatientIndexEntry[] entries = Index.getInstance().listPatientIndex();
			JPanel panel = new JPanel();
			panel.setLayout(new RowLayout(20,5));
			panel.setBackground(background);
			panel.add(Box.createHorizontalStrut(margin));
			for (int i=0; i<columnNames.length; i++) {
				panel.add(new ColumnLabel(columnNames[i]));
			}
			panel.add(RowLayout.crlf());
			for (int i=0; i<entries.length; i+=2) {
				if ((entries[i] != null) && (entries[i+1] != null) 
					 && (entries[i].name != null) && (entries[i+1].name != null)
					   && (entries[i].id != null) && (entries[i+1].id != null)) {
							panel.add(Box.createHorizontalStrut(margin));
							panel.add(new Label(entries[i+1].name));
							panel.add(new Label(entries[i+1].id));
							panel.add(new Label(entries[i].name));
							panel.add(new Label(entries[i].id));
							panel.add(RowLayout.crlf());
				}
				else {
					if (entries[i] == null) System.out.println("ListPanel.list: entry["+i+"] == null");
					else {
						if (entries[i].name == null) System.out.println("ListPanel.list: entry["+i+"].name == null");
						if (entries[i].id == null) System.out.println("ListPanel.list: entry["+i+"].id == null");
					}
					if (entries[i+1] == null) System.out.println("ListPanel.list: entry["+(i+1)+"] == null");
					else {
						if (entries[i+1].name == null) System.out.println("ListPanel.list: entry["+(i+1)+"].name == null");
						if (entries[i+1].id == null) System.out.println("ListPanel.list: entry["+(i+1)+"].id == null");
					}
				}
			}
			jsp.setViewportView(panel);
			revalidate();
		}
		public void save() {
			Index index = Index.getInstance();
			try {
				PatientIndexEntry[] entries = index.listPatientIndex();
				Workbook wb = new XSSFWorkbook();
				Sheet sheet = wb.createSheet("Index");
				Row row = sheet.createRow((short)0);
				CellStyle style = wb.createCellStyle();
				org.apache.poi.ss.usermodel.Font font = wb.createFont();
				font.setBold(true);
				style.setFont(font);
				for (int i=0; i<spreadsheetColumnNames.length; i++) {
					Cell cell = row.createCell(i);
					cell.setCellValue(spreadsheetColumnNames[i]);
					cell.setCellStyle(style);
				}		
				font.setBold(false);
				style.setFont(font);
				short rowNumber = 1;
				for (int i=0; i<entries.length; i+=2) {
					
					//Put in the patient
					row = sheet.createRow(rowNumber++); // was (short)(i/2+1)
					addCell(row, 0, entries[i+1].name, style);
					addCell(row, 1, entries[i+1].id, style);
					addCell(row, 2, entries[i].name, style);
					addCell(row, 3, entries[i].id, style);
					
					//Now list the studies for the patient
					Study[] studies = index.listStudiesFor(entries[i].id);
					for (Study study : studies) {
						row = sheet.createRow(rowNumber++); 
						addCell(row, 4, study.anonDate, style);
						addCell(row, 5, study.anonAccession, style);
						addCell(row, 6, study.phiDate, style);
						addCell(row, 7, study.phiAccession, style);
					}
				}
				for (int i=0; i<8; i++) sheet.autoSizeColumn(i);
				
				if (chooser.showSaveDialog(this) == chooser.APPROVE_OPTION) {
					File outputFile = chooser.getSelectedFile();
					FileOutputStream fos = new FileOutputStream(outputFile);
					wb.write(fos);
					fos.close();
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		private void addCell(Row row, int k, String text, CellStyle style) {
			Cell cell = row.createCell(k);
			cell.setCellValue(text);
			cell.setCellStyle(style);
		}
		class Label extends JLabel {
			public Label(String text) {
				super(text);
				setFont(mono);
			}
		}
		class ColumnLabel extends JLabel {
			public ColumnLabel(String text) {
				super(text);
				setFont(mono18);
			}
		}
	}
	
	class FooterPanel extends JPanel {
		public JButton search;
		public JButton list;
		public JButton save;
		public FooterPanel() {
			super();
			setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			setLayout(new FlowLayout());
			setBackground(background);
			search = new JButton("Search");
			add(search);
			add(Box.createHorizontalStrut(15));
			list = new JButton(" List ");
			add(list);
			add(Box.createHorizontalStrut(15));
			save = new JButton(" Save ");
			save.setEnabled(false);
			add(save);
		}
	}
	
}