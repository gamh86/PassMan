package custom;

import javax.swing.JLabel;
import java.awt.Font;

public class GenericLabel extends JLabel
{
	private Font font = new Font("Courier New", Font.PLAIN, 17);

	public GenericLabel(String s)
	{
		super(s);

		setFont(font);
	}
}
