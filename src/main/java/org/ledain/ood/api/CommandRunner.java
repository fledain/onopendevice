package org.ledain.ood.api;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;

import android.content.Context;
import android.util.Log;

import org.ledain.ood.AppConfig;
import org.ledain.ood.tools.DatabaseRecorder;
import org.ledain.ood.tools.IoUtils;
import org.ledain.ood.tools.ToolsAdb;
import org.ledain.ood.tools.ToolsDb;
import org.ledain.ood.tools.ToolsVSConfig;
import org.ledain.ood.tools.DatabaseRecorder.Database;
import org.ledain.ood.tools.ToolsDb.RecordMode;

public class CommandRunner
{
	private static final String	TAG	= "CommandRunner - ";
	private Context				_context;

	public static class CommandResult
	{
		public String		cmd;
//		public File			file			= null;
//		public InputStream	is				= null;
		public Process		process			= null;
		public String		mimetype		= "text/html; charset=UTF-8";
		public boolean		downloadable	= false;
		public boolean		export			= false;
		public Runnable		runnable		= null;
		public OutputStream	outputstream	= null;
		public String		disposition		= null;
	}

	public CommandRunner(Context a_Context)
	{
		_context = a_Context;
	}

	public CommandResult runCommand(final String a_command, final Map<String, Object> a_params) throws IOException
	{
		final CommandResult result = new CommandResult();
		result.cmd = a_command;

		if(a_command.startsWith("/tools/db/listtable"))
		{
			final boolean bXML = "xml".equals(a_params.get("f"));
			result.mimetype = bXML ? "text/xml" : "text/json";
			result.downloadable = true;

			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						ToolsDb.listTable(_context, bXML, a_params, result.outputstream);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
		}
		else if(a_command.equals("/tools/db/list"))
		{
			result.downloadable = true;
			result.mimetype = "text/json";

			result.runnable = new Runnable()
			{

				@Override
				public void run()
				{
					try
					{
						ToolsDb.writeListOfTables(_context, ToolsDb.getListOfTables(_context), result.outputstream);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
		}
		else if(a_command.contains("/tools/record/start/"))
		{
			ToolsDb.startRecord(_context, a_command.substring(a_command.lastIndexOf("/") + 1), RecordMode.ONCHANGE);
		}
		else if(a_command.contains("/tools/record/stop/"))
		{
			ToolsDb.stopRecord(_context, a_command.substring(a_command.lastIndexOf("/") + 1));
		}
		else if(a_command.contains("/tools/record/toggle/"))
		{
			result.downloadable = true;
			result.mimetype = "text/json";

			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					ToolsDb.toggleRecord(_context, a_command.substring(a_command.lastIndexOf("/") + 1));
					try
					{
						ToolsDb.getRecordsInfos(result.outputstream);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
		}
		else if(a_command.endsWith("/tools/record/list"))
		{
			result.downloadable = true;
			result.mimetype = "text/json";

			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						ToolsDb.listRecords(_context, result.outputstream);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
		}
		else if(a_command.startsWith("/tools/record/content/"))
		{
			String[] items = a_command.split("/");
			if(items.length == 6)
			{
				final Database db = Database.valueOf(items[4]);
				final String name = items[5];
				result.downloadable = true;
				result.mimetype = "text/json";

				result.runnable = new Runnable()
				{

					@Override
					public void run()
					{
						try
						{
							IoUtils.copy(new FileInputStream(DatabaseRecorder.getRecordContent(_context, db, name)),
											result.outputstream);
						}
						catch(IOException e)
						{
							Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
							try
							{
								result.outputstream.close();
							}
							catch(IOException e1)
							{
							}
						}
					}
				};
			}
		}
		else if(a_command.startsWith("/tools/record/delete/"))
		{
			String[] items = a_command.split("/");
			if(items.length == 6)
			{
				Database db = Database.valueOf(items[4]);
				String name = items[5];
				boolean bOK = DatabaseRecorder.deleteRecordContent(_context, db, name);
				Log.d(AppConfig.TAG_APP, TAG + "delete database record db=" + db + ", name=" + name + " => "
								+ (bOK ? "OK" : "failed"));
				result.runnable = new Runnable()
				{

					@Override
					public void run()
					{
						try
						{
							ToolsDb.listRecords(_context, result.outputstream);
						}
						catch(IOException e)
						{
							Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
							try
							{
								result.outputstream.close();
							}
							catch(IOException e1)
							{
							}
						}
					}
				};
				result.downloadable = true;
				result.mimetype = "text/json";
			}
		}
		else if(a_command.startsWith("/tools/record/diff/"))
		{
			String[] items = a_command.split("/");
			if(items.length == 7)
			{
				final Database db = Database.valueOf(items[4]);
				final String name1 = items[5];
				final String name2 = items[6];
				result.downloadable = true;
				result.mimetype = "text/json";

				result.runnable = new Runnable()
				{
					@Override
					public void run()
					{
						try
						{
							IoUtils.copy(new FileInputStream(DatabaseRecorder.getRecordDiff(_context, db, name1, name2)),
											result.outputstream);
						}
						catch(IOException e)
						{
							Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
							try
							{
								result.outputstream.close();
							}
							catch(IOException e1)
							{
							}
						}
					}
				};
			}
		}
		else if(a_command.endsWith("/tools/record/infos"))
		{
			result.runnable = new Runnable()
			{

				@Override
				public void run()
				{
					try
					{
						ToolsDb.getRecordsInfos(result.outputstream);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
			result.downloadable = true;
		}
		else if(a_command.endsWith("/tools/vsconfig/get"))
		{
			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						new ToolsVSConfig(_context).getConfig(result.outputstream, 0);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
			result.downloadable = true;
			result.mimetype = "text/plain";
		}
		else if(a_command.endsWith("/tools/vsconfig/set"))
		{
			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						new ToolsVSConfig(_context).setConfig(result.outputstream, a_params.get("pkg").toString(),
										a_params.get("config"));
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
			result.downloadable = true;
			result.mimetype = "text/plain";
		}
		else if(a_command.endsWith("/tools/logcat"))
		{
			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						if("true".equals(a_params.get("clear")))
						{
							ToolsAdb.clearLogs(_context);
						}

						InputStream input = null;

						if("true".equals(a_params.get("reload")))
						{
							// if not existing, first create snapshot
							if(!ToolsAdb.readLogs(_context, true))
							{
								input = new ByteArrayInputStream(("\n\nError: please make sure to execute \"adb tcpip "
												+ AppConfig.ADB_TCPIP_PORT + "\"").getBytes());
							}
						}

						if(input == null && ToolsAdb.logcatFile(_context).exists())
						{
							input = new FileInputStream(ToolsAdb.logcatFile(_context));
						}

						if(input != null)
						{
							IoUtils.copy(input, result.outputstream);
						}
						else
						{
							result.outputstream.write(" ".getBytes());
							result.outputstream.close();
						}

					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};

			result.downloadable = true;
			result.mimetype = "text/plain";
		}
		else if(a_command.endsWith("/tools/logcat/export"))
		{
			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						InputStream input = null;
						// if not existing, first create snapshot
						if(!ToolsAdb.logcatFile(_context).exists())
						{
							if(!ToolsAdb.readLogs(_context, true))
							{
								input = new ByteArrayInputStream(("\n\nError: please make sure to execute \"adb tcpip "
												+ AppConfig.ADB_TCPIP_PORT + "\"").getBytes());
							}
						}

						if(input == null)
						{
							input = new FileInputStream(ToolsAdb.logcatFile(_context));
						}

						// if already exist, just resend
						IoUtils.copy(input, result.outputstream);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
			result.downloadable = true;
			result.mimetype = "application/octet-stream";
			result.disposition = "attachment; filename=\"ood-" + android.os.Build.MANUFACTURER.toLowerCase() + "-"
							+ android.os.Build.DEVICE + "-" + getDate() + "-logcat.log\"";
		}
		else if(a_command.endsWith("/tools/logcat/togglerec"))
		{
			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						ToolsAdb.toggleRecording(_context, result.outputstream);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
			result.downloadable = true;
			result.mimetype = "text/plain";
		}
		else if(a_command.endsWith("/tools/logcat/refreshrec"))
		{
			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						ToolsAdb.refreshRecording(_context, result.outputstream);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
			result.downloadable = true;
			result.mimetype = "text/plain";
		}
		else if(a_command.equals("/tools/query"))
		{
			result.mimetype = "text/json";
			result.downloadable = true;

			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						ToolsDb.query(_context, a_params, result.outputstream);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
		}
		else if(a_command.equals("/export"))
		{
			result.downloadable = false;
			result.export = true;
			result.mimetype = "application/ZIP";
			result.disposition = "attachment; filename=\"ood-" + android.os.Build.MANUFACTURER.toLowerCase() + "-"
							+ android.os.Build.DEVICE + "-" + getDate() + ".ZIP\"";
		}
		else if(a_command.equals("/device/screenshot"))
		{
			result.mimetype = "image/png";
			result.downloadable = true;

			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						if(a_params.containsKey("name"))
						{
							ToolsAdb.readScreenshot(_context, a_params.get("name").toString(), result.outputstream);
						}
						else
						{
							ToolsAdb.screenshot(_context, result.outputstream);
						}
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
		}
		else if(a_command.equals("/device/screenshotslist"))
		{
			result.mimetype = "text/json";
			result.downloadable = true;

			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						ToolsAdb.listScreenshots(_context, result.outputstream, 0);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
		}
		else if(a_command.equals("/device/screenshot/delete"))
		{
			result.mimetype = "text/json";
			result.downloadable = true;

			result.runnable = new Runnable()
			{
				@Override
				public void run()
				{
					try
					{

						ToolsAdb.deleteScreenshots(_context, result.outputstream, a_params);
					}
					catch(IOException e)
					{
						Log.e(AppConfig.TAG_APP, TAG + "Exception for command " + a_command, e);
						try
						{
							result.outputstream.close();
						}
						catch(IOException e1)
						{
						}
					}
				}
			};
		}

		return result;
	}

	public static String getDate()
	{
		Calendar cal = Calendar.getInstance(TimeZone.getDefault());
		String tab[] = { String.valueOf(cal.get(Calendar.YEAR)), String.valueOf(cal.get(Calendar.MONTH) + 1),
						String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), String.valueOf(cal.get(Calendar.HOUR_OF_DAY)),
						String.valueOf(cal.get(Calendar.MINUTE)), String.valueOf(cal.get(Calendar.SECOND)), };

		for (int i = 0; i < tab.length; i++)
		{
			if(tab[i].length() == 1) tab[i] = "0" + tab[i];
		}

		return tab[0] + tab[1] + tab[2] + "T" + tab[3] + tab[4] + tab[5];
	}

}
