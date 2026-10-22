package com.fanok.audiobooks;

import android.content.Context;

/**
 * The app is Russian-only: there is no locale to apply, and night mode is set through
 * {@link AppThemes#applyNightMode}, never by rewriting {@code Configuration.uiMode}. These shims keep the remaining
 * {@code attachBaseContext} callers compiling until they are deleted; they return the context unchanged.
 */
public class LocaleManager {

    public static Context onAttach(Context context) {
        return context;
    }

    public static Context onAttach(Context context, String ignoredDefaultLanguage) {
        return context;
    }
}
