import fschmidt.util.java.IoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;


public class ServerUDP {

	//private static final String IP = "192.168.0.2";
	private static final String IP = "198.206.133.60";

	private static final Executor EXECUTOR = Executors.newCachedThreadPool();

	private static final InetSocketAddress VIDEO_IN = new InetSocketAddress(IP, 8090);
	private static final InetSocketAddress VIDEO_OUT = new InetSocketAddress(IP, 8091);
	private static final InetSocketAddress AUDIO_IN = new InetSocketAddress(IP, 8092);
	private static final InetSocketAddress AUDIO_OUT = new InetSocketAddress(IP, 8093);

	private static final Map<Short, UserAddress> USER_ADDRESSES = new TreeMap<Short, UserAddress>();

	private static class UserAddress {
		SocketAddress videoIn;
		SocketAddress videoOut;
		SocketAddress audioIn;
		SocketAddress audioOut;
	}

	private static synchronized UserAddress getUserAddress(short userId) {
		UserAddress userAddress = USER_ADDRESSES.get(userId);
		if (userAddress == null) {
			userAddress = new UserAddress();
			USER_ADDRESSES.put(userId, userAddress);
		}
		return userAddress;
	}

	private static synchronized UserAddress getPeerAddress(short userId) {
		for (short id : USER_ADDRESSES.keySet()) {
			if (id != userId)
				return getUserAddress(id);
		}
		return null;
	}

	private static synchronized short getUserId(SocketAddress socketAddress) {
		String addr = socketAddress.toString();
		for (Map.Entry<Short, UserAddress> entry : USER_ADDRESSES.entrySet()) {
			UserAddress userAddress = entry.getValue();
			boolean vin = userAddress.videoIn != null && userAddress.videoIn.toString().equals(addr);
			boolean vout = userAddress.videoOut != null && userAddress.videoOut.toString().equals(addr);
			boolean ain = userAddress.audioIn != null && userAddress.audioIn.toString().equals(addr);
			boolean aout = userAddress.audioOut != null && userAddress.audioOut.toString().equals(addr);
			if (vin || vout || ain || aout)
				return entry.getKey();
		}
		return -1;
	}

	public static void main(String[] args) throws IOException
	{
		initVideoStreaming();
		initAudioStreaming();
		initRemoteCommands();
		initRemoteLogging();
	}

	private static void initVideoStreaming() throws SocketException
	{
		final DatagramSocket socketVideoIn = new DatagramSocket(VIDEO_IN);
		final DatagramSocket socketVideoOut = new DatagramSocket(VIDEO_OUT);

		Runnable videoReceiver = new Runnable() {
			@Override
			public void run() {
				try {
					byte[] buffer = new byte[40 * 1024];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

					while (true) {
						socketVideoIn.receive(packet);
						if (packet.getLength() == 2) {
							byte[] data = packet.getData();
							int short0 = data[0] << 8;
							int short1 = data[1];
							short userId = (short) (short0 + short1);
							System.out.println("V-INPUT FROM " + userId + " | " + packet.getSocketAddress().toString());
							UserAddress userAddress = getUserAddress(userId);
							userAddress.videoIn = packet.getSocketAddress();
						} else {
							SocketAddress addr = packet.getSocketAddress();
							short userId = getUserId(addr);
							if (userId > 0) {
								UserAddress peerAddress = getPeerAddress(userId);
								if (peerAddress != null && peerAddress.videoOut != null) {
									packet.setSocketAddress(peerAddress.videoOut);
									socketVideoOut.send(packet);
								}
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		EXECUTOR.execute(videoReceiver);

		Runnable videoSender = new Runnable() {
			@Override
			public void run() {
				try {
					byte[] buffer = new byte[3];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					while (true) {
						socketVideoOut.receive(packet);
						byte[] data = packet.getData();
						int short0 = data[0] << 8;
						int short1 = data[1];
						short userId = (short) (short0 + short1);
						System.out.println("V-OUTPUT TO " + userId + " | " + packet.getAddress().getHostAddress() + ':' + packet.getPort());
						UserAddress userAddress = getUserAddress(userId);
						userAddress.videoOut = packet.getSocketAddress();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		EXECUTOR.execute(videoSender);
	}

	private static void initAudioStreaming() throws SocketException
	{
		final DatagramSocket socketAudioIn = new DatagramSocket(AUDIO_IN);
		final DatagramSocket socketAudioOut = new DatagramSocket(AUDIO_OUT);

		Runnable audioReceiver = new Runnable() {
			@Override
			public void run() {
				try {
					byte[] buffer = new byte[40 * 1024];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

					while (true) {
						socketAudioIn.receive(packet);
						if (packet.getLength() == 2) {
							byte[] data = packet.getData();
							int short0 = data[0] << 8;
							int short1 = data[1];
							short userId = (short) (short0 + short1);
							System.out.println("A-INPUT FROM " + userId + " | " + packet.getSocketAddress().toString());
							UserAddress userAddress = getUserAddress(userId);
							userAddress.audioIn = packet.getSocketAddress();
						} else {
							SocketAddress addr = packet.getSocketAddress();
							short userId = getUserId(addr);
							if (userId > 0) {
								UserAddress peerAddress = getPeerAddress(userId);
								if (peerAddress != null && peerAddress.audioOut != null) {
									packet.setSocketAddress(peerAddress.audioOut);
									socketAudioOut.send(packet);
								}
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		EXECUTOR.execute(audioReceiver);

		Runnable audioSender = new Runnable() {
			@Override
			public void run() {
				try {
					byte[] buffer = new byte[3];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					while (true) {
						socketAudioOut.receive(packet);
						byte[] data = packet.getData();
						int short0 = data[0] << 8;
						int short1 = data[1];
						short userId = (short) (short0 + short1);
						System.out.println("A-OUTPUT TO " + userId + " | " + packet.getAddress().getHostAddress() + ':' + packet.getPort());
						UserAddress userAddress = getUserAddress(userId);
						userAddress.audioOut = packet.getSocketAddress();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		EXECUTOR.execute(audioSender);
	}

	private static final AtomicReference<Socket> peer = new AtomicReference<Socket>();

	private static void initRemoteCommands() {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					ServerSocket server = new ServerSocket();
					server.bind(new InetSocketAddress(IP, 8098));
					while (true) {
						final Socket socket = server.accept();
						DataInputStream in = new DataInputStream(socket.getInputStream());
						in.readShort();
						if (peer.get() == null) {
							peer.set(socket);
						} else {
							System.out.println("Connecting peers for command sockets");
							Socket socket2 = peer.getAndSet(null);

							InputStream in1 = socket.getInputStream();
							DataOutputStream out1 = new DataOutputStream(socket.getOutputStream());

							InputStream in2 = socket2.getInputStream();
							DataOutputStream out2 = new DataOutputStream(socket2.getOutputStream());

							out1.writeUTF("connected");
							out2.writeUTF("connected");

							forward(in1, out2);
							forward(in2, out1);
						}
					}
				} catch (IOException e) {
					peer.set(null);
					e.printStackTrace();
				}
			}
		};
		EXECUTOR.execute(runnable);
	}

	private static void forward(final InputStream in, final OutputStream out) {
		Runnable runnable = new Runnable() {
			public void run() {
				try {
					while (true) {
						IoUtils.copyAll(in, out);
					}
				} catch (IOException e) {
					peer.set(null);
					System.out.println("Connection lost, exiting");
					try {
						in.close();
					} catch (IOException e1) {}
					try {
						out.close();
					} catch (IOException e1) {}
				}
			}
		};
		EXECUTOR.execute(runnable);
	}

	private static void initRemoteLogging() {
		try {
			ServerSocket server = new ServerSocket();
			server.bind(new InetSocketAddress(IP, 8099));
			while (true) {
				final Socket s = server.accept();
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						try {
							DataInputStream is = new DataInputStream(s.getInputStream());
							short userId = is.readShort();
							System.out.println("Logging connection for " + userId);
							while (true) {
								String s = is.readUTF();
								System.out.println(s);
							}
						} catch (EOFException e) {
							System.out.println("Closed connection / EOF");
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				};
				EXECUTOR.execute(runnable);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

