package pro.sketchware.activities.editor.manifest;

import static pro.sketchware.util.GsonUtils.getGson;

import android.annotation.SuppressLint;
import com.google.gson.JsonSyntaxException;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import pro.sketchware.activities.editor.manage.library.LibraryCategoryView;
import pro.sketchware.activities.editor.manage.library.LibraryItemView;
import pro.sketchware.activities.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.io.File;

import pro.sketchware.core.async.BackgroundTasks;
import pro.sketchware.core.project.ProjectDataManager;
import pro.sketchware.core.project.SketchwarePaths;
import pro.sketchware.core.async.TaskHost;
import pro.sketchware.util.ViewUtil;
import pro.sketchware.core.build.ProjectFilePaths;
import pro.sketchware.activities.editor.code.SrcCodeEditor;
import pro.sketchware.util.Helper;
import pro.sketchware.core.codegen.AndroidManifestInjector;
import pro.sketchware.util.LogUtil;
import pro.sketchware.widgets.CustomAttributeView;
import pro.sketchware.R;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.databinding.AndroidManifestInjectionBinding;
import pro.sketchware.util.FileUtil;
import pro.sketchware.util.SketchwareUtil;

@SuppressLint("SetTextI18n")
public class AndroidManifestInjection extends BaseAppCompatActivity {

    private final ArrayList<HashMap<String, Object>> activitiesListMap = new ArrayList<>();
    private AndroidManifestInjectionBinding binding;
    private String sc_id;
    private String currentActivityName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = AndroidManifestInjectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getIntent().hasExtra("sc_id") && getIntent().hasExtra("file_name")) {
            sc_id = getIntent().getStringExtra("sc_id");
            currentActivityName = getIntent().getStringExtra("file_name").replace(".java", "");
        }

        setupCustomToolbar();
        checkAttrs();
        setupOptions();
        setupManifestMode();
        refreshList();
        checkAttrs();

        binding.addActivity.setOnClickListener(v -> showAddActivityDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        checkAttrs();
        refreshList();
    }

    private String getAttributesPath() {
        return SketchwarePaths.getAndroidManifestAttributesPath(sc_id);
    }

    private String getAppComponentsPath() {
        return SketchwarePaths.getAndroidManifestAppComponentsPath(sc_id);
    }

    private String getActivitiesComponentsPath() {
        return SketchwarePaths.getAndroidManifestActivitiesComponentsPath(sc_id);
    }

    private void checkAttrs() {
        String path = getAttributesPath();
        if (FileUtil.isExistFile(path)) {
            ArrayList<HashMap<String, Object>> data;
            try {
                data = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            } catch (JsonSyntaxException e) {
                return;
            }
            if (data == null) {
                return;
            }
            for (int i = 0; i < data.size(); i++) {
                Object rawName = data.get(i).get("name");
                if ("_application_attrs".equals(rawName)) {
                    Object rawValue = data.get(i).get("value");
                    if (rawValue != null && rawValue.toString().contains("android:theme")) {
                        return;
                    }
                }
            }
            HashMap<String, Object> _item = new HashMap<>();
            _item.put("name", "_application_attrs");
            _item.put("value", "android:theme=\"@style/AppTheme\"");
            data.add(_item);
            FileUtil.writeFile(path, getGson().toJson(data));
        }
    }

    private void setupOptions() {
        List<LibraryCategoryView> options = new ArrayList<>();

        LibraryCategoryView basicCategoryView = new LibraryCategoryView(this);
        basicCategoryView.setTitle(null);
        options.add(basicCategoryView);

        basicCategoryView.addLibraryItem(createOption(Helper.getResString(R.string.manifest_option_application), Helper.getResString(R.string.manifest_option_application_desc), R.drawable.ic_mtrl_settings_applications, v -> {
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), AndroidManifestInjectionDetails.class);
            intent.putExtra("sc_id", sc_id);
            intent.putExtra("file_name", currentActivityName);
            intent.putExtra("type", "application");
            startActivity(intent);
        }), true);
        basicCategoryView.addLibraryItem(createOption(Helper.getResString(R.string.manifest_option_permissions), Helper.getResString(R.string.manifest_option_permissions_desc), R.drawable.ic_mtrl_shield_check, v -> {
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), AndroidManifestInjectionDetails.class);
            intent.putExtra("sc_id", sc_id);
            intent.putExtra("file_name", currentActivityName);
            intent.putExtra("type", "permission");
            startActivity(intent);
        }), true);
        basicCategoryView.addLibraryItem(createOption(Helper.getResString(R.string.manifest_option_launcher_activity), Helper.getResString(R.string.manifest_option_launcher_activity_desc), R.drawable.ic_mtrl_login, v -> showLauncherActDialog(AndroidManifestInjector.getLauncherActivity(sc_id))), true);
        basicCategoryView.addLibraryItem(createOption(Helper.getResString(R.string.manifest_option_all_activities), Helper.getResString(R.string.manifest_option_all_activities_desc), R.drawable.ic_mtrl_frame_source, v -> {
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), AndroidManifestInjectionDetails.class);
            intent.putExtra("sc_id", sc_id);
            intent.putExtra("file_name", currentActivityName);
            intent.putExtra("type", "all");
            startActivity(intent);
        }), true);
        basicCategoryView.addLibraryItem(createOption(Helper.getResString(R.string.manifest_option_app_components), Helper.getResString(R.string.manifest_option_app_components_desc), R.drawable.ic_mtrl_component, v -> showAppComponentDialog()), false);

        options.forEach(binding.cards::addView);
    }

    private LibraryItemView createOption(String title, String description, int icon, View.OnClickListener onClick) {
        var card = new LibraryItemView(this);
        makeup(card, icon, title, description);
        card.setOnClickListener(onClick);
        return card;
    }

    private void showAppComponentDialog() {
        Intent intent = new Intent();
        intent.setClass(getApplicationContext(), SrcCodeEditor.class);

        String APP_COMPONENTS_PATH = getAppComponentsPath();
        if (!FileUtil.isExistFile(APP_COMPONENTS_PATH)) FileUtil.writeFile(APP_COMPONENTS_PATH, "");
        intent.putExtra("content", APP_COMPONENTS_PATH);
        intent.putExtra("xml", "");
        intent.putExtra("disableHeader", "");
        intent.putExtra("title", Helper.getResString(R.string.manifest_option_app_components));
        startActivity(intent);
    }

    private void showLauncherActDialog(String actnamr) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_mtrl_lifecycle);
        dialog.setTitle(Helper.getResString(R.string.change_launcher_activity_dialog_title));
        View view = ViewUtil.inflateLayout(this, R.layout.dialog_add_custom_activity);

        TextInputEditText activity_name_input = view.findViewById(R.id.activity_name_input);

        activity_name_input.setText(actnamr);

        dialog.setView(view);
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            if (!Helper.getText(activity_name_input).trim().isEmpty()) {
                AndroidManifestInjector.setLauncherActivity(sc_id, Helper.getText(activity_name_input));
                SketchwareUtil.toast(Helper.getResString(R.string.common_word_saved));
                v.dismiss();
            } else {
                activity_name_input.setError(Helper.getResString(R.string.error_enter_activity_name));
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    public void showAddActivityDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_mtrl_add);
        dialog.setTitle(Helper.getResString(R.string.common_word_add_activtiy));
        View inflate = ViewUtil.inflateLayout(this, R.layout.dialog_add_custom_activity);

        TextInputEditText activity_name_input = inflate.findViewById(R.id.activity_name_input);

        activity_name_input.setText(currentActivityName);

        dialog.setView(inflate);
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            if (!Helper.getText(activity_name_input).trim().isEmpty()) {
                addNewActivity(Helper.getText(activity_name_input));
                SketchwareUtil.toast(Helper.getResString(R.string.toast_new_activity_added));
                v.dismiss();
            } else {
                activity_name_input.setError(Helper.getResString(R.string.error_enter_activity_name));
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void addNewActivity(String componentName) {
        String path = getAttributesPath();
        ArrayList<HashMap<String, Object>> data = new ArrayList<>();
        if (FileUtil.isExistFile(path)) {
            try {
                data = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            } catch (JsonSyntaxException e) {
            LogUtil.w("AndroidManifestInjection", "Caught JsonSyntaxException", e);
        }
        }
        {
            HashMap<String, Object> _item = new HashMap<>();
            _item.put("name", componentName);
            _item.put("value", "android:configChanges=\"orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout\"");

            data.add(_item);
        }
        {
            HashMap<String, Object> _item = new HashMap<>();
            _item.put("name", componentName);
            _item.put("value", "android:hardwareAccelerated=\"true\"");

            data.add(_item);
        }
        {
            HashMap<String, Object> _item = new HashMap<>();
            _item.put("name", componentName);
            _item.put("value", "android:supportsPictureInPicture=\"true\"");

            data.add(_item);
        }
        {
            HashMap<String, Object> _item = new HashMap<>();
            _item.put("name", componentName);
            _item.put("value", "android:screenOrientation=\"portrait\"");

            data.add(_item);
        }
        {
            HashMap<String, Object> _item = new HashMap<>();
            _item.put("name", componentName);
            _item.put("value", "android:theme=\"@style/AppTheme\"");

            data.add(_item);
        }

        {
            HashMap<String, Object> _item = new HashMap<>();
            _item.put("name", componentName);
            _item.put("value", "android:windowSoftInputMode=\"stateHidden\"");

            data.add(_item);
        }

        FileUtil.writeFile(path, getGson().toJson(data));
        refreshList();
    }

    private void refreshList() {
        activitiesListMap.clear();
        String path = getAttributesPath();
        ArrayList<String> temp = new ArrayList<>();
        ArrayList<HashMap<String, Object>> data;
        if (FileUtil.isExistFile(path)) {
            try {
                data = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            } catch (JsonSyntaxException e) {
                return;
            }
            if (data == null) {
                return;
            }
            for (int i = 0; i < data.size(); i++) {
                Object rawName = data.get(i).get("name");
                if (rawName != null) {
                    String name = rawName.toString();
                    if (!temp.contains(name)) {
                        if (!"_application_attrs".equals(name) && !"_apply_for_all_activities".equals(name) && !"_application_permissions".equals(name)) {
                            temp.add(name);
                        }
                    }
                }
            }
            for (int i = 0; i < temp.size(); i++) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("act_name", temp.get(i));
                activitiesListMap.add(map);
            }
            binding.activitiesListView.setAdapter(new ListAdapter(activitiesListMap));
            ((BaseAdapter) binding.activitiesListView.getAdapter()).notifyDataSetChanged();
        }
    }

    private void deleteActivity(int pos) {
        Object actNameValue = activitiesListMap.get(pos).get("act_name");
        String activity_name = actNameValue != null ? actNameValue.toString() : "";
        String path = getAttributesPath();
        ArrayList<HashMap<String, Object>> data;
        try {
            data = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
        } catch (JsonSyntaxException e) {
            return;
        }
        if (data == null) {
            return;
        }
        for (int i = data.size() - 1; i > -1; i--) {
            Object rawName = data.get(i).get("name");
            if (activity_name.equals(rawName)) {
                data.remove(i);
            }
        }
        FileUtil.writeFile(path, getGson().toJson(data));
        refreshList();
        removeComponents(activity_name);
        SketchwareUtil.toast(Helper.getResString(R.string.toast_activity_removed));
    }

    private void removeComponents(String activityName) {
        String path = getActivitiesComponentsPath();
        ArrayList<HashMap<String, Object>> data;
        if (FileUtil.isExistFile(path)) {
            try {
                data = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            } catch (JsonSyntaxException e) {
                return;
            }
            if (data == null) {
                return;
            }
            for (int i = data.size() - 1; i > -1; i--) {
                Object rawName = data.get(i).get("name");
                if (activityName.equals(rawName)) {
                    data.remove(i);
                    break;
                }
            }
            FileUtil.writeFile(path, getGson().toJson(data));
        }
    }

    private void setupCustomToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.manifest_title);
        toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, Helper.getResString(R.string.manifest_show_source)).setIcon(getDrawable(R.drawable.ic_mtrl_code)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == 1) {
            showQuickManifestSourceDialog();
        } else {
            return false;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void showQuickManifestSourceDialog() {
        showLoadingDialog();
        BackgroundTasks.callIoIfAlive(TaskHost.of(this), "AndroidManifestInjection", () ->
                new ProjectFilePaths(getApplicationContext(), sc_id).getFileSrc(
                        "AndroidManifest.xml",
                        ProjectDataManager.getFileManager(sc_id),
                        ProjectDataManager.getProjectDataManager(sc_id),
                        ProjectDataManager.getLibraryManager(sc_id)), source -> {
            dismissLoadingDialog();
            var intent = new Intent(this, CodeViewerActivity.class);
            intent.putExtra("code", !source.isEmpty() ? source : "Failed to generate source.");
            intent.putExtra("sc_id", sc_id);
            intent.putExtra("scheme", CodeViewerActivity.SCHEME_XML);
            startActivity(intent);
        }, error -> dismissLoadingDialog());
    }

    private void makeup(LibraryItemView parent, int icon, String title, String description) {
        parent.enabled.setVisibility(View.GONE);
        parent.icon.setImageResource(icon);
        parent.title.setText(title);
        parent.description.setText(description);
    }

    private String safeReadFile(String path) {
        if (path == null) return "";
        if (FileUtil.isExistFile(path)) {
            String s = FileUtil.readFile(path);
            return s != null ? s : "";
        }
        return "";
    }

    private void loadDefaultManifestIntoTextArea(TextInputEditText manifestInput) {
        manifestInput.setText("Loading default manifest...");
        BackgroundTasks.callIoIfAlive(TaskHost.of(this), "AndroidManifestInjection", () ->
                new ProjectFilePaths(getApplicationContext(), sc_id).getFileSrc(
                        "AndroidManifest.xml",
                        ProjectDataManager.getFileManager(sc_id),
                        ProjectDataManager.getProjectDataManager(sc_id),
                        ProjectDataManager.getLibraryManager(sc_id)
                ), (String defaultManifest) -> {
            if (!isFinishing()) {
                manifestInput.setText(!defaultManifest.isEmpty() ? defaultManifest : "Failed to generate default manifest.");
                SketchwareUtil.toast("Default manifest loaded for editing");
            }
        }, error -> {
            if (!isFinishing()) {
                manifestInput.setText("Error loading default manifest.");
                SketchwareUtil.toast("Error loading default manifest");
            }
        });
    }

    private void setupManifestMode() {
        android.widget.RadioGroup manifestModeGroup = findViewById(R.id.manifest_mode_group);
        android.widget.RadioButton radioDefault = findViewById(R.id.radio_default);
        android.widget.RadioButton radioCustom = findViewById(R.id.radio_custom);
        View manifestContainer = findViewById(R.id.manifest_input_container);
        TextInputEditText manifestInput = findViewById(R.id.manifest_input);

        android.widget.Button btnSaveManifest = findViewById(R.id.btn_save_manifest);
        android.widget.Button btnLoadDefault = findViewById(R.id.btn_load_default);
        android.widget.Button btnResetManifest = findViewById(R.id.btn_reset_manifest);

        if (manifestModeGroup == null || manifestInput == null) {
            return;
        }

        final String INJECTION_MANIFEST_DIR = SketchwarePaths.getMyscPath(sc_id) + File.separator + "Injection" + File.separator + "androidmanifest" + File.separator;
        final String MANIFEST_MODE_PATH = INJECTION_MANIFEST_DIR + "manifest_mode.txt";
        final String CUSTOM_MANIFEST_PATH = INJECTION_MANIFEST_DIR + "custom_manifest.xml";

        final String BASE_MANIFEST_FLAG_PATH = SketchwarePaths.getMyscPath(sc_id) + File.separator + "Manifast";
        final String BASE_MANIFEST_XML_PATH = SketchwarePaths.getMyscPath(sc_id) + File.separator + "Manifast.xml";

        File dir = new File(INJECTION_MANIFEST_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (!FileUtil.isExistFile(CUSTOM_MANIFEST_PATH)) {
            FileUtil.writeFile(CUSTOM_MANIFEST_PATH, "");
        }
        if (!FileUtil.isExistFile(MANIFEST_MODE_PATH)) {
            FileUtil.writeFile(MANIFEST_MODE_PATH, "default");
        }
        if (!FileUtil.isExistFile(BASE_MANIFEST_FLAG_PATH)) {
            FileUtil.writeFile(BASE_MANIFEST_FLAG_PATH, "false");
        }
        if (!FileUtil.isExistFile(BASE_MANIFEST_XML_PATH)) {
            FileUtil.writeFile(BASE_MANIFEST_XML_PATH, "");
        }

        String mode = safeReadFile(MANIFEST_MODE_PATH).trim();
        if ("custom".equals(mode)) {
            radioCustom.setChecked(true);
            manifestContainer.setVisibility(View.VISIBLE);
            String saved = safeReadFile(CUSTOM_MANIFEST_PATH);
            manifestInput.setText(saved);
            FileUtil.writeFile(BASE_MANIFEST_FLAG_PATH, "true");
            FileUtil.writeFile(BASE_MANIFEST_XML_PATH, saved != null ? saved : "");
        } else {
            radioDefault.setChecked(true);
            manifestContainer.setVisibility(View.GONE);
            FileUtil.writeFile(BASE_MANIFEST_FLAG_PATH, "false");
            FileUtil.writeFile(BASE_MANIFEST_XML_PATH, "");
        }

        manifestModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_custom) {
                manifestContainer.setVisibility(View.VISIBLE);
                FileUtil.writeFile(MANIFEST_MODE_PATH, "custom");
                FileUtil.writeFile(BASE_MANIFEST_FLAG_PATH, "true");

                String currentContent = safeReadFile(CUSTOM_MANIFEST_PATH);
                if (currentContent.trim().isEmpty()) {
                    loadDefaultManifestIntoTextArea(manifestInput);
                } else {
                    manifestInput.setText(currentContent);
                }

                FileUtil.writeFile(BASE_MANIFEST_XML_PATH, manifestInput.getText().toString());
                SketchwareUtil.toast("Custom manifest selected");
            } else {
                manifestContainer.setVisibility(View.GONE);
                FileUtil.writeFile(MANIFEST_MODE_PATH, "default");
                FileUtil.writeFile(BASE_MANIFEST_FLAG_PATH, "false");
                FileUtil.writeFile(BASE_MANIFEST_XML_PATH, "");
                SketchwareUtil.toast("Default manifest selected");
            }
        });

        btnSaveManifest.setOnClickListener(v -> {
            String content = Helper.getText(manifestInput);
            FileUtil.writeFile(CUSTOM_MANIFEST_PATH, content);
            FileUtil.writeFile(BASE_MANIFEST_XML_PATH, content);
            FileUtil.writeFile(MANIFEST_MODE_PATH, "custom");
            FileUtil.writeFile(BASE_MANIFEST_FLAG_PATH, "true");
            SketchwareUtil.toast("Custom manifest saved");
        });

        btnLoadDefault.setOnClickListener(v -> {
            loadDefaultManifestIntoTextArea(manifestInput);
        });

        btnResetManifest.setOnClickListener(v -> {
            FileUtil.writeFile(CUSTOM_MANIFEST_PATH, "");
            FileUtil.writeFile(BASE_MANIFEST_XML_PATH, "");
            FileUtil.writeFile(MANIFEST_MODE_PATH, "default");
            FileUtil.writeFile(BASE_MANIFEST_FLAG_PATH, "false");
            manifestInput.setText("");
            radioDefault.setChecked(true);
            SketchwareUtil.toast("Custom manifest reset");
        });
    }

    private class ListAdapter extends BaseAdapter {
        private final ArrayList<HashMap<String, Object>> _data;

        public ListAdapter(ArrayList<HashMap<String, Object>> data) {
            _data = data;
        }

        @Override
        public int getCount() {
            return _data.size();
        }

        @Override
        public HashMap<String, Object> getItem(int position) {
            return _data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CustomAttributeView attributeView = new CustomAttributeView(parent.getContext());

            attributeView.getImageView().setVisibility(View.GONE);
            attributeView.getTextView().setText((String) activitiesListMap.get(position).get("act_name"));
            attributeView.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setClass(getApplicationContext(), AndroidManifestInjectionDetails.class);
                intent.putExtra("sc_id", sc_id);
                intent.putExtra("file_name", (String) _data.get(position).get("act_name"));
                intent.putExtra("type", "activity");
                startActivity(intent);
            });
            attributeView.setOnLongClickListener(v -> {
                {
                    MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(AndroidManifestInjection.this);
                    dialog.setIcon(R.drawable.icon_delete);
                    dialog.setTitle(Helper.getResString(R.string.delete_custom_activity_dialog_title));
                    dialog.setMessage(Helper.getResString(R.string.delete_custom_activity_dialog_message).replace("%1$s", (String) _data.get(position).get("act_name")));

                    dialog.setPositiveButton(Helper.getResString(R.string.common_word_delete), (v1, which) -> {
                        deleteActivity(position);
                        v1.dismiss();
                    });
                    dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
                    dialog.show();
                }
                return true;
            });

            return attributeView;
        }
    }
}
