package renderer;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.Color;
import java.awt.Component;

public class PTableCellRenderer implements TableCellRenderer
{
	public static final DefaultTableCellRenderer DEFAULT = new DefaultTableCellRenderer();

	public PTableCellRenderer() { }

	@Override
	public Component getTableCellRendererComponent(
		JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
	{
		Component renderer = DEFAULT.getTableCellRendererComponent(
			table, value, isSelected, hasFocus, row, column);

		Color fground = null;
		Color bground = null;

		if (true == isSelected)
		{
			fground = Color.BLACK;
			bground = new Color(230, 247, 255);
		}
		else
		{
			if (0 == (row & 1))
			{
				fground = Color.BLACK;
				bground = Color.WHITE;
			}
			else
			{
				fground = Color.BLACK;
				bground = new Color(230, 230, 230);
			}
		}

		renderer.setForeground(fground);
		renderer.setBackground(bground);

		return renderer;
	}
}
