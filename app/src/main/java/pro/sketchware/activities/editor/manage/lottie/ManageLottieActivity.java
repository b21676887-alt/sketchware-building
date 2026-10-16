package pro.sketchware.activities.editor.manage.lottie;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import pro.sketchware.activities.base.BaseAppCompatActivity;

import java.lang.ref.WeakReference;

import pro.sketchware.R;
import pro.sketchware.databinding.ManageLottieBinding;
import pro.sketchware.util.Helper;
import pro.sketchware.core.async.BackgroundTasks;
import pro.sketchware.core.async.TaskHost;
import pro.sketchware.core.project.ImageCollectionManager;
import pro.sketchware.util.SketchToast;

public class ManageLottieActivity extends BaseAppCompatActivity implements ViewPager.OnPageChangeListener {
    private String sc_id;
    private LottieProjectFragment projectLottiesFragment;
    private LottieCollectionFragment collectionLottiesFragment;
    private ManageLottieBinding binding;

    public static int getLottieGridColumnCount(Context context) {
        var displayMetrics = context.getResources().getDisplayMetrics();
        return (int) (displayMetrics.widthPixels / displayMetrics.density) / 100;
    }

    @Override
    public void onPageScrollStateChanged(int state) { }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }

    public void f(int i) { binding.viewPager.setCurrentItem(i); }

    public LottieCollectionFragment l() { return collectionLottiesFragment; }

    public LottieProjectFragment m() { return projectLottiesFragment; }

    @Override
    public void onBackPressed() {
        if (projectLottiesFragment.isSelecting) {
            projectLottiesFragment.setSelectionMode(false);
        } else if (collectionLottiesFragment.isSelecting()) {
            collectionLottiesFragment.unselectAll();
            binding.layoutBtnImport.setVisibility(View.GONE);
        } else {
            showLoadingDialog();
            new Handler().postDelayed(() -> {
                new SaveLottiesAsyncTask(this).execute();
            }, 500L);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageLottieBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (!super.isStoragePermissionGranted()) {
            finish();
        }

        setSupportActionBar(binding.topAppBar);
        binding.topAppBar.setTitle("Lottie Manage");
        binding.topAppBar.setNavigationOnClickListener(v -> onBackPressed());

        if (savedInstanceState == null) sc_id = getIntent().getStringExtra("sc_id");
        else sc_id = savedInstanceState.getString("sc_id");

        binding.viewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));
        binding.viewPager.setOffscreenPageLimit(2);
        binding.viewPager.addOnPageChangeListener(this);
        binding.tabLayout.setupWithViewPager(binding.viewPager);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!super.isStoragePermissionGranted()) finish();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPageSelected(int position) {
        binding.layoutBtnGroup.setVisibility(View.GONE);
        binding.layoutBtnImport.setVisibility(View.GONE);
        if (position == 0) {
            binding.fab.animate().translationY(0F).setDuration(200L).start();
            binding.fab.show();
            collectionLottiesFragment.unselectAll();
        } else {
            binding.fab.animate().translationY(400F).setDuration(200L).start();
            binding.fab.hide();
            projectLottiesFragment.setSelectionMode(false);
        }
    }

    private static class SaveLottiesAsyncTask {
        private final WeakReference<ManageLottieActivity> activityRef;

        public SaveLottiesAsyncTask(ManageLottieActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        public void execute() {
            var activity = activityRef.get();
            if (activity == null) return;
            BackgroundTasks.runSerial(TaskHost.of(activity), "ManageLottieActivity$SaveLottiesAsyncTask",
                    this::doWork, this::onSuccess, this::onError);
        }

        private void doWork() {
            var activity = activityRef.get();
            if (activity == null) return;
            activity.projectLottiesFragment.saveImages();
        }

        private void onSuccess() {
            var activity = activityRef.get();
            if (activity == null) return;
            activity.dismissLoadingDialog();
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
            ImageCollectionManager.getInstance().clearCollections();
        }

        private void onError(Throwable t) {
            var activity = activityRef.get();
            if (activity == null) return;
            activity.dismissLoadingDialog();
            SketchToast.warning(activity, Helper.getResString(R.string.common_error_an_error_occurred) + "[" + (t != null ? t.getMessage() : "") + "]", 1).show();
        }
    }

    private class PagerAdapter extends FragmentPagerAdapter {
        private final String[] labels;
        public PagerAdapter(FragmentManager manager) {
            super(manager);
            labels = new String[2];
            labels[0] = Helper.getResString(R.string.design_manager_tab_title_this_project);
            labels[1] = Helper.getResString(R.string.design_manager_tab_title_my_collection);
        }

        @Override
        public int getCount() { return 2; }

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (position == 0) projectLottiesFragment = (LottieProjectFragment) fragment;
            else collectionLottiesFragment = (LottieCollectionFragment) fragment;
            return fragment;
        }

        @Override
        @NonNull
        public Fragment getItem(int position) { return position == 0 ? new LottieProjectFragment() : new LottieCollectionFragment(); }

        @Override
        public CharSequence getPageTitle(int position) { return labels[position]; }
    }
}