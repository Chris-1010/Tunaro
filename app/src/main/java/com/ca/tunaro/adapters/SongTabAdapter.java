package com.ca.tunaro.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.fragments.SongDetailsFragment;
import com.ca.tunaro.fragments.SongNotesFragment;
import com.ca.tunaro.fragments.SongSnippetsFragment;

import java.util.ArrayList;
import java.util.List;

public class SongTabAdapter extends FragmentStateAdapter {
    private final SongModel song;
    private final boolean isLoading;
    private final ArrayList<String> variantUris;
    private final Fragment[] fragments = new Fragment[3];

    public SongTabAdapter(@NonNull FragmentActivity fragmentActivity, SongModel song, boolean isLoading, List<String> variantUris) {
        super(fragmentActivity);
        this.song = song;
        this.isLoading = isLoading;
        this.variantUris = new ArrayList<>(variantUris);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                fragments[1] = SongNotesFragment.newInstance(song, variantUris);
                break;
            case 2:
                fragments[2] = SongSnippetsFragment.newInstance(song, variantUris);
                break;
            default:
                fragments[0] = SongDetailsFragment.newInstance(song, isLoading, variantUris);
        }

        return fragments[position];
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}