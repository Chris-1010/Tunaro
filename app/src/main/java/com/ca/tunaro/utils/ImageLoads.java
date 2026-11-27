package com.ca.tunaro.utils;

import android.content.Context;

import com.bumptech.glide.request.RequestOptions;

/**
 * Shared Glide request options for list thumbnails.
 *
 * Covers are stored at Spotify's largest size (headers need it), so list rows
 * request an explicit square decode size. An explicit size also lets the
 * load start before the view is laid out.
 */
public final class ImageLoads {

    private ImageLoads() {}

    /** Options for a square thumbnail whose drawn content is {@code sizeDp} on each side. */
    public static RequestOptions thumbnail(Context context, int sizeDp) {
        return thumbnailPx(dpToPx(context, sizeDp));
    }

    /** Options for a square thumbnail of {@code sizePx} pixels on each side. */
    public static RequestOptions thumbnailPx(int sizePx) {
        return new RequestOptions().override(Math.max(1, sizePx));
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
