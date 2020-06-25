package custom;

import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.*;
import javax.swing.text.*;

public class RightClickPopup extends JPanel
{
	private JPopupMenu menu = null;

	public RightClickPopup()
	{
		menu = new JPopupMenu();
		Action copy = new DefaultEditorKit.CopyAction();
		Action paste = new DefaultEditorKit.PasteAction();

		copy.putValue(Action.NAME, "Copy");
		paste.putValue(Action.NAME, "Paste");

/*
		if (null == currentLanguage) // default to English
		{
			copy.putValue(Action.NAME, "Copy");
			paste.putValue(Action.NAME, "Paste");
		}
		else
		{
			copy.putValue(Action.NAME, currentLanguage.get(STRING_COPY));
			paste.putValue(Action.NAME, currentLanguage.get(STRING_PASTE));
		}
*/

		copy.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control C"));
		paste.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control V"));

		menu.add(copy);
		menu.add(paste);
	}

	public JPopupMenu getMenu()
	{
		return menu;
	}
}
