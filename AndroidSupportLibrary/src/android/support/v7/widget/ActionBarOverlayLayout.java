/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcelable;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorCompat;
import android.support.v4.view.ViewPropertyAnimatorListener;
import android.support.v4.view.ViewPropertyAnimatorListenerAdapter;
import android.support.v4.widget.ScrollerCompat;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.view.menu.MenuPresenter;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.yc.androidsupportlibrary.R;

/**
 * Special layout for the containing of an overlay action bar (and its content) to correctly handle
 * fitting system windows when the content has request that its layout ignore them.
 *
 * @hide
 */
public class ActionBarOverlayLayout extends ViewGroup implements DecorContentParent,
        NestedScrollingParent {
    private static final String TAG = "ActionBarOverlayLayout";

    private int mActionBarHeight;
    //private WindowDecorActionBar mActionBar;
    private int mWindowVisibility = View.VISIBLE;

    // The main UI elements that we handle the layout of.
    private ContentFrameLayout mContent;
    private ActionBarContainer mActionBarTop;

    // Some interior UI elements.
    private DecorToolbar mDecorToolbar;

    // Content overlay drawable - generally the action bar's shadow
    private Drawable mWindowContentOverlay;
    private boolean mIgnoreWindowContentOverlay;

    private boolean mOverlayMode;
    private boolean mHasNonEmbeddedTabs;
    private boolean mHideOnContentScroll;
    private boolean mAnimatingForFling;
    private int mHideOnContentScrollReference;
    private int mLastSystemUiVisibility;
    private final Rect mBaseContentInsets = new Rect();
    private final Rect mLastBaseContentInsets = new Rect();
    private final Rect mContentInsets = new Rect();
    private final Rect mBaseInnerInsets = new Rect();
    private final Rect mInnerInsets = new Rect();
    private final Rect mLastInnerInsets = new Rect();

    private ActionBarVisibilityCallback mActionBarVisibilityCallback;

    private final int ACTION_BAR_ANIMATE_DELAY = 600; // ms

    private ScrollerCompat mFlingEstimator;

    private ViewPropertyAnimatorCompat mCurrentActionBarTopAnimator;

    private final ViewPropertyAnimatorListener mTopAnimatorListener
            = new ViewPropertyAnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(View view) {
            mCurrentActionBarTopAnimator = null;
            mAnimatingForFling = false;
        }

        @Override
        public void onAnimationCancel(View view) {
            mCurrentActionBarTopAnimator = null;
            mAnimatingForFling = false;
        }
    };

    private final Runnable mRemoveActionBarHideOffset = new Runnable() {
        public void run() {
            haltActionBarHideOffsetAnimations();
            mCurrentActionBarTopAnimator = ViewCompat.animate(mActionBarTop).translationY(0)
                    .setListener(mTopAnimatorListener);
        }
    };

    private final Runnable mAddActionBarHideOffset = new Runnable() {
        public void run() {
            haltActionBarHideOffsetAnimations();
            mCurrentActionBarTopAnimator = ViewCompat.animate(mActionBarTop)
                    .translationY(-mActionBarTop.getHeight())
                    .setListener(mTopAnimatorListener);
        }
    };

    static final int[] ATTRS = new int [] {
            R.attr.actionBarSize,
            android.R.attr.windowContentOverlay
    };

    private final NestedScrollingParentHelper mParentHelper;

    public ActionBarOverlayLayout(Context context) {
        this(context, null);
    }

    public ActionBarOverlayLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);

        mParentHelper = new NestedScrollingParentHelper(this);
    }

    private void init(Context context) {
        TypedArray ta = getContext().getTheme().obtainStyledAttributes(ATTRS);
        mActionBarHeight = ta.getDimensionPixelSize(0, 0);
        mWindowContentOverlay = ta.getDrawable(1);
        setWillNotDraw(mWindowContentOverlay == null);
        ta.recycle();

        mIgnoreWindowContentOverlay = context.getApplicationInfo().targetSdkVersion <
                Build.VERSION_CODES.KITKAT;

        mFlingEstimator = ScrollerCompat.create(context);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        haltActionBarHideOffsetAnimations();
    }

    public void setActionBarVisibilityCallback(ActionBarVisibilityCallback cb) {
        mActionBarVisibilityCallback = cb;
        if (getWindowToken() != null) {
            // This is being initialized after being added to a window;
            // make sure to update all state now.
            mActionBarVisibilityCallback.onWindowVisibilityChanged(mWindowVisibility);
            if (mLastSystemUiVisibility != 0) {
                int newVis = mLastSystemUiVisibility;
                onWindowSystemUiVisibilityChanged(newVis);
                ViewCompat.requestApplyInsets(this);
            }
        }
    }

    public void setOverlayMode(boolean overlayMode) {
        mOverlayMode = overlayMode;

        /*
         * Drawing the window content overlay was broken before K so starting to draw it
         * again unexpectedly will cause artifacts in some apps. They should fix it.
         */
        mIgnoreWindowContentOverlay = overlayMode &&
                getContext().getApplicationInfo().targetSdkVersion <
                        Build.VERSION_CODES.KITKAT;
    }

    public boolean isInOverlayMode() {
        return mOverlayMode;
    }

    public void setHasNonEmbeddedTabs(boolean hasNonEmbeddedTabs) {
        mHasNonEmbeddedTabs = hasNonEmbeddedTabs;
    }

    public void setShowingForActionMode(boolean showing) {
        // TODO: Add workaround for this
//        if (showing) {
//            // Here's a fun hack: if the status bar is currently being hidden,
//            // and the application has asked for stable content insets, then
//            // we will end up with the action mode action bar being shown
//            // without the status bar, but moved below where the status bar
//            // would be.  Not nice.  Trying to have this be positioned
//            // correctly is not easy (basically we need yet *another* content
//            // inset from the window manager to know where to put it), so
//            // instead we will just temporarily force the status bar to be shown.
//            if ((getWindowSystemUiVisibility() & (SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                    | SYSTEM_UI_FLAG_LAYOUT_STABLE))
//                    == (SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | SYSTEM_UI_FLAG_LAYOUT_STABLE)) {
//                setDisabledSystemUiVisibility(SYSTEM_UI_FLAG_FULLSCREEN);
//            }
//        } else {
//            setDisabledSystemUiVisibility(0);
//        }
    }

    protected void onConfigurationChanged(Configuration newConfig) {
        if (Build.VERSION.SDK_INT >= 8) {
            super.onConfigurationChanged(newConfig);
        }
        init(getContext());
        ViewCompat.requestApplyInsets(this);
    }

    public void onWindowSystemUiVisibilityChanged(int visible) {
        if (Build.VERSION.SDK_INT >= 16) {
            super.onWindowSystemUiVisibilityChanged(visible);
        }
        pullChildren();
        final int diff = mLastSystemUiVisibility ^ visible;
        mLastSystemUiVisibility = visible;
        final boolean barVisible = (visible & SYSTEM_UI_FLAG_FULLSCREEN) == 0;
        final boolean stable = (visible & SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0;
        if (mActionBarVisibilityCallback != null) {
            // We want the bar to be visible if it is not being hidden,
            // or the app has not turned on a stable UI mode (meaning they
            // are performing explicit layout around the action bar).
            mActionBarVisibilityCallback.enableContentAnimations(!stable);
            if (barVisible || !stable) mActionBarVisibilityCallback.showForSystem();
            else mActionBarVisibilityCallback.hideForSystem();
        }
        if ((diff & SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
            if (mActionBarVisibilityCallback != null) {
                ViewCompat.requestApplyInsets(this);
            }
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisibility = visibility;
        if (mActionBarVisibilityCallback != null) {
            mActionBarVisibilityCallback.onWindowVisibilityChanged(visibility);
        }
    }

    private boolean applyInsets(View view, Rect insets, boolean left, boolean top,
            boolean bottom, boolean right) {
        boolean changed = false;
        LayoutParams lp = (LayoutParams)view.getLayoutParams();
        if (left && lp.leftMargin != insets.left) {
            changed = true;
            lp.leftMargin = insets.left;
        }
        if (top && lp.topMargin != insets.top) {
            changed = true;
            lp.topMargin = insets.top;
        }
        if (right && lp.rightMargin != insets.right) {
            changed = true;
            lp.rightMargin = insets.right;
        }
        if (bottom && lp.bottomMargin != insets.bottom) {
            changed = true;
            lp.bottomMargin = insets.bottom;
        }
        return changed;
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        pullChildren();

        final int vis = ViewCompat.getWindowSystemUiVisibility(this);
        final boolean stable = (vis & SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0;
        final Rect systemInsets = insets;

        // The top action bar is always within the content area.
        boolean changed = applyInsets(mActionBarTop, systemInsets, true, true, false, true);

        mBaseInnerInsets.set(systemInsets);
        ViewUtils.computeFitSystemWindows(this, mBaseInnerInsets, mBaseContentInsets);
        if (!mLastBaseContentInsets.equals(mBaseContentInsets)) {
            changed = true;
            mLastBaseContentInsets.set(mBaseContentInsets);
        }

        if (changed) {
            requestLayout();
        }

        // We don't do any more at this point.  To correctly compute the content/inner
        // insets in all cases, we need to know the measured size of the various action
        // bar elements. fitSystemWindows() happens before the measure pass, so we can't
        // do that here. Instead we will take this up in onMeasure().
        return true;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        pullChildren();

        int maxHeight = 0;
        int maxWidth = 0;
        int childState = 0;

        int topInset = 0;
        int bottomInset = 0;

        measureChildWithMargins(mActionBarTop, widthMeasureSpec, 0, heightMeasureSpec, 0);
        LayoutParams lp = (LayoutParams) mActionBarTop.getLayoutParams();
        maxWidth = Math.max(maxWidth,
                mActionBarTop.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
        maxHeight = Math.max(maxHeight,
                mActionBarTop.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        childState = ViewUtils.combineMeasuredStates(childState,
                ViewCompat.getMeasuredState(mActionBarTop));

        final int vis = ViewCompat.getWindowSystemUiVisibility(this);
        final boolean stable = (vis & SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0;

        if (stable) {
            // This is the standard space needed for the action bar.  For stable measurement,
            // we can't depend on the size currently reported by it -- this must remain constant.
            topInset = mActionBarHeight;
            if (mHasNonEmbeddedTabs) {
                final View tabs = mActionBarTop.getTabContainer();
                if (tabs != null) {
                    // If tabs are not embedded, increase space on top to account for them.
                    topInset += mActionBarHeight;
                }
            }
        } else if (mActionBarTop.getVisibility() != GONE) {
            // This is the space needed on top of the window for all of the action bar
            // and tabs.
            topInset = mActionBarTop.getMeasuredHeight();
        }

        // If the window has not requested system UI layout flags, we need to
        // make sure its content is not being covered by system UI...  though it
        // will still be covered by the action bar if they have requested it to
        // overlay.
        mContentInsets.set(mBaseContentInsets);
        mInnerInsets.set(mBaseInnerInsets);
        if (!mOverlayMode && !stable) {
            mContentInsets.top += topInset;
            mContentInsets.bottom += bottomInset;
        } else {
            mInnerInsets.top += topInset;
            mInnerInsets.bottom += bottomInset;
        }
        applyInsets(mContent, mContentInsets, true, true, true, true);

        if (!mLastInnerInsets.equals(mInnerInsets)) {
            // If the inner insets have changed, we need to dispatch this down to
            // the app's fitSystemWindows().  We do this before measuring the content
            // view to keep the same semantics as the normal fitSystemWindows() call.
            mLastInnerInsets.set(mInnerInsets);

            mContent.dispatchFitSystemWindows(mInnerInsets);
        }

        measureChildWithMargins(mContent, widthMeasureSpec, 0, heightMeasureSpec, 0);
        lp = (LayoutParams) mContent.getLayoutParams();
        maxWidth = Math.max(maxWidth,
                mContent.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
        maxHeight = Math.max(maxHeight,
                mContent.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        childState = ViewUtils.combineMeasuredStates(childState,
                ViewCompat.getMeasuredState(mContent));

        // Account for padding too
        maxWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();

        // Check against our minimum height and width
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        setMeasuredDimension(
                ViewCompat.resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                ViewCompat.resolveSizeAndState(maxHeight, heightMeasureSpec,
                        childState << MEASURED_HEIGHT_STATE_SHIFT));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int count = getChildCount();

        final int parentLeft = getPaddingLeft();
        final int parentRight = right - left - getPaddingRight();

        final int parentTop = getPaddingTop();
        final int parentBottom = bottom - top - getPaddingBottom();

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft = parentLeft + lp.leftMargin;
                int childTop = parentTop + lp.topMargin;

                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }
        }
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);
        if (mWindowContentOverlay != null && !mIgnoreWindowContentOverlay) {
            final int top = mActionBarTop.getVisibility() == VISIBLE ?
                    (int) (mActionBarTop.getBottom() + ViewCompat.getTranslationY(mActionBarTop) + 0.5f)
                    : 0;
            mWindowContentOverlay.setBounds(0, top, getWidth(),
                    top + mWindowContentOverlay.getIntrinsicHeight());
            mWindowContentOverlay.draw(c);
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int axes) {
        if ((axes & SCROLL_AXIS_VERTICAL) == 0 || mActionBarTop.getVisibility() != VISIBLE) {
            return false;
        }
        return mHideOnContentScroll;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        mParentHelper.onNestedScrollAccepted(child, target, axes);
        mHideOnContentScrollReference = getActionBarHideOffset();
        haltActionBarHideOffsetAnimations();
        if (mActionBarVisibilityCallback != null) {
            mActionBarVisibilityCallback.onContentScrollStarted();
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed) {
        mHideOnContentScrollReference += dyConsumed;
        setActionBarHideOffset(mHideOnContentScrollReference);
    }

    @Override
    public void onStopNestedScroll(View target) {
        if (mHideOnContentScroll && !mAnimatingForFling) {
            if (mHideOnContentScrollReference <= mActionBarTop.getHeight()) {
                postRemoveActionBarHideOffset();
            } else {
                postAddActionBarHideOffset();
            }
        }
        if (mActionBarVisibilityCallback != null) {
            mActionBarVisibilityCallback.onContentScrollStopped();
        }
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        if (!mHideOnContentScroll || !consumed) {
            return false;
        }
        if (shouldHideActionBarOnFling(velocityX, velocityY)) {
            addActionBarHideOffset();
        } else {
            removeActionBarHideOffset();
        }
        mAnimatingForFling = true;
        return true;
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        // no-op
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public int getNestedScrollAxes() {
        return mParentHelper.getNestedScrollAxes();
    }

    void pullChildren() {
        if (mContent == null) {
            mContent = (ContentFrameLayout) findViewById(R.id.action_bar_activity_content);
            mActionBarTop = (ActionBarContainer) findViewById(R.id.action_bar_container);
            mDecorToolbar = getDecorToolbar(findViewById(R.id.action_bar));
        }
    }

    private DecorToolbar getDecorToolbar(View view) {
        if (view instanceof DecorToolbar) {
            return (DecorToolbar) view;
        } else if (view instanceof Toolbar) {
            return ((Toolbar) view).getWrapper();
        } else {
            throw new IllegalStateException("Can't make a decor toolbar out of " +
                    view.getClass().getSimpleName());
        }
    }

    public void setHideOnContentScrollEnabled(boolean hideOnContentScroll) {
        if (hideOnContentScroll != mHideOnContentScroll) {
            mHideOnContentScroll = hideOnContentScroll;
            if (!hideOnContentScroll) {
                haltActionBarHideOffsetAnimations();
                setActionBarHideOffset(0);
            }
        }
    }

    public boolean isHideOnContentScrollEnabled() {
        return mHideOnContentScroll;
    }

    public int getActionBarHideOffset() {
        return mActionBarTop != null ? -((int) ViewCompat.getTranslationY(mActionBarTop)) : 0;
    }

    public void setActionBarHideOffset(int offset) {
        haltActionBarHideOffsetAnimations();
        final int topHeight = mActionBarTop.getHeight();
        offset = Math.max(0, Math.min(offset, topHeight));
        ViewCompat.setTranslationY(mActionBarTop, -offset);
    }

    private void haltActionBarHideOffsetAnimations() {
        removeCallbacks(mRemoveActionBarHideOffset);
        removeCallbacks(mAddActionBarHideOffset);
        if (mCurrentActionBarTopAnimator != null) {
            mCurrentActionBarTopAnimator.cancel();
        }
    }

    private void postRemoveActionBarHideOffset() {
        haltActionBarHideOffsetAnimations();
        postDelayed(mRemoveActionBarHideOffset, ACTION_BAR_ANIMATE_DELAY);
    }

    private void postAddActionBarHideOffset() {
        haltActionBarHideOffsetAnimations();
        postDelayed(mAddActionBarHideOffset, ACTION_BAR_ANIMATE_DELAY);
    }

    private void removeActionBarHideOffset() {
        haltActionBarHideOffsetAnimations();
        mRemoveActionBarHideOffset.run();
    }

    private void addActionBarHideOffset() {
        haltActionBarHideOffsetAnimations();
        mAddActionBarHideOffset.run();
    }

    private boolean shouldHideActionBarOnFling(float velocityX, float velocityY) {
        mFlingEstimator.fling(0, 0, 0, (int) velocityY, 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        final int finalY = mFlingEstimator.getFinalY();
        return finalY > mActionBarTop.getHeight();
    }

    @Override
    public void setWindowCallback(Window.Callback cb) {
        pullChildren();
        mDecorToolbar.setWindowCallback(cb);
    }

    @Override
    public void setWindowTitle(CharSequence title) {
        pullChildren();
        mDecorToolbar.setWindowTitle(title);
    }

    @Override
    public CharSequence getTitle() {
        pullChildren();
        return mDecorToolbar.getTitle();
    }

    @Override
    public void initFeature(int windowFeature) {
        pullChildren();
        switch (windowFeature) {
            case Window.FEATURE_PROGRESS:
                mDecorToolbar.initProgress();
                break;
            case Window.FEATURE_INDETERMINATE_PROGRESS:
                mDecorToolbar.initIndeterminateProgress();
                break;
            case AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR_OVERLAY:
                setOverlayMode(true);
                break;
        }
    }

    @Override
    public void setUiOptions(int uiOptions) {
        // Split Action Bar not included.
    }

    @Override
    public boolean hasIcon() {
        pullChildren();
        return mDecorToolbar.hasIcon();
    }

    @Override
    public boolean hasLogo() {
        pullChildren();
        return mDecorToolbar.hasLogo();
    }

    @Override
    public void setIcon(int resId) {
        pullChildren();
        mDecorToolbar.setIcon(resId);
    }

    @Override
    public void setIcon(Drawable d) {
        pullChildren();
        mDecorToolbar.setIcon(d);
    }

    @Override
    public void setLogo(int resId) {
        pullChildren();
        mDecorToolbar.setLogo(resId);
    }

    @Override
    public boolean canShowOverflowMenu() {
        pullChildren();
        return mDecorToolbar.canShowOverflowMenu();
    }

    @Override
    public boolean isOverflowMenuShowing() {
        pullChildren();
        return mDecorToolbar.isOverflowMenuShowing();
    }

    @Override
    public boolean isOverflowMenuShowPending() {
        pullChildren();
        return mDecorToolbar.isOverflowMenuShowPending();
    }

    @Override
    public boolean showOverflowMenu() {
        pullChildren();
        return mDecorToolbar.showOverflowMenu();
    }

    @Override
    public boolean hideOverflowMenu() {
        pullChildren();
        return mDecorToolbar.hideOverflowMenu();
    }

    @Override
    public void setMenuPrepared() {
        pullChildren();
        mDecorToolbar.setMenuPrepared();
    }

    @Override
    public void setMenu(Menu menu, MenuPresenter.Callback cb) {
        pullChildren();
        mDecorToolbar.setMenu(menu, cb);
    }

    @Override
    public void saveToolbarHierarchyState(SparseArray<Parcelable> toolbarStates) {
        pullChildren();
        mDecorToolbar.saveHierarchyState(toolbarStates);
    }

    @Override
    public void restoreToolbarHierarchyState(SparseArray<Parcelable> toolbarStates) {
        pullChildren();
        mDecorToolbar.restoreHierarchyState(toolbarStates);
    }

    @Override
    public void dismissPopups() {
        pullChildren();
        mDecorToolbar.dismissPopupMenus();
    }

    public static class LayoutParams extends MarginLayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }
    }

    public interface ActionBarVisibilityCallback {
        void onWindowVisibilityChanged(int visibility);
        void showForSystem();
        void hideForSystem();
        void enableContentAnimations(boolean enable);
        void onContentScrollStarted();
        void onContentScrollStopped();
    }
}
