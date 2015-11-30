package org.ledain.ood.tools;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

@SuppressLint("NewApi")
public class CalendarTools
{
	private static final String	TAG	= "CalendarTools - ";

	private abstract static class CalendarTable
	{
		public static int	osversion	= android.os.Build.VERSION.SDK_INT;

		protected static Uri getUri(String a_tableName)
		{
			Uri _uri = null;

			if(osversion < android.os.Build.VERSION_CODES.FROYO)
			{
				_uri = Uri.parse("content://calendar/" + a_tableName);
			}
			else if(osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			{
				_uri = Uri.parse("content://com.android.calendar/" + a_tableName);
			}

			return _uri;
		}
	}

	public static class Instances extends CalendarTable
	{
		private static Uri			_uri		= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? getUri("instances/when")
																: CalendarContract.Instances.CONTENT_URI;
		public static final String	AUTHORITY	= osversion < android.os.Build.VERSION_CODES.ECLAIR ? "calendar"
																: "com.android.calendar";

		public static Uri getUri()
		{
			return _uri;
		}

		public static final Cursor query(ContentResolver cr, String[] projection, long begin, long end)
		{
			final String WHERE_CALENDARS_SELECTED = (osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? ""
							: (Calendars.VISIBLE + "=?"));
			final String[] WHERE_CALENDARS_ARGS = osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? (new String[] {})
							: (new String[] { "1" });
			final String DEFAULT_SORT_ORDER = Calendars.DISPLAY_NAME;
			Uri.Builder builder = getUri().buildUpon();
			ContentUris.appendId(builder, begin);
			ContentUris.appendId(builder, end);
			return cr.query(builder.build(), projection, WHERE_CALENDARS_SELECTED, WHERE_CALENDARS_ARGS, DEFAULT_SORT_ORDER);
		}

	}

	public static class Calendars extends CalendarTable
	{
		private static Uri			_uri		= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? getUri("calendars")
																: CalendarContract.Calendars.CONTENT_URI;
		public static final String	AUTHORITY	= osversion < android.os.Build.VERSION_CODES.ECLAIR ? "calendar"
																: "com.android.calendar";

		public static Uri getUri()
		{
			return _uri;
		}

		public static final String		ACCOUNT_TYPE_LOCAL	= CalendarContract.ACCOUNT_TYPE_LOCAL;

		public static final String		ID					= CalendarContract.Calendars._ID;
		public static final String		OWNER				= CalendarContract.Calendars.OWNER_ACCOUNT;
		public static final String		DISPLAY_NAME		= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? "displayName"
																			: CalendarContract.Calendars.CALENDAR_DISPLAY_NAME;
		public static final String		ACCESS_LEVEL		= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? "access_level"
																			: CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL;
		public static final int			CAL_ACCESS_OWNER	= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? 700
																			: CalendarContract.CalendarEntity.CAL_ACCESS_OWNER;
		public static final String		VISIBLE				= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? "visible"
																			: CalendarContract.Calendars.VISIBLE;

		public static final String[]	PROJECTION			= new String[] { ID, OWNER, DISPLAY_NAME, ACCESS_LEVEL };

		public static final int			ID_INDEX			= 0;
		public static final int			OWNER_INDEX			= 1;
		public static final int			DISPLAY_NAME_INDEX	= 2;
		public static final int			ACCESS_LEVEL_INDEX	= 3;

	}

	public static class Events extends CalendarTable
	{
		private static Uri			_uri		= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? getUri("events")
																: CalendarContract.Events.CONTENT_URI;
		public static final String	AUTHORITY	= osversion < android.os.Build.VERSION_CODES.ECLAIR ? "calendar"
																: "com.android.calendar";

		public static Uri getUri()
		{
			return _uri;
		}

		public static String getCalendarSelection()
		{
			if(CalendarTools.Events.osversion < android.os.Build.VERSION_CODES.FROYO)
			{
				return CalendarTools.Events.CALENDAR_ID + "=?";
			}
			else
			{
				return CalendarTools.Events.CALENDAR_ID + "=? AND " + CalendarTools.Events.DELETED + "<>1 AND "
								+ CalendarTools.Events.ORIGINAL_ID + " IS NULL";
			}
		}

		// table Events
		public static final String		ID					= CalendarContract.Events._ID;
		public static final String		TITLE				= CalendarContract.Events.TITLE;
		public static final String		LOCATION			= CalendarContract.Events.EVENT_LOCATION;
		public static final String		DESCRIPTION			= CalendarContract.Events.DESCRIPTION;
		public static final String		START				= CalendarContract.Events.DTSTART;
		public static final String		END					= CalendarContract.Events.DTEND;
		public static final String		ALLDAY				= CalendarContract.Events.ALL_DAY;
		public static final String		DURATION			= CalendarContract.Events.DURATION;
		public static final String		RRULE				= CalendarContract.Events.RRULE;
		public static final String		EXRULE				= CalendarContract.Events.EXRULE;
		public static final String		RDATE				= CalendarContract.Events.RDATE;
		public static final String		EXDATE				= CalendarContract.Events.EXDATE;
		public static final String		HASALARM			= CalendarContract.Events.HAS_ALARM;
		public static final String		CALENDAR_ID			= CalendarContract.Events.CALENDAR_ID;
		public static final String		EVENT_TIMEZONE		= CalendarContract.Events.EVENT_TIMEZONE;
		public static final String		DELETED				= CalendarContract.Events.DELETED;

		public static final String		ORIGINAL_ID			= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? "originalEvent"
																			: CalendarContract.Events.ORIGINAL_ID;

		public static final String[]	PROJECTION			= osversion < android.os.Build.VERSION_CODES.FROYO ? new String[] {
																			ID, TITLE, LOCATION, DESCRIPTION, START, END,
																			ALLDAY, DURATION, RRULE, EXRULE, RDATE, EXDATE,
																			HASALARM, CALENDAR_ID, ORIGINAL_ID }
																			: new String[] { ID, TITLE, LOCATION, DESCRIPTION,
																							START, END, ALLDAY, DURATION,
																							RRULE, EXRULE, RDATE, EXDATE,
																							HASALARM, CALENDAR_ID, ORIGINAL_ID,
																							DELETED };

		public static final int			ID_INDEX			= 0;
		public static final int			TITLE_INDEX			= 1;
		public static final int			LOCATION_INDEX		= 2;
		public static final int			DESCRIPTION_INDEX	= 3;
		public static final int			START_INDEX			= 4;
		public static final int			END_INDEX			= 5;
		public static final int			ALLDAY_INDEX		= 6;
		public static final int			DURATION_INDEX		= 7;
		public static final int			RRULE_INDEX			= 8;
		public static final int			EXRULE_INDEX		= 9;
		public static final int			RDATE_INDEX			= 10;
		public static final int			EXDATE_INDEX		= 11;
		public static final int			HASALARM_INDEX		= 12;
		public static final int			CALENDAR_ID_INDEX	= 13;
		public static final int			ORIGINAL_ID_INDEX	= 14;
		public static final int			DELETED_INDEX		= 15;
	}

	public static class Reminders extends CalendarTable
	{
		private static Uri		_uri			= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? getUri("reminders")
																: CalendarContract.Reminders.CONTENT_URI;
		public static final int	METHOD_DEFAULT	= osversion < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ? 0
																: CalendarContract.Reminders.METHOD_DEFAULT;

		public static Uri getUri()
		{
			return _uri;
		}

		// table Reminders
		public static final String		ID				= CalendarContract.Reminders._ID;
		public static final String		EVENT_ID		= CalendarContract.Reminders.EVENT_ID;
		public static final String		MINUTES			= CalendarContract.Reminders.MINUTES;
		public static final String		METHOD			= CalendarContract.Reminders.METHOD;

		public static final String[]	PROJECTION		= new String[] { ID, EVENT_ID, MINUTES, METHOD };

		// indexes into projection
		public static final int			ID_INDEX		= 0;
		public static final int			EVENT_ID_INDEX	= 1;
		public static final int			MINUTES_INDEX	= 2;
		public static final int			METHOD_INDEX	= 3;
	}

}
