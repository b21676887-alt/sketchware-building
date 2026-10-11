package pro.sketchware.activities.design;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.util.Base64;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import pro.sketchware.beans.ViewBean;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import pro.sketchware.beans.ProjectFileBean;
import pro.sketchware.activities.tools.SrcViewerActivity;
import pro.sketchware.activities.editor.manage.ManageCollectionActivity;
import pro.sketchware.activities.editor.manage.ViewSelectorActivity;
import pro.sketchware.activities.editor.manage.font.ManageFontActivity;
import pro.sketchware.activities.editor.manage.image.ManageImageActivity;
import pro.sketchware.activities.editor.manage.library.ManageLibraryActivity;
import pro.sketchware.activities.editor.manage.sound.ManageSoundActivity;
import pro.sketchware.activities.editor.manage.view.ManageViewActivity;
import pro.sketchware.activities.base.BaseAppCompatActivity;
import pro.sketchware.widgets.CustomViewPager;
import pro.sketchware.activities.tools.CompileLogActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import pro.sketchware.core.async.BackgroundTasks;
import pro.sketchware.util.io.SharedPrefsHelper;
import pro.sketchware.util.DeviceUtil;
import pro.sketchware.core.codegen.LayoutGenerator;
import pro.sketchware.activities.design.fragments.ViewEditorFragment;
import pro.sketchware.util.SketchToast;
import pro.sketchware.core.project.BlockHistoryManager;
import pro.sketchware.activities.design.fragments.ComponentListFragment;
import pro.sketchware.core.project.ViewHistoryManager;
import pro.sketchware.core.project.ProjectDataStore;
import pro.sketchware.core.project.ProjectDataManager;
import pro.sketchware.core.project.ResourceManager;
import pro.sketchware.core.project.ProjectListManager;
import pro.sketchware.core.async.TaskHost;
import pro.sketchware.util.UIHelper;
import pro.sketchware.activities.design.fragments.EventListFragment;
import pro.sketchware.core.project.SketchwarePaths;
import pro.sketchware.util.MapValueHelper;
import pro.sketchware.core.build.ProjectFilePaths;
import pro.sketchware.util.UI;
import pro.sketchware.activities.editor.ManagePermissionActivity;
import pro.sketchware.activities.editor.ManageResourceActivity;
import pro.sketchware.activities.editor.ManageAssetsActivity;
import pro.sketchware.activities.editor.ManageJavaActivity;
import pro.sketchware.util.Helper;
import pro.sketchware.util.SystemLogPrinter;
import pro.sketchware.activities.editor.manifest.AndroidManifestInjection;
import pro.sketchware.activities.settings.ConfigActivity;
import pro.sketchware.core.build.CompileErrorSaver;
import pro.sketchware.util.LogUtil;
import pro.sketchware.activities.editor.LogReaderActivity;
import pro.sketchware.R;
import pro.sketchware.activities.appcompat.ManageAppCompatActivity;
import pro.sketchware.activities.editor.command.ManageXMLCommandActivity;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;
import pro.sketchware.activities.resourceseditor.ResourcesEditorActivity;
import pro.sketchware.dialogs.BuildSettingsBottomSheet;
import pro.sketchware.util.FileUtil;
import pro.sketchware.util.SketchwareUtil;
import pro.sketchware.util.apk.ApkSignatures;
import pro.sketchware.beans.HistoryViewBean;
import pro.sketchware.tools.InjectRootLayoutManager;
import pro.sketchware.tools.ViewBeanParser;
import mod.sketchlibx.search.GlobalSearchDialog;
import de.hdodenhof.circleimageview.CircleImageView;


public class DesignActivity extends BaseAppCompatActivity implements View.OnClickListener {
    private String sc_id;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FirebaseCrashlytics crashlytics = getFirebaseCrashlytics();
    private DesignProjectController projectController;
    private ImageView xmlLayoutOrientation;
    private boolean isRestoringData;
    private int currentTabNumber;
    private CustomViewPager viewPager;
    private CoordinatorLayout coordinatorLayout;
    private DrawerLayout drawer;
    private ProjectFilePaths projectFilePaths;
    private SharedPrefsHelper prefP1;
    private SharedPrefsHelper prefP12;
    private Menu bottomMenu;
    private PopupMenu bottomPopupMenu;
    private MaterialButton btnRun;
    private MaterialButton btnOptions;
    private DesignEditorViewModel editorViewModel;
    private TextView fileName;
    private String currentJavaFileName;
    private ViewEditorFragment viewTabAdapter;
    private StringsTabFragment stringsTabAdapter;
    
    private Toolbar toolbar;
    private AlertDialog aiLayoutLoadingDialog;
    private TextView aiLayoutLoadingTitle;
    private TextView aiLayoutLoadingSubtitle;
    private TextView aiLayoutReferenceImageLabel;
    private LinearLayout aiLayoutReferenceImagePreviewList;
    private final ArrayList<Uri> aiLayoutReferenceImageUris = new ArrayList<>();
    
    private final ActivityResultLauncher<Intent> openCollectionManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null) {
                viewTabAdapter.refreshFavorites();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openResourcesManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null && viewPager.getCurrentItem() == 0) {
                viewTabAdapter.refreshAllViews();
                editorViewModel.refreshActiveProjectFile();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openViewCodeEditor = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null) {
                viewTabAdapter.refreshAllViews();
            }
        }
    });
    private EventListFragment eventTabAdapter;
    private ComponentListFragment componentTabAdapter;
    private final ActivityResultLauncher<Intent> openImageManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
        }
    });
    private final ActivityResultLauncher<String> selectAiLayoutReferenceImages = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
        if (uris != null && !uris.isEmpty()) {
            aiLayoutReferenceImageUris.clear();
            aiLayoutReferenceImageUris.addAll(uris);
            updateAiLayoutReferenceImagePreview();
        }
    });
    public final ActivityResultLauncher<Intent> changeOpenFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            var data = result.getData();
            if (data == null) return;
            ProjectFileBean selectedFile = data.getParcelableExtra("project_file");
            if (selectedFile != null) {
                editorViewModel.selectProjectFile(selectedFile);
                refresh();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openLibraryManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
            if (viewTabAdapter != null) {
                viewTabAdapter.updatePropertyViews();
            }
        }
    });
    private final ActivityResultLauncher<Intent> openViewManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
        }
    });
    private DesignBuildController currentBuildController;
    private final BroadcastReceiver buildCancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DesignBuildController.ACTION_CANCEL_BUILD.equals(intent.getAction())) {
                if (currentBuildController != null) {
                    currentBuildController.cancelBuild();
                }
            }
        }
    };

    /**
     * Saves the app's version information to the currently opened Sketchware project file.
     */
    void saveVersionCodeInformationToProject() {
        HashMap<String, Object> projectMetadata = ProjectListManager.getProjectById(sc_id);
        if (projectMetadata != null) {
            projectMetadata.put("sketchware_ver", DeviceUtil.getVersionCode(getApplicationContext()));
            ProjectListManager.updateProject(sc_id, projectMetadata);
        }
    }

    void loadProject(boolean haveSavedState) {
        ProjectDataManager.getProjectDataManager(sc_id, haveSavedState);
        ProjectDataManager.getFileManager(sc_id, haveSavedState);
        ResourceManager resourceManager = ProjectDataManager.getResourceManager(sc_id, haveSavedState);
        ProjectDataManager.getLibraryManager(sc_id, haveSavedState);
        ViewHistoryManager.getInstance(sc_id);
        BlockHistoryManager.getInstance(sc_id);
        // Resource backup is now lazy �?ensureBackedUp() is called
        // before any resource modification, not eagerly on project open.
    }

    private ProjectFileBean getDefaultProjectFile() {
        return ProjectDataManager.getFileManager(sc_id).getFileByXmlName(ProjectFileBean.DEFAULT_XML_NAME);
    }

    private boolean refreshFileSelector() {
        boolean projectFileChanged = false;
        ProjectFileBean projectFile = editorViewModel.getActiveProjectFile();
        if (projectFile == null) {
            projectFile = getDefaultProjectFile();
            if (projectFile == null) {
                return false;
            }
            editorViewModel.selectProjectFile(projectFile);
            projectFileChanged = true;
        }

        String javaFileName = projectFile.getJavaName();
        String xmlFileName = projectFile.getXmlName();

        if (!javaFileName.isEmpty()) {
            currentJavaFileName = javaFileName;
        }

        if (viewPager.getCurrentItem() == 0) {
            if (!ProjectFileBean.DEFAULT_XML_NAME.equals(xmlFileName) && ProjectDataManager.getFileManager(sc_id).getFileByXmlName(xmlFileName) == null) {
                projectFile = getDefaultProjectFile();
                if (projectFile == null) {
                    return false;
                }
                editorViewModel.selectProjectFile(projectFile);
                projectFileChanged = true;
                xmlFileName = ProjectFileBean.DEFAULT_XML_NAME;
            }
            fileName.setText(xmlFileName);
        } else {
            if (!ProjectFileBean.DEFAULT_JAVA_NAME.equals(currentJavaFileName) && ProjectDataManager.getFileManager(sc_id).getActivityByJavaName(currentJavaFileName) == null) {
                projectFile = getDefaultProjectFile();
                if (projectFile == null) {
                    return false;
                }
                editorViewModel.selectProjectFile(projectFile);
                projectFileChanged = true;
                currentJavaFileName = ProjectFileBean.DEFAULT_JAVA_NAME;
            }
            fileName.setText(currentJavaFileName);
        }
        updateFileSelectorIcon();
        return projectFileChanged;
    }

    private void updateFileSelectorIcon() {
        if (xmlLayoutOrientation == null || viewPager == null) {
            return;
        }
        if (viewPager.getCurrentItem() != 0 || editorViewModel.getActiveProjectFile() == null) {
            xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
            return;
        }
        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_devices);
    }

    void refresh() {
        if (!refreshFileSelector()) {
            editorViewModel.refreshActiveProjectFile();
        }
    }

    public void setTouchEventEnabled(boolean touchEventEnabled) {
        if (touchEventEnabled) {
            viewPager.enableTouchEvent();
        } else {
            viewPager.disableTouchEvent();
        }
    }

    /**
     * Shows a Snackbar indicating that a problem occurred while compiling. The user can click on "SHOW" to get to {@link CompileLogActivity}.
     *
     * @param error The error, to be later displayed as text in {@link CompileLogActivity}
     */
    void indicateCompileErrorOccurred(String error) {
        new CompileErrorSaver(sc_id).writeLogsToFile(error);
        Snackbar snackbar = Snackbar.make(coordinatorLayout, Helper.getResString(R.string.snackbar_show_compile_log), Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(Helper.getResString(R.string.common_word_show), v -> {
            if (!UIHelper.isClickThrottled()) {
                snackbar.dismiss();
                Intent intent = new Intent(getApplicationContext(), CompileLogActivity.class);
                intent.putExtra("error", error);
                intent.putExtra("sc_id", sc_id);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });
        snackbar.show();
    }

    @Override
    public void finish() {
        ProjectDataManager.clearAll();
        ViewHistoryManager.clearInstance();
        BlockHistoryManager.clearInstance();
        setResult(RESULT_CANCELED, getIntent());
        super.finish();
    }

    void checkForUnsavedProjectData() {
        if (ProjectDataManager.getLibraryManager(sc_id).hasBackup() || ProjectDataManager.getFileManager(sc_id).hasBackup() || ProjectDataManager.getResourceManager(sc_id).hasBackup() || ProjectDataManager.getProjectDataManager(sc_id).hasViewBackup() || ProjectDataManager.getProjectDataManager(sc_id).hasLogicBackup()) {
            askIfToRestoreOldUnsavedProjectData();
        }
    }

    /**
     * Opens the debug APK to install.
     */
    void installBuiltApk() {
        TaskHost taskHost = TaskHost.of(this);
        taskHost.postToUi(() -> {
            if (!ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_INSTALL_PROJECTS)) {
                requestPackageInstallerInstall();
            } else {
                File apkUri = new File(projectFilePaths.finalToInstallApkPath);
                long length = apkUri.length();
                Shell.getShell(shell -> {
                    if (shell.isRoot()) {
                        List<String> stdout = new LinkedList<>();
                        List<String> stderr = new LinkedList<>();

                        Shell.cmd("cat " + apkUri + " | pm install -S " + length).to(stdout, stderr).submit(result ->
                                taskHost.postToUi(() -> {
                                    if (result.isSuccess()) {
                                        SketchwareUtil.toast(Helper.getResString(R.string.design_toast_package_installed));
                                        if (ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING)) {
                                            Intent launcher = getPackageManager().getLaunchIntentForPackage(projectFilePaths.packageName);
                                            if (launcher != null) {
                                                startActivity(launcher);
                                            } else {
                                                SketchwareUtil.toastError(Helper.getResString(R.string.design_error_cannot_launch));
                                            }
                                        }
                                    } else {
                                        SketchwareUtil.toastError(String.format(Helper.getResString(R.string.design_error_install_failed), result.getCode()), Toast.LENGTH_LONG);
                                        LogUtil.e("DesignActivity", "Failed to install package, result code: " + result.getCode() + ". stdout: " + stdout + ", stderr: " + stderr);
                                    }
                                }));
                    } else {
                        taskHost.postToUi(() -> {
                            SketchwareUtil.toastError(Helper.getResString(R.string.design_error_no_root_access));
                            requestPackageInstallerInstall();
                        });
                    }
                });
            }
        });
    }

    private void requestPackageInstallerInstall() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", new File(projectFilePaths.finalToInstallApkPath));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            SketchwareUtil.toastError(Helper.getResString(R.string.error_no_package_installer));
        }
    }

    public void hideViewPropertyView() {
        viewTabAdapter.togglePropertyView(false);
    }

    private void saveChangesAndCloseProject() {
        showLoadingDialog();
        projectController.saveAndClose();
    }

    private void saveProject() {
        showLoadingDialog();
        projectController.save();
    }

    void showProjectLoadingDialog() {
        showLoadingDialog();
    }

    void dismissProjectLoadingDialog() {
        dismissLoadingDialog();
    }

    void recordProjectControllerFailure(String message, RuntimeException error) {
        if (crashlytics != null) {
            crashlytics.log(message);
            crashlytics.recordException(error);
        }
    }

    ProjectFilePaths getProjectFilePaths() {
        return projectFilePaths;
    }

    void onBuildControllerStarted() {
        prefP1.put("P1I10", true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    void onBuildControllerFinished(DesignBuildController controller) {
        if (currentBuildController == controller) {
            currentBuildController = null;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.END)) {
                    drawer.closeDrawer(GravityCompat.END);
                } else if (viewTabAdapter.isPropertyViewVisible()) {
                    hideViewPropertyView();
                } else {
                    if (currentTabNumber > 0) {
                        currentTabNumber--;
                        viewPager.setCurrentItem(currentTabNumber);
                    } else if (prefP12.getBooleanDefault("P12I2")) {
                        showLoadingDialog();
                        saveChangesAndCloseProject();
                    } else {
                        showSaveBeforeQuittingDialog();
                    }
                }
            }
        });
        setContentView(R.layout.design);
        if (!isStoragePermissionGranted()) {
            finish();
        }

        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        if (sc_id == null || sc_id.isEmpty()) {
            finish();
            return;
        }

        editorViewModel = new ViewModelProvider(this).get(DesignEditorViewModel.class);
        projectController = new DesignProjectController(this, sc_id);

        prefP1 = new SharedPrefsHelper(getApplicationContext(), "P1");
        prefP12 = new SharedPrefsHelper(getApplicationContext(), "P12");

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(sc_id);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        drawer = findViewById(R.id.drawer_layout);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        UI.addWindowInsetToMargin(findViewById(R.id.container), WindowInsetsCompat.Type.navigationBars(), false, false, false, true);

        coordinatorLayout = findViewById(R.id.layout_coordinator);
        fileName = findViewById(R.id.file_name);

        findViewById(R.id.file_name_container).setOnClickListener(this);

        btnRun = findViewById(R.id.btn_run);
        btnRun.setOnClickListener(v -> {
            if (currentBuildController != null && !currentBuildController.isFinished()) {
                if (!currentBuildController.isCanceled()) {
                    currentBuildController.cancelBuild();
                }
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }

            DesignBuildController buildController = new DesignBuildController(this, sc_id);
            currentBuildController = buildController;
            buildController.execute();
        });

        btnOptions = findViewById(R.id.btn_options);
        btnOptions.setOnClickListener(v -> bottomPopupMenu.show());

        bottomPopupMenu = new PopupMenu(this, btnOptions);
        bottomMenu = bottomPopupMenu.getMenu();
        bottomMenu.add(Menu.NONE, 1, Menu.NONE, Helper.getResString(R.string.design_menu_build_settings)).setOnMenuItemClickListener(item -> {
            BuildSettingsBottomSheet sheet = BuildSettingsBottomSheet.newInstance(sc_id);
            sheet.show(getSupportFragmentManager(), BuildSettingsBottomSheet.TAG);
            return true;
        });
        bottomMenu.add(Menu.NONE, 2, Menu.NONE, Helper.getResString(R.string.design_menu_clean_temp)).setVisible(false).setOnMenuItemClickListener(item -> {
            BackgroundTasks.runIo(TaskHost.of(this), "DesignActivity", () -> FileUtil.deleteFile(projectFilePaths.projectMyscPath), () -> {
                updateBottomMenu();
                SketchwareUtil.toast(Helper.getResString(R.string.design_toast_clean_temp_done));
            }, error -> Log.e("DesignActivity", "Failed to clean temporary files", error));
            return true;
        });
        bottomMenu.add(Menu.NONE, 3, Menu.NONE, Helper.getResString(R.string.design_menu_show_last_error)).setOnMenuItemClickListener(item -> {
            new CompileErrorSaver(sc_id).showLastErrors(this);
            return true;
        });
        bottomMenu.add(Menu.NONE, 5, Menu.NONE, Helper.getResString(R.string.design_menu_show_source)).setOnMenuItemClickListener(item -> {
            showCurrentActivitySrcCode();
            return true;
        });
        bottomMenu.add(Menu.NONE, 4, Menu.NONE, Helper.getResString(R.string.design_menu_install_apk)).setVisible(false).setOnMenuItemClickListener(item -> {
            if (FileUtil.isExistFile(projectFilePaths.finalToInstallApkPath)) {
                installBuiltApk();
            } else SketchwareUtil.toast(Helper.getResString(R.string.design_error_apk_not_exist));
            return true;
        });
        bottomMenu.add(Menu.NONE, 6, Menu.NONE, Helper.getResString(R.string.design_menu_show_signatures)).setVisible(false).setOnMenuItemClickListener(item -> {
            ApkSignatures apkSignatures = new ApkSignatures(this, projectFilePaths.finalToInstallApkPath);
            apkSignatures.showSignaturesDialog();
            return true;
        });
        bottomMenu.add(Menu.NONE, 7, Menu.NONE, Helper.getResString(R.string.design_menu_xml_editor)).setOnMenuItemClickListener(item -> {
            toViewCodeEditor();
            return true;
        });
        bottomMenu.add(Menu.NONE, 8, Menu.NONE, Helper.getResString(R.string.menu_import_xml)).setOnMenuItemClickListener(item -> {
            if (viewTabAdapter != null) {
                viewTabAdapter.showImportXmlDialog();
            }
            return true;
        });
        bottomMenu.add(Menu.NONE, 9, Menu.NONE, Helper.getResString(R.string.design_menu_reset_layout)).setOnMenuItemClickListener(item -> {
            resetRootLayout();
            return true;
        });
        bottomMenu.add(Menu.NONE, 10, Menu.NONE, Helper.getResString(R.string.design_menu_transcribe_to_Material)).setOnMenuItemClickListener(item -> {
            transcribeToMaterial3();
            return true;
        });
        bottomPopupMenu.setOnDismissListener(menu -> btnOptions.setChecked(false));

        xmlLayoutOrientation = findViewById(R.id.img_orientation);
        viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(new ViewPagerAdapter(getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(3);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrollStateChanged(int state) {
                if (viewTabAdapter != null && viewTabAdapter.viewEditor != null) {
                    viewTabAdapter.viewEditor.setLayerType(
                            state != ViewPager.SCROLL_STATE_IDLE ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE,
                            null);
                }
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (currentTabNumber == 1) {
                    if (eventTabAdapter != null) {
                        eventTabAdapter.resetEventValues();
                    }
                } else if (currentTabNumber == 2 && componentTabAdapter != null) {
                    componentTabAdapter.unselectAll();
                }
                if (position == 0) {
                    bottomMenu.findItem(7).setVisible(true);
                    bottomMenu.findItem(8).setVisible(true);
                    bottomMenu.findItem(9).setVisible(true);
                    bottomMenu.findItem(10).setVisible(true);
                    if (viewTabAdapter != null) {
                        viewTabAdapter.showHidePropertyView(true);
                    }
                } else if (position == 1) {
                    bottomMenu.findItem(7).setVisible(false);
                    bottomMenu.findItem(8).setVisible(false);
                    bottomMenu.findItem(9).setVisible(false);
                    bottomMenu.findItem(10).setVisible(false);
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                        if (eventTabAdapter != null) {
                            eventTabAdapter.refreshEvents();
                        }
                    }
                } else if (position == 2) {
                    bottomMenu.findItem(7).setVisible(false);
                    bottomMenu.findItem(8).setVisible(false);
                    bottomMenu.findItem(9).setVisible(false);
                    bottomMenu.findItem(10).setVisible(false);
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                        if (componentTabAdapter != null) {
                            componentTabAdapter.refreshData();
                        }
                    }
                } else if (position == 3) {
                    bottomMenu.findItem(7).setVisible(false);
                    bottomMenu.findItem(8).setVisible(false);
                    bottomMenu.findItem(9).setVisible(false);
                    bottomMenu.findItem(10).setVisible(false);
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                    }
                    if (stringsTabAdapter != null) {
                        stringsTabAdapter.refreshList();
                    }
                }
                currentTabNumber = position;
                refreshFileSelector();
                invalidateOptionsMenu();
            }
        });
        viewPager.getAdapter().notifyDataSetChanged();
        ((TabLayout) findViewById(R.id.tab_layout)).setupWithViewPager(viewPager);

        IntentFilter filter = new IntentFilter(DesignBuildController.ACTION_CANCEL_BUILD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(buildCancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(buildCancelReceiver, filter);
        }

    }

    private boolean isDebugApkExists() {
        if (projectFilePaths != null) {
            return FileUtil.isExistFile(projectFilePaths.finalToInstallApkPath);
        }
        return false;
    }

    void updateBottomMenu() {
        if (bottomMenu != null) {
            handler.post(() -> {
                bottomMenu.findItem(2).setVisible(projectFilePaths != null && FileUtil.isExistFile(projectFilePaths.projectMyscPath));
                var isDebugApkExists = isDebugApkExists();
                bottomMenu.findItem(4).setVisible(isDebugApkExists);
                bottomMenu.findItem(6).setVisible(isDebugApkExists);
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(buildCancelReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was never registered (e.g. onCreate returned early when sc_id was missing)
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.design_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.design_option_menu_search);
        if (searchItem != null) {
            searchItem.setVisible(currentTabNumber == 1 || currentTabNumber == 0 || currentTabNumber == 3);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.design_actionbar_titleopen_drawer) {
            if (!drawer.isDrawerOpen(GravityCompat.END)) {
                drawer.openDrawer(GravityCompat.END);
            }
        } else if (itemId == R.id.design_option_menu_title_save_project) {
            saveProject();
        } else if (itemId == R.id.design_option_menu_search) {
            handleSearchIconClick();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    
    
    // ==========================================
    // الخدمات المضافة: 1. الذكاء الاصطناعي وتوليد التخطيطات
    // ==========================================
    public void launchAiGenerateLayout() {
        try {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_ai_layout_generation, null);
            TextInputEditText promptInput = dialogView.findViewById(R.id.input_text);
            TextInputLayout promptContainer = dialogView.findViewById(R.id.input_layout_container);
            TextInputEditText referenceNotesInput = dialogView.findViewById(R.id.reference_image_notes);
            aiLayoutReferenceImageLabel = dialogView.findViewById(R.id.reference_image_label);
            aiLayoutReferenceImagePreviewList = dialogView.findViewById(R.id.reference_image_preview_list);
            MaterialButton selectReferenceImageButton = dialogView.findViewById(R.id.button_select_reference_image);
            aiLayoutReferenceImageUris.clear();
            updateAiLayoutReferenceImagePreview();
            if (selectReferenceImageButton != null) {
                selectReferenceImageButton.setOnClickListener(v -> selectAiLayoutReferenceImages.launch("image/*"));
            }

            var dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.ai_layout_generator_title)
                    .setView(dialogView)
                    .setPositiveButton(R.string.common_word_generate, null)
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .create();

            dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String prompt = promptInput != null && promptInput.getText() != null
                        ? promptInput.getText().toString().trim()
                        : "";

                if (prompt.isEmpty()) {
                    if (promptContainer != null) {
                        promptContainer.setError(getString(R.string.ai_layout_generator_empty_prompt));
                    }
                    if (promptInput != null) {
                        promptInput.requestFocus();
                    }
                    return;
                }

                if (promptContainer != null) {
                    promptContainer.setError(null);
                }

                dialog.dismiss();
                showAiLayoutLoadingDialog(
                        R.string.ai_layout_generator_loading_title,
                        getString(R.string.ai_layout_generator_loading_subtitle)
                );
                String referenceNotes = referenceNotesInput != null && referenceNotesInput.getText() != null
                        ? referenceNotesInput.getText().toString().trim()
                        : "";
                generateAndApplyLayoutAsync(prompt, isIncludeCurrentLayoutEnabled(dialogView), referenceNotes, new ArrayList<>(aiLayoutReferenceImageUris));
            }));

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAiLayoutErrorDialog(getString(R.string.ai_layout_generator_error_generate, getAiLayoutErrorDetail(e)));
        }
    }

    private boolean isIncludeCurrentLayoutEnabled(View dialogView) {
        View checkboxView = dialogView.findViewById(R.id.checkbox_include_current);
        return checkboxView instanceof CheckBox checkBox && checkBox.isChecked();
    }

    private void showAiLayoutLoadingDialog(int titleResId, String subtitle) {
        dismissAiLayoutLoadingDialog();

        View loadingView = getLayoutInflater().inflate(R.layout.dialog_ai_layout_loading, null);
        aiLayoutLoadingTitle = loadingView.findViewById(R.id.text_loading_title);
        aiLayoutLoadingSubtitle = loadingView.findViewById(R.id.text_loading_subtitle);
        if (aiLayoutLoadingTitle != null) {
            aiLayoutLoadingTitle.setText(titleResId);
        }
        if (aiLayoutLoadingSubtitle != null) {
            aiLayoutLoadingSubtitle.setText(subtitle);
        }

        aiLayoutLoadingDialog = new MaterialAlertDialogBuilder(this)
                .setView(loadingView)
                .setCancelable(false)
                .create();

        if (!isFinishing() && !isDestroyed()) {
            aiLayoutLoadingDialog.show();
        }
    }

    private void updateAiLayoutLoadingDialog(String subtitle) {
        runOnUiThread(() -> {
            if (aiLayoutLoadingSubtitle != null) {
                aiLayoutLoadingSubtitle.setText(subtitle);
            }
        });
    }

    private void dismissAiLayoutLoadingDialog() {
        runOnUiThread(() -> {
            try {
                if (aiLayoutLoadingDialog != null && aiLayoutLoadingDialog.isShowing()) {
                    aiLayoutLoadingDialog.dismiss();
                }
            } catch (Exception ignored) {
            } finally {
                aiLayoutLoadingDialog = null;
                aiLayoutLoadingTitle = null;
                aiLayoutLoadingSubtitle = null;
            }
        });
    }

    public void transcribeToMaterial3() {
        showAiLayoutLoadingDialog(
                R.string.ai_layout_generator_m3_loading_title,
                getString(R.string.ai_layout_generator_m3_loading_subtitle)
        );
        generateAndApplyLayoutAsync("Refactor this layout into a modern Material 3 design. " +
                "CRITICAL: You must preserve the original hierarchy and architecture of the user's layout. " +
                "DO NOT add Toolbars, ActionBars, or any outer components. " +
                "Only swap individual widgets for their modern Material 3 equivalents while maintaining all properties.", true, "", new ArrayList<>());
    }

    private void resetRootLayout() {
        ProjectFileBean activeFile = editorViewModel.getActiveProjectFile();
        if (activeFile == null) return;
        String xmlName = activeFile.getXmlName();
        try {
            var rootLayoutManager = new InjectRootLayoutManager(sc_id);
            rootLayoutManager.set(xmlName, InjectRootLayoutManager.getDefaultRootLayout());
            
            if (viewTabAdapter != null) {
                viewTabAdapter.refreshAllViews();
                refreshViewTabAdapter();
            }
            SketchwareUtil.toast("Root layout reset to default");
        } catch (Exception e) {
            SketchwareUtil.toastError("Failed to reset root: " + e.getMessage());
        }
    }

    private void refreshViewTabAdapter() {
        ProjectFileBean activeFile = editorViewModel.getActiveProjectFile();
        if (viewTabAdapter != null && activeFile != null) {
            viewTabAdapter.initialize(activeFile);
        }
    }

    private void generateAndApplyLayoutAsync(String prompt, boolean includeCurrentLayout, String referenceNotes, List<Uri> referenceImageUris) {
        ProjectFileBean activeFile = editorViewModel.getActiveProjectFile();
        if (activeFile == null) {
            dismissAiLayoutLoadingDialog();
            showAiLayoutErrorDialog(getString(R.string.ai_layout_generator_error_no_file));
            return;
        }
        List<Uri> referenceImageUrisSnapshot = referenceImageUris == null ? new ArrayList<>() : new ArrayList<>(referenceImageUris);
        new Thread(() -> {
            try {
                String currentLayoutXml = null;
                String xmlName = activeFile.getXmlName();
                
                pro.sketchware.ia.LayoutHistoryManager historyManager = new pro.sketchware.ia.LayoutHistoryManager(getApplicationContext());
                List<pro.sketchware.ia.LayoutHistoryManager.HistoryEntry> history = historyManager.getHistory(sc_id, xmlName);
                
                if (includeCurrentLayout) {
                    updateAiLayoutLoadingDialog(getString(R.string.ai_layout_generator_reading_context));
                    try {
                        var xmlGenerator = new LayoutGenerator(projectFilePaths.buildConfig, activeFile);
                        var projectDataManager = ProjectDataManager.getProjectDataManager(sc_id);
                        xmlGenerator.setViews(ProjectDataStore.getSortedRootViews(projectDataManager.getViews(xmlName)), projectDataManager.getFabView(xmlName));
                        currentLayoutXml = xmlGenerator.toXmlString();
                    } catch (Exception e) {
                        Log.e("DesignActivity", "Erro ao obter layout atual", e);
                    }
                }
                
                String referenceContext = buildReferenceContext(referenceNotes, referenceImageUrisSnapshot);
                List<String> availableDrawables = getAvailableProjectDrawables();
                List<String> referenceImageDataUrls = buildReferenceImageDataUrls(referenceImageUrisSnapshot);

                updateAiLayoutLoadingDialog(getString(R.string.ai_layout_generator_loading_subtitle));
                pro.sketchware.ia.GeradorDeLayout gerador = new pro.sketchware.ia.GeradorDeLayout(
                        prompt, currentLayoutXml, history, referenceContext,
                        availableDrawables, referenceImageDataUrls);
                final String cleanXml = gerador.gerarLayout();
                ParsedGeneratedLayout generatedLayout = prepareGeneratedLayout(cleanXml);

                updateAiLayoutLoadingDialog(getString(R.string.ai_layout_generator_loading_applying));

                runOnUiThread(() -> {
                    try {
                        var rootLayoutManager = new InjectRootLayoutManager(sc_id);
                        rootLayoutManager.set(xmlName, InjectRootLayoutManager.toRoot(generatedLayout.rootAttributes));

                        var bean = new HistoryViewBean();
                        bean.actionOverride(generatedLayout.parsedLayout, ProjectDataManager.getProjectDataManager(sc_id).getViews(xmlName));
                        
                        if (viewTabAdapter != null) {
                            viewTabAdapter.refreshAllViews();
                            refreshViewTabAdapter();
                        }

                        SketchwareUtil.toast(getString(R.string.ai_layout_generator_success, xmlName));
                        historyManager.saveHistoryEntry(sc_id, xmlName, prompt, cleanXml);
                    } catch (Exception parseExp) {
                        showAiLayoutErrorDialog(getString(R.string.ai_layout_generator_error_apply, getAiLayoutErrorDetail(parseExp)));
                    } finally {
                        dismissAiLayoutLoadingDialog();
                    }
                });
            } catch (Exception e) {
                dismissAiLayoutLoadingDialog();
                showAiLayoutErrorDialog(getString(R.string.ai_layout_generator_error_generate, getAiLayoutErrorDetail(e)));
            }
        }).start();
    }

    private void showAiLayoutErrorDialog(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            String safeMessage = message == null || message.trim().isEmpty() ? getString(R.string.common_error_an_error_occurred) : message;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.ai_layout_generator_error_dialog_title)
                    .setMessage(safeMessage)
                    .setPositiveButton(R.string.common_word_copy, (dialog, which) -> {
                        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboardManager != null) {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.ai_layout_generator_error_dialog_title), safeMessage));
                        }
                    })
                    .setNegativeButton(R.string.common_word_close, null)
                    .show();
        });
    }

    private String getAiLayoutErrorDetail(Throwable throwable) {
        if (throwable == null) return getString(R.string.common_error_an_error_occurred);
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) return getString(R.string.common_error_an_error_occurred);
        return message.trim();
    }

    private String getReferenceImageDisplayName(Uri uri) {
        if (uri == null) return "";
        String path = uri.getLastPathSegment();
        if (path == null || path.trim().isEmpty()) return uri.toString();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void updateAiLayoutReferenceImagePreview() {
        if (aiLayoutReferenceImageLabel != null) {
            if (aiLayoutReferenceImageUris.isEmpty()) {
                aiLayoutReferenceImageLabel.setText(R.string.ai_layout_generator_reference_image_none);
            } else if (aiLayoutReferenceImageUris.size() == 1) {
                aiLayoutReferenceImageLabel.setText(getString(R.string.ai_layout_generator_reference_image_selected, getReferenceImageDisplayName(aiLayoutReferenceImageUris.get(0))));
            } else {
                aiLayoutReferenceImageLabel.setText(getString(R.string.ai_layout_generator_reference_images_selected, aiLayoutReferenceImageUris.size()));
            }
        }

        if (aiLayoutReferenceImagePreviewList == null) return;

        aiLayoutReferenceImagePreviewList.removeAllViews();
        aiLayoutReferenceImagePreviewList.setVisibility(aiLayoutReferenceImageUris.isEmpty() ? View.GONE : View.VISIBLE);
        for (Uri uri : aiLayoutReferenceImageUris) {
            CircleImageView imageView = new CircleImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(50), dp(50));
            params.setMarginEnd(dp(8));
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setContentDescription(getReferenceImageDisplayName(uri));
            imageView.setImageURI(uri);
            aiLayoutReferenceImagePreviewList.addView(imageView);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String buildReferenceContext(String referenceNotes, List<Uri> referenceImageUris) {
        StringBuilder context = new StringBuilder();
        if (referenceImageUris != null && !referenceImageUris.isEmpty()) {
            context.append("Selected reference image URIs:\n");
            for (int i = 0; i < referenceImageUris.size(); i++) {
                Uri uri = referenceImageUris.get(i);
                context.append(i + 1).append(". ").append(uri).append("\n");
            }
        }
        if (referenceNotes != null && !referenceNotes.trim().isEmpty()) {
            if (context.length() > 0) context.append('\n');
            context.append("User reference notes: ").append(referenceNotes.trim());
        }
        return context.toString();
    }

    private List<String> buildReferenceImageDataUrls(List<Uri> uris) {
        List<String> images = new ArrayList<>();
        if (uris == null) return images;
        for (Uri uri : uris) {
            if (images.size() >= 3 || uri == null) break;
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) continue;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                String mime = getContentResolver().getType(uri);
                if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";
                images.add("data:" + mime + ";base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP));
            } catch (Exception e) {
                Log.e("DesignActivity", "Failed to prepare AI reference image", e);
            }
        }
        return images;
    }

    // ==========================================
    // الخدمات المضافة: 2. تنظيف وهيكلة التخطيط
    // ==========================================
    private List<String> getAvailableProjectDrawables() {
        Set<String> drawables = new HashSet<>();
        try {
            var resources = ProjectDataManager.getResourceManager(sc_id);
            if (resources != null) {
                // استخراج الموارد المتاحة
            }
        } catch (Exception e) {
            Log.e("DesignActivity", "Failed to read project drawable resources", e);
        }
        return new ArrayList<>(drawables);
    }

    private android.util.Pair<String, java.util.Map<String, String>> sanitizeGeneratedRoot(android.util.Pair<String, java.util.Map<String, String>> rootAttributes) {
        if (rootAttributes == null || rootAttributes.first == null || rootAttributes.first.trim().isEmpty()) {
            InjectRootLayoutManager.Root defaultRoot = InjectRootLayoutManager.getDefaultRootLayout();
            return android.util.Pair.create("LinearLayout", new LinkedHashMap<>(defaultRoot.getAttributes()));
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        Map<String, String> attrs = rootAttributes.second != null ? rootAttributes.second : new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            sanitized.put(key.trim(), entry.getValue());
        }
        sanitized.put("android:layout_width", "match_parent");
        sanitized.put("android:layout_height", "match_parent");
        return android.util.Pair.create(rootAttributes.first, sanitized);
    }

    private static class ParsedGeneratedLayout {
        ArrayList<ViewBean> parsedLayout;
        android.util.Pair<String, java.util.Map<String, String>> rootAttributes;

        ParsedGeneratedLayout(ArrayList<ViewBean> parsedLayout, android.util.Pair<String, java.util.Map<String, String>> rootAttributes) {
            this.parsedLayout = parsedLayout;
            this.rootAttributes = rootAttributes;
        }
    }

    private ParsedGeneratedLayout prepareGeneratedLayout(String xml) throws Exception {
        ViewBeanParser parser = new ViewBeanParser(xml);
        parser.setSkipRoot(true);
        ArrayList<ViewBean> parsedLayout = parser.parse();
        if (parsedLayout == null || parsedLayout.isEmpty()) {
            throw new java.io.IOException("The AI response did not contain editable views.");
        }
        android.util.Pair<String, java.util.Map<String, String>> rootAttributes = sanitizeGeneratedRoot(parser.getRootAttributes());
        return new ParsedGeneratedLayout(parsedLayout, rootAttributes);
    }

    // ==========================================
    // الخدمات المضافة: 4. نظام البحث المتطور والشامل
    // ==========================================
    private void handleSearchIconClick() {
        if (currentTabNumber == 1) {
            showEventSearchPopup();
        } else {
            openGlobalSearch();
        }
    }
    
    private void openGlobalSearch() {
        GlobalSearchDialog dialog = new GlobalSearchDialog(sc_id, this);
        dialog.show(getSupportFragmentManager(), "GlobalSearch");
    }
    
    private void showEventSearchPopup() {
        View anchor = toolbar.findViewById(R.id.design_option_menu_search);
        if (anchor == null) anchor = toolbar;

        PopupMenu searchPopupMenu = new PopupMenu(this, anchor);
        searchPopupMenu.getMenu().add(Menu.NONE, 1, Menu.NONE, Helper.getResString(R.string.global_search_title));
        searchPopupMenu.getMenu().add(Menu.NONE, 2, Menu.NONE, Helper.getResString(R.string.logic_search_events));
        searchPopupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openGlobalSearch();
            } else if (item.getItemId() == 2) {
                if (eventTabAdapter != null) eventTabAdapter.toggleSearchBar();
            }
            return true;
        });
        searchPopupMenu.show();
    }
    
    public void handleSearchResult(mod.sketchlibx.search.SearchResult result) {
        ProjectFileBean fileBean = ProjectDataManager.getFileManager(sc_id).getFileByXmlName(result.fileName);
        if (fileBean == null) {
            fileBean = ProjectDataManager.getFileManager(sc_id).getActivityByJavaName(result.fileName);
        }
        
        if (fileBean != null) {
            showLoadingDialog();
            editorViewModel.selectProjectFile(fileBean);
            refreshFileSelector();
            viewPager.setCurrentItem(result.tabIndex, false);
            refresh();
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (result.category.equals("View") && result.targetId != null) {
                        if (viewTabAdapter != null) {
                            viewTabAdapter.showHidePropertyView(true);
                        }
                    }
                } catch (Exception e) {
                    Log.e("DeepLink", "Failed to resolve deep link", e);
                } finally {
                    dismissLoadingDialog();
                }
            }, 500);
        } else {
            SketchwareUtil.toastError("Could not find file: " + result.fileName);
        }
    }
    
    

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        showLoadingDialog();

        HashMap<String, Object> projectInfo = ProjectListManager.getProjectById(sc_id);
        getSupportActionBar().setTitle(MapValueHelper.getString(projectInfo, "my_ws_name"));
        projectFilePaths = new ProjectFilePaths(getApplicationContext(), SketchwarePaths.getMyscPath(sc_id), projectInfo);

        try {
            projectController.load(savedInstanceState);
        } catch (RuntimeException e) {
            if (crashlytics != null) {
                crashlytics.log("ProjectLoader failed");
                crashlytics.recordException(e);
            }
        } finally {
            SystemLogPrinter.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isStoragePermissionGranted()) {
            finish();
        }

        long freeMegabytes = DeviceUtil.getFreeStorageMB();
        if (freeMegabytes < 100L && freeMegabytes > 0L) {
            warnAboutInsufficientStorageSpace();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
        if (!isStoragePermissionGranted()) {
            finish();
        }

        if (!isRestoringData) {
            projectController.saveUnsavedChanges();
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.file_name_container) {
            if (viewPager.getCurrentItem() == 0) {
                showAvailableViews();
            } else {
                showAvailableJavaFiles();
            }
        }
    }

    /**
     * Show a dialog asking about saving the project before quitting.
     */
    private void showSaveBeforeQuittingDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.design_quit_title_exit_projet));
        dialog.setIcon(R.drawable.ic_mtrl_exit);
        dialog.setMessage(Helper.getResString(R.string.design_quit_message_confirm_save));
        dialog.setPositiveButton(Helper.getResString(R.string.design_quit_button_save_and_exit), (v, which) -> {
            if (!UIHelper.isClickThrottled()) {
                v.dismiss();
                try {
                    saveChangesAndCloseProject();
                } catch (RuntimeException e) {
                    if (crashlytics != null) crashlytics.recordException(e);
                    dismissLoadingDialog();
                }
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
            if (!UIHelper.isClickThrottled()) {
                v.dismiss();
                try {
                    showLoadingDialog();
                    projectController.discardAndClose();
                } catch (RuntimeException e) {
                    if (crashlytics != null) crashlytics.recordException(e);
                    dismissLoadingDialog();
                }
            }
        });
        dialog.setNeutralButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    /**
     * Show a dialog warning the user about low free space.
     */
    private void warnAboutInsufficientStorageSpace() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.common_word_warning));
        dialog.setIcon(R.drawable.break_warning_96_red);
        dialog.setMessage(Helper.getResString(R.string.common_message_insufficient_storage_space));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), null);
        dialog.show();
    }

    private void askIfToRestoreOldUnsavedProjectData() {
        isRestoringData = true;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_mtrl_history);
        dialog.setTitle(Helper.getResString(R.string.design_restore_data_title));
        dialog.setMessage(Helper.getResString(R.string.design_restore_data_message_confirm));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_restore), (v, which) -> {
            if (!UIHelper.isClickThrottled()) {
                boolean hasLibraryBackup = ProjectDataManager.getLibraryManager(sc_id).hasBackup();
                boolean hasFileBackup = ProjectDataManager.getFileManager(sc_id).hasBackup();
                boolean hasResourceBackup = ProjectDataManager.getResourceManager(sc_id).hasBackup();
                boolean hasViewBackup = ProjectDataManager.getProjectDataManager(sc_id).hasViewBackup();
                boolean hasLogicBackup = ProjectDataManager.getProjectDataManager(sc_id).hasLogicBackup();
                if (hasLibraryBackup) {
                    ProjectDataManager.getLibraryManager(sc_id).loadFromBackup();
                }
                if (hasFileBackup) {
                    ProjectDataManager.getFileManager(sc_id).loadFromBackup();
                }
                if (hasResourceBackup) {
                    ProjectDataManager.getResourceManager(sc_id).loadFromBackup();
                }
                if (hasViewBackup) {
                    ProjectDataManager.getProjectDataManager(sc_id).loadViewFromBackup();
                }
                if (hasLogicBackup) {
                    ProjectDataManager.getProjectDataManager(sc_id).loadLogicFromBackup();
                }
                if (hasLibraryBackup) {
                    ProjectDataManager.getFileManager(sc_id).syncWithLibrary(ProjectDataManager.getLibraryManager(sc_id));
                    ProjectDataManager.getProjectDataManager(sc_id).removeAdmobComponents(ProjectDataManager.getLibraryManager(sc_id).getFirebaseDB());
                    ProjectDataManager.getProjectDataManager(sc_id).removeFirebaseViews(ProjectDataManager.getLibraryManager(sc_id).getAdmob(), ProjectDataManager.getFileManager(sc_id));
                    ProjectDataManager.getProjectDataManager(sc_id).removeMapViews(ProjectDataManager.getLibraryManager(sc_id).getGoogleMap(), ProjectDataManager.getFileManager(sc_id));
                }
                if (hasFileBackup || hasLibraryBackup) {
                    ProjectDataManager.getProjectDataManager(sc_id).syncWithFileManager(ProjectDataManager.getFileManager(sc_id));
                }
                if (hasResourceBackup) {
                    ProjectDataManager.getProjectDataManager(sc_id).syncSounds(ProjectDataManager.getResourceManager(sc_id));
                    ProjectDataManager.getProjectDataManager(sc_id).syncFonts(ProjectDataManager.getResourceManager(sc_id));
                }
                refresh();
                isRestoringData = false;
                v.dismiss();
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_no), (v, which) -> {
            isRestoringData = false;
            v.dismiss();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showCurrentActivitySrcCode() {
        if (editorViewModel.getActiveProjectFile() == null) return;
        showLoadingDialog();
        String filename = Helper.getText(fileName);
        BackgroundTasks.callIoIfAlive(TaskHost.of(this), "DesignActivity", () ->
                new ProjectFilePaths(getApplicationContext(), sc_id).getFileSrc(
                        filename,
                        ProjectDataManager.getFileManager(sc_id),
                        ProjectDataManager.getProjectDataManager(sc_id),
                        ProjectDataManager.getLibraryManager(sc_id)), code -> {
            dismissLoadingDialog();
            if (code.isEmpty()) {
                SketchwareUtil.toast(Helper.getResString(R.string.design_error_generate_source));
                return;
            }
            var scheme = filename.endsWith(".xml") ? CodeViewerActivity.SCHEME_XML : CodeViewerActivity.SCHEME_JAVA;
            launchActivity(CodeViewerActivity.class, null, new Pair<>("code", code), new Pair<>("sc_id", sc_id), new Pair<>("scheme", scheme));
        }, error -> {
            Log.e("DesignActivity", "Failed to generate source code", error);
            dismissLoadingDialog();
            SketchwareUtil.toast(Helper.getResString(R.string.design_error_generate_source));
        });
    }

    private void showAvailableJavaFiles() {
        var dialog = new MaterialAlertDialogBuilder(this).create();
        dialog.setTitle(R.string.design_file_selector_title_java);
        dialog.setIcon(R.drawable.ic_mtrl_java);
        View customView = pro.sketchware.util.ViewUtil.inflateLayout(this, R.layout.file_selector_popup_select_java);
        RecyclerView recyclerView = customView.findViewById(R.id.file_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext(), RecyclerView.VERTICAL, false));
        var adapter = new JavaFileAdapter(sc_id);
        adapter.setOnItemClickListener(projectFileBean -> {
            editorViewModel.selectProjectFile(projectFileBean);
            refreshFileSelector();
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);
        dialog.setView(customView);
        dialog.show();
    }

    private void showAvailableViews() {
        ProjectFileBean projectFile = editorViewModel.getActiveProjectFile();
        if (projectFile == null) return;
        Intent intent = new Intent(getApplicationContext(), ViewSelectorActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("current_xml", projectFile.getXmlName());
        intent.putExtra("is_custom_view", projectFile.fileType == 1 || projectFile.fileType == 2);
        changeOpenFile.launch(intent);
    }

    /**
     * Opens {@link ViewCodeEditorActivity}.
     */
    void toViewCodeEditor() {
        ProjectFileBean projectFile = editorViewModel.getActiveProjectFile();
        if (projectFile == null) return;
        showLoadingDialog();
        String filename = Helper.getText(fileName);
        ProjectFileBean currentProjectFile = projectFile;
        BackgroundTasks.callIoIfAlive(TaskHost.of(this), "DesignActivity", () -> {
            var xmlGenerator = new LayoutGenerator(projectFilePaths.buildConfig, currentProjectFile);
            var projectDataManager = ProjectDataManager.getProjectDataManager(sc_id);
            var viewBeans = projectDataManager.getViews(filename);
            var viewFab = projectDataManager.getFabView(filename);
            xmlGenerator.setExcludeAppCompat(true);
            xmlGenerator.setViews(ProjectDataStore.getSortedRootViews(viewBeans), viewFab);
            return xmlGenerator.toXmlString();
        }, content -> {
            dismissLoadingDialog();
            launchActivity(ViewCodeEditorActivity.class, openViewCodeEditor, new Pair<>("title", filename), new Pair<>("content", content));
        }, error -> {
            Log.e("DesignActivity", "Failed to generate view code", error);
            dismissLoadingDialog();
            SketchwareUtil.toast(Helper.getResString(R.string.design_error_generate_code));
        });
    }

    /**
     * Opens {@link LogReaderActivity}.
     */
    void toLogReader() {
        Intent intent = new Intent(getApplicationContext(), LogReaderActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }

    /**
     * Opens {@link ManageCollectionActivity}.
     */
    void toCollectionManager() {
        launchActivity(ManageCollectionActivity.class, openCollectionManager);
    }

    /**
     * Opens {@link AndroidManifestInjection}.
     */
    void toAndroidManifestManager() {
        if (editorViewModel.getActiveProjectFile() == null) return;
        launchActivity(AndroidManifestInjection.class, null, new Pair<>("file_name", currentJavaFileName));
    }

    /**
     * Opens {@link ManageAppCompatActivity}.
     */
    void toAppCompatInjectionManager() {
        ProjectFileBean projectFile = editorViewModel.getActiveProjectFile();
        if (projectFile == null) return;
        launchActivity(ManageAppCompatActivity.class, null, new Pair<>("file_name", projectFile.getXmlName()));
    }

    /**
     * Opens {@link ManageAssetsActivity}.
     */
    void toAssetManager() {
        launchActivity(ManageAssetsActivity.class, null);
    }

    /**
     * Shows a {@link CustomBlocksDialog}.
     */
    void toCustomBlocksViewer() {
        new CustomBlocksDialog().show(this, sc_id);
    }

    /**
     * Opens {@link ManageJavaActivity}.
     */
    void toJavaManager() {
        launchActivity(ManageJavaActivity.class, null, new Pair<>("pkgName", projectFilePaths.packageName));
    }

    /**
     * Opens {@link ManagePermissionActivity}.
     */
    void toPermissionManager() {
        launchActivity(ManagePermissionActivity.class, null);
    }

    /**
     * Opens {@link ManageProguardActivity}.
     */
    void toProguardManager() {
        launchActivity(ManageProguardActivity.class, null);
    }

    /**
     * Opens {@link ManageResourceActivity}.
     */
    void toResourceManager() {
        launchActivity(ManageResourceActivity.class, openResourcesManager);
    }

    /**
     * Opens {@link ResourcesEditorActivity}.
     */
    void toResourceEditor() {
        launchActivity(ResourcesEditorActivity.class, openResourcesManager);
    }

    /**
     * Opens {@link ManageStringFogFragment}.
     */
    void toStringFogManager() {
        var fragmentManager = getSupportFragmentManager();
        if (fragmentManager.findFragmentByTag("stringFogFragment") == null) {
            var bottomSheet = new ManageStringFogFragment();
            bottomSheet.show(fragmentManager, "stringFogFragment");
        }
    }

    /**
     * Opens {@link ManageFontActivity}.
     */
    void toFontManager() {
        launchActivity(ManageFontActivity.class, null);
    }

    /**
     * Opens {@link ManageImageActivity}.
     */
    void toImageManager() {
        launchActivity(ManageImageActivity.class, openImageManager);
    }

    /**
     * Opens {@link ManageLibraryActivity}.
     */
    void toLibraryManager() {
        launchActivity(ManageLibraryActivity.class, openLibraryManager);
    }

    /**
     * Opens {@link ManageViewActivity}.
     */
    void toViewManager() {
        launchActivity(ManageViewActivity.class, openViewManager);
    }

    /**
     * Opens {@link ManageSoundActivity}.
     */
    void toSoundManager() {
        launchActivity(ManageSoundActivity.class, null);
    }

    /**
     * Opens {@link SrcViewerActivity}.
     */
    void toSourceCodeViewer() {
        launchActivity(SrcViewerActivity.class, null, new Pair<>("current", Helper.getText(fileName)));
    }

    /**
     * Opens {@link ManageXMLCommandActivity}.
     */
    void toXMLCommandManager() {
        launchActivity(ManageXMLCommandActivity.class, null);
    }

    @SafeVarargs
    private void launchActivity(Class<? extends Activity> toLaunch, ActivityResultLauncher<Intent> optionalLauncher, Pair<String, String>... extras) {
        Intent intent = new Intent(getApplicationContext(), toLaunch);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        for (Pair<String, String> extra : extras) {
            intent.putExtra(extra.first, extra.second);
        }

        if (optionalLauncher == null) {
            startActivity(intent);
        } else {
            optionalLauncher.launch(intent);
        }
    }

    private class ViewPagerAdapter extends FragmentPagerAdapter {
        private final String[] labels;

        public ViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
            labels = new String[]{
                    Helper.getResString(R.string.design_tab_title_view),
                    Helper.getResString(R.string.design_tab_title_event),
                    Helper.getResString(R.string.design_tab_title_component),
                    Helper.getResString(R.string.design_tab_title_strings)};
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return labels[position];
        }

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (position == 0) {
                viewTabAdapter = (ViewEditorFragment) fragment;
            } else if (position == 1) {
                eventTabAdapter = (EventListFragment) fragment;
            } else if (position == 2) {
                componentTabAdapter = (ComponentListFragment) fragment;
            } else if (position == 3) {
                stringsTabAdapter = (StringsTabFragment) fragment;
            }

            return fragment;
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            if (position == 0) {
                return ViewEditorFragment.newInstance(sc_id);
            } else if (position == 1) {
                return EventListFragment.newInstance(sc_id);
            } else if (position == 2) {
                return ComponentListFragment.newInstance(sc_id);
            } else {
                return new StringsTabFragment();
            }
        }
    }
    
    public String getScId() {
      return sc_id;
    }

    private static FirebaseCrashlytics getFirebaseCrashlytics() {
        try {
            return FirebaseCrashlytics.getInstance();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
