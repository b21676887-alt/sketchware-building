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
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.noties.markwon.Markwon;
import pro.sketchware.activities.base.BaseAppCompatActivity;
import pro.sketchware.core.codegen.ComponentCodeGenerator;
import pro.sketchware.core.project.SketchwarePaths;
import pro.sketchware.lib.code_editor.CodeEditorPreferences;
import pro.sketchware.network.AiProviderService;
import pro.sketchware.util.EditorUtils;
import pro.sketchware.util.FileUtil;
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
    private String scId;

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
        scId = getIntent().getStringExtra("sc_id");
        
        // تعيين الـ Toolbar كـ ActionBar رئيسي لكي تظهر القائمة وعناصر الـ Menu تلقائياً
        setSupportActionBar(binding.toolbar);

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
        menu.add(Menu.NONE, MENU_AI_HELPER, Menu.NONE, Helper.getResString(R.string.ai_help_button));
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
                checkExistingAiHelpsAndHandle();
                return true;
            }
            default -> {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void checkExistingAiHelpsAndHandle() {
        if (scId == null || scId.isEmpty()) {
            explainCodeWithAI();
            return;
        }

        String aiHelpDirPath = SketchwarePaths.getAihelpPath(scId);
        File dir = new File(aiHelpDirPath);
        
        // التحقق مما إذا كان المجلد موجوداً ويحتوي على ملفات مساعدة سابقة بصيغة md
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".md"));
            if (files != null && files.length > 0) {
                // إظهار مربع حوار الاختيار بين طلب المساعدة أو عرض المساعدات السابقة
                new MaterialAlertDialogBuilder(this)
                    .setTitle("AI Helper")
                    .setMessage("هذا المشروع يمتلك ردود ومساعدات سابقة بالذكاء الاصطناعي. ماذا تريد أن تفعل؟")
                    .setPositiveButton("طلب المساعدة", (dialog, which) -> explainCodeWithAI())
                    .setNeutralButton("عرض المساعدات السابقة", (dialog, which) -> showPreviousHelpsList(files))
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .show();
                return;
            }
        }
        
        // إذا لم تكن هناك ردود سابقة، تنفيذ الدالة مباشرة
        explainCodeWithAI();
    }

    private void showPreviousHelpsList(File[] files) {
        int n = files.length;
        CharSequence[] fileNames = new CharSequence[n];
        for (int i = 0; i < n; i++) {
            fileNames[i] = files[i].getName();
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("المساعدات السابقة")
            .setItems(fileNames, (dialog, which) -> {
                String content = FileUtil.readFile(files[which].getAbsolutePath());
                showScrollableDialog("AI Helper - " + files[which].getName(), content);
            })
            .setNegativeButton(R.string.common_word_cancel, null)
            .show();
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

        if (titleView != null) titleView.setText(R.string.ai_help_loading_title);
        if (subtitleView != null) subtitleView.setText(R.string.ai_help_loading_subtitle);

        AtomicBoolean isCancelled = new AtomicBoolean(false);

        var progressDialog = new MaterialAlertDialogBuilder(this)
            .setView(loadingView)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                isCancelled.set(true);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            })
            .create();
        
        progressDialog.show();

        // إبقاء الشاشة يقظة أثناء المعالجة
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        new Thread(() -> {
            try {
                String deviceLanguage = Locale.getDefault().getLanguage();
                String systemInstruction = "You analyze source code and XML files for Sketchware Android projects. Respond in language code: " + deviceLanguage + ".";

                String[] lines = codeText.split("\r?\n");
                int totalLines = lines.length;
                int chunkSize = 800;
                int totalChunks = (int) Math.ceil((double) totalLines / chunkSize);

                String finalResponse = "";

                if (totalLines <= chunkSize) {
                    // إذا كان الكود أقل من أو يساوي 500 سطر، يتم إرساله مباشرة
                    String prompt = "Analyze this source code / XML snippet from Sketchware. "
                            + "Explain its structure, functionality, and provide any useful suggestions or fixes if needed.\n\n"
                            + codeText;

                    if (isCancelled.get()) return;
                    finalResponse = AiProviderService.getInstance().sendTextMessage(systemInstruction, prompt);
                } else {
                    // إذا كان الكود أكثر من 500 سطر، يُقسم إلى أجزاء
                    AiProviderService aiService = AiProviderService.getInstance();

                    for (int i = 0; i < totalChunks; i++) {
                        if (isCancelled.get()) return;

                        int start = i * chunkSize;
                        int end = Math.min(start + chunkSize, totalLines);
                        StringBuilder sb = new StringBuilder();
                        for (int j = start; j < end; j++) {
                            sb.append(lines[j]).append("\n");
                        }

                        int partNum = i + 1;
                        String prompt;
                        if (partNum < totalChunks) {
                            prompt = "This is part " + partNum + " of " + totalChunks + " of a large source code / XML snippet from Sketchware. "
                                    + "Store this part and DO NOT analyze it yet, and DO NOT provide any explanation or suggestions until you receive all parts. Just acknowledge receipt.\n\n"
                                    + sb.toString().trim();
                        } else {
                            prompt = "This is the final part (part " + partNum + " of " + totalChunks + ") of the source code / XML snippet from Sketchware. "
                                    + "Now that you have received all parts of the code, analyze its structure, functionality, and provide any useful suggestions or fixes if needed.\n\n"
                                    + sb.toString().trim();
                        }

                        finalResponse = aiService.sendTextMessage(systemInstruction, prompt);
                    }
                }

                if (isCancelled.get()) return;

                // حفظ الرد تلقائياً بصيغة md باستخدام مسار getAihelpPath مع اسم الملف الحالي (أو افتراضي)، الوقت، والتاريخ
                if (scId != null && !scId.isEmpty()) {
                    String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
                    String fileName = "CodeViewer_" + timeStamp + ".md";
                    
                    String dirPath = SketchwarePaths.getAihelpPath(scId);
                    FileUtil.writeFile(dirPath + File.separator + fileName, finalResponse);
                }

                final String responseResult = finalResponse;
                runOnUiThread(() -> {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    showScrollableDialog("AI Helper - Code Explanation", responseResult);
                });

            } catch (IOException e) {
                if (isCancelled.get()) return;
                runOnUiThread(() -> {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    SketchwareUtil.showAnErrorOccurredDialog(this, e.getMessage());
                });
            } catch (Exception e) {
                if (isCancelled.get()) return;
                runOnUiThread(() -> {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
