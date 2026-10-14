package pro.sketchware.activities.editor.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.Locale;

import io.noties.markwon.Markwon;
import pro.sketchware.activities.base.BaseAppCompatActivity;
import pro.sketchware.core.codegen.ComponentCodeGenerator;
import pro.sketchware.lib.code_editor.CodeEditorPreferences;
import pro.sketchware.network.AiProviderService;
import pro.sketchware.util.EditorUtils;
import pro.sketchware.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityCodeViewerBinding;
import pro.sketchware.util.SketchwareUtil;
import pro.sketchware.util.UI;

public class CodeViewerActivity extends BaseAppCompatActivity {

    public static final String SCHEME_XML = "xml";
    public static final String SCHEME_JAVA = "java";

    private ActivityCodeViewerBinding binding;
    private CodeEditorPreferences editorPrefs;

    private static final int MENU_WORD_WRAP = 0;
    private static final int MENU_FONT_SIZE = 1;
    private static final int MENU_AI_HELPER = 2;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityCodeViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        var code = getIntent().getStringExtra("code");
        var scheme = getIntent().getStringExtra("scheme");
        var scId = getIntent().getStringExtra("sc_id");

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setSubtitle(scId);

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(14);
        binding.editor.setText(ComponentCodeGenerator.formatCode(code, false));
        binding.editor.setEditable(false);

        editorPrefs = new CodeEditorPreferences(this, "cve");
        editorPrefs.applyToEditor(binding.editor, false);

        loadColorScheme(scheme);

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    private void loadColorScheme(String scheme) {
        if (SCHEME_XML.equals(scheme)) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else {
            EditorUtils.loadJavaConfig(binding.editor);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_WORD_WRAP, Menu.NONE, Helper.getResString(R.string.code_editor_menu_word_wrap))
                .setCheckable(true).setChecked(editorPrefs.getWordWrap());
        menu.add(Menu.NONE, MENU_FONT_SIZE, Menu.NONE, Helper.getResString(R.string.code_editor_menu_font_size));
        menu.add(Menu.NONE, MENU_AI_HELPER, Menu.NONE, "AI Helper");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case MENU_WORD_WRAP -> {
                item.setChecked(!item.isChecked());
                binding.editor.setWordwrap(item.isChecked());
                editorPrefs.setWordWrap(item.isChecked());
                return true;
            }
            case MENU_FONT_SIZE -> {
                editorPrefs.showFontSizeDialog(this, binding.editor, null);
                return true;
            }
            case MENU_AI_HELPER -> {
                explainCodeWithAI();
                return true;
            }
            default -> {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void explainCodeWithAI() {
        CharSequence cs = binding.editor.getText();
        String codeText = cs != null ? cs.toString().trim() : "";
        if (codeText.isEmpty()) {
            SketchwareUtil.toastError("No code to analyze.");
            return;
        }
        if (!SketchwareUtil.isConnected()) {
            SketchwareUtil.toastError(getString(R.string.common_message_check_network));
            return;
        }

        View loadingView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_layout_loading, null);
        TextView titleView = loadingView.findViewById(R.id.text_loading_title);
        TextView subtitleView = loadingView.findViewById(R.id.text_loading_subtitle);

        if (titleView != null) titleView.setText(R.string.ai_explain_loading_title);
        if (subtitleView != null) subtitleView.setText(R.string.ai_explain_loading_subtitle);

        var progressDialog = new MaterialAlertDialogBuilder(this)
            .setView(loadingView)
            .setCancelable(false)
            .create();
        progressDialog.show();

        new Thread(() -> {
            try {
                String prompt = "Analyze this source code / XML snippet from Sketchware. "
                        + "Explain its structure, functionality, and provide any useful suggestions or fixes if needed.\n\n"
                        + codeText;
                
                String deviceLanguage = Locale.getDefault().getLanguage();
                String systemInstruction = "You analyze source code and XML files for Sketchware Android projects. Respond in language code: " + deviceLanguage + ".";

                String response = AiProviderService.getInstance().sendTextMessage(
                        systemInstruction,
                        prompt
                );
                runOnUiThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    showScrollableDialog("AI Helper - Code Explanation", response);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    SketchwareUtil.showAnErrorOccurredDialog(this, e.getMessage());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    SketchwareUtil.showAnErrorOccurredDialog(this, getString(R.string.ai_explain_error_processing_response));
                });
            }
        }).start();
    }

    private void showScrollableDialog(String title, String content) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        TextView textView = new TextView(this);
        Markwon markwon = Markwon.create(this);
        markwon.setMarkdown(textView, content);
        textView.setTextIsSelectable(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(pad, pad, pad, pad);

        scrollView.addView(textView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        var dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(R.string.common_word_close, null)
            .setNeutralButton(R.string.common_word_copy, null)
            .create();

        dialog.setOnShowListener(d -> {
            var btnCopy = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL);
            btnCopy.setOnClickListener(v -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("ai_response", content));
                        SketchwareUtil.toast(getString(R.string.common_word_copied));
                    }
                } catch (Exception ignored) {
                }
            });
        });

        dialog.show();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (editorPrefs != null && binding != null) {
            editorPrefs.saveTextSizeFromEditor(binding.editor, getResources().getDisplayMetrics().scaledDensity);
        }
    }
}
