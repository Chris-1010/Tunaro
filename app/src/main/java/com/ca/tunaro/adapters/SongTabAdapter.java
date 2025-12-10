package com.ca.tunaro.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.fragments.SongDetailsFragment;
import com.ca.tunaro.fragments.SongNotesFragment;
import com.ca.tunaro.fragments.SongSnippetsFragment;

public class SongTabAdapter extends FragmentStateAdapter {
    private final SongModel song;
    private final Fragment[] fragments = new Fragment[3];

    public SongTabAdapter(@NonNull FragmentActivity fragmentActivity, SongModel song) {
        super(fragmentActivity);
        this.song = song;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                fragments[1] = SongNotesFragment.newInstance(song);
                break;
            case 2:
                fragments[2] = SongSnippetsFragment.newInstance(song);
                break;
            default:
                fragments[0] = SongDetailsFragment.newInstance(song);
        }

        return fragments[position];
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}