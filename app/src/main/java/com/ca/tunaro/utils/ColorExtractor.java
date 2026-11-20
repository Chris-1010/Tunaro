package com.ca.tunaro.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

public class ColorExtractor {

    public interface ColorExtractionCallback {
        void onColorExtracted(int dominantColor, int vibrantColor);

        void onError();
    }

    // Colour pairs are tiny; 64 covers a playlist's worth of covers.
    private static final int CACHE_SIZE = 64;
    // Palette only needs a coarse sample; Palette itself downsamples to ~112px.
    private static final int DECODE_SIZE_PX = 100;
    private static final int MAX_PALETTE_COLORS = 16;

    private static final ColorRequestCache<ColorExtractionCallback> CACHE =
            new ColorRequestCache<>(CACHE_SIZE);

    /**
     * Extract dominant and vibrant colours from an image URL.
     *
     * Results are cached per URL; a cached result is delivered synchronously so
     * gradients appear without a flash. Concurrent requests for the same URL share
     * one decode. Callbacks run on the main thread.
     *
     * @param context  Android context
     * @param imageUrl URL of the image
     * @param callback Callback with the extracted colours
     */
    public static void extractColors(Context context, String imageUrl, ColorExtractionCallback callback) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            callback.onError();
            return;
        }
        int[] cached = CACHE.get(imageUrl);
        if (cached != null) {
            callback.onColorExtracted(cached[0], cached[1]);
            return;
        }
        if (!CACHE.enqueue(imageUrl, callback)) {
            return; // A load for this URL is already running.
        }

        // Application context: the shared load must not be cancelled when the
        // first requester's activity goes away while other waiters remain.
        Glide.with(context.getApplicationContext())
                .asBitmap()
                .load(imageUrl)
                .override(DECODE_SIZE_PX)
                .into(new CustomTarget<Bitmap>() {
                    private boolean finished;
                    private boolean resourceReceived;

                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                        resourceReceived = true;
                        new Palette.Builder(bitmap)
                                .maximumColorCount(MAX_PALETTE_COLORS)
                                .generate(palette -> {
                                    if (palette != null) {
                                        int dominantColor = palette.getDominantColor(Color.BLACK);
                                        int vibrantColor = palette.getVibrantColor(Color.rgb(100, 0, 0));
                                        finish(new int[]{dominantColor, vibrantColor});
                                    } else {
                                        finish(null);
                                    }
                                });
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // Release waiters if the load is cleared before any bitmap
                        // arrives; once one has, the palette callback finishes instead.
                        if (!resourceReceived) finish(null);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        finish(null);
                    }

                    private void finish(@Nullable int[] colors) {
                        if (finished) return;
                        finished = true;
                        for (ColorExtractionCallback waiter : CACHE.complete(imageUrl, colors)) {
                            if (colors != null) {
                                waiter.onColorExtracted(colors[0], colors[1]);
                            } else {
                                waiter.onError();
                            }
                        }
                    }
                });
    }

    /**
     * Check if a color has sufficient contrast against another
     *
     * @param foregroundColor The text/foreground color
     * @param backgroundColor The background color
     * @return true if contrast ratio is >= 4.5:1
     */
    public static boolean hasSufficientContrast(int foregroundColor, int backgroundColor, float minContrastRatio) {
        if (minContrastRatio <= 0) {
            minContrastRatio = 4.5f; // Default to WCAG AA standard
        }
        return androidx.core.graphics.ColorUtils.calculateContrast(foregroundColor, backgroundColor) >= minContrastRatio;
    }

    /**
     * Pick a background colour for a black gradient, preferring the vibrant swatch
     * over the dominant one and avoiding greyish (low-saturation) colours unless
     * nothing better qualifies.
     */
    // The background is decorative, not text, so the vibrant swatch only needs to be
    // visible against the black gradient end — far below the 4.5:1 WCAG text ratio.
    // 2.5:1 still rejects the dark-red getVibrantColor fallback (1.5:1), which is how
    // "no vibrant swatch exists" is filtered out.
    private static final float MIN_VIBRANT_CONTRAST = 2.5f;

    public static int pickBackgroundColor(int dominantColor, int vibrantColor) {
        if (hasSufficientContrast(vibrantColor, Color.BLACK, MIN_VIBRANT_CONTRAST) && !isGreyish(vibrantColor)) {
            return vibrantColor;
        }
        if (hasSufficientContrast(dominantColor, Color.BLACK, 0) && !isGreyish(dominantColor)) {
            return dominantColor;
        }
        if (hasSufficientContrast(vibrantColor, Color.BLACK, MIN_VIBRANT_CONTRAST)) {
            return vibrantColor;
        }
        if (hasSufficientContrast(dominantColor, Color.BLACK, 0)) {
            return dominantColor;
        }
        return vibrantColor;
    }

    private static boolean isGreyish(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv[1] < 0.35f;
    }
}
