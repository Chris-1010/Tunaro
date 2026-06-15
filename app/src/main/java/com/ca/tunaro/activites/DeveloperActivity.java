package com.ca.tunaro.activites;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.fragments.ApiRequestFragment;
import com.ca.tunaro.fragments.SqlQueryFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class DeveloperActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkForRecovery()) return;
        setContentView(R.layout.activity_developer);

        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return position == 0 ? new SqlQueryFragment() : new ApiRequestFragment();
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(position == 0 ? "SQL" : "API")).attach();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.zoom_in, R.anim.zoom_out);
    }
}
