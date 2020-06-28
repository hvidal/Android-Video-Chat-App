/*
Copyright (c) 2014 Hugo Vidal Teixeira

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package videochat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;


class UDPSocket {

    private final short userId;
    private final InetSocketAddress address;

    private DatagramSocket socket;
    private boolean isConnected = false;

    private final Object lock = new Object();
    private byte[] nextBytes;
    private int nextLength;

    public AtomicInteger sent = new AtomicInteger(0);
    public AtomicInteger received = new AtomicInteger(0);

    UDPSocket(short userId, InetSocketAddress address, boolean isOutput) {
        this.userId = userId;
        this.address = address;

        if (isOutput) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        byte[] next;
                        int length;
                        synchronized (lock) {
                            next = nextBytes;
                            length = nextLength;
                            nextBytes = null;
                        }
                        if (next != null) {
                            try {
                                socket.send(new DatagramPacket(next, length, UDPSocket.this.address));
                                sent.incrementAndGet();
                            } catch (IOException e) {
                                Log.e(e);
                            }
                        } else
                            sleep(50);
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
    }

    boolean isConnected() {
        return isConnected;
    }

    void connect() {
        try {
            socket = new DatagramSocket();

            byte[] info = new byte[]{ (byte)((userId >> 8) & 0xFF), (byte)(userId & 0xFF) };
            socket.send(new DatagramPacket(info, info.length, address));

            isConnected = true;
        } catch (IOException e) {
            isConnected = false;
            Log.e(e);
        }
    }

    void connectAsync() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                connect();
            }
        };
        Thread t = new Thread(runnable);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {}
    }

    void send(byte[] bytes) throws IOException {
       send(bytes, bytes.length);
    }

    void send(byte[] bytes, int length) throws IOException {
        if (isConnected) {
            synchronized (lock) {
                nextBytes = bytes;
                nextLength = length;
            }
        }
    }

    DatagramPacket read() throws IOException {
        return read(30);
    }

    DatagramPacket read(int sizeInBytes) throws IOException {
        final byte[] buffer = new byte[1024 * sizeInBytes];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        received.incrementAndGet();
        return packet;
    }

    void close() {
        isConnected = false;
        socket.disconnect();
        socket.close();
    }
}
