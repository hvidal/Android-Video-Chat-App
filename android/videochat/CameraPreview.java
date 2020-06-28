/*
Copyright (c) 2014 Hugo Vidal Teixeira

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package videochat;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;


class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private Camera camera;
    public final UDPSocket outSocket;
    private final Command command;

    CameraPreview(Context context, short userId, InetSocketAddress address, Command command) {
        super(context);
        this.outSocket = new UDPSocket(userId, address, true);
        this.command = command;

        SurfaceHolder holder = getHolder(); // initialize the Surface Holder
        holder.addCallback(this); // add the call back to the surface holder
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        setKeepScreenOn(true);
    }

    public void surfaceCreated(SurfaceHolder holder)
    {
        // The Surface has been created, acquire the camera and tell it where to draw.
        camera = getCamera();
        camera.setDisplayOrientation(90);
        Camera.Parameters param = camera.getParameters(); // acquire the parameters for the camera

        Camera.Size smallPicture = smallest(param.getSupportedPictureSizes(), 450);
        Camera.Size smallPreview = smallest(param.getSupportedPreviewSizes(), 300);

        param.setPictureSize(smallPicture.width, smallPicture.height);
        param.setPreviewSize(smallPreview.width, smallPreview.height);
        camera.setParameters(param);

        final Camera.Size size = param.getPreviewSize(); // get the size of each frame captured by the camera
        final Rect rect = new Rect(0, 0, size.width, size.height);
        final int format = param.getPreviewFormat();

        Log.i("pic(" + smallPicture.width + "," + smallPicture.height + ") rect(" + size.width + "," + size.height + ")");

        outSocket.connectAsync();

        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.e(e);
        }

        camera.setPreviewCallback(new Camera.PreviewCallback()
        {
            private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            private long lastFrame = 0;

            /* Called for each frame previewed */
            public void onPreviewFrame(byte[] data, Camera camera)
            {
                if (!command.isPeerConnected.get() || !outSocket.isConnected())
                    return;
                long now = System.currentTimeMillis();
                if (now - lastFrame < 50)
                    return; // too fast, skip
                lastFrame = now;
                try {
                    YuvImage yuv = new YuvImage(data, format, size.width, size.height, null);
                    yuv.compressToJpeg(rect, command.localJpegQuality.get(), baos);
                    byte[] bytes = baos.toByteArray();
                    baos.reset();
                    outSocket.send(bytes);
                } catch (Exception e) {
                    Log.e(e);
                }
            }
        });
    }

    // Called when the holder is destroyed
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.stopPreview(); // preview will stop once user exits the application screen
        camera = null;
        outSocket.close();
    }

    // Called when holder has changed
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // camera frame preview starts when user launches application screen
        camera.startPreview();
    }

    void close() {
        outSocket.close();
    }

    private static Camera getCamera() {
        return getDefaultCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    private static Camera getDefaultCamera(int position) {
        int mNumberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < mNumberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == position) {
                return Camera.open(i);
            }
        }
        return Camera.open();
    }

    private Camera.Size smallest(List<Camera.Size> sizes, int limit) {
        Camera.Size smallSize = sizes.get(0);
        for (int i = 1; i < sizes.size(); i++) {
            Camera.Size size = sizes.get(i);
            if (size.width < smallSize.width && size.width > limit)
                smallSize = size;
        }
        return smallSize;
    }
}
