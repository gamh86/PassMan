package custom;

import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

public class TransparentTextField extends JTextField
{
	private final int TF_WIDTH = 400;
	private final int TF_HEIGHT = 27;
	private final Color color = new Color(255, 255, 255, 0);

	public TransparentTextField(String s)
	{
		super(s);

		setBorder(null);
		setFont(new Font("Times New Roman", Font.ITALIC, 18));
		setPreferredSize(new Dimension(TF_WIDTH, TF_HEIGHT));
		setEditable(false);
		setComponentPopupMenu(new RightClickPopup().getMenu());
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(color);
		g.fillRect(0, 0, getWidth(), getHeight());
		super.paintComponent(g);
	}
}
