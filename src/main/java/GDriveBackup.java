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

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.security.GeneralSecurityException;

import java.util.Collections;
import java.util.List;

public class GDriveBackup
{
	private static final String APPLICATION_NAME = "Password Manager";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static final String TOKENS_DIRECTORY_PATH = "tokens";

	// "View and manage Google Drive files and folders that you have opened or created with this app"
	private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
	private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

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

		try
		{
			name = removePathPart(path);
			name += "_passwordfile_backup";
		}
		catch (UnsupportedEncodingException e)
		{
			System.err.println("UnsupportedCodingException while getting name part of path");
			e.printStackTrace();
		}

		fMeta.setName(name);
		//fMeta.setOwnedByMe(true); // << This was causing a 403 Forbidden error (field not writeable)

		com.google.api.services.drive.model.File gFile = service.files().create(fMeta, content)
			.execute();

		System.out.println("Created file on GDrive: " + gFile.getId());

		return;	
	}
}
