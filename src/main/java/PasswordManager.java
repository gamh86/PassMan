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
	in the window after createAndShowGUI() despite the fact that
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

	public void setPasswordEntry(PasswordEntry e)
	{
		entry = e;
	}

	@Override
	public String toString()
	{
		return entry.getEmail();
	}
}

/*
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
*/

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
	private static final int PASSWORD_LIFETIME = 72; // days

	private static final String DATE_FORMAT = "dd-MM-YYYY HH:mm:ss";

	private Languages languages = null;

	private static final int ICONS_TINY_WIDTH = 18;
	private static final int ICONS_TINY_HEIGHT = 18;
	private static final int ICONS_SMALL_WIDTH = 22;
	private static final int ICONS_SMALL_HEIGHT = 22;
	private static final int ICONS_AVERAGE_WIDTH = 32;
	private static final int ICONS_AVERAGE_HEIGHT = 32;
	private static final int ICONS_LARGE_WIDTH = 64;
	private static final int ICONS_LARGE_HEIGHT = 64;
	private static final int ICONS_VLARGE_WIDTH = 128;
	private static final int ICONS_VLARGE_HEIGHT = 128;

/*
 * GUI components that need to be global so we can
 * reset them, for example, after changing langauge.
 */
	// in createAndShowGUI()
	private JTextArea taAppName = null;

	// in showSettings()
	private JLabel labelMasterPassword = null;
	private JLabel labelLanguage = null;
	private JLabel labelCharacterSet = null;

	private JTree tree = null;
	private DefaultTreeModel treeModel = null;

	private JTextField tfSelectedPassword = null;
	private JTextField tfSelectedUsername = null;
	private JTextField tfSelectedEmail = null;
	private JTextField tfSelectedPasswordLen = null;
	private JTextField tfSelectedPasswordAgeInDays = null;
	private JTextField tfSelectedPasswordUniqueId = null;

	private JLabel labelSelectedEmail = null;
	private JLabel labelSelectedUsername = null;
	private JLabel labelSelectedPasswordLen = null;
	private JLabel labelSelectedPasswordAgeInDays = null;
	private JLabel labelSelectedPassword = null;

/*
 * Shown next to the password age.
 */
	private JLabel staleIcon = null;
	private JLabel freshIcon = null;

/*
 * Various global fonts/colours
 */
	//private String fontName = "Times New Roman";
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
	//private Color colorLabel = new Color(240, 240, 240);
	private Color colorButton = new Color(255, 255, 255);
	//private Color colorScrollPane = new Color(242, 242, 242);

	private Map<Integer,String> currentLanguage = null;
	private String currentLanguageName = null;
	private Map<String,TransparentButton> languageFlags = null;
	private TransparentButton currentFlag = null;

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

	private static final String add64 = ICONS_DIR + "/add_64x64.png";
	private static final String bin64 = ICONS_DIR + "/bin_64x64.png";
	private static final String change64 = ICONS_DIR + "/change_64x64.png";
	private static final String download64 = ICONS_DIR + "/download_64x64.png";
	private static final String edit64 = ICONS_DIR + "/edit_64x64.png";
	private static final String locked64 = ICONS_DIR + "/locked_64x64.png";
	private static final String upload64 = ICONS_DIR + "/upload_64x64.png";
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

	private static final String _download = ICONS_DIR + "/download.png";
	private static final String _upload = ICONS_DIR + "/upload.png";

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
	private static ImageIcon iconDownload64 = null;
	private static ImageIcon iconEdit64 = null;
	private static ImageIcon iconUpload64 = null;
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

	private static ImageIcon iconDownload = null;
	private static ImageIcon iconUpload = null;

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
		iconDownload64 = new ImageIcon(download64);
		iconEdit64 = new ImageIcon(edit64);
		iconUpload64 = new ImageIcon(upload64);
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

		iconDownload = new ImageIcon(_download);
		iconUpload = new ImageIcon(_upload);
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

		JButton buttonConfirm = new TransparentButton(iconConfirm32);
		JLabel labelConfirm = new JLabel("Confirm");

		taPrompt.setFont(new Font("Verdana", Font.BOLD, 25));
		taPrompt.setBackground(frame.getBackground());
		taPrompt.setEditable(false);

		//buttonConfirm.setBackground(colorConfirm);
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

			currentLanguageName = settingsSelectedLanguage.getText();
			currentLanguage = languages.getLanguageFromName(currentLanguageName);
			showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_LANGUAGE_CHANGED));

		/*
		 * Set the text in currently visible global GUI components
		 * to the newly chosen language.
		 */
			// in main JFrame (created in createAndShowGUI)
			setTitle(currentLanguage.get(languages.STRING_APPLICATION_NAME) + " v" + VERSION);
			taAppName.setText(currentLanguage.get(languages.STRING_APPLICATION_NAME));

			labelSelectedEmail.setText(currentLanguage.get(languages.STRING_EMAIL));
			labelSelectedUsername.setText(currentLanguage.get(languages.STRING_USERNAME));
			labelSelectedPasswordLen.setText(currentLanguage.get(languages.STRING_PASSWORD_LENGTH));
			labelSelectedPasswordAgeInDays.setText(currentLanguage.get(languages.STRING_PASSWORD_AGE_DAYS));
			labelSelectedPassword.setText(currentLanguage.get(languages.STRING_PASSWORD));

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

		final int ICON_WIDTH = iconSettings128.getIconWidth();
		final int ICON_HEIGHT = iconSettings128.getIconHeight();
		final int VERTICAL_GAP = 40;
		final int LEFT_OFFSET = 20;
		final int SCROLLPANE_WIDTH = 300;
		final int SCROLLPANE_HEIGHT = 200;
		final int FRAME_MARGIN = 100;
		final int FRAME_WIDTH = (FRAME_MARGIN<<1) + SCROLLPANE_WIDTH + 100;
		final int FRAME_HEIGHT = (FRAME_MARGIN<<1) + SCROLLPANE_HEIGHT + ICON_HEIGHT + VERTICAL_GAP + 200;
		final int FRAME_HALF_WIDTH = (FRAME_WIDTH>>1);

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(FRAME_WIDTH, FRAME_HEIGHT);

		contentPane.setLayout(spring);

	/*
	 * These labels are global variables so that on
	 * language change their text can be changed
	 * from a callback function.
	 */
		labelMasterPassword = new GenericLabel(currentLanguage.get(languages.STRING_MASTER_PASSWORD));
		labelLanguage = new GenericLabel(currentLanguage.get(languages.STRING_LANGUAGE));
		labelCharacterSet = new GenericLabel(currentLanguage.get(languages.STRING_CHARACTER_SET));

		JTextField tfMasterPassword = new GenericTextField(" **************** ");

		JPanel buttonGrid = new JPanel(new GridLayout(0, 1));

		JScrollPane scrollPane = new JScrollPane(
			buttonGrid,
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
			JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
		);

		ArrayList<String> availableLanguages = languages.getLanguageNames();
		Dimension sizeButton = new Dimension(SCROLLPANE_WIDTH-10, 30);

		for (int i = 0; i < availableLanguages.size(); ++i)
		{
			JButton button = new JButton(availableLanguages.get(i));
			button.setBackground(colorButtonDeselected);
			button.setFont(fontInput);
			button.setPreferredSize(sizeButton);
			button.addActionListener(new settingsLanguageButtonListener());

			buttonGrid.add(button);
		}

		scrollPane.setPreferredSize(new Dimension(SCROLLPANE_WIDTH, SCROLLPANE_HEIGHT));

/*
		JButton buttonEnglish = new JButton("English");
		JButton buttonFrench = new JButton("Français");
		JButton buttonKorean = new JButton("한국어");
		JButton buttonMalaysian = new JButton("Bahasa Melayu");
		JButton buttonRussian = new JButton("Русский");

		buttonEnglish.setBackground(colorButtonDeselected);
		buttonFrench.setBackground(colorButtonDeselected);
		buttonKorean.setBackground(colorButtonDeselected);
		buttonMalaysian.setBackground(colorButtonDeselected);
		buttonRussian.setBackground(colorButtonDeselected);

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
*/

		tfMasterPassword.setFont(fontDetails);
		//tfMasterPassword.setPreferredSize(new Dimension(250, 30));
		tfMasterPassword.setBackground(new Color(230, 230, 230));
		tfMasterPassword.setBorder(null);
		tfMasterPassword.setEditable(false);

		//Dimension iconSize = new Dimension(ICON_WIDTH);

		JButton buttonChangeMaster = new TransparentButton(iconEdit32);
		//buttonChangeMaster.setPreferredSize(iconSize);

		JButton buttonChangeLanguage = new TransparentButton(iconEdit32);
		//buttonChangeLanguage.setPreferredSize(iconSize);

		JButton buttonAdjustCharacterSet = new TransparentButton(iconEdit32);
		//buttonAdjustCharacterSet.setPreferredSize(iconSize);

		final int MAIN_ICON_WEST = (FRAME_HALF_WIDTH - (ICON_WIDTH>>1));
		final int SCROLLPANE_WEST = (FRAME_HALF_WIDTH - (SCROLLPANE_WIDTH>>1));
		final int LABEL_FIELD_GAP = 10;

		spring.putConstraint(SpringLayout.WEST, containerSettingsIcon, MAIN_ICON_WEST, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, containerSettingsIcon, FRAME_MARGIN, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, labelMasterPassword, LEFT_OFFSET, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelMasterPassword, VERTICAL_GAP, SpringLayout.SOUTH, containerSettingsIcon);

		spring.putConstraint(SpringLayout.WEST, tfMasterPassword, LABEL_FIELD_GAP, SpringLayout.EAST, labelMasterPassword);
		spring.putConstraint(SpringLayout.NORTH, tfMasterPassword, 5, SpringLayout.NORTH, labelMasterPassword);

		spring.putConstraint(SpringLayout.EAST, buttonChangeMaster, -FRAME_MARGIN, SpringLayout.EAST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeMaster, 2, SpringLayout.NORTH, tfMasterPassword);

		spring.putConstraint(SpringLayout.WEST, labelLanguage, LEFT_OFFSET, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelLanguage, 30, SpringLayout.SOUTH, tfMasterPassword);

		spring.putConstraint(SpringLayout.WEST, scrollPane, SCROLLPANE_WEST, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, scrollPane, 20, SpringLayout.SOUTH, labelLanguage);

		spring.putConstraint(SpringLayout.EAST, buttonChangeLanguage, -FRAME_MARGIN, SpringLayout.EAST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeLanguage, 2, SpringLayout.NORTH, scrollPane);

		spring.putConstraint(SpringLayout.WEST, labelCharacterSet, LEFT_OFFSET, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, labelCharacterSet, 30, SpringLayout.SOUTH, scrollPane);

		spring.putConstraint(SpringLayout.EAST, buttonAdjustCharacterSet, -FRAME_MARGIN, SpringLayout.EAST, contentPane);
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
				createAndShowGUI();
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

		PDefaultMutableTreeNode _n = new PDefaultMutableTreeNode(entry);

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
		repaint();

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
		JButton buttonChangeCharset =
			new TransparentButton(getScaledImageIcon(iconSpanner32, ICONS_SMALL_WIDTH, ICONS_SMALL_HEIGHT));

		buttonChangeCharset.setToolTipText(currentLanguage.get(languages.STRING_CHANGE_CHARSET_PASSWORD_GENERATION));
		buttonChangeCharset.setEnabled(false);

		final int halfWidth = (SZ_WINDOW_WIDTH_DETAILS>>1);
		final int leftOffset = 40;

		final int labelFieldGap = 5;
		final int fieldNextLabelGap = 12;

		final int ICON_WEST = (halfWidth - (iconLocked64.getIconWidth()>>1));
		final int tfWest = (halfWidth - (SZ_TEXTFIELD_WIDTH_DETAILS>>1));

		final int BUTTON_CONFIRM_WEST = (halfWidth - (iconConfirm32.getIconWidth()>>1));

		spring.putConstraint(SpringLayout.WEST, iconContainer, ICON_WEST, SpringLayout.WEST, contentPane);
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

		spring.putConstraint(SpringLayout.WEST, buttonChangeCharset, 10, SpringLayout.EAST, tfPassword);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeCharset, 0, SpringLayout.NORTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, labelPasswordLen, 10, SpringLayout.WEST, tfPasswordLen);
		spring.putConstraint(SpringLayout.NORTH, labelPasswordLen, fieldNextLabelGap, SpringLayout.SOUTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, tfPasswordLen, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPasswordLen, labelFieldGap, SpringLayout.SOUTH, labelPasswordLen);

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, BUTTON_CONFIRM_WEST, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 40, SpringLayout.SOUTH, tfPasswordLen);

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
		//contentPane.add(labelConfirm);
		contentPane.add(buttonChangeCharset);
		//contentPane.add(labelChangeCharset);

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
					buttonChangeCharset.setEnabled(true);
				}
				else
				{
					tfPassword.setEditable(true);
					tfPasswordLen.setEditable(false);
					buttonChangeCharset.setEnabled(false);
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

	private void doChangePasswordEntryDetails()
	{
		if (null == passwordEntries)
		{
			showErrorDialog("No passwords");
			return;
		}

		if (null == currentTreePath)
		{
			showErrorDialog("No selected tree node...");
			return;
		}

		DefaultMutableTreeNode __n = (DefaultMutableTreeNode)currentTreePath.getLastPathComponent();
		if (null == __n || false == __n.isLeaf())
		{
			showErrorDialog("Select a password entry in tree!");
			return;
		}

		PDefaultMutableTreeNode n = (PDefaultMutableTreeNode)__n;
		PasswordEntry entry = n.getPasswordEntry();

		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();
		SpringLayout spring = new SpringLayout();

		frame.setSize(SZ_WINDOW_WIDTH_DETAILS, SZ_WINDOW_HEIGHT_DETAILS);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		contentPane.setLayout(spring);

		JLabel iconContainer = new JLabel(iconLocked64);
		final int ICON_WIDTH = iconLocked64.getIconWidth();
		final int ICON_HEIGHT = iconLocked64.getIconHeight();

		JLabel labelEmail = new GenericLabel("Email");
		JLabel labelUsername = new GenericLabel(currentLanguage.get(languages.STRING_USERNAME));
		JLabel labelPassword = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD));
		JLabel labelPasswordLen = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD_LENGTH_MIN_MAX));

		JTextField tfEmail = new GenericTextField(entry.getEmail());
		JTextField tfUsername = new GenericTextField(entry.getUsername());
		JTextField tfPassword = new GenericTextField(entry.getPassword());
		JTextField tfPasswordLen = new GenericTextField("");

		Dimension sizeTextField = new Dimension(SZ_TEXTFIELD_WIDTH_DETAILS, SZ_TEXTFIELD_HEIGHT_DETAILS);

		tfEmail.setPreferredSize(sizeTextField);
		tfUsername.setPreferredSize(sizeTextField);
		tfPassword.setPreferredSize(sizeTextField);
		tfPasswordLen.setPreferredSize(sizeTextField);

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

		checkGenerateRandom.setEnabled(false);

		JButton buttonConfirm = new TransparentButton(iconConfirm32);
		JButton buttonChangeCharset = new TransparentButton(getScaledImageIcon(iconSpanner32, 22, 22));
		//JLabel labelChangeCharset = new JLabel(currentLanguage.get(languages.STRING_CHARACTER_SET));
		//JLabel labelConfirm = new JLabel(currentLanguage.get(languages.STRING_CONFIRM));

		buttonChangeCharset.setToolTipText(currentLanguage.get(languages.STRING_CHANGE_CHARSET_PASSWORD_GENERATION));
		buttonChangeCharset.setEnabled(false);

		final int halfWidth = (SZ_WINDOW_WIDTH_DETAILS>>1);
		final int leftOffset = 40;

		final int labelFieldGap = 5; // gap between a label and text field below it
		final int fieldNextLabelGap = 24; // gap between a textfield and the next label for the next textfield

		final int ICON_WEST = (halfWidth - (ICON_WIDTH>>1));
		final int tfWest = (halfWidth - (SZ_TEXTFIELD_WIDTH_DETAILS>>1));

		final int BUTTON_CONFIRM_WEST = (halfWidth - (iconConfirm32.getIconWidth()>>1));

		spring.putConstraint(SpringLayout.WEST, iconContainer, ICON_WEST, SpringLayout.WEST, contentPane);
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

		spring.putConstraint(SpringLayout.WEST, buttonChangeCharset, 10, SpringLayout.EAST, tfPassword);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeCharset, 0, SpringLayout.NORTH, tfPassword);

		spring.putConstraint(SpringLayout.EAST, checkModifyPassword, 0, SpringLayout.EAST, tfPassword);
		spring.putConstraint(SpringLayout.SOUTH, checkModifyPassword, -5, SpringLayout.NORTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, checkGenerateRandom, 0, SpringLayout.WEST, tfPassword);
		spring.putConstraint(SpringLayout.SOUTH, checkGenerateRandom, -5, SpringLayout.NORTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, labelPasswordLen, 10, SpringLayout.WEST, tfPasswordLen);
		spring.putConstraint(SpringLayout.NORTH, labelPasswordLen, fieldNextLabelGap, SpringLayout.SOUTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, tfPasswordLen, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPasswordLen, labelFieldGap, SpringLayout.SOUTH, labelPasswordLen);

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, BUTTON_CONFIRM_WEST, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 40, SpringLayout.SOUTH, tfPasswordLen);

		//spring.putConstraint(SpringLayout.WEST, labelConfirm, 10, SpringLayout.EAST, buttonConfirm);
		//spring.putConstraint(SpringLayout.NORTH, labelConfirm, 5, SpringLayout.NORTH, buttonConfirm);

		//spring.putConstraint(SpringLayout.EAST, labelChangeCharset, -20, SpringLayout.WEST, iconContainer);
		//spring.putConstraint(SpringLayout.NORTH, labelChangeCharset, 5, SpringLayout.NORTH, buttonChangeCharset);

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
		//contentPane.add(labelChangeCharset);
		contentPane.add(buttonChangeCharset);
		//contentPane.add(labelConfirm);
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
					//buttonChangeCharset.setEnabled(true);
				}
				else
				{
					tfPassword.setText(tfSelectedPassword.getText());
					tfPassword.setEditable(false);
					tfPasswordLen.setText("");
					tfPasswordLen.setEditable(false);
					checkGenerateRandom.setSelected(false);
					checkGenerateRandom.setEnabled(false);
					buttonChangeCharset.setEnabled(false);

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
					buttonChangeCharset.setEnabled(true);
				}
				else
				{
					tfPassword.setEditable(true);
					tfPassword.setText(tfSelectedPassword.getText());
					tfPasswordLen.setText("");
					tfPasswordLen.setEditable(false);
					buttonChangeCharset.setEnabled(false);
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

			/*
			 * ENTRY already defined above.
			 */
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
				}

				if (null != newUsername)
				{
					entry.setUsername(newUsername);
					entry.setHash(getUniqueId(entry));

					tfSelectedUsername.setText(newUsername);
				}

			/*
			 * User may press confirm with the modify password checkbox selected
			 * but not actually changed the current password in the field.
			 * So check that the password has changed.
			 */
				if (null != newPassword && false == newPassword.equals(tfSelectedPassword.getText()))
				{
					entry.setPassword(newPassword);
					entry.setTimestamp(System.currentTimeMillis());

					tfSelectedPassword.setText(newPassword);
				}

				DefaultMutableTreeNode last = (DefaultMutableTreeNode)currentTreePath.getLastPathComponent();
				DefaultMutableTreeNode parent = (DefaultMutableTreeNode)last.getParent();

				System.out.println(parent.toString());
				updatePasswordEntryInTree(
					parent.toString(),
					oldEmail,
					entry
				);

				putPasswordEntry(oldHash, entry); // replace old entry in TreeMap
				putPasswordEntries(); // encrypt the data and write to password file

				revalidate();
				repaint();

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

		ImageIcon icon = iconLocked64;

		JPasswordField passField = new JPasswordField();
		JLabel containerIcon = new JLabel(icon);
		JTextArea taInfo = new JTextArea(currentLanguage.get(languages.STRING_PROMPT_UNLOCK_PASSWORD_FILE));

		final int ICON_WIDTH = icon.getIconWidth();
		final int ICON_HEIGHT = icon.getIconHeight();
		final int TF_WIDTH = 300;
		final int TF_HEIGHT = 25;
		final int HGAP = 20;
		final int FRAME_MARGIN = 60;
		final int FRAME_WIDTH = (FRAME_MARGIN<<1) + ICON_WIDTH + HGAP + TF_WIDTH + ICONS_SMALL_WIDTH;
		final int FRAME_HEIGHT = (FRAME_MARGIN<<1) + ICON_HEIGHT + TF_HEIGHT;

		Dimension sizePassField = new Dimension(TF_WIDTH, TF_HEIGHT);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(FRAME_WIDTH, FRAME_HEIGHT);
		contentPane.setLayout(spring);

		JButton buttonConfirm =
			new TransparentButton(getScaledImageIcon(iconConfirm32, TF_HEIGHT, TF_HEIGHT));

		taInfo.setEditable(false);
		taInfo.setBorder(null);
		taInfo.setBackground(contentPane.getBackground());
		taInfo.setFont(new Font("Times New Roman", Font.PLAIN, 20));

		passField.setPreferredSize(sizePassField);
		passField.setFont(fontInput);

		spring.putConstraint(SpringLayout.WEST, containerIcon, FRAME_MARGIN, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, containerIcon, FRAME_MARGIN, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, taInfo, HGAP+20, SpringLayout.EAST, containerIcon);
		spring.putConstraint(SpringLayout.NORTH, taInfo, 0, SpringLayout.NORTH, containerIcon);

		spring.putConstraint(SpringLayout.WEST, passField, HGAP, SpringLayout.EAST, containerIcon);
		spring.putConstraint(SpringLayout.SOUTH, passField, 0, SpringLayout.SOUTH, containerIcon);

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, 0, SpringLayout.EAST, passField);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 0, SpringLayout.NORTH, passField);

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
				createAndShowGUI();
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

		DefaultMutableTreeNode root = new DefaultMutableTreeNode(currentLanguage.get(languages.STRING_PASSWORD_IDS));

		while (iter.hasNext())
		{
			entry = iter.next();
			list = entry.getValue();
			it = list.iterator();

			DefaultMutableTreeNode nid = new DefaultMutableTreeNode(entry.getKey());

			while (it.hasNext())
			{
				PasswordEntry e = it.next();

				nid.add(new PDefaultMutableTreeNode(e));
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
			{
				return;
			}

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
			{
				showErrorDialog("No password entry in node...");
				return;
			}

			int age = getAgeInDaysFromTimestamp(entry.getTimestamp());

			tfSelectedPasswordLen.setText("" + entry.getPassword().length());
			tfSelectedPassword.setText(entry.getPassword());
			tfSelectedEmail.setText(entry.getEmail());
			tfSelectedUsername.setText(entry.getUsername());;
			tfSelectedPasswordAgeInDays.setText("" + age);

			if (PASSWORD_LIFETIME <= age)
			{
				staleIcon.setVisible(true);
				freshIcon.setVisible(false);
			}
			else
			{
				staleIcon.setVisible(false);
				freshIcon.setVisible(true);
			}

			revalidate();
		}
	}

	private void doSearchPasswordId(String id)
	{
		if (0 == id.length())
			return;

		DefaultMutableTreeNode r = (DefaultMutableTreeNode)treeModel.getRoot();
		boolean found = false;

		for (DefaultMutableTreeNode n = (DefaultMutableTreeNode)r.getFirstChild();
			null != n;
			n = (DefaultMutableTreeNode)n.getNextSibling())
		{
			if (n.toString().equals(id))
			{
				TreePath p = new TreePath(n.getPath());
				Rectangle area = tree.getPathBounds(p);
				area.height = tree.getVisibleRect().height;
				tree.scrollRectToVisible(area);
				tree.expandPath(p);
				
				found = true;
				break;
			}
		}

		if (false == found)
			showErrorDialog(currentLanguage.get(languages.STRING_ERROR_PASSWORD_ID));

		return;
	}

	/**
	 * Adjust the size of an image icon.
	 */
	private ImageIcon getScaledImageIcon(ImageIcon icon, int w, int h)
	{
		Image image = icon.getImage();
		Image newImage = image.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);

		return new ImageIcon(newImage);
	}

	private void setLanguageFlags()
	{
		final int FLAG_WIDTH = 34;
		final int FLAG_HEIGHT = 28;

		languageFlags = new HashMap<String,TransparentButton>();

		languageFlags.put("English",
			new TransparentButton(getScaledImageIcon(new ImageIcon(ICONS_DIR + "/flags/uk.png"), FLAG_WIDTH, FLAG_HEIGHT)));

		languageFlags.put("Français",
			new TransparentButton(getScaledImageIcon(new ImageIcon(ICONS_DIR + "/flags/france.png"), FLAG_WIDTH, FLAG_HEIGHT)));

		languageFlags.put("Bahasa Melayu",
			new TransparentButton(getScaledImageIcon(new ImageIcon(ICONS_DIR + "/flags/malaysia.png"), FLAG_WIDTH, FLAG_HEIGHT)));

		languageFlags.put("한국어",
			new TransparentButton(getScaledImageIcon(new ImageIcon(ICONS_DIR + "/flags/south_korea.png"), FLAG_WIDTH, FLAG_HEIGHT)));

		languageFlags.put("Русский",
			new TransparentButton(getScaledImageIcon(new ImageIcon(ICONS_DIR + "/flags/russia.png"), FLAG_WIDTH, FLAG_HEIGHT)));

		return;
	}

	/**
	 * When we change to a new language, various components
	 * with string values must be updated to the chosen language.
	 */
	private void resetGloballyVisibleStrings()
	{
		taAppName.setText(currentLanguage.get(languages.STRING_APPLICATION_NAME));

		labelSelectedEmail.setText(currentLanguage.get(languages.STRING_EMAIL));
		labelSelectedUsername.setText(currentLanguage.get(languages.STRING_USERNAME));
		labelSelectedPasswordLen.setText(currentLanguage.get(languages.STRING_PASSWORD_LENGTH));
		labelSelectedPasswordAgeInDays.setText(currentLanguage.get(languages.STRING_PASSWORD_AGE_DAYS));
		labelSelectedPassword.setText(currentLanguage.get(languages.STRING_PASSWORD));

		((DefaultMutableTreeNode)treeModel.getRoot())
			.setUserObject(currentLanguage.get(languages.STRING_PASSWORD_IDS));

		// Output the changes to the screen in our main JFrame
		revalidate();
		repaint();
	}

	/**
	 * Serialize in JSON format runtime options structure
	 * to the <username>.json file.
	 */
	private void updateRuntimeOptionsFile(RuntimeOptions r)
	{
		try
		{
			File f = new File(configFile);
			FileOutputStream fOut = new FileOutputStream(f);
			String json = mapper.writeValueAsString(r);

			fOut.write(json.getBytes());
			fOut.flush();
			fOut.close();

			return;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Show the language choices available to
	 * the user and change to chosen language.
	 */
	private void showLanguageSettingsWindow()
	{
		final int VERTICAL_GAP = 40;
		final int FRAME_MARGIN = 50;
		final int SCROLLPANE_WIDTH = 300;
		final int SCROLLPANE_HEIGHT = 150;

		ImageIcon icon = getScaledImageIcon(new ImageIcon(ICONS_DIR + "/language_256x256.png"), 64, 64);
		JLabel iconLanguage = new JLabel(icon);

		final int IMAGE_WIDTH = icon.getIconWidth();
		final int IMAGE_HEIGHT = icon.getIconHeight();
		final int FRAME_WIDTH = (FRAME_MARGIN<<1) + SCROLLPANE_WIDTH;
		final int FRAME_HEIGHT = (FRAME_MARGIN<<1) + SCROLLPANE_HEIGHT + (VERTICAL_GAP*3) + IMAGE_HEIGHT;
		final int FRAME_HALF_WIDTH = (FRAME_WIDTH>>1);

		GenericFrame frame = new GenericFrame(FRAME_WIDTH, FRAME_HEIGHT);
		iconLanguage.setBackground(frame.getBackground());
		Container contentPane = frame.getContentPane();
		JButton buttonConfirm = new TransparentButton(iconConfirm32);
		JPanel panelButtons = new JPanel(new GridLayout(0, 1));
		SpringLayout layout = new SpringLayout();
		ArrayList<String> languageNames = languages.getLanguageNames();

		for (String lang : languageNames)
		{
			JButton b = new JButton(lang);

			b.setBackground(colorButtonDeselected);
			b.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event)
				{
					if (null != currentlySelectedLanguage)
						currentlySelectedLanguage.setBackground(colorButtonDeselected);

					JButton b = ((JButton)event.getSource());
					if (b.equals(currentlySelectedLanguage))
					{
						b.setBackground(colorButtonDeselected);
						currentlySelectedLanguage = null;

						return;
					}

					currentlySelectedLanguage = ((JButton)event.getSource());
					currentlySelectedLanguage.setBackground(colorButtonSelected);
				}
			});

			panelButtons.add(b);
		}

		JScrollPane sp = new JScrollPane(
			panelButtons,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
		);

		sp.setPreferredSize(new Dimension(SCROLLPANE_WIDTH, SCROLLPANE_HEIGHT));

		Dimension sizeButton = buttonConfirm.getPreferredSize();

		final int ICON_WEST = FRAME_HALF_WIDTH - (IMAGE_WIDTH>>1);
		final int BUTTON_WEST = FRAME_HALF_WIDTH - (((int)sizeButton.getWidth())>>1);

		layout.putConstraint(SpringLayout.WEST, iconLanguage, ICON_WEST, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.NORTH, iconLanguage, FRAME_MARGIN, SpringLayout.NORTH, contentPane);

		layout.putConstraint(SpringLayout.NORTH, sp, VERTICAL_GAP, SpringLayout.SOUTH, iconLanguage);
		layout.putConstraint(SpringLayout.WEST, sp, FRAME_MARGIN, SpringLayout.WEST, contentPane);

		layout.putConstraint(SpringLayout.NORTH, buttonConfirm, VERTICAL_GAP, SpringLayout.SOUTH, sp);
		layout.putConstraint(SpringLayout.WEST, buttonConfirm, BUTTON_WEST, SpringLayout.WEST, contentPane);

		contentPane.setLayout(layout);
		contentPane.add(iconLanguage);
		contentPane.add(sp);
		contentPane.add(buttonConfirm);

		frame.getRootPane().setDefaultButton(buttonConfirm);

		buttonConfirm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				if (null == currentlySelectedLanguage)
				{
					showErrorDialog(currentLanguage.get(languages.STRING_ERROR_SELECT_LANGUAGE));
					return;
				}

				TransparentButton b = (TransparentButton)languageFlags.get(currentlySelectedLanguage.getText());

				assert(null != b);

				b.setVisible(true);
				currentFlag.setVisible(false);
				currentFlag = b;

				String langName = currentlySelectedLanguage.getText();

			/*
			 * Get the language map for chosen language.
			 */
				currentLanguage = languages.getLanguageFromName(langName);

			/*
			 * Update runtime options config file
			 */
				RuntimeOptions rOpts = new RuntimeOptions();
				rOpts.setLanguage(langName);
				updateRuntimeOptionsFile(rOpts);

			/*
			 * Reset all visible language strings.
			 */
				resetGloballyVisibleStrings();

				showInfoDialog(currentLanguage.get(languages.STRING_PROMPT_LANGUAGE_CHANGED));
				frame.dispose();
			}
		});

		frame.setVisible(true);
	}

	private JPanel panelDetails = null;
	private SpringLayout detailsLayout = null;

	private void createPasswordDetailsPanel(int w, int h)
	{
		panelDetails = new JPanel();
		detailsLayout = new SpringLayout();
		panelDetails.setLayout(detailsLayout);

		Dimension tfsSize = new Dimension(w, h);

		tfSelectedEmail = new DetailsTextField("");
		tfSelectedUsername = new DetailsTextField("");
		tfSelectedPasswordLen = new DetailsTextField("");
		tfSelectedPasswordAgeInDays = new DetailsTextField("");
		tfSelectedPassword = new DetailsTextField("");

		tfSelectedEmail.setPreferredSize(tfsSize);
		tfSelectedUsername.setPreferredSize(tfsSize);
		tfSelectedPasswordLen.setPreferredSize(tfsSize);
		tfSelectedPasswordAgeInDays.setPreferredSize(tfsSize);
		tfSelectedPassword.setPreferredSize(tfsSize);

		staleIcon = new JLabel(getScaledImageIcon(iconWarning32, 20, 20));
		freshIcon = new JLabel(getScaledImageIcon(iconOk32, 20, 20));

		staleIcon.setToolTipText(currentLanguage.get(languages.STRING_PASSWORD_IS_STALE));
		freshIcon.setToolTipText(currentLanguage.get(languages.STRING_PASSWORD_IS_FRESH));

		labelSelectedEmail = new GenericLabel(currentLanguage.get(languages.STRING_EMAIL));
		labelSelectedUsername = new GenericLabel(currentLanguage.get(languages.STRING_USERNAME));
		labelSelectedPasswordLen = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD_LENGTH));
		labelSelectedPasswordAgeInDays = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD_AGE_DAYS));
		labelSelectedPassword = new GenericLabel(currentLanguage.get(languages.STRING_PASSWORD));

		ImageIcon iconCopy25 = getScaledImageIcon(iconCopy32, 25, 25);

		JButton buttonCopyEmail = new TransparentButton(iconCopy25);
		JButton buttonCopyUsername = new TransparentButton(iconCopy25);
		JButton buttonCopyPassword = new TransparentButton(iconCopy25);

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
		final int LABEL_FIELD_GAP = 6;
		final int FIELD_NEXT_LABEL_GAP = 8;
		final int WEST_GAP = 20;
		final int VGAP = 12;
		final int LABEL_LEFT = 5;
		final int TEXTFIELD_BUTTON_GAP = 20;

		detailsLayout.putConstraint(SpringLayout.NORTH, labelSelectedEmail, VGAP, SpringLayout.NORTH, panelDetails);
		detailsLayout.putConstraint(SpringLayout.WEST, labelSelectedEmail, LABEL_LEFT, SpringLayout.WEST, tfSelectedEmail);

		detailsLayout.putConstraint(SpringLayout.NORTH, tfSelectedEmail, LABEL_FIELD_GAP, SpringLayout.SOUTH, labelSelectedEmail);
		detailsLayout.putConstraint(SpringLayout.WEST, tfSelectedEmail, WEST_GAP, SpringLayout.WEST, panelDetails);

		detailsLayout.putConstraint(SpringLayout.NORTH, buttonCopyEmail, 0, SpringLayout.NORTH, tfSelectedEmail);
		detailsLayout.putConstraint(SpringLayout.WEST, buttonCopyEmail, TEXTFIELD_BUTTON_GAP, SpringLayout.EAST, tfSelectedEmail);

		detailsLayout.putConstraint(SpringLayout.NORTH, labelSelectedUsername, FIELD_NEXT_LABEL_GAP, SpringLayout.SOUTH, tfSelectedEmail);
		detailsLayout.putConstraint(SpringLayout.WEST, labelSelectedUsername, LABEL_LEFT, SpringLayout.WEST, tfSelectedUsername);

		detailsLayout.putConstraint(SpringLayout.NORTH, tfSelectedUsername, LABEL_FIELD_GAP, SpringLayout.SOUTH, labelSelectedUsername);
		detailsLayout.putConstraint(SpringLayout.WEST, tfSelectedUsername, WEST_GAP, SpringLayout.WEST, panelDetails);

		detailsLayout.putConstraint(SpringLayout.NORTH, buttonCopyUsername, 0, SpringLayout.NORTH, tfSelectedUsername);
		detailsLayout.putConstraint(SpringLayout.WEST, buttonCopyUsername, TEXTFIELD_BUTTON_GAP, SpringLayout.EAST, tfSelectedUsername);

		detailsLayout.putConstraint(
			SpringLayout.NORTH,
			labelSelectedPassword,
			FIELD_NEXT_LABEL_GAP,
			SpringLayout.SOUTH,
			tfSelectedUsername
		);

		detailsLayout.putConstraint(SpringLayout.WEST, labelSelectedPassword, LABEL_LEFT, SpringLayout.WEST, tfSelectedPassword);

		detailsLayout.putConstraint(SpringLayout.NORTH, tfSelectedPassword, LABEL_FIELD_GAP, SpringLayout.SOUTH, labelSelectedPassword);
		detailsLayout.putConstraint(SpringLayout.WEST, tfSelectedPassword, WEST_GAP, SpringLayout.WEST, panelDetails);

		detailsLayout.putConstraint(SpringLayout.NORTH, buttonCopyPassword, 0, SpringLayout.NORTH, tfSelectedPassword);
		detailsLayout.putConstraint(SpringLayout.WEST, buttonCopyPassword, TEXTFIELD_BUTTON_GAP, SpringLayout.EAST, tfSelectedPassword);

		detailsLayout.putConstraint(
			SpringLayout.NORTH,
			labelSelectedPasswordLen,
			FIELD_NEXT_LABEL_GAP,
			SpringLayout.SOUTH,
			tfSelectedPassword
		);

		detailsLayout.putConstraint(SpringLayout.WEST, labelSelectedPasswordLen, LABEL_LEFT, SpringLayout.WEST, tfSelectedPasswordLen);

		detailsLayout.putConstraint(
			SpringLayout.NORTH,
			tfSelectedPasswordLen,
			LABEL_FIELD_GAP,
			SpringLayout.SOUTH,
			labelSelectedPasswordLen
		);

		detailsLayout.putConstraint(SpringLayout.WEST, tfSelectedPasswordLen, WEST_GAP, SpringLayout.WEST, panelDetails);

		detailsLayout.putConstraint(
			SpringLayout.NORTH,
			labelSelectedPasswordAgeInDays,
			FIELD_NEXT_LABEL_GAP,
			SpringLayout.SOUTH,
			tfSelectedPasswordLen
		);

		detailsLayout.putConstraint(
			SpringLayout.WEST,
			labelSelectedPasswordAgeInDays,
			LABEL_LEFT,
			SpringLayout.WEST,
			tfSelectedPasswordAgeInDays
		);

		detailsLayout.putConstraint(SpringLayout.SOUTH, staleIcon, -5, SpringLayout.NORTH, tfSelectedPasswordAgeInDays);
		detailsLayout.putConstraint(SpringLayout.EAST, staleIcon, 30, SpringLayout.EAST, labelSelectedPasswordAgeInDays);

		detailsLayout.putConstraint(SpringLayout.SOUTH, freshIcon, -5, SpringLayout.NORTH, tfSelectedPasswordAgeInDays);
		detailsLayout.putConstraint(SpringLayout.EAST, freshIcon, 30, SpringLayout.EAST, labelSelectedPasswordAgeInDays);

		detailsLayout.putConstraint(
			SpringLayout.NORTH,
			tfSelectedPasswordAgeInDays,
			LABEL_FIELD_GAP,
			SpringLayout.SOUTH,
			labelSelectedPasswordAgeInDays
		);

		detailsLayout.putConstraint(SpringLayout.WEST, tfSelectedPasswordAgeInDays, WEST_GAP, SpringLayout.WEST, panelDetails);

		staleIcon.setVisible(false);
		freshIcon.setVisible(false);

		//panelDetails.setBackground(new Color(250, 250, 250));
		Border border = BorderFactory.createEtchedBorder(EtchedBorder.RAISED);
		TitledBorder titled = new TitledBorder(
			border,
			currentLanguage.get(languages.STRING_PASSWORD_DETAILS),
			TitledBorder.RIGHT,
			TitledBorder.DEFAULT_POSITION
		);

		panelDetails.setBorder(titled);

		panelDetails.add(labelSelectedEmail);
		panelDetails.add(tfSelectedEmail);
		panelDetails.add(buttonCopyEmail);
		panelDetails.add(labelSelectedUsername);
		panelDetails.add(tfSelectedUsername);
		panelDetails.add(buttonCopyUsername);
		panelDetails.add(labelSelectedPasswordLen);
		panelDetails.add(tfSelectedPasswordLen);
		panelDetails.add(labelSelectedPasswordAgeInDays);
		panelDetails.add(staleIcon);
		panelDetails.add(freshIcon);
		panelDetails.add(tfSelectedPasswordAgeInDays);
		panelDetails.add(labelSelectedPassword);
		panelDetails.add(tfSelectedPassword);
		panelDetails.add(buttonCopyPassword);

		return;
	}


	public void createAndShowGUI()
	{
/*
 * Define the sizes of the components within the frame and calculate
 * the frame width and height based on these sizes.
 */
		final int IMAGE_ICON_SIZE = 22;

		final int ICON_WIDTH = iconUnlocked128.getIconWidth();
		final int ICON_HEIGHT = iconUnlocked128.getIconHeight();

		final int VERTICAL_GAP = 40;

		final int TF_SEARCH_HEIGHT = 25;
		final int PANEL_SCROLLBAR_GAP = 0;
		final int FRAME_MARGIN = 50;
		final int TREE_SCROLLBAR_WIDTH = 450;
		final int TREE_SCROLLBAR_HEIGHT = 350;
		final int PANEL_BUTTONS_HEIGHT = 50;
		final int PANEL_DETAILS_WIDTH = 480;
		final int PANEL_DETAILS_HEIGHT = TREE_SCROLLBAR_HEIGHT + TF_SEARCH_HEIGHT - IMAGE_ICON_SIZE;
		final int TF_HEIGHT = 27;

		final int FRAME_WIDTH =
			(FRAME_MARGIN<<1) +
			TREE_SCROLLBAR_WIDTH +
			PANEL_SCROLLBAR_GAP +
			PANEL_DETAILS_WIDTH;

		final int FRAME_HEIGHT =
			ICON_HEIGHT +
			(VERTICAL_GAP<<1) +
			TF_HEIGHT +
			(FRAME_MARGIN*3) +
			TREE_SCROLLBAR_HEIGHT;

		final int TF_SEARCH_WIDTH = TREE_SCROLLBAR_WIDTH-25;
		final int TF_DETAILS_WIDTH = 400;

		final int HALF_FRAME_WIDTH = (FRAME_WIDTH>>1);

	/*
	 * Create the hashmap of language names => buttons
	 */
		setLanguageFlags();

	/*
	 * Setup the panel that will show details of
	 * a selected password entry node in the tree.
	 */
		createPasswordDetailsPanel(TF_DETAILS_WIDTH, TF_HEIGHT);
		panelDetails.setPreferredSize(new Dimension(PANEL_DETAILS_WIDTH, PANEL_DETAILS_HEIGHT));

		SpringLayout spring = new SpringLayout();
		Container contentPane = getContentPane();

	/*
	 * Set various properties of our JFrame.
	 */
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setTitle(currentLanguage.get(languages.STRING_APPLICATION_NAME) + " v" + VERSION);
		setSize(FRAME_WIDTH, FRAME_HEIGHT);
		setResizable(false);

		contentPane.setLayout(spring);
		contentPane.setBackground(colorFrame);

	/*
	 * Create searchfor so user can search
	 * for a password ID In the JTree.
	 */
		JTextField tfSearch = new GenericTextField("");
		JButton buttonSearch = new GenericButton(getScaledImageIcon(iconSearch32, 20, 20));

		tfSearch.setPreferredSize(new Dimension(TF_SEARCH_WIDTH, TF_SEARCH_HEIGHT));
		buttonSearch.setPreferredSize(new Dimension(25, 25));
		JLabel unlockedContainer = new JLabel(iconShield128);

	/*
	 * Panel containing buttons for various
	 * functions carried out on password entries.
	 */
		JPanel panelButtons = new JPanel(new GridLayout(1, 0, 0, 0));
		panelButtons.setPreferredSize(new Dimension(PANEL_DETAILS_WIDTH, IMAGE_ICON_SIZE));

		JButton buttonAdd =
			new GenericButton(getScaledImageIcon(iconAdd64, IMAGE_ICON_SIZE, IMAGE_ICON_SIZE));

		JButton buttonChange =
			new GenericButton(getScaledImageIcon(iconChange64, IMAGE_ICON_SIZE, IMAGE_ICON_SIZE));

		JButton buttonRemove =
			new GenericButton(getScaledImageIcon(iconBin64, IMAGE_ICON_SIZE, IMAGE_ICON_SIZE));

		panelButtons.add(buttonAdd);
		panelButtons.add(buttonChange);
		panelButtons.add(buttonRemove);

		// taAppName is a global variable
		taAppName = new JTextArea(currentLanguage.get(languages.STRING_APPLICATION_NAME));

		taAppName.setFont(new Font("Tahoma", Font.BOLD, 45));
		taAppName.setForeground(new Color(32, 32, 32));
		taAppName.setBackground(contentPane.getBackground());
		taAppName.setBorder(null);
		taAppName.setEditable(false);

	/*
	 * Create the tree that shows the password
	 * IDs and their password entries.
	 */
		JTree tree = createPasswordEntryTree();
		JScrollPane sp = new JScrollPane(
			tree,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
		);

		sp.setPreferredSize(new Dimension(TREE_SCROLLBAR_WIDTH, TREE_SCROLLBAR_HEIGHT));

	/*
	 * TODO
	 *	Create a custom renderer to customize the look
	 *	and feel of our scrollbar (and other components)
	 */
		//scrollPane.getHorizontalScrollBar().setUI(new javax.swing.plaf.synth.SynthScrollBarUI());
		//scrollPane.getVerticalScrollBar().setUI(new javax.swing.plaf.synth.SynthScrollBarUI());

		spring.putConstraint(SpringLayout.WEST, unlockedContainer, HALF_FRAME_WIDTH-(ICON_WIDTH+80), SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, unlockedContainer, VERTICAL_GAP, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, tfSearch, FRAME_MARGIN, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfSearch, VERTICAL_GAP<<1, SpringLayout.SOUTH, unlockedContainer);

		spring.putConstraint(SpringLayout.WEST, buttonSearch, 0, SpringLayout.EAST, tfSearch);
		spring.putConstraint(SpringLayout.NORTH, buttonSearch, 0, SpringLayout.NORTH, tfSearch);

		spring.putConstraint(SpringLayout.WEST, taAppName, 20, SpringLayout.EAST, unlockedContainer);
		spring.putConstraint(SpringLayout.NORTH, taAppName, (ICON_HEIGHT>>1)-20, SpringLayout.NORTH, unlockedContainer);

		spring.putConstraint(SpringLayout.WEST, sp, FRAME_MARGIN, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, sp, 0, SpringLayout.SOUTH, tfSearch);

		spring.putConstraint(SpringLayout.WEST, panelButtons, PANEL_SCROLLBAR_GAP, SpringLayout.EAST, sp);
		spring.putConstraint(SpringLayout.NORTH, panelButtons, 0, SpringLayout.SOUTH, panelDetails);

		spring.putConstraint(SpringLayout.WEST, panelDetails, PANEL_SCROLLBAR_GAP, SpringLayout.EAST, sp);
		spring.putConstraint(SpringLayout.NORTH, panelDetails, 0, SpringLayout.NORTH, tfSearch);

		contentPane.add(unlockedContainer);
		contentPane.add(taAppName);
		contentPane.add(tfSearch);
		contentPane.add(buttonSearch);
		contentPane.add(sp);
		contentPane.add(panelDetails);
		contentPane.add(panelButtons);

		setLocationRelativeTo(null);
		setVisible(true);

		//revalidate();
		//repaint();

		Set<HashMap.Entry<String,TransparentButton>> flagSet = languageFlags.entrySet();
		Iterator<HashMap.Entry<String,TransparentButton>> iter = flagSet.iterator();

		while (iter.hasNext())
		{
			HashMap.Entry<String,TransparentButton> entry = iter.next();
			TransparentButton b = entry.getValue();

			b.setToolTipText(currentLanguage.get(languages.STRING_CLICK_TO_CHANGE_LANGUAGE));
			//b.setBorder(BorderFactory.createRaisedBevelBorder());

			spring.putConstraint(SpringLayout.EAST, b, -10, SpringLayout.EAST, contentPane);
			spring.putConstraint(SpringLayout.NORTH, b, 10, SpringLayout.NORTH, contentPane);

			contentPane.add(b);

			if (entry.getKey().equals(currentLanguageName))
			{
				b.setVisible(true);
				currentFlag = b;
			}
			else
				b.setVisible(false);

			b.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event)
				{
					showLanguageSettingsWindow();
					//((TransparentButton)event.getSource()).setVisible(false);
					//languageFlags.get(currentLanguageName).setVisible(true);
				}
			});
		}

		if (null == fContents || false == fileContentsCached)
		{
			doInitialConfiguration();
		}

		tfSearch.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event)
			{
				getRootPane().setDefaultButton(buttonSearch);
			}
		});

		buttonSearch.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				if (null == tree)
					return;

				doSearchPasswordId(tfSearch.getText());
				tfSearch.setText("");
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

/*
		buttonSet.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				showSettings();
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
*/

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
				if (null == currentTreePath)
					return;

				doChangePasswordEntryDetails();
			}
		});

/*
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
		JButton buttonRussian = new JButton("Русский");

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
					currentLanguageName = currentlySelectedLanguage.getText();
					rOpts.setLanguage(currentLanguageName);

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
				currentLanguageName = currentlySelectedLanguage.getText();
				currentLanguage = languages.getLanguageFromName(currentLanguageName);

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
			 * which will then call createAndShowGUI()
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

				currentLanguageName = rOpts.getLanguage();
				currentLanguage = languages.getLanguageFromName(currentLanguageName);
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
