package com.ca.tunaro.interfaces;

import android.view.View;

public interface Song_RecyclerViewInterface {
    void onItemClick(int position, View itemView);

    // Quick play functionality
    default void onAlbumCoverLongClick(int position) {}
}
