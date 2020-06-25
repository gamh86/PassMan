package renderer;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.Color;
import java.awt.Component;

public class PTableCellRenderer implements TableCellRenderer
{
	public static final DefaultTableCellRenderer DEFAULT = new DefaultTableCellRenderer();

	Color colorBackgroundOdd = new Color(240, 240, 240);
	Color colorBackgroundEven = Color.WHITE;
	Color colorSelectedCell = new Color(230, 247, 255);
	Color bground = null;
	Color fground = null;

	public PTableCellRenderer()
	{
	}

	@Override
	public Component getTableCellRendererComponent(
		JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
	{
		Component renderer = DEFAULT.getTableCellRendererComponent(
			table, value, isSelected, hasFocus, row, column);

		//Color colorFirstColumnForeground = Color.ORANGE;
		//Color colorSelectedCellFirstColumn = new Color(202, 252, 196);

		if (true == isSelected)
		{
			if (0 == column)
				fground = Color.RED;
			else
				fground = Color.BLACK;

			bground = colorSelectedCell;
		}
		else
		{
			switch(row & 1)
			{
				case 0:

					fground = Color.BLACK;
					bground = Color.WHITE;
					break;

				default:

					fground = Color.BLACK;
					bground = colorBackgroundOdd;
			}
		}

		renderer.setForeground(fground);
		renderer.setBackground(bground);

		return renderer;
	}
}
