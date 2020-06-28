/*
Copyright (c) 2014 Hugo Vidal Teixeira

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package videochat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;


class TCPSocket {

    private final short userId;
    private final InetSocketAddress address;

    private boolean isConnected = false;
    private Socket socket;
    private DataOutputStream outStream;
    private DataInputStream inStream;

    TCPSocket(short userId, InetSocketAddress address) {
        this.userId = userId;
        this.address = address;
        this.socket = new Socket();
    }

    boolean isConnected() {
        return isConnected;
    }

    void connect() {
        try {
            socket.connect(address);
            outStream = new DataOutputStream(socket.getOutputStream());
            outStream.writeShort(userId);
            inStream = new DataInputStream(socket.getInputStream());
            isConnected = true;
        } catch (IOException e) {
            isConnected = false;
            e.printStackTrace();
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
        outStream.writeInt(bytes.length);
        outStream.write(bytes);
        outStream.flush();
    }

    void send(byte[] bytes, int length) throws IOException {
        outStream.writeInt(length);
        outStream.write(bytes, 0, length);
        outStream.flush();
    }

    void send(String s) throws IOException {
        outStream.writeUTF(s);
        outStream.flush();
    }

    byte[] read() throws IOException {
        int length = inStream.readInt();
        byte[] buffer = new byte[length];
        inStream.readFully(buffer, 0, length);
        return buffer;
    }

    String readString() throws IOException {
        return inStream.readUTF();
    }

    void close() {
        try {
            if (outStream != null)
                outStream.close();
        } catch (IOException e) {}
        try {
            if (inStream != null)
                inStream.close();
        } catch (IOException e) {}
        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {}
    }

}
