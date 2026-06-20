package com.example.stardustchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

public final class LocaleHelper {
    public static final String PREFS_NAME = "ApiSettings";
    public static final String KEY_LANGUAGE = "app_language";
    public static final String LANGUAGE_EN = "en";
    public static final String LANGUAGE_ZH = "zh";

    private LocaleHelper() {
    }

    public static Context applyLocale(Context context) {
        return applyLocale(context, getLanguage(context));
    }

    public static Context applyLocale(Context context, String language) {
        Locale locale = LANGUAGE_ZH.equals(language) ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.setLocale(locale);
        }
        return context.createConfigurationContext(config);
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_LANGUAGE, "");
        if (LANGUAGE_ZH.equals(stored) || LANGUAGE_EN.equals(stored)) {
            return stored;
        }
        return Locale.getDefault().getLanguage().startsWith("zh") ? LANGUAGE_ZH : LANGUAGE_EN;
    }
}
