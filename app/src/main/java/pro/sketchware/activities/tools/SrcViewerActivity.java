package pro.sketchware.activities.tools;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.Locale;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import io.noties.markwon.Markwon;
import pro.sketchware.network.AiProviderService;

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
        binding.aiHelpCode.setOnClickListener(v -> explainCodeWithAI());

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
}
