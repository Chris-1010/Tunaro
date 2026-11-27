package com.ca.tunaro.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;

/**
 * App-wide Glide configuration: a disk cache sized for a large library of album
 * covers, and no legacy manifest-module scan at startup. List thumbnails request
 * fixed decode sizes via {@link ImageLoads}.
 */
@GlideModule
public final class TunaroGlideModule extends AppGlideModule {

    /** Disk cache for downloaded covers. */
    private static final long DISK_CACHE_BYTES = 300L * 1024 * 1024;

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_BYTES));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        // No legacy manifest modules are used; skip the manifest scan at startup.
        return false;
    }
}
