package ca.ubc.cs.cs317.dnslookup;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

public class DNSLookupService {

    private static final int DEFAULT_DNS_PORT = 53;
    private static final int MAX_INDIRECTION_LEVEL = 10;

    private static InetAddress rootServer;
    private static boolean verboseTracing = false;
    private static DatagramSocket socket;

    private static DNSCache cache = DNSCache.getInstance();

    private static Random random = new Random();

    /**
     * Main function, called when program is first invoked.
     *
     * @param args list of arguments specified in the command line.
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Invalid call. Usage:");
            System.err.println("\tjava -jar DNSLookupService.jar rootServer");
            System.err.println("where rootServer is the IP address (in dotted form) of the root DNS server to start the search at.");
            System.exit(1);
        }

        try {
            rootServer = InetAddress.getByName(args[0]);
            System.out.println("Root DNS server is: " + rootServer.getHostAddress());
        } catch (UnknownHostException e) {
            System.err.println("Invalid root server (" + e.getMessage() + ").");
            System.exit(1);
        }

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(5000);
        } catch (SocketException ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        Scanner in = new Scanner(System.in);
        Console console = System.console();
        do {
            // Use console if one is available, or standard input if not.
            String commandLine;
            if (console != null) {
                System.out.print("DNSLOOKUP> ");
                commandLine = console.readLine();
            } else
                try {
                    commandLine = in.nextLine();
                } catch (NoSuchElementException ex) {
                    break;
                }
            // If reached end-of-file, leave
            if (commandLine == null) break;

            // Ignore leading/trailing spaces and anything beyond a comment character
            commandLine = commandLine.trim().split("#", 2)[0];

            // If no command shown, skip to next command
            if (commandLine.trim().isEmpty()) continue;

            String[] commandArgs = commandLine.split(" ");

            if (commandArgs[0].equalsIgnoreCase("quit") ||
                    commandArgs[0].equalsIgnoreCase("exit"))
                break;
            else if (commandArgs[0].equalsIgnoreCase("server")) {
                // SERVER: Change root nameserver
                if (commandArgs.length == 2) {
                    try {
                        rootServer = InetAddress.getByName(commandArgs[1]);
                        System.out.println("Root DNS server is now: " + rootServer.getHostAddress());
                    } catch (UnknownHostException e) {
                        System.out.println("Invalid root server (" + e.getMessage() + ").");
                        continue;
                    }
                } else {
                    System.out.println("Invalid call. Format:\n\tserver IP");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("trace")) {
                // TRACE: Turn trace setting on or off
                if (commandArgs.length == 2) {
                    if (commandArgs[1].equalsIgnoreCase("on"))
                        verboseTracing = true;
                    else if (commandArgs[1].equalsIgnoreCase("off"))
                        verboseTracing = false;
                    else {
                        System.err.println("Invalid call. Format:\n\ttrace on|off");
                        continue;
                    }
                    System.out.println("Verbose tracing is now: " + (verboseTracing ? "ON" : "OFF"));
                } else {
                    System.err.println("Invalid call. Format:\n\ttrace on|off");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("lookup") ||
                    commandArgs[0].equalsIgnoreCase("l")) {
                // LOOKUP: Find and print all results associated to a name.
                RecordType type;
                if (commandArgs.length == 2)
                    type = RecordType.A;
                else if (commandArgs.length == 3)
                    try {
                        type = RecordType.valueOf(commandArgs[2].toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Invalid query type. Must be one of:\n\tA, AAAA, NS, MX, CNAME");
                        continue;
                    }
                else {
                    System.err.println("Invalid call. Format:\n\tlookup hostName [type]");
                    continue;
                }
                findAndPrintResults(commandArgs[1], type);
            } else if (commandArgs[0].equalsIgnoreCase("dump")) {
                // DUMP: Print all results still cached
                cache.forEachNode(DNSLookupService::printResults);
            } else {
                System.err.println("Invalid command. Valid commands are:");
                System.err.println("\tlookup fqdn [type]");
                System.err.println("\ttrace on|off");
                System.err.println("\tserver IP");
                System.err.println("\tdump");
                System.err.println("\tquit");
                continue;
            }

        } while (true);

        socket.close();
        System.out.println("Goodbye!");
    }

    /**
     * Finds all results for a host name and type and prints them on the standard output.
     *
     * @param hostName Fully qualified domain name of the host being searched.
     * @param type     Record type for search.
     */
    private static void findAndPrintResults(String hostName, RecordType type) {

        DNSNode node = new DNSNode(hostName, type);
        printResults(node, getResults(node, 0));
    }

    /**
     * Finds all the result for a specific node.
     *
     * @param node             Host and record type to be used for search.
     * @param indirectionLevel Control to limit the number of recursive calls due to CNAME redirection.
     *                         The initial call should be made with 0 (zero), while recursive calls for
     *                         regarding CNAME results should increment this value by 1. Once this value
     *                         reaches MAX_INDIRECTION_LEVEL, the function prints an error message and
     *                         returns an empty set.
     * @return A set of resource records corresponding to the specific query requested.
     */
    private static Set<ResourceRecord> getResults(DNSNode node, int indirectionLevel) {

        if (indirectionLevel > MAX_INDIRECTION_LEVEL) {
            System.err.println("Maximum number of indirection levels reached.");
            return Collections.emptySet();
        }
        
        // Get results starting at the root DNS server
        retrieveResultsFromServer(node, rootServer);
        
        // foundResults = did we find what we're looking for
        boolean foundResults = false;
        DNSNode cNameNode = null;
        
        // For every cached resource record for the current name (ie. "google.com")
        for (ResourceRecord result : cache.getCachedResults(node)) {
            // Check what type of record it is
            RecordType recordType = result.getType();
            if (recordType.equals(node.getType())) {
                // If it is the same type as what we're looking for, we have found what we need and don't need to look anymore
                // Note: node.getType() should be RecordType.A or RecordType.AAAA
                //       Anything else is probably an error
                foundResults = true;
            } else if (recordType.equals(RecordType.CNAME)) {
                // TODO - Since this is a CNAME, we need to build a NEW query node and start again at the root server with this
                //        We will only start again with this new node if foundResults == false, since that means we didn't find any good results
                String cName = "";
                cNameNode = new DNSNode(cName, node.getType());
            }
        }
        
        // If we did NOT find any A or AAAA records
        if (!foundResults) {
            // Should probably check in here whether or not we actually found a CNAME, but for now, let's pretend we did
            // In that case, we recurse on getResults with +1 indirectionLevel
            return getResults(cNameNode, indirectionLevel++);
        }

        return cache.getCachedResults(node);
    }

    /**
     * Retrieves DNS results from a specified DNS server. Queries are sent in iterative mode,
     * and the query is repeated with a new server if the provided one is non-authoritative.
     * Results are stored in the cache.
     *
     * @param node   Host name and record type to be used for the query.
     * @param server Address of the server to be used for the query.
     */
    private static void retrieveResultsFromServer(DNSNode node, InetAddress server) {
        try {
            // Send our query to the given DNS server
            sendToDNS(node, server, DEFAULT_DNS_PORT);
            
            // TODO - Need to get and parse response from server.
            // Example at (https://stackoverflow.com/questions/36743226/java-send-udp-packet-to-dns-server/39375234)
            
            // TODO - Need to create ResourceRecords for parsed response and add them to the cache
            
            // Now that we've added all of the responses to the cache,
            // we should look at the cache and decide what to do based on what we got back.
            Set<ResourceRecord> results = cache.getCachedResults(node);
            
            // For every result we got back
            for (ResourceRecord result : results) {
                // Check what type of record it is
                RecordType recordType = result.getType();
                if (recordType.equals(RecordType.NS)) {
                    // If it is NS, we need to get results from that server and add it to the cache
                    retrieveResultsFromServer(result.getNode(), result.getInetResult());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void sendToDNS(DNSNode node, InetAddress address, int port) throws IOException {
        ByteArrayOutputStream bytearrayOS = new ByteArrayOutputStream();
        DataOutputStream dataOS= new DataOutputStream(bytearrayOS);
        
        // Transaction ID
        // TODO - Should use random.getInt(65535) or something to produce unique IDs
        dataOS.writeShort(0xAAAA);
        // Flags
        dataOS.writeShort(0x0100);
        // # Questions
        dataOS.writeShort(0x0001);
        // # Answers (Should be 0 since we're querying)
        dataOS.writeShort(0x0000);
        // # Authorities (Should be 0 since we're querying)
        dataOS.writeShort(0x0000);
        // # Additionals (Should be 0 since we're querying)
        dataOS.writeShort(0x0000);
        
        String[] domainParts = node.getHostName().split("\\.");
        System.out.println(node.getHostName() + " has " + domainParts.length + " parts");

        for (int i = 0; i<domainParts.length; i++) {
            System.out.println("Writing: " + domainParts[i]);
            byte[] domainBytes = domainParts[i].getBytes("UTF-8");
            dataOS.writeByte(domainBytes.length);
            dataOS.write(domainBytes);
        }
        
        // No more parts
        dataOS.writeByte(0x00);
        // Type 0x01 = A (Host Request)
        dataOS.writeShort(node.getType().getCode());
        // Class 0x01 = IN
        dataOS.writeShort(0x0001);

        byte[] dnsFrame = bytearrayOS.toByteArray();

        System.out.println("Sending: " + dnsFrame.length + " bytes");
        for (int i =0; i< dnsFrame.length; i++) {
            System.out.print("0x" + String.format("%x", dnsFrame[i]) + " " );
        }

        // *** Send DNS Request Frame ***
        DatagramPacket dnsReqPacket = new DatagramPacket(dnsFrame, dnsFrame.length, address, port);
        socket.send(dnsReqPacket);

        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        System.out.println("\n\nReceived: " + packet.getLength() + " bytes");
        parseResponse(buf);

        for (int i = 0; i < packet.getLength(); i++) {
            System.out.print(" 0x" + String.format("%x", buf[i]) + " ");
        }
        System.out.println("\n");
    }

    private static void parseResponse(byte[] response) {
        
    }

    private static void verbosePrintResourceRecord(ResourceRecord record, int rtype) {
        if (verboseTracing)
            System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(),
                    record.getTTL(),
                    record.getType() == RecordType.OTHER ? rtype : record.getType(),
                    record.getTextResult());
    }

    /**
     * Prints the result of a DNS query.
     *
     * @param node    Host name and record type used for the query.
     * @param results Set of results to be printed for the node.
     */
    private static void printResults(DNSNode node, Set<ResourceRecord> results) {
        if (results.isEmpty())
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), -1, "0.0.0.0");
        for (ResourceRecord record : results) {
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), record.getTTL(), record.getTextResult());
        }
    }
}
