package backup;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.security.GeneralSecurityException;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class BackedFile
{
	@JsonProperty
	private String name;
	@JsonProperty
	private String id;
	@JsonProperty
	private long when;

	public void setName(String n) { this.name = n; }
	public void setId(String i) { this.id = i; }
	public void setWhen(long w) { this.when = w; }

	public String getName() { return this.name; }
	public String getId() { return this.id; }
	public long getWhen() { return this.when; }
}

public class GDriveBackup
{
	private static final String APPLICATION_NAME = "Password Manager";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static final String TOKENS_DIRECTORY_PATH = "tokens";

	// "View and manage Google Drive files and folders that you have opened or created with this app"
	private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
	private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
	private static final String BACKEDUP_FILE_PATH = "src/main/resources/backup.json";

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
	private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
		throws IOException
	{
		// Load client secrets.
		InputStream in = GDriveBackup.class.getResourceAsStream(CREDENTIALS_FILE_PATH);

		if (in == null)
		{
			throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
		}

		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
			HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
			.setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
			.setAccessType("offline")
			.build();

		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
		return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
	}

	private String removePathPart(String path)
		throws UnsupportedEncodingException
	{
		byte[] rawPath = path.getBytes("UTF-8");
		int pos = rawPath.length - 1;

		while (rawPath[pos] != (byte)'/' && pos > 1)
			--pos;

		++pos;

		byte[] rawName = new byte[rawPath.length - pos];
		System.arraycopy(rawPath, pos, rawName, 0, rawPath.length - pos);

		return new String(rawName);
	}

	/**
	 * Use the Drive.Files.Create class
	 * to create a new file on drive.
	 */
	public void doFileBackup(String path)
		throws IOException, GeneralSecurityException
	{
		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

		Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
			.setApplicationName(APPLICATION_NAME)
			.build();

		com.google.api.services.drive.model.File fMeta = new com.google.api.services.drive.model.File();
		java.io.File fObj = new java.io.File(path);
		FileContent content = new FileContent("application/octet-stream", fObj);
		String name = null;
		final ObjectMapper mapper = new ObjectMapper();
		java.io.File fileBackup = new java.io.File(BACKEDUP_FILE_PATH);
		BackedFile bFile = null;
		String gFileId = null;

		if (fileBackup.exists())
		{
			byte[] data = Files.readAllBytes(Paths.get(BACKEDUP_FILE_PATH));
			bFile = mapper.readValue(data, BackedFile.class);

			gFileId = bFile.getId();
		}
		else
		{
			bFile = new BackedFile();
		}

		try
		{
			name = removePathPart(path);
			name += "_passwordfile_backup_" + System.currentTimeMillis();
		}
		catch (UnsupportedEncodingException e)
		{
			System.err.println("UnsupportedCodingException while getting name part of path");
			e.printStackTrace();
		}

		fMeta.setName(name);
		//fMeta.setOwnedByMe(true); // << This was causing a 403 Forbidden error (field not writeable)

		/*
		 * If we already have a backup, use the files.update method.
		 */
		if (null != gFileId)
		{
			service
				.files()
				.update(gFileId, fMeta, content)
				.execute();

			System.out.println("Updated backup file on GDrive with ID " + gFileId);

			bFile.setWhen(System.currentTimeMillis());
		}
		else
		{
			com.google.api.services.drive.model.File gFile = service
				.files()
				.create(fMeta, content)
				.execute();

			System.out.println(
				"Created new file on GDrive with ID " + gFile.getId());

			bFile.setName(name);
			bFile.setId(gFile.getId());
			bFile.setWhen(System.currentTimeMillis());
		}

		mapper.writeValue(new java.io.File(BACKEDUP_FILE_PATH), bFile);

		return;	
	}

	public int doDownloadBackup(String path)
		throws IOException, GeneralSecurityException
	{
		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
			.setApplicationName(APPLICATION_NAME)
			.build();

		ObjectMapper mapper = new ObjectMapper();
		java.io.File fileBackup = new java.io.File(BACKEDUP_FILE_PATH);
		BackedFile bFile = null;
		OutputStream out = new ByteArrayOutputStream();

		if (!fileBackup.exists())
			return -1;

		byte[] data = Files.readAllBytes(Paths.get(BACKEDUP_FILE_PATH));
		bFile = mapper.readValue(data, BackedFile.class);

		service
			.files()
			.get(bFile.getId())
			.executeMediaAndDownloadTo(out);

		((ByteArrayOutputStream)out).writeTo(new FileOutputStream(new java.io.File(path)));

		return 0;
	}
}
