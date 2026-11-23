package com.mojang.minecraftpe;

import android.app.Activity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;

// Resolution of the game relative to the window. Sizes, DPI and insets the game asks Java for shrink
// by the same factor as its picture.
//  - Games on NativeActivity (up to 1.19) get smaller window buffers from libendercore.so,
//    which also shrinks touch coordinates.
//  - Games on GameActivity (1.21.101+) draw into a SurfaceView. It shrinks here and is stretched back
//    to the window, so the surface, the content rect and touch coordinates all get the smaller size.
final class ResolutionScale {
    private static final String TAG = "EnderCore-Resolution";

    private final float factor;

    ResolutionScale(float requested) {
        factor = requested > 0f && requested < 1f ? requested : 1f;
    }

    float getFactor() {
        return factor;
    }

    int scale(int value) {
        return Math.round(value * factor);
    }

    float scale(float value) {
        return value * factor;
    }

    // -- for 1.26.62+ begin --
    Insets scale(Insets insets) {
        if (insets == null || factor == 1f) {
            return insets;
        }
        return Insets.of(scale(insets.left), scale(insets.top), scale(insets.right), scale(insets.bottom));
    }

    void fitSurface(Activity activity) {
        if (factor == 1f) {
            return;
        }
        final SurfaceView surfaceView = findSurfaceView(activity.getWindow().getDecorView());
        if (surfaceView == null || !(surfaceView.getParent() instanceof View)) {
            Log.e(TAG, "Game surface not found, the resolution stays full.");
            return;
        }
        ((View) surfaceView.getParent()).addOnLayoutChangeListener(
                (container, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        container.post(() -> fitSurface(surfaceView, right - left, bottom - top)));
    }

    private void fitSurface(SurfaceView surfaceView, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int scaledWidth = Math.max(1, scale(width));
        int scaledHeight = Math.max(1, scale(height));
        surfaceView.setPivotX(0f);
        surfaceView.setPivotY(0f);
        surfaceView.setScaleX((float) width / scaledWidth);
        surfaceView.setScaleY((float) height / scaledHeight);
        ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
        if (params.width != scaledWidth || params.height != scaledHeight) {
            params.width = scaledWidth;
            params.height = scaledHeight;
            surfaceView.setLayoutParams(params);
        }
    }

    private static SurfaceView findSurfaceView(View view) {
        if (view instanceof SurfaceView) {
            return (SurfaceView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                SurfaceView found = findSurfaceView(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
    // -- for 1.26.62+ end --
}
