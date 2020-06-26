package custom;

import javax.swing.JButton;
import javax.swing.ImageIcon;
import java.awt.Color;

public class TransparentButton extends JButton
{
	public TransparentButton(ImageIcon icon)
	{
		super(icon);

		setBackground(new Color(240, 240, 240));
		setBorder(null);
	}
}
