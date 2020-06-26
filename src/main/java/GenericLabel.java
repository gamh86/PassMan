package custom;

import javax.swing.JLabel;
import java.awt.Font;

public class GenericLabel extends JLabel
{
	private Font font = new Font("Tahoma", Font.BOLD, 18);

	public GenericLabel(String s)
	{
		super(s);

		setFont(font);
	}
}
