package custom;

import javax.swing.JTextField;
//import javax.swing.border;
import javax.swing.BorderFactory;
//import javax.swing.EtchedBorder;
import java.awt.Color;
import java.awt.Font;

public class DetailsTextField extends JTextField
{
	public DetailsTextField(String s)
	{
		super(s);

		//setBackground(new Color(225, 225, 225));
		setFont(new Font("Courier New", Font.ITALIC, 17));
		setEditable(false);
		setBorder(BorderFactory.createLoweredBevelBorder());
	}
}
