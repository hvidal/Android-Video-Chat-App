/*
Copyright (c) 2014 Hugo Vidal Teixeira

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package videochat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


class Log {

    private Log() {} // never

    private static int userId;
    private static TCPSocket socket;
    private static Queue<String> queue = new ConcurrentLinkedQueue<String>();
    public static void init(short userId, InetSocketAddress address) {
        Log.userId = userId;
        socket = new TCPSocket(userId, address);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                socket.connect();
                while (true) {
                    String s = queue.poll();
                    if (s != null) {
                        try {
                            socket.send(s);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
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

    static void close() {
        socket.close();
    }

    private static void print(String prefix, String m) {
        String s = "[" + prefix + ' ' + Log.userId + "] " + m;
        queue.add(s);
    }

    static void i(String s) {
        print("INFO", s);
    }

    static void e(String s) {
        print("ERROR", s);
    }

    static void e(Throwable e) {
        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));
        print("ERROR", errors.toString());
    }

}
