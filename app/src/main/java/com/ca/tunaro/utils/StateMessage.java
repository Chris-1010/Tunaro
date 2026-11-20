package com.ca.tunaro.utils;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.R;
import com.facebook.shimmer.ShimmerFrameLayout;

/**
 * Drives an included {@code view_state_message} block: the one empty / error state look used
 * across screens (48dp tertiary icon, title, one-line hint, optional accent text action).
 */
public final class StateMessage {

    private StateMessage() {
    }

    /** Shows the block with the given content. Pass {@code actionRes = 0} for no action button. */
    public static void show(@Nullable View root, @DrawableRes int iconRes, @StringRes int titleRes,
                            @StringRes int hintRes, @StringRes int actionRes,
                            @Nullable Runnable onAction) {
        if (root == null) return;
        ImageView icon = root.findViewById(R.id.state_icon);
        TextView title = root.findViewById(R.id.state_title);
        TextView hint = root.findViewById(R.id.state_hint);
        TextView action = root.findViewById(R.id.state_action);

        if (icon != null) {
            icon.setImageResource(iconRes);
            icon.setVisibility(iconRes != 0 ? View.VISIBLE : View.GONE);
        }
        if (title != null) title.setText(titleRes);
        if (hint != null) {
            if (hintRes != 0) {
                hint.setText(hintRes);
                hint.setVisibility(View.VISIBLE);
            } else {
                hint.setVisibility(View.GONE);
            }
        }
        if (action != null) {
            if (actionRes != 0 && onAction != null) {
                action.setText(actionRes);
                action.setOnClickListener(v -> onAction.run());
                action.setVisibility(View.VISIBLE);
            } else {
                action.setOnClickListener(null);
                action.setVisibility(View.GONE);
            }
        }
        root.setVisibility(View.VISIBLE);
    }

    /** Shows an empty state with no action. */
    public static void showEmpty(@Nullable View root, @DrawableRes int iconRes,
                                 @StringRes int titleRes, @StringRes int hintRes) {
        show(root, iconRes, titleRes, hintRes, 0, null);
    }

    /** Shows the standard load-failure state with a Retry action. */
    public static void showError(@Nullable View root, @StringRes int titleRes,
                                 @StringRes int hintRes, @Nullable Runnable onRetry) {
        show(root, R.drawable.ic_state_cloud_off, titleRes, hintRes, R.string.retry, onRetry);
    }

    /**
     * Keeps an empty state in sync with an adapter: shown whenever the adapter has no items,
     * hidden otherwise. Evaluates once immediately.
     */
    public static void bindEmptyState(@NonNull RecyclerView.Adapter<?> adapter, @Nullable View root,
                                      @DrawableRes int iconRes, @StringRes int titleRes,
                                      @StringRes int hintRes) {
        if (root == null) return;
        Runnable sync = () -> {
            if (adapter.getItemCount() == 0) {
                showEmpty(root, iconRes, titleRes, hintRes);
            } else {
                hide(root);
            }
        };
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                sync.run();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                sync.run();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                sync.run();
            }
        });
        sync.run();
    }

    /** Shows (and animates) or hides a first-load shimmer placeholder. */
    public static void setShimmer(@Nullable View shimmer, boolean show) {
        if (!(shimmer instanceof ShimmerFrameLayout)) return;
        ShimmerFrameLayout layout = (ShimmerFrameLayout) shimmer;
        if (show) {
            layout.setVisibility(View.VISIBLE);
            layout.startShimmer();
        } else {
            layout.stopShimmer();
            layout.setVisibility(View.GONE);
        }
    }

    public static void hide(@Nullable View root) {
        if (root != null) root.setVisibility(View.GONE);
    }
}
