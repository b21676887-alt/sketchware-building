package pro.sketchware.activities.editor.manage.lottie;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import pro.sketchware.beans.ProjectResourceBean;
import pro.sketchware.activities.base.BaseFragment;
import pro.sketchware.activities.editor.manage.lottie.ManageLottieActivity;
import pro.sketchware.activities.editor.manage.lottie.AddLottieActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import pro.sketchware.R;
import pro.sketchware.activities.importicon.ImportIconActivity;
import pro.sketchware.databinding.FrManageLottieListBinding;
import pro.sketchware.databinding.ManageLottieListItemBinding;
import pro.sketchware.core.project.ProjectDataManager;
import pro.sketchware.util.SketchToast;
import pro.sketchware.util.io.EncryptedFileUtil;
import pro.sketchware.util.FilePathUtil;
import pro.sketchware.util.SvgUtils;
import com.airbnb.lottie.LottieDrawable;

public class LottieProjectFragment extends BaseFragment {

    private final FilePathUtil fpu = new FilePathUtil();
    public boolean isSelecting = false;
    public SvgUtils svgUtils;
    Map<Integer, Map<String, Object>> colorMap = new HashMap<>();
    private FrManageLottieListBinding binding;
    private String sc_id;
    private ArrayList<ProjectResourceBean> lotties;
    private MaterialCardView actionButtonContainer;
    private FloatingActionButton fab;
    private String projectLottiesDirectory = "";
    private Adapter adapter = null;

    private final ActivityResultLauncher<Intent> openImportIconActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            var data = result.getData();
            if (data == null) return;
            ProjectResourceBean icon = new ProjectResourceBean(
                    ProjectResourceBean.PROJECT_RES_TYPE_FILE,
                    data.getStringExtra("iconName"), data.getStringExtra("iconPath")
            );
            icon.savedPos = 2;
            icon.isNew = true;
            addImage(icon);
            SketchToast.toast(requireActivity(), getString(R.string.design_manager_message_add_complete), SketchToast.TOAST_NORMAL).show();
        }
    });

    private final ActivityResultLauncher<Intent> showAddImageDialog = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            var data = result.getData();
            if (data == null) return;
            ArrayList<ProjectResourceBean> added;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                added = data.getParcelableArrayListExtra("lotties", ProjectResourceBean.class);
            } else {
                added = data.getParcelableArrayListExtra("lotties");
            }
            if (added != null && !added.isEmpty()) {
                lotties.addAll(added);
                adapter.notifyItemRangeInserted(lotties.size() - added.size(), added.size());
                updateGuideVisibility();
                ((ManageLottieActivity) requireActivity()).l().refreshData();
                SketchToast.toast(requireActivity(), getString(R.string.design_manager_message_add_complete), SketchToast.TOAST_NORMAL).show();
            }
        }
    });

    private final ActivityResultLauncher<Intent> showImageDetailsDialog = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            var data = result.getData();
            if (data == null) return;
            ProjectResourceBean edited;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                edited = data.getParcelableExtra("lottie", ProjectResourceBean.class);
            } else {
                edited = data.getParcelableExtra("lottie");
            }
            if (edited == null || edited.resName == null) return;
            for (ProjectResourceBean p : lotties) {
                if (p != null && p.resName != null && p.resName.equals(edited.resName)) {
                    p.copy(edited);
                    adapter.notifyItemChanged(lotties.indexOf(p));
                    break;
                }
            }
            updateGuideVisibility();
            ((ManageLottieActivity) requireActivity()).l().refreshData();
            SketchToast.toast(requireActivity(), getString(R.string.design_manager_message_edit_complete), SketchToast.TOAST_NORMAL).show();
        }
    });

    public static void copyFile(String srcPath, String destPath) throws IOException {
        File srcFile = new File(srcPath);
        File destFile = new File(destPath);
        try (FileInputStream fis = new FileInputStream(srcFile);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) fos.write(buffer, 0, length);
        }
    }

    public ArrayList<ProjectResourceBean> getLotties() {
        return lotties;
    }

    private void initialize() {
        sc_id = requireActivity().getIntent().getStringExtra("sc_id");
        projectLottiesDirectory = fpu.getPathAssets(sc_id);
        new EncryptedFileUtil().mkdirs(projectLottiesDirectory);
        var list = ProjectDataManager.getResourceManager(sc_id).getLottieList();
        if (list != null) {
            for (ProjectResourceBean p : list) {
                p.savedPos = 0;
                lotties.add(p);
            }
        }
    }

    private void unselectAll() {
        for (ProjectResourceBean p : lotties) p.isSelected = false;
    }

    private void deleteSelected() {
        for (int i = lotties.size() - 1; i >= 0; i--) {
            if (lotties.get(i).isSelected) lotties.remove(i);
        }
        adapter.notifyDataSetChanged();
    }

    public void saveImages() {
        new EncryptedFileUtil().mkdirs(projectLottiesDirectory);
        for (int i = 0; i < lotties.size(); i++) {
            ProjectResourceBean obj = lotties.get(i);
            if (obj.isNew || obj.isEdited) {
                try {
                    String sourcePath = obj.savedPos == 0 ? projectLottiesDirectory + File.separator + obj.resFullName : obj.resFullName;
                    String destPath = projectLottiesDirectory + File.separator + obj.resName + ".json";
                    copyFile(sourcePath, destPath);
                    lotties.set(i, new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, obj.resName, obj.resName + ".json"));
                } catch (Exception e) {
                    Log.e("LottieProjectFragment", e.getMessage(), e);
                }
            }
        }
        ProjectDataManager.getResourceManager(sc_id).setLotties(lotties);
    }

    private void updateGuideVisibility() {
        if (lotties.isEmpty()) {
            binding.tvGuide.setVisibility(View.VISIBLE);
            binding.imageList.setVisibility(View.GONE);
        } else {
            binding.imageList.setVisibility(View.VISIBLE);
            binding.tvGuide.setVisibility(View.GONE);
        }
    }

    private void showAddLottieDialog() {
        Intent intent = new Intent(requireContext(), AddLottieActivity.class);
        intent.putParcelableArrayListExtra("lotties", lotties);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("dir_path", projectLottiesDirectory);
        showAddImageDialog.launch(intent);
    }

    private void openImportIconActivity() {
        Intent intent = new Intent(requireActivity(), ImportIconActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        intent.putStringArrayListExtra("imageNames", getAllLottieNames());
        openImportIconActivity.launch(intent);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) initialize();
        else {
            sc_id = savedInstanceState.getString("sc_id");
            projectLottiesDirectory = savedInstanceState.getString("dir_path");
            lotties = savedInstanceState.getParcelableArrayList("lotties");
        }
        if (lotties == null) lotties = new ArrayList<>();
        new EncryptedFileUtil().mkdirs(projectLottiesDirectory);
        adapter.notifyDataSetChanged();
        updateGuideVisibility();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (binding.imageList.getLayoutManager() instanceof GridLayoutManager manager) {
            manager.setSpanCount(ManageLottieActivity.getLottieGridColumnCount(requireContext()));
        }
        binding.imageList.requestLayout();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.manage_lottie_menu, menu);
        menu.findItem(R.id.menu_lottie_delete).setVisible(!isSelecting);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FrManageLottieListBinding.inflate(inflater, container, false);
        setHasOptionsMenu(true);
        lotties = new ArrayList<>();
        binding.imageList.setHasFixedSize(true);
        binding.imageList.setLayoutManager(new GridLayoutManager(requireActivity(), ManageLottieActivity.getLottieGridColumnCount(requireContext())));
        adapter = new Adapter(binding.imageList);
        binding.imageList.setAdapter(adapter);
        binding.tvGuide.setText(R.string.design_manager_lottie_description_guide_add_animation);
        actionButtonContainer = requireActivity().findViewById(R.id.layout_btn_group);
        MaterialButton delete = requireActivity().findViewById(R.id.btn_delete);
        MaterialButton cancel = requireActivity().findViewById(R.id.btn_cancel);
        delete.setOnClickListener(view -> {
            if (isSelecting) {
                deleteSelected();
                setSelectionMode(false);
                updateGuideVisibility();
                SketchToast.toast(requireActivity(), getString(R.string.common_message_complete_delete), SketchToast.TOAST_WARNING).show();
                fab.show();
            }
        });
        cancel.setOnClickListener(view -> {
            if (isSelecting) setSelectionMode(false);
        });
        fab = requireActivity().findViewById(R.id.fab);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(view -> {
            setSelectionMode(false);
            showAddLottieDialog();
        });
        return binding.getRoot();
    }

    public void setSelectionMode(boolean selection) {
        this.isSelecting = selection;
        requireActivity().invalidateOptionsMenu();
        unselectAll();
        actionButtonContainer.setVisibility(this.isSelecting ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void showLottieDetailsDialog(ProjectResourceBean bean) {
        Intent intent = new Intent(requireContext(), AddLottieActivity.class);
        intent.putParcelableArrayListExtra("lotties", lotties);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("dir_path", projectLottiesDirectory);
        intent.putExtra("edit_target", bean);
        showImageDetailsDialog.launch(intent);
    }

    private ArrayList<String> getAllLottieNames() {
        ArrayList<String> names = new ArrayList<>();
        names.add("app_icon");
        for (ProjectResourceBean p : lotties) names.add(p.resName);
        return names;
    }

    public void addImported(ArrayList<ProjectResourceBean> list) {
        ArrayList<ProjectResourceBean> imagesToAdd = new ArrayList<>();
        ArrayList<String> duplicateNames = new ArrayList<>();
        for (ProjectResourceBean next : list) {
            String imageName = next.resName;
            if (isImageNameDuplicate(imageName)) duplicateNames.add(imageName);
            else {
                ProjectResourceBean image = new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, imageName, next.resFullName);
                image.savedPos = 1;
                image.isNew = true;
                imagesToAdd.add(image);
            }
        }
        lotties.addAll(imagesToAdd);
        if (!duplicateNames.isEmpty()) {
            SketchToast.toast(requireActivity(), getString(R.string.common_message_name_unavailable) + "\n" +
                    "[" + String.join(", ", duplicateNames) + "]", SketchToast.TOAST_WARNING).show();
        } else {
            SketchToast.toast(requireActivity(), getString(R.string.design_manager_message_import_complete), SketchToast.TOAST_NORMAL).show();
        }
        adapter.notifyDataSetChanged();
        updateGuideVisibility();
    }

    private boolean isImageNameDuplicate(String imageName) {
        for (ProjectResourceBean p : lotties) {
            if (p.resName.equals(imageName)) return true;
        }
        return false;
    }

    private String getLottiePath(ProjectResourceBean bean) {
        return projectLottiesDirectory + File.separator + bean.resFullName;
    }

    // Added helper to fix compilation: add a single ProjectResourceBean into the list
    private void addImage(ProjectResourceBean icon) {
        if (icon == null) return;
        if (lotties == null) lotties = new ArrayList<>();
        lotties.add(icon);
        if (adapter != null) {
            adapter.notifyItemInserted(lotties.size() - 1);
        }
        updateGuideVisibility();
        try {
            ((ManageLottieActivity) requireActivity()).l().refreshData();
        } catch (Exception ignored) {
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {
        public Adapter(RecyclerView recyclerView) {
            if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        if (dy > 2) {
                            if (fab.isEnabled()) fab.hide();
                        } else if (dy < -2) {
                            if (fab.isEnabled()) fab.show();
                        }
                    }
                });
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectResourceBean image = lotties.get(position);
            holder.binding.deleteLottieContainer.setVisibility(isSelecting ? View.VISIBLE : View.GONE);
            holder.binding.lottieAnimationIndicator.setVisibility(View.VISIBLE);
            holder.binding.lottieDelete.setImageResource(image.isSelected ? R.drawable.ic_checkmark_green_48dp : R.drawable.ic_trashcan_white_48dp);
            holder.binding.chkSelect.setChecked(image.isSelected);
            holder.binding.tvLottieName.setText(image.resName);

            if (colorMap.get(position) != null) {
                int color = (int) colorMap.get(position).get("color");
                holder.binding.lottie.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            } else {
                holder.binding.lottie.clearColorFilter();
            }

            if (image.resFullName.endsWith(".json") || image.resFullName.endsWith(".lottie")) {
                String path = image.savedPos == 0 ? getLottiePath(image) : image.resFullName;
                try {
                    String json = readFileContents(path);
                    if (json == null || json.trim().isEmpty()) throw new Exception("Invalid JSON");
                    holder.binding.lottie.setAnimationFromJson(json, path);
                    holder.binding.lottie.setProgress(0f);
                } catch (Exception e) {
                    Log.e("LottieProjectFragment", "Error loading lottie: " + e.getMessage(), e);
                    holder.binding.lottie.cancelAnimation();
                    holder.binding.lottie.setImageResource(R.drawable.ic_remove_grey600_24dp);
                }
            }
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ManageLottieListItemBinding b = ManageLottieListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public int getItemCount() {
            return lotties.size();
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            holder.binding.lottie.cancelAnimation();
            holder.binding.lottie.setImageDrawable(null);
            holder.binding.lottie.setProgress(0f);
            super.onViewRecycled(holder);
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ManageLottieListItemBinding binding;
            public ViewHolder(@NonNull ManageLottieListItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                binding.lottie.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    ProjectResourceBean bean = lotties.get(pos);
                    if (!isSelecting) {
                        showLottieDetailsDialog(bean);
                    } else {
                        binding.chkSelect.setChecked(!binding.chkSelect.isChecked());
                        bean.isSelected = binding.chkSelect.isChecked();
                        notifyItemChanged(pos);
                    }
                });
                binding.lottie.setOnLongClickListener(v -> {
                    setSelectionMode(true);
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return true;
                    binding.chkSelect.setChecked(!binding.chkSelect.isChecked());
                    lotties.get(pos).isSelected = binding.chkSelect.isChecked();
                    return true;
                });
            }
        }
    }

    private String readFileContents(String path) throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(path));
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) bos.write(buffer, 0, read);
            return bos.toString("UTF-8");
        }
    }
}
