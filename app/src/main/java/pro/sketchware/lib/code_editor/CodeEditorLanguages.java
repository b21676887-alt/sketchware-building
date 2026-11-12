package pro.sketchware.lib.code_editor;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import pro.sketchware.util.LogUtil;
import pro.sketchware.SketchApplication;

public class CodeEditorLanguages {
        // Keep these in sync with the scopeName fields in languages.json.

    /** Java — handled by Sora's built-in JavaLanguage, not TextMate. */
    public static final String SCOPE_NAME_JAVA       = "source.java";
    /** Kotlin and Gradle Kotlin DSL (.gradle.kts). */
    public static final String SCOPE_NAME_KOTLIN     = "source.kotlin";
    /** Android / generic XML. */
    public static final String SCOPE_NAME_XML        = "text.xml";
    /** JSON data files. */
    public static final String SCOPE_NAME_JSON       = "source.json";
    /** Groovy — used for .gradle files. */
    public static final String SCOPE_NAME_GROOVY     = "source.groovy";
    /** C source files. */
    public static final String SCOPE_NAME_C          = "source.c";
    /** C++ source and header files (.cpp, .cc, .cxx, .h, .hpp, .hxx). */
    public static final String SCOPE_NAME_CPP        = "source.cpp";
    /** Unix shell scripts. */
    public static final String SCOPE_NAME_SHELL      = "source.shell";
    /** CMake list files and .cmake scripts. */
    public static final String SCOPE_NAME_CMAKE      = "source.cmake";
    /** Markdown documents. */
    public static final String SCOPE_NAME_MARKDOWN   = "text.html.markdown";
    /** Java .properties / generic INI files. */
    public static final String SCOPE_NAME_PROPERTIES = "source.ini";
    
    private static final String TAG = "CodeEditorLanguages";

    static {
        FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(SketchApplication.getAppContext().getAssets()));
        Throwable t;
        try {
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
        } catch (Exception e) {
            t = e;
            // fall-through
        } catch (NoSuchMethodError e) {
            t = e;
            LogUtil.e(TAG, "Probably running on a low API device");
            // fall-through
        }

    }

    public static Language loadTextMateLanguage(String scopeName) {
        Language language;

        try {
            language = TextMateLanguage.create(scopeName, true);
        } catch (Exception | NoSuchMethodError e) {
            LogUtil.e(TAG, "Failed to create language from scope name '" + scopeName + "', using empty one as default language", e);
            language = new EmptyLanguage();
        }

        return language;
    }
}
