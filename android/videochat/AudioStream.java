/*
Copyright (c) 2014 Hugo Vidal Teixeira

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package videochat;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


class AudioStream {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int MIN_BUF_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT);
    private static final int BUF_SIZE = 15 * MIN_BUF_SIZE;

    final UDPSocket inSocket;
    final UDPSocket outSocket;
    final Command command;
    private AudioRecord recorder;

    private AtomicBoolean isCapturing = new AtomicBoolean(false);
    private List<byte[]> voiceBytes = new ArrayList<byte[]>();
    String transcription = "";

    AudioStream(short userId, InetSocketAddress inAddress, InetSocketAddress outAddress, Command command) {
        this.inSocket = new UDPSocket(userId, inAddress, false);
        this.outSocket = new UDPSocket(userId, outAddress, true);
        this.command = command;
    }

    void startSender() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, FORMAT, BUF_SIZE);
                recorder.startRecording();

                outSocket.connect();

                while (command.isActive.get()) {
                    if (!command.isPeerConnected.get()) {
                        sleep(200);
                        continue;
                    }
                    try {
                        final byte[] buffer = new byte[2 * MIN_BUF_SIZE];
                        final int length = recorder.read(buffer, 0, buffer.length);
                        outSocket.send(buffer, length);
                        if (isCapturing.get()) {
                            final byte[] captured = new byte[length];
                            System.arraycopy(buffer, 0, captured, 0, length);
                            voiceBytes.add(captured);
                        }
                    } catch (IOException e) {
                        Log.e(e);
                    }
                }
            }

            private void sleep(long time) {
                try {
                    Thread.sleep(time);
                } catch (InterruptedException e) {}
            }
        };
        new Thread(runnable).start();
    }

    void startReceiver() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, CHANNEL, FORMAT, BUF_SIZE, AudioTrack.MODE_STREAM);
                audioTrack.play();

                inSocket.connect();
                while (command.isActive.get()) {
                    if (!command.isPeerConnected.get()) {
                        sleep(200);
                        continue;
                    }
                    try {
                        DatagramPacket packet = inSocket.read(10);
                        audioTrack.write(packet.getData(), 0, packet.getLength());
                    } catch (IOException e) {
                        Log.e(e);
                    }
                }
            }

            private void sleep(long time) {
                try {
                    Thread.sleep(time);
                } catch (InterruptedException e) {}
            }
        };
        new Thread(runnable).start();
    }

    synchronized void startCapture() {
        if (isCapturing.get())
            return;
        Log.i("Starting capture");
        isCapturing.set(true);
        voiceBytes.clear();
    }

    synchronized void endCapture(final Runnable runnable) {
        if (!voiceBytes.isEmpty()) {
            int total = 0;
            for (byte[] b : voiceBytes) {
                total += b.length;
            }
            int count = 0;
            byte[] bytes = new byte[total];
            for (byte[] b : voiceBytes) {
                System.arraycopy(b, 0, bytes, count, b.length);
                count += b.length;
            }
            voiceBytes.clear();

            Transcription.ResponseListener listener = new Transcription.ResponseListener() {
                @Override
                public void response(String value) {
                    transcription = value;
                    runnable.run();
                    Log.i("Capture finished: " + value);
                }
            };
            Transcription.request(bytes, listener);
        }
        isCapturing.set(false);
    }

    void close() {
        inSocket.close();
        outSocket.close();
    }
}
