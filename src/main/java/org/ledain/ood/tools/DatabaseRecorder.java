package org.ledain.ood.tools;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.ledain.ood.AppConfig;
import org.ledain.ood.tools.DatabaseExport.ExportType;
import org.ledain.ood.tools.ToolsDb.RecordMode;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.Log;
import android.util.Xml;

public class DatabaseRecorder
{
	private static final String							TAG					= "DatabaseRecorder - ";

	private static final int							DEFAULT_PERIOD		= 5000;
	private static final int							DEFAULT_MAX_RECORDS	= 2;

	// We don't use namespaces
	private static final String							ns					= null;

	public static final Map<Database, DatabaseRecorder>	_runningThreads		= new HashMap<Database, DatabaseRecorder>();

	private Context										_context;
	private RecordParams								_params;
	private Thread										_thread;
	private RecorderObserver							_recorderObserver;

	public class RecordParams
	{
		public Database	database;
		public int		period		= DEFAULT_PERIOD;
		public int		nMaxRecords	= DEFAULT_MAX_RECORDS;
		public Uri		dbUri;
		public String	sortKey;
	}

	public enum Database
	{
		CONTACTS, RAW_CONTACTS, GROUPS, DATA, ALL
	};

	public enum Change
	{
		NONE, ADDED, DELETED, UPDATED
	}

	public static class Entry
	{
		public Change				_change;
		public final Change[]		_details;
		public final List<String>	_colnames;
		public final List<String>	_cols;

		private Entry(List<String> colnames, List<String> cols)
		{
			_change = Change.NONE;
			_cols = cols;
			_colnames = colnames;
			_details = new Change[_cols.size()];
		}
	}

	public static class RecordParse
	{
		public String		name;
		public int			key;
		public String		date;
		public List<Entry>	entries;
		public List<String>	_cols;
	}

	public DatabaseRecorder(Context a_Context, Database a_db)
	{
		_context = a_Context;
		_params = new RecordParams();

		_params.database = a_db;

		switch(a_db)
		{
			case CONTACTS:
				_params.dbUri = ContactsContract.Contacts.CONTENT_URI;
				_params.sortKey = ContactsContract.Contacts._ID;
				break;
			case RAW_CONTACTS:
				_params.dbUri = ContactsContract.RawContacts.CONTENT_URI;
				_params.sortKey = ContactsContract.RawContacts._ID;
				break;
			case GROUPS:
				_params.dbUri = ContactsContract.Groups.CONTENT_URI;
				_params.sortKey = ContactsContract.Groups._ID;
				break;
			case DATA:
				_params.dbUri = ContactsContract.Data.CONTENT_URI;
				_params.sortKey = ContactsContract.Data._ID;
				break;
		}
	}

	protected void start(RecordMode a_Mode)
	{
		resetRecorder();

		DatabaseRecorder recorder = _runningThreads.get(_params.database);
		if(recorder != null)
		{
			Log.i(AppConfig.TAG_APP, TAG + "start: record already registered for database " + _params.database);
			return;
		}

		switch(a_Mode)
		{
			case PERIODICALLY:
				_thread = new RecorderThread(_context, _params);
				break;
			case ONCHANGE:
				_recorderObserver = new RecorderObserver(_context, _params);
				_recorderObserver.registerObserver();
				_thread = _recorderObserver._thread;
				break;
		}

		Log.i(AppConfig.TAG_APP, TAG + "start: starting record for database " + _params.database);
		_thread.start();

		if(_recorderObserver != null)
		{
			try
			{
				Thread.sleep(1000);
			}
			catch(InterruptedException e)
			{
			}
			_recorderObserver.onChange(false);
		}
		_runningThreads.put(_params.database, this);
	}

	protected void stop()
	{
		Log.i(AppConfig.TAG_APP, TAG + "stop: stopping record for database " + _params.database);
		if(_recorderObserver != null)
		{
			_recorderObserver.unregisterObserver();
		}
		_thread.interrupt();
	}

	private static String getDate()
	{
		Calendar cal = Calendar.getInstance(TimeZone.getDefault());
		String tab[] = { String.valueOf(cal.get(Calendar.YEAR)), String.valueOf(cal.get(Calendar.MONTH) + 1),
						String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), String.valueOf(cal.get(Calendar.HOUR)),
						String.valueOf(cal.get(Calendar.MINUTE)), String.valueOf(cal.get(Calendar.SECOND)), };

		for (int i = 0; i < tab.length; i++)
		{
			if(tab[i].length() == 1) tab[i] = "0" + tab[i];
		}

		return tab[0] + tab[1] + tab[2] + "T" + tab[3] + tab[4] + tab[5];
	}

	public void setMaxRecords(int a_nMaxRecords)
	{
		_params.nMaxRecords = a_nMaxRecords;
	}

	public static final File getRecordDir(Context a_Context, Database a_Database)
	{
		return new File(a_Context.getCacheDir() + File.separator + a_Database.toString() + File.separator);
	}

	public static boolean deleteRecordContent(Context a_Context, Database a_db, String a_name)
	{
		return new File(getRecordDir(a_Context, a_db), a_name).delete();
	}

	public static File getRecordContent(Context a_Context, Database a_db, String a_name) throws IOException
	{
		File dir = getRecordDir(a_Context, a_db);
		File data = new File(dir.getPath(), a_name);

		return convertContentXMLtoJSON(a_Context, new FileInputStream(data));
	}

	protected static File convertContentXMLtoJSON(Context a_Context, InputStream a_IS)
	{
		File file = null;
		OutputStream os = null;

		try
		{
			file = File.createTempFile("changes", null, a_Context.getCacheDir());
			os = new BufferedOutputStream(new FileOutputStream(file));

			RecordParse table = parse(a_IS);
			os.write(("{ \"code\": 0, \"name\":\"" + table.name + "\", \"total\":" + table.entries.size() + ", \"cols\": [ ")
							.getBytes());

			boolean bFirst = true;
			if(table._cols != null)
			{
				for (String colname : table._cols)
				{
					String s = (bFirst ? "" : ",") + " { \"name\":\"" + colname + "\" }";
					os.write(s.getBytes());
					bFirst = false;
				}
			}

			os.write("], \"content\": [ ".getBytes());
			bFirst = true;
			for (Entry e : table.entries)
			{
				String s = (bFirst ? "" : ",") + "\n{ \"cols\": [";
				int ccol = 0;
				for (String col : e._cols)
				{
					s += (ccol == 0 ? "" : ",") + "{ \"value\":\""
									+ col.replace("\"", "\\\"").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n")
									+ "\"}";
					ccol++;
				}

				s += "]} ";
				os.write(s.getBytes());
				bFirst = false;
			}
			os.write("] }".getBytes());
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "exception", e);
		}
		finally
		{
			try
			{
				os.close();
			}
			catch(IOException e)
			{
			}
		}
		return file;
	}

	public static File getRecordDiff(Context a_Context, Database a_db, String a_name1, String a_name2)
	{
		File dir = getRecordDir(a_Context, a_db);
		File dataBefore = new File(dir.getPath(), a_name1);
		File dataAfter = new File(dir.getPath(), a_name2);

		RecordParse result = compare(dataBefore, dataAfter);
		return changesToJSON(a_Context, result);
	}

	private static File changesToJSON(Context a_Context, RecordParse a_changes)
	{
		File file = null;
		OutputStream os = null;

		try
		{
			file = File.createTempFile("changes", null, a_Context.getCacheDir());
			os = new BufferedOutputStream(new FileOutputStream(file));

			os.write(("{ \"code\": 0, \"total\":" + a_changes.entries.size() + ", \"cols\": [ ").getBytes());
			boolean bFirst = true;
			for (String colname : a_changes._cols)
			{
				String s = (bFirst ? "" : ",") + " { \"name\":\"" + colname + "\" }";
				os.write(s.getBytes());
				bFirst = false;
			}

			os.write("], \"changes\": [ ".getBytes());
			bFirst = true;
			for (Entry e : a_changes.entries)
			{
				String s = (bFirst ? "" : ",") + "\n{ \"change\":\"" + e._change.toString() + "\", \"cols\": [";
				int ccol = 0;
				for (String col : e._cols)
				{
					s += (ccol == 0 ? "" : ",") + "{ \"change\":\""
									+ (e._details[ccol] == null ? "null" : e._details[ccol].toString()) + "\", \"value\":\""
									+ col + "\"}";
					ccol++;
				}

				s += "]} ";
				os.write(s.getBytes());
				bFirst = false;
			}
			os.write("] }".getBytes());
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "exception", e);
		}
		finally
		{
			try
			{
				os.close();
			}
			catch(IOException e)
			{
			}
		}
		return file;
	}

	private void resetRecorder()
	{
		File dir = getRecordDir(_context, _params.database);

		if(dir.exists())
		{
			for (String entry : dir.list())
			{
				File todelete = new File(dir, entry);
				if(!todelete.delete())
				{
					Log.e(AppConfig.TAG_APP, TAG + "resetRecorder: failed to delete file " + todelete.toString());
				}
			}
			dir.delete();
		}

		dir.mkdirs();
	}

	private static final void finalizeRecorder(Context a_Context, RecordParams a_Params)
	{
//		getRecordDiff(a_Context, a_Params.database, 1, 2);
	}

	private static final void recordNow(Context a_Context, RecordParams a_Params, File a_Dir, String a_Filename)
	{
		try
		{
			DatabaseExport dbexport = new DatabaseExport(a_Context, a_Params.dbUri, a_Params.database.toString(),
							a_Params.sortKey, null);
			ByteArrayOutputStream baos = dbexport.exportData(ExportType.XML);

			File file = new File(a_Dir.getPath(), a_Filename);
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(baos.toByteArray());
			fos.close();
		}
		catch(IOException e)
		{
		}
	}

	private static class RecorderThread extends Thread
	{
		private Context			_context;
		private RecordParams	_params;

		public RecorderThread(Context a_Context, RecordParams a_Params)
		{
			_context = a_Context;
			_params = a_Params;
		}

		@Override
		public void run()
		{
			int cRecords = 0;
			File dir = getRecordDir(_context, _params.database);

			// start logging into files the diff between 2 times
			while(!isInterrupted() && cRecords < _params.nMaxRecords)
			{
				cRecords += 1;

				recordNow(_context, _params, dir, _params.database + "-record-" + cRecords + ".rec");

				try
				{
					Thread.sleep(_params.period);
				}
				catch(InterruptedException e)
				{
				}
			}

			finalizeRecorder(_context, _params);
		}
	}

	private static class RecorderObserver extends ContentObserver
	{
		private Context			_context;
		private RecordParams	_params;
		private RecordThread	_thread;

		private class RecordThread extends Thread
		{
			private boolean	_checkDelay;
			private int		_cptSyncAuto;
			private Object	_startLock	= new Object();

			public RecordThread()
			{
				super();
			}

			@Override
			public void run()
			{
				File dir = getRecordDir(_context, _params.database);
				int cRecord = 0;

				while(true)
				{
					try
					{
						synchronized(_startLock)
						{
							_startLock.wait();
						}

						while(_cptSyncAuto > 0)
						{
							Thread.sleep(1000);
							_cptSyncAuto--;
						}

						cRecord++;
						recordNow(_context, _params, dir, _params.database + "-record-" + cRecord + ".rec");
						_checkDelay = false;

					}
					catch(InterruptedException e)
					{
					}
				}
			}

			private void launchWithDelay()
			{
				if(_checkDelay)
				{
					if(_cptSyncAuto < 2)
					{
						_cptSyncAuto++;
					}

					return;
				}

				_cptSyncAuto = 1;
				_checkDelay = true;
				synchronized(_startLock)
				{
					_startLock.notify();
				}
			}

		}

		public RecorderObserver(Context a_Context, RecordParams a_Params)
		{
			super(new Handler(Looper.getMainLooper()));

			_context = a_Context;
			_params = a_Params;
			_thread = new RecordThread();
		}

		public void registerObserver()
		{
			_context.getContentResolver().registerContentObserver(_params.dbUri, true, this);
		}

		public void unregisterObserver()
		{
			_context.getContentResolver().unregisterContentObserver(this);
		}

		@Override
		public void onChange(boolean selfChange)
		{
			_thread.launchWithDelay();
		}

	}

	private static RecordParse compare(File dataBefore, File dataAfter)
	{
		List<Entry> changes = new ArrayList<Entry>();
		RecordParse result = new RecordParse();

		/*
		 * 1. search each line from before in after
		 * 1.a. found (remove from after)
		 * 1.a.1. same: don't display
		 * 1.a.2. different: display as changed, with colors (which fields have changed)
		 * 1.b. not found: display as deleted
		 * 2. for remaining entries in after, display as new
		 * 
		 * To make things easier, let's use the _id as key, in first column.
		 * We need to put in memory both tables
		 */
		try
		{
			RecordParse before = parse(new FileInputStream(dataBefore));
			RecordParse after = parse(new FileInputStream(dataAfter));

			String nextId = null;
			Iterator<Entry> listAfter = after.entries.iterator();
			Entry ea = null;
			if(listAfter.hasNext())
			{
				ea = listAfter.next();
				nextId = ea._cols.get(before.key);
			}

			if(before._cols != null)
			{
				result._cols = before._cols;
			}
			else
			{
				result._cols = after._cols;
			}

			for (Entry eb : before.entries)
			{
				eb._change = Change.NONE;

				String id = eb._cols.get(before.key);

				if(nextId != null && id.equals(nextId))
				{
					// found. Now compare.
					for (int icol = 0; icol < eb._cols.size(); icol++)
					{
						String ebcol = eb._cols.get(icol);
						String eacol = ea._cols.get(icol);

						if(ebcol == eacol) // both null, or alike
						{
							eb._details[icol] = Change.NONE;
						}
						else if(ebcol != null)
						{
							if(ebcol.equals(eacol))
							{
								eb._details[icol] = Change.NONE;
							}
							else
							{
								eb._details[icol] = Change.UPDATED;
								eb._change = Change.UPDATED;
								eb._cols.set(icol, eacol);
							}
						}
					}

					if(listAfter.hasNext())
					{
						ea = listAfter.next();
						nextId = ea._cols.get(before.key);
					}
					else
					{
						ea = null;
						nextId = null;
					}
				}
				else
				{
					// ! found : mark as deleted
					eb._change = Change.DELETED;
				}

				Log.d(AppConfig.TAG_APP, TAG + "compare: row change=" + eb._change);
				if(eb._change != Change.NONE)
				{
					changes.add(eb);
				}
			}

			if(ea != null)
			{
				ea._change = Change.ADDED;
				changes.add(ea);
				Log.d(AppConfig.TAG_APP, TAG + "compare: row change=" + ea._change);
			}
			while(listAfter.hasNext())
			{
				ea = listAfter.next();
				ea._change = Change.ADDED;
				changes.add(ea);
				Log.d(AppConfig.TAG_APP, TAG + "compare: row change=" + ea._change);
			}
		}
		catch(Exception e)
		{
		}

		result.entries = changes;
		return result;
	}

	public static RecordParse parse(InputStream in) throws XmlPullParserException, IOException
	{
		try
		{
			XmlPullParser parser = Xml.newPullParser();
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
			parser.setInput(in, null);

			parser.nextTag();

			String name = null;
			for (int iAttr = 0; iAttr < parser.getAttributeCount(); iAttr++)
			{

				String attr = parser.getAttributeName(iAttr);
				if(attr.equals("name"))
				{
					name = parser.getAttributeValue(iAttr);
					break;
				}
			}

			parser.nextTag();

			RecordParse result = readTable(parser);
			result.name = name;
			return result;
		}
		finally
		{
			in.close();
		}
	}

	private static RecordParse readTable(XmlPullParser parser) throws XmlPullParserException, IOException
	{
		RecordParse result = new RecordParse();
		result.entries = new ArrayList<Entry>();

		parser.require(XmlPullParser.START_TAG, ns, "table");

		result.key = -1;
		for (int iAttr = 1; iAttr < parser.getAttributeCount(); iAttr++)
		{

			String attr = parser.getAttributeName(iAttr);
			if(attr.equals("key"))
			{
				result.key = Integer.parseInt(parser.getAttributeValue(iAttr));
			}
			else if(attr.equals("date"))
			{
				result.date = parser.getAttributeValue(iAttr);
			}
		}

		while(parser.next() != XmlPullParser.END_TAG)
		{
			if(parser.getEventType() != XmlPullParser.START_TAG)
			{
				continue;
			}
			String name = parser.getName();
			// Starts by looking for the entry tag
			if(name.equals("row"))
			{
				result.entries.add(readRow(parser, result.entries.size() == 0));
			}
			else
			{
				skip(parser);
			}
		}

		if(result.entries != null && result.entries.size() > 0)
		{
			result._cols = result.entries.get(0)._colnames;
		}
		return result;
	}

	private static Entry readRow(XmlPullParser parser, boolean a_bAddHeader) throws XmlPullParserException, IOException
	{
		parser.require(XmlPullParser.START_TAG, ns, "row");
		List<String> list = new ArrayList<String>();
		List<String> names = a_bAddHeader ? new ArrayList<String>() : null;

		while(parser.next() != XmlPullParser.END_TAG)
		{
			if(parser.getEventType() != XmlPullParser.START_TAG)
			{
				continue;
			}
			String name = parser.getName();
			if(name.equals("col"))
			{
				if(a_bAddHeader)
				{
					for (int iAttr = 0; iAttr < parser.getAttributeCount(); iAttr++)
					{
						String attr = parser.getAttributeName(iAttr);
						if(attr.equals("name"))
						{
							names.add(parser.getAttributeValue(iAttr));
							break;
						}
					}
				}
				list.add(readCol(parser));
			}
			else
			{
				skip(parser);
			}
		}
		return new Entry(names, list);
	}

	private static String readCol(XmlPullParser parser) throws IOException, XmlPullParserException
	{
		parser.require(XmlPullParser.START_TAG, ns, "col");
		String value = readText(parser);
		parser.require(XmlPullParser.END_TAG, ns, "col");
		return value;
	}

	// For the tags title and summary, extracts their text values.
	private static String readText(XmlPullParser parser) throws IOException, XmlPullParserException
	{
		String result = "";
		if(parser.next() == XmlPullParser.TEXT)
		{
			result = parser.getText();
			parser.nextTag();
		}
		return result;
	}

	private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException
	{
		if(parser.getEventType() != XmlPullParser.START_TAG)
		{
			throw new IllegalStateException();
		}
		int depth = 1;
		while(depth != 0)
		{
			switch(parser.next())
			{
				case XmlPullParser.END_TAG:
					depth--;
					break;
				case XmlPullParser.START_TAG:
					depth++;
					break;
			}
		}
	}

	public static boolean isRecorded(Database a_db)
	{
		if(a_db == Database.ALL)
		{
			return _runningThreads.size() > 0;
		}
		else
		{
			return _runningThreads.get(a_db) != null;
		}
	}

	public static void startRecord(Context a_Context, Database a_db, RecordMode a_mode)
	{
		if(a_db == Database.ALL)
		{
			for (Database db : Database.values())
			{
				if(db != Database.ALL)
				{
					startRecord(a_Context, db, a_mode);
				}
			}
		}
		else
		{
			DatabaseRecorder rec = new DatabaseRecorder(a_Context, a_db);
			rec.start(a_mode);
		}
	}

	public static void stopRecord(Context a_Context, Database a_db)
	{
		if(a_db == Database.ALL)
		{
			for (Database db : Database.values())
			{
				if(db != Database.ALL)
				{
					stopRecord(a_Context, db);
				}
			}
		}
		else
		{
			DatabaseRecorder recorder = _runningThreads.remove(a_db);
			if(recorder != null)
			{
				recorder.stop();
			}
			else
			{
				Log.i(AppConfig.TAG_APP, TAG + "stopRecord: no record registered for database " + a_db);
			}
		}
	}
}
