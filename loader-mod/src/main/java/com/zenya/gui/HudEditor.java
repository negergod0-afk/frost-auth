package com.zenya.gui;

/** Stub – HudEditor removed. All interactions are no-ops. */
public final class HudEditor {
    public static final HudEditor INSTANCE = new HudEditor();
    private HudEditor() {}

    public boolean onMouseClick(double x, double y, int button) { return false; }
    public void    onMouseRelease() {}
    public boolean isDragging() { return false; }
    public void    onMouseDrag(double x, double y) {}
    public boolean onMouseScroll(double x, double y, double amount) { return false; }
}
