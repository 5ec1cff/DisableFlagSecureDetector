package android.view;

import dev.rikka.tools.refine.RefineAs;

// API 31-33
@RefineAs(SurfaceControl.class)
public class SurfaceControlHidden {
    // API 31 and above

    public static ScreenshotHardwareBuffer captureLayers(LayerCaptureArgs captureArgs) {
        throw new UnsupportedOperationException("");
    }

    public static class ScreenshotGraphicBuffer {}

    public static class ScreenshotHardwareBuffer {}

    private abstract static class CaptureArgs {
        abstract static class Builder<T extends Builder<T>> {
            public T setUid(long uid) {
                throw new UnsupportedOperationException("");
            }
        }
    }

    public static class LayerCaptureArgs {
        public static class Builder extends CaptureArgs.Builder<Builder> {
            public Builder(SurfaceControl layer) {
                throw new UnsupportedOperationException("");
            }

            public LayerCaptureArgs build() {
                throw new UnsupportedOperationException("");
            }
        }

    }
}
