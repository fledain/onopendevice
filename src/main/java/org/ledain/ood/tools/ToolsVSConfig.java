package org.ledain.ood.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.ResultReceiver;
import android.util.Log;

import com.google.gson.stream.JsonReader;
import org.ledain.ood.AppConfig;

public class ToolsVSConfig
{
	private static final String	TAG								= "ToolsVSConfig - ";

	public static final String	ACTION_CONFIG_SET_SETTINGS		= "ConfigManager.setsettings";
	public static final String	ACTION_CONFIG_GET_SETTINGS		= "ConfigManager.getsettings";

	public static final String	PARAM_VOXCONFIG					= "voxconfig";					// result of getconfig
	public static final String	PARAM_VOXSETCONFIG				= "voxsetconfig";				// ask to change config
	public static final String	PARAM_LOGIN						= "login";
	public static final String	PARAM_PASSWORD					= "password";
	public static final String	PARAM_API_MAIN_URL				= "apimainurl";
	public static final String	PARAM_SYNC_URL					= "syncurl";
	public static final String	PARAM_MSISDN					= "msisdn";
	public static final String	PARAM_SYNC_HISTORY_SIZE			= "synchistorysize";
	public static final String	PARAM_SYNC_OVER_WIFI			= "syncoverwifi";
	public static final String	PARAM_SYNC_WHILE_ROAMING		= "syncwhileroaming";
	public static final String	PARAM_FORCE_SLOW_SYNC			= "forceslowsync";
	public static final String	PARAM_ENABLE_DEBUG_LOGS			= "enabledebuglogs";
	public static final String	PARAM_ENABLE_HTTP_PACKETS_LOGS	= "enablewbxmllogs";
	public static final String	EXTRA_RESULT_RECEIVER			= "EXTRA_RESULT_RECEIVER";

	private String				_sPkg;
	private Context				_context;

	private static class Pair
	{
		public String	pkg;

		public Pair(String a_pkg)
		{
			pkg = a_pkg;
		}
	}

	private class MyResultReceiver extends ResultReceiver
	{
		final private Object	_lock;
		private Bundle			_result;

		public MyResultReceiver(final Object a_Lock)
		{
			super(new Handler(_context.getMainLooper()));
			_lock = a_Lock;
		}

		public Bundle getResult()
		{
			return _result;
		}

		public ResultReceiver getNativeResultReceiver()
		{
			// converts the instance with class signature linked to this application
			// to an instance with native class (ie. android.os.ResultReceiver).
			// As a result, no issue will occur when remote application will unmarshall 
			// intent bundle params.
			Parcel parcel = Parcel.obtain();
			writeToParcel(parcel, 0);
			parcel.setDataPosition(0);
			ResultReceiver nativeObject = ResultReceiver.CREATOR.createFromParcel(parcel);
			parcel.recycle();
			return nativeObject;
		}

		@Override
		protected void onReceiveResult(int a_ResultCode, Bundle a_ResultData)
		{
			Log.d(AppConfig.TAG_APP, TAG + "readConfig#onReceiveResult: before lock");
			_result = a_ResultData;
			if(_result == null)
			{
				// some results may be null
				_result = new Bundle();
			}

			if(_lock != null)
			{
				synchronized(_lock)
				{
					Log.d(AppConfig.TAG_APP, TAG + "readConfig#onReceiveResult: before notify");
					_lock.notify();
					Log.d(AppConfig.TAG_APP, TAG + "readConfig#onReceiveResult: after notify");
				}
			}
		}
	}

	public static final Pair[]	DEFAULT_PRODUCTS	= { new Pair("org.ledain.ood") };

	public ToolsVSConfig(Context a_Context)
	{
		_context = a_Context;
	}

	public void getConfig(OutputStream outstream, int a_code) throws IOException
	{
		List<String> list = getPackagesList();
		outstream.write((" { \"code\":" + a_code + ", \"products\": [ ").getBytes());
		boolean bFirst = true;

		for (String product : list)
		{
			outstream.write(((bFirst ? "" : ",") + "\n { \"pkg\":\"" + product + "\" ").getBytes());

			// product details
			{
				// read settings from the settings
				ConfigDeviceReader configReader = new ConfigDeviceReader();
				Map<String, String> values = configReader.readConfig(product);

				outstream.write(", \"config\": [ ".getBytes());
				boolean bFirstEntry = true;
				for (Entry<String, String> e : values.entrySet())
				{
					String key = e.getKey();
					String type = "text";
					if(key.equals(PARAM_SYNC_OVER_WIFI) || key.equals(PARAM_SYNC_WHILE_ROAMING)
									|| key.equals(PARAM_FORCE_SLOW_SYNC) || key.equals(PARAM_ENABLE_DEBUG_LOGS)
									|| key.equals(PARAM_ENABLE_HTTP_PACKETS_LOGS))
					{
						type = "boolean";
					}

					outstream.write(((bFirstEntry ? "" : ",") + "\n { \"type\":\"" + type + "\", \"name\":\"" + e.getKey()
									+ "\", \"value\":\"" + e.getValue() + "\" } ").getBytes());
					bFirstEntry = false;
				}
				outstream.write(" ] ".getBytes());
			}
			bFirst = false;

			outstream.write(" } ".getBytes());
		}
		outstream.write(" ] } ".getBytes());
	}

	public void setConfig(OutputStream outstream, String a_Pkg, Object a_Config) throws IOException
	{
		final Object lock = new Object();

		String values = jsonToVS(a_Config);

		MyResultReceiver receiver = new MyResultReceiver(lock);
		synchronized(lock)
		{
			setSettings(a_Pkg, values, receiver.getNativeResultReceiver());
			Thread.yield();

			try
			{
				Log.d(AppConfig.TAG_APP, TAG + "setConfig: before wait");
				lock.wait(2000);
				Log.d(AppConfig.TAG_APP, TAG + "setConfig: after wait");
			}
			catch(InterruptedException e)
			{
				Log.e(AppConfig.TAG_APP, TAG + "setConfig exception", e);
			}
		}

		final int code;
		if(receiver.getResult() != null)
		{
			Log.d(AppConfig.TAG_APP, TAG + "setConfig: return OK");
			code = 0;
		}
		else
		{
			Log.w(AppConfig.TAG_APP, TAG + "setConfig: failed.");
			code = 1;
		}

		getConfig(outstream, code);
	}

	private static Pair getPair(String a_pkg)
	{
		for (Pair element : DEFAULT_PRODUCTS)
		{
			if(a_pkg.equalsIgnoreCase(element.pkg))
			{
				return element;
			}
		}

		return null;
	}

	private List<String> getPackagesList()
	{
		List<String> list = new ArrayList<String>();

		PackageManager pacakgeManager = _context.getPackageManager();
		List<PackageInfo> packsInfo = pacakgeManager.getInstalledPackages(0);
		if(packsInfo != null)
		{
			for (PackageInfo packInfo : packsInfo)
			{
				if(getPair(packInfo.packageName) != null)
				{
					list.add(packInfo.packageName);
				}
			}
		}

		return list;
	}

	private String jsonToVS(Object a_JSON)
	{
		String config = "";
		JsonReader reader = null;

		if(!(a_JSON instanceof Map))
		{
			return null;
		}

		Map<String, Object> values = (Map<String, Object>) a_JSON;

		for (Entry<String, Object> e : values.entrySet())
		{
			config += "|#|" + e.getKey() + "|#|" + e.getValue().toString();
		}

		return config;
	}

	private void runApp(String a_strPackageName)
	{
		ActivityManager am = (ActivityManager) _context.getSystemService(Context.ACTIVITY_SERVICE);
		// get the info from the currently running task
		boolean bFound = false;
		List<ActivityManager.RunningAppProcessInfo> listApp = am.getRunningAppProcesses();

		Iterator<ActivityManager.RunningAppProcessInfo> app = listApp.iterator();
		ActivityManager.RunningAppProcessInfo entryApp;
		while(app.hasNext())
		{
			entryApp = app.next();
			Log.d(AppConfig.TAG_APP, "run application entryApp.processName " + entryApp.processName);
			if(entryApp.processName.equals(a_strPackageName))
			{
				bFound = true;
				break;
			}
		}

		if(!bFound)
		{
			Intent LaunchIntent = _context.getPackageManager().getLaunchIntentForPackage(a_strPackageName);

			if(LaunchIntent != null)
			{
				_context.startActivity(LaunchIntent);
			}
			else
			{
				_context.sendBroadcast(new Intent(a_strPackageName + ".RUN_APP").addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES));
			}
//			else
//			{
//				if(AppConfig.DEBUG) Log.d(AppConfig.TAG_APP, "onItemClick unable to run application");
//				Toast.makeText(this, R.string.unable_to_run_application, Toast.LENGTH_SHORT).show();
//			}
		}

	}

	private class ConfigDeviceReader
	{
		public Map<String, String> readConfig(String a_PackageName)
		{
			final Map<String, String> values = new HashMap<String, String>();
			final Object lock = new Object();

			Log.d(AppConfig.TAG_APP, TAG + "readConfig: before synchronize");

			MyResultReceiver receiver = new MyResultReceiver(lock);

			synchronized(lock)
			{
				getSettings(a_PackageName, receiver.getNativeResultReceiver());

				try
				{
					Log.d(AppConfig.TAG_APP, TAG + "readConfig: before wait");
					lock.wait(2000);
					Log.d(AppConfig.TAG_APP, TAG + "readConfig: after wait");
				}
				catch(InterruptedException e)
				{
					Log.e(AppConfig.TAG_APP, TAG + "readConfig exception", e);
				}
			}

			if(receiver.getResult() != null)
			{
				onSettingsReceived(receiver.getResult().getString(PARAM_VOXCONFIG), values);
				Log.d(AppConfig.TAG_APP, TAG + "readConfig: return " + values);
			}
			else
			{
				Log.w(AppConfig.TAG_APP, TAG + "readConfig: no result.");
			}

			return values;
		}

		private void onSettingsReceived(String a_config, Map<String, String> a_Values)
		{
			if(a_config.startsWith(PARAM_VOXCONFIG))
			{
				final String SEP = "|#|";

				// first discard prefix "voxconfig|#|"
				int index = a_config.indexOf(SEP);
				int start = index + SEP.length();

				index = a_config.indexOf(SEP, start);
				while(index != -1)
				{
					String key, value;

					key = a_config.substring(start, index);
					start = index + SEP.length();

					index = a_config.indexOf(SEP, start);
					if(index > 0)
					{
						value = a_config.substring(start, index);
						start = index + SEP.length();
						index = a_config.indexOf(SEP, start);
					}
					else
					{
						value = a_config.substring(start);
					}

					a_Values.put(key, value);
				}
			}
		}

	}

	public void getSettings(String a_PackageName, ResultReceiver a_ResultReceiver)
	{
		Log.d(AppConfig.TAG_APP, TAG + "getSettings a_PackageName " + a_PackageName);
		StringBuilder tmp = new StringBuilder();
		tmp.append(a_PackageName).append('.').append(ACTION_CONFIG_GET_SETTINGS);
		// DON'T USE here DeviceServiceApi.sendBroadcast because Packagename is needed

		_context.sendBroadcast(new Intent(tmp.toString()).putExtra(EXTRA_RESULT_RECEIVER, a_ResultReceiver));
	}

	public void setSettings(String a_PackageName, String a_Config, ResultReceiver a_ResultReceiver)
	{
		Log.d(AppConfig.TAG_APP, TAG + "setSettings a_PackageName " + a_PackageName);
		StringBuilder tmp = new StringBuilder();
		tmp.append(a_PackageName).append('.').append(ACTION_CONFIG_SET_SETTINGS);
		// DON'T USE here DeviceServiceApi.sendBroadcast because Packagename is needed

		_context.sendBroadcast(new Intent(tmp.toString()).putExtra(PARAM_VOXSETCONFIG, a_Config).putExtra(
						EXTRA_RESULT_RECEIVER, a_ResultReceiver));
	}

}
