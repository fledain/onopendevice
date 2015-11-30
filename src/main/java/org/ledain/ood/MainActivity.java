package org.ledain.ood;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import org.ledain.ood.R;
import org.ledain.ood.webserver.WebServerService;

public class MainActivity extends Activity
{
	private static final String	TAG	= "MainActivity - ";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Context context = getApplicationContext();
		Intent webServerService = new Intent(context, WebServerService.class);
		context.startService(webServerService);

		TextView text = (TextView) findViewById(R.id.label);
		text.setText("\n" + "To open OnOpenDevice from your desktop browser, execute the following:\n\n"
						+ "    adb.exe forward tcp:8090 tcp:8090\n\n" + "and browse http://localhost:8090/\n" + "\n");
//						+ "Contact point: " + NetworkUtils.getIPAddress(true) + ":" + AppConfig.SERVER_PORT);

		((Button) findViewById(R.id.export)).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("http://localhost:" + AppConfig.SERVER_PORT
								+ "/")));
			}
		});
	}

	@Override
	protected void onDestroy()
	{
		Context context = getApplicationContext();
		Intent webServerService = new Intent(context, WebServerService.class);
		context.stopService(webServerService);

		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public String getLocalIpAddress()
	{
		try
		{
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
			{
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
				{
					InetAddress inetAddress = enumIpAddr.nextElement();
					if(!inetAddress.isLoopbackAddress())
					{
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		}
		catch(SocketException ex)
		{
			Log.e(AppConfig.TAG_APP, TAG + "getLocalIpAddress: exception", ex);
		}
		return null;
	}

}
