package org.ledain.ood.tools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import org.ledain.ood.AppConfig;

public class ToolsDb
{
	private static final String	TAG	= "ToolsDb - ";

	public enum RecordMode
	{
		PERIODICALLY, ONCHANGE
	}

	public static class Tables
	{
		public String					name;
		public Map<String, List<Table>>	tables;
		public boolean					isOurProduct;

		public Tables(String a_name, boolean a_bIsOurProduct, Map<String, List<Table>> a_tables)
		{
			name = a_name;
			tables = a_tables;
			isOurProduct = a_bIsOurProduct;
		}
	}

	public static class Table
	{
		public String			name;
		public boolean			hasXml;
		public String			uri;
		public static String	url	= "/tools/db/listtable";
		public boolean			toExport;

		public Table(String a_name, boolean a_hasXml, String a_uri, boolean a_bToExport)
		{
			name = a_name;
			hasXml = a_hasXml;
			uri = a_uri;
			toExport = a_bToExport;
		}

		public static Comparator<Table>	COMPARATOR	= new Comparator<Table>()
													{

														@Override
														public int compare(Table lhs, Table rhs)
														{
															return lhs.name.compareTo(rhs.name);
														}
													};
	}

	public static void listTable(Context a_Context, boolean a_bXML, Map<String, Object> a_params, OutputStream a_outputstream)
					throws IOException
	{
		Uri dbUri = null;
		String tableName = "";

		ByteArrayOutputStream os = null;

		try
		{
			dbUri = Uri.parse(URLDecoder.decode(a_params.get("uri").toString(), "UTF-8"));
		}
		catch(UnsupportedEncodingException e)
		{
		}

		if(dbUri == null)
		{
			Log.e(AppConfig.TAG_APP, TAG + "no database URI defined");
			a_outputstream.write("(error: no database URI defined)".getBytes());
			return;
		}

		DatabaseExport dbexport = new DatabaseExport(a_Context, dbUri, tableName, null, null);

		Log.d(AppConfig.TAG_APP, TAG + "listTable: export table to XML");

		try
		{
			os = dbexport.exportDataToXml();
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "Error exporting data", e);
		}

		if(os == null)
		{
			Log.e(AppConfig.TAG_APP, TAG + "Error exporting data");
			a_outputstream.write("{}".getBytes());
			return;
		}

		if(a_bXML)
		{
			try
			{
				IoUtils.copy(new ByteArrayInputStream(os.toByteArray()), a_outputstream);
			}
			catch(UnsupportedEncodingException e)
			{
			}
		}
		else
		{
			ByteArrayInputStream bais = new ByteArrayInputStream(os.toByteArray());
			IoUtils.copy(new FileInputStream(DatabaseRecorder.convertContentXMLtoJSON(a_Context, bais)), a_outputstream);
		}

	}

	public static void listRecords(Context a_Context, OutputStream a_outstream) throws IOException
	{
		Log.i(AppConfig.TAG_APP, TAG + "listRecords");

		a_outstream.write(" { \"code\": 0, \"databases\": [ ".getBytes());
		boolean bFirst = true;
		for (DatabaseRecorder.Database db : DatabaseRecorder.Database.values())
		{
			File dir = DatabaseRecorder.getRecordDir(a_Context, db);
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

				a_outstream.write(((bFirst ? "" : ",") + "\n { \"name\":\"" + db.toString() + "\", \"records\": [ ").getBytes());
				boolean bFirstEntry = true;
				for (File entry : entries)
				{
					a_outstream.write(((bFirstEntry ? "" : ",") + "{ \"name\":\"" + entry.getName() + "\", \"date\":\""
									+ (new Date(entry.lastModified())).toString() + "\" }").getBytes());
					bFirstEntry = false;
				}

				bFirst = false;
				a_outstream.write("] } ".getBytes());
			}
		}
		a_outstream.write("] }".getBytes());
	}

	public static void startRecord(Context a_Context, String a_db, RecordMode a_mode)
	{
		DatabaseRecorder.Database db = DatabaseRecorder.Database.valueOf(a_db);
		Log.d(AppConfig.TAG_APP, TAG + "startRecord for database " + db);

		DatabaseRecorder.startRecord(a_Context, db, a_mode);
	}

	public static void stopRecord(Context a_Context, String a_db)
	{
		DatabaseRecorder.Database db = DatabaseRecorder.Database.valueOf(a_db);

		Log.d(AppConfig.TAG_APP, TAG + "stopRecord for database " + db);
		DatabaseRecorder.stopRecord(a_Context, db);
	}

	public static void toggleRecord(Context a_Context, String a_db)
	{
		DatabaseRecorder.Database curdb = DatabaseRecorder.Database.valueOf(a_db);

		if(DatabaseRecorder.isRecorded(curdb))
		{
			Log.d(AppConfig.TAG_APP, TAG + "toggleRecord: stop database " + curdb);
			stopRecord(a_Context, a_db);
		}
		else
		{
			Log.d(AppConfig.TAG_APP, TAG + "toggleRecord: start database " + curdb);
			startRecord(a_Context, a_db, RecordMode.ONCHANGE);
		}
	}

	public static void getRecordsInfos(OutputStream a_outstream) throws IOException
	{
		a_outstream.write(" { \"code\": 0, \"databases\": [ ".getBytes());
		boolean bFirst = true;
		for (DatabaseRecorder.Database db : DatabaseRecorder.Database.values())
		{
			a_outstream.write(((bFirst ? "" : ",") + "\n { \"name\":\"" + db.toString() + "\", \"isrecorded\": "
							+ DatabaseRecorder.isRecorded(db) + " } ").getBytes());
			bFirst = false;
		}
		a_outstream.write("] }".getBytes());
	}

	private static void process(InputStream input) throws IOException
	{
		InputStreamReader isr = new InputStreamReader(input);
		BufferedReader reader = new BufferedReader(isr);
		String line;

		while((line = reader.readLine()) != null)
		{
			System.out.println(line);
		}
		reader.close();
	}

	public static Class[] getClasses(String pckgname) throws ClassNotFoundException
	{
		ArrayList<Class> classes = new ArrayList<Class>();
		// Get a File object for the package
		File directory = null;
		try
		{
			for (String jar : System.getProperty("java.boot.class.path").split(":"))
			{
				JarFile jarFile = new JarFile(jar);

				final Enumeration<JarEntry> entries = jarFile.entries();
				while(entries.hasMoreElements())
				{
					final JarEntry entry = entries.nextElement();
					if(entry.getName().contains("."))
					{
						System.out.println("File : " + entry.getName());
						JarEntry fileEntry = jarFile.getJarEntry(entry.getName());
						InputStream input = jarFile.getInputStream(fileEntry);
						process(input);
					}
				}
			}
		}
		catch(NullPointerException x)
		{
			throw new ClassNotFoundException(pckgname + " (" + directory + ") does not appear to be a valid package");
		}
		catch(MalformedURLException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch(IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(directory.exists())
		{
			// Get the list of the files contained in the package
			String[] files = directory.list();
			for (String file : files)
			{
				// we are only interested in .class files
				if(file.endsWith(".class"))
				{
					// removes the .class extension
					classes.add(Class.forName(pckgname + '.' + file.substring(0, file.length() - 6)));
				}
			}
		}
		else
		{
			throw new ClassNotFoundException(pckgname + " does not appear to be a valid package");
		}
		Class[] classesA = new Class[classes.size()];
		classes.toArray(classesA);
		return classesA;
	}

	@SuppressLint("NewApi")
	public static List<Tables> getListOfTables(Context a_Context)
	{
		List<Tables> listOfTables = new ArrayList<Tables>();
		//List<Tables> listOfUnknown = new ArrayList<Tables>();
		List<ProviderInfo> providers = a_Context.getPackageManager().queryContentProviders(null, 0, 0);

		Collections.sort(providers, new Comparator<ProviderInfo>()
		{
			@Override
			public int compare(ProviderInfo lhs, ProviderInfo rhs)
			{
				return lhs.packageName.compareTo(rhs.packageName);
			}
		});

		{
			Map<String, List<Table>> mapTables = null;
			List<Table> tables = null;

			for (ProviderInfo provider : providers)
			{
				mapTables = null;
				tables = null;

				if(provider.authority.contains("sync.core.provider"))
				{
					if(provider.exported)
					{
						mapTables = new HashMap<String, List<Table>>();

						tables = new ArrayList<Table>();
						tables.add(new Table("Admin - command", true, "content://" + provider.authority + "/admin/command",
										true));
						mapTables.put("Admin", tables);

						tables = new ArrayList<Table>();
						tables.add(new Table("files", true, "content://" + provider.authority + "/media/files", true));
						tables.add(new Table("albums", true, "content://" + provider.authority + "/media/albums", true));
						tables.add(new Table("thumbnails", true, "content://" + provider.authority + "/media/thumbnails", true));
						tables.add(new Table("browsing", true, "content://" + provider.authority + "/media/browsing", true));
						tables.add(new Table("comment", true, "content://" + provider.authority + "/media/comment", true));
						tables.add(new Table("user_profile", true, "content://" + provider.authority + "/media/user_profile",
										true));
						tables.add(new Table("timeline", true, "content://" + provider.authority + "/media/timeline", true));
						tables.add(new Table("node", true, "content://" + provider.authority + "/media/node", true));
						tables.add(new Table("node_access", true, "content://" + provider.authority + "/media/node_access",
										true));
						tables.add(new Table("sharing", true, "content://" + provider.authority + "/media/sharing", true));
						tables.add(new Table("node", true, "content://" + provider.authority + "/media/node", true));
						mapTables.put("Media", tables);

						tables = new ArrayList<Table>();
						tables.add(new Table("message", true, "content://" + provider.authority + "/messages/message", true));
						tables.add(new Table("threads", true, "content://" + provider.authority + "/messages/thread", true));
						mapTables.put("Messages", tables);

						tables = new ArrayList<Table>();
						tables.add(new Table("preferences", true, "content://" + provider.authority
										+ "/preferences/preferences", true));
						mapTables.put("Preferences", tables);

						tables = new ArrayList<Table>();
						tables.add(new Table("request_data", true, "content://" + provider.authority + "/request/request_data",
										true));
						tables.add(new Table("filecache", true, "content://" + provider.authority + "/request/filecache", true));
						mapTables.put("Request", tables);

						tables = new ArrayList<Table>();
						tables.add(new Table("Sync - syncinf", true, "content://" + provider.authority + "/sync/syncinf", true));
						tables.add(new Table("Sync - history", true, "content://" + provider.authority + "/sync/history", true));
						tables.add(new Table("Sync - databasehistory", true, "content://" + provider.authority
										+ "/sync/databasehistory", true));
						tables.add(new Table("Sync - syncids", true, "content://" + provider.authority + "/sync/syncids", true));
						tables.add(new Table("Sync - synclastoperation", true, "content://" + provider.authority
										+ "/sync/synclastoperation", true));
						tables.add(new Table("Sync - currentsyncinf", true, "content://" + provider.authority
										+ "/sync/currentsyncinf", true));
						tables.add(new Table("Sync - additionalfield", true, "content://" + provider.authority
										+ "/sync/additionalfield", true));
						mapTables.put("Sync", tables);

						tables = new ArrayList<Table>();
						tables.add(new Table("users", true, "content://" + provider.authority + "/userdirectory/users", true));
						tables.add(new Table("services", true, "content://" + provider.authority + "/userdirectory/services",
										true));
						tables.add(new Table("deviceagents", true, "content://" + provider.authority
										+ "/userdirectory/deviceagents", true));
						tables.add(new Table("storage", true, "content://" + provider.authority + "/userdirectory/storage",
										true));
						mapTables.put("UserDirectory", tables);

						tables = new ArrayList<Table>();
						tables.add(new Table("account80", true, "content://" + provider.authority + "/sync80/account80", true));
						tables.add(new Table("storage80", true, "content://" + provider.authority + "/sync80/storage", true));
						tables.add(new Table("deviceagents", true, "content://" + provider.authority + "/sync80/deviceagents",
										true));
						mapTables.put("UserDirectory80", tables);
						mergeTables(listOfTables, provider.packageName, mapTables);
					}
					else
					{
						listOfTables.add(new Tables(provider.packageName, true, null));
					}
				}
				else if(provider.authority.contains(".vault.sync"))
				{
					if(provider.exported)
					{
						mapTables = new HashMap<String, List<Table>>();

						tables = new ArrayList<Table>();
						tables.add(new Table("repository", true, "content://" + provider.authority + "/repository", true));
						tables.add(new Table("file", true, "content://" + provider.authority + "/file", true));
						mapTables.put("vault.sync", tables);
						mergeTables(listOfTables, provider.packageName, mapTables);
					}
					else
					{
						listOfTables.add(new Tables(provider.packageName, true, null));
					}
				}
				else if(provider.authority.toLowerCase().contains(".cscontentprovider"))
				{
					if(provider.exported)
					{
						mapTables = new HashMap<String, List<Table>>();

						tables = new ArrayList<Table>();
						tables.add(new Table("shares", true, "content://" + provider.authority + "/shares", true));
						tables.add(new Table("members", true, "content://" + provider.authority + "/members", true));
						tables.add(new Table("resources", true, "content://" + provider.authority + "/resources", true));
						tables.add(new Table("owners", true, "content://" + provider.authority + "/owners", true));
						tables.add(new Table("links", true, "content://" + provider.authority + "/links", true));
						tables.add(new Table("resourceSummaryGroups", true, "content://" + provider.authority
										+ "/resourceSummaryGroups", true));

						mapTables.put("cloudshare", tables);
						mergeTables(listOfTables, provider.packageName, mapTables);
					}
					else
					{
						listOfTables.add(new Tables(provider.packageName, true, null));
					}
				}
				else if(provider.authority.equals("com.android.contacts") || provider.authority.equals("contacts")
								|| provider.authority.endsWith(";com.android.contacts")
								|| provider.authority.startsWith("com.android.contacts;")
								|| provider.authority.contains(";com.android.contacts;"))
				{
					mapTables = new HashMap<String, List<Table>>();

					tables = new ArrayList<Table>();
					addProviderTables(tables, ContactsContract.class);
					mapTables.put("Contacts", tables);

					listOfTables.add(new Tables(provider.packageName, false, mapTables));
				}
				else if(provider.authority.equals("com.android.calendar") || provider.authority.equals("calendar"))
				{
					mapTables = new HashMap<String, List<Table>>();

					tables = new ArrayList<Table>();
					if(android.os.Build.VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH)
					{
						addProviderTables(tables, CalendarContract.class);
					}
					else
					{
						tables.add(new Table("Calendars", true, CalendarTools.Calendars.getUri().toString(), false));
						tables.add(new Table("Events", true, CalendarTools.Events.getUri().toString(), false));
						tables.add(new Table("Reminders", true, CalendarTools.Reminders.getUri().toString(), false));
					}
					mapTables.put("Calendar", tables);

					listOfTables.add(new Tables(provider.packageName, false, mapTables));
				}
				else if(provider.authority.equals("call_log"))
				{
					mapTables = new HashMap<String, List<Table>>();

					tables = new ArrayList<Table>();
					addProviderTables(tables, CallLog.class);
					mapTables.put("Call logs", tables);

					listOfTables.add(new Tables(provider.packageName, false, mapTables));
				}
				else if(provider.authority.equals(android.provider.MediaStore.AUTHORITY))
				{
					mapTables = new HashMap<String, List<Table>>();

					tables = new ArrayList<Table>();
					addProviderTables(tables, MediaStore.class);
					/*
					tables.add(new Table("Images - external", true,
									android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()));
					tables.add(new Table("Images - internal", true,
									android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI.toString()));
					tables.add(new Table("Video - external", true, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
									.toString()));
					tables.add(new Table("Video - internal", true, android.provider.MediaStore.Video.Media.INTERNAL_CONTENT_URI
									.toString()));
					tables.add(new Table("Audio - external", true, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
									.toString()));
					tables.add(new Table("Audio - internal", true, android.provider.MediaStore.Audio.Media.INTERNAL_CONTENT_URI
									.toString()));
					*/
					mapTables.put("Media", tables);

					listOfTables.add(new Tables(provider.packageName, false, mapTables));
				}
				else if(provider.authority.equals("sms"))
				{
					mapTables = new HashMap<String, List<Table>>();

					tables = new ArrayList<Table>();
					tables.add(new Table("SMS", true, "content://sms", false));
					tables.add(new Table("SMS Inbox", true, "content://sms/inbox", false));
					tables.add(new Table("SMS Outbox", true, "content://sms/sent", false));
					tables.add(new Table("SMS Conversations", true, "content://mms-sms/conversations", false));
					mapTables.put("SMS", tables);

					listOfTables.add(new Tables(provider.packageName, false, mapTables));
				}
				else if(provider.authority.equals("settings"))
				{
					mapTables = new HashMap<String, List<Table>>();

					tables = new ArrayList<Table>();
					tables.add(new Table("Settings global", true, "content://settings/global", true));
					tables.add(new Table("Settings system", true, "content://settings/system", true));
					tables.add(new Table("Settings secure", true, "content://settings/secure", true));
					tables.add(new Table("User dictionary words", true, "content://user_dictionary/words/", false));
					tables.add(new Table("Browser bookmarks", true, "content://browser/bookmarks", false));
					mapTables.put("Settings", tables);

					listOfTables.add(new Tables(provider.packageName, false, mapTables));
				}
				else
				{
					Log.d(AppConfig.TAG_APP, TAG + "getListOfTables: unknown provider " + provider.packageName + " / "
									+ provider.name + ", auth=" + provider.authority);
					/*
					listOfUnknown.add(new Tables("pkg=" + provider.packageName + ", auth=" + provider.authority, false, null));
					*/
				}

				if(provider.uriPermissionPatterns != null)
				{
					Log.d(AppConfig.TAG_APP, TAG + "getListOfTables: uri permission found.");
				}
			}
		}

		return listOfTables;
	}

	private static void mergeTables(List<Tables> listOfTables, String packageName, Map<String, List<Table>> mapTables)
	{
		Tables listTablesForProvider = null;

		if(mapTables == null)
		{
			mapTables = new HashMap<String, List<Table>>();

		}
		for (Tables t : listOfTables)
		{
			if(t.name.equals(packageName))
			{
				listTablesForProvider = t;
				break;
			}
		}
		if(listTablesForProvider == null)
		{
			listTablesForProvider = new Tables(packageName, true, mapTables);
			listOfTables.add(listTablesForProvider);
		}
		else
		{
			if(listTablesForProvider.tables == null)
			{
				listTablesForProvider.tables = new HashMap<String, List<Table>>();
			}
			listTablesForProvider.tables.putAll(mapTables);
		}

	}

	public static void writeListOfTables(Context a_Context, List<Tables> a_tables, OutputStream outstream) throws IOException
	{
		outstream.write(" { \"code\": 0, \"databases\": [ ".getBytes());

		boolean bFirstDb = true;
		for (Tables tables : a_tables)
		{
			outstream.write(((bFirstDb ? "" : ",") + "\n { \"name\":\"" + tables.name + "\", \"isexported\": "
							+ (tables.tables != null) + ", \"isourproduct\": " + tables.isOurProduct + ", \"groups\": [ ")
							.getBytes());

			if(tables.tables != null)
			{
				boolean bFirstGroup = true;

				for (Entry<String, List<Table>> e : tables.tables.entrySet())
				{
					outstream.write(((bFirstGroup ? "" : ",") + "\n { \"name\": \"" + e.getKey() + "\", \"tables\": [ ")
									.getBytes());

					Collections.sort(e.getValue(), Table.COMPARATOR);

					boolean bFirstTable = true;
					for (Table table : e.getValue())
					{
						outstream.write(((bFirstTable ? "" : ",") + "\n { \"name\":\"" + table.name
										+ "\", \"xml\": true, \"uri\": \"" + table.uri + "\" } ").getBytes());
						bFirstTable = false;
					}
					outstream.write(" ] } ".getBytes());
					bFirstGroup = false;
				}
			}

			bFirstDb = false;
			outstream.write("	] } ".getBytes());
		}
		/*
		outstream.write("], \"unknown\": [ ".getBytes());
		bFirstDb = true;
		for (Tables tables : listOfUnknown)
		{
			outstream.write(((bFirstDb ? "" : ",") + "\n { \"name\":\"" + tables.name + "\", \"isexported\": false }")
							.getBytes());
			bFirstDb = false;
		}
		*/
		outstream.write("] }".getBytes());
	}

	private static void addProviderTables(List<Table> a_Tables, Class<?> a_provider)
	{
		try
		{
			for (Field field : a_provider.getDeclaredFields())
			{
				if(field != null && field.getType().equals(Uri.class))
				{
					a_Tables.add(new Table(field.getDeclaringClass().getName() + "." + field.getName(), true, field.get(null)
									.toString(), false));
				}
			}
		}
		catch(Exception e)
		{
		}

		for (Class<?> theclass : a_provider.getDeclaredClasses())
		{
			addProviderTables(a_Tables, theclass);
		}
	}

	public static void query(Context a_Context, Map<String, Object> a_params, OutputStream a_outputstream) throws IOException
	{
		Uri dbUri = null;
		String command = null;
		String[] projection = null;
		String selection = null;
		String[] selectionArgs = null;
		String sort = null;
		Long begin = null;
		Long end = null;
		Cursor cursor = null;

		ByteArrayOutputStream os = null;

		try
		{
			String uri = a_params.get("uri").toString();
			if(!uri.startsWith("content://"))
			{
				uri = getUriFromClass(uri);
			}
			dbUri = Uri.parse(URLDecoder.decode(uri, "UTF-8"));
			command = a_params.get("command").toString();

			int size;

			if(a_params.get("projection") != null)
			{
				size = ((List<Object>) a_params.get("projection")).size();
				projection = new String[size];
				((List<Object>) a_params.get("projection")).toArray(projection);
				if(projection.length == 0)
				{
					projection = null;
				}
			}

			if(a_params.get("selection") != null)
			{
				selection = a_params.get("selection").toString();
				if(TextUtils.isEmpty(selection))
				{
					selection = null;
				}
			}

			if(a_params.get("selectionArgs") != null)
			{
				size = ((List<Object>) a_params.get("selectionArgs")).size();
				selectionArgs = new String[size];
				((List<Object>) a_params.get("selectionArgs")).toArray(selectionArgs);
				if(selectionArgs.length == 0)
				{
					selectionArgs = null;
				}
			}

			if(a_params.get("sort") != null)
			{
				sort = a_params.get("sort").toString();
				if(TextUtils.isEmpty(sort))
				{
					sort = null;
				}
			}
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "Unexpected exception", e);
			a_outputstream.write(("{ \"code\":1, \"msg\":\"unexpected exception " + e.getMessage() + "\"}").getBytes());
			return;
		}

		if(dbUri == null)
		{
			Log.e(AppConfig.TAG_APP, TAG + "no database URI defined");
			a_outputstream.write("{ \"code\":1, \"msg\":\"no database URI defined\"}".getBytes());
			return;
		}

		if(TextUtils.isEmpty(command))
		{
			command = "query";
		}

		try
		{
			if(command.equals("query"))
			{
				if(dbUri.equals(CalendarTools.Instances.getUri()))
				{
					begin = Long.parseLong(a_params.get("begin").toString());
					end = Long.parseLong(a_params.get("end").toString());

					/*
					String beginString = String.valueOf(begin);
					String endString = String.valueOf(end);

					final String SQL_WHERE_GET_EVENTS_ENTRIES = "((" + Events.DTSTART + " <= ? AND " + "(" + Events.LAST_DATE
									+ " IS NULL OR " + Events.LAST_DATE + " >= ?)) OR " + "(" + Events.ORIGINAL_INSTANCE_TIME
									+ " IS NOT NULL AND " + Events.ORIGINAL_INSTANCE_TIME + " <= ? AND "
									+ Events.ORIGINAL_INSTANCE_TIME + " >= ?))";

					// To determine if a recurrence exception originally overlapped the
					// window, we need to assume a maximum duration, since we only know
					// the original start time.
					final int MAX_ASSUMED_DURATION = 7 * 24 * 60 * 60 * 1000;

					selectionArgs = new String[] { endString, beginString, endString,
									String.valueOf(begin - MAX_ASSUMED_DURATION) };
					cursor = a_Context.getContentResolver().query(CalendarContract.Events.CONTENT_URI, projection,
									SQL_WHERE_GET_EVENTS_ENTRIES, selectionArgs, sort);

					*/
					cursor = CalendarTools.Instances.query(a_Context.getContentResolver(), projection, begin, end);
				}
				else
				{
					cursor = a_Context.getContentResolver().query(dbUri, projection, selection, selectionArgs, sort);
				}
			}
			else if(command.equals("delete"))
			{
				int n = a_Context.getContentResolver().delete(dbUri, selection, selectionArgs);
				a_outputstream.write(("{ \"code\":0, \"msg\":\"number of deleted rows: " + n + "\"}").getBytes());
				return;
			}
			else
			{
				Log.e(AppConfig.TAG_APP, TAG + "unknown command " + command);
				a_outputstream.write(("{ \"code\":1, \"msg\":\"unknown command " + command + "\"}").getBytes());
				return;
			}
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "query: exception", e);
			a_outputstream.write(("{ \"code\":1, \"msg\":\"error querying database: " + e.getMessage() + "\"}").getBytes());
			return;
		}

		if(cursor != null)
		{
			DatabaseExport dbexport = new DatabaseExport(a_Context, dbUri, null, null, cursor);

			Log.d(AppConfig.TAG_APP, TAG + "query: export table to XML");

			try
			{
				os = dbexport.exportDataToXml();
			}
			catch(Exception e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "Error exporting data", e);
			}

			if(os == null)
			{
				Log.e(AppConfig.TAG_APP, TAG + "Error exporting data");
				a_outputstream.write("{ \"code\":1, \"msg\":\"Error exporting data\"}".getBytes());
				return;
			}

			ByteArrayInputStream bais = new ByteArrayInputStream(os.toByteArray());
			IoUtils.copy(new FileInputStream(DatabaseRecorder.convertContentXMLtoJSON(a_Context, bais)), a_outputstream);
		}
		else
		{
			Log.e(AppConfig.TAG_APP, TAG + "Query: no cursor.");
			a_outputstream.write("{ \"code\":1, \"msg\":\"Query: no cursor.\"}".getBytes());
			return;
		}
	}

	private static String getUriFromClass(String a_uri)
	{
		String name = a_uri.substring(0, a_uri.lastIndexOf('.'));
		String member = a_uri.substring(name.length() + 1);

		try
		{
			Class<?> aclass = Class.forName(name);
			Field f = aclass.getField(member);

			if(f != null && f.getType().equals(Uri.class))
			{
				return f.get(null).toString();
			}
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "getUriFromClass: cannot retrieve Uri.");
		}

		return null;
	}
}
