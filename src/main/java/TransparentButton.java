package custom;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.ImageIcon;
import java.awt.Graphics;
import java.awt.Color;

public class TransparentButton extends JButton
{
	private Color color = null;

	public TransparentButton(ImageIcon icon)
	{
		super(icon);
		super.setContentAreaFilled(false);

		color = new Color(255, 255, 255, 0);

		setBackground(color);
		setBorder(null);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(color);
		g.fillRect(0, 0, getWidth(), getHeight());
		super.paintComponent(g);
	}
}
