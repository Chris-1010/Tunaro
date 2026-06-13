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

    /**
     * Extract dominant color from an image URL
     *
     * @param context  Android context
     * @param imageUrl URL of the image
     * @param callback Callback with the extracted color
     */
    public static void extractColors(Context context, String imageUrl, ColorExtractionCallback callback) {
        Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                        // Generate palette from bitmap
                        Palette.from(bitmap).generate(palette -> {
                            if (palette != null) {
                                // Try to get dominant color, fallback to black if not available
                                int dominantColor = palette.getDominantColor(Color.BLACK);
                                int vibrantColor = palette.getVibrantColor(Color.rgb(100, 0, 0));
                                callback.onColorExtracted(dominantColor, vibrantColor);
                            } else {
                                callback.onError();
                            }
                        });
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        callback.onError();
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
