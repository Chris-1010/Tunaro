package com.ca.tunaro;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {
    private final Fragment[] fragments = new Fragment[3];

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                fragments[position] = new LibraryFragment();
                break;
            case 2:
                fragments[position] = new RankingsFragment();
                break;
            default:
                fragments[0] = new PlayFragment();
        }

        return fragments[position];
    }

    @Override
    public int getItemCount() {
        return 3;    // The amount of tabs
    }

    public Fragment getFragment(int position) {
        return fragments[position];
    }
}
