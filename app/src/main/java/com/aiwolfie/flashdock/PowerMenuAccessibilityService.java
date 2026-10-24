package com.aiwolfie.flashdock;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Opens Android's own power dialog only after the owner enables this service. */
public final class PowerMenuAccessibilityService extends AccessibilityService {
    private static volatile PowerMenuAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally does not inspect events or screen contents.
    }

    @Override
    public void onInterrupt() {
        // No ongoing feedback or gesture session to interrupt.
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean openPowerDialog() {
        PowerMenuAccessibilityService service = instance;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }
}
