package com.ca.tunaro;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class SongTabAdapter extends FragmentStateAdapter {
    private final SongModel song;
    private final Fragment[] fragments = new Fragment[2];

    public SongTabAdapter(@NonNull FragmentActivity fragmentActivity, SongModel song) {
        super(fragmentActivity);
        this.song = song;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // Keeping the switch over an 'if' statement in case there'll be more tabs in the future
        switch (position) {
            case 1:
                fragments[position] = SongSnippetsFragment.newInstance(song);
                break;
            default:
                fragments[0] = SongNotesFragment.newInstance(song);
        }

        return fragments[position];
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}