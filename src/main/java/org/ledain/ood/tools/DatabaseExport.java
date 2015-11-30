package org.ledain.ood.tools;

/**
 * 
 */
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.ledain.ood.AppConfig;

/**
 * Example of use:
 * 
 * DatabaseExport export = new DatabaseExport(a_Context,
 * CalendarTools.Calendars.getUri(), "calendars");
 * export.exportData();
 * 
 */
public class DatabaseExport
{
	private static final String	TAG			= "DatabaseExport - ";

	private Context				_ctx;
	private Uri					_dbUri;
	private String				_sortKey;
	private IExporter			_exporter;
	private String				_tablename;
	private Cursor				_cursor;

	public int					KeyColIndex	= -1;

	public enum ExportType
	{
		NONE, XML, HTML
	};

	public static String getDate()
	{
		/*
		Calendar cal = Calendar.getInstance(TimeZone.getDefault());
		String tab[] = { String.valueOf(cal.get(Calendar.YEAR)), String.valueOf(cal.get(Calendar.MONTH) + 1),
						String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), String.valueOf(cal.get(Calendar.HOUR)),
						String.valueOf(cal.get(Calendar.MINUTE)), String.valueOf(cal.get(Calendar.SECOND)), };

		for (int i = 0; i < tab.length; i++)
		{
			if(tab[i].length() == 1) tab[i] = "0" + tab[i];
		}

		return tab[0] + tab[1] + tab[2] + "T" + tab[3] + tab[4] + tab[5];
		*/
		return new Date().toString();
	}

	public DatabaseExport(Context ctx, Uri a_dbUri, String a_tableName, String a_sortKey, Cursor a_Cursor)
	{
		_ctx = ctx;
		_dbUri = a_dbUri;
		_sortKey = a_sortKey;
		_tablename = a_tableName;
		_cursor = a_Cursor;
	}

	public ByteArrayOutputStream exportDataToXml()
	{
		return exportData(ExportType.XML);
	}

	public ByteArrayOutputStream exportDataToHtml()
	{
		return exportData(ExportType.HTML);
	}

	public ByteArrayOutputStream exportData()
	{
		return exportData(ExportType.NONE);
	}

	public ByteArrayOutputStream exportData(ExportType a_type)
	{
		ByteArrayOutputStream result = null;

		try
		{
			// create a file on the sdcard to export the
			// database contents to

			switch(a_type)
			{
				case NONE:
					_exporter = null;
					break;

				case XML:
					_exporter = new Exporter();
					break;

				case HTML:
					_exporter = new HtmlExporter();
					break;
			}
		}
		catch(FileNotFoundException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "exportData() exception", e);
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "exportData() exception", e);
		}

		try
		{
			if(_exporter != null)
			{
				_exporter.startDbExport(_dbUri.toString());
			}

			exportTable();

			if(_exporter != null)
			{
				result = _exporter.endDbExport();
				_exporter.close();
			}
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "exportData() exception", e);
		}

		return result;
	}

	private void exportTable() throws IOException
	{
		String sortOrder = null;
		if(_sortKey != null)
		{
			sortOrder = _sortKey + " ASC";
		}

		String date = getDate();
		final Cursor cur = _cursor != null ? _cursor : _ctx.getContentResolver().query(_dbUri, null, null, null, sortOrder);

		if(cur == null)
		{
			Log.e(AppConfig.TAG_APP, TAG + "Null cursor returned for URI " + _dbUri.toString());
			throw new IOException("Null cursor returned for URI " + _dbUri.toString());
		}
		cur.moveToFirst();

		int numcols = cur.getColumnCount();

		if(_sortKey != null)
		{
			KeyColIndex = cur.getColumnIndex(_sortKey);
		}
		if(_exporter != null)
		{
			_exporter.startTable(_tablename, KeyColIndex, date);
		}

		cur.moveToFirst();

		// move through the table, creating rows
		// and adding each column with name and value
		// to the row
		List<String> list = new ArrayList<String>();
		if(cur.getPosition() < cur.getCount())
		{
			for (int idx = 0; idx < numcols; idx++)
			{
				list.add(cur.getColumnName(idx));
			}
		}
		_exporter.setRowsNames(list);

		while(cur.getPosition() < cur.getCount())
		{
			if(_exporter != null)
			{
				_exporter.startRow();
			}
			String name;
			String val;

			for (int idx = 0; idx < numcols; idx++)
			{
				name = "(unset)";
				val = "(unset)";
				try
				{
					name = cur.getColumnName(idx);

					try
					{
						val = new String(cur.getBlob(idx)).substring(0, 50);
						if(val.length() == 50)
						{
							val += "...";
						}
					}
					catch(Exception e)
					{
						val = cur.getString(idx);
					}
				}
				catch(Exception e)
				{

				}
				if(_exporter != null)
				{
					_exporter.addColumn(name, val);
				}

			}

			if(_exporter != null)
			{
				_exporter.endRow();
			}
			cur.moveToNext();
		}

		cur.close();

		if(_exporter != null)
		{
			_exporter.endTable();
		}
	}

	interface IExporter
	{
		void startDbExport(String string) throws IOException;

		void setRowsNames(List<String> list) throws IOException;

		void endTable() throws IOException;

		void endRow() throws IOException;

		void addColumn(String name, String val) throws IOException;

		void startRow() throws IOException;

		void startTable(String _tablename, int key, String date) throws IOException;

		void close() throws IOException;

		ByteArrayOutputStream endDbExport() throws IOException;
	}

	class Exporter implements IExporter
	{
		private static final String		CLOSING_WITH_TICK	= "'>";
		private static final String		START_DB			= "<export-database name='";
		private static final String		END_DB				= "</export-database>";
		private static final String		START_TABLE			= "<table name='";
		private static final String		END_TABLE			= "</table>";
		private static final String		START_ROW			= "<row>";
		private static final String		END_ROW				= "</row>";
		private static final String		START_COL			= "<col name='";
		private static final String		END_COL				= "</col>";

		private ByteArrayOutputStream	_bos;

		public Exporter() throws IOException
		{
			_bos = new ByteArrayOutputStream();
		}

		@Override
		public void close() throws IOException
		{
			if(_bos != null)
			{
				_bos.close();
			}
		}

		@Override
		public void startDbExport(String dbName) throws IOException
		{
			String stg = START_DB + dbName + CLOSING_WITH_TICK;
			_bos.write(stg.getBytes());
		}

		@Override
		public ByteArrayOutputStream endDbExport() throws IOException
		{
			_bos.write(END_DB.getBytes());
			return _bos;
		}

		@Override
		public void startTable(String tableName, int key, String date) throws IOException
		{
			String stg = START_TABLE + tableName + "'" + (key != -1 ? " key=\"" + key + "\"" : "") + " date=\"" + date + "\">";
			_bos.write(stg.getBytes());
		}

		@Override
		public void endTable() throws IOException
		{
			_bos.write(END_TABLE.getBytes());
		}

		@Override
		public void startRow() throws IOException
		{
			_bos.write(START_ROW.getBytes());
		}

		@Override
		public void endRow() throws IOException
		{
			_bos.write(END_ROW.getBytes());
		}

		@Override
		public void addColumn(String name, String val) throws IOException
		{
			String stg = START_COL + name + CLOSING_WITH_TICK + "<![CDATA[" + val + "]]>" + END_COL;
			_bos.write(stg.getBytes());
		}

		@Override
		public void setRowsNames(List<String> list) throws IOException
		{
			// TODO Auto-generated method stub

		}
	}

	class HtmlExporter implements IExporter
	{
		private ByteArrayOutputStream	_bos;

		public HtmlExporter()
		{
			_bos = new ByteArrayOutputStream();
		}

		@Override
		public void close() throws IOException
		{
			if(_bos != null)
			{
				_bos.close();
			}
		}

		@Override
		public void startDbExport(String dbName) throws IOException
		{
			String stg = "<h2>Table: " + dbName + "</h2>";
			_bos.write(stg.getBytes());
		}

		@Override
		public ByteArrayOutputStream endDbExport() throws IOException
		{
			//_bos.write(END_DB.getBytes());
			return _bos;
		}

		@Override
		public void startTable(String tableName, int key, String date) throws IOException
		{
			String stg = "<table border='1'>\n";
			_bos.write(stg.getBytes());
		}

		@Override
		public void endTable() throws IOException
		{
			String stg = "</table>";
			_bos.write(stg.getBytes());
		}

		@Override
		public void startRow() throws IOException
		{
			String stg = "<tr>";
			_bos.write(stg.getBytes());
		}

		@Override
		public void endRow() throws IOException
		{
			String stg = "</tr>\n";
			_bos.write(stg.getBytes());
		}

		@Override
		public void addColumn(String name, String val) throws IOException
		{
			String stg;
			stg = "<td>" + val + "</td>\n";
			_bos.write(stg.getBytes());
		}

		@Override
		public void setRowsNames(List<String> list) throws IOException
		{
			String stg = "<tr>";
			_bos.write(stg.getBytes());
			for (String colname : list)
			{
				stg = "<th>" + colname + "</th>\n";
				_bos.write(stg.getBytes());
			}

			stg = "</tr>\n";
			_bos.write(stg.getBytes());
		}
	}

}
