package com.ca.tunaro.utils;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

/**
 * Derives a small palette of variations from a single album-extracted base
 * colour, used to theme snippet rows (background, border, seekbar, text,
 * play button) so they feel connected to the currently-selected song.
 *
 * <p>The chosen look is a dark / translucent tint: the row blends into the
 * dark gradient background with light text, while the play button and seekbar
 * progress use the vibrant swatch to pop.
 */
public class SnippetTheme {

    /** Translucent dark fill for the row background. */
    public final int rowBackground;
    /** Vibrant stroke for the row border. */
    public final int border;
    /** Muted, dark track for the seekbar background. */
    public final int seekbarTrack;
    /** Vibrant fill for the seekbar progress. */
    public final int seekbarProgress;
    /** Accent for the seekbar thumb. */
    public final int seekbarThumb;
    /** Vibrant tint for the play button. */
    public final int playButton;
    /** Contrast colour (black/white) for the play/stop icon on the play button. */
    public final int playButtonIcon;
    /** High-contrast colour for the primary text (title / range). */
    public final int primaryText;
    /** Slightly dimmer colour for secondary text (snippet number). */
    public final int secondaryText;
    /** Tint for the row icons (e.g. detach/loop). */
    public final int icon;

    private SnippetTheme(int rowBackground, int border, int seekbarTrack,
                         int seekbarProgress, int seekbarThumb, int playButton,
                         int playButtonIcon, int primaryText, int secondaryText,
                         int icon) {
        this.rowBackground = rowBackground;
        this.border = border;
        this.seekbarTrack = seekbarTrack;
        this.seekbarProgress = seekbarProgress;
        this.seekbarThumb = seekbarThumb;
        this.playButton = playButton;
        this.playButtonIcon = playButtonIcon;
        this.primaryText = primaryText;
        this.secondaryText = secondaryText;
        this.icon = icon;
    }

    /**
     * Build a theme from the album's vibrant and dominant swatches.
     */
    public static SnippetTheme from(int vibrantColor, int dominantColor) {
        // The vibrant swatch drives the accents; fall back to dominant when the
        // vibrant one is too washed out to read against a dark fill.
        int accent = ColorExtractor.pickBackgroundColor(dominantColor, vibrantColor);

        int rowBackground = withAlpha(darken(desaturate(accent, 0.25f), 0.78f), 220);
        int border = setAlpha(saturate(accent, 0.15f), 150);
        int seekbarTrack = withAlpha(darken(accent, 0.55f), 120);
        int seekbarProgress = saturate(lighten(accent, 0.05f), 0.2f);
        int seekbarThumb = lighten(seekbarProgress, 0.12f);
        int playButton = saturate(accent, 0.1f);

        // The play/stop icon sits on the (often vibrant) play button. Pick black
        // or white depending on which reads better against that fill, so the icon
        // stays visible on bright accents (e.g. yellow) as well as dark ones.
        int playButtonIcon = contrastColor(playButton);

        // Text sits on rowBackground; pick whichever of white/near-black reads.
        // calculateContrast requires an opaque background, so blend the
        // translucent fill over the dark gradient it actually sits on.
        int opaqueRowBg = ColorUtils.compositeColors(rowBackground, Color.BLACK);
        boolean lightText = ColorUtils.calculateContrast(Color.WHITE, opaqueRowBg)
                >= ColorUtils.calculateContrast(Color.parseColor("#202020"), opaqueRowBg);
        int primaryText = lightText ? Color.WHITE : Color.parseColor("#202020");
        int secondaryText = lightText
                ? withAlpha(Color.WHITE, 180)
                : withAlpha(Color.parseColor("#202020"), 180);
        int icon = primaryText;

        return new SnippetTheme(rowBackground, border, seekbarTrack, seekbarProgress,
                seekbarThumb, playButton, playButtonIcon, primaryText, secondaryText, icon);
    }

    /**
     * Returns whichever of white or black reads best on top of {@code background}.
     * The background may be translucent — it's composited over black first, since
     * contrast maths requires an opaque colour. Shared by every place that needs
     * a readable on-colour for a themed fill (row text, tab text, button labels,
     * the play/stop icon).
     */
    public static int contrastColor(int background) {
        int opaque = ColorUtils.compositeColors(background, Color.BLACK);
        return ColorUtils.calculateContrast(Color.WHITE, opaque)
                >= ColorUtils.calculateContrast(Color.BLACK, opaque)
                ? Color.WHITE : Color.BLACK;
    }

    /** A neutral fallback used before/without colour extraction. */
    public static SnippetTheme fallback() {
        int accent = Color.parseColor("#7E57C2"); // purpleTheme-ish
        return from(accent, accent);
    }

    // ----- colour maths -----

    private static int lighten(int color, float amount) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[2] = clamp(hsl[2] + amount);
        return ColorUtils.HSLToColor(hsl);
    }

    private static int darken(int color, float fraction) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[2] = clamp(hsl[2] * (1f - fraction));
        return ColorUtils.HSLToColor(hsl);
    }

    private static int saturate(int color, float amount) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[1] = clamp(hsl[1] + amount);
        return ColorUtils.HSLToColor(hsl);
    }

    private static int desaturate(int color, float fraction) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[1] = clamp(hsl[1] * (1f - fraction));
        return ColorUtils.HSLToColor(hsl);
    }

    private static int withAlpha(int color, int alpha) {
        return setAlpha(color, alpha);
    }

    private static int setAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
