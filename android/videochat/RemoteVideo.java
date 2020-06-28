/*
Copyright (c) 2014 Hugo Vidal Teixeira

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package videochat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public class RemoteVideo extends ImageView {

    public final UDPSocket inSocket;
    private final Command command;

    private final TextView fpsView;
    private long fpsTimeCount = 0;
    private int fps = 0;

    public Queue<Bitmap> bitmapQueue = new ConcurrentLinkedQueue<Bitmap>();

    private Bitmap currentBitmap;
    private long lastDisplayedTime = 0;

    private Matrix matrix;

    public AtomicInteger discarded = new AtomicInteger(0);
    public AtomicLong displayTime = new AtomicLong(0);
    public AtomicLong decodeTime = new AtomicLong(0);

    public RemoteVideo(Context context, short userId, InetSocketAddress address, Command command, TextView fpsView) {
        super(context);
        this.inSocket = new UDPSocket(userId, address, false);
        this.command = command;
        this.fpsView = fpsView;
        setKeepScreenOn(true);
        start();

        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (RemoteVideo.this.command.isPeerConnected.get() && !bitmapQueue.isEmpty())
                    RemoteVideo.this.invalidate();
                handler.postDelayed(this, 25);
            }
        };
        handler.post(runnable);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap bitmap = bitmapQueue.poll();
        if (bitmap != null) {
            if (currentBitmap != null)
                currentBitmap.recycle();
            currentBitmap = bitmap;
            paint(canvas);
        } else if (currentBitmap != null)
            paint(canvas);
        else
            blackBackground(canvas);
    }

    private Point center;

    private void paint(Canvas canvas) {
        long now = System.currentTimeMillis();
        if (matrix == null) {
            int w = currentBitmap.getWidth();
            int h = currentBitmap.getHeight();
            RectF source = new RectF(0, 0, w, h);
            // because of rotation, we switch width and height
            int h2 = canvas.getWidth();
            int w2 = h2 * w / h;

            // position the image in the center of the screen
            center = new Point(canvas.getWidth()/2, canvas.getHeight()/2);
            float x = center.x - w2/2f;
            float y = center.y - h2/2f;
            RectF destination = new RectF(x, y, x + w2, y + h2);
            matrix = new Matrix();
            matrix.setRectToRect(source, destination, Matrix.ScaleToFit.FILL);
            Log.i("canvas.w=" + canvas.getWidth() + " | canvas.h=" + canvas.getHeight());
        }
        blackBackground(canvas);

        canvas.rotate(270f, center.x, center.y);
        canvas.drawBitmap(currentBitmap, matrix, null);
        updateFps();
        lastDisplayedTime = System.currentTimeMillis();
        displayTime.set(System.currentTimeMillis() - now);
    }

    private static final Paint BLACK_PAINT = new Paint();
    static { BLACK_PAINT.setColor(Color.BLACK); }

    private void blackBackground(Canvas canvas) {
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), BLACK_PAINT);
    }

    private void updateFps() {
        long diff = System.currentTimeMillis() - lastDisplayedTime;
        fpsTimeCount += diff;
        fps++;
        if (fpsTimeCount > 800) {
            long avgTime = fpsTimeCount / fps;
            fps = (int) (1000 / avgTime);
            command.reportFPS(fps);
            fpsView.setText(fps + " FPS, L=" + command.localJpegQuality.get() + "%, R=" + command.remoteJpegQuality.get() + "%");
            fpsTimeCount = 0;
            fps = 0;
        }
    }

    private void start() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                inSocket.connect();
                if (!inSocket.isConnected())
                    return;

                while (command.isActive.get()) {
                    try {
                        DatagramPacket packet = inSocket.read();
                        if (bitmapQueue.size() > 3) {
                            Bitmap bmp = bitmapQueue.poll();
                            bmp.recycle();
                            discarded.incrementAndGet();
                            continue; // discard image so that it can catch up the live streaming
                        }
                        long now = System.currentTimeMillis();
                        Bitmap bmp = BitmapFactory.decodeByteArray(packet.getData(), 0, packet.getLength(), null);
                        bitmapQueue.add(bmp);
                        decodeTime.set(System.currentTimeMillis() - now);
                    } catch (IOException e) {
                        Log.e(e);
                    }
                }
            }
        };
        new Thread(runnable).start();
    }

    public void close() {
        inSocket.close();
    }
}
