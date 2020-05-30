package aescrypt;

import java.io.File;
import java.io.FileReader; /* for reading streams of characters */
import java.io.FileInputStream; /* for reading raw bytes (binary data) */
import java.io.FileOutputStream; /* for writing raw bytes to file */
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.BufferedReader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import java.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;

import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import javax.crypto.BadPaddingException;

public class AESCrypt
{
	private static SecretKeySpec secretKey;
	private static byte[] rawKey;

	private static final int AES_KEY_SIZE = 32;
	private static final int AES_KEY_BITS = (AES_KEY_SIZE * 8);
	private static final int IV_LENGTH = 16;
	private static final int SALT_LENGTH = 8;
	private static final int PBKDF2_ITERATIONS = 1000;
	private static final String CRYPTO_TRANSFORMATION = "AES/CBC/PKCS5Padding";
	private static final String CRYPTO_ALGORITHM = "AES";

	public AESCrypt() { }

	private String parseDirectory(String path)
	{
		int pos = path.length();

		try
		{
			byte[] rawPath = path.getBytes("UTF-8");
			pos = rawPath.length - 1;

			while (rawPath[pos] != (byte)'/' && pos > 1)
				--pos;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		return path.substring(0, pos);
	}

	/**
	 * Get SALT_LENGTH pseudo-random bytes
	 */
	private byte[] generateSalt()
	{
		byte[] salt = new byte[SALT_LENGTH];
		new SecureRandom().nextBytes(salt);
		return salt;
	}

	/**
	 * Get IV_LENGTH pseudo-random bytes
	 */
	private byte[] generateIV()
	{
		byte[] iv = new byte[IV_LENGTH];
		new SecureRandom().nextBytes(iv);
		return iv;
	}

	/**
	 * Encrypt the password file
	 * @param path the path to the password file
	 * @param key the password file owner's passphrase
	 */

	public byte[] encryptFile(String path, String key)
	{
		String fContents = "";
		byte[] encrypted = null;
		byte[] iv = null;
		byte[] salt = null;
		byte[] outData = null;

		try
		{
			BufferedReader bf = new BufferedReader(new FileReader(path));
			String line = null;

			while ((line = bf.readLine()) != null)
			{
				fContents += line + "\n";
			}

			System.out.println(fContents);
			bf.close();

			salt = generateSalt();

			char[] rawKey = key.toCharArray();

			Base64.Encoder base = Base64.getEncoder();

			PBEKeySpec pbe = new PBEKeySpec(rawKey, salt, PBKDF2_ITERATIONS, AES_KEY_BITS);
			SecretKeyFactory kFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			byte[] rawDerivedKey = kFactory.generateSecret(pbe).getEncoded();

			iv = generateIV();

			Cipher cipher = Cipher.getInstance(CRYPTO_TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(rawDerivedKey, CRYPTO_ALGORITHM), new IvParameterSpec(iv));
			byte[] result = cipher.doFinal(fContents.getBytes("UTF-8"));

			outData = new byte[result.length + iv.length + SALT_LENGTH + 4];

			ByteBuffer byteBuf = ByteBuffer.allocate(4);
			byteBuf.putInt(PBKDF2_ITERATIONS);

			final int offsetIters = 0;
			final int offsetIv = 4;
			final int offsetSalt = 20;
			final int offsetData = 28;

			System.arraycopy(byteBuf.array(), 0, outData, offsetIters, 4);
			System.arraycopy(iv, 0, outData, offsetIv, IV_LENGTH);
			System.arraycopy(salt, 0, outData, offsetSalt, SALT_LENGTH);
			System.arraycopy(result, 0, outData, offsetData, result.length);

			File fObj = new File(path);
			FileOutputStream fOut = new FileOutputStream(fObj, false);
			byte[] zeros = new byte[(int)fObj.length()];

			int pos = 0;
			while (pos < fObj.length())
				zeros[pos++] = (byte)0;

			fOut.write(zeros);
			fOut.flush();
			fOut.close();

			fOut = new FileOutputStream(fObj, false);
			fOut.write(outData);
			fOut.flush();
			fOut.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		return outData;
	}

	public byte[] encryptData(String data, String key)
	{
		byte[] outData = null;

		try
		{
			byte[] salt = generateSalt();
			char[] rawKey = key.toCharArray();
			byte[] iv = null;

			Base64.Encoder base = Base64.getEncoder();

			PBEKeySpec pbe = new PBEKeySpec(rawKey, salt, PBKDF2_ITERATIONS, AES_KEY_BITS);
			SecretKeyFactory kFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			byte[] rawDerivedKey = kFactory.generateSecret(pbe).getEncoded();

			iv = generateIV();

			Cipher cipher = Cipher.getInstance(CRYPTO_TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(rawDerivedKey, CRYPTO_ALGORITHM), new IvParameterSpec(iv));
			byte[] result = cipher.doFinal(data.getBytes("UTF-8"));

			outData = new byte[result.length + IV_LENGTH + SALT_LENGTH + 4];

			ByteBuffer byteBuf = ByteBuffer.allocate(4);
			byteBuf.putInt(PBKDF2_ITERATIONS);

			final int offsetIters = 0;
			final int offsetIv = 4;
			final int offsetSalt = 20;
			final int offsetData = 28;

			System.arraycopy(byteBuf.array(), 0, outData, offsetIters, 4);
			System.arraycopy(iv, 0, outData, offsetIv, IV_LENGTH);
			System.arraycopy(salt, 0, outData, offsetSalt, SALT_LENGTH);
			System.arraycopy(result, 0, outData, offsetData, result.length);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		return outData;
	}

	public String decryptFile(String path, String key)
	{
		String contents = null;

		try
		{
			byte[] iv = new byte[IV_LENGTH];
			byte[] salt = new byte[SALT_LENGTH];
			char[] rawKey = key.toCharArray();
			byte[] data = null;
			byte[] iters = new byte[4];
			File fObj = new File(path);
			FileInputStream fIn = new FileInputStream(fObj);
			int pbkdf2iterations = 0;

			byte[] enc = new byte[(int)fObj.length()];

/*
 * File length minus (IV length (16) + salt length (8) + size of int (4))
 */
			data = new byte[enc.length - (16 + SALT_LENGTH + 4)];

			fIn.read(enc);
			fIn.close();

			final int offsetIters = 0;
			final int offsetIv = 4;
			final int offsetSalt = 20;
			final int offsetData = 28;

			System.arraycopy(enc, offsetIters, iters, 0, 4);
			System.arraycopy(enc, offsetIv, iv, 0, IV_LENGTH);
			System.arraycopy(enc, offsetSalt, salt, 0, SALT_LENGTH);
			System.arraycopy(enc, offsetData, data, 0, enc.length - (16 + SALT_LENGTH + 4));

			ByteBuffer byteBuf = ByteBuffer.wrap(iters);
			pbkdf2iterations = byteBuf.getInt(0);

/*
 * Always use the number of iterations
 * for PBKDF2 that was encoded in the
 * password file.
 */
			PBEKeySpec pbe = new PBEKeySpec(rawKey, salt, pbkdf2iterations, AES_KEY_BITS);
			SecretKeyFactory kFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			byte[] rawDerivedKey = kFactory.generateSecret(pbe).getEncoded();

			Cipher cipher = Cipher.getInstance(CRYPTO_TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(rawDerivedKey, CRYPTO_ALGORITHM), new IvParameterSpec(iv));

			byte[] decrypted = cipher.doFinal(data);

			contents = new String(decrypted, Charset.forName("UTF-8"));
		}
		catch (BadPaddingException e)
		{
			return null;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		return contents;
	}
}
