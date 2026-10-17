package pro.sketchware.activities.editor.manage.lottie;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.provider.OpenableColumns;
import android.util.Log;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;


import pro.sketchware.beans.ProjectResourceBean;
import pro.sketchware.activities.base.BaseDialogActivity;
import pro.sketchware.widgets.EasyDeleteEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import pro.sketchware.R;
import pro.sketchware.core.async.BackgroundTasks;
import pro.sketchware.core.async.TaskHost;
import pro.sketchware.core.exception.SketchwareException;
import pro.sketchware.core.exception.CompileException;
import pro.sketchware.core.project.ImageCollectionManager;
import pro.sketchware.core.project.SketchwarePaths;
import pro.sketchware.core.validation.FileNameValidator;
import pro.sketchware.core.project.BlockConstants;
import pro.sketchware.util.SketchToast;
import pro.sketchware.util.io.UriPathResolver;
import pro.sketchware.util.io.EncryptedFileUtil;
import pro.sketchware.util.Helper;

import java.util.List;

public class AddLottieActivity extends BaseDialogActivity implements View.OnClickListener {
    private ArrayList<ProjectResourceBean> existingLotties;
    private TextView tv_add_lottie;
    private LottieAnimationView preview;
    private ArrayList<Uri> pickedLottieUris;
    private FileNameValidator nameValidator;
    private EditText ed_input_edittext;
    private EasyDeleteEditText ed_input;
    private ImageView tv_desc;
    private CheckBox chk_collection;
    private String sc_id;
    private ArrayList<ProjectResourceBean> lotties;
    private boolean multipleLottiesPicked = false;
    private LinearLayout layout_lottie_inform = null;
    private LinearLayout layout_lottie_modify = null;
    private TextView tv_lottiecnt = null;
    private boolean lottiePicked = false;
    private String lottieFilePath = null;
    private String dir_path = "";
    private boolean editing = false;
    private ProjectResourceBean lottie = null;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 215 && preview != null) {
            preview.setEnabled(true);
            if (resultCode == RESULT_OK && data != null) {
                tv_add_lottie.setVisibility(View.GONE);
                if (data.getClipData() == null) {
                    lottiePicked = true;
                    multipleLottiesPicked = false;
                    setLottieFromUri(data.getData());
                    if (nameValidator != null) {
                        nameValidator.setBatchCount(1);
                    }
                } else {
                    ClipData clipData = data.getClipData();
                    if (clipData.getItemCount() == 1) {
                        lottiePicked = true;
                        multipleLottiesPicked = false;
                        setLottieFromUri(clipData.getItemAt(0).getUri());
                        if (nameValidator != null) {
                            nameValidator.setBatchCount(1);
                        }
                    } else {
                        handleLottiePickClipData(clipData);
                        multipleLottiesPicked = true;
                        if (nameValidator != null) {
                            nameValidator.setBatchCount(clipData.getItemCount());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.cancel_button) {
            setResult(RESULT_CANCELED);
            finish();
        } else if (id == R.id.common_dialog_cancel_button) {
            finish();
        } else if (id == R.id.common_dialog_ok_button) {
            save();
        } else if (id == R.id.lottie_selected) {
            preview.setEnabled(false);
            pickLotties(!editing);
        }
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setDialogTitle("Add Lottie Animation");
        setOkButtonText(Helper.getResString(R.string.common_word_save));
        setContentView(R.layout.manage_lottie_add);
        Intent intent = getIntent();
        existingLotties = intent.getParcelableArrayListExtra("lotties");
        sc_id = intent.getStringExtra("sc_id");
        dir_path = intent.getStringExtra("dir_path");
        lottie = intent.getParcelableExtra("edit_target");
        if (lottie != null) {
            editing = true;
        }
        layout_lottie_inform = findViewById(R.id.layout_lottie_inform);
        layout_lottie_modify = findViewById(R.id.layout_lottie_modify);
        chk_collection = findViewById(R.id.chk_collection);
        tv_desc = findViewById(R.id.tv_desc);
        tv_lottiecnt = findViewById(R.id.tv_lottiecnt);
        tv_add_lottie = findViewById(R.id.tv_add_photo);
        preview = findViewById(R.id.lottie_selected);
        ed_input = findViewById(R.id.ed_input);
        ed_input_edittext = ed_input.getEditText();
        ed_input_edittext.setPrivateImeOptions("defaultInputmode=english;");
        ed_input.setHint("Enter Lottie animation name");
        nameValidator = new FileNameValidator(this, ed_input.getTextInputLayout(), BlockConstants.RESERVED_KEYWORDS, getReservedLottieNames());
        nameValidator.setBatchCount(1);
        chk_collection.setText(Helper.getResString(R.string.design_manager_title_add_to_collection));
        tv_add_lottie.setText("Add Lottie Animation");
        preview.setOnClickListener(this);
        dialogOkButton.setOnClickListener(this);
        dialogCancelButton.setOnClickListener(this);
        lottiePicked = false;
        // ensure directory exists (dir_path should point to assets/lottie)
        new EncryptedFileUtil().mkdirs(dir_path);
        lotties = new ArrayList<>();
    }

    @Override
    public void onPostCreate(Bundle bundle) {
        super.onPostCreate(bundle);
        if (editing) {
            lottie.isEdited = true;
            setDialogTitle("Edit Lottie Animation");
            nameValidator = new FileNameValidator(this, ed_input.getTextInputLayout(), BlockConstants.RESERVED_KEYWORDS, getReservedLottieNames(), lottie.resName);
            nameValidator.setBatchCount(1);
            ed_input_edittext.setText(lottie.resName);
            ed_input_edittext.setEnabled(false);
            chk_collection.setEnabled(false);
            tv_add_lottie.setVisibility(View.GONE);
            if (lottie.savedPos == 0) {
                setLottieFromFile(getResourceFilePath(lottie));
            } else {
                setLottieFromFile(lottie.resFullName);
            }
        }
    }

    private ArrayList<String> getReservedLottieNames() {
        var names = new ArrayList<String>();
        if (existingLotties != null) {
            for (var existingLottie : existingLotties) {
                names.add(existingLottie.resName);
            }
        }
        return names;
    }

    private void save() {
        if (validateAndCheckFile(nameValidator)) {
            new Handler().postDelayed(() -> {
                showLoadingDialog();
                new SaveAsyncTask(this).execute();
            }, 500L);
        }
    }

    private void s() {
        if (tv_desc != null) {
            tv_desc.setVisibility(View.INVISIBLE);
        }
        if (layout_lottie_inform != null && layout_lottie_modify != null && tv_lottiecnt != null) {
            layout_lottie_inform.setVisibility(View.GONE);
            layout_lottie_modify.setVisibility(View.VISIBLE);
            tv_lottiecnt.setVisibility(View.GONE);
        }
    }

    private void pickLotties(boolean allowMultiple) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (allowMultiple) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            startActivityForResult(Intent.createChooser(intent, "Choose Lottie File"), 215);
        } catch (ActivityNotFoundException unused) {
            SketchToast.warning(this, Helper.getResString(R.string.common_error_activity_not_found), SketchToast.TOAST_NORMAL).show();
        }
    }

    private void setLottieFromFile(String path) {
        lottieFilePath = path;
        try {
            String json = readFileContents(path);
            preview.setAnimationFromJson(json, path);
            preview.setRepeatCount(LottieDrawable.INFINITE);
            preview.playAnimation();
        } catch (Exception ignored) {
            preview.cancelAnimation();
            preview.setImageResource(android.R.drawable.ic_media_play);
        }
        int indexOfFilenameExtension = path.lastIndexOf(".");
        if (ed_input_edittext != null && (ed_input_edittext.getText() == null || ed_input_edittext.getText().length() <= 0)) {
            ed_input_edittext.setText(path.substring(path.lastIndexOf("/") + 1, indexOfFilenameExtension));
        }
        s();
    }

    private void onMultipleLottiesPicked(int count) {
        if (layout_lottie_inform == null || layout_lottie_modify == null || tv_lottiecnt == null) {
            return;
        }
        layout_lottie_inform.setVisibility(View.VISIBLE);
        layout_lottie_modify.setVisibility(View.GONE);
        tv_lottiecnt.setVisibility(View.VISIBLE);
        tv_lottiecnt.setText("+ " + (count - 1) + " more");
    }

    private boolean validateAndCheckFile(FileNameValidator validator) {
        if (!validator.isValid()) {
            return false;
        }
        if (lottiePicked || lottieFilePath != null) {
            return true;
        }
        tv_desc.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ani_1));
        return false;
    }

    private void setLottieFromUri(Uri uri) {
        if (uri == null) return;
        String filePath = null;
        try {
            filePath = UriPathResolver.resolve(this, uri);
        } catch (Throwable ignored) {
        }
        if (filePath == null) {
            filePath = resolveLottieFilePathFromUri(uri);
        }
        if (filePath != null) {
            setLottieFromFile(filePath);
        } else {
            SketchToast.warning(this, Helper.getResString(R.string.collection_no_exist_file), SketchToast.TOAST_NORMAL).show();
        }
    }

    private void handleLottiePickClipData(ClipData clipData) {
        if (clipData != null) {
            pickedLottieUris = new ArrayList<>();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                if (i == 0) {
                    setLottieFromUri(clipData.getItemAt(i).getUri());
                }
                pickedLottieUris.add(clipData.getItemAt(i).getUri());
            }
            onMultipleLottiesPicked(clipData.getItemCount());
        }
    }

    private String resolveLottieFilePathFromUri(Uri uri) {
        try {
            String resolved = null;
            try {
                resolved = UriPathResolver.resolve(getApplicationContext(), uri);
            } catch (Throwable ignored) {
            }
            if (resolved != null) return resolved;

            String fileName = queryDisplayName(uri);
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "lottie_" + System.currentTimeMillis() + ".json";
            } else if (!fileName.toLowerCase().endsWith(".json")) {
                fileName = fileName + ".json";
            }

            File cacheDir = new File(getCacheDir(), "lottie_imports");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File outFile = new File(cacheDir, fileName);

            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(outFile)) {
                if (is == null) return null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("AddLottieActivity", "resolveLottieFilePathFromUri", e);
            return null;
        }
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private String readFileContents(String path) throws Exception {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(new File(path));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toString("UTF-8");
        }
    }

    private String getResourceFilePath(ProjectResourceBean resource) {
        return dir_path + File.separator + resource.resFullName;
    }

    private static class SaveAsyncTask {
        private final WeakReference<AddLottieActivity> activityRef;

        public SaveAsyncTask(AddLottieActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        public void execute() {
            var activity = activityRef.get();
            if (activity == null) return;
            BackgroundTasks.runSerial(TaskHost.of(activity), "AddLottieActivity$SaveAsyncTask",
                    this::doWork, this::onSuccess, this::onError);
        }

        private void doWork() throws SketchwareException {
            var activity = activityRef.get();
            if (activity == null) return;
            try {
                if (!activity.multipleLottiesPicked) {
                    if (!activity.editing) {
                        var bean = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE,
                                Helper.getText(activity.ed_input_edittext).trim(), activity.lottieFilePath);
                        bean.savedPos = 1;
                        bean.isNew = true;
                        if (activity.chk_collection.isChecked()) {
                            activity.addToMyCollection(bean);
                        }
                        activity.lotties.add(bean);
                    } else if (!activity.lottiePicked) {
                        var bean = activity.lottie;
                        bean.isEdited = true;
                    } else {
                        var bean = activity.lottie;
                        bean.resFullName = activity.lottieFilePath;
                        bean.savedPos = 1;
                        bean.isEdited = true;
                    }
                } else {
                    var toAdd = new ArrayList<ProjectResourceBean>();
                    int idx = 0;
                    while (idx < activity.pickedLottieUris.size()) {
                        var uri = activity.pickedLottieUris.get(idx);
                        var name = Helper.getText(activity.ed_input_edittext).trim() + "_" + ++idx;
                        var filePath = activity.resolveLottieFilePathFromUri(uri);
                        if (filePath == null) {
                            return;
                        }
                        var bean = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, name, filePath);
                        bean.savedPos = 1;
                        bean.isNew = true;
                        toAdd.add(bean);
                    }
                    if (activity.chk_collection.isChecked()) {
                        for (ProjectResourceBean b : toAdd) {
                            activity.addToMyCollection(b);
                        }
                    }
                    activity.multipleLottiesPicked = false;
                    activity.lotties.addAll(toAdd);
                }
            } catch (Exception e) {
                if (e instanceof CompileException compileException) {
                    var errorMessage = compileException.getMessage();
                    var code = switch (errorMessage) {
                        case "fail_to_copy" -> R.string.collection_failed_to_copy;
                        case "file_no_exist" -> R.string.collection_no_exist_file;
                        case "duplicate_name" -> R.string.collection_duplicated_name;
                        default -> 0;
                    };
                    var message = code != 0 ? activity.getString(code) : null;

                    // If CompileException carries details, append them (method name may differ)
                    try {
                        java.lang.reflect.Method m = compileException.getClass().getMethod("getErrorDetails");
                        Object details = m.invoke(compileException);
                        if (details instanceof java.util.List<?> list && !list.isEmpty()) {
                            String names = "";
                            for (Object nm : list) {
                                if (!names.isEmpty()) names += ", ";
                                names += nm.toString();
                            }
                            message += "[" + names + "]";
                        }
                    } catch (Exception ignored) {
                    }

                    throw new SketchwareException(message);
                }
                Log.e("AddLottieActivity", e.getMessage(), e);
                throw new SketchwareException(e.getMessage());
            }
        }

        private void onSuccess() {
            var activity = activityRef.get();
            if (activity == null) return;
            activity.dismissLoadingDialog();
            Intent intent = new Intent();
            intent.putExtra("sc_id", activity.sc_id);
            if (activity.editing) {
                intent.putExtra("lottie", activity.lottie);
            } else {
                intent.putExtra("lotties", activity.lotties);
            }
            activity.setResult(RESULT_OK, intent);
            activity.finish();
        }

        private void onError(Throwable error) {
            var activity = activityRef.get();
            if (activity == null) return;
            activity.dismissLoadingDialog();
            String msg = buildErrorMessage(error);
            SketchToast.warning(activity, msg, SketchToast.TOAST_NORMAL).show();
        }

        private String buildErrorMessage(Throwable error) {
            String errorMessage = error != null ? error.getMessage() : null;
            if (errorMessage == null || errorMessage.isEmpty()) {
                return Helper.getResString(R.string.common_error_an_error_occurred);
            }
            return errorMessage;
        }
    }
    
    private void addToMyCollection(ProjectResourceBean lottie) throws SketchwareException {
        String destDirPath = SketchwarePaths.getCollectionPath() + File.separator + "assets";
        File destDir = new File(destDirPath);
        if (!destDir.exists()) destDir.mkdirs();

        String destName = lottie.resName.toLowerCase().endsWith(".json") ? lottie.resName : lottie.resName + ".json";
        File src = new File(lottie.resFullName);
        File dest = new File(destDir, destName);

        if (!src.exists()) {
            throw new SketchwareException(Helper.getResString(R.string.collection_no_exist_file));
        }
        if (dest.exists()) {
            throw new SketchwareException(Helper.getResString(R.string.collection_duplicated_name) + "[" + lottie.resName + "]");
        }

        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SketchwareException(Helper.getResString(R.string.collection_failed_to_copy));
        }
        
    }
}
