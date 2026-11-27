package com.ca.tunaro.utils;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * Album-art gradient backgrounds shared by the detail screens (playlist, album, artist).
 */
public final class GradientBackground {

    static final int CROSSFADE_MS = 200;
    /** How far the collapsed-toolbar tint is blended toward black. */
    static final float SCRIM_DARKEN = 0.35f;
    /** Fraction of the collapsed toolbar held on black before the tint ramps in. */
    static final float SCRIM_HOLD = 0.2f;

    private GradientBackground() {
    }

    /** Top-right colour fading to black at the bottom-left. */
    public static GradientDrawable gradient(int color) {
        return new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                new int[]{color, Color.BLACK});
    }

    /**
     * Set the gradient as {@code view}'s background. With {@code animate}, crossfade from the
     * current background; otherwise (e.g. a cached colour delivered synchronously) swap instantly.
     */
    public static void apply(@Nullable View view, int color, boolean animate) {
        if (view == null) return;
        Drawable target = gradient(color);
        if (!animate) {
            view.setBackground(target);
            return;
        }
        TransitionDrawable fade = new TransitionDrawable(
                new Drawable[]{currentBackground(view), target});
        fade.setCrossFadeEnabled(true);
        view.setBackground(fade);
        fade.startTransition(CROSSFADE_MS);
    }

    /**
     * Collapsed-toolbar scrim: black under the back arrow and small cover, ramping to a darkened
     * tint of {@code color} at the right edge.
     */
    public static GradientDrawable collapsedScrim(int color) {
        int tint = ColorUtils.blendARGB(color, Color.BLACK, SCRIM_DARKEN);
        GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.BLACK, Color.BLACK, tint});
        scrim.setGradientCenter(SCRIM_HOLD, 0.5f);
        return scrim;
    }

    // The drawable to fade from: unwrap a previous crossfade to its end state so repeated
    // applies never nest TransitionDrawables.
    private static Drawable currentBackground(View view) {
        Drawable current = view.getBackground();
        if (current instanceof TransitionDrawable) {
            TransitionDrawable previous = (TransitionDrawable) current;
            current = previous.getDrawable(previous.getNumberOfLayers() - 1);
        }
        return current != null ? current : new ColorDrawable(Color.BLACK);
    }
}
