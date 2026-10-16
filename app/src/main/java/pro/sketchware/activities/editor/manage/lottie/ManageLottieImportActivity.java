package pro.sketchware.activities.editor.manage.lottie;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import pro.sketchware.beans.ProjectResourceBean;
import pro.sketchware.activities.base.BaseAppCompatActivity;
import pro.sketchware.widgets.EasyDeleteEditText;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import pro.sketchware.R;
import pro.sketchware.core.validation.XmlNameValidator;
import pro.sketchware.util.SketchToast;
import pro.sketchware.util.Helper;

public class ManageLottieImportActivity extends BaseAppCompatActivity implements View.OnClickListener {
    private LottieAnimationView lottie;
    private TextView tv_currentnum;
    private EditText ed_input_edittext;
    private CheckBox chk_samename;
    private ItemAdapter adapter;
    private ArrayList<ProjectResourceBean> projectLotties;
    private ArrayList<ProjectResourceBean> selectedCollections;
    private int selectedItem = 0;
    private XmlNameValidator nameValidator;

    private ArrayList<String> getReservedSelectedCollectionNames() {
        ArrayList<String> names = new ArrayList<>();
        names.add("app_icon");
        for (ProjectResourceBean selectedCollection : selectedCollections) names.add(selectedCollection.resName);
        return names;
    }

    private ArrayList<String> getReservedProjectLottieNames() {
        ArrayList<String> names = new ArrayList<>();
        names.add("app_icon");
        for (ProjectResourceBean projectLottie : projectLotties) names.add(projectLottie.resName);
        return names;
    }

    private boolean hasDuplicateNames() {
        ArrayList<String> duplicateNames = new ArrayList<>();
        for (ProjectResourceBean selectedCollection : selectedCollections) {
            if (selectedCollection.isDuplicateCollection) duplicateNames.add(selectedCollection.resName);
        }
        if (duplicateNames.isEmpty()) return false;
        String names = String.join(", ", duplicateNames);
        SketchToast.toast(getApplicationContext(), Helper.getResString(R.string.common_message_name_unavailable) + "\n[" + names + "]", SketchToast.TOAST_WARNING).show();
        return true;
    }

    private boolean isNameValid() {
        return nameValidator.isValid();
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK);
        super.onBackPressed();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_decide) {
            String name = Helper.getText(ed_input_edittext);
            if (!isNameValid()) {
                ed_input_edittext.setText(selectedCollections.get(selectedItem).resName);
            } else if (!chk_samename.isChecked()) {
                ProjectResourceBean bean = selectedCollections.get(selectedItem);
                bean.resName = name;
                bean.isDuplicateCollection = false;
                nameValidator.setJavaNames(getReservedSelectedCollectionNames());
                adapter.notifyDataSetChanged();
            } else {
                int i = 0;
                while (i < selectedCollections.size()) {
                    ProjectResourceBean bean = selectedCollections.get(i);
                    bean.resName = name + "_" + ++i;
                    bean.isDuplicateCollection = false;
                }
                nameValidator.setJavaNames(getReservedSelectedCollectionNames());
                adapter.notifyDataSetChanged();
            }
        } else if (id == R.id.lottie_backbtn) {
            onBackPressed();
        } else if (id == R.id.tv_sendbtn && !hasDuplicateNames()) {
            Intent intent = new Intent();
            intent.putParcelableArrayListExtra("results", selectedCollections);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!super.isStoragePermissionGranted()) finish();
        setContentView(R.layout.manage_lottie_import);
        ImageView lottie_backbtn = findViewById(R.id.lottie_backbtn);
        lottie_backbtn.setOnClickListener(this);
        tv_currentnum = findViewById(R.id.tv_currentnum);
        TextView tv_totalnum = findViewById(R.id.tv_totalnum);
        TextView tv_sendbtn = findViewById(R.id.tv_sendbtn);
        tv_sendbtn.setText(Helper.getResString(R.string.common_word_import).toUpperCase());
        tv_sendbtn.setOnClickListener(this);
        TextView tv_samename = findViewById(R.id.tv_samename);
        tv_samename.setText(Helper.getResString(R.string.design_manager_lottie_title_apply_same_naming));
        adapter = new ItemAdapter();
        RecyclerView recycler_list = findViewById(R.id.recycler_list);
        recycler_list.setAdapter(adapter);
        recycler_list.setLayoutManager(new LinearLayoutManager(getApplicationContext(), RecyclerView.HORIZONTAL, false));
        projectLotties = getIntent().getParcelableArrayListExtra("project_lotties");
        selectedCollections = getIntent().getParcelableArrayListExtra("selected_collections");
        int selectedCollectionsSize = selectedCollections.size();
        tv_currentnum.setText(String.valueOf(1));
        tv_totalnum.setText(String.valueOf(selectedCollectionsSize));
        EasyDeleteEditText ed_input = findViewById(R.id.ed_input);
        ed_input_edittext = ed_input.getEditText();
        ed_input_edittext.setText(selectedCollections.get(0).resName);
        ed_input_edittext.setPrivateImeOptions("defaultInputmode=english;");
        ed_input.setHint("Enter Lottie animation name");
        nameValidator = new XmlNameValidator(getApplicationContext(), ed_input.getTextInputLayout(), pro.sketchware.core.project.BlockConstants.RESERVED_KEYWORDS, getReservedProjectLottieNames(), getReservedSelectedCollectionNames());
        chk_samename = findViewById(R.id.chk_samename);
        chk_samename.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                nameValidator.setCurrentName(null);
                nameValidator.setBatchCount(selectedCollections.size());
            } else {
                nameValidator.setCurrentName(selectedCollections.get(selectedItem).resName);
                nameValidator.setBatchCount(1);
            }
        });
        Button btn_decide = findViewById(R.id.btn_decide);
        btn_decide.setText(Helper.getResString(R.string.design_manager_change_name_button));
        btn_decide.setOnClickListener(this);
        lottie = findViewById(R.id.lottie);
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        initializeLogic();
        showPreview(0);
        adapter.notifyDataSetChanged();
    }

    private void initializeLogic() {
        ArrayList<ProjectResourceBean> duplicateCollections = new ArrayList<>();
        ArrayList<ProjectResourceBean> notDuplicateCollections = new ArrayList<>();
        for (ProjectResourceBean sel : selectedCollections) {
            if (isNameInUseByProjectLottie(sel.resName)) {
                sel.isDuplicateCollection = true;
                duplicateCollections.add(sel);
            } else {
                sel.isDuplicateCollection = false;
                notDuplicateCollections.add(sel);
            }
        }
        if (!duplicateCollections.isEmpty()) SketchToast.warning(getApplicationContext(), Helper.getResString(R.string.design_manager_message_collection_name_conflict), SketchToast.TOAST_WARNING).show();
        else SketchToast.toast(getApplicationContext(), Helper.getResString(R.string.design_manager_message_collection_name_no_conflict), SketchToast.TOAST_NORMAL).show();
        selectedCollections = new ArrayList<>();
        selectedCollections.addAll(duplicateCollections);
        selectedCollections.addAll(notDuplicateCollections);
    }

    private void showPreview(int index) {
        String path = selectedCollections.get(index).resFullName;
        try {
            String json = readFileContents(path);
            lottie.setAnimationFromJson(json, path);
            lottie.setRepeatCount(0);
            lottie.playAnimation();
        } catch (Exception e) {
            lottie.cancelAnimation();
            lottie.setBackgroundResource(R.drawable.ic_remove_grey600_24dp);
        }
    }

    private String readFileContents(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(new File(path));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) bos.write(buffer, 0, read);
            return bos.toString("UTF-8");
        }
    }

    private boolean isNameInUseByProjectLottie(String name) {
        for (ProjectResourceBean p : projectLotties) if (p.resName.equals(name)) return true;
        return false;
    }

    private class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {
        public ItemAdapter() { }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
            ProjectResourceBean bean = selectedCollections.get(position);
            viewHolder.lottie_conflict.setImageResource(bean.isDuplicateCollection ? R.drawable.ic_cancel_48dp : R.drawable.ic_ok_48dp);
            viewHolder.lottie.setBackgroundResource(position == selectedItem ? R.drawable.bg_outline_dark_yellow : android.R.color.white);
            try {
                String json = readFileContents(bean.resFullName);
                viewHolder.lottie.setAnimationFromJson(json, bean.resFullName);
                viewHolder.lottie.setRepeatCount(LottieDrawable.INFINITE);
                viewHolder.lottie.playAnimation();
            } catch (Exception e) {
                viewHolder.lottie.cancelAnimation();
                viewHolder.lottie.setBackgroundResource(R.drawable.ic_remove_grey600_24dp);
            }
            viewHolder.tv_name.setText(bean.resName);
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.manage_lottie_import_list_item, parent, false));
        }

        @Override
        public int getItemCount() { return selectedCollections.size(); }

        private class ViewHolder extends RecyclerView.ViewHolder {
            public final LinearLayout layout_item;
            public final ImageView lottie_conflict;
            public final LottieAnimationView lottie;
            public final TextView tv_name;

            public ViewHolder(View itemView) {
                super(itemView);
                layout_item = itemView.findViewById(R.id.layout_item);
                lottie_conflict = itemView.findViewById(R.id.lottie_conflict);
                lottie = itemView.findViewById(R.id.lottie);
                tv_name = itemView.findViewById(R.id.tv_name);
                lottie.setOnClickListener(v -> {
                    selectedItem = getLayoutPosition();
                    showPreview(selectedItem);
                    tv_currentnum.setText(String.valueOf(selectedItem + 1));
                    ed_input_edittext.setText(selectedCollections.get(selectedItem).resName);
                    if (chk_samename.isChecked()) {
                        nameValidator.setCurrentName(null);
                        nameValidator.setBatchCount(selectedCollections.size());
                    } else {
                        nameValidator.setCurrentName(selectedCollections.get(selectedItem).resName);
                        nameValidator.setBatchCount(1);
                    }
                    adapter.notifyDataSetChanged();
                });
            }
        }
    }
}