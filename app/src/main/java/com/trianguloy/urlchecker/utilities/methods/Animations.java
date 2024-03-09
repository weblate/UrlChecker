package com.trianguloy.urlchecker.utilities.methods;

import android.animation.LayoutTransition;
import android.view.View;
import android.view.ViewGroup;

/**
 * Animations-related functionality
 */
public interface Animations {
    /**
     * Enables animations for a specific view
     */
    static void enableAnimations(View view) {
        if (view instanceof ViewGroup) {
            final LayoutTransition lt = ((ViewGroup) view).getLayoutTransition();
            if (lt != null) {
                lt.enableTransitionType(LayoutTransition.CHANGING);
            } else {
                AndroidUtils.assertError(view + " doesn't have a LayoutTransition");
            }
        } else {
            AndroidUtils.assertError(view + " isn't a ViewGroup");
        }
    }
}