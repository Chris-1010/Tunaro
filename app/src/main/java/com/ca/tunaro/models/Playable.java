package com.ca.tunaro.models;

/**
 * The minimal contract the playback navigation model needs from a song: a stable
 * identity for duplicate collapse and a playability flag for skipping. Keeping
 * the model behind this interface keeps it free of Android/Spotify dependencies.
 */
public interface Playable {
    String getUri();

    boolean isPlayable();
}
