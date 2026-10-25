package com.hack.note4.robot;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

/** Minimal USB CDC-ACM serial sender. Opens an Arduino Leonardo (or any CDC-ACM device,
 *  or a CH340 USB-serial) as a bulk OUT stream and writes bytes to it. No dependencies. */
class UsbSerial {
    private final UsbDeviceConnection conn;
    private final UsbEndpoint out;
    private final String name;

    private UsbSerial(UsbDeviceConnection c, UsbEndpoint o, String name) {
        this.conn = c; this.out = o; this.name = name;
    }

    String name() { return name; }

    /** Find and open a CDC-ACM device (Arduino Leonardo), falling back to a CH340. Null if none. */
    static UsbSerial open(UsbManager um) {
        for (UsbDevice d : um.getDeviceList().values()) {
            for (int i = 0; i < d.getInterfaceCount(); i++) {
                UsbInterface itf = d.getInterface(i);
                if (itf.getInterfaceClass() == 2 /* CDC */) {
                    UsbDeviceConnection c = um.openDevice(d);
                    if (c == null) continue;
                    if (!c.claimInterface(itf, true)) { c.close(); continue; }
                    UsbEndpoint outEp = findBulkOut(itf);
                    if (outEp == null) { c.releaseInterface(itf); c.close(); continue; }
                    // CDC: set line coding 9600 8N1, then raise DTR+RTS so the Arduino starts reading.
                    byte[] lineCoding = { (byte) 0x80, 0x25, 0, 0, 0, 0, 8 }; // 9600 = 0x2580 LE, 8N1
                    c.controlTransfer(0x21, 0x20, 0, i, lineCoding, lineCoding.length, 200);
                    c.controlTransfer(0x21, 0x22, 3, i, null, 0, 200);
                    return new UsbSerial(c, outEp, d.getDeviceName());
                }
            }
            // Fallback: CH340-based USB-serial (vendor 0x1A86) — bulk OUT, no CDC setup.
            if (d.getVendorId() == 0x1A86) {
                for (int i = 0; i < d.getInterfaceCount(); i++) {
                    UsbInterface itf = d.getInterface(i);
                    UsbDeviceConnection c = um.openDevice(d);
                    if (c == null) continue;
                    if (!c.claimInterface(itf, true)) { c.close(); continue; }
                    UsbEndpoint outEp = findBulkOut(itf);
                    if (outEp != null) return new UsbSerial(c, outEp, d.getDeviceName());
                    c.close();
                }
            }
        }
        return null;
    }

    private static UsbEndpoint findBulkOut(UsbInterface itf) {
        for (int e = 0; e < itf.getEndpointCount(); e++) {
            UsbEndpoint ep = itf.getEndpoint(e);
            if (ep.getDirection() == UsbConstants.USB_DIR_OUT
                    && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) return ep;
        }
        return null;
    }

    /** Send bytes. Returns false if the device is gone (unplugged). */
    boolean send(byte[] data) {
        if (conn == null || out == null) return false;
        int r = conn.bulkTransfer(out, data, data.length, 200);
        return r >= 0;
    }

    void close() {
        try { if (conn != null) conn.close(); } catch (Exception e) {}
    }
}
