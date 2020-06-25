package custom;

import javax.swing.JLabel;
import java.awt.Font;

public class SelectedDetailsLabel extends JLabel
{
	public SelectedDetailsLabel(String s)
	{
		super(s);

		setFont(new Font("Tahoma", Font.BOLD, 19));
	}
}
