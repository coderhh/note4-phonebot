package com.hack.note4.robot;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/** Streams 13 joint targets to the onboard ESP32 over Wi-Fi (UDP).
 *  PhoneBot-style: the phone is the brain, the ESP32+PCA9685 drives the servos. */
class WifiSender {
    private final String host;
    private final int port;
    private DatagramSocket sock;
    private InetAddress addr;

    WifiSender(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Send 13 joint angles (0-180). Returns false if the controller is unreachable. */
    synchronized boolean send(int[] joints) {
        if (joints.length < 13) return false;
        try {
            if (sock == null) {
                sock = new DatagramSocket();
                addr = InetAddress.getByName(host);
            }
            byte[] buf = new byte[14];
            int sum = 0;
            for (int i = 0; i < 13; i++) {
                int v = joints[i];
                if (v < 0) v = 0; if (v > 180) v = 180;
                buf[i] = (byte) v;
                sum += v;
            }
            buf[13] = (byte) (sum & 0xFF);
            sock.send(new DatagramPacket(buf, buf.length, addr, port));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void close() { try { if (sock != null) sock.close(); } catch (Exception e) {} sock = null; }
}
