package org.ledain.ood.webserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.apache.http.HttpException;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

import android.content.Context;

import org.ledain.ood.AppConfig;

public class WebServer extends Thread
{

	public static boolean				RUNNING		= false;

	private static final String			APP_PATTERN	= "/app/*";
	private static final String			ALL_PATTERN	= "/*";

	/*
	private static final String			API_PATTERN		= "/api/*";
	private static final String			TOOLS_PATTERN	= "/tools/*";
	*/
	private Context						context		= null;

	private BasicHttpProcessor			httpproc	= null;
	private BasicHttpContext			httpContext	= null;
	private HttpService					httpService	= null;
	private HttpRequestHandlerRegistry	registry	= null;

	public WebServer(Context context)
	{
		this.setContext(context);

		httpproc = new BasicHttpProcessor();
		httpContext = new BasicHttpContext();

		httpproc.addInterceptor(new ResponseDate());
		httpproc.addInterceptor(new ResponseServer());
		httpproc.addInterceptor(new ResponseContent());
		httpproc.addInterceptor(new ResponseConnControl());

		httpService = new HttpService(httpproc, new DefaultConnectionReuseStrategy(), new DefaultHttpResponseFactory());

		registry = new HttpRequestHandlerRegistry();

		registry.register(APP_PATTERN, new AppCommandHandler(context));
		registry.register(ALL_PATTERN, new ApiCommandHandler(context));

		httpService.setHandlerResolver(registry);
	}

	private ServerSocket	serverSocket;

	@Override
	public void run()
	{
		try
		{
			serverSocket = new ServerSocket(AppConfig.SERVER_PORT);

			serverSocket.setReuseAddress(true);

			while(RUNNING)
			{
				try
				{
					final Socket socket = serverSocket.accept();

					DefaultHttpServerConnection serverConnection = new DefaultHttpServerConnection();

					serverConnection.bind(socket, new BasicHttpParams());

					httpService.handleRequest(serverConnection, httpContext);

					serverConnection.shutdown();
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
				catch(HttpException e)
				{
					e.printStackTrace();
				}
			}

			serverSocket.close();
		}
		catch(SocketException e)
		{
			e.printStackTrace();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}

		RUNNING = false;
	}

	public synchronized void startServer()
	{
		RUNNING = true;
		start();
	}

	public synchronized void stopServer()
	{
		RUNNING = false;
		if(serverSocket != null)
		{
			try
			{
				serverSocket.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public void setContext(Context context)
	{
		this.context = context;
	}

	public Context getContext()
	{
		return context;
	}
}
