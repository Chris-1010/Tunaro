package com.ca.tunaro.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.ca.tunaro.fragments.ArtistAlbumsFragment;
import com.ca.tunaro.fragments.ArtistSongsFragment;

public class ArtistTabAdapter extends FragmentStateAdapter {
    private final String artistId;

    public ArtistTabAdapter(@NonNull FragmentActivity fragmentActivity, String artistId) {
        super(fragmentActivity);
        this.artistId = artistId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 1) {
            return ArtistAlbumsFragment.newInstance(artistId);
        }
        return ArtistSongsFragment.newInstance(artistId);
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
