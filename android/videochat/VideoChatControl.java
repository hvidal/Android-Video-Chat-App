/*
Copyright (c) 2014 Hugo Vidal Teixeira

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package videochat;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.InetSocketAddress;


public class VideoChatControl {

    private static final int PORT_VIDEO_OUT = 8090;
    private static final int PORT_VIDEO_IN = 8091;
    private static final int PORT_AUDIO_IN = 8093;
    private static final int PORT_AUDIO_OUT = 8092;
    private static final int PORT_COMMAND = 8098;
    private static final int PORT_LOGGING = 8099;

    private final short userId;
    private final String serverIP;
    private final Activity activity;
    private final float densityScale;

    private final Command command;

    public VideoChatControl(short userId, String serverIP, Activity activity) {
        this.userId = userId;
        this.serverIP = serverIP;
        this.activity = activity;
        this.densityScale = activity.getResources().getDisplayMetrics().density;
        this.command = new Command(userId, new InetSocketAddress(serverIP, PORT_COMMAND));

        Log.init(userId, new InetSocketAddress(serverIP, PORT_LOGGING));

        fullScreen();
        buildUI();
    }

    private void fullScreen() {
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void buildUI() {
        // BEGIN LAYOUT
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        linearLayout.setLayoutParams(p1);

        FrameLayout frameLayout = new FrameLayout(activity);
        FrameLayout.LayoutParams p2 = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        frameLayout.setLayoutParams(p2);
        linearLayout.addView(frameLayout);

        FrameLayout frameLayoutRemoteView = new FrameLayout(activity);
        FrameLayout.LayoutParams p3 = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        frameLayoutRemoteView.setLayoutParams(p3);
        frameLayout.addView(frameLayoutRemoteView);

        final TextView debugInfoTextView = new TextView(activity);
        debugInfoTextView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        debugInfoTextView.setTextColor(Color.WHITE);
        debugInfoTextView.setSingleLine(false);
        debugInfoTextView.setClickable(true);
        frameLayout.addView(debugInfoTextView);

        final TextView transcriptionTextView = new TextView(activity);
        FrameLayout.LayoutParams p5 = new FrameLayout.LayoutParams(dip(200), dip(100), Gravity.BOTTOM | Gravity.LEFT);
        transcriptionTextView.setLayoutParams(p5);
        transcriptionTextView.setTextColor(Color.WHITE);
        transcriptionTextView.setBackgroundColor(0x11333333);
        transcriptionTextView.setSingleLine(false);
        transcriptionTextView.setPadding(10, 10, 10, 10);
        transcriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        frameLayout.addView(transcriptionTextView);

        FrameLayout frameLayoutLocalView = new FrameLayout(activity);
        frameLayoutLocalView.setLayoutParams(new FrameLayout.LayoutParams(dip(100), dip(100), Gravity.BOTTOM | Gravity.RIGHT));
        frameLayoutLocalView.setBackgroundColor(0x33333333);
        frameLayout.addView(frameLayoutLocalView);

        LinearLayout upperRightLinearLayout = new LinearLayout(activity);
        upperRightLinearLayout.setLayoutParams(new FrameLayout.LayoutParams(dip(200), dip(50), Gravity.RIGHT));
        upperRightLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
        frameLayout.addView(upperRightLinearLayout);

        final TextView fpsTextView = new TextView(activity);
        fpsTextView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3f));
        fpsTextView.setTextColor(Color.WHITE);
        fpsTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        upperRightLinearLayout.addView(fpsTextView);

        Button exitButton = new Button(activity);
        exitButton.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        exitButton.setBackgroundColor(Color.LTGRAY);
        exitButton.setText("Exit");
        exitButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        upperRightLinearLayout.addView(exitButton);

        screenScale(frameLayoutLocalView, .25f, .2f);
        screenScale(transcriptionTextView, .75f, .2f);

        activity.setContentView(linearLayout);
        // END LAYOUT

        final CameraPreview cameraPreview = new CameraPreview(activity, userId, new InetSocketAddress(serverIP, PORT_VIDEO_OUT), command);
        frameLayoutLocalView.addView(cameraPreview);

        final RemoteVideo remoteView = new RemoteVideo(activity, userId, new InetSocketAddress(serverIP, PORT_VIDEO_IN), command, fpsTextView);
        frameLayoutRemoteView.addView(remoteView);

        final InetSocketAddress audioInAddress = new InetSocketAddress(serverIP, PORT_AUDIO_IN);
        final InetSocketAddress audioOutAddress = new InetSocketAddress(serverIP, PORT_AUDIO_OUT);
        final AudioStream audioStream = new AudioStream(userId, audioInAddress, audioOutAddress, command);
        audioStream.startSender();
        audioStream.startReceiver();

        debugInfoTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    debugInfoTextView.setText("");

                    audioStream.endCapture(new Runnable() {
                        @Override public void run() {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    transcriptionTextView.setText(audioStream.transcription);
                                }
                            });
                        }
                    });
                    transcriptionTextView.setText("Processing...");

                } else {
                    audioStream.startCapture();
                    transcriptionTextView.setText("Capturing...");
                    debugInfoTextView.setText(
                            "user = " + userId +
                            "\nQueue: " + remoteView.bitmapQueue.size() +
                            "\ndecode=" + remoteView.decodeTime.get() + " ms | display=" + remoteView.displayTime.get() + " ms | discarded=" + remoteView.discarded.get() +
                            "\nvideo sent=" + cameraPreview.outSocket.sent + " | rec=" + remoteView.inSocket.received +
                            "\naudio sent=" + audioStream.outSocket.sent + " | rec=" + audioStream.inSocket.received
                    );
                }
                return false;
            }
        });

        final Runnable disconnect = new Runnable() {
            @Override
            public void run() {
                cameraPreview.close();
                remoteView.close();
                audioStream.close();
                command.close();
                Log.close();
                activity.finish();
            }
        };

        command.setOnPeerDisconnected(disconnect);

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Exiting");
                command.sendDisconnect();
                disconnect.run();
            }
        });

    }

    public void call() {
        Log.i("Call / App started");
    }

    private void screenScale(View view, float scaleX, float scaleY) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        params.width = (int) (size.x * scaleX);
        params.height = (int) (size.y * scaleY);
        view.setLayoutParams(params);
    }

    private int dip(int size) {
        return (int) (size * densityScale + 0.5f);
    }

}
