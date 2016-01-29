/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.v7.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.TextView;

class AppCompatTextHelperV17 extends AppCompatTextHelper {

    private static final int[] VIEW_ATTRS_v17 = {
            android.R.attr.drawableStart, android.R.attr.drawableEnd };

    private TintInfo mDrawableStartTint;
    private TintInfo mDrawableEndTint;

    AppCompatTextHelperV17(TextView view) {
        super(view);
    }

    void loadFromAttributes(AttributeSet attrs, int defStyleAttr) {
        super.loadFromAttributes(attrs, defStyleAttr);

        final Context context = mView.getContext();
        final TintManager tintManager = TintManager.get(context);

        TypedArray a = context.obtainStyledAttributes(attrs, VIEW_ATTRS_v17, defStyleAttr, 0);
        if (a.hasValue(0)) {
            mDrawableStartTint = createTintInfo(context, tintManager, a.getResourceId(0, 0));
        }
        if (a.hasValue(1)) {
            mDrawableEndTint = createTintInfo(context, tintManager, a.getResourceId(1, 0));
        }
        a.recycle();
    }

    @Override
    void applyCompoundDrawablesTints() {
        super.applyCompoundDrawablesTints();

        if (mDrawableStartTint != null || mDrawableEndTint != null) {
            final Drawable[] compoundDrawables = mView.getCompoundDrawablesRelative();
            applyCompoundDrawableTint(compoundDrawables[0], mDrawableStartTint);
            applyCompoundDrawableTint(compoundDrawables[2], mDrawableEndTint);
        }
    }
}
