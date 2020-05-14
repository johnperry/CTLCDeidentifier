/*-----------------------------------------------------------------
*  This source software is released under the terms of the
*  Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
*------------------------------------------------------------------*/

package org.jp.deidentifier;

import java.awt.*;
import javax.swing.*;

/**
 * The base JPanel for all launcher tabs.
 */
public class BasePanel extends JPanel implements Scrollable{

	static Color bgColor = Configuration.getInstance().background;
	static Color titleColor = new Color(0x2977b9);
	private boolean trackWidth = true;

	public BasePanel() {
		super();
		setLayout(new BorderLayout());
		setBackground(bgColor);
	}

	public void setTrackWidth(boolean trackWidth) { this.trackWidth = trackWidth; }
	public boolean getScrollableTracksViewportHeight() { return false; }
	public boolean getScrollableTracksViewportWidth() { return trackWidth; }
	public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 30; }
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 30; }
}
