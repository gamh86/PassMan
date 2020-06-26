package custom;

import javax.swing.JTextField;
import java.awt.Font;

public class GenericTextField extends JTextField
{
	private Font font = new Font("Courier New", Font.PLAIN, 19);

	public GenericTextField(String s)
	{
		super(s);

		setFont(font);
		setComponentPopupMenu(new RightClickPopup().getMenu());
	}
}
