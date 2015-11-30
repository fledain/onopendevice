package org.ledain.ood.api;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.ledain.ood.AppConfig;
import org.ledain.ood.tools.DatabaseRecorder;
import org.ledain.ood.tools.ToolsAccounts;

public class AppInfo
{
	private static final String	TAG	= "AppInfo - ";

	private Context				_context;
	private String				_deviceInfos;

	public AppInfo(Context a_Context)
	{
		_context = a_Context;

		// the process of identifying the default account can be delayed
		// Then initiate it now
		ToolsAccounts.identifyDefaultContactAccount(_context);
	}

	public String replace(String a_input) throws IOException
	{
		try
		{
			String s = a_input.replace("@@@device_infos@@@", getDeviceInfos()).replace("@@@accounts@@@", getAccounts())
							.replace("@@@applications@@@", getApplications()).replace("@@@connectivity@@@", getConnectivity())
							.replace("@@@device_title@@@", getDeviceTitle()).replace("@@@appversion@@@", getAppVersion())
							.replace("@@@telephony@@@", getTelephony()).replace("@@@miscinfos@@@", getMiscInfos());
			s = getIsRecorded(s);

			return s;
		}
		catch(Exception e)
		{
			throw new IOException(e.getMessage());
		}
	}

	private String getIsRecorded(String a_S)
	{
		for (DatabaseRecorder.Database db : DatabaseRecorder.Database.values())
		{
			a_S = a_S.replace("@@@isrecorded_" + db.toString() + "@@@", String.valueOf(DatabaseRecorder.isRecorded(db)));
		}

		return a_S;
	}

	private String getDeviceInfos() throws IllegalAccessException, IllegalArgumentException
	{
		if(_deviceInfos != null)
		{
			return _deviceInfos;
		}

		String result = "<table>";
		List<Field> list = new ArrayList<Field>();

		list.addAll(Arrays.asList(android.os.Build.VERSION.class.getDeclaredFields()));
		list.addAll(Arrays.asList(android.os.Build.class.getDeclaredFields()));

		for (Field field : list)
		{
			field.setAccessible(true);
			try
			{
				result += "<tr><td>" + field.getDeclaringClass().toString().substring(6).replace("$", ".") + "."
								+ field.getName() + "</td><td>" + field.get(null).toString() + "</td></tr>\n";
			}
			catch(IllegalAccessException e1)
			{
			}
			catch(IllegalArgumentException e1)
			{
			}
		}

		for (Entry<Object, Object> e : System.getProperties().entrySet())
		{
			result += "<tr><td>Property: " + e.getKey() + "</td><td>" + e.getValue() + "</td></tr>\n";
		}

		result += "<tr><td>Environment: getDataDirectory()</td><td>" + Environment.getDataDirectory() + "</td></tr>\n";
		result += "<tr><td>Environment: getDownloadCacheDirectory()</td><td>" + Environment.getDownloadCacheDirectory()
						+ "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStorageDirectory()" + "</td><td>"
						+ Environment.getExternalStorageDirectory() + "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStoragePublicDirectory(DIRECTORY_ALARMS)</td><td>"
						+ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_ALARMS) + "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStoragePublicDirectory(DIRECTORY_DCIM)</td><td>"
						+ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStoragePublicDirectory(DIRECTORY_DOWNLOAD)</td><td>"
						+ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStoragePublicDirectory(DIRECTORY_MOVIES)</td><td>"
						+ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStoragePublicDirectory(DIRECTORY_MUSIC)</td><td>"
						+ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStoragePublicDirectory(DIRECTORY_NOTIFICATIONS)</td><td>"
						+ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS) + "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStoragePublicDirectory(DIRECTORY_PICTURES)</td><td>"
						+ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStoragePublicDirectory(DIRECTORY_PODCASTS)</td><td>"
						+ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS) + "</td></tr>\n";
		result += "<tr><td>Environment: getExternalStoragePublicDirectory(DIRECTORY_RINGTONES)</td><td>"
						+ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES) + "</td></tr>\n";
		result += "<tr><td>Environment: getRootDirectory()</td><td>" + Environment.getRootDirectory() + "</td></tr>\n";

		result += "<tr><td>Context: getPackageResourcePath()</td><td>" + _context.getPackageResourcePath() + "</td></tr>\n";
		result += "<tr><td>Context: getCacheDir()</td><td>" + _context.getCacheDir() + "</td></tr>\n";
		result += "<tr><td>Context: getExternalCacheDir()</td><td>" + _context.getExternalCacheDir() + "</td></tr>\n";
		result += "<tr><td>Context: getExternalFilesDir(DIRECTORY_ALARMS)</td><td>"
						+ _context.getExternalFilesDir(Environment.DIRECTORY_ALARMS) + "</td></tr>\n";
		result += "<tr><td>Context: getExternalFilesDir(DIRECTORY_DCIM)</td><td>"
						+ _context.getExternalFilesDir(Environment.DIRECTORY_DCIM) + "</td></tr>\n";
		result += "<tr><td>Context: getExternalFilesDir(DIRECTORY_DOWNLOADS)</td><td>"
						+ _context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "</td></tr>\n";
		result += "<tr><td>Context: getExternalFilesDir(DIRECTORY_MOVIES)</td><td>"
						+ _context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) + "</td></tr>\n";
		result += "<tr><td>Context: getExternalFilesDir(DIRECTORY_MUSIC)</td><td>"
						+ _context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "</td></tr>\n";
		result += "<tr><td>Context: getExternalFilesDir(DIRECTORY_NOTIFICATIONS)</td><td>"
						+ _context.getExternalFilesDir(Environment.DIRECTORY_NOTIFICATIONS) + "</td></tr>\n";
		result += "<tr><td>Context: getExternalFilesDir(DIRECTORY_PICTURES)</td><td>"
						+ _context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "</td></tr>\n";
		result += "<tr><td>Context: getExternalFilesDir(DIRECTORY_PODCASTS)</td><td>"
						+ _context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) + "</td></tr>\n";
		result += "<tr><td>Context: getExternalFilesDir(DIRECTORY_RINGTONES)</td><td>"
						+ _context.getExternalFilesDir(Environment.DIRECTORY_RINGTONES) + "</td></tr>\n";

		result += "<tr><td>Context: getFilesDir()</td><td>" + _context.getFilesDir() + "</td></tr>\n";

		{
			for (Class<?> subclass : ContactsContract.CommonDataKinds.class.getDeclaredClasses())
			{
				Log.d(AppConfig.TAG_APP, TAG + "getDeviceInfos: looking at " + subclass.getName());
				if(subclass != null)
				{
					Method m = null;

					for (Method mm : subclass.getDeclaredMethods())
					{
						if(mm.getName().equals("getTypeLabelResource") || mm.getName().equals("getTypeResource"))
						{
							m = mm;
							break;
						}
					}

					if(m != null)
					{
						for (Field field : subclass.getDeclaredFields())
						{
							if(field.getName().startsWith("TYPE_"))
							{
								Object obj = field.get(null);
								if(obj instanceof Integer)
								{
									try
									{
										int type = (Integer) obj;
										int res = (Integer) m.invoke(null, type);

										result += "<tr><td>ContactsContract.CommonDataKinds."
														+ field.getDeclaringClass().getSimpleName() + "." + field.getName()
														+ "</td><td>" + _context.getString(res) + "</td></tr>\n";
									}
									catch(InvocationTargetException e1)
									{
										Log.e(AppConfig.TAG_APP, TAG + "getDeviceInfos: error", e1);
									}
								}
							}
						}
					}
				}
			}
		}

		result += "</table>";

		_deviceInfos = result;
		return result;
	}

	private String getDeviceTitle()
	{
		return android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " (" + android.os.Build.VERSION.RELEASE + ")";
	}

	private String getAccounts()
	{
		String result = "<table>";
		List<SyncAdapterType> unknownAdapters = new ArrayList<SyncAdapterType>();

		result += "<tr><th>Name</th><th>Type</th><th>Authority</th><th>Writable</th><th>Default</th></tr>\n";

		Account defaultContactsAccount = ToolsAccounts.identifyDefaultContactAccount(_context);

		// all online accounts defined by system & user.
		Account[] deviceAccounts = AccountManager.get(_context).getAccounts();

		List<Account> listAccounts = new ArrayList<Account>();
		for (Account account : deviceAccounts)
		{
			if(defaultContactsAccount == null || !account.equals(defaultContactsAccount))
			{
				listAccounts.add(account);
			}
		}

		if(defaultContactsAccount != null)
		{
			listAccounts.add(0, defaultContactsAccount);
		}

		for (SyncAdapterType syncAdapterType : ContentResolver.getSyncAdapterTypes())
		{
			unknownAdapters.add(syncAdapterType);
		}

		boolean bEven = true;
		// walk through all user online accounts, and keep contacts sync accounts
		for (Account account : listAccounts)
		{
			boolean bFound = false;

			// search SyncAdapter details
			for (SyncAdapterType syncAdapterType : ContentResolver.getSyncAdapterTypes())
			{
				String defaultAuth = "";

				if(syncAdapterType.accountType.equals(account.type))
				{
					unknownAdapters.remove(syncAdapterType);

					bFound = true;
					// match: this account has contact authority.
					boolean bWritable = syncAdapterType.supportsUploading();

					String rowClass = "account" + (bEven ? "Even" : "Odd");
					if(account.equals(defaultContactsAccount) && ContactsContract.AUTHORITY.equals(syncAdapterType.authority))
					{
						rowClass = "accountDefault";
						defaultAuth = "contacts (syncadapter)";
					}
					result += "<tr class='" + rowClass + "'>";
					result += "<td>" + account.name + "</td><td>" + account.type + "</td><td>" + syncAdapterType.authority
									+ "</td><td>" + bWritable + "</td><td>" + defaultAuth + "</td></tr>\n";
				}
			}

			if(!bFound)
			{
				if(account.equals(defaultContactsAccount))
				{
					result += "<tr class='accountDefault'>";
					result += "<td>" + account.name + "</td><td>" + account.type + "</td><td>" + ContactsContract.AUTHORITY
									+ "</td><td>true</td><td>contacts (no syncadapter)</td></tr>\n";
				}
				else
				{
					String rowClass = "account" + (bEven ? "Even" : "Odd");
					result += "<tr class='" + rowClass + "'>";
					result += "<td>" + account.name + "</td><td>" + account.type
									+ "</td><td>unknown</td><td>unknown</td><td></td></tr>\n";
				}
			}

			bEven = !bEven;
		}

		for (SyncAdapterType syncAdapterType : unknownAdapters)
		{
			String rowClass = "account" + (bEven ? "Even" : "Odd");
			result += "<tr class='" + rowClass + "'>";
			result += "<td>(unknown)</td><td>" + syncAdapterType.accountType + "</td><td>" + syncAdapterType.authority
							+ "</td><td>" + syncAdapterType.supportsUploading() + "</td><td></td></tr>\n";
		}

		result += "</table>";

		return result;
	}

	private String getApplications()
	{
		String result = "<table>";
		ActivityManager am = (ActivityManager) _context.getSystemService(Context.ACTIVITY_SERVICE);

		result += "<tr>";
		result += "<th>Importance</th>";
		result += "<th>ImportanceReasonCode</th>";
		result += "<th>ImportanceReasonPid</th>";
		result += "<th>ImportanceReasonComponent</th>";
//		result += "<th>Last Trim Level</th>";
		result += "<th>LRU</th>";
		result += "<th>PID</th>";
		result += "<th>Package list</th>";
		result += "<th>Process name</th>";
		result += "<th>UID</th>";
		result += "</tr>\n";
		for (RunningAppProcessInfo appInfo : am.getRunningAppProcesses())
		{
			result += "<tr>";
			result += "<td>" + getImportance(appInfo.importance) + "</td>\n";
			result += "<td>" + appInfo.importanceReasonCode + "</td>\n";
			result += "<td>" + appInfo.importanceReasonPid + "</td>\n";
			result += "<td>"
							+ (appInfo.importanceReasonComponent == null ? "" : appInfo.importanceReasonComponent
											.flattenToString()) + "</td>\n";
//			result += "<td>" + appInfo.lastTrimLevel + "</td>\n";
			result += "<td>" + appInfo.lru + "</td>\n";
			result += "<td>" + appInfo.pid + "</td>\n";
			result += "<td>" + Arrays.toString(appInfo.pkgList) + "</td>\n";
			result += "<td>" + appInfo.processName + "</td>\n";
			result += "<td>" + appInfo.uid + "</td>";
			result += "</tr>\n";
		}

		result += "</table>";

		return result;
	}

	private String getImportance(int importance)
	{
		switch(importance)
		{
			case RunningAppProcessInfo.IMPORTANCE_BACKGROUND:
				return "BACKGROUND";
			case RunningAppProcessInfo.IMPORTANCE_EMPTY:
				return "EMPTY";
			case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
				return "FOREGROUND";
			case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
				return "PERCEPTIBLE";
			case RunningAppProcessInfo.IMPORTANCE_SERVICE:
				return "SERVICE";
			case RunningAppProcessInfo.IMPORTANCE_VISIBLE:
				return "VISIBLE";
		}
		return "";
	}

	private String getConnectivity()
	{
		ConnectivityManager conn = (ConnectivityManager) _context.getSystemService(Context.CONNECTIVITY_SERVICE);

		String result = "<table>";
		result += "<tr>";
		result += "<th>DetailedState</th>";
		result += "<th>ExtraInfo</th>";
		result += "<th>Reason</th>";
		result += "<th>State</th>";
		result += "<th>Subtype</th>";
		result += "<th>Subtype name</th>";
		result += "<th>Type</th>";
		result += "<th>Type name</th>";
		result += "<th>Is connected</th>";
		result += "<th>Is connected or connecting</th>";
		result += "<th>Is failover</th>";
		result += "<th>Is roaming</th>";
		result += "</tr>\n";

		for (NetworkInfo info : conn.getAllNetworkInfo())
		{
			result += "<tr>";
			result += "<td>" + info.getDetailedState() + "</td>";
			result += "<td>" + info.getExtraInfo() + "</td>";
			result += "<td>" + info.getReason() + "</td>";
			result += "<td>" + info.getState() + "</td>";
			result += "<td>" + info.getSubtype() + "</td>";
			result += "<td>" + info.getSubtypeName() + "</td>";
			result += "<td>" + info.getType() + "</td>";
			result += "<td>" + info.getTypeName() + "</td>";
			result += "<td>" + info.isConnected() + "</td>";
			result += "<td>" + info.isConnectedOrConnecting() + "</td>";
			result += "<td>" + info.isFailover() + "</td>";
			result += "<td>" + info.isRoaming() + "</td>";
			result += "</tr>\n";
		}
		result += "</table>";

		return result;
	}

	private String getLogs()
	{
		String result = "";
		BufferedInputStream is = null;

		try
		{
			is = new BufferedInputStream(Runtime.getRuntime().exec("logcat -d -v threadtime").getInputStream());
			byte[] array = new byte[1024];

			int n = is.read(array, 0, array.length);
			while(n >= 0)
			{
				if(n > 0)
				{
					result += (new String(array, 0, n, "UTF-8").replaceAll("\n", "<br/>"));
					n = is.read(array);
				}
				else
				{
					Thread.sleep(1000);
				}
			}

		}
		catch(IOException e)
		{
		}
		catch(InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			if(is != null)
			{
				try
				{
					is.close();
				}
				catch(IOException e)
				{
				}
			}
		}
		return result;
	}

	private String getAppVersion()
	{
		try
		{
			return "v" + _context.getPackageManager().getPackageInfo(_context.getPackageName(), 0).versionName;
		}
		catch(NameNotFoundException e)
		{
			return "";
		}
	}

	private String getTelephony()
	{
		TelephonyManager telManager = (TelephonyManager) _context.getSystemService(Context.TELEPHONY_SERVICE);

		if(telManager == null)
		{
			return "No telephony support.";
		}

		String result = "<table>";
		for (Method method : TelephonyManager.class.getMethods())
		{
			String rettype = method.getGenericReturnType().toString().toLowerCase();

			if(method.getGenericParameterTypes().length == 0
							&& (rettype.equals("boolean") || rettype.equals("int") || rettype.equals("long") || rettype
											.equals("class java.lang.string")))
			{
				if(method.getName().startsWith("get") || method.getName().startsWith("is")
								|| method.getName().startsWith("has"))
				{
					Object ret = null;
					try
					{
						ret = method.invoke(telManager, null);
					}
					catch(IllegalAccessException e)
					{
						ret = "(IllegalAccessException error: " + e.getMessage() + ")";
					}
					catch(IllegalArgumentException e)
					{
						ret = "(IllegalArgumentException error: " + e.getMessage() + ")";
					}
					catch(InvocationTargetException e)
					{
						ret = "(InvocationTargetException error: " + e.getMessage() + ")";
					}
					catch(Exception e)
					{
						ret = "(Exception error: " + e.getMessage() + ")";
					}

					if(ret == null)
					{
						ret = "null";
					}

					result += "<tr><td>" + method.getName() + "</td><td>" + ret.toString() + "</td></tr>\r\n";
				}
			}
		}
		result += "</table>";

		return result;
	}

	private String getMiscInfos()
	{
		String result = "<table>";

		// admins
		{
			DevicePolicyManager dPM = (DevicePolicyManager) _context.getSystemService(Context.DEVICE_POLICY_SERVICE);
			String admins = "";

			if(dPM == null)
			{
				admins = "<li>No Device Policy Manager support.";
			}
			else if(dPM.getActiveAdmins() == null || dPM.getActiveAdmins().size() == 0)
			{
				admins = "<li>no active device admins.";
			}
			else
			{
				for (ComponentName name : dPM.getActiveAdmins())
				{
					admins += "\r\n<li>" + name.toShortString();
				}
			}
			result += "\r\n<tr><td>List of device admin</td><td><ul>" + admins + "</ul></td></tr>\r\n";
		}

		{
			result += "\r\n<tr><td>Android ID</td><td><ul><li>"
							+ Secure.getString(_context.getContentResolver(), Secure.ANDROID_ID) + "</ul></td></tr>\r\n";
		}

		{
			result += "\r\n<tr><td>Configuration</td><td><ul>";

			Configuration config = _context.getResources().getConfiguration();
			for (Field field : Configuration.class.getDeclaredFields())
			{
				try
				{
					// discard constants
					if(field.get(config) != null && !field.getName().toUpperCase().equals(field.getName()))
					{
						result += "\r\n<li>" + field.getName() + ": " + field.get(config).toString();
					}
				}
				catch(IllegalAccessException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				catch(IllegalArgumentException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			result += "</ul></td></tr>\r\n";

		}

		result += "</table>";

		return result;
	}
}
