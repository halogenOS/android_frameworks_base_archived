/*
 * Copyright (C) 2017 The halogenOS Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.internal.logging.MetricsProto.MetricsEvent;

public class NotificationsTile extends QSTile<QSTile.State> {

    private String mNotif = "";


    public NotificationsTile(Host host) {
        super(host);
    }

    @Override
    public State newTileState() {
        return new QSTile.State();
    }

    @Override
    public void handleClick() {
        switch(mNotif){
            case "":
                mNotif = Settings.System.STATUS_BAR_SHOW_TICKER;
                break;
            case Settings.System.STATUS_BAR_SHOW_TICKER:
                mNotif = Settings.System.KEY_ENABLE_HEADSUP_NOTIFICATIONS;
                break;
            case Settings.System.KEY_ENABLE_HEADSUP_NOTIFICATIONS:
                mNotif = "";
                break;
        }
        refreshState();
    }

    @Override
    public void handleLongClick() {
        Intent intent = new Intent("android.settings.CUSTOMIZATIONS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        mHost.startActivityDismissingKeyguard(intent);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_notifications_none_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }

    @Override
    protected void handleUpdateState(QSTile.State state, Object arg) {
        switch (mNotif){
            case "":
                state.label = mContext.getString(R.string.quick_settings_notifications_none_label);
                state.icon = ResourceIcon.get(R.drawable.ic_qs_heads_up_off);
                Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.KEY_ENABLE_HEADSUP_NOTIFICATIONS,0);
                Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_SHOW_TICKER,0);
                break;
            case Settings.System.STATUS_BAR_SHOW_TICKER:
                state.label = mContext.getString(R.string.quick_settings_notifications_ticker_label);
                state.icon = ResourceIcon.get(R.drawable.ic_qs_notifications_ticker);
                Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.KEY_ENABLE_HEADSUP_NOTIFICATIONS,0);
                Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_SHOW_TICKER,1);
                break;
            case Settings.System.KEY_ENABLE_HEADSUP_NOTIFICATIONS:
                state.label = mContext.getString(R.string.quick_settings_notifications_headsup_label);
                state.icon = ResourceIcon.get(R.drawable.ic_qs_heads_up_on);
                Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.KEY_ENABLE_HEADSUP_NOTIFICATIONS,1);
                Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_SHOW_TICKER,0);
                break;
        }
    }

    @Override
    public void setListening(boolean listening) {
    }
}
