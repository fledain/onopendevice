package org.ledain.ood.webserver;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import android.content.Context;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.ledain.ood.AppConfig;
import org.ledain.ood.api.AppInfo;
import org.ledain.ood.api.CommandRunner;
import org.ledain.ood.api.CommandRunner.CommandResult;
import org.ledain.ood.tools.IoUtils;
import org.ledain.ood.tools.IoUtils.Encoding;
import org.ledain.ood.tools.ToolsAdb;
import org.ledain.ood.tools.ToolsDb;
import org.ledain.ood.tools.ToolsDb.Table;
import org.ledain.ood.tools.ToolsDb.Tables;
import org.ledain.ood.tools.ToolsVSConfig;

public class ApiCommandHandler implements HttpRequestHandler
{
	private static final String	TAG	= "ApiCommandHandler - ";

	protected enum ContentEncoding
	{
		IDENTITY, GZIP, ZIP
	};

	private Context			_context	= null;
	private CommandRunner _cmdrunner;
	private AppInfo _appInfos;

	public ApiCommandHandler(Context context)
	{
		this._context = context;

		_cmdrunner = new CommandRunner(context);
		_appInfos = new AppInfo(context);
	}

	@Override
	public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws HttpException, IOException
	{
		String action = request.getRequestLine().getUri();

		if(action == null)
		{
			Log.e(AppConfig.TAG_APP, TAG + "API request not found: " + action);
			response.setStatusCode(404);
			return;
		}

		if(action.contains("?"))
		{
			action = action.substring(0, action.indexOf("?"));
		}
		Map<String, Object> params = getRequestParams(request);

		Log.d(AppConfig.TAG_APP, TAG + "API serving: " + action);

		final ContentEncoding ce;
		final CommandResult cmdResult = _cmdrunner.runCommand(action, params);
		if(cmdResult.export)
		{
			ce = ContentEncoding.ZIP;
		}
		else if(cmdResult.mimetype.startsWith("image/"))
		{
			ce = ContentEncoding.IDENTITY;
		}
		else
		{
			Header[] headers = request.getHeaders("Accept-Encoding");
			if(headers == null || headers.length == 0)
			{
				ce = ContentEncoding.IDENTITY;
			}
			else
			{
				boolean bFound = false;
				for (Header h : headers)
				{
					if(h.getValue().contains("gzip"))
					{
						bFound = true;
						break;
					}
				}
				ce = bFound ? ContentEncoding.GZIP : ContentEncoding.IDENTITY;
			}
		}

		HttpEntity entity = new EntityTemplate(new ContentProducer()
		{
			@Override
			public void writeTo(final OutputStream outstream) throws IOException
			{

				if(cmdResult.downloadable)
				{
					// runnable is mandatory
					sendDownloadable(outstream, cmdResult, ce);
				}
				else if(cmdResult.export)
				{
					sendIndex_zip(outstream, cmdResult);
				}
				else
				{
					sendIndex(outstream, cmdResult, ce);
				}
			}
		});

		if(!TextUtils.isEmpty(cmdResult.disposition))
		{
			response.setHeader("Content-Disposition", cmdResult.disposition);
		}
		if(!TextUtils.isEmpty(cmdResult.mimetype))
		{
			response.setHeader("Content-Type", cmdResult.mimetype);
		}
		if(ce == ContentEncoding.GZIP)
		{
			response.setHeader("Content-Encoding", "gzip");
		}

		if(cmdResult.export)
		{
			/*
			response.setHeader("Content-Type", "application/octet-stream");
			//response.setHeader("Content-Transfer-Encoding", "binary");
			response.setHeader("Content-Disposition", "attachment; filename=\"ood.ZIP\"");
			*/
		}

		response.setEntity(entity);
	}

	private void sendIndex(final OutputStream outstream, final CommandResult cmdResult, ContentEncoding ce)
					throws UnsupportedEncodingException, IOException
	{
		final OutputStream ostream;

		if(ce == ContentEncoding.GZIP)
		{
			ostream = new GZIPOutputStream(outstream);
		}
		else
		{
			ostream = outstream;
		}

		ostream.write(customResult(ostream, cmdResult).getBytes());

		Log.d(AppConfig.TAG_APP, TAG + "API: serving index");

		ostream.flush();
		if(ce == ContentEncoding.GZIP)
		{
			ostream.close();
		}

	}

	private void sendIndex_zip(final OutputStream outstream, final CommandResult cmdResult)
					throws UnsupportedEncodingException, IOException
	{
		final ZipOutputStream zos = new ZipOutputStream(outstream);

		Log.d(AppConfig.TAG_APP, TAG + "API: serving index");

		ZipEntry entry = new ZipEntry("index.html");
		zos.putNextEntry(entry);
		String trailer = customResult(zos, cmdResult);

		// export data
		exportData(zos);

		// write trailer
		zos.write(trailer.getBytes());

		zos.closeEntry();

		zos.close();
	}

	private byte[] readAsset(String a_file)
	{
		byte[] buffer = null;

		try
		{
			InputStream is = _context.getAssets().open(a_file);
			BufferedInputStream bis = new BufferedInputStream(is);
			int n = bis.available();
			if(n >= 0)
			{
				buffer = new byte[n];
				bis.read(buffer);
			}
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "readAsset: error reading asset " + a_file);
		}
		return buffer;
	}

	private void sendDownloadable(final OutputStream outstream, final CommandResult cmdResult, ContentEncoding ce)
					throws IOException
	{
		if(cmdResult.runnable != null)
		{
			final OutputStream ostream;

			if(ce == ContentEncoding.GZIP)
			{
				ostream = new GZIPOutputStream(outstream);
			}
			else
			{
				ostream = outstream;
			}

			cmdResult.outputstream = ostream;
			cmdResult.runnable.run();

			if(ce == ContentEncoding.GZIP)
			{
				ostream.close();
			}
		}
		else
		{
			outstream.close();
		}
		if(cmdResult.process != null)
		{
			cmdResult.process.destroy();
			cmdResult.process = null;
		}
	}

	public Context getContext()
	{
		return _context;
	}

	private String customResult(OutputStream outstream, CommandResult a_cmdResult) throws IOException
	{
		String result = new String(readAsset("index.html"));

		// find trailer
		int indexTrailer = result.lastIndexOf("</body>");
		String trailer = result.substring(indexTrailer);

		// write all except trailer
		result = result.substring(0, indexTrailer);

		outstream.write(_appInfos.replace(result).getBytes());
/*
		if(a_cmdResult != null && a_cmdResult.result != null && a_cmdResult.result.length() > 0)
		{
			outstream.write("\r\n  <script language='JavaScript'>\r\n".getBytes());
			outstream.write("g_PageResult = \"".getBytes());
			outstream.write(new SpannableString(a_cmdResult.result).toString().getBytes());
			outstream.write("\"".getBytes());
			outstream.write("\r\n</script>".getBytes());
		}
*/

		return trailer;
	}

	private static Map<String, Object> getRequestParams(HttpRequest a_request) throws IOException
	{
		Map<String, Object> params;

		if(a_request instanceof BasicHttpEntityEnclosingRequest)
		{
			params = readJSonBody(a_request);
		}
		else
		{
			params = new HashMap<String, Object>();

			String action = a_request.getRequestLine().getUri();
			int indexOfSQ = action.indexOf("?");
			if(indexOfSQ != -1)
			{
				String[] qs = action.substring(indexOfSQ + 1).split("&");
				for (String param : qs)
				{
					String[] pair = param.split("=");
					params.put(pair[0], pair[1]);
				}
			}
		}

		return params;
	}

	private static Map<String, Object> readJSonBody(HttpRequest a_request) throws IOException
	{
		JsonReader reader = null;
		try
		{
			reader = new JsonReader(new InputStreamReader(((BasicHttpEntityEnclosingRequest) a_request).getEntity()
							.getContent()));
			return (Map<String, Object>) readJSON(reader).get("root");
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "query: error reading parameters.", e);
			return null;
		}
		finally
		{
			if(reader != null)
			{
				reader.close();
			}
		}
	}

	private static Map<String, Object> readJSON(JsonReader reader) throws IOException
	{
		String name = "root";
		Map<String, Object> result = new HashMap<String, Object>();
		List<Object> array = null;

		try
		{
			while(true)
			{
				JsonToken token = reader.peek();
				Log.d(AppConfig.TAG_APP, TAG + "readJSON: token " + token.name());
				switch(token)
				{
					case NAME:
						name = reader.nextName();
						break;

					case NUMBER:
					case STRING:
					case BOOLEAN:
						String value = token == JsonToken.BOOLEAN ? Boolean.toString(reader.nextBoolean()) : reader
										.nextString();
						if(array != null)
						{
							array.add(value);
						}
						else
						{
							result.put(name, value);
						}
						break;

					case NULL:
						Log.e(AppConfig.TAG_APP, TAG + "readJSON: unexpected NULL value.");
						break;

					case BEGIN_ARRAY:
						reader.beginArray();
						array = new ArrayList<Object>();
						break;

					case END_ARRAY:
						reader.endArray();
						result.put(name, array);
						array = null;
						break;

					case BEGIN_OBJECT:
						reader.beginObject();
						//array.get(iArray).put(name, readJSON(reader));
						result.put(name, readJSON(reader));
						//return result;
						break;

					case END_OBJECT:
						reader.endObject();
						return result;

					case END_DOCUMENT:
						return result;
				}
			}
		}
		catch(Exception e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "readJSON: unexpected exception", e);
			return result;
		}

	}

	private void readTextAsset(OutputStream outstream, String a_name)
	{
		try
		{
			IoUtils.copy(_context.getAssets().open(a_name), outstream);
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "readTextAsset: failed to read " + a_name, e);
		}
	}

	private void exportData(OutputStream outstream) throws IOException
	{
		CommandResult command = new CommandResult();
		Map<String, Object> params = new HashMap<String, Object>();
		String uri;

		outstream.write("\r\n	<style type='text/css'>".getBytes());
		readTextAsset(outstream, "app/dtree.css");
		readTextAsset(outstream, "app/tabs.css");
		readTextAsset(outstream, "app/main.css");
		outstream.write("</style>".getBytes());

		outstream.write("\r\n  <script language='JavaScript'>\r\n".getBytes());
		readTextAsset(outstream, "app/tabs.js");
		readTextAsset(outstream, "app/dtree.js");
		readTextAsset(outstream, "app/main.js");
		readTextAsset(outstream, "app/datetimepicker_css.js");

		outstream.write("\r\n\r\n".getBytes());

		for (String file : new String[] { "base.gif", "cd.gif", "database.jpg", "diff.png", "empty.gif", "error.png",
						"folder.gif", "folderopen.gif", "globe.gif", "icon-ok.png", "imgfolder.gif", "join.gif",
						"joinbottom.gif", "line.gif", "logo.png", "minus.gif", "minusbottom.gif", "musicfolder.gif",
						"nolines_minus.gif", "nolines_plus.gif", "page.gif", "plus.gif", "plusbottom.gif", "question.gif",
						"trash.gif", "warning.png", "xml.gif", "cal.gif", "cal_close.gif", "cal_fastforward.gif",
						"cal_fastreverse.gif", "cal_forward.gif", "cal_minus.gif", "cal_plus.gif", "cal_reverse.gif" })
		{
			exportImage(outstream, "app/img/" + file);
		}

		uri = "logcat";
		outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":\"").getBytes());
		try
		{
			if(!ToolsAdb.logcatFile(_context).exists())
			{
				ToolsAdb.readLogs(_context, true);
			}
			if(!ToolsAdb.logcatFile(_context).exists())
			{
				IoUtils.copy(new FileInputStream(ToolsAdb.logcatFile(_context)), outstream, Encoding.HTML);
			}
		}
		catch(Exception e)
		{

		}
		outstream.write("\" } );\r\n ".getBytes());

		List<Tables> tables = ToolsDb.getListOfTables(_context);
		uri = "listofdatabases";
		outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":").getBytes());
		ToolsDb.writeListOfTables(_context, tables, outstream);
		outstream.write(" } );\r\n ".getBytes());

		// export tables
		{
			for (Tables tt : tables)
			{
				if(tt.tables != null)
				{
					for (Entry<String, List<Table>> e : tt.tables.entrySet())
					{
						for (Table table : e.getValue())
						{
							if(table.toExport)
							{
								uri = table.uri;
								outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":").getBytes());
								params.put("uri", uri);
								ToolsDb.listTable(_context, false, params, outstream);
								outstream.write(" } );\r\n ".getBytes());
							}
						}
					}
				}
			}

			uri = ContactsContract.RawContacts.CONTENT_URI.toString();
			outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":").getBytes());
			params.put("uri", uri);
			ToolsDb.listTable(_context, false, params, outstream);
			outstream.write(" } );\r\n ".getBytes());

			uri = ContactsContract.Data.CONTENT_URI.toString();
			outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":").getBytes());
			params.put("uri", uri);
			ToolsDb.listTable(_context, false, params, outstream);
			outstream.write(" } );\r\n ".getBytes());

			uri = ContactsContract.Settings.CONTENT_URI.toString();
			outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":").getBytes());
			params.put("uri", uri);
			ToolsDb.listTable(_context, false, params, outstream);
			outstream.write(" } );\r\n ".getBytes());

		}

		uri = "listofsettings";
		outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":").getBytes());
		ToolsVSConfig vsconfig = new ToolsVSConfig(_context);
		vsconfig.getConfig(outstream, 0);
		outstream.write(" } );\r\n ".getBytes());

		uri = "listofscreenshots";
		outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":").getBytes());
		ToolsAdb.listScreenshots(_context, outstream, 0);
		outstream.write(" } );\r\n ".getBytes());
		for (String ss : ToolsAdb.listScreenshots(_context))
		{
			exportScreenshot(outstream, ss);
		}

		outstream.write("\r\n</script>".getBytes());
	}

	private void exportImage(OutputStream outstream, String a_file) throws IOException
	{
		String uri = a_file;
		String ext = a_file.substring(a_file.lastIndexOf(".") + 1);
		outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":\"").getBytes());
		outstream.write(("data:image/" + ext + ";base64,").getBytes());
		IoUtils.copy(_context.getAssets().open(a_file), outstream, Encoding.BASE64);
		outstream.write("\" } );\r\n ".getBytes());
	}

	private void exportScreenshot(OutputStream outstream, String a_name) throws IOException
	{
		String uri = "device/screenshot?name=" + a_name;
		String ext = a_name.substring(a_name.lastIndexOf(".") + 1);

		outstream.write(("addExportedData( { \"uri\":\"" + uri + "\", \"results\":\"").getBytes());
		outstream.write(("data:image/" + ext + ";base64,").getBytes());
		IoUtils.copy(new FileInputStream(new File(ToolsAdb.screenshotDir(_context), a_name)), outstream, Encoding.BASE64);
		outstream.write("\" } );\r\n ".getBytes());
	}

	private String fileToString(File a_file)
	{
		InputStream is = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream((int) a_file.length());

		try
		{
			byte[] array = new byte[1024];
			is = new FileInputStream(a_file);

			int n = is.read(array);
			while(n >= 0)
			{
				if(n > 0)
				{
					Log.v(AppConfig.TAG_APP, TAG + "API: serving " + n + " bytes...");
					String s = new String(array, 0, n, "UTF-8");
					baos.write(s.getBytes("UTF-8"));
					n = is.read(array);
					Log.v(AppConfig.TAG_APP, TAG + "API: next avail = " + n + " bytes...");
					baos.flush();
				}
				else
				{
					Log.w(AppConfig.TAG_APP, TAG + "API: waiting for serving...");
					Thread.sleep(1000);
				}
			}
		}
		catch(InterruptedException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "fileToString: interruptedException", e);
		}
		catch(IOException e)
		{
			Log.e(AppConfig.TAG_APP, TAG + "fileToString: IOException", e);
		}
		finally
		{
			a_file.delete();
			a_file = null;
			if(is != null)
			{
				try
				{
					is.close();
				}
				catch(IOException e)
				{
				}
				is = null;
			}
		}

		return baos.toString();
	}
}
