import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Small program to discover ASUS routers within the network.
 */
public class Discover {
    // Constants from user/shared/include/ibox.h.
    private final static short IBOX_SRV_PORT = 9999;

    private final static byte NET_SERVICE_ID_BASE = 0x0a;
    private final static byte NET_SERVICE_ID_LPT_EMU = NET_SERVICE_ID_BASE + 1;
    private final static byte NET_SERVICE_ID_IBOX_INFO = NET_SERVICE_ID_BASE + 2;

    private final static byte NET_PACKET_TYPE_BASE = 0x14;
    private final static byte NET_PACKET_TYPE_CMD = NET_PACKET_TYPE_BASE + 1;
    private final static byte NET_PACKET_TYPE_RES = NET_PACKET_TYPE_BASE + 2;

    private final static short NET_CMD_ID_BASE = 0x1e;
    private final static short NET_CMD_ID_GETINFO = NET_CMD_ID_BASE + 1;
    private final static short NET_CMD_ID_GETINFO_EX = NET_CMD_ID_BASE + 2;
    private final static short NET_CMD_ID_GETINFO_SITES = NET_CMD_ID_BASE + 3;
    private final static short NET_CMD_ID_SETINFO = NET_CMD_ID_BASE + 4;
    private final static short NET_CMD_ID_SETSYSTEM = NET_CMD_ID_BASE + 5;
    private final static short NET_CMD_ID_GETINFO_PROF = NET_CMD_ID_BASE + 6;
    private final static short NET_CMD_ID_SETINFO_PROF = NET_CMD_ID_BASE + 7;
    private final static short NET_CMD_ID_CHECK_PASS = NET_CMD_ID_BASE + 8;
    private final static short NET_CMD_ID_SETKEY_EX = NET_CMD_ID_BASE + 9;
    private final static short NET_CMD_ID_QUICKGW_EX = NET_CMD_ID_BASE + 10;
    private final static short NET_CMD_ID_EZPROBE = NET_CMD_ID_BASE + 11;

    private final static short NET_CMD_ID_MANU_BASE = 0x32;
    private final static short NET_CMD_ID_MANU_CMD = NET_CMD_ID_MANU_BASE + 1;
    private final static short NET_CMD_ID_GETINFO_MANU = NET_CMD_ID_MANU_BASE + 2;
    private final static short NET_CMD_ID_GETINFO_EX2 = NET_CMD_ID_MANU_BASE + 3;
    private final static short NET_CMD_ID_MAXIMUM = NET_CMD_ID_BASE + 4;

    private static DatagramSocket createDatagramSocket() {
        try {
            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(IBOX_SRV_PORT));
            return socket;

        } catch (SocketException e) {
            System.out.println("Unable to create a datagram socket.");
            e.printStackTrace();
        }

        return null;
    }

    private static List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> broadcastAddresses = new ArrayList<InetAddress>();
        Enumeration<NetworkInterface> interfaces = null;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            System.out.println("Unable to retrieve list of network interfaces.");
            e.printStackTrace();
        }

        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            try {
                if (networkInterface.isLoopback()) {
                    continue;
                }

                for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = address.getBroadcast();
                    if (broadcast == null) {
                        continue;
                    }

                    broadcastAddresses.add(broadcast);
                }

            } catch (SocketException e) {
                System.out.println("Unable to determine if interface is a loopback interface.");
                e.printStackTrace();
            }
        }

        return broadcastAddresses;
    }

    private static void sendInfoQuery(InetAddress address) {
        // Construct the buffer.
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.put(NET_SERVICE_ID_IBOX_INFO)
                .put(NET_PACKET_TYPE_CMD)
                .putShort(NET_CMD_ID_GETINFO);

        // Send the buffer using a datagram.
        DatagramPacket packet = new DatagramPacket(
                buffer.array(),
                buffer.limit(),
                address, IBOX_SRV_PORT);

        DatagramSocket socket = createDatagramSocket();
        try {
            socket.setBroadcast(true);
            socket.send(packet);
        } catch (SocketException e) {
            System.out.println("Unable to set datagram socket to broadcasting.");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Unable to send datagram packet.");
            e.printStackTrace();
        }


    }

    public static void main(String[] args) {
        DatagramSocket socket = createDatagramSocket();
        if (socket == null) {
            return;
        }

        List<InetAddress> broadcastAddresses = getBroadcastAddresses();
        for (InetAddress address : broadcastAddresses) {
            sendInfoQuery(address);
        }

        // Receive information query.
        // Wait 5 seconds.
        long start = System.currentTimeMillis();
        long end = start + 5 * 1000;
        while(System.currentTimeMillis() < end) {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(512);
                DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.limit());
                socket.receive(packet);

                if (buffer.get() == NET_SERVICE_ID_IBOX_INFO &&
                        buffer.get() == NET_PACKET_TYPE_RES &&
                        buffer.getShort() == NET_CMD_ID_GETINFO) {
                    // Remove unneeded data.
                    buffer.getInt(); // Info
                    buffer.position(buffer.position() + 128); // PrinterInfo

                    // SSID
                    // TODO: I have not tested this with non-ASCII SSIDs.
                    byte[] ssidBytes = new byte[32];
                    buffer.get(ssidBytes);
                    String ssid = new String(ssidBytes, "US-ASCII");
                    ssid = ssid.trim();

                    // NetMask
                    byte[] netMaskBytes = new byte[32];
                    buffer.get(netMaskBytes);
                    String netMask = new String(netMaskBytes, "US-ASCII");

                    // ProductID
                    byte[] productIdBytes = new byte[32];
                    buffer.get(productIdBytes);
                    String productId = new String(productIdBytes, "US-ASCII");

                    // FirmwareVersion
                    byte[] firmwareVersionBytes = new byte[16];
                    buffer.get(firmwareVersionBytes);
                    String firmwareVersion = new String(firmwareVersionBytes, "US-ASCII");

                    // OperationMode
                    byte operationMode = buffer.get();

                    // MacAddress
                    byte[] macAddress = new byte[6];
                    buffer.get(macAddress);
                    StringBuilder macAddressBuilder = new StringBuilder(18);
                    for (byte macAddressByte : macAddress) {
                        if (macAddressBuilder.length() > 0) {
                            macAddressBuilder.append(':');
                        }

                        macAddressBuilder.append(String.format("%02x", macAddressByte));
                    }

                    // Regulation
                    byte regulation = buffer.get();

                    // Print information.
                    System.out.println(ssid);
                    System.out.println(new String(new char[ssid.length()]).replace("\0", "="));
                    System.out.println("IP Address: " + packet.getAddress().getHostAddress());
                    System.out.println("Subnet Mask: " + netMask);
                    System.out.println("Product ID: " + productId);
                    System.out.println("Firmware Version: " + firmwareVersion);
                    System.out.println("Operation Mode: " + operationMode);
                    System.out.println("MAC address: " + macAddressBuilder.toString());
                    System.out.println("Regulation: " + regulation);
                }

            } catch (IOException e) {
                System.out.println("Unable to receive datagram packet.");
                e.printStackTrace();
            }
        }
    }
}
