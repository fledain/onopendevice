package org.ledain.ood.tools;

import android.accounts.Account;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;

import org.ledain.ood.AppConfig;

public class ToolsAccounts
{
	private static final String	TAG				= "ToolsAccounts - ";
	private static Account		_defaultAccount	= null;
	private static Thread		_runningThread	= null;

	public static Account identifyDefaultContactAccount(final Context a_Context)
	{
		if(_defaultAccount == null)
		{
			if(_runningThread == null)
			{
				String nameOfTest = "Test OOD";

				// first, delete all existing Test OOD contacts
				int n = a_Context.getContentResolver().delete(RawContacts.CONTENT_URI, "display_name =?",
								new String[] { nameOfTest });
				if(n > 0)
				{
					Log.d(AppConfig.TAG_APP, TAG + "identifyDefaultContactAccount: deleted previous OOD test contacts #" + n);
				}

				// Create a raw contact
				ContentValues values = new ContentValues();
				final Uri rawContactUri = a_Context.getContentResolver().insert(RawContacts.CONTENT_URI, values);
				final long rawContactId = ContentUris.parseId(rawContactUri);

				values.clear();
				values.put(Data.RAW_CONTACT_ID, rawContactId);
				values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
				values.put(StructuredName.DISPLAY_NAME, nameOfTest);
				a_Context.getContentResolver().insert(Data.CONTENT_URI, values);

				final Runnable r = new Runnable()
				{
					@Override
					public void run()
					{
						String name = null;
						String type = null;
						Cursor cursor = null;

						try
						{
							// Query to get data
							cursor = a_Context.getContentResolver().query(rawContactUri, null, null, null, null);

							if(cursor != null && cursor.getCount() > 0)
							{
								if(cursor.moveToFirst())
								{
									name = cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_NAME));
									type = cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_TYPE));
									if(!TextUtils.isEmpty(name) && !TextUtils.isEmpty(type))
									{
										_defaultAccount = new Account(name, type);
										Log.d(AppConfig.TAG_APP, TAG + "identifyDefaultContactAccount: got account "
														+ _defaultAccount);
									}
								}
							}
						}
						catch(Exception e)
						{
							Log.e(AppConfig.TAG_APP, TAG + "Error occurred while reading cursor:", e);
						}
						finally
						{
							if(cursor != null)
							{
								cursor.close();
							}
						}
					}
				};

				r.run();

				if(_defaultAccount == null)
				{
					Log.w(AppConfig.TAG_APP, TAG + "No default account found right now. Delay new attempt.");
					_runningThread = new Thread(new Runnable()
					{
						@Override
						public void run()
						{
							for (int i = 0; i < 60 && _defaultAccount == null; i++)
							{
								try
								{
									Thread.sleep(1000);
								}
								catch(InterruptedException e)
								{
								}
								r.run();
							}
							_runningThread = null;
							a_Context.getContentResolver().delete(rawContactUri, null, null);
							if(_defaultAccount == null)
							{
								_defaultAccount = new Account("null", "null");
							}
						}
					});
					_runningThread.start();
					return new Account("(identifying default account...)", "(please reload in few seconds)");
				}
				else
				{
					a_Context.getContentResolver().delete(rawContactUri, null, null);
				}
			}
			else
			{
				return new Account("(identification still ongoing...)", "(please reload in few seconds)");
			}
		}

		return _defaultAccount;
	}
}
