/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar;

import android.content.Context;
import android.content.ContentResolver;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.android.systemui.R;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;

public class SwitchHeadsupButton extends Button {
    private final Drawable mStaticEnableDrawable, mStaticDisableDrawable;
    private Drawable mActiveDrawable;

    public SwitchHeadsupButton(Context context) {
        this(context, null);
    }

    public SwitchHeadsupButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwitchHeadsupButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SwitchHeadsupButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mStaticEnableDrawable = getContext().getDrawable(R.drawable.ic_notification_on);
        mStaticEnableDrawable.setBounds(0, 0,
                mStaticEnableDrawable.getIntrinsicWidth(),
                mStaticEnableDrawable.getIntrinsicHeight());
        mStaticEnableDrawable.setCallback(this);
        mStaticDisableDrawable = getContext().getDrawable(R.drawable.ic_notification_off);
        mStaticDisableDrawable.setBounds(0, 0,
                mStaticDisableDrawable.getIntrinsicWidth(),
                mStaticDisableDrawable.getIntrinsicHeight());
        mStaticDisableDrawable.setCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        showButton();
        if(mActiveDrawable == null) return;
        canvas.save();
        int drawableHeight = mActiveDrawable.getBounds().height();
        int drawableWidth  = mActiveDrawable.getBounds().width();
        boolean isRtl = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        int dx = isRtl ? getWidth() / 2 + drawableHeight / 2 : getWidth() / 2 - drawableHeight / 2;
        canvas.translate(dx, getHeight() / 2.0f + drawableHeight /
                2.0f);
        canvas.scale(isRtl ? -1.0f : 1.0f, -1.0f);
        canvas.rotate(180, drawableWidth / 2, drawableHeight / 2);
        mActiveDrawable.draw(canvas);
        canvas.restore();
    }
    
    public void setDrawableWithSwitch(int headsUpEnabled) {
        mActiveDrawable = headsUpEnabled == 0 ? 
            mStaticEnableDrawable : mStaticDisableDrawable;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who)
                || who == mStaticDisableDrawable
                || who == mStaticEnableDrawable;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * This method returns the drawing rect for the view which is different from the regular
     * drawing rect, since we layout all children in the {@link NotificationStackScrollLayout} at
     * position 0 and usually the translation is neglected. The standard implementation doesn't
     * account for translation.
     *
     * @param outRect The (scrolled) drawing bounds of the view.
     */
    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        float translationX = ((ViewGroup) mParent).getTranslationX();
        float translationY = ((ViewGroup) mParent).getTranslationY();
        outRect.left += translationX;
        outRect.right += translationX;
        outRect.top += translationY;
        outRect.bottom += translationY;
    }

    public void showButton() {
        setDrawableWithSwitch(Settings.System.getInt(getContext().getContentResolver(),
            Settings.System.KEY_ENABLE_HEADSUP_NOTIFICATIONS, 1));
        invalidate();
    }

    /**
     * @return Whether the button is currently static and not being animated.
     */
    public boolean isButtonStatic() {
        return mActiveDrawable == mStaticEnableDrawable ||
                mActiveDrawable == mStaticDisableDrawable;
    }
}
