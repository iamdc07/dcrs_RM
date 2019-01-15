package rm;

import implementation.RmOperations;
import data.Data;
import schema.Manager;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

public class UdpProc implements Runnable {
    private Thread thread;
    private Logger logs;
    private DatagramSocket socket;
    private DatagramPacket packet;
    private RmOperations rmOps;
    private String data;

    public UdpProc(Logger logs, DatagramSocket socket, DatagramPacket packet, RmOperations rmOps) {
        this.logs = logs;
        this.socket = socket;
        this.packet = packet;
        this.rmOps = rmOps;
    }


    @Override
    public void run() {
        try {
            Data data = (Data) deserialize(packet.getData());

            byte[] responseMessage = null;
            byte[] reply = null;

            String message;

            switch (data.operationID) {
                case 0:
                    this.replicaFails(data.department);
                    break;
                case 1:
                    reply = this.startReplica(data);
                    break;
                case 9:
                    reply = this.replicaRequestData();
                    if (reply.length != 0)
                        responseMessage = reply;
                    else
                        responseMessage = serialize("No data obtained from other Replica Manager");
                    break;
                case 10:
                    reply = this.getReplicaData(data);
                    if (reply.length != 0)
                        responseMessage = reply;
                    else
                        responseMessage = serialize("No data received from Replica");
                    break;
                default:
                    responseMessage = serialize("Server Communication Error");
                    System.out.println("Operation not found!");
                    break;
            }
            DatagramPacket response = new DatagramPacket(responseMessage, responseMessage.length,
                    this.packet.getAddress(), packet.getPort());

            socket.send(response);

        } catch (SocketException | ClassNotFoundException e) {
            System.out.println("Socket: " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("IO: " + e.getMessage());
        }
    }

    // To check for software failures
    private void replicaFails(String name) throws IOException {
        this.rmOps.incrementFailureCount(name);

        if (this.rmOps.isFailureCritical(name)) {
            logs.info("Fixing the replica");
            try {
                Data data = new Data("1", 8);
                byte[] message = serialize(data);
                DatagramSocket socket = new DatagramSocket();
                InetAddress inetAddress = InetAddress.getByName("localhost");
                DatagramPacket packet = new DatagramPacket(message, message.length, inetAddress, this.rmOps.getPort(name));
                socket.send(packet);

                socket.receive(packet);
//                String response = (String) deserialize(packet.getData());
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] startReplica(Data data) {
        this.rmOps.startReplica(data.department);
        HashMap<String, HashMap<String, Integer>> courseList = new HashMap<>();
        HashMap<String, HashMap<String, ArrayList<String>>> studentList = new HashMap<>();
        byte[] byteBuffer = new byte[10000];
        try {
            // for incoming packets
            byte[] inBuffer = new byte[10000];
            DatagramPacket incoming = new DatagramPacket(inBuffer, inBuffer.length);

            // new socket to keep track of everything
            DatagramSocket socket = new DatagramSocket();

            // make the packet
            data = new Data(courseList, studentList, 10);

            // make packet and send to all other RMs
            byte[] outgoing = this.serialize(data);
            for (Manager manager : this.rmOps.getReplicaManagers()) {
                DatagramPacket datagramPacket = new DatagramPacket(outgoing, outgoing.length, manager.getIpAddress(), manager.getUdpPort());
                socket.send(datagramPacket);
            }

            socket.setSoTimeout(4000);

            while (true) {
                try {
                    socket.receive(incoming);

                    Data inData = (Data) this.deserialize(incoming.getData());

                    byte[] response = this.serialize(inData);
                    InetAddress inetAddress = InetAddress.getByName("localhost");
                    DatagramPacket dataResponse = new DatagramPacket(response, response.length, inetAddress, this.rmOps.getPort(data.department));
                    socket.send(dataResponse);
                } catch (SocketTimeoutException exception) {
                    this.logs.info("Connections to Replica Manager timed out.");
                    break;
                } catch (ClassNotFoundException exception) {
                    this.logs.warning("Could not parse incoming data from Replica Manager.\nMessage: " + exception.getMessage());
                }
            }
            socket.close();

        } catch (SocketException exception) {
            this.logs.warning("Error connecting to other RMs\nMessage: " + exception.getMessage());
        } catch (IOException exception) {
            this.logs.warning("Error encoding/parsing the packet.\nMessage: " + exception.getMessage());
        }
        return byteBuffer;
    }


    private byte[] getReplicaData(Data data) {
        byte[] byteBuffer = new byte[10000];
        try {
            // for incoming packets
            byte[] inBuffer = new byte[10000];
            DatagramPacket incoming = new DatagramPacket(inBuffer, inBuffer.length);

            // new socket to keep track of everything
            DatagramSocket socket = new DatagramSocket();

            // make packet and send to all other RMs
            byte[] outgoing = this.serialize(data);
            InetAddress inetAddress = InetAddress.getByName("localhost");
            DatagramPacket datagramPacket = new DatagramPacket(outgoing, outgoing.length, inetAddress, this.rmOps.getPort(data.department));
            socket.send(datagramPacket);


            socket.setSoTimeout(4000);

            while (true) {
                try {
                    socket.receive(incoming);

                    Data inData = (Data) this.deserialize(incoming.getData());

                    if (inData != null) {
                        return this.serialize(inData);
                    }
                } catch (SocketTimeoutException exception) {
                    this.logs.info("Connections to Replica Manager timed out.");
                    break;
                } catch (ClassNotFoundException exception) {
                    this.logs.warning("Could not parse incoming data from Replica Manager.\nMessage: " + exception.getMessage());
                }
            }
            socket.close();

        } catch (SocketException exception) {
            this.logs.warning("Error connecting to other RMs\nMessage: " + exception.getMessage());
        } catch (IOException exception) {
            this.logs.warning("Error encoding/parsing the packet.\nMessage: " + exception.getMessage());
        }
        return byteBuffer;
    }

    private byte[] replicaRequestData() {
        HashMap<String, HashMap<String, Integer>> courseList = new HashMap<>();
        HashMap<String, HashMap<String, ArrayList<String>>> studentList = new HashMap<>();
        byte[] byteBuffer = new byte[10000];
        try {
            // for incoming packets
            byte[] inBuffer = new byte[10000];
            DatagramPacket incoming = new DatagramPacket(inBuffer, inBuffer.length);

            // new socket to keep track of everything
            DatagramSocket socket = new DatagramSocket();

            // make the packet
            Data data = new Data(courseList, studentList, 10);

            // make packet and send to all other RMs
            byte[] outgoing = this.serialize(data);
            for (Manager manager : this.rmOps.getReplicaManagers()) {
                DatagramPacket datagramPacket = new DatagramPacket(outgoing, outgoing.length, manager.getIpAddress(), manager.getUdpPort());
                socket.send(datagramPacket);
            }

            socket.setSoTimeout(4000);

            while (true) {
                try {
                    socket.receive(incoming);

                    Data inData = (Data) this.deserialize(incoming.getData());

                    if (inData != null) {
                        return this.serialize(inData);
                    }
                } catch (SocketTimeoutException exception) {
                    this.logs.info("Connections to Replica Manager timed out.");
                    break;
                } catch (ClassNotFoundException exception) {
                    this.logs.warning("Could not parse incoming data from Replica Manager.\nMessage: " + exception.getMessage());
                }
            }
            socket.close();

        } catch (SocketException exception) {
            this.logs.warning("Error connecting to other RMs\nMessage: " + exception.getMessage());
        } catch (IOException exception) {
            this.logs.warning("Error encoding/parsing the packet.\nMessage: " + exception.getMessage());
        }
        return byteBuffer;
    }

    public void start() {
        // One in coming connection. Forking a thread.
        if (thread == null) {
            thread = new Thread(this, "Udp Process");
            thread.start();
        }
    }

    public static byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            try (ObjectOutputStream o = new ObjectOutputStream(b)) {
                o.writeObject(obj);
            }
            return b.toByteArray();
        }
    }

    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream b = new ByteArrayInputStream(bytes)) {
            try (ObjectInputStream o = new ObjectInputStream(b)) {
                return o.readObject();
            }
        }
    }

//    Operation
//    0 - Software Failure
//    1- Crash Failure
//    9 - replicaRequestData
}
