package custom;

import javax.swing.JTextField;
import java.awt.Font;

public class GenericTextField extends JTextField
{
	private Font font = new Font("Courier New", Font.PLAIN, 16);

	public GenericTextField(String s)
	{
		super(s);

		setFont(font);
		setComponentPopupMenu(new RightClickPopup().getMenu());
	}

	public GenericTextField(String s, int fontStyle)
	{
		super(s);

		Font _font = new Font("Courier New", fontStyle, 16);
		setFont(_font);
		setComponentPopupMenu(new RightClickPopup().getMenu());
	}
}
