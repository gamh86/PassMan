package custom;

import javax.swing.JButton;
import javax.swing.ImageIcon;
import java.awt.Color;

public class GenericButton extends JButton
{
	private Color color = Color.WHITE;

	public GenericButton(ImageIcon icon)
	{
		super(icon);

		setBackground(color);
	}
}
