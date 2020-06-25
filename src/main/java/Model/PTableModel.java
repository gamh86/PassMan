package model;

import passworddata.*; // for PasswordEntry

import javax.swing.*;
import javax.swing.table.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class PTableModel extends DefaultTableModel
{
	public PTableModel(String[] colNames, int r)
	{
		super(colNames, r);
	}

	@Override
	public Class getColumnClass(int c)
	{
		return (getValueAt(0, c).getClass());
	}
}
