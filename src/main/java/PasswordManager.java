import aescrypt.*;
import backup.*; // for our GDriveBackup class
import custom.*;
import languages.*;
import renderer.*;
import model.*;
import passworddata.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.apache.commons.codec.binary.Base64;

import java.io.File;
import java.io.FileReader; /* for reading streams of characters */
import java.io.FileInputStream; /* for reading raw bytes (binary data) */
import java.io.FileOutputStream; /* for writing raw bytes to file */
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.BufferedReader;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.lang.Math;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap; // sorted keys
import java.util.regex.*;
import java.util.Random;
import java.util.TimeZone;
import java.util.Set;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.Vector;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection; // for copying text to clipboard
import java.awt.event.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import javax.swing.text.*;
import javax.swing.plaf.metal.MetalScrollBarUI;
import javax.swing.plaf.ColorUIResource;
import javax.swing.DefaultListSelectionModel;
//import javax.swing.table.DefaultTableModel;

/* TODO

	Fix bug related to the elements from the doinitialconfig
	frame (same main JFrame extended by our class) remaining
	in the window after setupGUI() despite the fact that
	in doinitialconfiguration(), they are explicitly removed
	and dispose() is called on the window... (exact same
	process takes place after the initial language configuration
	is finished: components removed, frame disposed, and
	this problem does _not_ occur in that case...

	Deal with window resizes.
*/

@JsonIgnoreProperties(ignoreUnknown = true)
class RuntimeOptions
{
	@JsonProperty
	String language;

	public void setLanguage(String l) { language = l; }
	public String getLanguage() { return language; }
}

class PDefaultMutableTreeNode extends DefaultMutableTreeNode
{
	private PasswordEntry entry;

	public PDefaultMutableTreeNode(PasswordEntry e)
	{
		entry = e;
	}

	public PasswordEntry getPasswordEntry()
	{
		return entry;
	}

	@Override
	public String toString()
	{
		return entry.getEmail();
	}
}

class ExcelAdapter implements ActionListener
{
	private String rowString;
	private String value;
	private Clipboard clipboard;
	private StringSelection selected;
	private JTable table;

	public ExcelAdapter(JTable t)
	{
		this.table = t;
		final KeyStroke copy = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK, false);
		table.registerKeyboardAction(this, "Copy", copy, JComponent.WHEN_FOCUSED);
		clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
	}

	@Override
	public void actionPerformed(ActionEvent event)
	{
		final String actionCommand = event.getActionCommand();

		if (actionCommand.equals("Copy"))
		{
			final int rowCount = table.getSelectedRowCount();
			final int colCount = table.getSelectedColumnCount();
			final int[] rows = table.getSelectedRows();
			final int[] cols = table.getSelectedColumns();

			if (rowCount > 1 || colCount > 1)
			{
				JOptionPane.showMessageDialog(
					null,
					null,
					"Only one cell can be copied at a time",
					JOptionPane.ERROR_MESSAGE
				);

				return;
			}

			selected = new StringSelection((String)table.getValueAt(rows[0], cols[0]));
			clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(selected, null);
		}
	}
}

public class PasswordManager extends JFrame
{
	private static final String VERSION = "1.0.1";
	private String passwordDirectory = null;
	private String passwordFile = null;
	private String configFile = null;

	private ObjectMapper mapper;

	private String userHome = null;
	private String userName = null;

	private boolean passwordFileUnlocked = false;
	private String password = null;

	private boolean fileContentsCached = false;
	private String fContents = null;
	private TreeMap<String,ArrayList<PasswordEntry>> passwordEntries;

	private static final String dirName = ".PassMan";
	private static final String appName = "Password Manager";
	private static final String ICONS_DIR = "src/main/resources/icons";

	private final int SCREEN_WIDTH;
	private final int SCREEN_HEIGHT;

	private static final long MILLIS_PER_DAY = 86400000;
	private static final int DEFAULT_PASSWORD_LIFETIME = 72; // days

	private static final String DATE_FORMAT = "dd-MM-YYYY HH:mm:ss";

	private static final int IDX_PASSWORD_ID = 0;
	private static final int IDX_EMAIL = 1;
	private static final int IDX_USERNAME = 2;
	private static final int IDX_PASSWORD = 3;
	private static final int IDX_PASSWORD_LENGTH = 4;
	private static final int IDX_AGE = 5;
	private static final int IDX_UNIQUE_ID = 6;

	private Languages languages = null;

/*
 * GUI components that need to be global so we can
 * reset them, for example, after changing langauge.
 */
	// in setupGUI()
	private JTextArea taAppName = null;

	// in showSettings()
	private JLabel labelMasterPassword = null;
	private JLabel labelLanguage = null;
	private JLabel labelCharacterSet = null;

	private JTable table = null;
	private JTree tree = null;
	private DefaultTableModel model = null;
	private DefaultTreeModel treeModel = null;
	private int currentlySelectedRow = -1;

	private JTextField tfSelectedPassword = null;
	private JTextField tfSelectedUsername = null;
	private JTextField tfSelectedEmail = null;
	private JTextField tfSelectedPasswordLen = null;
	private JTextField tfSelectedPasswordAgeInDays = null;
	private JTextField tfSelectedPasswordUniqueId = null;

/*
 * Various global fonts/colours
 */
	private String fontName = "Times New Roman";
	private Font fontLabel = new Font(fontName, Font.PLAIN, 18);
	private Font fontInput = new Font(fontName, Font.PLAIN, 20);
	private Font fontDetails = new Font("Times New Roman", Font.PLAIN, 18);
	private Font fontPasswordId = new Font("Verdana", Font.BOLD, 18);
	private Font fontDialog = new Font("Verdana", Font.PLAIN, 20);
	private Font fontInfo = new Font("Times New Roman", Font.PLAIN, 16);
	private Font fontLargePrompt = new Font("Verdana", Font.BOLD, 24);
	private Font fontTextField = new Font("Verdana", Font.PLAIN, 18);

	private Color colorButtonSelected = new Color(230, 243, 255);
	private Color colorButtonDeselected = new Color(215, 215, 215);
	private Color colorConfirm = Color.WHITE;//new Color(220, 220, 220);
	private Color colorFrame = new Color(240, 240, 240);
	private Color colorLabel = new Color(240, 240, 240);
	private Color colorButton = new Color(255, 255, 255);
	private Color colorScrollPane = new Color(242, 242, 242);

	private Map<Integer,String> currentLanguage = null;

/*
 * Paths to icons.
 */
	private static final String analysis128 = ICONS_DIR + "/analysis_128x128.png";
	private static final String locked128 = ICONS_DIR + "/locked_128x128.png";
	private static final String secret128 = ICONS_DIR + "/secret_128x128.png";
	private static final String success128 = ICONS_DIR + "/success_128x128.png";
	private static final String unlocked128 = ICONS_DIR + "/unlocked_128x128.png";
	private static final String settings128 = ICONS_DIR + "/settings_128x128.png";
	private static final String safe128 = ICONS_DIR + "/safe_128x128.png";
	private static final String shield128 = ICONS_DIR + "/shield_128x128.png";

	private static final String add80 = ICONS_DIR + "/add_80x80.png";
	private static final String bin80 = ICONS_DIR + "/bin_80x80.png";
	private static final String change80 = ICONS_DIR + "/change_80x80.png";
	private static final String view80 = ICONS_DIR + "/view_80x80.png";

	private static final String locked64 = ICONS_DIR + "/locked_64x64.png";
	private static final String add64 = ICONS_DIR + "/add_64x64.png";
	private static final String bin64 = ICONS_DIR + "/bin_64x64.png";
	private static final String change64 = ICONS_DIR + "/change_64x64.png";
	private static final String edit64 = ICONS_DIR + "/edit_64x64.png";
	private static final String view64 = ICONS_DIR + "/view_64x64.png";
	private static final String cog64 = ICONS_DIR + "/cog_64x64.png";
	private static final String confirm64 = ICONS_DIR + "/confirm_64x64.png";
	private static final String search64 = ICONS_DIR + "/search_64x64.png";

	private static final String add32 = ICONS_DIR + "/add_32x32.png";
	private static final String bin32 = ICONS_DIR + "/bin_32x32.png";
	private static final String change32 = ICONS_DIR + "/change_32x32.png";
	private static final String cog32 = ICONS_DIR + "/cog_32x32.png";
	private static final String edit32 = ICONS_DIR + "/edit_32x32.png";
	private static final String view32 = ICONS_DIR + "/view_32x32.png";
	private static final String info32 = ICONS_DIR + "/info_32x32.png";
	private static final String confirm32 = ICONS_DIR + "/confirm_32x32.png";

	private static final String copy32 = ICONS_DIR + "/copy_32x32.png";
	private static final String upload32 = ICONS_DIR + "/upload_32x32.png";
	private static final String download32 = ICONS_DIR + "/download_32x32.png";
	private static final String drive32 = ICONS_DIR + "/drive_32x32.png";
	private static final String search32 = ICONS_DIR + "/search_32x32.png";
	private static final String unlocked32 = ICONS_DIR + "/unlocked_32x32.png";
	private static final String spanner32 = ICONS_DIR + "/spanner_32x32.png";

	private static final String warning32 = ICONS_DIR + "/warning_32x32.png";
	private static final String ok32 = ICONS_DIR + "/ok_32x32.png";

	private static ImageIcon iconAnalysis128 = null;
	private static ImageIcon iconLocked128 = null;
	private static ImageIcon iconSecret128 = null;
	private static ImageIcon iconSuccess128 = null;
	private static ImageIcon iconUnlocked128 = null;
	private static ImageIcon iconSafe128 = null;
	private static ImageIcon iconSettings128 = null;
	private static ImageIcon iconShield128 = null;

	private static ImageIcon iconAdd80 = null;
	private static ImageIcon iconBin80 = null;
	private static ImageIcon iconChange80 = null;
	private static ImageIcon iconView80 = null;

	private static ImageIcon iconLocked64 = null;
	private static ImageIcon iconAdd64 = null;
	private static ImageIcon iconBin64 = null;
	private static ImageIcon iconChange64 = null;
	private static ImageIcon iconEdit64 = null;
	private static ImageIcon iconView64 = null;
	private static ImageIcon iconCog64 = null;
	private static ImageIcon iconConfirm64 = null;
	private static ImageIcon iconSearch64 = null;

	private static ImageIcon iconAdd32 = null;
	private static ImageIcon iconBin32 = null;
	private static ImageIcon iconChange32 = null;
	private static ImageIcon iconCog32 = null;
	private static ImageIcon iconEdit32 = null;
	private static ImageIcon iconView32 = null;
	private static ImageIcon iconInfo32 = null;
	private static ImageIcon iconUnlocked32 = null;
	private static ImageIcon iconConfirm32 = null;

	private static ImageIcon iconCopy32 = null;
	private static ImageIcon iconUpload32 = null;
	private static ImageIcon iconDownload32 = null;
	private static ImageIcon iconGDrive32 = null;
	private static ImageIcon iconSearch32 = null;
	private static ImageIcon iconSpanner32 = null;
	private static ImageIcon iconWarning32 = null;
	private static ImageIcon iconOk32 = null;

/*
	private static final byte[] asciiChars = {
		'!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
		'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';', '<', '=', '>', '?',
		'@', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
		'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '[', '\\', ']', '^', '_',
		'`', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
		'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '{', '}', '~'
	};
*/

	private static ArrayList<Byte> characterSet = null;
	private boolean[] characterStatusMap = null;

	private void getImages()
	{
		iconAnalysis128 = new ImageIcon(analysis128);
		iconLocked128 = new ImageIcon(locked128);
		iconSecret128 = new ImageIcon(secret128);
		iconSuccess128 = new ImageIcon(success128);
		iconUnlocked128 = new ImageIcon(unlocked128);
		iconSafe128 = new ImageIcon(safe128);
		iconSettings128 = new ImageIcon(settings128);
		iconShield128 = new ImageIcon(shield128);

		iconAdd80 = new ImageIcon(add80);
		iconBin80 = new ImageIcon(bin80);
		iconChange80 = new ImageIcon(change80);
		iconView80 = new ImageIcon(view80);

		iconLocked64 = new ImageIcon(locked64);
		iconAdd64 = new ImageIcon(add64);
		iconBin64 = new ImageIcon(bin64);
		iconChange64 = new ImageIcon(change64);
		iconEdit64 = new ImageIcon(edit64);
		iconView64 = new ImageIcon(view64);
		iconCog64 = new ImageIcon(cog64);
		iconConfirm64 = new ImageIcon(confirm64);
		iconSearch64 = new ImageIcon(search64);

		iconAdd32 = new ImageIcon(add32);
		iconBin32 = new ImageIcon(bin32);
		iconChange32 = new ImageIcon(change32);
		iconCog32 = new ImageIcon(cog32);
		iconEdit32 = new ImageIcon(edit32);
		iconView32 = new ImageIcon(view32);
		iconInfo32 = new ImageIcon(info32);
		iconUnlocked32 = new ImageIcon(unlocked32);
		iconConfirm32 = new ImageIcon(confirm32);

		iconCopy32 = new ImageIcon(copy32);
		iconUpload32 = new ImageIcon(upload32);
		iconDownload32 = new ImageIcon(download32);
		iconGDrive32 = new ImageIcon(drive32);
		iconSearch32 = new ImageIcon(search32);
		iconSpanner32 = new ImageIcon(spanner32);

		iconWarning32 = new ImageIcon(warning32);
		iconOk32 = new ImageIcon(ok32);
	}

	private void showCharacterSet()
	{
		JFrame frame = new JFrame();
		SpringLayout spring = new SpringLayout();
		Container contentPane = frame.getContentPane();
		final int windowWidth = 850;
		final int windowHeight = 450;
		final int buttonContainerWidth = (windowWidth - 50);
		final int buttonContainerHeight = ((windowHeight>>2)*3);
		final int leftOffset = ((windowWidth-buttonContainerWidth)>>1);
		boolean on = true;

		contentPane.setLayout(spring);

		frame.setSize(windowWidth, windowHeight);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JPanel panelTop = new JPanel(new FlowLayout());
		JPanel panelButtonContainer = new JPanel(new FlowLayout());

		panelButtonContainer.setPreferredSize(new Dimension(buttonContainerWidth, buttonContainerHeight));

		JTextArea taCharacterSetInfo = new JTextArea(currentLanguage.get(languages.STRING_TOGGLE_CHARACTER_SET));

		taCharacterSetInfo.setEditable(false);
		taCharacterSetInfo.setBorder(null);
		taCharacterSetInfo.setBackground(colorFrame);
		taCharacterSetInfo.setFont(fontLargePrompt);

		spring.putConstraint(SpringLayout.WEST, panelTop, 30, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, panelTop, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, panelButtonContainer, 20, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, panelButtonContainer, 20, SpringLayout.SOUTH, panelTop);

		panelTop.add(taCharacterSetInfo);

		for (byte b = (byte)0x21; b < (byte)0x7e; ++b)
		{
			if (b == (byte)'|')
				continue;

			on = characterStatusMap[(int)(b - (byte)0x21)];

		/*
		 * No string object constructor for creation of
		 * a string from a single byte. So use an array
		 * of length 1.
		 */
			byte[] _b = new byte[1];

			_b[0] = b;

			JButton button = new JButton(new String(_b));
			button.addActionListener(new characterButtonListener());
			button.setBackground(true == on ? colorButtonSelected : colorButtonDeselected);
			panelButtonContainer.add(button);
		}

		contentPane.add(panelTop, BorderLayout.NORTH);
		contentPane.add(panelButtonContainer, BorderLayout.CENTER);

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	/**
	 * Fill the character set with characters in the range
	 * (0x21,0x7e], excluding any unwanted characters
	 * specified by the user.
	 */
	private void fillCharacterSet()
	{
		boolean on = true;
		characterSet = new ArrayList<Byte>();

		for (byte b = (byte)0x21; b < (byte)0x7e; ++b)
		{
			on = characterStatusMap[(int)(b - (byte)0x21)];

			if (false == on)
				continue;

			characterSet.add(b);
		}

		return;
	}

	private void fillCharacterStatusMap()
	{
		int rangeSize = (int)(0x7e - 0x21);

		for (int i = 0; i < rangeSize; ++i)
		{
			characterStatusMap[i] = true;
		}

		characterStatusMap[(int)((byte)'|' - (byte)0x21)] = false;
	}

	private class characterButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent event)
		{
			JButton clickedButton = (JButton)event.getSource();
			byte selectedCharacter = clickedButton.getText().getBytes()[0];
			int index = (int)(selectedCharacter - (byte)0x21);
			boolean currentStatus = characterStatusMap[index];
			boolean newStatus;

			if (selectedCharacter == (byte)'|') // this can never be used
				return;

			newStatus = !currentStatus;
			clickedButton.setBackground(true == newStatus ? colorButtonSelected : colorButtonDeselected);
			characterStatusMap[index] = newStatus;

			fillCharacterSet();
		}
	}

	private void showInfoDialog(String msg)
	{
		JLabel containerMessage = new JLabel(msg);

		containerMessage.setFont(fontDialog);
		Object[] options = { currentLanguage.get(languages.STRING_PROMPT_OK) };

		int action = JOptionPane.showOptionDialog(
			null, // frame
			containerMessage,
			"", // title
			1,
			JOptionPane.INFORMATION_MESSAGE,
			null, // icon
			options,
			options[0]);
/*
		JOptionPane.showMessageDialog(
			null, // frame
			containerMessage,
			"",
			JOptionPane.INFORMATION_MESSAGE);
*/
	}

	private void showErrorDialog(String msg)
	{
		JLabel containerMessage = new JLabel(msg);

		containerMessage.setFont(fontDialog);

		JOptionPane.showMessageDialog(
			null, // frame
			containerMessage,
			"",
			JOptionPane.ERROR_MESSAGE);
	}

	private int showQuestionDialog(String question)
	{
		int action = 0;
		JLabel containerMessage = new JLabel(question);

		containerMessage.setFont(fontDialog);
		Object[] options = { currentLanguage.get(languages.STRING_PROMPT_OK), currentLanguage.get(languages.STRING_PROMPT_CANCEL) };

		action = JOptionPane.showOptionDialog(
			null,
			containerMessage,
			"",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE,
			null,
			options,
			options[0]);

		return ++action;
	}

	private void checkPasswordDir()
	{
/*
 * If the directory already exists, nothing happens.
 */
		new File(passwordDirectory).mkdirs();
	}

	private void setPasswordFile(String path)
	{
		passwordFile = path;
	}

	/**
	 * Zero out contents of file.
	 *
	 * @param fOut the fileoutputstream opened with the File object
	 * @param len the length of the about-to-be-replaced password file in bytes.
	 */
	private void overwriteFileContents(FileOutputStream fOut, long len)
	{
		byte[] zeros = new byte[(int)len];
		int pos;

		for (pos = 0; (long)pos < len; ++pos)
			zeros[pos] = 0;

		//fOut.getChannel().truncate(0);

		try
		{
			fOut.write(zeros);
			fOut.flush();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		return;
	}

	/**
	 * Encrypt and write the entries in the list of password entries to disk.
	 */
	private void putPasswordEntries()
	{
		assert(null != passwordEntries);

		String jsonData = null;

		try
		{
			jsonData = mapper.writeValueAsString(passwordEntries);
		}
		catch (JsonProcessingException e1)
		{
			e1.printStackTrace();
			System.err.println(e1.getMessage());

			return;
		}

		try
		{
			AESCrypt aes = new AESCrypt();

			byte[] rawEncrypted = aes.encryptData(jsonData, password);
			File fObj = new File(passwordFile);

			FileOutputStream fOut = new FileOutputStream(fObj, false);

			overwriteFileContents(fOut, fObj.length());

			fOut.getChannel().truncate(0);
			fOut.write(rawEncrypted);
			fOut.flush();
			fOut.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void createPasswordEntriesMap()
	{
		if (false == fileContentsCached || null == fContents)
			getFileContents(passwordFile);

		try
		{
			com.fasterxml.jackson.core.type.TypeReference typeRef = new
				com.fasterxml.jackson.core.type.TypeReference<TreeMap<String,ArrayList<PasswordEntry>>>() {};

			//System.out.println(fContents);
			byte[] data = fContents.getBytes("UTF-8");
			passwordEntries = (TreeMap<String,ArrayList<PasswordEntry>>)mapper.readValue(data, typeRef);
		}
		catch (Exception e)
		{
			System.err.println(e.getMessage());
			e.printStackTrace();
		}

		return;
	}

	/**
	 * Read password file contents and save into global variable fContents
	 *
	 * @param path The path to the password file
	 */
	private void getFileContents(String path)
	{
		assert(null != password);

		try
		{
			AESCrypt aes = new AESCrypt();
			fContents = aes.decryptFile(path, password);
			fileContentsCached = true;
			createPasswordEntriesMap();
			return;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Create a new password using pseudorandomness
	 * from the system entropy pool.
	 */
	public String createNewPassword(int len)
	{
		assert(len > 0 && len <= 100);

		byte[] randBytes = new byte[len];
		byte[] rawPass = new byte[len];
		int nrCharacters = characterSet.size();
		int selectedIndex = 0;

		new Random().nextBytes(randBytes);
		for (int i = 0; i < len; ++i)
		{
			selectedIndex = Math.abs(randBytes[i]) % nrCharacters;
			rawPass[i] = characterSet.get(selectedIndex);
		}

		return new String(rawPass, Charset.forName("UTF-8"));
	}

	/**
	 * Ensure that length entered by the user
	 * contains only valid numeric characters
	 * and that it is within the correct range.
	 */
	private boolean isValidLength(String len)
	{
		try
		{
			int strLen = len.length();
			byte[] rawLen = new byte[strLen];
			int pos = 0;

			rawLen = len.getBytes("UTF-8");

			while (pos < strLen)
			{
				if (rawLen[pos] < (byte)0x30 || rawLen[pos] > (byte)0x39)
					return false;

				++pos;
			}

			int parsedLen = Integer.parseInt(len);

			if (parsedLen < 8 || parsedLen > 100)
				return false;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		return true;
	}

	private boolean isAlphaNumericByte(byte b)
	{
		if ((b > (byte)0x2f && b < (byte)0x3a) ||
			(b > (byte)0x60 && b < (byte)0x7b) ||
			(b > (byte)0x40 && b < (byte)0x5b))
			return true;

		return false;
	}

	private String removeTrailingNewlines(String str)
	{
		byte[] rawStr = null;

		if (str.length() < 2)
			return str;

		try
		{
			rawStr = str.getBytes("UTF-8");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		int pos = rawStr.length - 1;

		while ((rawStr[pos] == (byte)'\r' || rawStr[pos] == (byte)'\n') && pos > 1)
			--pos;

		++pos;

		byte[] rawNew = new byte[pos];

		System.arraycopy(rawStr, 0, rawNew, 0, pos);

		String newStr = new String(rawNew);

		return newStr;
	}

	private String getNextLine(ByteBuffer buf)
	{
		byte[] raw = buf.array();
		int pos = buf.position();
		int start = pos;
		int end = raw.length;

		while (pos < end && raw[pos] != (byte)'\n')
			++pos;

		if (pos >= end)
		{
			return null;
		}

		byte[] rawLine = new byte[(int)(pos - start)];
		System.arraycopy(raw, start, rawLine, 0, (pos - start));
		buf.position(++pos);

		return new String(rawLine);
	}



	/**
	 * XXX - May be better to just show this in the showdetails window...
	 * Show information such as # possible permutations for length.
	 */
/*
	private void doAnalysePassword(PasswordEntry entry)
	{
		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();
		SpringLayout spring = new SpringLayout();
		final int windowWidth = 550;
		final int windowHeight = 600;
		final int labelWidth = 250;
		final int labelHeight = 30;
		final int SECONDS_PER_DAY = (60 * 60 * 24); // mean solar day
		final int SECONDS_PER_WEEK = (SECONDS_PER_DAY * 7);
		final double SECONDS_PER_YEAR = ((double)SECONDS_PER_DAY * 365.25);

		Dimension sizeLabel = new Dimension(labelWidth, labelHeight);

		frame.setSize(windowWidth, windowHeight);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setTitle(currentLanguage.get(languages.STRING_TITLE_PASSWORD_ANALYSIS));

		JLabel containerIcon = new JLabel(iconAnalysis128);

		JLabel labelPermutations = new JLabel(currentLanguage.get(languages.STRING_POSSIBLE_PERMUTATIONS));
		JLabel labelPasswordLen = new JLabel(currentLanguage.get(languages.STRING_PASSWORD_LENGTH2));
		JLabel labelSizeCharacterSet = new JLabel(currentLanguage.get(languages.STRING_SIZE_CHARACTER_SET));
		JLabel labelCrackTime = new JLabel(currentLanguage.get(languages.STRING_CRACK_TIME));

		JLabel _longestLabel = labelPermutations;

		if (labelPasswordLen.getText().length() > _longestLabel.getText().length())
			_longestLabel = labelPasswordLen;

		if (labelCrackTime.getText().length() > _longestLabel.getText().length())
			_longestLabel = labelCrackTime;

		if (labelSizeCharacterSet.getText().length() > _longestLabel.getText().length())
			_longestLabel = labelSizeCharacterSet;

		int offsetLeftTextFields = getStringWidth(_longestLabel.getText()) + 60;

		labelPermutations.setPreferredSize(sizeLabel);
		labelPasswordLen.setPreferredSize(sizeLabel);
		labelSizeCharacterSet.setPreferredSize(sizeLabel);
		labelCrackTime.setPreferredSize(sizeLabel);

		labelPermutations.setFont(fontLabel);
		labelPasswordLen.setFont(fontLabel);
		labelSizeCharacterSet.setFont(fontLabel);
		labelCrackTime.setFont(fontLabel);

		JTextArea taInfo = new JTextArea(currentLanguage.get(languages.STRING_PASSWORD_STRENGTH_INFORMATION));

		taInfo.setEditable(false);
		taInfo.setBackground(contentPane.getBackground());
		taInfo.setFont(fontInfo);
		taInfo.setBorder(null);

	/*
	 * Let's just say, for the sake of argument, that
	 * testing a possible password permutation takes
	 * 5 instructions.
	 *
		Double INSTRUCTIONS_PER_SECOND = new Double("2000000000000.0");
		double INSTRUCTIONS_PER_ATTEMPT = 5.0;
		double ATTEMPTS_PER_SECOND = (INSTRUCTIONS_PER_SECOND / INSTRUCTIONS_PER_ATTEMPT);

		double nrPermutations = Math.pow((double)entry.getCharsetSize(), (double)entry.getPassword().length());

	/*
	 * On average, one crack a password after testing half.
	 *
		double secondsToCrack = (nrPermutations/2) / ATTEMPTS_PER_SECOND;
		double daysToCrack = secondsToCrack / (double)SECONDS_PER_DAY;
		//double weeksToCrack = secondsToCrack / (double)SECONDS_PER_WEEK;
		double yearsToCrack = secondsToCrack / SECONDS_PER_YEAR;

		JTextField tfPermutations = new JTextField(String.format("%6.3e", nrPermutations));
		JTextField tfPasswordLen = new JTextField(String.format("%d", entry.getPassword().length()));
		JTextField tfSizeCharacterSet = new JTextField(String.format("%d", entry.getCharsetSize()));
		JTextField tfCrackTimeSeconds = new JTextField(String.format("%.3e " + currentLanguage.get(languages.STRING_SECONDS), secondsToCrack));
		JTextField tfCrackTimeDays = new JTextField(String.format("%.3e " + currentLanguage.get(languages.STRING_DAYS), daysToCrack));
		JTextField tfCrackTimeYears = new JTextField(String.format("%.3e " + currentLanguage.get(languages.STRING_YEARS), yearsToCrack));

		tfPermutations.setEditable(false);
		tfPermutations.setBorder(null);
		tfPermutations.setFont(fontDetails);

		tfPasswordLen.setEditable(false);
		tfPasswordLen.setBorder(null);
		tfPasswordLen.setFont(fontDetails);

		tfSizeCharacterSet.setEditable(false);
		tfSizeCharacterSet.setBorder(null);
		tfSizeCharacterSet.setFont(fontDetails);

		tfCrackTimeSeconds.setEditable(false);
		tfCrackTimeSeconds.setBorder(null);
		tfCrackTimeSeconds.setFont(fontDetails);

		tfCrackTimeDays.setEditable(false);
		tfCrackTimeDays.setBorder(null);
		tfCrackTimeDays.setFont(fontDetails);

		tfCrackTimeYears.setEditable(false);
		tfCrackTimeYears.setBorder(null);
		tfCrackTimeYears.setFont(fontDetails);

		int north = 40;
		int iconWidth = iconAnalysis128.getIconWidth();
		int iconHeight = iconAnalysis128.getIconHeight();

		spring.putConstraint(SpringLayout.WEST, containerIcon, ((windowWidth>>1) - (iconWidth>>1)), SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, containerIcon, north, SpringLayout.NORTH, contentPane);

		north += iconHeight + 40;

		spring.putConstraint(SpringLayout.WEST, labelPasswordLen, 30, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelPasswordLen, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfPasswordLen, offsetLeftTextFields, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPasswordLen, north, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelSizeCharacterSet, 30, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelSizeCharacterSet, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfSizeCharacterSet, offsetLeftTextFields, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfSizeCharacterSet, north, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelPermutations, 30, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelPermutations, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfPermutations, offsetLeftTextFields, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPermutations, north, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelCrackTime, 30, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelCrackTime, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfCrackTimeSeconds, offsetLeftTextFields, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfCrackTimeSeconds, north, SpringLayout.NORTH, contentPane);

		north += 30;

		spring.putConstraint(SpringLayout.WEST, tfCrackTimeDays, offsetLeftTextFields, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfCrackTimeDays, north, SpringLayout.NORTH, contentPane);

		north += 30;

		spring.putConstraint(SpringLayout.WEST, tfCrackTimeYears, offsetLeftTextFields, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfCrackTimeYears, north, SpringLayout.NORTH, contentPane);

		north += 70;

		spring.putConstraint(SpringLayout.WEST, taInfo, 100, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, taInfo, north, SpringLayout.NORTH, contentPane);

		contentPane.setLayout(spring);

		contentPane.add(containerIcon);
		contentPane.add(labelPasswordLen);
		contentPane.add(tfPasswordLen);
		contentPane.add(labelSizeCharacterSet);
		contentPane.add(tfSizeCharacterSet);
		contentPane.add(labelPermutations);
		contentPane.add(tfPermutations);
		contentPane.add(labelCrackTime);
		contentPane.add(tfCrackTimeSeconds);
		contentPane.add(tfCrackTimeDays);
		contentPane.add(tfCrackTimeYears);
		contentPane.add(taInfo);

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
*/

	private String getMD5(String of)
	{
		MessageDigest md = null;
		String hash = null;
		Base64 base64 = new Base64(true);

		try
		{
			md = MessageDigest.getInstance("MD5");
			hash = base64.encodeBase64URLSafeString(md.digest(of.getBytes("UTF-8")));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.err.println(e.getMessage());

			return null;
		}

		return hash;
	}

	private String getUniqueId(PasswordEntry entry)
	{
		return getMD5(entry.getEmail() + entry.getUsername() + entry.getTimestamp());
	}

	/**
	 * XXX Code Duplication
	 *
	 * We can coalesce this and the showPasswordDetailsForId into a single function
	 * with default values for the three other variables. We can check for null
	 * values and then search the password entry list if need be; otherwise use the
	 * values provided.
	 */
/*
	private void showPasswordDetails(PasswordEntry entry)
	{
		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();
		Font fontDetails = new Font("Times New Roman", Font.PLAIN, 18);
		Font fontLabel = new Font("Verdana", Font.BOLD, 18);

		int passwordLen = entry.getPassword().length();
		int windowWidth = 0;
		int windowHeight = 420;
		final int labelWidth = 200;
		final int labelHeight = 30;
		final int leftOffsetDetails = (labelWidth + 20);

		if (passwordLen < 19)
			windowWidth = Math.max(19, idLen) * 10 + 30;
		else
			windowWidth = passwordLen * 10 + 30;

		windowWidth += leftOffsetDetails + 120;

		int halfWidth = (windowWidth/2);
		int north = 0;

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(windowWidth, 420);
		frame.setTitle(currentLanguage.get(languages.STRING_TITLE_PASSWORD_DETAILS));

		JLabel unlockedContainer = new JLabel(iconUnlocked128);
		SpringLayout spring = new SpringLayout();

		JLabel labelUsername = new JLabel(currentLanguage.get(languages.STRING_USERNAME));
		JLabel labelPass = new JLabel(currentLanguage.get(languages.STRING_PASSWORD));
		JLabel labelWhen = new JLabel(currentLanguage.get(languages.STRING_CREATION_TIME));

		Dimension labelSize = new Dimension(labelWidth, labelHeight);

		JLabel labelCopy = new JLabel(currentLanguage.get(languages.STRING_COPY_PASSWORD));
		labelCopy.setFont(new Font("Times New Roman", Font.ITALIC, 14));

		JButton buttonCopyUsername = new JButton(iconCopy32);
		JButton buttonCopyPassword = new JButton(iconCopy32);

		buttonCopyUsername.setBackground(colorFrame);
		buttonCopyPassword.setBackground(colorFrame);

		buttonCopyUsername.setBorder(null);
		buttonCopyPassword.setBorder(null);

		labelUsername.setFont(fontLabel);
		labelPass.setFont(fontLabel);
		labelWhen.setFont(fontLabel);

		JTextField tfUsername = new JTextField(entry.getUsername());
		JTextField tfPassword = new JTextField(entry.getPassword());

		tfUsername.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfPassword.setComponentPopupMenu(new RightClickPopup().getMenu());

		Date date = new Date(entry.getTimestamp());
		DateFormat dateFormat = new SimpleDateFormat("dd-MM-YYYY HH:mm:ss");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

		JTextField tfWhen = new JTextField(dateFormat.format(date));

		tfUsername.setEditable(false);
		tfPassword.setEditable(false);
		tfWhen.setEditable(false);

		tfUsername.setBorder(null);
		tfPassword.setBorder(null);
		tfWhen.setBorder(null);

		tfUsername.setFont(fontDetails);
		tfPassword.setFont(fontDetails);
		tfWhen.setFont(fontDetails);

		JButton buttonInformation = new JButton(iconInfo32);

		buttonInformation.setBorder(null);
		buttonInformation.setBackground(colorFrame);
		buttonInformation.setPreferredSize(new Dimension(iconInfo32.getIconHeight(), iconInfo32.getIconHeight()));

		final int iconWest = (halfWidth - (iconUnlocked128.getIconWidth()>>1));
		final int leftOffset = 40;
		final int tfGap = 5;
		final int labelFieldGap = 10;
		final int labelOffset = 5;
		final int fieldNextLabelGap = 20;
		final int buttonCopyWest = 15;

		spring.putConstraint(SpringLayout.WEST, unlockedContainer, iconWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, unlockedContainer, 25, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, labelUsername, labelOffset, SpringLayout.WEST, tfUsername);
		spring.putConstraint(SpringLayout.NORTH, labelUsername, fieldNextLabelGap, SpringLayout.SOUTH, unlockedContainer);

		spring.putConstraint(SpringLayout.WEST, tfUsername, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfUsername, labelFieldGap, SpringLayout.SOUTH, labelUsername);

		spring.putConstraint(SpringLayout.EAST, buttonCopyUsername, buttonCopyWest, SpringLayout.EAST, labelUsername);
		spring.putConstraint(SpringLayout.NORTH, buttonCopyUsername, 0, SpringLayout.NORTH, labelUsername);

		spring.putConstraint(SpringLayout.WEST, labelPass, labelOffset, SpringLayout.WEST, tfPassword);
		spring.putConstraint(SpringLayout.NORTH, labelPass, fieldNextLabelGap, SpringLayout.SOUTH, tfUsername);

		spring.putConstraint(SpringLayout.WEST, tfPassword, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPassword, labelFieldGap, SpringLayout.SOUTH, labelPass);

		spring.putConstraint(SpringLayout.EAST, buttonCopyPassword, buttonCopyWest, SpringLayout.EAST, labelPass);
		spring.putConstraint(SpringLayout.NORTH, buttonCopyPassword, 0, SpringLayout.NORTH, labelPass);

		spring.putConstraint(SpringLayout.EAST, buttonInformation, 40, SpringLayout.EAST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonInformation, 10, SpringLayout.SOUTH, labelUsername);

		spring.putConstraint(SpringLayout.WEST, labelWhen, labelOffset, SpringLayout.WEST, tfWhen);
		spring.putConstraint(SpringLayout.NORTH, labelWhen, fieldNextLabelGap, SpringLayout.SOUTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, tfWhen, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfWhen, labelFieldGap, SpringLayout.SOUTH, labelWhen);

		contentPane.setLayout(spring);

		contentPane.add(unlockedContainer);
		contentPane.add(labelUsername);
		contentPane.add(tfUsername);
		contentPane.add(buttonCopyUsername);
		contentPane.add(labelPass);
		contentPane.add(buttonInformation);
		contentPane.add(tfPassword);
		contentPane.add(buttonCopyPassword);
		contentPane.add(labelWhen);
		contentPane.add(tfWhen);

		buttonCopyUsername.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				Toolkit
					.getDefaultToolkit()
					.getSystemClipboard()
					.setContents(new StringSelection(tfUsername.getText()), null);

				showInfoDialog("Username copied to clipboard");
			}
		});

		buttonCopyPassword.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				Toolkit
					.getDefaultToolkit()
					.getSystemClipboard()
					.setContents(new StringSelection(tfPassword.getText()), null);

				showInfoDialog("Password copied to clipboard");
			}
		});

		buttonInformation.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				doAnalysePassword(findPasswordForId(tfId.getText()));
			}
		});

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private void showChangedDetails(String id, String username, String old, String New, long timestamp)
	{
		if (null == id)
			return;

		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();
		Font fontDetails = new Font("Times New Roman", Font.PLAIN, 18);
		Font fontLabel = new Font("Verdana", Font.BOLD, 18);

		int passwordLen = Math.max(New.length(), old.length());
		int idLen = id.length();
		int windowWidth = 0;
		int windowHeight = 600;
		final int labelWidth = 200;
		final int labelHeight = 30;
		final int leftOffsetDetails = (labelWidth + 20);

		if (idLen > passwordLen || passwordLen < 19)
			windowWidth = Math.max(19, idLen) * 10 + 30;
		else
			windowWidth = passwordLen * 10 + 30;

		windowWidth += leftOffsetDetails + 120;

		int halfWidth = (windowWidth/2);
		int north = 0;

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(windowWidth, windowHeight);
		frame.setTitle("Password Details");

		JLabel unlockedContainer = new JLabel(iconUnlocked128);
		SpringLayout spring = new SpringLayout();

		JTextArea taInfo = new JTextArea(currentLanguage.get(languages.STRING_PROMPT_DETAILS_CHANGED));

		taInfo.setBorder(null);
		taInfo.setFont(new Font("Verdana", Font.PLAIN, 22));
		taInfo.setBackground(frame.getBackground());
		taInfo.setEditable(false);

		JLabel labelId = new JLabel(currentLanguage.get(languages.STRING_PASSWORD_ID));
		JLabel labelUsername = new JLabel(currentLanguage.get(languages.STRING_USERNAME));
		JLabel labelOldPass = new JLabel(currentLanguage.get(languages.STRING_CURRENT_PASSWORD));
		JLabel labelPass = new JLabel(currentLanguage.get(languages.STRING_NEW_PASSWORD));
		JLabel labelWhen = new JLabel(currentLanguage.get(languages.STRING_CREATION_TIME));

		Dimension labelSize = new Dimension(labelWidth, labelHeight);

		labelId.setPreferredSize(labelSize);
		labelUsername.setPreferredSize(labelSize);
		labelOldPass.setPreferredSize(labelSize);
		labelPass.setPreferredSize(labelSize);
		labelWhen.setPreferredSize(labelSize);

		JLabel labelCopy = new JLabel(currentLanguage.get(languages.STRING_COPY_PASSWORD));
		labelCopy.setFont(new Font("Times New Roman", Font.ITALIC, 14));

		JButton buttonCopy = new JButton(iconCopy32);
		buttonCopy.setBackground(Color.WHITE);
		buttonCopy.setPreferredSize(new Dimension(iconCopy32.getIconHeight() + 10, iconCopy32.getIconHeight() + 10));
		buttonCopy.setBorder(null);
		buttonCopy.setBackground(new Color(240, 240, 240));

		labelId.setFont(fontLabel);
		labelUsername.setFont(fontLabel);
		labelOldPass.setFont(fontLabel);
		labelPass.setFont(fontLabel);
		labelWhen.setFont(fontLabel);

		JTextField tfId = new JTextField(id);
		JTextField tfUsername = new JTextField(username);
		JTextField tfOldPassword = new JTextField(old);
		JTextField tfPassword = new JTextField(New);

		tfId.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfUsername.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfOldPassword.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfPassword.setComponentPopupMenu(new RightClickPopup().getMenu());

		Date date = new Date(timestamp);
		DateFormat dateFormat = new SimpleDateFormat("dd-MM-YYYY HH:mm:ss");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

		JTextField tfWhen = new JTextField(dateFormat.format(date));

		tfId.setFont(fontDetails);
		tfUsername.setFont(fontDetails);
		tfOldPassword.setFont(fontDetails);
		tfPassword.setFont(fontDetails);
		tfWhen.setFont(fontDetails);

		tfOldPassword.setForeground(new Color(180, 180, 180));

		tfId.setEditable(false);
		tfUsername.setEditable(false);
		tfOldPassword.setEditable(false);
		tfPassword.setEditable(false);
		tfWhen.setEditable(false);

		tfId.setBorder(null);
		tfUsername.setBorder(null);
		tfOldPassword.setBorder(null);
		tfPassword.setBorder(null);
		tfWhen.setBorder(null);

		JButton buttonInformation = new JButton(iconInfo32);

		buttonInformation.setBorder(null);
		buttonInformation.setBackground(colorFrame);
		buttonInformation.setPreferredSize(new Dimension(iconInfo32.getIconHeight(), iconInfo32.getIconHeight()));

		buttonInformation.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				doAnalysePassword(findPasswordForId(tfId.getText()));
			}
		});

		north = iconUnlocked128.getIconHeight();

		spring.putConstraint(SpringLayout.WEST, unlockedContainer, (halfWidth - (iconUnlocked128.getIconWidth()/2)), SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, unlockedContainer, 25, SpringLayout.NORTH, contentPane);

		north += 80;

		spring.putConstraint(SpringLayout.WEST, taInfo, (halfWidth - 160), SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, taInfo, north, SpringLayout.NORTH, contentPane);

		north += 100;

		spring.putConstraint(SpringLayout.WEST, labelId, 25, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelId, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfId, leftOffsetDetails, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfId, north, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelUsername, 25, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelUsername, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfUsername, leftOffsetDetails, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfUsername, north, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelOldPass, 25, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelOldPass, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfOldPassword, leftOffsetDetails, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfOldPassword, north, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelPass, 25, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelPass, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfPassword, leftOffsetDetails, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPassword, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, buttonInformation, (windowWidth - iconInfo32.getIconWidth() - 10), SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonInformation, north-10, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelWhen, 25, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelWhen, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfWhen, leftOffsetDetails, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfWhen, north, SpringLayout.NORTH, contentPane);

		north += 40;

		int offsetRightLabelCopy = getStringWidth(labelCopy.getText()) + 10;

		spring.putConstraint(SpringLayout.WEST, labelCopy, windowWidth - offsetRightLabelCopy, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelCopy, windowHeight - 60, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, buttonCopy, windowWidth - iconCopy32.getIconHeight() - 20, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonCopy, windowHeight - iconCopy32.getIconHeight() - 45, SpringLayout.NORTH, contentPane);

		contentPane.setLayout(spring);

		contentPane.add(unlockedContainer);
		contentPane.add(taInfo);
		contentPane.add(labelId);
		contentPane.add(tfId);
		contentPane.add(labelUsername);
		contentPane.add(tfUsername);
		contentPane.add(labelOldPass);
		contentPane.add(tfOldPassword);
		contentPane.add(labelPass);
		contentPane.add(tfPassword);
		contentPane.add(buttonInformation);
		contentPane.add(labelWhen);
		contentPane.add(tfWhen);
		contentPane.add(labelCopy);
		contentPane.add(buttonCopy);

		buttonCopy.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				PasswordEntry entry = findPasswordForId(currentlySelected.getText());
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(entry.getPassword()), null);
				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_PASSWORD_COPIED));
			}
		});

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private void showPasswordDetailsForId(PasswordEntry entry)
	{
		if (null == entry)
			return;

		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();

		Font fontLabel = new Font("Verdana", Font.BOLD, 18);

		assert(null != entry);

		int passwordLen = entry.getPassword().length();
		int idLen = entry.getId().length();
		int windowWidth = 0;
		int windowHeight = 420;
		final int labelWidth = 200;
		final int labelHeight = 30;
		final int leftOffsetDetails = (labelWidth + 20);

		if (idLen > passwordLen || passwordLen < 19)
			windowWidth = Math.max(19, idLen) * 10 + 30;
		else
			windowWidth = passwordLen * 10 + 30;

		windowWidth += leftOffsetDetails + 120;

		int halfWidth = (windowWidth/2);
		int north = 0;

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(windowWidth, windowHeight);
		frame.setTitle(currentLanguage.get(languages.STRING_TITLE_PASSWORD_DETAILS));

		JLabel unlockedContainer = new JLabel(iconUnlocked128);
		SpringLayout spring = new SpringLayout();

		JLabel labelId = new JLabel(currentLanguage.get(languages.STRING_PASSWORD_ID));
		JLabel labelUsername = new JLabel(currentLanguage.get(languages.STRING_USERNAME));
		JLabel labelPass = new JLabel(currentLanguage.get(languages.STRING_PASSWORD));
		JLabel labelWhen = new JLabel(currentLanguage.get(languages.STRING_CREATION_TIME));

		Dimension labelSize = new Dimension(labelWidth, labelHeight);

		labelId.setPreferredSize(labelSize);
		labelUsername.setPreferredSize(labelSize);
		labelPass.setPreferredSize(labelSize);
		labelWhen.setPreferredSize(labelSize);

		labelId.setFont(fontLabel);
		labelUsername.setFont(fontLabel);
		labelPass.setFont(fontLabel);
		labelWhen.setFont(fontLabel);

		JLabel labelCopy = new JLabel(currentLanguage.get(languages.STRING_COPY_PASSWORD));
		labelCopy.setFont(new Font("Times New Roman", Font.PLAIN, 14));

		JButton buttonCopy = new JButton(iconCopy32);
		buttonCopy.setBackground(Color.WHITE);
		buttonCopy.setPreferredSize(new Dimension(iconCopy32.getIconHeight() + 10, iconCopy32.getIconHeight() + 10));
		buttonCopy.setBorder(null);
		buttonCopy.setBackground(new Color(240, 240, 240));

		JTextField tfId = new JTextField(entry.getId());
		JTextField tfUsername = new JTextField(entry.getUsername());
		JTextField tfPassword = new JTextField(entry.getPassword());

		tfId.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfUsername.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfPassword.setComponentPopupMenu(new RightClickPopup().getMenu());

		Date date = new Date(entry.getTimestamp());
		DateFormat dateFormat = new SimpleDateFormat("dd-MM-YYYY HH:mm:ss");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

		JTextField tfWhen = new JTextField(dateFormat.format(date));

		tfId.setFont(fontDetails);
		tfUsername.setFont(fontDetails);
		tfPassword.setFont(fontDetails);
		tfWhen.setFont(fontDetails);

		tfId.setEditable(false);
		tfUsername.setEditable(false);
		tfPassword.setEditable(false);
		tfWhen.setEditable(false);

		tfId.setBorder(null);
		tfUsername.setBorder(null);
		tfPassword.setBorder(null);
		tfWhen.setBorder(null);

		JButton buttonInformation = new JButton(iconInfo32);

		buttonInformation.setBorder(null);
		buttonInformation.setBackground(colorFrame);
		buttonInformation.setPreferredSize(new Dimension(iconInfo32.getIconHeight(), iconInfo32.getIconHeight()));

		buttonInformation.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				doAnalysePassword(findPasswordForId(tfId.getText()));
			}
		});

		north = iconUnlocked128.getIconHeight();

		spring.putConstraint(SpringLayout.WEST, unlockedContainer, (halfWidth - (iconUnlocked128.getIconWidth()/2)), SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, unlockedContainer, 25, SpringLayout.NORTH, contentPane);

		north += 60;

		spring.putConstraint(SpringLayout.WEST, labelId, 25, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelId, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfId, leftOffsetDetails, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfId, north, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelUsername, 25, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelUsername, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfUsername, leftOffsetDetails, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfUsername, north, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelPass, 25, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelPass, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfPassword, leftOffsetDetails, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPassword, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, buttonInformation, (windowWidth - iconInfo32.getIconWidth() - 10), SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonInformation, north-10, SpringLayout.NORTH, contentPane);

		north += 40;

		spring.putConstraint(SpringLayout.WEST, labelWhen, 25, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelWhen, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfWhen, leftOffsetDetails, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfWhen, north, SpringLayout.NORTH, contentPane);

		north += 40;

		int offsetRightLabelCopy = getStringWidth(labelCopy.getText()) + 10;

		spring.putConstraint(SpringLayout.WEST, labelCopy, windowWidth - offsetRightLabelCopy, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelCopy, windowHeight - 60, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, buttonCopy, windowWidth - iconCopy32.getIconHeight() - 20, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonCopy, windowHeight - iconCopy32.getIconHeight() - 45, SpringLayout.NORTH, contentPane);

		contentPane.setLayout(spring);

		contentPane.add(unlockedContainer);
		contentPane.add(labelId);
		contentPane.add(tfId);
		contentPane.add(labelUsername);
		contentPane.add(tfUsername);
		contentPane.add(labelPass);
		contentPane.add(tfPassword);
		contentPane.add(buttonInformation);
		contentPane.add(labelWhen);
		contentPane.add(tfWhen);
		contentPane.add(labelCopy);
		contentPane.add(buttonCopy);

		buttonCopy.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				PasswordEntry entry = findPasswordForId(currentlySelected.getText());
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(entry.getPassword()), null);
				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_PASSWORD_COPIED));
			}
		});

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
*/
	private boolean passwordIdExists(String id)
	{
		return (null != passwordEntries && passwordEntries.containsKey(id));
	}

	private void changeMasterPassword()
	{
		assert(null != password);

		if (null == passwordEntries)
		{
			getFileContents(passwordFile);
		}

		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();
		SpringLayout spring = new SpringLayout();
		JLabel lockedContainer = new JLabel(iconLocked128);
		JTextArea taPrompt = new JTextArea(currentLanguage.get(languages.STRING_CHANGE_MASTER_PASSWORD));

		JLabel labelOldPassword = new JLabel(currentLanguage.get(languages.STRING_CURRENT_PASSWORD));
		JPasswordField passFieldOld = new JPasswordField(23);
		JLabel labelPassword = new JLabel(currentLanguage.get(languages.STRING_NEW_PASSWORD));
		JPasswordField passField = new JPasswordField(23);
		JLabel labelPasswordConfirm = new JLabel(currentLanguage.get(languages.STRING_CONFIRM_NEW_PASSWORD));
		JPasswordField passFieldConfirm = new JPasswordField(23);

		JButton buttonConfirm = new JButton(iconConfirm32);
		JLabel labelConfirm = new JLabel("Confirm");

		taPrompt.setFont(new Font("Verdana", Font.BOLD, 25));
		taPrompt.setBackground(frame.getBackground());
		taPrompt.setEditable(false);

		buttonConfirm.setBackground(colorConfirm);
		//buttonConfirm.setBorder(null);

		labelOldPassword.setFont(fontLabel);
		labelPassword.setFont(fontLabel);
		labelPasswordConfirm.setFont(fontLabel);
		passFieldOld.setFont(fontInput);
		passField.setFont(fontInput);
		passFieldConfirm.setFont(fontInput);

		int north = 40;
		int leftOffset = 105;
		int windowWidth = 650;
		int windowHeight = 720;

		contentPane.setLayout(spring);

		frame.setSize(windowWidth, windowHeight);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		final int iconWest = ((windowWidth>>1) - (iconLocked128.getIconWidth()>>1));

		spring.putConstraint(SpringLayout.WEST, lockedContainer, iconWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, lockedContainer, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, taPrompt, 150, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, taPrompt, 20, SpringLayout.SOUTH, lockedContainer);

		spring.putConstraint(SpringLayout.WEST, labelOldPassword, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelOldPassword, 20, SpringLayout.SOUTH, taPrompt);

		spring.putConstraint(SpringLayout.WEST, passFieldOld, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, passFieldOld, 30, SpringLayout.SOUTH, labelOldPassword);

		spring.putConstraint(SpringLayout.WEST, labelPassword, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelPassword, 50, SpringLayout.SOUTH, passFieldOld);

		spring.putConstraint(SpringLayout.WEST, passField, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, passField, 30, SpringLayout.SOUTH, labelPassword);

		spring.putConstraint(SpringLayout.WEST, labelPasswordConfirm, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelPasswordConfirm, 50, SpringLayout.SOUTH, passField);

		spring.putConstraint(SpringLayout.WEST, passFieldConfirm, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, passFieldConfirm, 30, SpringLayout.SOUTH, labelPasswordConfirm);

		final int buttonWest = ((windowWidth>>1) - (iconConfirm32.getIconWidth()>>1)) - 20;

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, buttonWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, north, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, labelConfirm, 10, SpringLayout.EAST, buttonConfirm);
		spring.putConstraint(SpringLayout.NORTH, labelConfirm, 5, SpringLayout.NORTH, buttonConfirm);

		contentPane.add(lockedContainer);
		contentPane.add(taPrompt);
		contentPane.add(labelOldPassword);
		contentPane.add(passFieldOld);
		contentPane.add(labelPassword);
		contentPane.add(passField);
		contentPane.add(labelPasswordConfirm);
		contentPane.add(passFieldConfirm);
		contentPane.add(buttonConfirm);
		contentPane.add(labelConfirm);

		buttonConfirm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				String oldPass = new String(passFieldOld.getPassword());
				String newPass = new String(passField.getPassword());
				String newPassConfirm = new String(passFieldConfirm.getPassword());

				if (false == oldPass.equals(password))
				{
					showErrorDialog(currentLanguage.get(languages.STRING_ERROR_INCORRECT_PASSWORD));
					return;
				}

				if (false == newPass.equals(newPassConfirm))
				{
					showErrorDialog(currentLanguage.get(languages.STRING_ERROR_PASSWORDS_DO_NOT_MATCH));
					return;
				}

				password = newPass;

			/*
			 * Will use the global var PASSWORD to encrypt
			 * and write the cached password entries to disk.
			 */
				putPasswordEntries();
				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_MASTER_PASSWORD_CHANGED));

				frame.dispose();
			}
		});

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private JButton selectedSetting = null;

	private void setSelectedSetting(JButton selected)
	{
		if (null != selectedSetting)
			selectedSetting.setBackground(colorButtonDeselected);

		if (selectedSetting == selected)
		{
			selectedSetting.setBackground(colorButtonDeselected);
			selectedSetting = null;
			return;
		}

		selectedSetting = selected;
		selectedSetting.setBackground(colorButtonSelected);
		return;
	}

	private JButton settingsSelectedLanguage = null;

	private void changeCurrentLanguage()
	{
		if (null == settingsSelectedLanguage)
		{
			showErrorDialog(currentLanguage.get(languages.STRING_ERROR_SELECT_LANGUAGE));
			return;
		}

		try
		{
			RuntimeOptions rOpts = new RuntimeOptions();

			rOpts.setLanguage(settingsSelectedLanguage.getText());
			String json = mapper.writeValueAsString(rOpts);

			FileOutputStream fOut = new FileOutputStream(new File(configFile), false);
			fOut.write(json.getBytes());
			fOut.flush();
			fOut.close();

			currentLanguage = languages.getLanguageFromName(settingsSelectedLanguage.getText());
			showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_CHANGED_LANGUAGE));

		/*
		 * Set the text in currently visible global GUI components
		 * to the newly chosen language.
		 */
			// in main JFrame (created in setupGUI)
			setTitle(currentLanguage.get(languages.STRING_APPLICATION_NAME) + " v" + VERSION);
			taAppName.setText(currentLanguage.get(languages.STRING_APPLICATION_NAME));
			//labelPasswordIds.setText(currentLanguage.get(languages.STRING_PASSWORD_ID_LIST));

			// in JFrame created in showSettings
			labelMasterPassword.setText(currentLanguage.get(languages.STRING_MASTER_PASSWORD));
			labelLanguage.setText(currentLanguage.get(languages.STRING_LANGUAGE));
			labelCharacterSet.setText(currentLanguage.get(languages.STRING_CHARACTER_SET));

			// Refreshes the actual display
			revalidate();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}


		//showInfoDialog("Would change language to " + settingsSelectedLanguage.getText());
		return;
	}

	private void setSelectedLanguageButtonSettings(JButton selectedLanguageButton)
	{
		if (null != settingsSelectedLanguage)
		{
			settingsSelectedLanguage.setBackground(colorButtonDeselected);
		}

		if (settingsSelectedLanguage == selectedLanguageButton)
		{
			settingsSelectedLanguage = null;
			return;
		}

		settingsSelectedLanguage = selectedLanguageButton;
		settingsSelectedLanguage.setBackground(colorButtonSelected);
		return;
	}

	private void showSettings()
	{
		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();
		SpringLayout spring = new SpringLayout();

		JLabel containerSettingsIcon = new JLabel(iconSettings128);

		final int windowWidth = 650;
		final int windowHeight = 650;
		final int scrollPaneWidth = 300;
		final int scrollPaneHeight = 80;
		int halfWidth = (windowWidth>>1);

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(windowWidth, windowHeight);

		contentPane.setLayout(spring);

// global vars
		labelMasterPassword = new JLabel(currentLanguage.get(languages.STRING_MASTER_PASSWORD));
		labelLanguage = new JLabel(currentLanguage.get(languages.STRING_LANGUAGE));
		labelCharacterSet = new JLabel(currentLanguage.get(languages.STRING_CHARACTER_SET));

		labelMasterPassword.setFont(fontLabel);
		labelLanguage.setFont(fontLabel);
		labelCharacterSet.setFont(fontLabel);

		JTextField tfMasterPassword = new JTextField(" **************** ");

		JPanel buttonGrid = new JPanel(new GridLayout(0, 1));

		JScrollPane scrollPane = new JScrollPane(
			buttonGrid,
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
			JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
		);

		JButton buttonEnglish = new JButton("English");
		JButton buttonFrench = new JButton("Français");
		JButton buttonKorean = new JButton("한국어");
		JButton buttonMalaysian = new JButton("Bahasa Melayu");

		scrollPane.setPreferredSize(new Dimension(scrollPaneWidth, scrollPaneHeight));

		buttonEnglish.setBackground(colorButtonDeselected);
		buttonFrench.setBackground(colorButtonDeselected);
		buttonKorean.setBackground(colorButtonDeselected);
		buttonMalaysian.setBackground(colorButtonDeselected);

		buttonEnglish.setFont(fontInput);
		buttonFrench.setFont(fontInput);
		buttonKorean.setFont(fontInput);
		buttonMalaysian.setFont(fontInput);

		buttonEnglish.addActionListener(new settingsLanguageButtonListener());
		buttonFrench.addActionListener(new settingsLanguageButtonListener());
		buttonKorean.addActionListener(new settingsLanguageButtonListener());
		buttonMalaysian.addActionListener(new settingsLanguageButtonListener());

		buttonGrid.add(buttonEnglish);
		buttonGrid.add(buttonFrench);
		buttonGrid.add(buttonKorean);
		buttonGrid.add(buttonMalaysian);

		tfMasterPassword.setFont(fontDetails);
		//tfMasterPassword.setPreferredSize(new Dimension(250, 30));
		tfMasterPassword.setBackground(new Color(230, 230, 230));
		tfMasterPassword.setBorder(null);
		tfMasterPassword.setEditable(false);

		int iconWidth = iconEdit32.getIconWidth();
		int iconHeight = iconEdit32.getIconHeight();
		Dimension iconSize = new Dimension(iconWidth + 10, iconHeight + 10);

		JButton buttonChangeMaster = new JButton(iconEdit32);
		buttonChangeMaster.setBackground(colorFrame);
		buttonChangeMaster.setPreferredSize(iconSize);
		buttonChangeMaster.setBorder(null);

		JButton buttonChangeLanguage = new JButton(iconEdit32);
		buttonChangeLanguage.setBackground(colorFrame);
		buttonChangeLanguage.setPreferredSize(iconSize);
		buttonChangeLanguage.setBorder(null);

		JButton buttonAdjustCharacterSet = new JButton(iconEdit32);
		buttonAdjustCharacterSet.setBackground(colorFrame);
		buttonAdjustCharacterSet.setPreferredSize(iconSize);
		buttonAdjustCharacterSet.setBorder(null);

		final int iconWest = (halfWidth - (iconSettings128.getIconWidth()>>1));
		final int leftOffset = 20;
		final int scrollPaneWest = (halfWidth - (scrollPaneWidth>>1));

		spring.putConstraint(SpringLayout.WEST, containerSettingsIcon, iconWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, containerSettingsIcon, 60, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, labelMasterPassword, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelMasterPassword, 20, SpringLayout.SOUTH, containerSettingsIcon);

		spring.putConstraint(SpringLayout.WEST, tfMasterPassword, 10, SpringLayout.EAST, labelMasterPassword);
		spring.putConstraint(SpringLayout.NORTH, tfMasterPassword, 5, SpringLayout.NORTH, labelMasterPassword);

		spring.putConstraint(SpringLayout.EAST, buttonChangeMaster, -30, SpringLayout.EAST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeMaster, 2, SpringLayout.NORTH, tfMasterPassword);

		spring.putConstraint(SpringLayout.WEST, labelLanguage, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelLanguage, 30, SpringLayout.SOUTH, tfMasterPassword);

		spring.putConstraint(SpringLayout.WEST, scrollPane, scrollPaneWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, scrollPane, 20, SpringLayout.SOUTH, labelLanguage);

		spring.putConstraint(SpringLayout.EAST, buttonChangeLanguage, -30, SpringLayout.EAST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeLanguage, 2, SpringLayout.NORTH, scrollPane);

		spring.putConstraint(SpringLayout.WEST, labelCharacterSet, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelCharacterSet, 30, SpringLayout.SOUTH, scrollPane);

		spring.putConstraint(SpringLayout.EAST, buttonAdjustCharacterSet, -30, SpringLayout.EAST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonAdjustCharacterSet, 2, SpringLayout.NORTH, labelCharacterSet);

		contentPane.add(containerSettingsIcon);
		contentPane.add(labelMasterPassword);
		contentPane.add(tfMasterPassword);
		contentPane.add(buttonChangeMaster);
		contentPane.add(labelLanguage);
		contentPane.add(scrollPane);
		contentPane.add(buttonChangeLanguage);
		contentPane.add(labelCharacterSet);
		contentPane.add(buttonAdjustCharacterSet);

		buttonChangeMaster.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				changeMasterPassword();
			}
		});

		buttonChangeLanguage.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				changeCurrentLanguage();
			}
		});

		buttonAdjustCharacterSet.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				showCharacterSet();
			}
		});

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private class buttonSettingListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent event)
		{
			setSelectedSetting((JButton)event.getSource());
		}
	}

	private class settingsLanguageButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent event)
		{
			setSelectedLanguageButtonSettings((JButton)event.getSource());
		}
	}

	private void doInitialConfiguration()
	{
		//JFrame frame = new JFrame();
		Container contentPane = getContentPane();
		SpringLayout spring = new SpringLayout();

		JLabel iconContainer = new JLabel(iconLocked128);

		final int windowWidth = 620;
		final int windowHeight = 650;

		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(windowWidth, windowHeight);
		setTitle(currentLanguage.get(languages.STRING_TITLE_PASSWORD_MANAGER_CONFIGURATION));

		contentPane.setLayout(spring);

		JTextArea taInfo = new JTextArea(currentLanguage.get(languages.STRING_CONFIGURATION_PROMPT));

		taInfo.setFont(fontLabel);
		taInfo.setEditable(false);
		taInfo.setBackground(colorFrame);

		JLabel labelPass = new JLabel(currentLanguage.get(languages.STRING_PASSWORD));
		JLabel labelConfirm = new JLabel(currentLanguage.get(languages.STRING_CONFIRM_PASSWORD));

		JPasswordField passField = new JPasswordField(25);
		JPasswordField passFieldConfirm = new JPasswordField(25);

		passField.setFont(fontInput);
		passFieldConfirm.setFont(fontInput);

		JButton buttonConfirm = new JButton(iconConfirm64);
		buttonConfirm.setBackground(Color.WHITE);
		//buttonOk.setBackground(new Color(135, 255, 175)); // 0x87ffaf

		int halfWidth = (windowWidth>>1);
		int leftOffset = (halfWidth - 225);

		final int iconWest = (halfWidth - (iconLocked128.getIconWidth()>>1));
		final int buttonWest = (halfWidth - (iconConfirm64.getIconWidth()>>1)-10);

		spring.putConstraint(SpringLayout.WEST, iconContainer, iconWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, iconContainer, 30, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, taInfo, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, taInfo, 75, SpringLayout.SOUTH, iconContainer);

		spring.putConstraint(SpringLayout.WEST, labelPass, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelPass, 20, SpringLayout.SOUTH, taInfo);

		spring.putConstraint(SpringLayout.WEST, passField, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, passField, 10, SpringLayout.SOUTH, labelPass);

		spring.putConstraint(SpringLayout.WEST, labelConfirm, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelConfirm, 20, SpringLayout.SOUTH, passField);

		spring.putConstraint(SpringLayout.WEST, passFieldConfirm, leftOffset, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, passFieldConfirm, 10, SpringLayout.SOUTH, labelConfirm);

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, buttonWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 40, SpringLayout.SOUTH, passFieldConfirm);

		contentPane.add(iconContainer);
		contentPane.add(taInfo);
		contentPane.add(labelPass);
		contentPane.add(passField);
		contentPane.add(labelConfirm);
		contentPane.add(passFieldConfirm);
		contentPane.add(buttonConfirm);

		getRootPane().setDefaultButton(buttonConfirm);

		setLocationRelativeTo(null);
		setVisible(true);

		buttonConfirm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				String pass1 = new String(passField.getPassword());
				String pass2 = new String(passFieldConfirm.getPassword());

				if (false == pass1.equals(pass2))
				{
					showErrorDialog(currentLanguage.get(languages.STRING_ERROR_PASSWORDS_DO_NOT_MATCH));
					return;
				}
				else
				if (pass1.length() < 8 || pass1.length() > 100)
				{
					showErrorDialog(currentLanguage.get(languages.STRING_ERROR_INVALID_PASSWORD_LENGTH));
					return;
				}

				password = pass1;

				try
				{
					File fObj = new File(passwordFile);
					fObj.createNewFile();
					AESCrypt aes = new AESCrypt();
					aes.encryptFile(passwordFile, pass1);
				}
				catch (Exception e)
				{
					e.printStackTrace();
					System.exit(1);
				}

				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_PASSWORD_FILE_CREATED));

				contentPane.remove(iconContainer);
				contentPane.remove(taInfo);
				contentPane.remove(labelPass);
				contentPane.remove(passField);
				contentPane.remove(labelConfirm);
				contentPane.remove(passFieldConfirm);
				contentPane.remove(buttonConfirm);

				//revalidate();
				//repaint();

				dispose();
				setupGUI();
			}
		});
	}

	private void addNewPasswordEntry(String id, PasswordEntry entry)
	{
		if (passwordEntries.containsKey(id))
		{
			ArrayList<PasswordEntry> list = passwordEntries.get(id);
			list.add(entry);

			passwordEntries.put(id, list);
		}
		else
		{
			ArrayList<PasswordEntry> list = new ArrayList<PasswordEntry>();
			list.add(entry);

			passwordEntries.put(id, list);
		}

		return;
	}

	private PasswordEntry getEntryForHash(String hash)
	{
		Set<Map.Entry<String,ArrayList<PasswordEntry>>> set = passwordEntries.entrySet();
		Iterator<Map.Entry<String,ArrayList<PasswordEntry>>> iter = set.iterator();
		Iterator<PasswordEntry> it = null;
		PasswordEntry entry = null;

		while (iter.hasNext())
		{
			Map.Entry<String,ArrayList<PasswordEntry>> e = iter.next();
			it = e.getValue().iterator();

			while (it.hasNext())
			{
				entry = it.next();

				if (entry.getHash() == hash)
					return entry;
			}
		}

		return null;
	}

	private void removeEntryForHash(String hash)
	{
		Set<Map.Entry<String,ArrayList<PasswordEntry>>> set = passwordEntries.entrySet();
		Iterator<Map.Entry<String,ArrayList<PasswordEntry>>> iter = set.iterator();
		Iterator<PasswordEntry> it = null;
		PasswordEntry entry = null;
		ArrayList<PasswordEntry> list = null;

		while (iter.hasNext())
		{
			Map.Entry<String,ArrayList<PasswordEntry>> e = iter.next();
			it = e.getValue().iterator();

			while (it.hasNext())
			{
				entry = it.next();

				if (entry.getHash() == hash)
				{
					list = e.getValue();
					list.remove(list.indexOf(entry));

					if (list.size() > 0)
					{
						passwordEntries.put(e.getKey(), list);
					}
					else
					{
						passwordEntries.remove(e.getKey());
					}

					return;
				}
			}
		}

		return;
	}

	/**
	 * Find entry for OLDHASH and replace it with NEWENTRY
	 */
	private void putPasswordEntry(String oldHash, PasswordEntry newEntry)
	{
		Set<Map.Entry<String,ArrayList<PasswordEntry>>> set = passwordEntries.entrySet();
		Iterator<Map.Entry<String,ArrayList<PasswordEntry>>> iter = set.iterator();
		Iterator<PasswordEntry> it = null;
		PasswordEntry entry = null;
		ArrayList<PasswordEntry> list = null;

		while (iter.hasNext())
		{
			Map.Entry<String,ArrayList<PasswordEntry>> e = iter.next();
			it = e.getValue().iterator();

			while (it.hasNext())
			{
				entry = it.next();

				if (entry.getHash() == oldHash)
				{
					list = e.getValue();
					list.remove(list.indexOf(entry));
					list.add(newEntry);

					passwordEntries.put(e.getKey(), list);
					return;
				}
			}
		}
	}

	private void addPasswordEntryToTree(String id, PasswordEntry entry)
	{
		if (null == tree || null == treeModel)
		{
			showErrorDialog("No tree/tree model");
			return;
		}

		if (null == entry)
			return;

		DefaultMutableTreeNode r = (DefaultMutableTreeNode)treeModel.getRoot();

		if (null == r)
		{
			showErrorDialog("No root node of tree...");
			return;
		}

		DefaultMutableTreeNode n = (DefaultMutableTreeNode)r.getFirstChild();

		if (null == n)
		{
			showErrorDialog("No first child of root...");
			return;	
		}

		while (false == n.toString().equals(id))
		{
			n = (DefaultMutableTreeNode)n.getNextSibling();
			if (null == n)
				break;
		}

		DefaultMutableTreeNode _n = new DefaultMutableTreeNode(entry.getEmail());

		if (null != n && n.toString().equals(id))
		{
			n.add(_n);
		}
		else
		{
			DefaultMutableTreeNode nid = new DefaultMutableTreeNode(id);
			nid.add(_n);
			r.add(nid);
		}

		treeModel.reload();
		revalidate();

		return;
	}

	/**
	 * Update the password details in the tree for an entry. EMAIL
	 * is necessary because the email may have been updated, and we
	 * need the old one to find the stale entry.
	 */
	private void updatePasswordEntryInTree(String id, String email, PasswordEntry entry)
	{
		if (null == id || null == email)
			return;

		DefaultMutableTreeNode r = (DefaultMutableTreeNode)treeModel.getRoot();

		if (null == r)
			return;

		DefaultMutableTreeNode n = (DefaultMutableTreeNode)r.getFirstChild();

		if (null == n)
			return;

		while (false == n.toString().equals(id))
		{
			n = (DefaultMutableTreeNode)n.getNextSibling();
			if (null == n)
				return;
		}

		DefaultMutableTreeNode _n = (DefaultMutableTreeNode)n.getFirstChild();

		while (false == _n.toString().equals(email))
		{
			_n = (DefaultMutableTreeNode)_n.getNextSibling();
			if (null == _n)
				return;
		}

		if (false == _n.toString().equals(entry.getEmail()))
			_n.setUserObject(entry.getEmail());

		treeModel.reload();

		return;
	}

	/**
	 * Find and remove a password entry node from the tree.
	 * If the node for the ID has only one child, remove
	 * the ID node, otherwise search for the child node
	 * that represents EMAIL and remove it.
	 */
	private void removePasswordEntryFromTree(String id, String email)
	{
		if (null == tree || treeModel == null)
			return;

		DefaultMutableTreeNode r = (DefaultMutableTreeNode)treeModel.getRoot();

		if (null == r)
			return;

		DefaultMutableTreeNode n = (DefaultMutableTreeNode)r.getFirstChild();

		if (null == n)
			return;

		while (false == n.toString().equals(id))
		{
			n = (DefaultMutableTreeNode)n.getNextSibling();
			if (null == n)
				return;
		}

		if (1 == n.getChildCount())
			r.remove((MutableTreeNode)n);
		else
		{
			DefaultMutableTreeNode _n = (DefaultMutableTreeNode)n.getFirstChild();
			while (false == _n.toString().equals(email))
			{
				_n = (DefaultMutableTreeNode)_n.getNextSibling();
				if (null == _n)
					return;
			}

			n.remove((MutableTreeNode)_n);
		}

		treeModel.reload();

		return;
	}

	private static final int SZ_WINDOW_WIDTH_DETAILS = 550;
	private static final int SZ_WINDOW_HEIGHT_DETAILS = 600;
	private static final int SZ_TEXTFIELD_WIDTH_DETAILS = 425;
	private static final int SZ_TEXTFIELD_HEIGHT_DETAILS = 25;

	/**
	 * Add new password to file
	 * @param model The table model to which the entry will be added.
	 */
	private void doAddNewPassword()
	{
		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();
		SpringLayout spring = new SpringLayout();

		//final int windowWidth = 550;
		//final int windowHeight = 600;
		//final int tfWidth = 425;
		//final int tfHeight = 25;
		Dimension tfSize = new Dimension(SZ_TEXTFIELD_WIDTH_DETAILS, SZ_TEXTFIELD_HEIGHT_DETAILS);

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(SZ_WINDOW_WIDTH_DETAILS, SZ_WINDOW_HEIGHT_DETAILS);
		contentPane.setLayout(spring);

		JLabel iconContainer = new JLabel(iconLocked64);
		JLabel labelId = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD_ID));
		JLabel labelEmail = new GenericLabel(currentLanguage.get(languages.STRING_EMAIL));
		JLabel labelUsername = new GenericLabel(currentLanguage.get(languages.STRING_USERNAME));
		JLabel labelPasswordLen = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD_LENGTH_MIN_MAX));
		JLabel labelPassword = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD));

		JTextField tfId = new GenericTextField("");
		JTextField tfEmail = new GenericTextField("");
		JTextField tfUsername = new GenericTextField("");
		JTextField tfPasswordLen = new GenericTextField("");
		JTextField tfPassword = new GenericTextField("");

		tfId.setPreferredSize(tfSize);
		tfEmail.setPreferredSize(tfSize);
		tfUsername.setPreferredSize(tfSize);
		tfPasswordLen.setPreferredSize(tfSize);
		tfPassword.setPreferredSize(tfSize);

		tfPasswordLen.setEditable(false);

		JCheckBox checkbox = new JCheckBox(currentLanguage.get(languages.STRING_GENERATE_RANDOM), false);

		JButton buttonConfirm = new TransparentButton(iconConfirm32);
		JButton buttonChangeCharset = new TransparentButton(iconSpanner32);

		JLabel labelChangeCharset = new JLabel("Charset");
		JLabel labelConfirm = new JLabel("Confirm");

		final int halfWidth = (SZ_WINDOW_WIDTH_DETAILS>>1);
		final int leftOffset = 40;

		final int labelFieldGap = 5;
		final int fieldNextLabelGap = 12;

		final int iconWest = (halfWidth - (iconConfirm32.getIconWidth()>>1));
		final int tfWest = (halfWidth - (SZ_TEXTFIELD_WIDTH_DETAILS>>1));

		spring.putConstraint(SpringLayout.WEST, iconContainer, iconWest-10, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, iconContainer, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, labelId, 10, SpringLayout.WEST, tfId);
		spring.putConstraint(SpringLayout.NORTH, labelId, 30, SpringLayout.SOUTH, iconContainer);

		spring.putConstraint(SpringLayout.WEST, tfId, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfId, labelFieldGap, SpringLayout.SOUTH, labelId);

		spring.putConstraint(SpringLayout.WEST, labelEmail, 10, SpringLayout.WEST, tfEmail);
		spring.putConstraint(SpringLayout.NORTH, labelEmail, fieldNextLabelGap, SpringLayout.SOUTH, tfId);

		spring.putConstraint(SpringLayout.WEST, tfEmail, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfEmail, labelFieldGap, SpringLayout.SOUTH, labelEmail);

		spring.putConstraint(SpringLayout.WEST, labelUsername, 10, SpringLayout.WEST, tfUsername);
		spring.putConstraint(SpringLayout.NORTH, labelUsername, fieldNextLabelGap, SpringLayout.SOUTH, tfEmail);

		spring.putConstraint(SpringLayout.WEST, tfUsername, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfUsername, labelFieldGap, SpringLayout.SOUTH, labelUsername);

		spring.putConstraint(SpringLayout.WEST, labelPassword, 10, SpringLayout.WEST, tfPassword);
		spring.putConstraint(SpringLayout.NORTH, labelPassword, fieldNextLabelGap, SpringLayout.SOUTH, tfUsername);

		spring.putConstraint(SpringLayout.EAST, checkbox, 0, SpringLayout.EAST, tfPassword);
		spring.putConstraint(SpringLayout.SOUTH, checkbox, -2, SpringLayout.NORTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, tfPassword, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPassword, labelFieldGap, SpringLayout.SOUTH, labelPassword);

		spring.putConstraint(SpringLayout.WEST, labelPasswordLen, 10, SpringLayout.WEST, tfPasswordLen);
		spring.putConstraint(SpringLayout.NORTH, labelPasswordLen, fieldNextLabelGap, SpringLayout.SOUTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, tfPasswordLen, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPasswordLen, labelFieldGap, SpringLayout.SOUTH, labelPasswordLen);

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, 20, SpringLayout.EAST, iconContainer);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 40, SpringLayout.SOUTH, tfPasswordLen);

		spring.putConstraint(SpringLayout.WEST, labelConfirm, 10, SpringLayout.EAST, buttonConfirm);
		spring.putConstraint(SpringLayout.NORTH, labelConfirm, 5, SpringLayout.NORTH, buttonConfirm);

		spring.putConstraint(SpringLayout.EAST, buttonChangeCharset, -10, SpringLayout.WEST, labelChangeCharset);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeCharset, 40, SpringLayout.SOUTH, tfPasswordLen);

		spring.putConstraint(SpringLayout.EAST, labelChangeCharset, -20, SpringLayout.WEST, iconContainer);
		spring.putConstraint(SpringLayout.NORTH, labelChangeCharset, 5, SpringLayout.NORTH, buttonChangeCharset);

		contentPane.add(iconContainer);
		contentPane.add(labelId);
		contentPane.add(tfId);
		contentPane.add(labelEmail);
		contentPane.add(tfEmail);
		contentPane.add(labelUsername);
		contentPane.add(tfUsername);
		contentPane.add(labelPassword);
		contentPane.add(tfPassword);
		contentPane.add(checkbox);
		contentPane.add(labelPasswordLen);
		contentPane.add(tfPasswordLen);
		contentPane.add(buttonConfirm);
		contentPane.add(labelConfirm);
		contentPane.add(buttonChangeCharset);
		contentPane.add(labelChangeCharset);

		frame.getRootPane().setDefaultButton(buttonConfirm);

		frame.addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowGainedFocus(WindowEvent event)
			{
				tfId.requestFocusInWindow();
			}
		});

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		checkbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				boolean isSelected = checkbox.isSelected();

				if (true == isSelected)
				{
					tfPassword.setText("");
					tfPassword.setEditable(false);
					tfPasswordLen.setEditable(true);
				}
				else
				{
					tfPassword.setEditable(true);
					tfPasswordLen.setEditable(false);
				}

				frame.revalidate();
				frame.repaint();

				return;
			}
		});

		buttonConfirm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				boolean usingRandomGenerator = checkbox.isSelected();
				String newPassword = null;

				if (true == usingRandomGenerator)
				{
					if (false == isValidLength(tfPasswordLen.getText()))
					{
						showErrorDialog(currentLanguage.get(languages.STRING_ERROR_INVALID_PASSWORD_LENGTH));
						return;
					}

					int len = Integer.parseInt(tfPasswordLen.getText());
					newPassword = createNewPassword(len);
				}
				else
				{
					int passwordLen = tfPassword.getText().length();

					if (passwordLen < 8 || passwordLen > 100)
					{
						showErrorDialog(currentLanguage.get(languages.STRING_ERROR_INVALID_PASSWORD_LENGTH));
						return;
					}

					newPassword = tfPassword.getText();
				}

				PasswordEntry newEntry = new PasswordEntry();


			/* XXX
			 *	Add option to randomly generate username.
			 */
				newEntry.setEmail(tfEmail.getText());
				newEntry.setUsername(tfUsername.getText());
				newEntry.setPassword(newPassword);
				newEntry.setTimestamp(System.currentTimeMillis());
				newEntry.setCharsetSize(characterSet.size());

				Object[] obj = null;

				if (false == fileContentsCached)
					getFileContents(passwordFile);

				newEntry.setHash(getUniqueId(newEntry));

				addNewPasswordEntry(tfId.getText(), newEntry);
				putPasswordEntries();

				addPasswordEntryToTree(tfId.getText(), newEntry);
				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_PASSWORD_CREATED));

				frame.revalidate();
				frame.dispose();
			}
		});

		buttonChangeCharset.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				showCharacterSet();
			}
		});
	}

	private void clearSelectedDetailsFields()
	{
		tfSelectedEmail.setText("");
		tfSelectedUsername.setText("");
		tfSelectedPasswordLen.setText("");
		tfSelectedPasswordAgeInDays.setText("");
		tfSelectedPassword.setText("");

		return;
	}

	/**
	 * Remove a password entry by its hash value.
	 * Since we can have multiple password entries
	 * for a given ID, we ensure we remove the right
	 * one by removing by hash value.
	 */
	private void doRemovePassword()
	{
		if (null == passwordEntries)
		{
			showErrorDialog(currentLanguage.get(languages.STRING_PROMPT_NO_PASSWORDS));
			return;
		}

		if (null == currentTreePath)
		{
			showErrorDialog("No password entry selected");
			return;
		}

		int action = showQuestionDialog(currentLanguage.get(languages.STRING_PROMPT_PASSWORD_WILL_BE_REMOVED));

		if (JOptionPane.CANCEL_OPTION == action)
			return;

		PDefaultMutableTreeNode n = (PDefaultMutableTreeNode)currentTreePath.getLastPathComponent();

		if (null == n)
			return;

		DefaultMutableTreeNode parent = (DefaultMutableTreeNode)n.getParent();

		if (null == parent)
			return;

	/*
	 * Remove the password entry from the data structure.
	 */
		PasswordEntry entry = n.getPasswordEntry();
		removeEntryForHash(entry.getHash());
		putPasswordEntries();

	/*
	 * If this password ID only has one entry,
	 * then remove the password ID node from
	 * the root node; otherwise, remove the
	 * leaf node from the password ID node.
	 */
		if (1 == parent.getChildCount())
		{
			((DefaultMutableTreeNode)parent.getParent()).remove(parent);
		}
		else
		{
			parent.remove(n);
		}

		treeModel.reload();
		clearSelectedDetailsFields();
		showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_PASSWORD_REMOVED));
	}

	private void doChangePasswordEntryDetails(String hash)
	{
		if (null == hash)
		{
			showErrorDialog("doChangePasswordEntryDetails(): received NULL argument");
			return;
		}

		if (null == passwordEntries)
		{
			showErrorDialog("No passwords");
			return;
		}

		if (-1 == currentlySelectedRow)
		{
			showErrorDialog("No selected row");
			return;
		}

		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();
		SpringLayout spring = new SpringLayout();

		//final int windowWidth = 650;
		//final int windowHeight = 700;
		//final int tfWidth = 450;
		//final int tfHeight = 25;

		frame.setSize(SZ_WINDOW_WIDTH_DETAILS, SZ_WINDOW_HEIGHT_DETAILS);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		contentPane.setLayout(spring);

		JLabel iconContainer = new JLabel(iconLocked64);

		JLabel labelEmail = new JLabel("Email");
		JLabel labelUsername = new JLabel(currentLanguage.get(languages.STRING_USERNAME));
		JLabel labelPassword = new JLabel(currentLanguage.get(languages.STRING_PASSWORD));
		JLabel labelPasswordLen = new JLabel(currentLanguage.get(languages.STRING_PASSWORD_LENGTH_MIN_MAX));

		Font fLabel = new Font("Courier New", Font.PLAIN, 16);

		labelEmail.setFont(fLabel);
		labelUsername.setFont(fLabel);
		labelPassword.setFont(fLabel);
		labelPasswordLen.setFont(fLabel);

		PasswordEntry entry = getEntryForHash(hash);

		JTextField tfEmail = new JTextField(entry.getEmail());
		JTextField tfUsername = new JTextField(entry.getUsername());
		JTextField tfPassword = new JTextField();
		JTextField tfPasswordLen = new JTextField();

		Dimension sizeTextField = new Dimension(SZ_TEXTFIELD_WIDTH_DETAILS, SZ_TEXTFIELD_HEIGHT_DETAILS);

		tfEmail.setFont(fontInput);
		tfUsername.setFont(fontInput);
		tfPassword.setFont(fontInput);
		tfPasswordLen.setFont(fontInput);

		tfEmail.setPreferredSize(sizeTextField);
		tfUsername.setPreferredSize(sizeTextField);
		tfPassword.setPreferredSize(sizeTextField);
		tfPasswordLen.setPreferredSize(sizeTextField);

		tfEmail.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfUsername.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfPassword.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfPasswordLen.setComponentPopupMenu(new RightClickPopup().getMenu());

	/*
 	 * Make the starting condition that none are being modified.
	 */
		tfEmail.setEditable(false);
		tfUsername.setEditable(false);
		tfPassword.setEditable(false);
		tfPasswordLen.setEditable(false);

		JCheckBox checkModifyEmail = new JCheckBox(currentLanguage.get(languages.STRING_CHANGE_EMAIL), false);
		JCheckBox checkModifyUsername = new JCheckBox(currentLanguage.get(languages.STRING_CHANGE_USERNAME), false);
		JCheckBox checkModifyPassword = new JCheckBox(currentLanguage.get(languages.STRING_CHANGE_PASSWORD), false);
		JCheckBox checkGenerateRandom = new JCheckBox(currentLanguage.get(languages.STRING_GENERATE_RANDOM), false);

		JButton buttonConfirm = new JButton(iconConfirm32);
		JButton buttonChangeCharset = new JButton(iconSpanner32);
		JLabel labelChangeCharset = new JLabel("Charset");
		JLabel labelConfirm = new JLabel("Confirm");

		buttonConfirm.setBackground(colorFrame);
		buttonConfirm.setBorder(null);

		buttonChangeCharset.setBackground(colorFrame);
		buttonChangeCharset.setBorder(null);

		final int halfWidth = (SZ_WINDOW_WIDTH_DETAILS>>1);
		final int leftOffset = 40;

		final int labelFieldGap = 5; // gap between a label and text field below it
		final int fieldNextLabelGap = 24; // gap between a textfield and the next label for the next textfield

		final int iconWest = (halfWidth - (iconConfirm32.getIconWidth()>>1));
		final int tfWest = (halfWidth - (SZ_TEXTFIELD_WIDTH_DETAILS>>1));

		spring.putConstraint(SpringLayout.WEST, iconContainer, iconWest-10, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, iconContainer, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, labelEmail, 10, SpringLayout.WEST, tfEmail);
		spring.putConstraint(SpringLayout.NORTH, labelEmail, 60, SpringLayout.SOUTH, iconContainer);

		spring.putConstraint(SpringLayout.WEST, tfEmail, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfEmail, labelFieldGap, SpringLayout.SOUTH, labelEmail);

		spring.putConstraint(SpringLayout.EAST, checkModifyEmail, 0, SpringLayout.EAST, tfEmail);
		spring.putConstraint(SpringLayout.SOUTH, checkModifyEmail, -5, SpringLayout.NORTH, tfEmail);

		spring.putConstraint(SpringLayout.WEST, labelUsername, 10, SpringLayout.WEST, tfUsername);
		spring.putConstraint(SpringLayout.NORTH, labelUsername, fieldNextLabelGap, SpringLayout.SOUTH, tfEmail);

		spring.putConstraint(SpringLayout.WEST, tfUsername, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfUsername, labelFieldGap, SpringLayout.SOUTH, labelUsername);

		spring.putConstraint(SpringLayout.EAST, checkModifyUsername, 0, SpringLayout.EAST, tfUsername);
		spring.putConstraint(SpringLayout.SOUTH, checkModifyUsername, -5, SpringLayout.NORTH, tfUsername);

		spring.putConstraint(SpringLayout.WEST, labelPassword, 10, SpringLayout.WEST, tfPassword);
		spring.putConstraint(SpringLayout.NORTH, labelPassword, fieldNextLabelGap, SpringLayout.SOUTH, tfUsername);

		spring.putConstraint(SpringLayout.WEST, tfPassword, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPassword, labelFieldGap, SpringLayout.SOUTH, labelPassword);

		spring.putConstraint(SpringLayout.EAST, checkModifyPassword, 0, SpringLayout.EAST, tfPassword);
		spring.putConstraint(SpringLayout.SOUTH, checkModifyPassword, -5, SpringLayout.NORTH, tfPassword);

		spring.putConstraint(SpringLayout.EAST, checkGenerateRandom, -150, SpringLayout.EAST, checkModifyPassword);
		spring.putConstraint(SpringLayout.SOUTH, checkGenerateRandom, -5, SpringLayout.NORTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, labelPasswordLen, 10, SpringLayout.WEST, tfPasswordLen);
		spring.putConstraint(SpringLayout.NORTH, labelPasswordLen, fieldNextLabelGap, SpringLayout.SOUTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, tfPasswordLen, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPasswordLen, labelFieldGap, SpringLayout.SOUTH, labelPasswordLen);

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, 20, SpringLayout.EAST, iconContainer);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 40, SpringLayout.SOUTH, tfPasswordLen);

		spring.putConstraint(SpringLayout.WEST, labelConfirm, 10, SpringLayout.EAST, buttonConfirm);
		spring.putConstraint(SpringLayout.NORTH, labelConfirm, 5, SpringLayout.NORTH, buttonConfirm);

		spring.putConstraint(SpringLayout.EAST, buttonChangeCharset, -10, SpringLayout.WEST, labelChangeCharset);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeCharset, 40, SpringLayout.SOUTH, tfPasswordLen);

		spring.putConstraint(SpringLayout.EAST, labelChangeCharset, -20, SpringLayout.WEST, iconContainer);
		spring.putConstraint(SpringLayout.NORTH, labelChangeCharset, 5, SpringLayout.NORTH, buttonChangeCharset);

		contentPane.add(iconContainer);
		contentPane.add(labelEmail);
		contentPane.add(tfEmail);
		contentPane.add(checkModifyEmail);
		contentPane.add(labelUsername);
		contentPane.add(tfUsername);
		contentPane.add(checkModifyUsername);
		//contentPane.add(labelPassword);
		contentPane.add(tfPassword);
		contentPane.add(checkModifyPassword);
		contentPane.add(checkGenerateRandom);
		contentPane.add(labelPasswordLen);
		contentPane.add(tfPasswordLen);
		contentPane.add(labelChangeCharset);
		contentPane.add(buttonChangeCharset);
		contentPane.add(labelConfirm);
		contentPane.add(buttonConfirm);

		addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowGainedFocus(WindowEvent event)
			{
				tfEmail.requestFocusInWindow();
			}
		});

		checkModifyEmail.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				boolean isSelected = ((JCheckBox)event.getSource()).isSelected();

				if (true == isSelected)
				{
					tfEmail.setEditable(true);
				}
				else
				{
					tfEmail.setEditable(false);
				}

				return;
			}
		});

		checkModifyUsername.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				boolean isSelected = ((JCheckBox)event.getSource()).isSelected();

				if (true == isSelected)
				{
					tfUsername.setEditable(true);
				}
				else
				{
					tfUsername.setEditable(false);
				}

				return;
			}
		});

		checkModifyPassword.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				boolean isSelected = ((JCheckBox)event.getSource()).isSelected();

				if (true == isSelected)
				{
					tfPassword.setEditable(true);
					checkGenerateRandom.setEnabled(true);
				}
				else
				{
					tfPassword.setText("");
					tfPassword.setEditable(false);
					tfPasswordLen.setText("");
					tfPasswordLen.setEditable(false);
					checkGenerateRandom.setSelected(false);
					checkGenerateRandom.setEnabled(false);

					//frame.repaint();
					frame.revalidate();
				}

				return;
			}
		});

		checkGenerateRandom.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				boolean isSelected = ((JCheckBox)event.getSource()).isSelected();

				if (true == isSelected)
				{
					tfPassword.setText("");
					tfPassword.setEditable(false);
					tfPasswordLen.setText("");
					tfPasswordLen.setEditable(true);
				}
				else
				{
					tfPassword.setEditable(true);
					tfPasswordLen.setText("");
					tfPasswordLen.setEditable(false);
				}

				frame.revalidate();
				frame.repaint();
			}
		});

		buttonConfirm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				String newEmail = null;
				String newUsername = null;
				String newPassword = null;
				String passwordLen = null;
				String oldPassword = null;

				if (-1 == currentlySelectedRow)
				{
					showErrorDialog("No row selected");
					return;
				}

				String hash = (String)table.getValueAt(currentlySelectedRow, IDX_UNIQUE_ID);
				PasswordEntry entry = getEntryForHash(hash);
				oldPassword = entry.getPassword();

				String oldEmail = null;

				if (true == checkModifyEmail.isSelected())
				{
					oldEmail = entry.getEmail();
					newEmail = tfEmail.getText();
					if (0 == newEmail.length())
					{
						showErrorDialog("Email required");
						return;
					}
				}

				if (true == checkModifyUsername.isSelected())
				{
					newUsername = tfUsername.getText();

					if (0 == newUsername.length())
					{
						showErrorDialog("Username required");
						return;
					}
				}

				if (true == checkModifyPassword.isSelected())
				{
					if (true == checkGenerateRandom.isSelected())
					{
						if (false == isValidLength(tfPasswordLen.getText()))
						{
							showErrorDialog(currentLanguage.get(languages.STRING_ERROR_INVALID_PASSWORD_LENGTH));
							return;
						}

						int passLen = Integer.parseInt(tfPasswordLen.getText());

						newPassword = createNewPassword(passLen);
					}
					else
					{
						newPassword = tfPassword.getText();
						int passLen = newPassword.length();

						final int PASSWORD_MIN_LEN = 8;
						final int PASSWORD_MAX_LEN = 100;
						if (passLen < PASSWORD_MIN_LEN || passLen > PASSWORD_MAX_LEN)
						{
							showErrorDialog(currentLanguage.get(languages.STRING_ERROR_INVALID_PASSWORD_LENGTH));
							return;
						}
					}
				}

				String oldHash = entry.getHash();

				if (null != newEmail)
				{
					entry.setEmail(newEmail);
					entry.setHash(getUniqueId(entry));

					model.setValueAt(newEmail, currentlySelectedRow, IDX_EMAIL);
				}

				if (null != newUsername)
				{
					entry.setUsername(newUsername);
					entry.setHash(getUniqueId(entry));

					model.setValueAt(newUsername, currentlySelectedRow, IDX_USERNAME);
				}

			/*
			 * The hash is based on email, username, and timestamp
			 */
				if (oldHash != entry.getHash())
					model.setValueAt(entry.getHash(), currentlySelectedRow, IDX_UNIQUE_ID);

				if (null != newPassword)
				{
					entry.setPassword(newPassword);
					entry.setTimestamp(System.currentTimeMillis());

					model.setValueAt(newPassword, currentlySelectedRow, IDX_PASSWORD);
					model.setValueAt(newPassword.length(), currentlySelectedRow, IDX_PASSWORD_LENGTH);
				}

				updatePasswordEntryInTree(
					(String)model.getValueAt(currentlySelectedRow, IDX_PASSWORD_ID),
					oldEmail,
					entry
				);

				putPasswordEntry(oldHash, entry); // replace old entry in TreeMap
				putPasswordEntries(); // encrypt the data and write to password file

				revalidate();
				repaint();

				//if (null == newPassword)
				//{
				//	showPasswordDetails(entry);
				//}
				// XXX show changed password details

				frame.dispose();
			}
		});

		buttonChangeCharset.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				showCharacterSet();
			}
		});

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private void printPasswordEntries()
	{
		Set<Map.Entry<String,ArrayList<PasswordEntry>>> set = passwordEntries.entrySet();
		Iterator<Map.Entry<String,ArrayList<PasswordEntry>>> iter = set.iterator();
		Iterator<PasswordEntry> it = null;
		ArrayList<PasswordEntry> list = null;

		while (iter.hasNext())
		{
			Map.Entry<String,ArrayList<PasswordEntry>> e = iter.next();
			list = e.getValue();
			it = list.iterator();

			System.out.println(e.getKey() + " (" + list.size() + " entries)");
			while (it.hasNext())
			{
				PasswordEntry entry = it.next();
				System.out.println(
					entry.getEmail() + "\n" +
					entry.getUsername() + "\n" +
					entry.getPassword() + "\n"
				);
			}
		}
	}

	private static int passwordAttempts = 0;

	private void unlockPasswordFile()
	{
		Container contentPane = getContentPane();
		SpringLayout spring = new SpringLayout();

		ImageIcon icon = iconSecret128;

		JPasswordField passField = new JPasswordField();
		JButton buttonConfirm = new JButton(iconUnlocked32);
		JLabel containerIcon = new JLabel(icon);
		JTextArea taInfo = new JTextArea(currentLanguage.get(languages.STRING_PROMPT_UNLOCK_PASSWORD_FILE));

		final int windowWidth = 620;
		final int windowHeight = 250;
		final int passFieldWidth = 320;
		final int passFieldHeight = 35;
		final int buttonWidth = 40;
		final int buttonHeight = 35;
		//final int combinedWidth = passFieldWidth + buttonWidth;

		Dimension sizePassField = new Dimension(passFieldWidth, passFieldHeight);
		Dimension sizeButtonConfirm = new Dimension(buttonWidth, buttonHeight);

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setTitle("PassMan v1.1");
		setSize(windowWidth, windowHeight);

		contentPane.setLayout(spring);

		buttonConfirm.setPreferredSize(sizeButtonConfirm);
		buttonConfirm.setBackground(colorButton);
		//buttonConfirm.setBorder(null);

		taInfo.setEditable(false);
		taInfo.setBorder(null);
		taInfo.setBackground(contentPane.getBackground());
		taInfo.setFont(fontLargePrompt);

		passField.setPreferredSize(sizePassField);
		passField.setFont(fontInput);

		int iconWidth = icon.getIconWidth();
		int iconHeight = icon.getIconHeight();

		spring.putConstraint(SpringLayout.WEST, containerIcon, 40, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, containerIcon, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, taInfo, 25, SpringLayout.EAST, containerIcon);
		spring.putConstraint(SpringLayout.NORTH, taInfo, 10, SpringLayout.NORTH, containerIcon);

		spring.putConstraint(SpringLayout.WEST, passField, 25, SpringLayout.EAST, containerIcon);
		spring.putConstraint(SpringLayout.NORTH, passField, 20, SpringLayout.SOUTH, taInfo);

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, 0, SpringLayout.EAST, passField);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 20, SpringLayout.SOUTH, taInfo);

		contentPane.add(containerIcon);
		contentPane.add(taInfo);
		contentPane.add(passField);
		contentPane.add(buttonConfirm);

		addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowGainedFocus(WindowEvent event)
			{
				passField.requestFocusInWindow();
			}
		});

		getRootPane().setDefaultButton(buttonConfirm);
		setLocationRelativeTo(null);
		setVisible(true);

		buttonConfirm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				password = new String(passField.getPassword());
				int passLen = password.length();

				if (passLen < 8 || passLen > 100)
				{
					showErrorDialog("Invalid password length");
					return;
				}

				getFileContents(passwordFile);
				//AESCrypt aes = new AESCrypt();

/*
				try
				{
					byte[] data = sdata.getBytes("UTF-8");
					ObjectMapper mapper = new ObjectMapper();
					com.fasterxml.jackson.core.type.TypeReference typeRef = new com.fasterxml.jackson.core.type.TypeReference<TreeMap<String,ArrayList<PasswordEntry>>>() {};

					passwordEntries = (TreeMap<String,ArrayList<PasswordEntry>>)mapper.readValue(data, typeRef);
					printPasswordEntries();
				}
				catch (Exception e)
				{
					System.err.println(e.getMessage());
				}
*/

				if (null == fContents)
				{
					password = null;
					++passwordAttempts;

					if (passwordAttempts > 2)
					{
						showErrorDialog(currentLanguage.get(languages.STRING_ERROR_TOO_MANY_ATTEMPTS));
						System.exit(1);
					}

					showErrorDialog(currentLanguage.get(languages.STRING_ERROR_INCORRECT_PASSWORD));
					return;
				}

				//fileContentsCached = true;
				//createPasswordEntriesMap();

				remove(containerIcon);
				remove(taInfo);
				remove(passField);
				remove(buttonConfirm);

				dispose();
				setupGUI();
			}
		});
	}

/*
	XXX

	This was the original planned main window layout.
	Keeping for nostalgic purposes...

		-------------------------------------------------------------------------------------------------


                                          Password Ids         Created         Status (! stale)
		       --------         --------------------------------------------------------------------
		       | Get  |         |   facebook        01 Jan 2020, 12:43  [ ! ]   [*] <= selected   ||
		       --------         |   some_id         09 Mar 2020, 17:25          [ ]               || {scroller}
		       --------         |   gab.ai          12 Jul 2019, 18:11  [ ! ]   [ ]               ||
		       | Add  |         |   newgrounds      17 Jun 1999, 10:57  [ ! ]   [ ]               ||
		       --------         |-------------------------------------------------------------------
		       --------
		       | Rem  |
		       --------                       ----------
		       --------                       | Random |  | 100 | <= length
		       | Chg  |                       ----------
		       --------

		-------------------------------------------------------------------------------------------------
*/

	private JButton currentlySelectedLanguage = null;

	private void setSelectedLanguage(JButton language)
	{
		if (null != currentlySelectedLanguage)
		{
			currentlySelectedLanguage.setBackground(colorButtonDeselected);
		}

		if (currentlySelectedLanguage == language)
		{
			currentlySelectedLanguage = null;
			return;
		}

		currentlySelectedLanguage = language;
		currentlySelectedLanguage.setBackground(colorButtonSelected);
	}

	private class buttonSelectLanguageListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent event)
		{
			setSelectedLanguage((JButton)event.getSource());
		}
	}

	private void showDownloadBackupWindow()
	{
		JFrame frame = new JFrame();
		SpringLayout spring = new SpringLayout();
		Container contentPane = frame.getContentPane();

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(420, 400);
		frame.setTitle("Remote Server Backups"); // currentLanguage.get(languages.STRING_TITLE_BACKUP_PASSWORD_FILE);

		JTextArea taDesc = new JTextArea(
			"Retrieve a backup file (if any) from\n" +
			"a storage server.");

		taDesc.setEditable(false);
		taDesc.setBackground(frame.getBackground());
		taDesc.setFont(fontTextField);

		JButton buttonGDrive = new JButton(iconGDrive32);

		buttonGDrive.setBackground(colorFrame);
		buttonGDrive.setBorder(null);

		JLabel labelGDrive = new JLabel("Google Drive");
		labelGDrive.setFont(fontLabel);

		int sizeIconWidth = iconGDrive32.getIconWidth();
		int sizeIconHeight = iconGDrive32.getIconHeight();

		spring.putConstraint(SpringLayout.WEST, taDesc, 40, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, taDesc, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, buttonGDrive, 20, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonGDrive, 20, SpringLayout.SOUTH, taDesc);

		spring.putConstraint(SpringLayout.WEST, labelGDrive, 10, SpringLayout.EAST, buttonGDrive);
		spring.putConstraint(SpringLayout.NORTH, labelGDrive, 20, SpringLayout.SOUTH, taDesc);

		// set content pane layout
		contentPane.setLayout(spring);

		// add components
		contentPane.add(taDesc);
		contentPane.add(buttonGDrive);
		contentPane.add(labelGDrive);

		//frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		buttonGDrive.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				try
				{
					GDriveBackup gbackup = new GDriveBackup();

					int action = showQuestionDialog(
						"Warning: This will overwrite the local password file! Do you wish to continue?");

				 	if (JOptionPane.CANCEL_OPTION == action)
				 		return;

				 	gbackup.doDownloadBackup(passwordFile);
					showInfoDialog("Retrieved backup file from GDrive");
				}
				catch (IOException e1)
				{
					showErrorDialog(e1.getMessage());
				}
				catch (GeneralSecurityException e2)
				{
					showErrorDialog(e2.getMessage());
				}

				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_CREATED_BACKUP_FILE));
			}
		});
	}

	private void showBackupWindow()
	{
		JFrame frame = new JFrame();
		SpringLayout spring = new SpringLayout();
		Container contentPane = frame.getContentPane();

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(420, 400);
		frame.setTitle("Backup Password File"); // currentLanguage.get(languages.STRING_TITLE_BACKUP_PASSWORD_FILE);

		JTextArea taDesc = new JTextArea(
			"Backup your password file to a remote\n" +
			"storage server.\n");

		taDesc.setEditable(false);
		taDesc.setBackground(frame.getBackground());
		taDesc.setFont(fontTextField);

		JButton buttonGDrive = new JButton(iconGDrive32);

		buttonGDrive.setBackground(colorFrame);
		buttonGDrive.setBorder(null);

		JLabel labelGDrive = new JLabel("Google Drive");
		labelGDrive.setFont(fontLabel);

		int sizeIconWidth = iconGDrive32.getIconWidth();
		int sizeIconHeight = iconGDrive32.getIconHeight();

		spring.putConstraint(SpringLayout.WEST, taDesc, 40, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, taDesc, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, buttonGDrive, 20, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonGDrive, 20, SpringLayout.SOUTH, taDesc);

		spring.putConstraint(SpringLayout.WEST, labelGDrive, 10, SpringLayout.EAST, buttonGDrive);
		spring.putConstraint(SpringLayout.NORTH, labelGDrive, 5, SpringLayout.NORTH, buttonGDrive);

		// set content pane layout
		contentPane.setLayout(spring);

		// add components
		contentPane.add(taDesc);
		contentPane.add(buttonGDrive);
		contentPane.add(labelGDrive);

		//frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		buttonGDrive.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				try
				{
					GDriveBackup gbackup = new GDriveBackup();
					gbackup.doFileBackup(passwordFile);
				}
				catch (IOException e1)
				{
					showErrorDialog(e1.getMessage());
				}
				catch (GeneralSecurityException e2)
				{
					showErrorDialog(e2.getMessage());
				}

				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_CREATED_BACKUP_FILE));
			}
		});
	}

	private int getAgeInDaysFromTimestamp(long ts)
	{
		return (int)((System.currentTimeMillis() - ts) / MILLIS_PER_DAY);
	}

	/**
	 * Create a tree showing the password IDs and
	 * password entries as their child nodes.
	 */
	private JTree createPasswordEntryTree()
	{
		Set<Map.Entry<String,ArrayList<PasswordEntry>>> set = passwordEntries.entrySet();
		Iterator<Map.Entry<String,ArrayList<PasswordEntry>>> iter = set.iterator();
		Map.Entry<String,ArrayList<PasswordEntry>> entry = null;
		ArrayList<PasswordEntry> list = null;
		Iterator<PasswordEntry> it = null;

		//PVector<Object> root = new PVector<Object>("Password IDs");
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(currentLanguage.get(languages.STRING_PASSWORD_IDS));

		while (iter.hasNext())
		{
			entry = iter.next();
			list = entry.getValue();
			it = list.iterator();

			DefaultMutableTreeNode nid = new DefaultMutableTreeNode(entry.getKey());
			//PVector<Object> v1 = new PVector<Object>(entry.getKey());

			while (it.hasNext())
			{
				PasswordEntry e = it.next();

				nid.add(new PDefaultMutableTreeNode(e));
				//PVector<Object> v2 = new PVector<Object>(e.getEmail());
				//v2.add(e.getUsername());
				//v2.add(e.getPassword());
				//v2.add(getAgeInDaysFromTimestamp(e.getTimestamp()) + " days old");
				//v2.add("Hash: " + e.getHash());
				//v1.add(v2);
			}

			root.add(nid);
		}

	/*
	 * TREE and TREEMODEL are global variables.
	 */
		tree = new JTree(root);

		tree
			.getSelectionModel()
			.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

		treeModel = (DefaultTreeModel)tree.getModel();
		//tree.setRootVisible(true);
		tree.setFont(new Font("Times New Roman", Font.PLAIN, 22));
		tree.addTreeSelectionListener(new PTreeSelectionListener());

		return tree;
	}

	private TreePath currentTreePath = null;

	private class PTreeSelectionListener implements javax.swing.event.TreeSelectionListener
	{
		public PTreeSelectionListener() { super(); }

		@Override
		public void valueChanged(javax.swing.event.TreeSelectionEvent event)
		{
			TreePath p = event.getNewLeadSelectionPath();

			if (null == p)
				return;

			currentTreePath = p;
			DefaultMutableTreeNode __n = (DefaultMutableTreeNode)p.getLastPathComponent();

			if (null == __n || false == __n.isLeaf())
			{
					clearSelectedDetailsFields();
					return;
			}

			PDefaultMutableTreeNode n = (PDefaultMutableTreeNode)__n;

			PasswordEntry entry = n.getPasswordEntry();
			if (null == entry)
				return;

			tfSelectedPasswordLen.setText("" + entry.getPassword().length());
			tfSelectedPassword.setText(entry.getPassword());
			tfSelectedEmail.setText(entry.getEmail());
			tfSelectedUsername.setText(entry.getUsername());;
			tfSelectedPasswordAgeInDays.setText("" + getAgeInDaysFromTimestamp(entry.getTimestamp()));

			revalidate();
		}
	}

	public void setupGUI()
	{
/*
 * Define the sizes of the components within the frame and calculate
 * the frame width and height based on these sizes.
 */
		final int ICON_WIDTH = iconUnlocked128.getIconWidth();
		final int ICON_HEIGHT = iconUnlocked128.getIconHeight();

		final int VERTICAL_GAP = 40;

		final int PANEL_SCROLLBAR_GAP = 40;
		final int FRAME_MARGIN = 50;
		final int TREE_SCROLLBAR_WIDTH = 400;
		final int TREE_SCROLLBAR_HEIGHT = 500;
		//final int TOOLBAR_WIDTH = 350;
		final int PANEL_BUTTONS_HEIGHT = 50;
		final int PANEL_DETAILS_WIDTH = 480;
		final int PANEL_DETAILS_HEIGHT = TREE_SCROLLBAR_HEIGHT - PANEL_BUTTONS_HEIGHT;
		final int FRAME_WIDTH = (FRAME_MARGIN<<1) + TREE_SCROLLBAR_WIDTH + PANEL_SCROLLBAR_GAP + PANEL_DETAILS_WIDTH;
		final int FRAME_HEIGHT = ICON_HEIGHT + VERTICAL_GAP + FRAME_MARGIN + (FRAME_MARGIN<<1) + TREE_SCROLLBAR_HEIGHT;

		final int TF_DETAILS_WIDTH = 400;
		final int TF_DETAILS_HEIGHT = 27;

		final int HALF_FRAME_WIDTH = (FRAME_WIDTH>>1);

		SpringLayout spring = new SpringLayout();
		Container contentPane = getContentPane();

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setTitle(currentLanguage.get(languages.STRING_APPLICATION_NAME) + " v" + VERSION);
		setSize(FRAME_WIDTH, FRAME_HEIGHT);

		contentPane.setBackground(colorFrame);

		JPanel panelButtons = new JPanel();
		JLabel unlockedContainer = new JLabel(iconShield128);

		JButton buttonAdd = new GenericButton(iconAdd32);
		JButton buttonView = new GenericButton(iconView32);
		JButton buttonChange = new GenericButton(iconChange32);
		JButton buttonSet = new GenericButton(iconCog32);
		JButton buttonUpload = new GenericButton(iconUpload32);
		JButton buttonDownload = new GenericButton(iconDownload32);
		JButton buttonRemove = new GenericButton(iconBin32);

		final int nrButtons = 8;
		final int buttonsPerRow = 8;

		panelButtons.add(buttonAdd);
		panelButtons.add(buttonView);
		panelButtons.add(buttonChange);
		panelButtons.add(buttonUpload);
		panelButtons.add(buttonDownload);
		panelButtons.add(buttonRemove);

// global var
		taAppName = new JTextArea(currentLanguage.get(languages.STRING_APPLICATION_NAME));

		taAppName.setFont(new Font("Tahoma", Font.BOLD, 45));
		taAppName.setForeground(new Color(32, 32, 32));
		taAppName.setBackground(contentPane.getBackground());
		taAppName.setBorder(null);
		taAppName.setEditable(false);

/*
		final String[] columnNames = {
			"Password ID",
			"Email",
			"Username",
			"Password",
			"Password Length",
			"Age (days)",
			"Unique ID"
		};

		model = new PTableModel(columnNames, 0); // global var

		Set<Map.Entry<String,ArrayList<PasswordEntry>>> eSet = passwordEntries.entrySet();
		Iterator<Map.Entry<String,ArrayList<PasswordEntry>>> iter = eSet.iterator();
		Iterator<PasswordEntry> it = null;

		while (iter.hasNext())
		{
			Map.Entry<String,ArrayList<PasswordEntry>> entry = iter.next();
			it = entry.getValue().iterator();

			while (it.hasNext())
			{
				PasswordEntry ent = it.next();

				try
				{
					ent.setHash(getMD5(ent.getUsername() + ent.getTimestamp()));
				}
				catch (Exception e)
				{
					System.err.println(e.getMessage());
				}

				model.addRow(new Object[] {
					entry.getKey(),
					ent.getEmail(),
					ent.getUsername(),
					ent.getPassword(),
					ent.getPassword().length(),
					((System.currentTimeMillis() - ent.getTimestamp())/MILLIS_PER_DAY),
					ent.getHash()
				});
			}
		}

		table = new JTable(model);
		table.setFont(new Font("Courier New", Font.PLAIN, 20));

		TableCellRenderer renderer = new PTableCellRenderer();
		table.setDefaultRenderer(Object.class, renderer);

		JScrollPane scrollPane = new JScrollPane(
			table,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
		);
*/

		JTree tree = createPasswordEntryTree();
		JScrollPane sp = new JScrollPane(
			tree,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
		);



		//final int tableWidth = FRAME_WIDTH-spWidth-80;
		//final int tableHeight = spHeight;

		//scrollPane.setPreferredSize(new Dimension(tableWidth, tableHeight));
		sp.setPreferredSize(new Dimension(TREE_SCROLLBAR_WIDTH, TREE_SCROLLBAR_HEIGHT));

		//table
		//	.getTableHeader()
		//	.setFont(fontLabel);

		JPanel panelSelected = new JPanel();
		panelSelected.setPreferredSize(new Dimension(PANEL_DETAILS_WIDTH, PANEL_DETAILS_HEIGHT));
		SpringLayout spring2 = new SpringLayout();

		panelSelected.setLayout(spring2);

		Dimension tfsSize = new Dimension(TF_DETAILS_WIDTH, TF_DETAILS_HEIGHT);

		tfSelectedEmail = new TransparentTextField("");
		tfSelectedUsername = new TransparentTextField("");
		tfSelectedPasswordLen = new TransparentTextField("");
		tfSelectedPasswordAgeInDays = new TransparentTextField("");
		tfSelectedPassword = new TransparentTextField("");

		JLabel labelSelectedEmail = new GenericLabel(currentLanguage.get(languages.STRING_EMAIL));
		JLabel labelSelectedUsername = new GenericLabel(currentLanguage.get(languages.STRING_USERNAME));
		JLabel labelSelectedPasswordLen = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD_LENGTH));
		JLabel labelSelectedPasswordAgeInDays = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD_AGE_DAYS));
		JLabel labelSelectedPassword = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD));

		JButton buttonCopyEmail = new TransparentButton(iconCopy32);
		JButton buttonCopyUsername = new TransparentButton(iconCopy32);
		JButton buttonCopyPassword = new TransparentButton(iconCopy32);

		buttonCopyEmail.setToolTipText(currentLanguage.get(languages.STRING_COPY_EMAIL));
		buttonCopyUsername.setToolTipText(currentLanguage.get(languages.STRING_COPY_USERNAME));
		buttonCopyPassword.setToolTipText(currentLanguage.get(languages.STRING_COPY_PASSWORD));

		buttonCopyEmail.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				Toolkit
					.getDefaultToolkit()
					.getSystemClipboard()
					.setContents(new StringSelection(tfSelectedEmail.getText()), null);

				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_EMAIL_COPIED));
			}
		});

		buttonCopyUsername.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				Toolkit
					.getDefaultToolkit()
					.getSystemClipboard()
					.setContents(new StringSelection(tfSelectedUsername.getText()), null);

				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_USERNAME_COPIED));
			}
		});

		buttonCopyPassword.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				Toolkit
					.getDefaultToolkit()
					.getSystemClipboard()
					.setContents(new StringSelection(tfSelectedPassword.getText()), null);

				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_PASSWORD_COPIED));
			}
		});

	/*
	 * The layout of the labels and textfields within the panel which will
	 * display the details of the selected leaf node in the JTree.
	 */
		final int LABEL_FIELD_GAP = 10;
		final int FIELD_NEXT_LABEL_GAP = 20;
		final int WEST_GAP = 20;
		final int LABEL_LEFT = 10;
		final int TEXTFIELD_BUTTON_GAP = 20;

		spring2.putConstraint(SpringLayout.NORTH, labelSelectedEmail, 20, SpringLayout.NORTH, panelSelected);
		spring2.putConstraint(SpringLayout.WEST, labelSelectedEmail, LABEL_LEFT, SpringLayout.WEST, tfSelectedEmail);

		spring2.putConstraint(SpringLayout.NORTH, tfSelectedEmail, LABEL_FIELD_GAP, SpringLayout.SOUTH, labelSelectedEmail);
		spring2.putConstraint(SpringLayout.WEST, tfSelectedEmail, WEST_GAP, SpringLayout.WEST, panelSelected);

		spring2.putConstraint(SpringLayout.NORTH, buttonCopyEmail, 0, SpringLayout.NORTH, tfSelectedEmail);
		spring2.putConstraint(SpringLayout.WEST, buttonCopyEmail, TEXTFIELD_BUTTON_GAP, SpringLayout.EAST, tfSelectedEmail);

		spring2.putConstraint(SpringLayout.NORTH, labelSelectedUsername, FIELD_NEXT_LABEL_GAP, SpringLayout.SOUTH, tfSelectedEmail);
		spring2.putConstraint(SpringLayout.WEST, labelSelectedUsername, LABEL_LEFT, SpringLayout.WEST, tfSelectedUsername);

		spring2.putConstraint(SpringLayout.NORTH, tfSelectedUsername, LABEL_FIELD_GAP, SpringLayout.SOUTH, labelSelectedUsername);
		spring2.putConstraint(SpringLayout.WEST, tfSelectedUsername, WEST_GAP, SpringLayout.WEST, panelSelected);

		spring2.putConstraint(SpringLayout.NORTH, buttonCopyUsername, 0, SpringLayout.NORTH, tfSelectedUsername);
		spring2.putConstraint(SpringLayout.WEST, buttonCopyUsername, TEXTFIELD_BUTTON_GAP, SpringLayout.EAST, tfSelectedUsername);

		spring2.putConstraint(SpringLayout.NORTH, labelSelectedPasswordLen, FIELD_NEXT_LABEL_GAP, SpringLayout.SOUTH, tfSelectedUsername);
		spring2.putConstraint(SpringLayout.WEST, labelSelectedPasswordLen, LABEL_LEFT, SpringLayout.WEST, tfSelectedPasswordLen);

		spring2.putConstraint(SpringLayout.NORTH, tfSelectedPasswordLen, LABEL_FIELD_GAP, SpringLayout.SOUTH, labelSelectedPasswordLen);
		spring2.putConstraint(SpringLayout.WEST, tfSelectedPasswordLen, WEST_GAP, SpringLayout.WEST, panelSelected);

		spring2.putConstraint(SpringLayout.NORTH, labelSelectedPasswordAgeInDays, FIELD_NEXT_LABEL_GAP, SpringLayout.SOUTH, tfSelectedPasswordLen);
		spring2.putConstraint(SpringLayout.WEST, labelSelectedPasswordAgeInDays, LABEL_LEFT, SpringLayout.WEST, tfSelectedPasswordAgeInDays);

		spring2.putConstraint(SpringLayout.NORTH, tfSelectedPasswordAgeInDays, LABEL_FIELD_GAP, SpringLayout.SOUTH, labelSelectedPasswordAgeInDays);
		spring2.putConstraint(SpringLayout.WEST, tfSelectedPasswordAgeInDays, WEST_GAP, SpringLayout.WEST, panelSelected);

		spring2.putConstraint(SpringLayout.NORTH, labelSelectedPassword, FIELD_NEXT_LABEL_GAP, SpringLayout.SOUTH, tfSelectedPasswordAgeInDays);
		spring2.putConstraint(SpringLayout.WEST, labelSelectedPassword, LABEL_LEFT, SpringLayout.WEST, tfSelectedPassword);

		spring2.putConstraint(SpringLayout.NORTH, tfSelectedPassword, LABEL_FIELD_GAP, SpringLayout.SOUTH, labelSelectedPassword);
		spring2.putConstraint(SpringLayout.WEST, tfSelectedPassword, WEST_GAP, SpringLayout.WEST, panelSelected);

		spring2.putConstraint(SpringLayout.NORTH, buttonCopyPassword, 0, SpringLayout.NORTH, tfSelectedPassword);
		spring2.putConstraint(SpringLayout.WEST, buttonCopyPassword, TEXTFIELD_BUTTON_GAP, SpringLayout.EAST, tfSelectedPassword);

		panelSelected.add(labelSelectedEmail);
		panelSelected.add(tfSelectedEmail);
		panelSelected.add(buttonCopyEmail);
		panelSelected.add(labelSelectedUsername);
		panelSelected.add(tfSelectedUsername);
		panelSelected.add(buttonCopyUsername);
		panelSelected.add(labelSelectedPasswordLen);
		panelSelected.add(tfSelectedPasswordLen);
		panelSelected.add(labelSelectedPasswordAgeInDays);
		panelSelected.add(tfSelectedPasswordAgeInDays);
		panelSelected.add(labelSelectedPassword);
		panelSelected.add(tfSelectedPassword);
		panelSelected.add(buttonCopyPassword);

		//scrollPane.getHorizontalScrollBar().setUI(new javax.swing.plaf.synth.SynthScrollBarUI());
		//scrollPane.getVerticalScrollBar().setUI(new javax.swing.plaf.synth.SynthScrollBarUI());

		spring.putConstraint(SpringLayout.WEST, unlockedContainer, HALF_FRAME_WIDTH-(ICON_WIDTH+80), SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, unlockedContainer, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, taAppName, 20, SpringLayout.EAST, unlockedContainer);
		spring.putConstraint(SpringLayout.NORTH, taAppName, (ICON_HEIGHT>>1)-20, SpringLayout.NORTH, unlockedContainer);

		spring.putConstraint(SpringLayout.WEST, sp, FRAME_MARGIN, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, sp, VERTICAL_GAP, SpringLayout.SOUTH, unlockedContainer);

		spring.putConstraint(SpringLayout.WEST, panelSelected, PANEL_SCROLLBAR_GAP, SpringLayout.EAST, sp);
		spring.putConstraint(SpringLayout.NORTH, panelSelected, 0, SpringLayout.NORTH, sp);

		//spring.putConstraint(SpringLayout.WEST, scrollPane, 0, SpringLayout.EAST, sp);
		//spring.putConstraint(SpringLayout.NORTH, scrollPane, 0, SpringLayout.NORTH, sp);

		spring.putConstraint(SpringLayout.WEST, panelButtons, PANEL_SCROLLBAR_GAP, SpringLayout.EAST, sp);
		spring.putConstraint(SpringLayout.SOUTH, panelButtons, 0, SpringLayout.SOUTH, sp);

		contentPane.setLayout(spring);

		contentPane.add(unlockedContainer);
		contentPane.add(taAppName);
		//contentPane.add(scrollPane);
		contentPane.add(sp);
		contentPane.add(panelSelected);
		//contentPane.add(panelButtons);
		contentPane.add(panelButtons);

		setLocationRelativeTo(null);
		setVisible(true);

		revalidate();
		repaint();

		if (null == fContents || false == fileContentsCached)
		{
			doInitialConfiguration();
		}

		buttonSet.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				showSettings();
			}
		});

		buttonAdd.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				doAddNewPassword();
				return;
			}
		});

		buttonView.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				JTextField tf = null;

				if (-1 == currentlySelectedRow)
					return;

				tf = new JTextField((String)table.getValueAt(currentlySelectedRow, IDX_PASSWORD));

				tf.setEditable(false);
				tf.setFont(new Font("Courier New", Font.PLAIN, 20));
				tf.setComponentPopupMenu(new RightClickPopup().getMenu());
				String qid = (String)table.getValueAt(currentlySelectedRow, IDX_UNIQUE_ID);

				Object[] opts = { "OK" };
				JOptionPane.showOptionDialog(
					null,
					(Object)tf,
					"Password for " + qid,
					1,
					JOptionPane.INFORMATION_MESSAGE,
					null, // icon
					opts,
					opts[0]
				);
			}
		});

		buttonRemove.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				if (null == currentTreePath)
					return;
				
				doRemovePassword();
			}
		});

		buttonChange.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				if (-1 == currentlySelectedRow)
					return;

				doChangePasswordEntryDetails((String)table.getValueAt(currentlySelectedRow, IDX_UNIQUE_ID));
			}
		});

		buttonUpload.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				showBackupWindow();	
			}
		});

		buttonDownload.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				showDownloadBackupWindow();
			}
		});

/*
		table
			.getSelectionModel()
			.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent event)
			{
				if (true == event.getValueIsAdjusting())
					return;

				currentlySelectedRow = table.getSelectedRow();
			}
		});

		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e)
			{
				int row = table.rowAtPoint(e.getPoint());
				if (row >= 0 && row < table.getRowCount())
					table.setRowSelectionInterval(row, row);
				else
					table.clearSelection();

				int idx = table.getSelectedRow();

				if (idx < 0)
					return;

				if (e.getComponent() instanceof JTable)
				{
					JPopupMenu pop = new RightClickPopup().getMenu();
					pop.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
*/
	}

	private void doLanguageConfiguration()
	{
		Container contentPane = getContentPane();
		SpringLayout spring = new SpringLayout();
		final int windowWidth = 500;
		final int windowHeight = 650;
		final int scrollPaneWidth = (windowWidth-40);
		final int scrollPaneHeight = 200;

		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(windowWidth, windowHeight);

		contentPane.setLayout(spring);

		JLabel containerIconSettings = new JLabel(iconSettings128);

		JButton buttonEnglish = new JButton("English");
		JButton buttonFrench = new JButton("Français");
		JButton buttonKorean = new JButton("한국어");
		JButton buttonMalaysian = new JButton("Bahasa Melayu");

		JPanel buttonGrid = new JPanel(new GridLayout(0, 1));

		JScrollPane scrollPane = new JScrollPane(
			buttonGrid,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		JButton buttonConfirm = new JButton(iconConfirm64);

		buttonConfirm.setBackground(colorConfirm);

		scrollPane.setPreferredSize(new Dimension(scrollPaneWidth, scrollPaneHeight));

		buttonEnglish.setBackground(colorButtonDeselected);
		buttonFrench.setBackground(colorButtonDeselected);
		buttonKorean.setBackground(colorButtonDeselected);
		buttonMalaysian.setBackground(colorButtonDeselected);

		buttonEnglish.setFont(fontInput);
		buttonFrench.setFont(fontInput);
		buttonKorean.setFont(fontInput);
		buttonMalaysian.setFont(fontInput);

		buttonEnglish.addActionListener(new buttonSelectLanguageListener());
		buttonFrench.addActionListener(new buttonSelectLanguageListener());
		buttonKorean.addActionListener(new buttonSelectLanguageListener());
		buttonMalaysian.addActionListener(new buttonSelectLanguageListener());

		buttonGrid.add(buttonEnglish);
		buttonGrid.add(buttonFrench);
		buttonGrid.add(buttonKorean);
		buttonGrid.add(buttonMalaysian);

		setSelectedLanguage(buttonEnglish);

		final int iconWidth = iconSettings128.getIconWidth();
		final int iconHeight = iconSettings128.getIconHeight();
		final int halfWidth = (windowWidth>>1);
		final int iconWest = (halfWidth - (iconWidth>>1));
		final int scrollPaneWest = (halfWidth - (scrollPaneWidth>>1));
		final int buttonWest = (halfWidth - (iconConfirm64.getIconWidth()>>1));

		spring.putConstraint(SpringLayout.WEST, containerIconSettings, iconWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, containerIconSettings, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, scrollPane, scrollPaneWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, scrollPane, 40, SpringLayout.SOUTH, containerIconSettings);

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, buttonWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 40, SpringLayout.SOUTH, scrollPane);

		contentPane.add(containerIconSettings);
		contentPane.add(scrollPane);
		contentPane.add(buttonConfirm);

		getRootPane().setDefaultButton(buttonConfirm);

		setLocationRelativeTo(null);
		setVisible(true);

	/*
	 * XXX
	 *
	 *	Make a real XML file using fasterxml, or a JSON file.
	 */
		buttonConfirm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				if (null == currentlySelectedLanguage)
				{
					showErrorDialog("You must select a language!");
					return;
				}

				try
				{
					File fObj = new File(configFile);
					FileOutputStream fOut = new FileOutputStream(fObj);

					RuntimeOptions rOpts = new RuntimeOptions();
					rOpts.setLanguage(currentlySelectedLanguage.getText());

					String json = mapper.writeValueAsString(rOpts);
					fObj.createNewFile();

					fOut.write(json.getBytes());
					fOut.flush();
					fOut.close();
				}
				catch (Exception e)
				{
					e.printStackTrace();
					System.exit(1);
				}

			/*
			 * Defaults to English if the string is invalid.
			 */
				currentLanguage = languages.getLanguageFromName(currentlySelectedLanguage.getText());

				contentPane.remove(containerIconSettings);
				contentPane.remove(scrollPane);
				contentPane.remove(buttonConfirm);

				dispose();

				doInitialConfiguration();
			}
		});
	}

	private void getConfigAndDoStartup()
	{
		File fObj = new File(passwordFile);

		if (false == fObj.exists())
		{
			/*
			 * This will then call doInitialConfiguration(),
			 * which will then call setupGUI()
			 */
			doLanguageConfiguration();
		}
		else
		{
			try
			{
				File f = new File(configFile);
				FileInputStream fIn = new FileInputStream(f);
				byte[] data = new byte[(int)f.length()];

				fIn.read(data, 0, (int)f.length());
				RuntimeOptions rOpts = mapper.readValue(data, RuntimeOptions.class);

				currentLanguage = languages.getLanguageFromName(rOpts.getLanguage());
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.exit(1);
			}

			unlockPasswordFile();
		}
	}

	public PasswordManager()
	{
		Dimension sizeScreen = Toolkit.getDefaultToolkit().getScreenSize();

		SCREEN_WIDTH = (int)sizeScreen.getWidth();
		SCREEN_HEIGHT = (int)sizeScreen.getHeight();

		mapper = new ObjectMapper();

		languages = new Languages();
		currentLanguage = languages.getLanguage(languages.ENGLISH);

		characterStatusMap = new boolean[(int)(0x7e - 0x21)];
		fillCharacterStatusMap();
		fillCharacterSet();

		userHome = System.getProperty("user.home");
		userName = System.getProperty("user.name");
		passwordDirectory = userHome + "/" + dirName;
		passwordFile = passwordDirectory + "/" + userName;
		configFile = passwordDirectory + "/" + userName + ".json";

		checkPasswordDir();
		getImages();

		getConfigAndDoStartup();
	}

	public static void main(final String argv[])
	{
		//UIManager.put("Scrollbar.thumb", new ColorUIResource(Color.BLACK));
		//UIManager.put("Scrollbar.thumbHighlight", new ColorUIResource(Color.BLACK));
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				PasswordManager passman = new PasswordManager();
			}
		});
	}
}
