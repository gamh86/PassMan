import aescrypt.*;
import backup.*; // for our GDriveBackup class
import renderer.*;

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
import java.util.regex.*;
import java.util.Random;
import java.util.TimeZone;
import java.util.Set;
import java.util.Optional;
import java.util.StringTokenizer;

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
import javax.swing.text.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
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

	Add a component indicating password staleness in the password
	details frames.
*/

@JsonIgnoreProperties(ignoreUnknown = true)
class PasswordEntry
{
	@JsonProperty
	private String email;
	@JsonProperty
	private String password;
	@JsonProperty
	private String username;
	@JsonProperty
	private long timestamp;
	@JsonProperty
	private int charsetSize; // so we can calculate stats in doAnalysePassword()
	@JsonProperty
	private String hash;

	public PasswordEntry() { }

	public String getEmail() { return email; }
	public String getPassword() { return password; }
	public String getUsername() { return username; }
	public long getTimestamp() { return timestamp; }
	public int getCharsetSize() { return charsetSize; }
	public String getHash() { return hash; }

	public void setEmail(String newEmail) { email = newEmail; }
	public void setPassword(String newPass) { password = newPass; }
	public void setUsername(String newUsername) { username = newUsername; }
	public void setTimestamp(long newTimestamp) { timestamp = newTimestamp; }
	public void setCharsetSize(int size) { charsetSize = size; }
	public void setHash(String h) { hash = h; }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Backup
{
	@JsonProperty
	String server;
	@JsonProperty
	long timestamp;
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
	private HashMap<String,ArrayList<PasswordEntry>> passwordEntries;

	private static final String dirName = ".PassMan";
	private static final String appName = "Password Manager";
	private static final String ICONS_DIR = "src/main/resources/icons";

	private static final String JSON_BACKUP_FILE = "src/main/resources/backup.json";

	private static final int MAIN_WINDOW_WIDTH = 1000;
	private static final int MAIN_WINDOW_HEIGHT = 560;
	private static final int MAIN_WINDOW_SCROLLBAR_WIDTH = (MAIN_WINDOW_WIDTH - 120);
	private static final int MAIN_WINDOW_SCROLLBAR_HEIGHT = (MAIN_WINDOW_HEIGHT - 400);

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

/*
 * GUI components that need to be global so we can
 * reset them, for example, after changing langauge.
 */
	// in setupGUI()
	private JTextArea taAppName = null;
	private JLabel labelPasswordIds = null;

	// in showSettings()
	private JLabel labelMasterPassword = null;
	private JLabel labelLanguage = null;
	private JLabel labelCharacterSet = null;

	private JTable table = null;
	private DefaultTableModel model = null;
	private int currentlySelectedRow = -1;

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

/*
 * Unmodifiable maps mapping constants
 * below to relevant String objects.
 */
	private static final Map<Integer,String> languageKorean = getLanguageStringsKorean();
	private static final Map<Integer,String> languageEnglish = getLanguageStringsEnglish();
	private static final Map<Integer,String> languageMalaysian = getLanguageStringsMalaysian();
	private static final Map<Integer,String> languageFrench = getLanguageStringsFrench();

	private ArrayList<Map<Integer,String> > specialLanguageList = null;

/*
 * Constants to access String objects
 * within HashMap language objects.
 */
	private static final int STRING_APPLICATION_NAME = 0;
	private static final int STRING_PASSWORD_ID = 1;
	private static final int STRING_USERNAME = 2;
	private static final int STRING_PASSWORD = 3;
	private static final int STRING_PASSWORD_LENGTH = 4;
	private static final int STRING_MASTER_PASSWORD = 5;
	private static final int STRING_UNLOCK_PASSWORD_FILE = 6;
	private static final int STRING_CREATION_TIME = 7;
	private static final int STRING_COPY_PASSWORD = 8;
	private static final int STRING_CONFIGURATION_PROMPT = 9;
	private static final int STRING_CHANGE_SETTINGS = 10;
	private static final int STRING_CONFIRM_PASSWORD = 11;
	private static final int STRING_CURRENT_PASSWORD = 12;
	private static final int STRING_NEW_PASSWORD = 13;
	private static final int STRING_CONFIRM_NEW_PASSWORD = 14;

	private static final int STRING_TITLE_PASSWORD_DETAILS = 15;
	private static final int STRING_TITLE_PASSWORD_MANAGER_CONFIGURATION = 16;

	private static final int STRING_PROMPT_DETAILS_CHANGED = 17;
	private static final int STRING_PROMPT_PASSWORD_COPIED = 18;
	private static final int STRING_PROMPT_PASSWORD_CREATED = 19;
	private static final int STRING_PROMPT_PASSWORD_WILL_BE_CHANGED = 20;
	private static final int STRING_PROMPT_PASSWORD_WILL_BE_REMOVED = 21;
	private static final int STRING_PROMPT_PASSWORD_REMOVED = 22;
	private static final int STRING_PROMPT_MASTER_PASSWORD_CHANGED = 23;
	private static final int STRING_PROMPT_NO_PASSWORDS = 24;
	private static final int STRING_PROMPT_PASSWORD_FILE_CREATED = 25;

	private static final int STRING_ERROR_PASSWORD_ID = 26;
	private static final int STRING_ERROR_PASSWORD_ID_EXISTS = 27;
	private static final int STRING_ERROR_INVALID_PASSWORD_LENGTH = 28;
	private static final int STRING_ERROR_INCORRECT_PASSWORD = 29;
	private static final int STRING_ERROR_PASSWORDS_DO_NOT_MATCH = 30;
	private static final int STRING_ERROR_NO_ENTRY = 31;
	private static final int STRING_ERROR_SELECT_PASSWORD_ID = 32;
	private static final int STRING_ERROR_PASSWORD_NOT_CHANGED = 33;

	private static final int STRING_CHANGE_MASTER_PASSWORD = 34;
	private static final int STRING_PROMPT_OK = 35;
	private static final int STRING_PROMPT_CANCEL = 36;
	private static final int STRING_LANGUAGE = 37;
	private static final int STRING_ERROR_SELECT_LANGUAGE = 38;
	private static final int STRING_PROMPT_CHANGED_LANGUAGE = 39;
	private static final int STRING_PASSWORD_ID_LIST = 40;
	private static final int STRING_POSSIBLE_PERMUTATIONS = 41;
	private static final int STRING_COPY = 42;
	private static final int STRING_PASSWORD_LENGTH2 = 43;
	private static final int STRING_CRACK_TIME = 44;
	private static final int STRING_SECONDS = 45;
	private static final int STRING_DAYS = 46;
	private static final int STRING_YEARS = 47;
	private static final int STRING_PASSWORD_STRENGTH_INFORMATION = 48;
	private static final int STRING_TITLE_PASSWORD_ANALYSIS = 49;

	private static final int STRING_ERROR_TOO_MANY_ATTEMPTS = 50;
	private static final int STRING_PROMPT_UNLOCK_PASSWORD_FILE = 51;
	private static final int STRING_LEAVE_BLANK = 52;
	private static final int STRING_CHARACTER_SET = 53;
	private static final int STRING_TOGGLE_CHARACTER_SET = 54;
	private static final int STRING_GENERATE_RANDOM = 55;
	private static final int STRING_SIZE_CHARACTER_SET = 56;
	private static final int STRING_CHANGE_ID = 57;
	private static final int STRING_CHANGE_USERNAME = 58;
	private static final int STRING_CHANGE_PASSWORD = 59;
	private static final int STRING_PASTE = 60;
	private static final int STRING_OLD_PASSWORD = 61;

	private static final int STRING_CREATE_BACKUP_FILE = 62;
	private static final int STRING_PROMPT_CREATED_BACKUP_FILE = 63;

	private static Map<Integer,String> getLanguageStringsKorean()
	{
		Map<Integer,String> map = new HashMap<Integer,String>();

		map.put(STRING_APPLICATION_NAME, "페스만");
		map.put(STRING_PASSWORD_ID, "비밀번호 아이디");
		map.put(STRING_USERNAME, "사용자 이름");
		map.put(STRING_PASSWORD, "비밀번호");
		map.put(STRING_NEW_PASSWORD, "새 비밀번호");
		map.put(STRING_OLD_PASSWORD, "기존 비밀번호");
		map.put(STRING_PASSWORD_LENGTH, "비밀번호 길이 (8 - 100)");
		map.put(STRING_MASTER_PASSWORD, "마스터 비밀번호");
		map.put(STRING_UNLOCK_PASSWORD_FILE, "비밀번호 파일을 잠금 해제하기");
		map.put(STRING_CREATION_TIME, "작성일");
		map.put(STRING_COPY_PASSWORD, "클립보드에게 비밀번호를 복사하기");
		map.put(STRING_CHANGE_SETTINGS, "설정 변경");
		map.put(STRING_CURRENT_PASSWORD, "현재 비밀번호 입력");
		//map.put(STRING_ENTER_NEW_PASSWORD, "새 비밀번호 입력");
		map.put(STRING_CONFIRM_NEW_PASSWORD, "새 비밀번호 재입력");
		map.put(STRING_CONFIRM_PASSWORD, "비밀번호 재입력");
		map.put(STRING_CHANGE_MASTER_PASSWORD, "마스터 비밀번호 변경");
		map.put(STRING_LANGUAGE, "언어");
		map.put(STRING_PASSWORD_ID_LIST, "비밀번호 아이디 목록");
		map.put(STRING_POSSIBLE_PERMUTATIONS, "가능한 순열들");
		map.put(STRING_PASSWORD_LENGTH2, "비밀번호 길이");
		map.put(STRING_COPY, "복사하기");
		map.put(STRING_CRACK_TIME, "비밀번호를 해독 시간");
		map.put(STRING_SECONDS, "초");
		map.put(STRING_DAYS, "일");
		map.put(STRING_YEARS, "년");
		map.put(STRING_LEAVE_BLANK, "현재 비밀번호를 유지하도록 비워 두십시오");
		map.put(STRING_CHARACTER_SET, "비밀번호 문자 세트");
		map.put(STRING_TOGGLE_CHARACTER_SET, "비밀번호 문자 세트의 문자들을 전환할 수 있습니다");
		map.put(STRING_GENERATE_RANDOM, "임의의 비밀번호 생성하기");
		map.put(STRING_SIZE_CHARACTER_SET, "문자 세트 크기");
		map.put(STRING_CHANGE_ID, "비밀번호 아이디 변경");
		map.put(STRING_CHANGE_USERNAME, "사용자 이름 변경");
		map.put(STRING_CHANGE_PASSWORD, "비밀번호 변경");
		map.put(STRING_PASTE, "붙이");
		map.put(STRING_CREATE_BACKUP_FILE, "백업 파일을 만듭니다");

		map.put(STRING_TITLE_PASSWORD_MANAGER_CONFIGURATION, "비밀번호 관리자 설정");
		map.put(STRING_TITLE_PASSWORD_DETAILS, "비밀번호 설경");
		map.put(STRING_TITLE_PASSWORD_ANALYSIS, "비밀번호 분석");

		map.put(STRING_PROMPT_PASSWORD_COPIED, "클립보드에게 비밀번호가 복사하되었습니다.");
		map.put(STRING_PROMPT_DETAILS_CHANGED, "설정이 성공적으로 변경되었습니다");
		map.put(STRING_PROMPT_MASTER_PASSWORD_CHANGED, "마스터 비밀번호가 변경되었습니다");
		map.put(STRING_PROMPT_PASSWORD_CREATED, "비밀번호가 생성되었습니다");
		map.put(STRING_PROMPT_PASSWORD_FILE_CREATED, "비밀번호 파일이 생성되었습니다");
		map.put(STRING_PROMPT_PASSWORD_WILL_BE_CHANGED, "선택한 아이디의 비밀번호가 변경될 것입니다. 계속 하시겠습니까?");
		map.put(STRING_PROMPT_PASSWORD_WILL_BE_REMOVED, "선택한 아이디의 비밀번호가 삭제하될 것입니다. 계속 하시겠습니까?");
		map.put(STRING_PROMPT_PASSWORD_REMOVED, "비밀번호를 삭제되었습니다");
		map.put(STRING_PROMPT_NO_PASSWORDS, "비밀번호들이 없습니다");
		map.put(STRING_PROMPT_OK, "확인");
		map.put(STRING_PROMPT_CANCEL, "취소");
		map.put(STRING_PROMPT_CHANGED_LANGUAGE, "언어를 변경되었습니다");
		map.put(STRING_PROMPT_UNLOCK_PASSWORD_FILE, "마스터 비밀번호를 입력");//하십시오");
		map.put(STRING_PROMPT_CREATED_BACKUP_FILE, "백업 파일을 만듭니되었습니다");

		map.put(STRING_ERROR_PASSWORD_ID, "이 아이디의 비밀번호를 변경하될 수 없었습니다");
		map.put(STRING_ERROR_PASSWORD_ID_EXISTS, "비밀번호가 이미 존재합니다");
		map.put(STRING_ERROR_INVALID_PASSWORD_LENGTH, "비밀번호 길이가 유효하지 않습니다");
		map.put(STRING_ERROR_INCORRECT_PASSWORD, "잘못뒨 비밀번호");
		map.put(STRING_ERROR_PASSWORDS_DO_NOT_MATCH, "비밀번호가 일치하지 않습니다");
		map.put(STRING_ERROR_NO_ENTRY, "이 아이디에 대한 항목이 없습니다.");
		map.put(STRING_ERROR_SELECT_PASSWORD_ID, "아이디를 선택해야합니다");
		map.put(STRING_ERROR_PASSWORD_NOT_CHANGED, "이 아이디의 비밀번호 안 변경될 수 있었습니다");
		map.put(STRING_ERROR_SELECT_LANGUAGE, "언어를 선택해야합니다");

		map.put(STRING_CONFIGURATION_PROMPT,
					"새 비밀번호 파일이 생성될 거입니다. 지금 선택한\n"
					+ "비밀번호는 파일을 암호화하고 안전하게 유지하는 데\n"
					+ "사용됩니다. 이 비밀번호를 잃어 버리지 않도록하\n"
					+ "십시오. 그렇지 않으면 비밀번호 파일의 데이터를\n"
					+ "복우할 수 없을 거입니다.");

		map.put(STRING_PASSWORD_STRENGTH_INFORMATION,
					"* 이는 초당 약 2 조 개의 명령을 수행\n"
					+ "  할 수 있는 프로세서를 임의로\n"
					+ "  기반으로합니다.");

		return Collections.unmodifiableMap(map);
	}

	private static Map<Integer,String> getLanguageStringsFrench()
	{
		Map<Integer,String> map = new HashMap<Integer,String>();

		map.put(STRING_APPLICATION_NAME, "PassMan");
		map.put(STRING_PASSWORD_ID, "ID");
		map.put(STRING_USERNAME, "Pseudo");
		map.put(STRING_PASSWORD, "Mot de passe");
		map.put(STRING_NEW_PASSWORD, "Nouveau mot de passe");
		map.put(STRING_OLD_PASSWORD, "Ancien mot de passe");
		map.put(STRING_PASSWORD_LENGTH, "Longueur du mot de passe (8 - 100)");
		map.put(STRING_MASTER_PASSWORD, "Mot de passe maître");
		map.put(STRING_CREATION_TIME, "Crée");
		map.put(STRING_COPY_PASSWORD, "Copier le mot de passe");
		map.put(STRING_CHANGE_SETTINGS, "Mettre à jour des parametres");
		map.put(STRING_UNLOCK_PASSWORD_FILE, "Déverouiller le fichier des mots de passes");
		map.put(STRING_CURRENT_PASSWORD, "Saisir mot de passe actuel");
		//map.put(STRING_NEW_PASSWORD, "Saisir nouveau mot de passe");
		map.put(STRING_CONFIRM_NEW_PASSWORD, "Vérifier nouveau mot de passe");
		map.put(STRING_CONFIRM_PASSWORD, "Vérifier mot de passe");
		map.put(STRING_CHANGE_MASTER_PASSWORD, "Mettre à jour mot de passe maître");
		map.put(STRING_LANGUAGE, "Langue");
		map.put(STRING_PASSWORD_ID_LIST, "Liste des IDs");
		map.put(STRING_POSSIBLE_PERMUTATIONS, "Permutations possibles");
		map.put(STRING_PASSWORD_LENGTH2, "Longueur du mot de passe");
		map.put(STRING_COPY, "Copier");
		map.put(STRING_CRACK_TIME, "Temps pour cracker");
		map.put(STRING_SECONDS, "secondes");
		map.put(STRING_DAYS, "jours");
		map.put(STRING_YEARS, "ans");
		map.put(STRING_LEAVE_BLANK, "Laisser vide pour garder mot de passe actuel");
		map.put(STRING_CHARACTER_SET, "Jeu de caractères");
		map.put(STRING_TOGGLE_CHARACTER_SET, "Basculer des caractères dans le jeu de caractères pour les mots de passe");
		map.put(STRING_GENERATE_RANDOM, "En générer un au hasard");
		map.put(STRING_SIZE_CHARACTER_SET, "Taille du jeu de caractères");
		map.put(STRING_CHANGE_ID, "Modifier ID");
		map.put(STRING_CHANGE_USERNAME, "Modifier Pseudo");
		map.put(STRING_CHANGE_PASSWORD, "Modifier Mot de Passe");
		map.put(STRING_PASTE, "Coller");
		map.put(STRING_CREATE_BACKUP_FILE, "Créer fichier de sauvegarde");

		map.put(STRING_TITLE_PASSWORD_MANAGER_CONFIGURATION, "");
		map.put(STRING_TITLE_PASSWORD_DETAILS, "Détails du mot de passe");
		map.put(STRING_TITLE_PASSWORD_ANALYSIS, "Analyse du Mot de Passe");

		map.put(STRING_PROMPT_DETAILS_CHANGED, "Les détails ont été changés avec succès");
		map.put(STRING_PROMPT_PASSWORD_COPIED, "Mot de passe copié");
		map.put(STRING_PROMPT_MASTER_PASSWORD_CHANGED, "");
		map.put(STRING_PROMPT_PASSWORD_CREATED, "Mot de passe créé et ajouté avec succès");
		map.put(STRING_PROMPT_PASSWORD_FILE_CREATED, "Fichier des mots de passes créé");
		map.put(STRING_PROMPT_PASSWORD_WILL_BE_CHANGED, "Le mot de passe pour cet ID sera mis à jour. Voudriez-vous procéder ?");
		map.put(STRING_PROMPT_PASSWORD_WILL_BE_REMOVED, "Le mot de passe pour cet ID sera supprimé. Voudiez-vous procéder ?");
		map.put(STRING_PROMPT_PASSWORD_REMOVED, "Mot de passe supprimé");
		map.put(STRING_PROMPT_NO_PASSWORDS, "Aucun mot de passe");
		map.put(STRING_PROMPT_OK, "OK");
		map.put(STRING_PROMPT_CANCEL, "Annuler");
		map.put(STRING_PROMPT_CHANGED_LANGUAGE, "La langue a été changée");
		map.put(STRING_PROMPT_UNLOCK_PASSWORD_FILE, "Saisir mot de passe maître");
		map.put(STRING_PROMPT_CREATED_BACKUP_FILE, "Fichier de sauvegarde a été créée");

		map.put(STRING_ERROR_PASSWORD_ID, "Le mot de passe pour cet ID n'a pas pu être mis à jour");
		map.put(STRING_ERROR_PASSWORD_ID_EXISTS, "Il y a déjà un mot de passe avec cet ID");
		map.put(STRING_ERROR_PASSWORDS_DO_NOT_MATCH, "Les mots de passes ne correspondent pas");
		map.put(STRING_ERROR_INVALID_PASSWORD_LENGTH, "Longueur du mot de passe invalide");
		map.put(STRING_ERROR_INCORRECT_PASSWORD, "Mot de passe est incorrect");
		map.put(STRING_ERROR_NO_ENTRY, "Aucune entrée trouvée pour cet ID");
		map.put(STRING_ERROR_SELECT_PASSWORD_ID, "Il faut choisir un ID");
		map.put(STRING_ERROR_PASSWORD_NOT_CHANGED, "Le mot de passe n'a pas pu être mis à jour pour cet ID");
		map.put(STRING_ERROR_SELECT_LANGUAGE, "Il faut choisir une langue");
		map.put(STRING_ERROR_TOO_MANY_ATTEMPTS, "Trop de tentatives incorrectes !");

		map.put(STRING_CONFIGURATION_PROMPT,
					"Un nouveau fichier de mots de passes sera crée. Le\n"
					+ "mot de passe que vous choisissez maintenant sera\n"
					+ "utilisé pour chiffrer et sécuriser ce fichier. Faites"
					+ "en sorte de ne pas oublier ce mot de passe, sinon\n"
					+ "il sera impossible de récupérer vos données !");

		/*
		 * In French, billion == milliard, and trillion == billion.
		 */
		map.put(STRING_PASSWORD_STRENGTH_INFORMATION,
					"* Ceci se base arbitrairement sur un\n"
					+ "  processeur qui peut réaliser environ\n"
					+ "  2 billions d'instructions par seconde\n");

		return Collections.unmodifiableMap(map);
	}

	private static Map<Integer,String> getLanguageStringsEnglish()
	{
		Map<Integer,String> map = new HashMap<Integer,String>();

		map.put(STRING_APPLICATION_NAME, "PassMan");
		map.put(STRING_PASSWORD_ID, "Password ID");
		map.put(STRING_USERNAME, "Username");
		map.put(STRING_PASSWORD, "Password");
		map.put(STRING_NEW_PASSWORD, "New password");
		map.put(STRING_OLD_PASSWORD, "Old password");
		map.put(STRING_PASSWORD_LENGTH, "Password length (8 - 100)");
		map.put(STRING_MASTER_PASSWORD, "Master password");
		map.put(STRING_CREATION_TIME, "Created");
		map.put(STRING_COPY_PASSWORD, "Copy password to clipboard");
		map.put(STRING_CHANGE_SETTINGS, "Update settings");
		map.put(STRING_UNLOCK_PASSWORD_FILE, "Unlock the password file");
		map.put(STRING_CURRENT_PASSWORD, "Enter current password");
		//map.put(STRING_NEW_PASSWORD, "Enter new password");
		map.put(STRING_CONFIRM_NEW_PASSWORD, "Confirm new password");
		map.put(STRING_CONFIRM_PASSWORD, "Confirm password");
		map.put(STRING_CHANGE_MASTER_PASSWORD, "Change master password");
		map.put(STRING_LANGUAGE, "Language");
		map.put(STRING_PASSWORD_ID_LIST, "Password ID List");
		map.put(STRING_POSSIBLE_PERMUTATIONS, "Possible permutations");
		map.put(STRING_PASSWORD_LENGTH2, "Password length");
		map.put(STRING_COPY, "Copy");
		map.put(STRING_CRACK_TIME, "Time to Crack");
		map.put(STRING_SECONDS, "seconds");
		map.put(STRING_DAYS, "days");
		map.put(STRING_YEARS, "years");
		map.put(STRING_LEAVE_BLANK, "Leave blank to keep current password");
		map.put(STRING_CHARACTER_SET, "Password character set");
		map.put(STRING_TOGGLE_CHARACTER_SET, "Toggle characters in the password character set");
		map.put(STRING_GENERATE_RANDOM, "Generate random");
		map.put(STRING_SIZE_CHARACTER_SET, "Character set size");
		map.put(STRING_CHANGE_ID, "Modify ID");
		map.put(STRING_CHANGE_USERNAME, "Modify Username");
		map.put(STRING_CHANGE_PASSWORD, "Modify Password");
		map.put(STRING_PASTE, "Paste");
		map.put(STRING_CREATE_BACKUP_FILE, "Create backup file");

		map.put(STRING_TITLE_PASSWORD_MANAGER_CONFIGURATION, "Password Manager Configuration");
		map.put(STRING_TITLE_PASSWORD_DETAILS, "Password Details");
		map.put(STRING_TITLE_PASSWORD_ANALYSIS, "Analysis of Password");

		map.put(STRING_PROMPT_DETAILS_CHANGED, "Details successfully changed");
		map.put(STRING_PROMPT_PASSWORD_COPIED, "Password copied to clipboard");
		map.put(STRING_PROMPT_MASTER_PASSWORD_CHANGED, "Master password changed");
		map.put(STRING_PROMPT_PASSWORD_CREATED, "Password created and added");
		map.put(STRING_PROMPT_PASSWORD_FILE_CREATED, "Password file successfully created");
		map.put(STRING_PROMPT_PASSWORD_CREATED, "Successfully created and added password");
		map.put(STRING_PROMPT_PASSWORD_WILL_BE_CHANGED, "This will change the password for the selected ID. Do you want to continue?");
		map.put(STRING_PROMPT_PASSWORD_WILL_BE_REMOVED, "This will remove the password for the selected ID! Do you want to continue?");
		map.put(STRING_PROMPT_PASSWORD_REMOVED, "Password removed");
		map.put(STRING_PROMPT_NO_PASSWORDS, "No passwords");
		map.put(STRING_PROMPT_OK, "OK");
		map.put(STRING_PROMPT_CANCEL, "Cancel");
		map.put(STRING_PROMPT_CHANGED_LANGUAGE, "Language changed");
		map.put(STRING_PROMPT_UNLOCK_PASSWORD_FILE, "Enter master password");
		map.put(STRING_PROMPT_CREATED_BACKUP_FILE, "Created backup file");

		map.put(STRING_ERROR_PASSWORD_ID, "Could not change password for this ID");
		map.put(STRING_ERROR_PASSWORD_ID_EXISTS, "");
		map.put(STRING_ERROR_INCORRECT_PASSWORD, "Incorrect password");
		map.put(STRING_ERROR_PASSWORDS_DO_NOT_MATCH, "Passwords do not match");
		map.put(STRING_ERROR_INVALID_PASSWORD_LENGTH, "Invalid password length");
		map.put(STRING_ERROR_NO_ENTRY, "No entry found for this ID");
		map.put(STRING_ERROR_SELECT_PASSWORD_ID, "You must select an ID");
		map.put(STRING_ERROR_PASSWORD_NOT_CHANGED, "Password could not be changed for this ID");
		map.put(STRING_ERROR_SELECT_LANGUAGE, "You must select a language");
		map.put(STRING_ERROR_TOO_MANY_ATTEMPTS, "Too many incorrect attempts!");

		map.put(STRING_CONFIGURATION_PROMPT,
					"This  will create a new password file. The password\n"
					+ "you choose now will be used to encrypt the file and\n"
					+ "keep it secure. Make sure you do not lose this password,\n"
					+ "otherwise  the data in the password  file cannot be\n"
					+ "recuperated!");

		map.put(STRING_PASSWORD_STRENGTH_INFORMATION,
					"* This is based arbitrarily on a processor\n"
					+ "  that can perform around 2 trillion\n"
					+ "  instructions per second.");

		return Collections.unmodifiableMap(map);
	}

	private static Map<Integer,String> getLanguageStringsMalaysian()
	{
		Map<Integer,String> map = new HashMap<Integer,String>();

		map.put(STRING_APPLICATION_NAME, "PassMan");
		map.put(STRING_PASSWORD_ID, "Kata laluan ID");
		map.put(STRING_USERNAME, "Nama lengguna");
		map.put(STRING_PASSWORD, "Kata laluan");
		map.put(STRING_NEW_PASSWORD, "Kata laluan baru");
		map.put(STRING_OLD_PASSWORD, "Kata laluan lama");
		map.put(STRING_PASSWORD_LENGTH, "Panjang kata laluan (8 - 100)");
		map.put(STRING_MASTER_PASSWORD, "Kata laluan induk");
		map.put(STRING_CREATION_TIME, "Dicipta");
		map.put(STRING_COPY_PASSWORD, "Salin kata laluan ke papan klip");
		map.put(STRING_CHANGE_SETTINGS, "Kemas kini tetapan");
		map.put(STRING_UNLOCK_PASSWORD_FILE, "Membuka kunci file kata laluan");
		map.put(STRING_CURRENT_PASSWORD, "Masukkan kata laluan terkini");
		//map.put(STRING_NEW_PASSWORD, "Masukkan kata laluan baru");
		map.put(STRING_CONFIRM_NEW_PASSWORD, "Pengesahan kata laluan baru");
		map.put(STRING_CONFIRM_PASSWORD, "Pengesahan kata laluan");
		map.put(STRING_CHANGE_MASTER_PASSWORD, "Ubah kata laluan induk");
		map.put(STRING_LANGUAGE, "Bahasa");
		map.put(STRING_PASSWORD_ID_LIST, "Senarai ID Kata Laluan");
		map.put(STRING_POSSIBLE_PERMUTATIONS, "Permutasi yang mungkin");
		map.put(STRING_PASSWORD_LENGTH2, "Panjang kata laluan");
		map.put(STRING_COPY, "Salin");
		map.put(STRING_CRACK_TIME, "Masa untuk Crack");
		map.put(STRING_SECONDS, "saat");
		map.put(STRING_DAYS, "hari");
		map.put(STRING_YEARS, "tahun");
		map.put(STRING_LEAVE_BLANK, "Biarkan kosong untuk menyimpan kata laluan semasa");
		map.put(STRING_CHARACTER_SET, "Set aksara aksara.");
		map.put(STRING_TOGGLE_CHARACTER_SET, "Togol aksara dalam set aksara kata laluan");
		map.put(STRING_GENERATE_RANDOM, "Menjana satu secara rawak");
		map.put(STRING_SIZE_CHARACTER_SET, "Saiz set watak");
		map.put(STRING_CHANGE_ID, "Ubah kata laluan ID");
		map.put(STRING_CHANGE_USERNAME, "Ubah lengguna");
		map.put(STRING_CHANGE_PASSWORD, "Ubah kata laluan");
		map.put(STRING_PASTE, "Tampal");
		map.put(STRING_CREATE_BACKUP_FILE, "Buat fail sandaran");

		map.put(STRING_TITLE_PASSWORD_MANAGER_CONFIGURATION, "Konfigurasi Pengurus Kata Laluan");
		map.put(STRING_TITLE_PASSWORD_DETAILS, "Butiran Kata Laluan");
		map.put(STRING_TITLE_PASSWORD_ANALYSIS, "Analisis Kata Laluan");

		map.put(STRING_PROMPT_DETAILS_CHANGED, "Butiran telah berjaya dikemaskini");
		map.put(STRING_PROMPT_PASSWORD_COPIED, "Kata laluan telah disalin di papan klip");
		map.put(STRING_PROMPT_MASTER_PASSWORD_CHANGED, "Kata laluan induk telah diubah");
		map.put(STRING_PROMPT_PASSWORD_CREATED, "Kata Laluan baru berjaya dicipta dan ditambah.");
		map.put(STRING_PROMPT_PASSWORD_FILE_CREATED, "Fail kata laluan baru telah dicipta.");
		map.put(STRING_PROMPT_PASSWORD_WILL_BE_CHANGED, "Ini akan mengubah kata laluan untuk ID terpilih. Adakah anda ingin sambung?");
		map.put(STRING_PROMPT_PASSWORD_WILL_BE_REMOVED, "Ini akan keluarkan kata laluan untuk ID terpilih. Adakah anda ingin sambung?");
		map.put(STRING_PROMPT_PASSWORD_REMOVED, "Kata laluan dikeluarkan.");
		map.put(STRING_PROMPT_NO_PASSWORDS, "Tiada kata laluan");
		map.put(STRING_PROMPT_OK, "Okey");
		map.put(STRING_PROMPT_CANCEL, "Batalkan");
		map.put(STRING_PROMPT_CHANGED_LANGUAGE, "Bahasa diubah");
		map.put(STRING_PROMPT_UNLOCK_PASSWORD_FILE, "Masukkan kata laluan induk");
		map.put(STRING_PROMPT_CREATED_BACKUP_FILE, "Fail sandaran dibuat");

		map.put(STRING_ERROR_PASSWORD_ID, "Kata laluan tidak boleh diubah untuk ID ini. ");
		map.put(STRING_ERROR_PASSWORD_ID_EXISTS, "Kata laluan untuk ID ini sudah wujud.");
		map.put(STRING_ERROR_PASSWORDS_DO_NOT_MATCH, "Kata laluan tidak sepadan");
		map.put(STRING_ERROR_INVALID_PASSWORD_LENGTH, "Panjang kata laluan tidak sah");
		map.put(STRING_ERROR_INCORRECT_PASSWORD, "Kata laluan salah");
		map.put(STRING_ERROR_NO_ENTRY, "Tiada entri dijumpai untuk ID ini.");
		map.put(STRING_ERROR_SELECT_PASSWORD_ID, "Anda perlu memilih ID");
		map.put(STRING_ERROR_PASSWORD_NOT_CHANGED, "Tidak boleh ubah kata laluan untuk ID ini");
		map.put(STRING_ERROR_SELECT_LANGUAGE, "Anda mesti memilih bahasa");
		map.put(STRING_ERROR_TOO_MANY_ATTEMPTS, "Terlalu banyak percubaan yang salah!");

		map.put(STRING_CONFIGURATION_PROMPT,
					"Ini adalah untuk membuat fail kata laluan baru.\n"
					+ "Kata laluan pilihan anda akan digunakan untuk\n"
					+ "menyulitkan fail dan penyimpanan yang lebih selamat.\n"
					+ "Pastikan anda tidak hilang kata laluan ini, Jika\n"
					+ "tidak, data dalam fail kata laluan tidak dapat\n"
					+ "dipulihkan!");

		map.put(STRING_PASSWORD_STRENGTH_INFORMATION,
					"* Ini berdasarkan sembarangan pada\n"
					+ "  pemproses yang boleh melaksanakan\n"
					+ "  sekitar 2 trilion arahan sesaat.");

		return Collections.unmodifiableMap(map);
	}

	private Map<String,Map<Integer,String> > languageStrings = new HashMap<String,Map<Integer,String> >();
	private Map<Integer,String> currentLanguage = null;

	/**
	 * Get a very crude estimation of
	 * the number of pixels of width
	 * a string takes up in a frame.
	 *
	 * @param str The string to measure.
	 */
	private int getStringWidth(String str)
	{
		int width = 0;

		if (specialLanguageList.contains(currentLanguage))
			width = (str.length() * 15);
		else
			width = (str.length() * 10);

		return width;
	}

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

		JTextArea taCharacterSetInfo = new JTextArea(currentLanguage.get(STRING_TOGGLE_CHARACTER_SET));

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
		Object[] options = { currentLanguage.get(STRING_PROMPT_OK) };

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
		Object[] options = { currentLanguage.get(STRING_PROMPT_OK), currentLanguage.get(STRING_PROMPT_CANCEL) };

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
				com.fasterxml.jackson.core.type.TypeReference<HashMap<String,ArrayList<PasswordEntry>>>() {};

			//System.out.println(fContents);
			byte[] data = fContents.getBytes("UTF-8");
			passwordEntries = (HashMap<String,ArrayList<PasswordEntry>>)mapper.readValue(data, typeRef);
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
		frame.setTitle(currentLanguage.get(STRING_TITLE_PASSWORD_ANALYSIS));

		JLabel containerIcon = new JLabel(iconAnalysis128);

		JLabel labelPermutations = new JLabel(currentLanguage.get(STRING_POSSIBLE_PERMUTATIONS));
		JLabel labelPasswordLen = new JLabel(currentLanguage.get(STRING_PASSWORD_LENGTH2));
		JLabel labelSizeCharacterSet = new JLabel(currentLanguage.get(STRING_SIZE_CHARACTER_SET));
		JLabel labelCrackTime = new JLabel(currentLanguage.get(STRING_CRACK_TIME));

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

		JTextArea taInfo = new JTextArea(currentLanguage.get(STRING_PASSWORD_STRENGTH_INFORMATION));

		taInfo.setEditable(false);
		taInfo.setBackground(contentPane.getBackground());
		taInfo.setFont(fontInfo);
		taInfo.setBorder(null);

	/*
	 * Let's just say, for the sake of argument, that
	 * testing a possible password permutation takes
	 * 5 instructions.
	 */
		Double INSTRUCTIONS_PER_SECOND = new Double("2000000000000.0");
		double INSTRUCTIONS_PER_ATTEMPT = 5.0;
		double ATTEMPTS_PER_SECOND = (INSTRUCTIONS_PER_SECOND / INSTRUCTIONS_PER_ATTEMPT);

		double nrPermutations = Math.pow((double)entry.getCharsetSize(), (double)entry.getPassword().length());

	/*
	 * On average, one crack a password after testing half.
	 */
		double secondsToCrack = (nrPermutations/2) / ATTEMPTS_PER_SECOND;
		double daysToCrack = secondsToCrack / (double)SECONDS_PER_DAY;
		//double weeksToCrack = secondsToCrack / (double)SECONDS_PER_WEEK;
		double yearsToCrack = secondsToCrack / SECONDS_PER_YEAR;

		JTextField tfPermutations = new JTextField(String.format("%6.3e", nrPermutations));
		JTextField tfPasswordLen = new JTextField(String.format("%d", entry.getPassword().length()));
		JTextField tfSizeCharacterSet = new JTextField(String.format("%d", entry.getCharsetSize()));
		JTextField tfCrackTimeSeconds = new JTextField(String.format("%.3e " + currentLanguage.get(STRING_SECONDS), secondsToCrack));
		JTextField tfCrackTimeDays = new JTextField(String.format("%.3e " + currentLanguage.get(STRING_DAYS), daysToCrack));
		JTextField tfCrackTimeYears = new JTextField(String.format("%.3e " + currentLanguage.get(STRING_YEARS), yearsToCrack));

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
		frame.setTitle(currentLanguage.get(STRING_TITLE_PASSWORD_DETAILS));

		JLabel unlockedContainer = new JLabel(iconUnlocked128);
		SpringLayout spring = new SpringLayout();

		JLabel labelUsername = new JLabel(currentLanguage.get(STRING_USERNAME));
		JLabel labelPass = new JLabel(currentLanguage.get(STRING_PASSWORD));
		JLabel labelWhen = new JLabel(currentLanguage.get(STRING_CREATION_TIME));

		Dimension labelSize = new Dimension(labelWidth, labelHeight);

		JLabel labelCopy = new JLabel(currentLanguage.get(STRING_COPY_PASSWORD));
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

		JTextArea taInfo = new JTextArea(currentLanguage.get(STRING_PROMPT_DETAILS_CHANGED));

		taInfo.setBorder(null);
		taInfo.setFont(new Font("Verdana", Font.PLAIN, 22));
		taInfo.setBackground(frame.getBackground());
		taInfo.setEditable(false);

		JLabel labelId = new JLabel(currentLanguage.get(STRING_PASSWORD_ID));
		JLabel labelUsername = new JLabel(currentLanguage.get(STRING_USERNAME));
		JLabel labelOldPass = new JLabel(currentLanguage.get(STRING_CURRENT_PASSWORD));
		JLabel labelPass = new JLabel(currentLanguage.get(STRING_NEW_PASSWORD));
		JLabel labelWhen = new JLabel(currentLanguage.get(STRING_CREATION_TIME));

		Dimension labelSize = new Dimension(labelWidth, labelHeight);

		labelId.setPreferredSize(labelSize);
		labelUsername.setPreferredSize(labelSize);
		labelOldPass.setPreferredSize(labelSize);
		labelPass.setPreferredSize(labelSize);
		labelWhen.setPreferredSize(labelSize);

		JLabel labelCopy = new JLabel(currentLanguage.get(STRING_COPY_PASSWORD));
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
				showInfoDialog(currentLanguage.get(STRING_PROMPT_PASSWORD_COPIED));
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
		frame.setTitle(currentLanguage.get(STRING_TITLE_PASSWORD_DETAILS));

		JLabel unlockedContainer = new JLabel(iconUnlocked128);
		SpringLayout spring = new SpringLayout();

		JLabel labelId = new JLabel(currentLanguage.get(STRING_PASSWORD_ID));
		JLabel labelUsername = new JLabel(currentLanguage.get(STRING_USERNAME));
		JLabel labelPass = new JLabel(currentLanguage.get(STRING_PASSWORD));
		JLabel labelWhen = new JLabel(currentLanguage.get(STRING_CREATION_TIME));

		Dimension labelSize = new Dimension(labelWidth, labelHeight);

		labelId.setPreferredSize(labelSize);
		labelUsername.setPreferredSize(labelSize);
		labelPass.setPreferredSize(labelSize);
		labelWhen.setPreferredSize(labelSize);

		labelId.setFont(fontLabel);
		labelUsername.setFont(fontLabel);
		labelPass.setFont(fontLabel);
		labelWhen.setFont(fontLabel);

		JLabel labelCopy = new JLabel(currentLanguage.get(STRING_COPY_PASSWORD));
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
				showInfoDialog(currentLanguage.get(STRING_PROMPT_PASSWORD_COPIED));
			}
		});

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
*/
	private boolean passwordIdExists(String id)
	{
		if (null == passwordEntries)
			return false;

		if (passwordEntries.containsKey(id))
			return true;

		return false;
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
		JTextArea taPrompt = new JTextArea(currentLanguage.get(STRING_CHANGE_MASTER_PASSWORD));

		JLabel labelOldPassword = new JLabel(currentLanguage.get(STRING_CURRENT_PASSWORD));
		JPasswordField passFieldOld = new JPasswordField(23);
		JLabel labelPassword = new JLabel(currentLanguage.get(STRING_NEW_PASSWORD));
		JPasswordField passField = new JPasswordField(23);
		JLabel labelPasswordConfirm = new JLabel(currentLanguage.get(STRING_CONFIRM_NEW_PASSWORD));
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
					showErrorDialog(currentLanguage.get(STRING_ERROR_INCORRECT_PASSWORD));
					return;
				}

				if (false == newPass.equals(newPassConfirm))
				{
					showErrorDialog(currentLanguage.get(STRING_ERROR_PASSWORDS_DO_NOT_MATCH));
					return;
				}

				password = newPass;

			/*
			 * Will use the global var PASSWORD to encrypt
			 * and write the cached password entries to disk.
			 */
				putPasswordEntries();
				showInfoDialog(currentLanguage.get(STRING_PROMPT_MASTER_PASSWORD_CHANGED));

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
			showErrorDialog(currentLanguage.get(STRING_ERROR_SELECT_LANGUAGE));
			return;
		}

		try
		{
			FileOutputStream fOut = new FileOutputStream(new File(configFile), false);

			String newLanguageEntry = "<language>\n" + settingsSelectedLanguage.getText() + "\n</language>";
			fOut.write(newLanguageEntry.getBytes());
			fOut.flush();
			fOut.close();

			//previousLanguage = currentLanguage;
			currentLanguage = languageStrings.get(settingsSelectedLanguage.getText());

			if (null == currentLanguage)
			{
				showErrorDialog("An error occurred - defaulting to English");
				currentLanguage = languageEnglish;
				return;
			}

			showInfoDialog(currentLanguage.get(STRING_PROMPT_CHANGED_LANGUAGE));

		/*
		 * Set the text in currently visible global GUI components
		 * to the newly chosen language.
		 */
			// in main JFrame (created in setupGUI)
			setTitle(currentLanguage.get(STRING_APPLICATION_NAME) + " v" + VERSION);
			taAppName.setText(currentLanguage.get(STRING_APPLICATION_NAME));
			labelPasswordIds.setText(currentLanguage.get(STRING_PASSWORD_ID_LIST));

			// in JFrame created in showSettings
			labelMasterPassword.setText(currentLanguage.get(STRING_MASTER_PASSWORD));
			labelLanguage.setText(currentLanguage.get(STRING_LANGUAGE));
			labelCharacterSet.setText(currentLanguage.get(STRING_CHARACTER_SET));

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
		labelMasterPassword = new JLabel(currentLanguage.get(STRING_MASTER_PASSWORD));
		labelLanguage = new JLabel(currentLanguage.get(STRING_LANGUAGE));
		labelCharacterSet = new JLabel(currentLanguage.get(STRING_CHARACTER_SET));

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
		setTitle(currentLanguage.get(STRING_TITLE_PASSWORD_MANAGER_CONFIGURATION));

		contentPane.setLayout(spring);

		JTextArea taInfo = new JTextArea(currentLanguage.get(STRING_CONFIGURATION_PROMPT));

		taInfo.setFont(fontLabel);
		taInfo.setEditable(false);
		taInfo.setBackground(colorFrame);

		JLabel labelPass = new JLabel(currentLanguage.get(STRING_PASSWORD));
		JLabel labelConfirm = new JLabel(currentLanguage.get(STRING_CONFIRM_PASSWORD));

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
					showErrorDialog(currentLanguage.get(STRING_ERROR_PASSWORDS_DO_NOT_MATCH));
					return;
				}
				else
				if (pass1.length() < 8 || pass1.length() > 100)
				{
					showErrorDialog(currentLanguage.get(STRING_ERROR_INVALID_PASSWORD_LENGTH));
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

				showInfoDialog(currentLanguage.get(STRING_PROMPT_PASSWORD_FILE_CREATED));

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

	/**
	 * Add new password to file
	 * @param buttonGrid where to add the button with password ID on main window
	 */
	private void doAddNewPassword(DefaultTableModel model)
	{
		if (null == model)
		{
			showErrorDialog("doNewPassword() received null arg");
			return;
		}

		JFrame frame = new JFrame();
		Container contentPane = frame.getContentPane();
		SpringLayout spring = new SpringLayout();

		final int windowWidth = 550;
		final int windowHeight = 600;
		final int tfWidth = 425;
		final int tfHeight = 25;
		Dimension tfSize = new Dimension(tfWidth, tfHeight);

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(windowWidth, windowHeight);
		contentPane.setLayout(spring);

		JLabel padlockContainer = new JLabel(iconLocked64);
		JLabel labelId = new JLabel(currentLanguage.get(STRING_PASSWORD_ID));
		JLabel labelEmail = new JLabel("Email");
		JLabel labelUsername = new JLabel(currentLanguage.get(STRING_USERNAME));
		JLabel labelPasswordLen = new JLabel(currentLanguage.get(STRING_PASSWORD_LENGTH));
		JLabel labelPassword = new JLabel(currentLanguage.get(STRING_PASSWORD));

		Font fLabel = new Font("Courier New", Font.PLAIN, 16);

		labelId.setFont(fLabel);
		labelEmail.setFont(fLabel);
		labelUsername.setFont(fLabel);
		labelPasswordLen.setFont(fLabel);
		labelPassword.setFont(fLabel);

		JTextField tfId = new JTextField("");
		JTextField tfEmail = new JTextField("");
		JTextField tfUsername = new JTextField("");
		JTextField tfPasswordLen = new JTextField("");
		JTextField tfPassword = new JTextField("");

		tfId.setFont(fontInput);
		tfEmail.setFont(fontInput);
		tfUsername.setFont(fontInput);
		tfPasswordLen.setFont(fontInput);
		tfPassword.setFont(fontInput);

		tfId.setPreferredSize(tfSize);
		tfEmail.setPreferredSize(tfSize);
		tfUsername.setPreferredSize(tfSize);
		tfPasswordLen.setPreferredSize(tfSize);
		tfPassword.setPreferredSize(tfSize);

		tfId.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfEmail.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfUsername.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfPasswordLen.setComponentPopupMenu(new RightClickPopup().getMenu());
		tfPassword.setComponentPopupMenu(new RightClickPopup().getMenu());

		tfPasswordLen.setEditable(false);

		JCheckBox checkbox = new JCheckBox(currentLanguage.get(STRING_GENERATE_RANDOM), false);

		JButton buttonConfirm = new JButton(iconConfirm32);
		JButton buttonChangeCharset = new JButton(iconSpanner32);
		JLabel labelChangeCharset = new JLabel("Charset");
		JLabel labelConfirm = new JLabel("Confirm");

		buttonConfirm.setBackground(colorFrame);
		buttonConfirm.setBorder(null);

		buttonChangeCharset.setBackground(colorFrame);
		buttonChangeCharset.setBorder(null);

		final int halfWidth = (windowWidth>>1);
		final int leftOffset = 40;

		final int labelFieldGap = 5;
		final int fieldNextLabelGap = 12;

		final int iconWest = (halfWidth - (iconConfirm32.getIconWidth()>>1));
		final int tfWest = (halfWidth - (tfWidth>>1));

		spring.putConstraint(SpringLayout.WEST, padlockContainer, iconWest-10, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, padlockContainer, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, labelId, 10, SpringLayout.WEST, tfId);
		spring.putConstraint(SpringLayout.NORTH, labelId, 30, SpringLayout.SOUTH, padlockContainer);

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

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, 20, SpringLayout.EAST, padlockContainer);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 40, SpringLayout.SOUTH, tfPasswordLen);

		spring.putConstraint(SpringLayout.WEST, labelConfirm, 10, SpringLayout.EAST, buttonConfirm);
		spring.putConstraint(SpringLayout.NORTH, labelConfirm, 5, SpringLayout.NORTH, buttonConfirm);

		spring.putConstraint(SpringLayout.EAST, buttonChangeCharset, -10, SpringLayout.WEST, labelChangeCharset);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeCharset, 40, SpringLayout.SOUTH, tfPasswordLen);

		spring.putConstraint(SpringLayout.EAST, labelChangeCharset, -20, SpringLayout.WEST, padlockContainer);
		spring.putConstraint(SpringLayout.NORTH, labelChangeCharset, 5, SpringLayout.NORTH, buttonChangeCharset);

		contentPane.add(padlockContainer);
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
				tfEmail.requestFocusInWindow();
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
						showErrorDialog(currentLanguage.get(STRING_ERROR_INVALID_PASSWORD_LENGTH));
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
						showErrorDialog(currentLanguage.get(STRING_ERROR_INVALID_PASSWORD_LENGTH));
						return;
					}

					newPassword = tfPassword.getText();
				}

				PasswordEntry newEntry = new PasswordEntry();

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

				model.addRow(new Object[] {
					tfId.getText(),
					newEntry.getEmail(),
					newEntry.getUsername(),
					newEntry.getPassword(),
					newEntry.getPassword().length(),
					0,
					newEntry.getHash()
				});

				showInfoDialog(currentLanguage.get(STRING_PROMPT_PASSWORD_CREATED));
				//showPasswordDetails(newEntry);

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

	private void doRemovePassword(String hash)
	{
		if (null == passwordEntries)
		{
			showErrorDialog(currentLanguage.get(STRING_PROMPT_NO_PASSWORDS));
			return;
		}

		if (-1 == currentlySelectedRow)
		{
			showErrorDialog("No row selected");
			return;
		}

		int action = showQuestionDialog(currentLanguage.get(STRING_PROMPT_PASSWORD_WILL_BE_REMOVED));

		if (JOptionPane.CANCEL_OPTION == action)
			return;

		removeEntryForHash(hash);
		putPasswordEntries();
		model.removeRow(currentlySelectedRow);

		revalidate();

		showInfoDialog(currentLanguage.get(STRING_PROMPT_PASSWORD_REMOVED));
	}

	private PasswordEntry getEntryForHash(String hash)
	{
		Set<HashMap.Entry<String,ArrayList<PasswordEntry>>> set = passwordEntries.entrySet();
		Iterator<HashMap.Entry<String,ArrayList<PasswordEntry>>> iter = set.iterator();
		Iterator<PasswordEntry> it = null;
		PasswordEntry entry = null;

		while (iter.hasNext())
		{
			HashMap.Entry<String,ArrayList<PasswordEntry>> e = iter.next();
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
		Set<HashMap.Entry<String,ArrayList<PasswordEntry>>> set = passwordEntries.entrySet();
		Iterator<HashMap.Entry<String,ArrayList<PasswordEntry>>> iter = set.iterator();
		Iterator<PasswordEntry> it = null;
		PasswordEntry entry = null;
		ArrayList<PasswordEntry> list = null;

		while (iter.hasNext())
		{
			HashMap.Entry<String,ArrayList<PasswordEntry>> e = iter.next();
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
		Set<HashMap.Entry<String,ArrayList<PasswordEntry>>> set = passwordEntries.entrySet();
		Iterator<HashMap.Entry<String,ArrayList<PasswordEntry>>> iter = set.iterator();
		Iterator<PasswordEntry> it = null;
		PasswordEntry entry = null;
		ArrayList<PasswordEntry> list = null;

		while (iter.hasNext())
		{
			HashMap.Entry<String,ArrayList<PasswordEntry>> e = iter.next();
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

	private void doChangeDetails()
	{
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
		final int windowWidth = 650;
		final int windowHeight = 700;
		final int tfWidth = 450;
		final int tfHeight = 25;

		frame.setSize(windowWidth, windowHeight);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		contentPane.setLayout(spring);

		JLabel containerIconLocked = new JLabel(iconLocked128);

		JLabel labelEmail = new JLabel("Email");
		JLabel labelUsername = new JLabel(currentLanguage.get(STRING_USERNAME));
		JLabel labelPassword = new JLabel(currentLanguage.get(STRING_PASSWORD));
		JLabel labelPasswordLen = new JLabel(currentLanguage.get(STRING_PASSWORD_LENGTH));
		//JLabel labelLeaveBlank = new JLabel(currentLanguage.get(STRING_LEAVE_BLANK));

		labelEmail.setFont(fontLabel);
		labelUsername.setFont(fontLabel);
		labelPassword.setFont(fontLabel);
		labelPasswordLen.setFont(fontLabel);
		//labelLeaveBlank.setFont(new Font("Verdana", Font.PLAIN, 14));

		String hash = (String)table.getValueAt(currentlySelectedRow, IDX_UNIQUE_ID);
		PasswordEntry entry = getEntryForHash(hash);

		JTextField tfEmail = new JTextField(entry.getEmail());
		JTextField tfUsername = new JTextField(entry.getUsername());
		JTextField tfPassword = new JTextField();
		JTextField tfPasswordLen = new JTextField();

		Dimension sizeTextField = new Dimension(tfWidth, tfHeight);

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

		JCheckBox checkModifyEmail = new JCheckBox(currentLanguage.get(STRING_CHANGE_ID), true);
		JCheckBox checkModifyUsername = new JCheckBox(currentLanguage.get(STRING_CHANGE_USERNAME), true);
		JCheckBox checkModifyPassword = new JCheckBox(currentLanguage.get(STRING_CHANGE_PASSWORD), true);
		JCheckBox checkGenerateRandom = new JCheckBox(currentLanguage.get(STRING_GENERATE_RANDOM), false);

		JButton buttonConfirm = new JButton(iconConfirm32);
		JButton buttonChangeCharset = new JButton(iconSpanner32);
		JLabel labelChangeCharset = new JLabel("Charset");
		JLabel labelConfirm = new JLabel("Confirm");

		buttonConfirm.setBackground(colorFrame);
		buttonConfirm.setBorder(null);

		buttonChangeCharset.setBackground(colorFrame);
		buttonChangeCharset.setBorder(null);

		final int halfWidth = (windowWidth>>1);
		final int leftOffset = 40;

		final int labelFieldGap = 5;
		final int fieldNextLabelGap = 12;

		final int iconWest = (halfWidth - (iconConfirm32.getIconWidth()>>1));
		final int tfWest = (halfWidth - (tfWidth>>1));

		spring.putConstraint(SpringLayout.WEST, containerIconLocked, iconWest-10, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, containerIconLocked, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, labelEmail, 10, SpringLayout.WEST, tfEmail);
		spring.putConstraint(SpringLayout.NORTH, labelEmail, 30, SpringLayout.SOUTH, containerIconLocked);

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

		spring.putConstraint(SpringLayout.EAST, checkGenerateRandom, -10, SpringLayout.EAST, checkModifyPassword);
		spring.putConstraint(SpringLayout.SOUTH, checkGenerateRandom, -5, SpringLayout.NORTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, labelPasswordLen, 10, SpringLayout.WEST, tfPasswordLen);
		spring.putConstraint(SpringLayout.NORTH, labelPasswordLen, fieldNextLabelGap, SpringLayout.SOUTH, tfPassword);

		spring.putConstraint(SpringLayout.WEST, tfPasswordLen, tfWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, tfPasswordLen, labelFieldGap, SpringLayout.SOUTH, labelPasswordLen);

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, 20, SpringLayout.EAST, containerIconLocked);
		spring.putConstraint(SpringLayout.NORTH, buttonConfirm, 40, SpringLayout.SOUTH, tfPasswordLen);

		spring.putConstraint(SpringLayout.WEST, labelConfirm, 10, SpringLayout.EAST, buttonConfirm);
		spring.putConstraint(SpringLayout.NORTH, labelConfirm, 5, SpringLayout.NORTH, buttonConfirm);

		spring.putConstraint(SpringLayout.EAST, buttonChangeCharset, -10, SpringLayout.WEST, labelChangeCharset);
		spring.putConstraint(SpringLayout.NORTH, buttonChangeCharset, 40, SpringLayout.SOUTH, tfPasswordLen);

		spring.putConstraint(SpringLayout.EAST, labelChangeCharset, -20, SpringLayout.WEST, containerIconLocked);
		spring.putConstraint(SpringLayout.NORTH, labelChangeCharset, 5, SpringLayout.NORTH, buttonChangeCharset);

		contentPane.add(containerIconLocked);
		//contentPane.add(labelEmail);
		contentPane.add(tfEmail);
		contentPane.add(checkModifyEmail);
		//contentPane.add(labelUsername);
		contentPane.add(tfUsername);
		contentPane.add(checkModifyUsername);
		//contentPane.add(labelPassword);
		contentPane.add(tfPassword);
		contentPane.add(checkModifyPassword);
		contentPane.add(checkGenerateRandom);
		contentPane.add(buttonConfirm);

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
					checkGenerateRandom.setSelected(false);
					checkGenerateRandom.setEnabled(false);

					frame.revalidate();
					//frame.repaint();
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

				if (true == checkModifyEmail.isSelected())
				{
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
							showErrorDialog(currentLanguage.get(STRING_ERROR_INVALID_PASSWORD_LENGTH));
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
							showErrorDialog(currentLanguage.get(STRING_ERROR_INVALID_PASSWORD_LENGTH));
							return;
						}
					}
				}

				if (null != newEmail)
				{
					entry.setEmail(newEmail);
				}

				if (null != newUsername)
				{
					entry.setUsername(newUsername);
				}

				String oldHash = entry.getHash();

				if (null != newPassword)
				{
					entry.setPassword(newPassword);
					entry.setTimestamp(System.currentTimeMillis());
					entry.setHash(getUniqueId(entry));
				}

				putPasswordEntry(oldHash, entry); // replace old entry in HashMap
				putPasswordEntries(); // encrypt the data and write to password file
				revalidate();

				//if (null == newPassword)
				//{
				//	showPasswordDetails(entry);
				//}
				// XXX show changed password details

				frame.dispose();
			}
		});

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private void printPasswordEntries()
	{
		Set<HashMap.Entry<String,ArrayList<PasswordEntry>>> set = passwordEntries.entrySet();
		Iterator<HashMap.Entry<String,ArrayList<PasswordEntry>>> iter = set.iterator();
		Iterator<PasswordEntry> it = null;
		ArrayList<PasswordEntry> list = null;

		while (iter.hasNext())
		{
			HashMap.Entry<String,ArrayList<PasswordEntry>> e = iter.next();
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
		JTextArea taInfo = new JTextArea(currentLanguage.get(STRING_PROMPT_UNLOCK_PASSWORD_FILE));

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

		spring.putConstraint(SpringLayout.WEST, buttonConfirm, 25, SpringLayout.EAST, containerIcon);
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
					com.fasterxml.jackson.core.type.TypeReference typeRef = new com.fasterxml.jackson.core.type.TypeReference<HashMap<String,ArrayList<PasswordEntry>>>() {};

					passwordEntries = (HashMap<String,ArrayList<PasswordEntry>>)mapper.readValue(data, typeRef);
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
						showErrorDialog(currentLanguage.get(STRING_ERROR_TOO_MANY_ATTEMPTS));
						System.exit(1);
					}

					showErrorDialog(currentLanguage.get(STRING_ERROR_INCORRECT_PASSWORD));
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
		frame.setTitle("Remote Server Backups"); // currentLanguage.get(STRING_TITLE_BACKUP_PASSWORD_FILE);

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

				showInfoDialog(currentLanguage.get(STRING_PROMPT_CREATED_BACKUP_FILE));
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
		frame.setTitle("Backup Password File"); // currentLanguage.get(STRING_TITLE_BACKUP_PASSWORD_FILE);

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

				showInfoDialog(currentLanguage.get(STRING_PROMPT_CREATED_BACKUP_FILE));
			}
		});
	}

	public void setupGUI()
	{
		SpringLayout spring = new SpringLayout();

		Container contentPane = getContentPane();

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(MAIN_WINDOW_WIDTH, MAIN_WINDOW_HEIGHT);
		setTitle(currentLanguage.get(STRING_APPLICATION_NAME) + " v" + VERSION);

		contentPane.setBackground(colorFrame);

		JPanel panelButtons = new JPanel();
		JLabel unlockedContainer = new JLabel(iconShield128);

		JButton buttonAdd = new JButton(iconAdd32);
		JButton buttonView = new JButton(iconView32);
		JButton buttonSet = new JButton(iconCog32);
		JButton buttonUpload = new JButton(iconUpload32);
		JButton buttonDownload = new JButton(iconDownload32);

		buttonAdd.setBackground(colorButton);
		buttonView.setBackground(colorButton);
		buttonSet.setBackground(colorButton);
		buttonUpload.setBackground(colorButton);
		buttonDownload.setBackground(colorButton);

		final int nrButtons = 8;
		final int buttonsPerRow = 8;

		panelButtons.setLayout(new GridLayout(((nrButtons-1)/buttonsPerRow)+1, nrButtons));

		panelButtons.add(buttonAdd);
		panelButtons.add(buttonView);
		panelButtons.add(buttonSet);
		panelButtons.add(buttonUpload);
		panelButtons.add(buttonDownload);

// global var
		taAppName = new JTextArea(currentLanguage.get(STRING_APPLICATION_NAME));

		taAppName.setFont(new Font("Tahoma", Font.BOLD, 45));
		taAppName.setForeground(new Color(32, 32, 32));
		taAppName.setBackground(contentPane.getBackground());
		taAppName.setBorder(null);
		taAppName.setEditable(false);

		JPanel panelIds = new JPanel();

		panelIds.setLayout(new GridLayout(0, 1));

		final int nrColumns = 6;

		Object[] columnNames = {
			"Password ID",
			"Email",
			"Username",
			"Password",
			"Password Length",
			"Age (days)",
			"Unique ID"
		};

		model = new DefaultTableModel(columnNames, 0); // global var
		SimpleDateFormat dateFmt = new SimpleDateFormat(DATE_FORMAT);

		Set<HashMap.Entry<String,ArrayList<PasswordEntry>>> eSet = passwordEntries.entrySet();
		Iterator<HashMap.Entry<String,ArrayList<PasswordEntry>>> iter = eSet.iterator();
		Iterator<PasswordEntry> it = null;

		while (iter.hasNext())
		{
			HashMap.Entry<String,ArrayList<PasswordEntry>> entry = iter.next();
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

		for (int i = 0; i <= IDX_UNIQUE_ID; ++i)
			table.getColumnModel().getColumn(i).setMinWidth(150);
		//table.getColumnModel().getColumn(IDX_PASSWORD_ID).setMinWidth(100);
		//table.getColumnModel().getColumn(IDX_EMAIL).setMinWidth(200);
		//table.getColumnModel().getColumn(IDX_USERNAME).setMinWidth(100);
		//table.getColumnModel().getColumn(IDX_PASSWORD).setMinWidth(300);
		//table.getColumnModel().getColumn(IDX_PASSWORD_LENGTH).setMinWidth(150);
		//table.getColumnModel().getColumn(IDX_AGE).setMinWidth(150);
		//table.getColumnModel().getColumn(IDX_UNIQUE_ID).setMinWidth(200);

		JScrollPane scrollPane = new JScrollPane(table,
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
			JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

		//final int scrollPaneWidth = MAIN_WINDOW_WIDTH - 100;
		//final int scrollPaneHeight = 175;

		final int tableWidth = MAIN_WINDOW_WIDTH - 100;
		final int tableHeight = 175;
		final int scrollWest = ((MAIN_WINDOW_WIDTH - tableWidth)>>1);

		Dimension sizeTable = new Dimension(tableWidth, tableHeight);
		Dimension size = new Dimension(800, 200);

		table.setPreferredScrollableViewportSize(sizeTable);
		//table.setFillsViewportHeight(true);
		scrollPane.setPreferredSize(sizeTable);

		new ExcelAdapter(table); // so we can use CTRL+C in cells

		//table.setPreferredSize(size);
		//table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setFont(new Font("Courier New", Font.PLAIN, 18));
		//table.setForeground(new Color(0, 0, 153));
		table.setBackground(new Color(252, 252, 252));
		TableCellRenderer renderer = new PTableCellRenderer();
		table.setDefaultRenderer(Object.class, renderer);

		table
			.getTableHeader()
			.setFont(fontLabel);

		table.setCellSelectionEnabled(true);
		table.getSelectionModel()
			.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		table.getSelectionModel().addSelectionInterval(0,2);

		//scrollPane.getHorizontalScrollBar().setUI(new BasicScrollBarUI());
		//scrollPane.getVerticalScrollBar().setUI(new BasicScrollBarUI());

		final int VERTICAL_GAP = 50;
		final int VERTICAL_GAP_LABEL = 40;
		final int HORIZONTAL_GAP = 50;

		int iconWidth = iconUnlocked128.getIconWidth();
		int iconHeight = iconUnlocked128.getIconHeight();

		int halfWidth = (MAIN_WINDOW_WIDTH>>1);

		spring.putConstraint(SpringLayout.WEST, unlockedContainer, 150, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, unlockedContainer, 40, SpringLayout.NORTH, contentPane);

		spring.putConstraint(SpringLayout.WEST, taAppName, 20, SpringLayout.EAST, unlockedContainer);
		spring.putConstraint(SpringLayout.NORTH, taAppName, (iconHeight>>1)-20, SpringLayout.NORTH, unlockedContainer);

		spring.putConstraint(SpringLayout.WEST, scrollPane, scrollWest, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, scrollPane, VERTICAL_GAP+20, SpringLayout.SOUTH, unlockedContainer);

		spring.putConstraint(SpringLayout.WEST, panelButtons, HORIZONTAL_GAP+60, SpringLayout.WEST, contentPane);
		spring.putConstraint(SpringLayout.NORTH, panelButtons, 40, SpringLayout.SOUTH, scrollPane);

		contentPane.setLayout(spring);

		contentPane.add(unlockedContainer);
		contentPane.add(taAppName);
		contentPane.add(scrollPane);
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
			/*
			 * Everything needs to be done in doAddNewPassword(), because
			 * actionPerformed() just returns straight away despite the
			 * fact we still have an open window for creating the new
			 * password.
			 */
				doAddNewPassword(model);
			}
		});

		buttonView.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event)
			{
				return;
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

		table
			.getSelectionModel()
			.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent event)
			{
				if (true == event.getValueIsAdjusting())
					return;

				int[] selectedRows = table.getSelectedRows();

				if (selectedRows.length > 0)
				{
					//showInfoDialog((String)table.getValueAt(selectedRows[0], IDX_PASSWORD));
					currentlySelectedRow = selectedRows[0];
				}
			}
		});

/*
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

					fObj.createNewFile();
					String configLanguage = "<language>\n" + currentlySelectedLanguage.getText() + "\n</language>";

					fOut.write(configLanguage.getBytes());
					fOut.flush();
					fOut.close();
				}
				catch (Exception e)
				{
					e.printStackTrace();
					System.exit(1);
				}

				currentLanguage = languageStrings.get(currentlySelectedLanguage.getText());

			/*
			 * Default to English in the event of a problem.
			 */
				if (null == currentLanguage)
					currentLanguage = languageEnglish;

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
				BufferedReader bufRead = new BufferedReader(new FileReader(configFile));
				String line = null;
				Pattern p = Pattern.compile(".*<language>.*");
				Matcher m = null;

				while ((line = bufRead.readLine()) != null)
				{
					m = p.matcher(line);
					if (true == m.matches())
					{
						line = bufRead.readLine();
						break;
					}
				}

				if (null == line)
				{
					showInfoDialog("No language configuration settings found");
					doLanguageConfiguration();
					return;
				}

				line = removeTrailingNewlines(line);

				currentLanguage = languageStrings.get(line);
				//showInfoDialog("LANGUAGE: " + line);

				if (null == currentLanguage)
				{
					showErrorDialog("No language object for \"" + line + "\" - defaulting to English");
					currentLanguage = languageEnglish;
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.exit(1);
			}

			//setupGUI();
			unlockPasswordFile();
		}
	}
/*
 * Private classes
 */

	private class RightClickPopup extends JPanel
	{
		private JPopupMenu menu = null;

		public RightClickPopup()
		{
			menu = new JPopupMenu();
			Action copy = new DefaultEditorKit.CopyAction();
			Action paste = new DefaultEditorKit.PasteAction();

			if (null == currentLanguage) // default to English
			{
				copy.putValue(Action.NAME, "Copy");
				paste.putValue(Action.NAME, "Paste");
			}
			else
			{
				copy.putValue(Action.NAME, currentLanguage.get(STRING_COPY));
				paste.putValue(Action.NAME, currentLanguage.get(STRING_PASTE));
			}

			copy.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control C"));
			paste.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control V"));

			menu.add(copy);
			menu.add(paste);
		}

		public JPopupMenu getMenu()
		{
			return menu;
		}
	}

	public PasswordManager()
	{
		mapper = new ObjectMapper();

		languageStrings.put("English", languageEnglish);
		languageStrings.put("Français", languageFrench);
		languageStrings.put("한국어", languageKorean);
		languageStrings.put("Bahasa Melayu", languageMalaysian);

	/*
	 * Languages of which the characters
	 * require greater than one byte of memory.
	 */
		specialLanguageList = new ArrayList<Map<Integer,String> >();
		specialLanguageList.add(languageKorean);

		characterStatusMap = new boolean[(int)(0x7e - 0x21)];
		fillCharacterStatusMap();
		fillCharacterSet();

		userHome = System.getProperty("user.home");
		userName = System.getProperty("user.name");
		passwordDirectory = userHome + "/" + dirName;
		passwordFile = passwordDirectory + "/" + userName;
		configFile = passwordDirectory + "/" + userName + ".config";

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
