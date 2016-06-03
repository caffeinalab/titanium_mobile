/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.calendar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.TiConvert;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateUtils;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;

@Kroll.proxy(parentModule=CalendarModule.class)
public class CalendarProxy extends KrollProxy {

	protected String id, name;
	private static final String TAG = "Calendar";
	protected boolean selected, hidden;
	private static final long MAX_DATE_RANGE = 2 * DateUtils.YEAR_IN_MILLIS - 3 * DateUtils.DAY_IN_MILLIS;

	public CalendarProxy(String id, String name, boolean selected, boolean hidden)
	{
		super();

		this.id = id;
		this.name = name;
		this.selected = selected;
		this.hidden = hidden;
	}

	public CalendarProxy(TiContext context, String id, String name, boolean selected, boolean hidden)
	{
		this(id, name, selected, hidden);
	}

	public static String getBaseCalendarUri()
	{
		if (Build.VERSION.SDK_INT >= 8) { // FROYO, 2.2
			return "content://com.android.calendar";
		}

		return "content://calendar";
	}

	public static String getAccountName() {
		return TiApplication.getInstance().getAppInfo().getId();
	}

	public static ArrayList<CalendarProxy> queryCalendars(String query, String[] queryArgs)
	{
		ArrayList<CalendarProxy> calendars = new ArrayList<CalendarProxy>();
		if (!hasCalendarPermissions()) {
			return calendars;
		}
		ContentResolver contentResolver = TiApplication.getInstance().getContentResolver();

		Cursor cursor = null;
		if (Build.VERSION.SDK_INT >= 14) { // ICE_CREAM_SANDWICH, 4.0
			cursor = contentResolver.query(Uri.parse(getBaseCalendarUri() + "/calendars"),
				new String[] { "_id", "calendar_displayName", "visible"}, query, queryArgs, null);
		}
		else if (Build.VERSION.SDK_INT >= 11) { // HONEYCOMB, 3.0
			cursor = contentResolver.query(Uri.parse(getBaseCalendarUri() + "/calendars"),
				new String[] { "_id", "displayName", "selected"}, query, queryArgs, null);
		}
		else {
			cursor = contentResolver.query(Uri.parse(getBaseCalendarUri() + "/calendars"),
				new String[] { "_id", "displayName", "selected", "hidden" }, query, queryArgs, null);
		}

		// calendars can be null
		if (cursor!=null)
		{
			while (cursor.moveToNext()) {
				String id = cursor.getString(0);
				String name = cursor.getString(1);
				boolean selected = !cursor.getString(2).equals("0");
				// For API level >= 11 (3.0), there is no column "hidden".
				boolean hidden = false;
				if (Build.VERSION.SDK_INT < 11) {
					hidden = !cursor.getString(3).equals("0");
				}

				calendars.add(new CalendarProxy(id, name, selected, hidden));
			}
		}

		return calendars;
	}

	public static ArrayList<CalendarProxy> queryCalendars(TiContext context, String query, String[] queryArgs)
	{
		return queryCalendars(query, queryArgs);
	}

	public static boolean hasCalendarPermissions() {
		if (Build.VERSION.SDK_INT < 23) {
			return true;
		}
		Activity currentActivity = TiApplication.getAppCurrentActivity();
		if (currentActivity != null && 
				currentActivity.checkSelfPermission("android.permission.READ_CALENDAR") == PackageManager.PERMISSION_GRANTED &&
				currentActivity.checkSelfPermission("android.permission.WRITE_CALENDAR") == PackageManager.PERMISSION_GRANTED) {
			return true;
		} 
		Log.w(TAG, "Calendar permissions are missing");
		return false;
	}

	@Kroll.method
	public EventProxy[] getEventsInYear(int year)
	{
		Calendar jan1 = Calendar.getInstance();
		jan1.clear();
		jan1.set(year, 0, 1);

		long date1 = jan1.getTimeInMillis();
		long date2 = date1 + DateUtils.YEAR_IN_MILLIS;
		ArrayList<EventProxy> events = EventProxy.queryEventsBetweenDates(date1, date2, this);
		return events.toArray(new EventProxy[events.size()]);
	}

	@Kroll.method
	public EventProxy[] getEventsInMonth(int year, int month)
	{
		Calendar firstOfTheMonth = Calendar.getInstance();
		firstOfTheMonth.clear();
		firstOfTheMonth.set(year, month, 1);
		Calendar lastOfTheMonth = Calendar.getInstance();
		lastOfTheMonth.clear();
		lastOfTheMonth.set(year, month, 1, 23, 59, 59);

		int lastDay = lastOfTheMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
		lastOfTheMonth.set(Calendar.DAY_OF_MONTH, lastDay);

		long date1 = firstOfTheMonth.getTimeInMillis();
		long date2 = lastOfTheMonth.getTimeInMillis();

		ArrayList<EventProxy> events = EventProxy.queryEventsBetweenDates(date1, date2, this);
		return events.toArray(new EventProxy[events.size()]);
	}

	@Kroll.method
	public EventProxy[] getEventsInDate(int year, int month, int day)
	{
		Calendar beginningOfDay = Calendar.getInstance();
		beginningOfDay.clear();
		beginningOfDay.set(year, month, day, 0, 0, 0);
		Calendar endOfDay = Calendar.getInstance();
		endOfDay.clear();
		endOfDay.set(year, month, day, 23, 59, 59);

		long date1 = beginningOfDay.getTimeInMillis();
		long date2 = endOfDay.getTimeInMillis();

		ArrayList<EventProxy> events = EventProxy.queryEventsBetweenDates(date1, date2, this);
		return events.toArray(new EventProxy[events.size()]);
	}

	@Kroll.method
	public EventProxy[] getEventsBetweenDates(Date date1, Date date2)
	{
		long start = date1.getTime();
		long end = date2.getTime();
		ArrayList<EventProxy> events = new ArrayList<EventProxy>();

		// A workaround for TIMOB-8439
		while (end - start > MAX_DATE_RANGE) {
			events.addAll(EventProxy.queryEventsBetweenDates(start, start + MAX_DATE_RANGE, this));
			start += MAX_DATE_RANGE;
		}

		events.addAll(EventProxy.queryEventsBetweenDates(start, end, this));

		return events.toArray(new EventProxy[events.size()]);
	}

	@Kroll.method
	public EventProxy getEventById(int id)
	{
		ArrayList<EventProxy> events = EventProxy.queryEvents("_id = ?", new String[] { ""+id });
		if (events.size() > 0) {
			return events.get(0);
		} else return null;
	}

	@Kroll.method
	public EventProxy createEvent(KrollDict data)
	{
		return EventProxy.createEvent(this, data);
	}

	@Kroll.getProperty @Kroll.method
	public String getName()
	{
		return name;
	}

	@Kroll.getProperty @Kroll.method
	public String getId()
	{
		return id;
	}

	@Kroll.getProperty @Kroll.method
	public boolean getSelected()
	{
		return selected;
	}

	@Kroll.getProperty @Kroll.method
	public boolean getHidden()
	{
		return hidden;
	}

	@Kroll.method
	public static String create(KrollDict data) 
	{
		ContentResolver contentResolver = TiApplication.getInstance().getContentResolver();
		if (!hasCalendarPermissions()) {
			return null;
		}

		ContentValues calendarValues = new ContentValues();

		calendarValues.put(CalendarContract.Calendars.ACCOUNT_NAME, CalendarProxy.getAccountName());
		calendarValues.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);

		calendarValues.put(CalendarContract.Calendars.NAME, TiApplication.getInstance().getAppInfo().getName());
		calendarValues.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, TiApplication.getInstance().getAppInfo().getName());
		calendarValues.put(CalendarContract.Calendars.VISIBLE, 1);
		calendarValues.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER);
		calendarValues.put(CalendarContract.Calendars.SYNC_EVENTS, 1);

		Uri.Builder builder = CalendarContract.Calendars.CONTENT_URI.buildUpon();
		builder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CalendarProxy.getAccountName());
		builder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
		builder.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true");

		Uri uri = contentResolver.insert(builder.build(), calendarValues);
		String id = uri.getLastPathSegment();

		return id;
	}

	@Kroll.method
	public Boolean remove() 
	{
		ContentResolver contentResolver = TiApplication.getInstance().getContentResolver();
		if (!hasCalendarPermissions()) {
			return false;
		}

		Uri.Builder builder = CalendarContract.Calendars.CONTENT_URI.buildUpon();
		builder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CalendarProxy.getAccountName());
		builder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
		builder.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true");

		int rows = contentResolver.delete(builder.build(), CalendarContract.Calendars._ID + "=?", new String[]{ this.id });
		return (rows == 1);
	}

	@Override
	public String getApiName()
	{
		return "Ti.Calendar.Calendar";
	}
}

