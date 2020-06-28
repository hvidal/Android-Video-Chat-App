/*
Copyright (c) 2014 Hugo Vidal Teixeira

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package videochat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


class Command {

    private static final int MIN_QUALITY = 15;
    private static final int MAX_QUALITY = 50;

    final AtomicInteger remoteJpegQuality = new AtomicInteger(30);
    final AtomicInteger localJpegQuality = new AtomicInteger(30);
    final AtomicBoolean isPeerConnected = new AtomicBoolean(false);
    final AtomicBoolean isActive = new AtomicBoolean(false);

    private TCPSocket socket;
    private int fpsSensor = 0;
    private Runnable onPeerDisconnected;

    Command(short userId, InetSocketAddress address) {
        socket = new TCPSocket(userId, address);
        isActive.set(true);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                socket.connect();
                try {
                    while (isActive.get()) {
                        execute(socket.readString());
                    }
                } catch(IOException e) {
                    Log.e(e);
                }
            }
        };
        new Thread(runnable).start();
    }

    private void execute(String command) {
        if (command.equals("inc"))
            add(5);
        else if (command.equals("dec"))
            add(-5);
        else if (command.startsWith("remote="))
            updateRemote(command);
        else if (command.equals("connected"))
            isPeerConnected.set(true);
        else if (command.equals("disconnected")) {
            isPeerConnected.set(false);
            if (onPeerDisconnected != null)
                onPeerDisconnected.run();
        }
    }

    private void updateRemote(String command) {
        String value = command.substring(7);
        remoteJpegQuality.set(Integer.valueOf(value));
    }

    private void add(int delta) {
        synchronized (localJpegQuality) {
            int value = localJpegQuality.get();
            value += delta;
            if (value >= MIN_QUALITY && value <= MAX_QUALITY)
                localJpegQuality.set(value);
        }
        sendCommand("remote="+localJpegQuality.get());
    }

    void reportFPS(int fps) {
        if (fps < 8)
            fpsSensor--;
        else if (fps > 12)
            fpsSensor++;

        if (fpsSensor < -5) {
            sendCommand("dec");
            fpsSensor = 0;
        } else if (fpsSensor > 5) {
            sendCommand("inc");
            fpsSensor = 0;
        }
    }

    void sendDisconnect() {
        sendCommand("disconnected");
        isPeerConnected.set(false);
    }

    private void sendCommand(String cmd) {
        try {
            socket.send(cmd);
            Log.i("Command sent: " + cmd);
        } catch (IOException e) {
            Log.e(e);
        }
    }

    void close() {
        isPeerConnected.set(false);
        isActive.set(false);
        socket.close();
    }

    public void setOnPeerDisconnected(Runnable runnable) {
        this.onPeerDisconnected = runnable;
    }
}
