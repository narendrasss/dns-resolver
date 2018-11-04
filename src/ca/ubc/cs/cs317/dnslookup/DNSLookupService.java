package ca.ubc.cs.cs317.dnslookup;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.*;

public class DNSLookupService {

    private static final int DEFAULT_DNS_PORT = 53;
    private static final int MAX_INDIRECTION_LEVEL = 10;

    private static InetAddress rootServer;
    // TODO: Change this back to false
    private static boolean verboseTracing = true;
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
            System.err.println(
                    "where rootServer is the IP address (in dotted form) of the root DNS server to start the search at.");
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
            if (commandLine == null)
                break;

            // Ignore leading/trailing spaces and anything beyond a comment character
            commandLine = commandLine.trim().split("#", 2)[0];

            // If no command shown, skip to next command
            if (commandLine.trim().isEmpty())
                continue;

            String[] commandArgs = commandLine.split(" ");

            if (commandArgs[0].equalsIgnoreCase("quit") || commandArgs[0].equalsIgnoreCase("exit"))
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
            } else if (commandArgs[0].equalsIgnoreCase("lookup") || commandArgs[0].equalsIgnoreCase("l")) {
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
     * Finds all results for a host name and type and prints them on the standard
     * output.
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
     * @param indirectionLevel Control to limit the number of recursive calls due to
     *                         CNAME redirection. The initial call should be made
     *                         with 0 (zero), while recursive calls for regarding
     *                         CNAME results should increment this value by 1. Once
     *                         this value reaches MAX_INDIRECTION_LEVEL, the
     *                         function prints an error message and returns an empty
     *                         set.
     * @return A set of resource records corresponding to the specific query
     *         requested.
     */
    private static Set<ResourceRecord> getResults(DNSNode node, int indirectionLevel) {
        if (indirectionLevel > MAX_INDIRECTION_LEVEL) {
            System.err.println("Maximum number of indirection levels reached.");
            return Collections.emptySet();
        }

        // Look in cache if EXACT search was done before
        Set<ResourceRecord> cached = cache.getCachedResults(node);
        if (cached.size() > 0) return cached;

        // Look in cache if name has CNAME
        DNSNode alt = new DNSNode(node.getHostName(), RecordType.CNAME);
        Set<ResourceRecord> altNames = cache.getCachedResults(alt);
        // if has a CNAME, perform CNAME search
        if (altNames.size() > 0) {
            for (ResourceRecord name : altNames) {
                DNSNode newNode = new DNSNode(name.getTextResult(), node.getType());
                Set<ResourceRecord> altResults = getResults(newNode, 0);
                for (ResourceRecord result : altResults) {
                    ResourceRecord update = new ResourceRecord(
                        node.getHostName(), node.getType(), result.getTTL(), result.getInetResult()
                    );
                    cache.addResult(update);
                }
                return cache.getCachedResults(node);
            }
        }

        // Get results starting at the root DNS server
        retrieveResultsFromServer(node, rootServer);

        // Check if there are answers
        Set<ResourceRecord> results = cache.getCachedResults(node);
        if (results.size() > 0) return results;

        // Otherwise there might be CNAMEs
        DNSNode cNameNode = new DNSNode(node.getHostName(), RecordType.CNAME);
        Set<ResourceRecord> cNames = cache.getCachedResults(cNameNode);

        for (ResourceRecord cNameRecord : cNames) {
            // check cache if it has more CNAMEs
            DNSNode cName = new DNSNode(cNameRecord.getTextResult(), node.getType());
            Set<ResourceRecord> cNameResults = getResults(cName, indirectionLevel++);
            for (ResourceRecord record : cNameResults) {
                ResourceRecord update = new ResourceRecord(
                    node.getHostName(), node.getType(), record.getTTL(), record.getInetResult()
                );
                cache.addResult(update);
            }
            break;
        }

        return cache.getCachedResults(node);
    }

    /**
     * Retrieves DNS results from a specified DNS server. Queries are sent in
     * iterative mode, and the query is repeated with a new server if the provided
     * one is non-authoritative. Results are stored in the cache.
     *
     * @param node   Host name and record type to be used for the query.
     * @param server Address of the server to be used for the query.
     */
    private static void retrieveResultsFromServer(DNSNode node, InetAddress server) {
        try {
            // Send our query to the given DNS server
            DNSResponse parsedResponse = new DNSResponse(
                sendToDNS(node, server, DEFAULT_DNS_PORT, false)
            );

            ArrayList<ResourceRecord> answers = parsedResponse.getAnswers();
            ArrayList<ResourceRecord> nameServers = parsedResponse.getNameServers();
            ArrayList<ResourceRecord> additionals = parsedResponse.getAdditionals();
            boolean isAuthoritative = parsedResponse.getIsAuthoritative();

            if (verboseTracing) {
                System.out.print("Response ID: " + parsedResponse.getId());
                System.out.print(" Authoritative: " + isAuthoritative + "\n");
                System.out.println("  Answers (" + parsedResponse.getAnswers().size() + ")");
                for (ResourceRecord r : answers) {
                    verbosePrintResourceRecord(r, r.getType().getCode());
                }
                System.out.println("  Nameservers (" + parsedResponse.getNameServers().size() + ")");
                for (ResourceRecord r : nameServers) {
                    verbosePrintResourceRecord(r, r.getType().getCode());
                }
                System.out.println("  Additional Information (" + parsedResponse.getAdditionals().size() + ")");
                for (ResourceRecord r : additionals) {
                    verbosePrintResourceRecord(r, r.getType().getCode());
                }
            }

            addAllToCache(answers);
            addAllToCache(parsedResponse.getCompressedAnswers());
            addAllToCache(nameServers);
            addAllToCache(additionals);

            if (!isAuthoritative) {
                if (nameServers.size() > 0) {
                    ResourceRecord ns = nameServers.get(0);
                    String next = ns.getTextResult();
                    InetAddress ip = getAddress(next, additionals);
                    if (ip != null) {
                        retrieveResultsFromServer(node, ip);
                    } else /* need to look for ip of name server */ {
                        DNSNode nameServer = new DNSNode(next, RecordType.A);
                        Set<ResourceRecord> results = getResults(nameServer, 0);
                        for (ResourceRecord record : results) {
                            retrieveResultsFromServer(node, record.getInetResult());
                            break;
                        }
                    }
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static InetAddress getAddress(String next, ArrayList<ResourceRecord> database) {
        for (ResourceRecord record : database) {
            if (record.getHostName().equals(next) && record.getType() == RecordType.A) {
                return record.getInetResult();
            }
        }
        return null;
    }

    private static void addAllToCache(ArrayList<ResourceRecord> input) {
        for (ResourceRecord r : input) {
            cache.addResult(r);
        }
    }

    private static byte[] sendToDNS(DNSNode node, InetAddress address, int port, boolean wasSent) throws IOException {
        ByteArrayOutputStream bytearrayOS = new ByteArrayOutputStream();
        DataOutputStream dataOS = new DataOutputStream(bytearrayOS);

        // Transaction ID
        int id = random.nextInt(65535) & 0x00FFFF;
        dataOS.writeShort(id);
        // Flags
        dataOS.writeShort(0x0000);
        // # Questions
        dataOS.writeShort(0x0001);
        // # Answers (Should be 0 since we're querying)
        dataOS.writeShort(0x0000);
        // # Authorities (Should be 0 since we're querying)
        dataOS.writeShort(0x0000);
        // # Additionals (Should be 0 since we're querying)
        dataOS.writeShort(0x0000);

        String[] domainParts = node.getHostName().split("\\.");
        //System.out.println(node.getHostName() + " has " + domainParts.length + " parts");

        for (int i = 0; i < domainParts.length; i++) {
            //System.out.println("Writing: " + domainParts[i]);
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

        /*System.out.println("Sending: " + dnsFrame.length + " bytes");
        for (int i = 0; i < dnsFrame.length; i++) {
            System.out.print("0x" + String.format("%x", dnsFrame[i]) + " ");
        }*/

        if (verboseTracing) {
            System.out.print("\n\nQuery ID: " + id + " " + node.getHostName() + " " + node.getType() + " --> " + address.getHostAddress() + "\n");
        }

        // *** Send DNS Request Frame ***
        DatagramPacket dnsReqPacket = new DatagramPacket(dnsFrame, dnsFrame.length, address, port);
        socket.send(dnsReqPacket);

        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        try {
            socket.receive(packet);
        } catch (SocketTimeoutException se) {
            if (!wasSent) {
                sendToDNS(node, address, port, true);
            } else {
                return buf;
            }
        }

        /*
        System.out.println("\nReceived: " + packet.getLength() + " bytes");

        for (int i = 0; i < packet.getLength(); i++) {
            if (String.format("%x", buf[i]).length() == 1) {
                System.out.print(" 0" + String.format("%x", buf[i]) + " ");
            } else {
                System.out.print(" " + String.format("%x", buf[i]) + " ");
            }
            if ((i + 1) % 8 == 0) {
                if ((i + 1) % 16 == 0) {
                    System.out.print("\n");
                } else {
                    System.out.print("  ");
                }
            }
        }
        System.out.println("\n");
        */
        

        return buf;
    }

    private static void verbosePrintResourceRecord(ResourceRecord record, int rtype) {
        if (verboseTracing)
            System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(), record.getTTL(),
                    record.getType() == RecordType.OTHER ? rtype : record.getType(), record.getTextResult());
    }

    /**
     * Prints the result of a DNS query.
     *
     * @param node    Host name and record type used for the query.
     * @param results Set of results to be printed for the node.
     */
    private static void printResults(DNSNode node, Set<ResourceRecord> results) {
        if (results.isEmpty())
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(), node.getType(), -1, "0.0.0.0");
        for (ResourceRecord record : results) {
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(), node.getType(), record.getTTL(),
                    record.getTextResult());
        }
    }
}
