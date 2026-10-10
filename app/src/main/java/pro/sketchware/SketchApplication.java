package pro.sketchware;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.firebase.FirebaseApp; // أضف هذه المكتبة

import pro.sketchware.activities.tools.CollectErrorActivity;

import pro.sketchware.core.project.ProjectListManager;
import pro.sketchware.activities.settings.fragments.language.LanguageOverrideContextWrapper;
import pro.sketchware.activities.settings.fragments.language.LanguageOverrideManager;
import pro.sketchware.util.CrashLogManager;
import pro.sketchware.util.theme.ThemeManager;

public class SketchApplication extends Application {
    private static Context mApplicationContext;
    private static Activity currentActivity;
    private static Context cachedLocaleContext;
    private static String cachedLocaleTag;

    public static Context getAppContext() {
        return mApplicationContext;
    }

    public static Context getContext() {
        if (currentActivity != null) {
            return currentActivity;
        }
        LocaleListCompat appLocales = AppCompatDelegate.getApplicationLocales();
        if (!appLocales.isEmpty() && appLocales.get(0) != null) {
            String tag = appLocales.get(0).toLanguageTag();
            if (cachedLocaleContext != null && tag.equals(cachedLocaleTag)) {
                return cachedLocaleContext;
            }
            Configuration config = new Configuration(
                    mApplicationContext.getResources().getConfiguration());
            config.setLocale(appLocales.get(0));
            cachedLocaleContext = LanguageOverrideContextWrapper.wrap(
                    mApplicationContext.createConfigurationContext(config));
            cachedLocaleTag = tag;
            return cachedLocaleContext;
        }
        return LanguageOverrideContextWrapper.wrap(mApplicationContext);
    }

    @Override
    public void onCreate() {
        super.onCreate(); // يفضل استدعاء super.onCreate() أولاً أو قبل العمليات المرتبطة بالنظام
        
        // تهيئة فايربيس لمنع حدوث Crash عند مزامنة أو حفظ مفاتيح API
        FirebaseApp.initializeApp(this);

        mApplicationContext = getApplicationContext();
        ProjectListManager.initializeDb(mApplicationContext, false);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) { currentActivity = activity; }
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) { currentActivity = activity; }
            @Override public void onActivityPaused(@NonNull Activity activity) { if (currentActivity == activity) currentActivity = null; }
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) { if (currentActivity == activity) currentActivity = null; }
        });
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String crashFilePath = CrashLogManager.writeCrashLog(thread, throwable);

            Intent intent = new Intent(getApplicationContext(), CollectErrorActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (crashFilePath != null) {
                intent.putExtra("crash_file", crashFilePath);
            } else {
                intent.putExtra("error", Log.getStackTraceString(throwable));
            }
            startActivity(intent);
            Process.killProcess(Process.myPid());
            System.exit(1);
        });
        
        LanguageOverrideManager.getInstance().init(this);
        ThemeManager.applyTheme(this, ThemeManager.getCurrentTheme(this));
    }
}
