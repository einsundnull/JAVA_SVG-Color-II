package main;

import java.awt.Color;

public class FloodFillResult {
    public final int pixelCount;
    public final Color originColor;
    public final boolean wasTransparent;

    public FloodFillResult(int pixelCount, Color originColor, boolean wasTransparent) {
        this.pixelCount = pixelCount;
        this.originColor = originColor;
        this.wasTransparent = wasTransparent;
    }

    public boolean changed() { return pixelCount > 0; }
}
