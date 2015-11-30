package org.ledain.ood.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.ledain.ood.AppConfig;
import org.ledain.ood.adblib.AdbBase64;
import org.ledain.ood.adblib.AdbConnection;
import org.ledain.ood.adblib.AdbCrypto;
import org.ledain.ood.adblib.AdbStream;

public class ToolsAdb
{
	private static final String	TAG				= "ToolsAdb - ";

	private static boolean		_isActive;
	private static Thread		_threadRecorder	= null;

	// This implements the AdbBase64 interface required for AdbCrypto
	public static AdbBase64 getBase64Impl()
	{
		return new AdbBase64()
		{
			@Override
			public String encodeToString(byte[] arg0)
			{
				return Base64.encodeToString(arg0, Base64.DEFAULT);
			}
		};
	}

	// This function loads a keypair from the specified files if one exists, and if not,
	// it creates a new keypair and saves it in the specified files
	private static AdbCrypto setupCrypto(String pubKeyFile, String privKeyFile) throws NoSuchAlgorithmException,
					InvalidKeySpecException, IOException
	{
		File pub = new File(pubKeyFile);
		File priv = new File(privKeyFile);
		AdbCrypto c = null;

		// Try to load a key pair from the files 
		if(pub.exists() && priv.exists())
		{
			try
			{
				c = AdbCrypto.loadAdbKeyPair(getBase64Impl(), priv, pub);
			}
			catch(IOException e)
			{
				// Failed to read from file
				c = null;
			}
			catch(InvalidKeySpecException e)
			{
				// Key spec was invalid
				c = null;
			}
			catch(NoSuchAlgorithmException e)
			{
				// RSA algorithm was unsupported with the crypo packages available
				c = null;
			}
		}

		if(c == null)
		{
			// We couldn't load a key, so let's generate a new one
			c = AdbCrypto.generateAdbKeyPair(getBase64Impl());

			// Save it
			c.saveAdbKeyPair(priv, pub);
			System.out.println("Generated new keypair");
		}
		else
		{
			System.out.println("Loaded existing keypair");
		}

		return c;
	}

	public static File logcatFile(Context a_Context)
	{
		return new File(a_Context.getCacheDir() + File.separator + "logcat");
	}

	public static File screenshotDir(Context a_Context)
	{
		return new File(a_Context.getCacheDir() + File.separator + "screenshot" + File.separator);
	}

	private static String screenshotTmpFile(Context a_Context)
	{
		return "/storage/sdcard0/ood.png";
		//return Environment.getExternalStorageDirectory().getPath() + File.separator + "ood.png";
		//return "/sdcard1/ood.png";
		//return a_Context.getFilesDir().getPath() + File.separator + "ood.png";
	}

	public static boolean deleteScreenshot(Context a_Context, String a_name)
	{
		Log.i(AppConfig.TAG_APP, TAG + "deleteScreenshot, name=" + a_name);
		return new File(screenshotDir(a_Context), a_name).delete();
	}

	private static void keepScreenshot(Context a_Context, File a_img)
	{
		int i = 0;
		File dir = screenshotDir(a_Context);
		if(!dir.exists())
		{
			dir.mkdirs();
		}
		File img;
		do
		{
			img = new File(dir, "screenshot-" + (++i) + ".png");
		} while(img.exists());

		if(!a_img.renameTo(img))
		{
			Log.w(AppConfig.TAG_APP, TAG + "keepScreenshot: failed to rename " + a_img + " to " + img + ". Try stream.");
			try
			{
				FileOutputStream out = new FileOutputStream(img);
				IoUtils.copy(new FileInputStream(a_img), out);
				out.close();
				Log.i(AppConfig.TAG_APP, TAG + "keepScreenshot: finally copied!");
			}
			catch(Exception e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "keepScreenshot: failed to stream " + a_img + " to " + img);
			}
		}
		if(a_img.exists())
		{
			if(!a_img.delete())
			{
				Log.e(AppConfig.TAG_APP, TAG + "keepScreenshot: failed to delete file " + a_img);
			}
		}
	}

	public static List<String> listScreenshots(Context a_Context) throws IOException
	{
		Log.i(AppConfig.TAG_APP, TAG + "listScreenshots");
		List<String> list = new ArrayList<String>();

		File dir = screenshotDir(a_Context);
		if(dir.exists())
		{
			List<File> entries = Arrays.asList(dir.listFiles());
			Collections.sort(entries, new Comparator<File>()
			{
				@Override
				public int compare(File lhs, File rhs)
				{
					long time1 = lhs.lastModified();
					long time2 = rhs.lastModified();

					return time1 == time2 ? 0 : (time1 < time2 ? -1 : 1);
				}
			});

			for (File entry : entries)
			{
				list.add(entry.getName());
			}
		}

		return list;
	}

	public static void listScreenshots(Context a_Context, OutputStream a_outstream, int a_code) throws IOException
	{
		Log.i(AppConfig.TAG_APP, TAG + "listScreenshots");

		a_outstream.write((" { \"code\": " + a_code + ", \"list\": [ ").getBytes());
		File dir = screenshotDir(a_Context);
		if(dir.exists())
		{
			List<File> entries = Arrays.asList(dir.listFiles());
			Collections.sort(entries, new Comparator<File>()
			{
				@Override
				public int compare(File lhs, File rhs)
				{
					long time1 = lhs.lastModified();
					long time2 = rhs.lastModified();

					return time1 == time2 ? 0 : (time1 < time2 ? -1 : 1);
				}
			});

			boolean bFirstEntry = true;
			for (File entry : entries)
			{
				a_outstream.write(((bFirstEntry ? "" : ",") + "{ \"name\":\"" + entry.getName() + "\", \"date\":\""
								+ (new Date(entry.lastModified())).toString() + "\" }").getBytes());
				bFirstEntry = false;
			}
		}
		a_outstream.write("] }".getBytes());
	}

	public static void deleteScreenshots(Context a_Context, OutputStream a_outstream, final Map<String, Object> a_params)
					throws IOException
	{
		Log.i(AppConfig.TAG_APP, TAG + "listScreenshots");

		int code = 0;
		if(a_params != null)
		{
			List<Object> ids = (List<Object>) a_params.get("list");
			if(ids != null && ids.size() > 0)
			{
				for (Object id : ids)
				{
					if(id != null)
					{
						if(!deleteScreenshot(a_Context, id.toString()))
						{
							code += 1;
						}
					}
				}
			}
		}
		listScreenshots(a_Context, a_outstream, code);
	}

	public static void readScreenshot(Context a_Context, String a_name, OutputStream a_outstream) throws IOException
	{
		Log.i(AppConfig.TAG_APP, TAG + "readScreenshot, name=" + a_name);

		File f = new File(screenshotDir(a_Context), a_name);
		if(f.exists())
		{
			IoUtils.copy(new FileInputStream(f), a_outstream);
		}
		a_outstream.close();
	}

	public static boolean readLogs(Context a_Context, boolean a_bStop) throws IOException
	{
		AdbConnection adb = null;
		Socket sock = null;
		AdbCrypto crypto = null;

		String command = "logcat " + (a_bStop ? "-d" : "") + " -v threadtime";

		// Setup the crypto object required for the AdbConnection
		try
		{
			crypto = setupCrypto(a_Context.getCacheDir() + File.separator + "pub.key", a_Context.getCacheDir() + File.separator
							+ "priv.key");
		}
		catch(NoSuchAlgorithmException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB setup crypto failed.", e);
			return false;
		}
		catch(InvalidKeySpecException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB setup crypto failed.", e);
			return false;
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB setup crypto failed.", e);
			return false;
		}

		// Connect the socket to the remote host
		try
		{
			//sock = new Socket("192.168.1.137", 5555);
			sock = new Socket("127.0.0.1", AppConfig.ADB_TCPIP_PORT);
		}
		catch(UnknownHostException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB socket failed.", e);
			return false;
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB socket failed.", e);
			return false;
		}

		// Construct the AdbConnection object
		try
		{
			adb = AdbConnection.create(sock, crypto);
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB create failed.<br/>", e);
			return false;
		}

		AdbStream shellstream = null;
		// Start the application layer connection process
		try
		{
			adb.connect();

			// Open the shell stream of ADB
			try
			{
				shellstream = adb.open("shell:");
			}
			catch(UnsupportedEncodingException e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "ADB open failed.<br/>", e);
				return false;
			}
			catch(IOException e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "ADB open failed.<br/>", e);
				return false;
			}
			catch(InterruptedException e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "ADB open failed.<br/>", e);
				return false;
			}
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB connect failed.<br/>", e);
			return false;
		}
		catch(InterruptedException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB connect failed.<br/>", e);
			return false;
		}

		final OutputStream outstream = new FileOutputStream(logcatFile(a_Context));
		try
		{
			final AdbStream stream = shellstream;
			final Object lock = new Object();
			_isActive = false;

			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					while(!stream.isClosed())
					{
						try
						{
							// Print each thing we read from the shell stream
							outstream.write(stream.read());
							_isActive = true;
							synchronized(lock)
							{
								lock.notify();
							}
						}
						catch(UnsupportedEncodingException e)
						{
							e.printStackTrace();
							return;
						}
						catch(InterruptedException e)
						{
							e.printStackTrace();
							return;
						}
						catch(IOException e)
						{
							e.printStackTrace();
							return;
						}
					}
				}
			}).start();

			try
			{
				stream.write(command + "\n");
				_isActive = true;
				while(_isActive)
				{
					_isActive = false;
					synchronized(lock)
					{
						lock.wait(2000);
					}
				}
				stream.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		finally
		{
			outstream.close();
		}

		return true;
	}

	public static final void toggleRecording(final Context a_Context, OutputStream a_outstream) throws IOException
	{
		if(_threadRecorder == null)
		{
			_threadRecorder = new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						readLogs(a_Context, false);
					}
					catch(IOException e)
					{
					}
				}
			});
			_threadRecorder.start();
		}
		else
		{
			_threadRecorder.interrupt();
			_threadRecorder = null;
		}

		refreshRecording(a_Context, a_outstream);
	}

	public static final void refreshRecording(final Context a_Context, OutputStream a_outstream) throws IOException
	{
		a_outstream.write(("{ \"code\": 0, \"recording\":" + (_threadRecorder != null) + ", \"size\":"
						+ logcatFile(a_Context).length() + " }").getBytes());
		a_outstream.close();
	}

	public static final void clearLogs(Context a_Context)
	{
		logcatFile(a_Context).delete();
	}

	public static void screenshot(final Context a_Context, final OutputStream a_outstream) throws IOException
	{
		int code = screenshot(a_Context) ? 0 : 1;
		listScreenshots(a_Context, a_outstream, code);
	}

	public static boolean screenshot(final Context a_Context) throws IOException
	{

		AdbConnection adb = null;
		Socket sock = null;
		AdbCrypto crypto = null;

		new File(screenshotTmpFile(a_Context)).delete();

		String command = "screencap " + screenshotTmpFile(a_Context);

		// Setup the crypto object required for the AdbConnection
		try
		{
			crypto = setupCrypto(a_Context.getCacheDir() + File.separator + "pub.key", a_Context.getCacheDir() + File.separator
							+ "priv.key");
		}
		catch(NoSuchAlgorithmException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB setup crypto failed.", e);
			return false;
		}
		catch(InvalidKeySpecException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB setup crypto failed.", e);
			return false;
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB setup crypto failed.", e);
			return false;
		}

		// Connect the socket to the remote host
		try
		{
			//sock = new Socket("192.168.1.137", 5555);
			sock = new Socket("127.0.0.1", AppConfig.ADB_TCPIP_PORT);
		}
		catch(UnknownHostException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB socket failed.", e);
			return false;
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB socket failed.", e);
			return false;
		}

		// Construct the AdbConnection object
		try
		{
			adb = AdbConnection.create(sock, crypto);
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB create failed.<br/>", e);
			return false;
		}

		AdbStream shellstream = null;
		// Start the application layer connection process
		try
		{
			adb.connect();

			// Open the shell stream of ADB
			try
			{
				shellstream = adb.open("shell:");
			}
			catch(UnsupportedEncodingException e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "ADB open failed.<br/>", e);
				return false;
			}
			catch(IOException e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "ADB open failed.<br/>", e);
				return false;
			}
			catch(InterruptedException e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "ADB open failed.<br/>", e);
				return false;
			}
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB connect failed.<br/>", e);
			return false;
		}
		catch(InterruptedException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB connect failed.<br/>", e);
			return false;
		}

		try
		{
			final AdbStream stream = shellstream;
			final Object lock = new Object();
			_isActive = false;

			File img = new File(screenshotTmpFile(a_Context));
			try
			{
				long oldlen = 0;
				long newlen = 0;
				stream.write(command + "\n");
				_isActive = true;
				while(_isActive)
				{
					oldlen = newlen;

					//_isActive = false;
					synchronized(lock)
					{
						lock.wait(1000);
					}
					if(img.exists())
					{
						newlen = img.length();
						_isActive = newlen > oldlen;
					}
				}
				stream.close();
			}
			catch(Exception e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "ADB screencap: exception", e);
			}

			if(img.exists())
			{
				keepScreenshot(a_Context, img);
			}

			return true;
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "ADB screencap: final exception", e);
			return false;
		}
	}
}
