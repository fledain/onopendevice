package org.ledain.ood.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import org.ledain.ood.AppConfig;

public class IoUtils
{
	private static final String	TAG						= "IoUtils - ";

	private static final int	DEFAULT_BYTEARRAY_SIZE	= 10 * 1024;
	private static final String	HTML_ENCODER			= "'\"<>";

	public enum Encoding
	{
		IDENTITY, BASE64, HTML
	}

	private static class HtmlOutputStream extends OutputStream
	{
		private OutputStream	_out;

		public HtmlOutputStream(OutputStream out, int flags)
		{
			super();
			_out = out;
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException
		{
			for (int i = off; i < (b.length - off); i++)
			{
				if(HTML_ENCODER.indexOf(b[i]) != -1)
				{
					_out.write('\\');
				}
				else if(b[i] == 13)
				{
					continue;
				}
				else if(b[i] == 10)
				{
					_out.write("\\r\\n".getBytes());
					continue;
				}
				_out.write(b[i]);

			}
		}

		@Override
		public void write(int oneByte) throws IOException
		{
			if(HTML_ENCODER.indexOf(oneByte) != -1)
			{
				_out.write('\\');
			}
			_out.write(oneByte);
		}

		@Override
		public void flush() throws IOException
		{
			_out.flush();
		}

		@Override
		public void close() throws IOException
		{
			_out.close();
		}
	}

	public static long copy(InputStream in, OutputStream out) throws IOException
	{
		return copy(in, out, Encoding.IDENTITY);
	}

	public static long copy(InputStream in, OutputStream out, Encoding enc) throws IOException
	{
		long res = 0;
		int stepSize = DEFAULT_BYTEARRAY_SIZE;

		final OutputStream ostream;

		if(enc == Encoding.BASE64)
		{
			ostream = new Base64OutputStream(out, Base64.NO_CLOSE | Base64.NO_WRAP);
		}
		else if(enc == Encoding.HTML)
		{
			ostream = new HtmlOutputStream(out, 0);
		}
		else
		{
			ostream = out;
		}
		Log.d(AppConfig.TAG_APP, TAG + "copy - stepSize = " + stepSize);
		final byte[] buffer = new byte[stepSize];
		int read;
		try
		{
			while((read = in.read(buffer)) != -1)
			{
				ostream.write(buffer, 0, read);
				ostream.flush();
				res += read;
			}
		}
		catch(IOException e)
		{
			if(e instanceof SocketException)
			{
				Log.d(AppConfig.TAG_APP, TAG + "copy - SocketException:" + e.getMessage());
			}
			else
			{
				Log.e(AppConfig.TAG_APP, TAG + "copy - exception:", e);
			}
			throw e;
		}
		if(enc == Encoding.BASE64)
		{
			// flag NO_CLOSE --> only flush
			ostream.close();
		}

		in.close();

		return res;
	}

}
