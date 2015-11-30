package org.ledain.ood.webserver;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.ledain.ood.AppConfig;

public class WebServerService extends Service
{
	private static final String	TAG		= "WebServerService - ";

	private WebServer			server	= null;

	@Override
	public void onCreate()
	{
		Log.i(AppConfig.TAG_APP, TAG + "Creating and starting httpService");
		super.onCreate();

		server = new WebServer(this);
		server.startServer();
	}

	@Override
	public void onDestroy()
	{
		Log.i(AppConfig.TAG_APP, TAG + "Destroying httpService");
		server.stopServer();
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		return START_STICKY;
	}
}
