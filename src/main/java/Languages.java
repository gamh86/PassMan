package languages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

public class Languages
{
	public static final int STRING_APPLICATION_NAME = 0;
	public static final int STRING_PASSWORD_ID = 1;
	public static final int STRING_USERNAME = 2;
	public static final int STRING_PASSWORD = 3;
	public static final int STRING_PASSWORD_LENGTH = 4;
	public static final int STRING_MASTER_PASSWORD = 5;
	public static final int STRING_UNLOCK_PASSWORD_FILE = 6;
	public static final int STRING_CREATION_TIME = 7;
	public static final int STRING_COPY_PASSWORD = 8;
	public static final int STRING_CONFIGURATION_PROMPT = 9;
	public static final int STRING_CHANGE_SETTINGS = 10;
	public static final int STRING_CONFIRM_PASSWORD = 11;
	public static final int STRING_CURRENT_PASSWORD = 12;
	public static final int STRING_NEW_PASSWORD = 13;
	public static final int STRING_CONFIRM_NEW_PASSWORD = 14;

	public static final int STRING_TITLE_PASSWORD_DETAILS = 15;
	public static final int STRING_TITLE_PASSWORD_MANAGER_CONFIGURATION = 16;

	public static final int STRING_PROMPT_DETAILS_CHANGED = 17;
	public static final int STRING_PROMPT_PASSWORD_COPIED = 18;
	public static final int STRING_PROMPT_PASSWORD_CREATED = 19;
	public static final int STRING_PROMPT_PASSWORD_WILL_BE_CHANGED = 20;
	public static final int STRING_PROMPT_PASSWORD_WILL_BE_REMOVED = 21;
	public static final int STRING_PROMPT_PASSWORD_REMOVED = 22;
	public static final int STRING_PROMPT_MASTER_PASSWORD_CHANGED = 23;
	public static final int STRING_PROMPT_NO_PASSWORDS = 24;
	public static final int STRING_PROMPT_PASSWORD_FILE_CREATED = 25;

	public static final int STRING_ERROR_PASSWORD_ID = 26;
	public static final int STRING_ERROR_PASSWORD_ID_EXISTS = 27;
	public static final int STRING_ERROR_INVALID_PASSWORD_LENGTH = 28;
	public static final int STRING_ERROR_INCORRECT_PASSWORD = 29;
	public static final int STRING_ERROR_PASSWORDS_DO_NOT_MATCH = 30;
	public static final int STRING_ERROR_NO_ENTRY = 31;
	public static final int STRING_ERROR_SELECT_PASSWORD_ID = 32;
	public static final int STRING_ERROR_PASSWORD_NOT_CHANGED = 33;

	public static final int STRING_CHANGE_MASTER_PASSWORD = 34;
	public static final int STRING_PROMPT_OK = 35;
	public static final int STRING_PROMPT_CANCEL = 36;
	public static final int STRING_LANGUAGE = 37;
	public static final int STRING_ERROR_SELECT_LANGUAGE = 38;
	public static final int STRING_PROMPT_CHANGED_LANGUAGE = 39;
	public static final int STRING_PASSWORD_ID_LIST = 40;
	public static final int STRING_POSSIBLE_PERMUTATIONS = 41;
	public static final int STRING_COPY = 42;
	public static final int STRING_PASSWORD_LENGTH2 = 43;
	public static final int STRING_CRACK_TIME = 44;
	public static final int STRING_SECONDS = 45;
	public static final int STRING_DAYS = 46;
	public static final int STRING_YEARS = 47;
	public static final int STRING_PASSWORD_STRENGTH_INFORMATION = 48;
	public static final int STRING_TITLE_PASSWORD_ANALYSIS = 49;

	public static final int STRING_ERROR_TOO_MANY_ATTEMPTS = 50;
	public static final int STRING_PROMPT_UNLOCK_PASSWORD_FILE = 51;
	public static final int STRING_LEAVE_BLANK = 52;
	public static final int STRING_CHARACTER_SET = 53;
	public static final int STRING_TOGGLE_CHARACTER_SET = 54;
	public static final int STRING_GENERATE_RANDOM = 55;
	public static final int STRING_SIZE_CHARACTER_SET = 56;
	public static final int STRING_CHANGE_ID = 57;
	public static final int STRING_CHANGE_USERNAME = 58;
	public static final int STRING_CHANGE_PASSWORD = 59;
	public static final int STRING_PASTE = 60;
	public static final int STRING_OLD_PASSWORD = 61;

	public static final int STRING_CREATE_BACKUP_FILE = 62;
	public static final int STRING_PROMPT_CREATED_BACKUP_FILE = 63;
	public static final int STRING_CHANGE_EMAIL = 64;

/*
 * Unmodifiable maps mapping constants
 * below to relevant String objects.
 */
	private static final Map<Integer,String> languageKorean = getLanguageStringsKorean();
	private static final Map<Integer,String> languageEnglish = getLanguageStringsEnglish();
	private static final Map<Integer,String> languageMalaysian = getLanguageStringsMalaysian();
	private static final Map<Integer,String> languageFrench = getLanguageStringsFrench();

	public static final int KOREAN = 0;
	public static final int ENGLISH = 1;
	public static final int MALAYSIAN = 2;
	public static final int FRENCH = 3;

	private ArrayList<String> languageValues = null;
	private ArrayList<Map<Integer,String> > _languages_;

	public Languages()
	{
		_languages_ = new ArrayList<Map<Integer,String> >();

		_languages_.add(languageKorean);
		_languages_.add(languageEnglish);
		_languages_.add(languageMalaysian);
		_languages_.add(languageFrench);

		languageValues = new ArrayList<>();
	/*
	 * Can then use .indexOf(<language string>) to get the correct index.
	 */
		languageValues.add("한국어");
		languageValues.add("English");
		languageValues.add("Bahasa Melayu");
		languageValues.add("Français");
	}

	private static Map<Integer,String> getLanguageStringsKorean()
	{
		Map<Integer,String> map = new TreeMap<Integer,String>();

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
		map.put(STRING_CHANGE_EMAIL, "이메일 변경");
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
		Map<Integer,String> map = new TreeMap<Integer,String>();

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
		map.put(STRING_CHANGE_EMAIL, "Modifier Email");
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
		Map<Integer,String> map = new TreeMap<Integer,String>();

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
		map.put(STRING_CHANGE_EMAIL, "Modify Email");
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
		Map<Integer,String> map = new TreeMap<Integer,String>();

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
		map.put(STRING_CHANGE_EMAIL, "Ubah Email");
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

	public Map<Integer,String> getLanguage(int lang)
	{
		Map<Integer,String> l = _languages_.get(lang);

		if (null == l)
			return _languages_.get(ENGLISH);
		else
			return l;
	}

	/**
	 * Get a language map from string (e.g., "English")
	 */
	public Map<Integer,String> getLanguageFromString(String s)
	{
		return getLanguage(languageValues.indexOf(s));
	}
}
