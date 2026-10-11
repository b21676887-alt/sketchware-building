package pro.sketchware.activities.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.view.LayoutInflater;
import android.content.ClipboardManager;
import android.content.ClipData;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.IOException;
import java.util.Locale;

import pro.sketchware.activities.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.sketchware.util.CompileLogHelper;
import pro.sketchware.util.Helper;
import pro.sketchware.R;
import pro.sketchware.core.build.CompileErrorSaver;
import pro.sketchware.databinding.CompileLogBinding;
import pro.sketchware.network.AiProviderService;
import io.noties.markwon.Markwon;
import android.os.Environment;
import java.io.File;
import pro.sketchware.core.project.ProjectTracker;
import pro.sketchware.util.SketchwareUtil;
import pro.sketchware.util.UI;

public class CompileLogActivity extends BaseAppCompatActivity {

    private static final String PREFERENCE_WRAPPED_TEXT = "wrapped_text";
    private static final String PREFERENCE_USE_MONOSPACED_FONT = "use_monospaced_font";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private CompileErrorSaver compileErrorSaver;
    private SharedPreferences logViewerPreferences;

    private CompileLogBinding binding;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = CompileLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UI.addWindowInsetToMargin(binding.optionsLayout, WindowInsetsCompat.Type.navigationBars(), true, true, true, true);

        logViewerPreferences = getPreferences(Context.MODE_PRIVATE);

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        if (getIntent().getBooleanExtra("showingLastError", false)) {
            binding.topAppBar.setTitle(R.string.compile_log_title_last);
        } else {
            binding.topAppBar.setTitle(R.string.compile_log_title);
        }

        String sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null) {
            finish();
            return;
        }

        compileErrorSaver = new CompileErrorSaver(sc_id);

        binding.copyButton.setOnClickListener(v -> {
            CharSequence logText = binding.tvCompileLog.getText();
            if (logText != null && logText.length() > 0) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Compile Log", logText));
                SketchwareUtil.toast(Helper.getResString(R.string.toast_copied_to_clipboard));
            }
        });

        binding.clearButton.setOnClickListener(v -> {
            if (compileErrorSaver.logFileExists()) {
                compileErrorSaver.deleteSavedLogs();
                getIntent().removeExtra("error");
                SketchwareUtil.toast(Helper.getResString(R.string.toast_compile_logs_cleared));
            } else {
                SketchwareUtil.toast(Helper.getResString(R.string.toast_no_compile_logs));
            }

            setErrorText();
        });

        final String wrapTextLabel = "Wrap text";
        final String monospacedFontLabel = "Monospaced font";
        final String fontSizeLabel = "Font size";

        PopupMenu options = new PopupMenu(this, binding.formatButton);
        options.getMenu().add(wrapTextLabel).setCheckable(true).setChecked(getWrappedTextPreference());
        options.getMenu().add(monospacedFontLabel).setCheckable(true).setChecked(getMonospacedFontPreference());
        options.getMenu().add(fontSizeLabel);

        options.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getTitle().toString()) {
                case wrapTextLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleWrapText(menuItem.isChecked());
                }
                case monospacedFontLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleMonospacedText(menuItem.isChecked());
                }
                case fontSizeLabel -> changeFontSizeDialog();
                default -> {
                    return false;
                }
            }

            return true;
        });

        binding.formatButton.setOnClickListener(v -> options.show());

        applyLogViewerPreferences();

        setErrorText();
        
        if (binding.aiExplainButton != null) {
            binding.aiExplainButton.setOnClickListener(v -> explainLogWithAI());
        }
    }

    private void setErrorText() {
        String error = getIntent().getStringExtra("error");
        if (error == null) error = compileErrorSaver.getLogsFromFile();
        if (error == null) {
            binding.noContentLayout.setVisibility(View.VISIBLE);
            binding.optionsLayout.setVisibility(View.GONE);
            return;
        }

        binding.optionsLayout.setVisibility(View.VISIBLE);
        binding.noContentLayout.setVisibility(View.GONE);

        binding.tvCompileLog.setText(CompileLogHelper.getColoredLogs(this, error));
        binding.tvCompileLog.setTextIsSelectable(true);
    }

    private void applyLogViewerPreferences() {
        toggleWrapText(getWrappedTextPreference());
        toggleMonospacedText(getMonospacedFontPreference());
        binding.tvCompileLog.setTextSize(getFontSizePreference());
    }

    private boolean getWrappedTextPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_WRAPPED_TEXT, false);
    }

    private boolean getMonospacedFontPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_USE_MONOSPACED_FONT, true);
    }

    private int getFontSizePreference() {
        return logViewerPreferences.getInt(PREFERENCE_FONT_SIZE, 13);
    }

    private void toggleWrapText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_WRAPPED_TEXT, isChecked).apply();

        if (isChecked) {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errVScroll.addView(binding.tvCompileLog);
        } else {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errHScroll.removeAllViews();
            binding.errHScroll.addView(binding.tvCompileLog);
            binding.errVScroll.addView(binding.errHScroll);
        }
    }

    private void toggleMonospacedText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_USE_MONOSPACED_FONT, isChecked).apply();

        if (isChecked) {
            binding.tvCompileLog.setTypeface(Typeface.MONOSPACE);
        } else {
            binding.tvCompileLog.setTypeface(Typeface.DEFAULT);
        }
    }

    private void changeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(10); //Must not be less than setValue(), which is currently 11 in compile_log.xml
        picker.setMaxValue(70);
        picker.setWrapSelectorWheel(false);
        picker.setValue(getFontSizePreference());

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_select_font_size)
                .setView(layout)
                .setPositiveButton(R.string.common_word_save, (dialog, which) -> {
                    logViewerPreferences.edit().putInt(PREFERENCE_FONT_SIZE, picker.getValue()).apply();

                    binding.tvCompileLog.setTextSize((float) picker.getValue());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    private void explainLogWithAI() {
        CharSequence cs = binding.tvCompileLog.getText();
        String logText = cs != null ? cs.toString().trim() : "";
        if (logText.isEmpty()) {
            SketchwareUtil.toastError(getString(R.string.compile_log_no_log_to_analyze));
            return;
        }
        if (!SketchwareUtil.isConnected()) {
            SketchwareUtil.toastError(getString(R.string.common_message_check_network));
            return;
        }

        // Diálogo de carregamento com Lottie
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
                String prompt = "Analyze this Sketchware Android compile log. "
                        + "Explain the root cause, identify the affected screen or generated source when possible, "
                        + "and give concrete correction steps without modifying the project.\n\n"
                        + logText;
                
                String deviceLanguage = Locale.getDefault().getLanguage();
                String systemInstruction = "You analyze Sketchware Android compile logs. Respond in language code: " + deviceLanguage + ".";

                String response = AiProviderService.getInstance().sendTextMessage(
                        systemInstruction,
                        prompt
                );
                runOnUiThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    showScrollableDialog(getString(R.string.ai_explain_title), response);
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
        // Renderiza Markdown usando Markwon
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
                        cm.setPrimaryClip(ClipData.newPlainText("ia_response", content));
                        SketchwareUtil.toast(getString(R.string.common_word_copied));
                    }
                } catch (Exception ignored) {
                }
            });
        });

        dialog.show();
    }
    
    private String limitAiText(String content, int maxChars) {
        if (content == null) {
            return "";
        }

        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }

        if (maxChars <= 3) {
            return normalized.substring(0, Math.max(0, maxChars));
        }

        return normalized.substring(0, maxChars - 3).trim() + "...";
    }
}
