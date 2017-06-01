/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import libcore.icu.LocaleData;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.android.systemui.DemoMode;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Digital clock for the status bar.
 */
public class Clock extends TextView implements DemoMode {

    private static final int CLOCK_RIGHT    = 0;
    private static final int CLOCK_CENTER   = 1;
    private static final int CLOCK_LEFT     = 2;

    private static final int STYLE_NORMAL   = 0;
    private static final int STYLE_SMALL    = 1;
    private static final int STYLE_GONE     = 2;

    private static final int DATE_REGULAR   = 0;
    private static final int DATE_LOWERCASE = 1;
    private static final int DATE_UPPERCASE = 2;

    public boolean isSupposedToShow = false;

    protected int mClockPosition = CLOCK_RIGHT;

    private boolean mAttached;
    private Calendar mCalendar;
    private String mClockFormatString = "";
    private SimpleDateFormat mClockFormat;
    private SimpleDateFormat mContentDescriptionFormat;
    private Locale mLocale;
    private boolean mScreenOn = true;

    private String mDateFormat = "";
    private int mLastClockPosition = -1;
    private int mDateStyle = STYLE_GONE;
    private int mDatePosition = CLOCK_LEFT;
    private int mDateCase = DATE_LOWERCASE;
    private int mClockStartPadding;
    private int mClockEndPadding;
    private boolean mInitDone = false;
    private boolean mClockEnabled = true;
    private boolean mReceiverRegistered = false;
    private boolean mRefreshClock = false;
    private LinearLayout mStartParent;
    private LayoutParams mInitialLp;
    private SettingsObserver mSettingsObserver;
    private IntentFilter mFilter;

    private int mAmPmStyle;
    private boolean mShowSeconds;
    private Handler mSecondsHandler;

    public Clock(Context context) {
        this(context, null);
    }

    public Clock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Clock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.Clock,
                0, 0);
        try {
            mAmPmStyle = a.getInt(R.styleable.Clock_amPmStyle, STYLE_NORMAL);
        } finally {
            a.recycle();
        }

        mClockStartPadding = (int) getResources()
                .getDimension(R.dimen.status_bar_clock_starting_padding);
        mClockEndPadding = (int) getResources()
                .getDimension(R.dimen.status_bar_clock_end_padding);

        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);

            mFilter = filter;

            if (mClockEnabled) {
                getContext().registerReceiverAsUser(mIntentReceiver,
                        UserHandle.ALL, filter, null, getHandler());
                mReceiverRegistered = true;
            }
        }

        // NOTE: It's safe to do these after registering the receiver since the receiver always runs
        // in the main thread, therefore the receiver can't run before this method returns.

        // The time zone may have changed while the receiver wasn't registered, so update the Time
        mCalendar = Calendar.getInstance(TimeZone.getDefault());

        // Make sure we update to the current time
        updateClock();
        updateShowSeconds();
        if (!mInitDone) {
            mStartParent = (LinearLayout) getParent();
            mInitialLp = (LayoutParams) getLayoutParams();
            mInitDone = true;
            updateCustomSettings();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            if (mClockEnabled) {
                getContext().unregisterReceiver(mIntentReceiver);
                mReceiverRegistered = false;
            }
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = Calendar.getInstance(TimeZone.getTimeZone(tz));
                TimeZone.setDefault(mCalendar.getTimeZone());
                if (mClockFormat != null) {
                    mClockFormat.setTimeZone(mCalendar.getTimeZone());
                }
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                final Locale newLocale = getResources().getConfiguration().locale;
                if (! newLocale.equals(mLocale)) {
                    mLocale = newLocale;
                    mClockFormatString = ""; // force refresh
                }
            }

            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mScreenOn = true;
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
            }

            if (mScreenOn) {
                getHandler().post(() -> updateClock());
            }
        }
    };

    final void updateClock() {
        if (mDemoMode) return;
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        setText(getSmallTime());
        setContentDescription(mContentDescriptionFormat.format(mCalendar.getTime()));
    }

    private void updateShowSeconds() {
        updateShowSeconds(false);
    }

    private void updateShowSeconds(boolean force) {
        if (mShowSeconds) {
            // Wait until we have a display to start trying to show seconds.
            if (mSecondsHandler == null && getDisplay() != null) {
                mSecondsHandler = new Handler();
                if (getDisplay().getState() == Display.STATE_ON) {
                    if (force) {
                        mSecondsHandler.post(mSecondTick);
                    } else {
                        mSecondsHandler.postAtTime(mSecondTick,
                                SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
                    }
                }
                IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
                filter.addAction(Intent.ACTION_SCREEN_ON);
                mContext.registerReceiver(mScreenReceiver, filter);
            }
        } else {
            if (mSecondsHandler != null) {
                mContext.unregisterReceiver(mScreenReceiver);
                mSecondsHandler.removeCallbacks(mSecondTick);
                mSecondsHandler = null;
                updateClock();
            }
        }
    }

    private final CharSequence getSmallTime() {
        Context context = getContext();
        boolean is24 = DateFormat.is24HourFormat(context, ActivityManager.getCurrentUser());
        LocaleData d = LocaleData.get(context.getResources().getConfiguration().locale);

        final char MAGIC1 = '\uEF00';
        final char MAGIC2 = '\uEF01';

        SimpleDateFormat sdf;
        String format = mShowSeconds
                ? is24 ? d.timeFormat_Hms : d.timeFormat_hms
                : is24 ? d.timeFormat_Hm : d.timeFormat_hm;
        if (!format.equals(mClockFormatString)) {
            mContentDescriptionFormat = new SimpleDateFormat(format);
            /*
             * Search for an unquoted "a" in the format string, so we can
             * add dummy characters around it to let us find it again after
             * formatting and change its size.
             */
            if (mAmPmStyle != STYLE_NORMAL) {
                int a = -1;
                boolean quoted = false;
                for (int i = 0; i < format.length(); i++) {
                    char c = format.charAt(i);

                    if (c == '\'') {
                        quoted = !quoted;
                    }
                    if (!quoted && c == 'a') {
                        a = i;
                        break;
                    }
                }

                if (a >= 0) {
                    // Move a back so any whitespace before AM/PM is also in the alternate size.
                    final int b = a;
                    while (a > 0 && Character.isWhitespace(format.charAt(a-1))) {
                        a--;
                    }
                    format = format.substring(0, a) + MAGIC1 + format.substring(a, b)
                        + "a" + MAGIC2 + format.substring(b + 1);
                }
            }
            mClockFormat = sdf = new SimpleDateFormat(format);
            mClockFormatString = format;
        } else {
            sdf = mClockFormat;
        }

        // Following part taken from reference commit as described
        // in the commit message and adjusted.
        // For authorship please refer to the commit message of the ref commit.

        CharSequence dateString = null;
        String result = "";
        String timeResult = sdf.format(mCalendar.getTime());
        String dateResult = "";

        if (mDateStyle != STYLE_GONE) {
            Date now = new Date();

            // Set dateString to short uppercase weekday if empty
            dateString = DateFormat.format(
                            mDateFormat == null || mDateFormat.isEmpty() ?
                                "EEE" : mDateFormat, now);
            if (mDateCase != DATE_REGULAR) {
                // When Date style is small, convert date to uppercase
                dateResult = mDateCase == DATE_LOWERCASE ?
                                dateString.toString().toLowerCase() :
                                dateString.toString().toUpperCase();
            } else {
                dateResult = dateString.toString();
            }
            result = mDatePosition == CLOCK_LEFT
                            ? dateResult + " " + timeResult
                            : timeResult + " " + dateResult;
        } else {
            // No date, just show time
            result = timeResult;
        }

        SpannableStringBuilder formatted = new SpannableStringBuilder(result);

        if (mDateStyle != STYLE_NORMAL && dateString != null) {
            int dateStringLen = dateString.length();
            int timeStringOffset =
                    (mDatePosition == CLOCK_RIGHT) ?
                        timeResult.length() + 1 : 0;
            if (mDateStyle == STYLE_GONE) {
                formatted.delete(0, dateStringLen);
            } else {
                if (mDateStyle == STYLE_SMALL) {
                    CharacterStyle style = new RelativeSizeSpan(0.7f);
                    formatted.setSpan(style, timeStringOffset,
                                      timeStringOffset + dateStringLen,
                                      Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                }
            }
        }

        if (mAmPmStyle != STYLE_NORMAL) {
            int magic1 = result.indexOf(MAGIC1);
            int magic2 = result.indexOf(MAGIC2);
            if (magic1 >= 0 && magic2 > magic1) {
                if (mAmPmStyle == STYLE_GONE) {
                    formatted.delete(magic1, magic2+1);
                } else {
                    if (mAmPmStyle == STYLE_SMALL) {
                        CharacterStyle style = new RelativeSizeSpan(0.7f);
                        formatted.setSpan(style, magic1, magic2,
                                          Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                    formatted.delete(magic2, magic2 + 1);
                    formatted.delete(magic1, magic1 + 1);
                }
            }
        }

        return formatted;
    }

    private boolean mDemoMode;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            updateClock();
        } else if (mDemoMode && command.equals(COMMAND_CLOCK)) {
            String millis = args.getString("millis");
            String hhmm = args.getString("hhmm");
            if (millis != null) {
                mCalendar.setTimeInMillis(Long.parseLong(millis));
            } else if (hhmm != null && hhmm.length() == 4) {
                int hh = Integer.parseInt(hhmm.substring(0, 2));
                int mm = Integer.parseInt(hhmm.substring(2));
                boolean is24 = DateFormat.is24HourFormat(
                        getContext(), ActivityManager.getCurrentUser());
                if (is24) {
                    mCalendar.set(Calendar.HOUR_OF_DAY, hh);
                } else {
                    mCalendar.set(Calendar.HOUR, hh);
                }
                mCalendar.set(Calendar.MINUTE, mm);
            }
            setText(getSmallTime());
            setContentDescription(mContentDescriptionFormat.format(mCalendar.getTime()));
        }
    }

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (mSecondsHandler != null) {
                    mSecondsHandler.removeCallbacks(mSecondTick);
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mSecondsHandler != null) {
                    mSecondsHandler.postAtTime(mSecondTick,
                            SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
                }
            }
        }
    };

    private final Runnable mSecondTick = new Runnable() {
        @Override
        public void run() {
            if (mCalendar != null) {
                updateClock();
            }
            mSecondsHandler.postAtTime(this, SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
        }
    };

    public boolean isClockEnabled() {
        return mClockEnabled;
    }

    public boolean isCentered() {
        return mClockPosition == CLOCK_CENTER;
    }

    protected synchronized void updateClockPosition() {
        if (!mInitDone) return;
        final Clock thiz = this;
        /* A handler is used to prevent a NullPointerException happening in
           ViewGroup:2923 (dispatchAttachedToWindow) because this is called
           from onAttachedToWindow when starting SystemUI and we can't remove
           or add views while it is being dispatched so we have to run it after
           dispatchAttachedToWindow, achieved by posting it on the handler */
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                ViewGroup parent = (ViewGroup) getParent();
                PhoneStatusBarView centerParent =
                    (PhoneStatusBarView) mStartParent.getParent();
                if (centerParent == null || parent == null
                        || mClockPosition == mLastClockPosition
                        || !mAttached) return;
                mLastClockPosition = mClockPosition;
                switch (mClockPosition) {
                    case CLOCK_LEFT:
                        // Move view to beginning of everything
                        parent.removeView(thiz);
                        mStartParent.addView(thiz, 0, mInitialLp);
                        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        setPadding(mClockEndPadding, 0, mClockStartPadding, 0);
                        break;
                    case CLOCK_CENTER:
                        // Move view next to notification icons and center
                        parent.removeView(thiz);
                        FrameLayout.LayoutParams params =
                                new FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    Gravity.CENTER
                                );
                        centerParent.addView(thiz, 0, params);
                        setGravity(Gravity.CENTER);
                        setPadding(0, 0, 0, 0);
                        break;
                    case CLOCK_RIGHT:
                        // Move view to end of everything
                        parent.removeView(thiz);
                        mStartParent.addView(
                            thiz, parent.getChildCount(), mInitialLp);
                        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        setPadding(mClockStartPadding, 0, mClockEndPadding, 0);
                        break;
                    default: break;
                }
            }
        });
    }

    public void updateCustomSettings() {
        if (!mClockEnabled) {
            if (mReceiverRegistered) {
                setVisibility(View.GONE);
                getContext().unregisterReceiver(mIntentReceiver);
                mReceiverRegistered = false;
                isSupposedToShow = false;
            }
        } else {
            if (!mReceiverRegistered) {
                getContext().registerReceiverAsUser(
                    mIntentReceiver, UserHandle.ALL, mFilter,
                    null, getHandler());
                mReceiverRegistered = true;
                isSupposedToShow = true;
                setVisibility(View.VISIBLE);
            }
            updateClockPosition();
            if (mClockFormatString.isEmpty() || mRefreshClock) {
                updateShowSeconds(true);
                updateClock();
            }
        }
    }

    class SettingsObserver extends ContentObserver {

        private ContentResolver resolver;

        public SettingsObserver(Handler handler) {
            super(handler);
            resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_AM_PM_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK_SECONDS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_DATE_DISPLAY),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_DATE_STYLE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_DATE_POSITION),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_DATE_FORMAT),
                    false, this, UserHandle.USER_ALL);
            onChange(true);
        }

        @Override
        public void onChange(boolean selfChange) {
            mClockPosition = Settings.System.getIntForUser(resolver,
                    Settings.System.STATUSBAR_CLOCK_STYLE,
                    CLOCK_RIGHT, UserHandle.USER_CURRENT);
            mClockEnabled = Settings.System.getIntForUser(resolver,
                    Settings.System.STATUS_BAR_CLOCK, 1,
                    UserHandle.USER_CURRENT) == 1;
            mShowSeconds = Settings.System.getIntForUser(resolver,
                    Settings.System.STATUS_BAR_CLOCK_SECONDS, 0,
                    UserHandle.USER_CURRENT) == 1;
            int amPmStyle = Settings.System.getIntForUser(resolver,
                    Settings.System.STATUSBAR_CLOCK_AM_PM_STYLE,
                    DateFormat.is24HourFormat(getContext(),
                        ActivityManager.getCurrentUser()) ? STYLE_GONE // *
                                                          : STYLE_NORMAL,
                    UserHandle.USER_CURRENT);
            // *: Note that this has to be reversed (fixed below)
            amPmStyle = 2 - amPmStyle; // Otherwise is reversed
            if (amPmStyle != mAmPmStyle) {
                mAmPmStyle = amPmStyle;
                mClockFormatString = ""; // force refresh
            }

            mDateStyle = Settings.System.getIntForUser(resolver,
                    Settings.System.STATUSBAR_CLOCK_DATE_DISPLAY, 0,
                    UserHandle.USER_CURRENT); // *
            mDatePosition = Settings.System.getIntForUser(resolver,
                    Settings.System.STATUSBAR_CLOCK_DATE_POSITION, 0,
                    UserHandle.USER_CURRENT); // *
            mDateCase = Settings.System.getIntForUser(resolver,
                    Settings.System.STATUSBAR_CLOCK_DATE_STYLE, DATE_REGULAR,
                    UserHandle.USER_CURRENT);
            mDateFormat = Settings.System.getString(resolver,
                    Settings.System.STATUSBAR_CLOCK_DATE_FORMAT, "");

            // Since we are sharing the constants above and a few ones
            // are handled differently or reversed, do the conversion here
            mDateStyle = 2 - mDateStyle;
            mDatePosition = (1 - mDatePosition) * 2; // (0 or 1) * 2 = (0 or 2)

            mRefreshClock = true;
            if (!selfChange) updateCustomSettings();
        }

    }
}

