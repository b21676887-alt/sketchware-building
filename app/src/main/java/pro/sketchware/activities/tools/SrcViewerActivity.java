package pro.sketchware.activities.tools;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import io.noties.markwon.Markwon;
import pro.sketchware.core.project.SketchwarePaths;
import pro.sketchware.network.AiProviderService;
import pro.sketchware.util.FileUtil;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;

import androidx.core.view.WindowInsetsCompat;

import pro.sketchware.beans.SrcCodeBean;
import pro.sketchware.widgets.CommonSpinnerItem;
import pro.sketchware.activities.base.BaseAppCompatActivity;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.core.build.ProjectBuilder;
import pro.sketchware.util.SketchToast;
import pro.sketchware.core.project.ProjectDataManager;
import pro.sketchware.core.build.ProjectFilePaths;
import pro.sketchware.util.Helper;
import pro.sketchware.util.LogUtil;
import pro.sketchware.R;
import pro.sketchware.databinding.SrcViewerBinding;
import pro.sketchware.lib.code_editor.CodeEditorPreferences;
import pro.sketchware.util.EditorUtils;
import pro.sketchware.util.UI;
import pro.sketchware.util.SketchwareUtil;

public class SrcViewerActivity extends BaseAppCompatActivity {

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private SrcViewerBinding binding;
    private String sc_id;
    private ArrayList<SrcCodeBean> sourceCodeBeans;

    private String currentFileName;
    private CodeEditorPreferences editorPrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = SrcViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        currentFileName = getIntent().hasExtra("current") ? getIntent().getStringExtra("current") : "";
        sc_id = savedInstanceState != null ? savedInstanceState.getString("sc_id") : getIntent().getStringExtra("sc_id");

        UI.addWindowInsetToPadding(binding.getRoot(), WindowInsetsCompat.Type.systemBars(), false, true, false, true);

        editorPrefs = new CodeEditorPreferences(this, "viewer");

        configureEditor();

        binding.changeFontSize.setOnClickListener(v -> editorPrefs.showFontSizeDialog(this, binding.editor, null));
        
        // التحقق من وجود ردود سابقة عند النقر على زر الذكاء الاصطناعي
        binding.aiHelpCode.setOnClickListener(v -> checkExistingAiHelpsAndHandle());

        binding.filesListSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SrcCodeBean bean = sourceCodeBeans.get(position);
                binding.editor.setText(bean.source);
                currentFileName = bean.srcFileName;
                if (currentFileName.endsWith(".xml")) {
                    EditorUtils.loadXmlConfig(binding.editor);
                } else {
                    EditorUtils.loadJavaConfig(binding.editor);
                }
                editorPrefs.applyToEditor(binding.editor, false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        showLoadingDialog(); // show loading

        backgroundExecutor.execute(() -> {
            try {
                var ProjectFilePaths = new ProjectFilePaths(getBaseContext(), sc_id);
                var fileManager = ProjectDataManager.getFileManager(sc_id);
                var dataManager = ProjectDataManager.getProjectDataManager(sc_id);
                var libraryManager = ProjectDataManager.getLibraryManager(sc_id);
                ProjectFilePaths.initializeMetadata(libraryManager, fileManager, dataManager, pro.sketchware.core.build.ProjectFilePaths.ExportType.SOURCE_CODE_VIEWING);
                ProjectBuilder builder = new ProjectBuilder(this, ProjectFilePaths);
                builder.buildBuiltInLibraryInformation();
                sourceCodeBeans = ProjectFilePaths.generateSourceCodeBeans(fileManager, dataManager, libraryManager, builder.getBuiltInLibraryManager());

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (sourceCodeBeans == null) {
                        SketchToast.warning(getApplicationContext(), Helper.getResString(R.string.common_error_unknown), SketchToast.TOAST_NORMAL).show();
                    } else {
                        binding.filesListSpinner.setAdapter(new FilesListSpinnerAdapter());

                        for (SrcCodeBean src : sourceCodeBeans) {
                            if (src.srcFileName.equals(currentFileName)) {
                                binding.filesListSpinner.setSelection(sourceCodeBeans.indexOf(src));
                                break;
                            }
                        }
                        binding.editor.setText(sourceCodeBeans.get(binding.filesListSpinner.getSelectedItemPosition()).source);
                        dismissLoadingDialog(); // hide loading
                    }
                });
            } catch (Exception e) {
                LogUtil.e("SrcViewerActivity", "Failed to generate source code", e);
            }
        });

    }

    private void configureEditor() {
        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setEditable(false);
        binding.editor.setPinLineNumber(true);

        if (currentFileName.endsWith(".xml")) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else {
            EditorUtils.loadJavaConfig(binding.editor);
        }
        editorPrefs.applyToEditor(binding.editor, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        backgroundExecutor.shutdownNow();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    public class FilesListSpinnerAdapter extends BaseAdapter {

        private View getCustomSpinnerView(int position, View view, boolean isCurrentlyViewingFile) {
            CommonSpinnerItem spinnerItem = view != null ? (CommonSpinnerItem) view :
                    new CommonSpinnerItem(SrcViewerActivity.this);
            spinnerItem.setData(sourceCodeBeans.get(position).srcFileName, isCurrentlyViewingFile);
            return spinnerItem;
        }

        @Override
        public int getCount() {
            return sourceCodeBeans.size();
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            boolean isCheckmarkVisible = binding.filesListSpinner.getSelectedItemPosition() == position;
            return getCustomSpinnerView(position, convertView, isCheckmarkVisible);
        }

        @Override
        public Object getItem(int position) {
            return sourceCodeBeans.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomSpinnerView(position, convertView, false);
        }
    }
    
    private void checkExistingAiHelpsAndHandle() {
        if (sc_id == null || sc_id.isEmpty()) {
            explainCodeWithAI();
            return;
        }

        String aiHelpDirPath = SketchwarePaths.getAihelpPath(sc_id);
        File dir = new File(aiHelpDirPath);
        
        // التحقق مما إذا كان المجلد موجوداً ويحتوي على ملفات مساعدة سابقة
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

        if (titleView != null) titleView.setText(R.string.ai_explain_loading_title);
        if (subtitleView != null) subtitleView.setText(R.string.ai_explain_loading_subtitle);

        AtomicBoolean isCancelled = new AtomicBoolean(false);

        var progressDialog = new MaterialAlertDialogBuilder(this)
            .setView(loadingView)
            .setCancelable(false)
            .setNegativeButton(R.string.common_word_cancel, (dialog, which) -> {
                isCancelled.set(true);
            })
            .create();
        progressDialog.show();

        // إبقاء الشاشة يقظة أثناء المعالجة والإرسال
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        new Thread(() -> {
            try {
                String deviceLanguage = Locale.getDefault().getLanguage();
                String systemInstruction = "You analyze source code and XML files for Sketchware Android projects. Respond in language code: " + deviceLanguage + ".";

                String[] lines = codeText.split("\n");
                int totalLines = lines.length;
                int chunkSize = 500;
                int totalChunks = (int) Math.ceil((double) totalLines / chunkSize);

                String finalResponse = "";

                if (totalLines <= chunkSize) {
                    if (isCancelled.get()) return;
                    String prompt = "Analyze this source code / XML snippet from Sketchware. "
                            + "Explain its structure, functionality, and provide any useful suggestions or fixes if needed.\n\n"
                            + codeText;
                    finalResponse = AiProviderService.getInstance().sendTextMessage(systemInstruction, prompt);
                } else {
                    for (int i = 0; i < totalChunks; i++) {
                        if (isCancelled.get()) return;

                        int start = i * chunkSize;
                        int end = Math.min(start + chunkSize, totalLines);
                        StringBuilder chunkBuilder = new StringBuilder();
                        for (int j = start; j < end; j++) {
                            chunkBuilder.append(lines[j]).append("\n");
                        }

                        int partNum = i + 1;
                        String prompt;
                        if (partNum < totalChunks) {
                            prompt = "Here is part " + partNum + " of " + totalChunks + " of the source code/XML snippet from Sketchware. "
                                    + "Please DO NOT analyze, explain, or provide suggestions yet. Just acknowledge receipt and wait for the remaining parts.\n\n"
                                    + chunkBuilder.toString().trim();
                        } else {
                            prompt = "Here is the final part (" + partNum + " of " + totalChunks + ") of the source code/XML snippet from Sketchware. "
                                    + "Now that you have received all parts, please analyze its structure, functionality, and provide any useful suggestions or fixes if needed.\n\n"
                                    + chunkBuilder.toString().trim();
                        }

                        finalResponse = AiProviderService.getInstance().sendTextMessage(systemInstruction, prompt);
                    }
                }

                if (isCancelled.get()) return;

                // حفظ الرد تلقائياً بصيغة md باستخدام getAihelpPath مع اسم الملف الحالي، الوقت، والتاريخ
                if (sc_id != null && !sc_id.isEmpty()) {
                    String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
                    String cleanFileName = (currentFileName != null && !currentFileName.isEmpty()) ? currentFileName.replaceAll("[^a-zA-Z0-9.-]", "_") : "code";
                    String fileName = cleanFileName + "_" + timeStamp + ".md";
                    
                    String dirPath = SketchwarePaths.getAihelpPath(sc_id);
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
}
