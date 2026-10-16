package pro.sketchware.activities.editor.manage.lottie;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import pro.sketchware.beans.ProjectResourceBean;
import pro.sketchware.activities.base.BaseFragment;
import pro.sketchware.activities.editor.manage.lottie.ManageLottieActivity;
import pro.sketchware.activities.editor.manage.lottie.ManageLottieImportActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import pro.sketchware.R;
import pro.sketchware.databinding.FrManageLottieListBinding;
import pro.sketchware.databinding.ManageLottieListItemBinding;
import pro.sketchware.util.SketchToast;
import pro.sketchware.core.project.SketchwarePaths;
import pro.sketchware.util.io.EncryptedFileUtil;
import pro.sketchware.core.project.ImageCollectionManager;

public class LottieCollectionFragment extends BaseFragment implements View.OnClickListener {

    private final ActivityResultLauncher<Intent> openImageImportDetails = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        var data = result.getData();
        if (result.getResultCode() == Activity.RESULT_OK && data != null && isAdded()) {
            ArrayList<ProjectResourceBean> imported;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) imported = data.getParcelableArrayListExtra("results", ProjectResourceBean.class);
            else imported = data.getParcelableArrayListExtra("results");
            if (imported == null) return;
            ArrayList<ProjectResourceBean> newImported = new ArrayList<>();
            for (ProjectResourceBean p : imported) newImported.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, p.resName, p.resFullName));
            if (!newImported.isEmpty()) {
                ((ManageLottieActivity) requireActivity()).m().addImported(newImported);
                ((ManageLottieActivity) requireActivity()).f(0);
            }
        }
    });

    private FrManageLottieListBinding binding;
    private ArrayList<ProjectResourceBean> collectionLotties;
    private Adapter adapter;
    private String sc_id;

    private Button btnImport;
    private MaterialCardView layoutBtnImport;

    private boolean isValidLottieJson(String json) {
        if (json == null || json.isEmpty()) return false;
        try {
            return json.contains("\"v\"") && (json.contains("\"assets\"") || json.contains("\"layers\""));
        } catch (Exception e) {
            return false;
        }
    }

    public void refreshData() {
        collectionLotties = new ArrayList<>();
        File assetsDir = new File(SketchwarePaths.getCollectionPath() + File.separator + "assets");
        if (!assetsDir.exists()) {
            adapter.notifyDataSetChanged();
            updateGuideVisibility();
            return;
        }
        File[] files = assetsDir.listFiles((d, name) -> name != null && name.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                String json = readFileContents(f.getAbsolutePath());
                if (isValidLottieJson(json)) {
                    String resFull = f.getName();
                    String resName = resFull.substring(0, Math.max(0, resFull.lastIndexOf('.')));
                    collectionLotties.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, resName, resFull));
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateGuideVisibility();
    }

    private String readFileContents(String path) {
        try (FileInputStream fis = new FileInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            while ((read = fis.read(buffer)) != -1) baos.write(buffer, 0, read);
            return baos.toString("UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void unselectAll() {
        for (ProjectResourceBean p : collectionLotties) p.isSelected = false;
        adapter.notifyDataSetChanged();
    }

    public boolean isSelecting() {
        for (ProjectResourceBean p : collectionLotties) if (p.isSelected) return true;
        return false;
    }

    public void updateGuideVisibility() {
        boolean isEmpty = collectionLotties.isEmpty();
        binding.tvGuide.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.imageList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    public void importLotties() {
        ArrayList<ProjectResourceBean> selected = new ArrayList<>();
        for (ProjectResourceBean p : collectionLotties) {
            if (p.isSelected) selected.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, p.resName, SketchwarePaths.getCollectionPath() + File.separator + "assets" + File.separator + p.resFullName));
        }
        if (!selected.isEmpty()) {
            Intent intent = new Intent(requireActivity(), ManageLottieImportActivity.class);
            intent.putParcelableArrayListExtra("project_lotties", ((ManageLottieActivity) requireActivity()).m().getLotties());
            intent.putParcelableArrayListExtra("selected_collections", selected);
            openImageImportDetails.launch(intent);
        }
        unselectAll();
        adapter.notifyDataSetChanged();
    }

    private void onItemSelected() {
        int count = 0;
        for (ProjectResourceBean p : collectionLotties) if (p.isSelected) count++;
        if (count > 0) {
            btnImport.setText(getString(R.string.common_word_import_count, count).toUpperCase());
            layoutBtnImport.setVisibility(View.VISIBLE);
        } else layoutBtnImport.setVisibility(View.GONE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sc_id = savedInstanceState != null ? savedInstanceState.getString("sc_id") : requireActivity().getIntent().getStringExtra("sc_id");
        refreshData();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_import) {
            layoutBtnImport.setVisibility(View.GONE);
            importLotties();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (binding.imageList.getLayoutManager() instanceof GridLayoutManager manager) {
            manager.setSpanCount(ManageLottieActivity.getLottieGridColumnCount(requireContext()));
        }
        binding.imageList.requestLayout();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FrManageLottieListBinding.inflate(inflater, container, false);
        binding.imageList.setHasFixedSize(true);
        binding.imageList.setLayoutManager(new GridLayoutManager(requireActivity(), ManageLottieActivity.getLottieGridColumnCount(requireContext())));
        adapter = new Adapter();
        binding.imageList.setAdapter(adapter);
        binding.tvGuide.setText(R.string.design_manager_lottie_description_guide_add_animation);
        btnImport = requireActivity().findViewById(R.id.btn_import);
        layoutBtnImport = requireActivity().findViewById(R.id.layout_btn_import);
        btnImport.setOnClickListener(this);
        layoutBtnImport.setVisibility(View.GONE);
        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectResourceBean lottie = collectionLotties.get(position);
            holder.binding.chkSelect.setVisibility(View.VISIBLE);
            holder.binding.lottieAnimationIndicator.setVisibility(View.VISIBLE);

            String jsonPath = SketchwarePaths.getCollectionPath() + File.separator + "assets" + File.separator + lottie.resFullName;
            String json = readFileContents(jsonPath);

            try {
                if (json != null && isValidLottieJson(json)) {
                    holder.binding.lottie.setAnimationFromJson(json, lottie.resFullName);
                    holder.binding.lottie.setProgress(0f);
                    holder.binding.lottieAnimationIndicator.setVisibility(View.GONE);
                } else {
                    holder.binding.lottie.cancelAnimation();
                    holder.binding.lottie.setImageResource(R.drawable.ic_mtrl_animation);
                    holder.binding.lottieAnimationIndicator.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                holder.binding.lottie.cancelAnimation();
                holder.binding.lottie.setImageResource(R.drawable.ic_mtrl_animation);
                holder.binding.lottieAnimationIndicator.setVisibility(View.VISIBLE);
            }

            holder.binding.tvLottieName.setText(lottie.resName);
            holder.binding.chkSelect.setChecked(lottie.isSelected);
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ManageLottieListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public int getItemCount() {
            return collectionLotties.size();
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            holder.binding.lottie.cancelAnimation();
            holder.binding.lottie.setImageDrawable(null);
            holder.binding.lottie.setProgress(0f);
            super.onViewRecycled(holder);
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            public final ManageLottieListItemBinding binding;
            public ViewHolder(@NonNull ManageLottieListItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                binding.chkSelect.setVisibility(View.VISIBLE);
                binding.lottie.setOnClickListener(v -> {
                    binding.chkSelect.setChecked(!binding.chkSelect.isChecked());
                    collectionLotties.get(getLayoutPosition()).isSelected = binding.chkSelect.isChecked();
                    onItemSelected();
                    notifyItemChanged(getLayoutPosition());
                });
            }
        }
    }
}