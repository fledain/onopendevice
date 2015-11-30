package org.ledain.ood.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import android.content.Context;
import android.util.Log;

import org.ledain.ood.AppConfig;
import org.ledain.ood.tools.IoUtils;
import org.ledain.ood.webserver.ApiCommandHandler.ContentEncoding;

public class AppCommandHandler implements HttpRequestHandler
{
	private static final String	TAG		= "ApiCommandHandler - ";

	private Context				context	= null;

	public AppCommandHandler(Context context)
	{
		this.context = context;
	}

	@Override
	public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws HttpException, IOException
	{
		final String action = request.getRequestLine().getUri();
		final String assetName;
		final String contentType;

		assetName = action.substring(1);

		final ContentEncoding wishCE;

		if(action.endsWith(".css"))
		{
			contentType = "text/css";
			wishCE = ContentEncoding.GZIP;
		}
		else if(action.endsWith(".js"))
		{
			contentType = "text/javascript";
			wishCE = ContentEncoding.GZIP;
		}
		else if(action.endsWith(".jpg"))
		{
			contentType = "image/jpeg";
			wishCE = ContentEncoding.IDENTITY;
		}
		else if(action.endsWith(".gif"))
		{
			contentType = "image/gif";
			wishCE = ContentEncoding.IDENTITY;
		}
		else if(action.endsWith(".png"))
		{
			contentType = "image/png";
			wishCE = ContentEncoding.IDENTITY;
		}
		else if(action.endsWith(".otf"))
		{
			contentType = "font/opentype";
			wishCE = ContentEncoding.GZIP;
		}
		else
		{
			Log.e(AppConfig.TAG_APP, TAG + "APP request resource not found: " + action);
			contentType = "text/plain";
			response.setStatusCode(404);
			return;
		}

		Log.d(AppConfig.TAG_APP, TAG + "APP serving resource: " + action);

		Header[] headers = request.getHeaders("Accept-Encoding");
		final ContentEncoding ce;

		if(wishCE == ContentEncoding.IDENTITY || headers == null || headers.length == 0)
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

		HttpEntity entity = new EntityTemplate(new ContentProducer()
		{
			@Override
			public void writeTo(final OutputStream outstream) throws IOException
			{
				long n = 0;
				if(assetName != null)
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

					n = IoUtils.copy(context.getAssets().open(assetName), ostream);

					ostream.flush();
					if(ce == ContentEncoding.GZIP)
					{
						ostream.close();
					}
				}
				else
				{
					outstream.close();
				}
				Log.v(AppConfig.TAG_APP, TAG + "APP: served " + n + " bytes for " + assetName);

			}
		});
		response.setHeader("Content-Type", contentType);
		response.setHeader("Cache-Control", "max-age=" + (3600 * 24 * 1) + ", public");
		if(ce == ContentEncoding.GZIP)
		{
			response.setHeader("Content-Encoding", "gzip");
		}

		response.setEntity(entity);
	}
}
