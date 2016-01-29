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
 * limitations under the License.
 */

package android.support.v7.app;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.view.WindowCallbackWrapper;
import android.support.v7.view.menu.ListMenuPresenter;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.MenuPresenter;
import android.support.v7.widget.DecorToolbar;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.ToolbarWidgetWrapper;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.SpinnerAdapter;

import com.yc.androidsupportlibrary.R;

class ToolbarActionBar extends ActionBar {
    private DecorToolbar mDecorToolbar;
    private boolean mToolbarMenuPrepared;
    private Window.Callback mWindowCallback;
    private boolean mMenuCallbackSet;

    private boolean mLastMenuVisibility;
    private ArrayList<OnMenuVisibilityListener> mMenuVisibilityListeners = new ArrayList<>();

    private ListMenuPresenter mListMenuPresenter;

    private final Runnable mMenuInvalidator = new Runnable() {
        @Override
        public void run() {
            populateOptionsMenu();
        }
    };

    private final Toolbar.OnMenuItemClickListener mMenuClicker =
            new Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return mWindowCallback.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
                }
            };

    public ToolbarActionBar(Toolbar toolbar, CharSequence title, Window.Callback callback) {
        mDecorToolbar = new ToolbarWidgetWrapper(toolbar, false);
        mWindowCallback = new ToolbarCallbackWrapper(callback);
        mDecorToolbar.setWindowCallback(mWindowCallback);
        toolbar.setOnMenuItemClickListener(mMenuClicker);
        mDecorToolbar.setWindowTitle(title);
    }

    public Window.Callback getWrappedWindowCallback() {
        return mWindowCallback;
    }

    @Override
    public void setCustomView(View view) {
        setCustomView(view, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void setCustomView(View view, LayoutParams layoutParams) {
        if (view != null) {
            view.setLayoutParams(layoutParams);
        }
        mDecorToolbar.setCustomView(view);
    }

    @Override
    public void setCustomView(int resId) {
        final LayoutInflater inflater = LayoutInflater.from(mDecorToolbar.getContext());
        setCustomView(inflater.inflate(resId, mDecorToolbar.getViewGroup(), false));
    }

    @Override
    public void setIcon(int resId) {
        mDecorToolbar.setIcon(resId);
    }

    @Override
    public void setIcon(Drawable icon) {
        mDecorToolbar.setIcon(icon);
    }

    @Override
    public void setLogo(int resId) {
        mDecorToolbar.setLogo(resId);
    }

    @Override
    public void setLogo(Drawable logo) {
        mDecorToolbar.setLogo(logo);
    }

    @Override
    public void setStackedBackgroundDrawable(Drawable d) {
        // This space for rent (do nothing)
    }

    @Override
    public void setSplitBackgroundDrawable(Drawable d) {
        // This space for rent (do nothing)
    }

    @Override
    public void setHomeButtonEnabled(boolean enabled) {
        // If the nav button on a Toolbar is present, it's enabled. No-op.
    }

    @Override
    public void setElevation(float elevation) {
        ViewCompat.setElevation(mDecorToolbar.getViewGroup(), elevation);
    }

    @Override
    public float getElevation() {
        return ViewCompat.getElevation(mDecorToolbar.getViewGroup());
    }

    @Override
    public Context getThemedContext() {
        return mDecorToolbar.getContext();
    }

    @Override
    public boolean isTitleTruncated() {
        return super.isTitleTruncated();
    }

    @Override
    public void setHomeAsUpIndicator(Drawable indicator) {
        mDecorToolbar.setNavigationIcon(indicator);
    }

    @Override
    public void setHomeAsUpIndicator(int resId) {
        mDecorToolbar.setNavigationIcon(resId);
    }

    @Override
    public void setHomeActionContentDescription(CharSequence description) {
        mDecorToolbar.setNavigationContentDescription(description);
    }

    @Override
    public void setDefaultDisplayHomeAsUpEnabled(boolean enabled) {
        // Do nothing
    }

    @Override
    public void setHomeActionContentDescription(int resId) {
        mDecorToolbar.setNavigationContentDescription(resId);
    }

    @Override
    public void setShowHideAnimationEnabled(boolean enabled) {
        // This space for rent; no-op.
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

    @Override
    public void setListNavigationCallbacks(SpinnerAdapter adapter, OnNavigationListener callback) {
        mDecorToolbar.setDropdownParams(adapter, new NavItemSelectedListener(callback));
    }

    @Override
    public void setSelectedNavigationItem(int position) {
        switch (mDecorToolbar.getNavigationMode()) {
            case NAVIGATION_MODE_LIST:
                mDecorToolbar.setDropdownSelectedPosition(position);
                break;
            default:
                throw new IllegalStateException(
                        "setSelectedNavigationIndex not valid for current navigation mode");
        }
    }

    @Override
    public int getSelectedNavigationIndex() {
        return -1;
    }

    @Override
    public int getNavigationItemCount() {
        return 0;
    }

    @Override
    public void setTitle(CharSequence title) {
        mDecorToolbar.setTitle(title);
    }

    @Override
    public void setTitle(int resId) {
        mDecorToolbar.setTitle(resId != 0 ? mDecorToolbar.getContext().getText(resId) : null);
    }

    @Override
    public void setWindowTitle(CharSequence title) {
        mDecorToolbar.setWindowTitle(title);
    }

    @Override
    public void setSubtitle(CharSequence subtitle) {
        mDecorToolbar.setSubtitle(subtitle);
    }

    @Override
    public void setSubtitle(int resId) {
        mDecorToolbar.setSubtitle(resId != 0 ? mDecorToolbar.getContext().getText(resId) : null);
    }

    @Override
    public void setDisplayOptions(@DisplayOptions int options) {
        setDisplayOptions(options, 0xffffffff);
    }

    @Override
    public void setDisplayOptions(@DisplayOptions int options, @DisplayOptions int mask) {
        final int currentOptions = mDecorToolbar.getDisplayOptions();
        mDecorToolbar.setDisplayOptions(options & mask | currentOptions & ~mask);
    }

    @Override
    public void setDisplayUseLogoEnabled(boolean useLogo) {
        setDisplayOptions(useLogo ? DISPLAY_USE_LOGO : 0, DISPLAY_USE_LOGO);
    }

    @Override
    public void setDisplayShowHomeEnabled(boolean showHome) {
        setDisplayOptions(showHome ? DISPLAY_SHOW_HOME : 0, DISPLAY_SHOW_HOME);
    }

    @Override
    public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
        setDisplayOptions(showHomeAsUp ? DISPLAY_HOME_AS_UP : 0, DISPLAY_HOME_AS_UP);
    }

    @Override
    public void setDisplayShowTitleEnabled(boolean showTitle) {
        setDisplayOptions(showTitle ? DISPLAY_SHOW_TITLE : 0, DISPLAY_SHOW_TITLE);
    }

    @Override
    public void setDisplayShowCustomEnabled(boolean showCustom) {
        setDisplayOptions(showCustom ? DISPLAY_SHOW_CUSTOM : 0, DISPLAY_SHOW_CUSTOM);
    }

    @Override
    public void setBackgroundDrawable(@Nullable Drawable d) {
        mDecorToolbar.setBackgroundDrawable(d);
    }

    @Override
    public View getCustomView() {
        return mDecorToolbar.getCustomView();
    }

    @Override
    public CharSequence getTitle() {
        return mDecorToolbar.getTitle();
    }

    @Override
    public CharSequence getSubtitle() {
        return mDecorToolbar.getSubtitle();
    }

    @Override
    public int getNavigationMode() {
        return NAVIGATION_MODE_STANDARD;
    }

    @Override
    public void setNavigationMode(@NavigationMode int mode) {
        if (mode == ActionBar.NAVIGATION_MODE_TABS) {
            throw new IllegalArgumentException("Tabs not supported in this configuration");
        }
        mDecorToolbar.setNavigationMode(mode);
    }

    @Override
    public int getDisplayOptions() {
        return mDecorToolbar.getDisplayOptions();
    }

    @Override
    public Tab newTab() {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public void addTab(Tab tab) {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public void addTab(Tab tab, boolean setSelected) {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public void addTab(Tab tab, int position) {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public void addTab(Tab tab, int position, boolean setSelected) {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public void removeTab(Tab tab) {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public void removeTabAt(int position) {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public void removeAllTabs() {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public void selectTab(Tab tab) {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public Tab getSelectedTab() {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public Tab getTabAt(int index) {
        throw new UnsupportedOperationException(
                "Tabs are not supported in toolbar action bars");
    }

    @Override
    public int getTabCount() {
        return 0;
    }

    @Override
    public int getHeight() {
        return mDecorToolbar.getHeight();
    }

    @Override
    public void show() {
        // TODO: Consider a better transition for this.
        // Right now use no automatic transition so that the app can supply one if desired.
        mDecorToolbar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hide() {
        // TODO: Consider a better transition for this.
        // Right now use no automatic transition so that the app can supply one if desired.
        mDecorToolbar.setVisibility(View.GONE);
    }

    @Override
    public boolean isShowing() {
        return mDecorToolbar.getVisibility() == View.VISIBLE;
    }

    @Override
    public boolean openOptionsMenu() {
        return mDecorToolbar.showOverflowMenu();
    }

    @Override
    public boolean invalidateOptionsMenu() {
        mDecorToolbar.getViewGroup().removeCallbacks(mMenuInvalidator);
        ViewCompat.postOnAnimation(mDecorToolbar.getViewGroup(), mMenuInvalidator);
        return true;
    }

    @Override
    public boolean collapseActionView() {
        if (mDecorToolbar.hasExpandedActionView()) {
            mDecorToolbar.collapseActionView();
            return true;
        }
        return false;
    }

    void populateOptionsMenu() {
        final Menu menu = getMenu();
        final MenuBuilder mb = menu instanceof MenuBuilder ? (MenuBuilder) menu : null;
        if (mb != null) {
            mb.stopDispatchingItemsChanged();
        }
        try {
            menu.clear();
            if (!mWindowCallback.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu) ||
                    !mWindowCallback.onPreparePanel(Window.FEATURE_OPTIONS_PANEL, null, menu)) {
                menu.clear();
            }
        } finally {
            if (mb != null) {
                mb.startDispatchingItemsChanged();
            }
        }
    }

    @Override
    public boolean onMenuKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            openOptionsMenu();
        }
        return true;
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent ev) {
        Menu menu = getMenu();
        if (menu != null) {
            final KeyCharacterMap kmap = KeyCharacterMap.load(
                    ev != null ? ev.getDeviceId() : KeyCharacterMap.VIRTUAL_KEYBOARD);
            menu.setQwertyMode(kmap.getKeyboardType() != KeyCharacterMap.NUMERIC);
            menu.performShortcut(keyCode, ev, 0);
        }
        // This action bar always returns true for handling keyboard shortcuts.
        // This will block the window from preparing a temporary panel to handle
        // keyboard shortcuts.
        return true;
    }

    public void addOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
        mMenuVisibilityListeners.add(listener);
    }

    public void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
        mMenuVisibilityListeners.remove(listener);
    }

    public void dispatchMenuVisibilityChanged(boolean isVisible) {
        if (isVisible == mLastMenuVisibility) {
            return;
        }
        mLastMenuVisibility = isVisible;

        final int count = mMenuVisibilityListeners.size();
        for (int i = 0; i < count; i++) {
            mMenuVisibilityListeners.get(i).onMenuVisibilityChanged(isVisible);
        }
    }

    private View getListMenuView(Menu menu) {
        ensureListMenuPresenter(menu);

        if (menu == null || mListMenuPresenter == null) {
            return null;
        }

        if (mListMenuPresenter.getAdapter().getCount() > 0) {
            return (View) mListMenuPresenter.getMenuView(mDecorToolbar.getViewGroup());
        }
        return null;
    }

    private void ensureListMenuPresenter(Menu menu) {
        if (mListMenuPresenter == null && (menu instanceof MenuBuilder)) {
            MenuBuilder mb = (MenuBuilder) menu;

            Context context = mDecorToolbar.getContext();
            final TypedValue outValue = new TypedValue();
            final Resources.Theme widgetTheme = context.getResources().newTheme();
            widgetTheme.setTo(context.getTheme());

            // First apply the actionBarPopupTheme
            widgetTheme.resolveAttribute(R.attr.actionBarPopupTheme, outValue, true);
            if (outValue.resourceId != 0) {
                widgetTheme.applyStyle(outValue.resourceId, true);
            }

            // Apply the panelMenuListTheme
            widgetTheme.resolveAttribute(R.attr.panelMenuListTheme, outValue, true);
            if (outValue.resourceId != 0) {
                widgetTheme.applyStyle(outValue.resourceId, true);
            } else {
                widgetTheme.applyStyle(R.style.Theme_AppCompat_CompactMenu, true);
            }

            context = new ContextThemeWrapper(context, 0);
            context.getTheme().setTo(widgetTheme);

            // Finally create the list menu presenter
            mListMenuPresenter = new ListMenuPresenter(context, R.layout.abc_list_menu_item_layout);
            mListMenuPresenter.setCallback(new PanelMenuPresenterCallback());
            mb.addMenuPresenter(mListMenuPresenter);
        }
    }

    private class ToolbarCallbackWrapper extends WindowCallbackWrapper {
        public ToolbarCallbackWrapper(Window.Callback wrapped) {
            super(wrapped);
        }

        @Override
        public boolean onPreparePanel(int featureId, View view, Menu menu) {
            final boolean result = super.onPreparePanel(featureId, view, menu);
            if (result && !mToolbarMenuPrepared) {
                mDecorToolbar.setMenuPrepared();
                mToolbarMenuPrepared = true;
            }
            return result;
        }

        @Override
        public View onCreatePanelView(int featureId) {
            switch (featureId) {
                case Window.FEATURE_OPTIONS_PANEL:
                    final Menu menu = mDecorToolbar.getMenu();
                    if (onPreparePanel(featureId, null, menu) && onMenuOpened(featureId, menu)) {
                        return getListMenuView(menu);
                    }
                    break;
            }
            return super.onCreatePanelView(featureId);
        }
    }

    private Menu getMenu() {
        if (!mMenuCallbackSet) {
            mDecorToolbar.setMenuCallbacks(new ActionMenuPresenterCallback(),
                    new MenuBuilderCallback());
            mMenuCallbackSet = true;
        }
        return mDecorToolbar.getMenu();
    }

    private final class ActionMenuPresenterCallback implements MenuPresenter.Callback {
        private boolean mClosingActionMenu;

        @Override
        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            if (mWindowCallback != null) {
                mWindowCallback.onMenuOpened(AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR, subMenu);
                return true;
            }
            return false;
        }

        @Override
        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            if (mClosingActionMenu) {
                return;
            }

            mClosingActionMenu = true;
            mDecorToolbar.dismissPopupMenus();
            if (mWindowCallback != null) {
                mWindowCallback.onPanelClosed(AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR, menu);
            }
            mClosingActionMenu = false;
        }
    }

    private final class PanelMenuPresenterCallback implements MenuPresenter.Callback {
        @Override
        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            if (mWindowCallback != null) {
                mWindowCallback.onPanelClosed(Window.FEATURE_OPTIONS_PANEL, menu);
            }
        }

        @Override
        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            if (subMenu == null && mWindowCallback != null) {
                mWindowCallback.onMenuOpened(Window.FEATURE_OPTIONS_PANEL, subMenu);
            }
            return true;
        }
    }

    private final class MenuBuilderCallback implements MenuBuilder.Callback {

        @Override
        public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
            return false;
        }

        @Override
        public void onMenuModeChange(MenuBuilder menu) {
            if (mWindowCallback != null) {
                if (mDecorToolbar.isOverflowMenuShowing()) {
                    mWindowCallback.onPanelClosed(AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR, menu);
                } else if (mWindowCallback.onPreparePanel(Window.FEATURE_OPTIONS_PANEL,
                        null, menu)) {
                    mWindowCallback.onMenuOpened(AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR, menu);
                }
            }
        }
    }
}
