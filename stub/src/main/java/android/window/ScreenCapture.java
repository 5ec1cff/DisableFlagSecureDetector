package android.window;

import android.graphics.Bitmap;
import android.view.SurfaceControl;

// API 34+
public class ScreenCapture {
    public static ScreenshotHardwareBuffer captureLayers(LayerCaptureArgs captureArgs) {
        throw new UnsupportedOperationException("");
    }

    public static class ScreenshotHardwareBuffer {
        public boolean containsSecureLayers() {
            throw new UnsupportedOperationException("");
        }

        public Bitmap asBitmap() {
            throw new UnsupportedOperationException("");
        }
    }


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
