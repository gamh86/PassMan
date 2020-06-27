package custom;

import javax.swing.JFrame;

public class GenericFrame extends JFrame
{
	public GenericFrame()
	{
		super();

		setGenericProperties();
	}

	public GenericFrame(String t)
	{
		super(t);

		setGenericProperties();
	}

	public GenericFrame(int w, int h)
	{
		super();

		setSize(w, h);
		setGenericProperties();
	}

	private void setGenericProperties()
	{
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(null);
		setResizable(false);
	}
}
