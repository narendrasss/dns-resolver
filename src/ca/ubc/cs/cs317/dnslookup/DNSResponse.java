package ca.ubc.cs.cs317.dnslookup;

import ca.ubc.cs.cs317.dnslookup.RecordType;
import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.net.InetAddress;

/**
 * The DNSResponse class corresponds to an entire result returned by a DNS
 * response. The constructor takes in the hexadecimal byte array and provides
 * getters for the different data contained in the DNS response.
 */
public class DNSResponse {

	private DataInputStream data;
	private byte[] byteData;

	private HashMap<Integer, String> domains = new HashMap<Integer, String>();

	private int POINTER_MASK = 0b11000000;

	private int id;
	private boolean isAuthoritative = false;

	public ArrayList<ResourceRecord> answers = new ArrayList<ResourceRecord>();
	public ArrayList<ResourceRecord> compressedAnswers = new ArrayList<ResourceRecord>();
	public ArrayList<ResourceRecord> nameServers = new ArrayList<ResourceRecord>();
	public ArrayList<ResourceRecord> additionals = new ArrayList<ResourceRecord>();

	/* Constructors and getters */
	public DNSResponse(byte[] data) {
		this.data = new DataInputStream(new ByteArrayInputStream(data));
		this.byteData = data;
		if (data.length > 0) {
			try {
				parseResponse();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public ArrayList<ResourceRecord> getAnswers() {
		return this.answers;
	}

	public ArrayList<ResourceRecord> getCompressedAnswers() {
		return this.compressedAnswers;
	}

	public ArrayList<ResourceRecord> getNameServers() {
		return this.nameServers;
	}

	public ArrayList<ResourceRecord> getAdditionals() {
		return this.additionals;
	}

	public int getId() {
		return this.id;
	}

	public boolean getIsAuthoritative() {
		return this.isAuthoritative;
	}

	/**
	 * Populates the answers, compressedAnswers, nameServers and additionals fields
	 * based on the byte array that is passed to the constructor.
	 */
	private void parseResponse() throws IOException {
		id = data.readShort() & 0x00FFFF;

		int flag = data.readByte();
		isAuthoritative = ((flag >> 2) & 1) == 1;
		data.readByte(); // rest of flags

		int numQuestions = data.readShort();
		int numAnswers = data.readShort();
		int numAuths = data.readShort();
		int numAdds = data.readShort();

		for (int i = 0; i < numQuestions; i++) {
			getDomainName();
			data.readShort(); // question type
			data.readShort(); // question class
		}
		parseData(numAnswers, answers);
		compressAnswers();
		parseData(numAuths, nameServers);
		parseData(numAdds, additionals);
	}

	/**
	 * Populates the result array with data based on the byte array and
	 * the number of data blocks to expect.
	 * 
	 * @param numData	Number of data 'blocks' to parse
	 * @param result	The list to populate with resource records
	 */
	private void parseData(int numData, ArrayList<ResourceRecord> result) throws IOException {
		for (int i = 0; i < numData; i++) {
			ResourceRecord record = parseToResourceRecord();
			result.add(record);
		}
	}

	/**
	 * Parses the next block of data available into a resource record.
	 * 
	 * @return	A ResourceRecord containing information from the next
	 * 			block of data.
	 */
	private ResourceRecord parseToResourceRecord() throws IOException {
		String host = "";
		RecordType type = null;
		long ttl = 0;
		String result = "";
		InetAddress ip = null;

		host = getDomainName();

		type = RecordType.getByCode(data.readShort());
		data.readShort(); // response class
		ttl = data.readInt();

		int length = data.readShort();

		switch (type) {
			// IPv4 address
			case A:
				byte[] addr4 = new byte[length];
				for (int i = 0; i < length; i++) {
					addr4[i] = data.readByte();
				}
				ip = InetAddress.getByAddress(host, addr4);
				break;
			// IPv6 address
			case AAAA:
				byte[] addr6 = new byte[16];
				for (int i = 0; i < 16; i++) {
					addr6[i] = data.readByte();
				}
				ip = InetAddress.getByAddress(host, addr6);
				break;
			// domain names
			case NS:
			case CNAME:
				result = getDomainName();
				break;
			// if any other type, do nothing
			default:
				result = "---";
				break;
		}

		ResourceRecord record;
		if (ip != null) {
			record = new ResourceRecord(host, type, ttl, ip);
		} else {
			record = new ResourceRecord(host, type, ttl, result);
		}
		return record;
	}

	/**
	 * Parses the next block of data into a domain name.
	 * Note: Expects the next block of data to be in a format
	 * 		 where it is possible to parse a domain from.
	 * 
	 * @return	The parsed domain name.
	 * @throws IOException
	 */
	private String getDomainName() throws IOException {
		int nextByte = data.readByte();
		String result = "";

		if (isPointer(nextByte)) {
			int offset = getOffset(constructPointer(nextByte));
			if (domains.containsKey(offset)) {
				return domains.get(offset);
			} else {
				return getDomainName(offset);
			}
		}

		while (nextByte > 0 && !isPointer(nextByte)) {
			byte[] domainParts = new byte[nextByte];
			for (int j = 0; j < nextByte; j++) {
				domainParts[j] = data.readByte();
			}
			String domain = new String(domainParts);
			if (result.length() == 0) {
				result = domain;
			} else {
				result = result + "." + domain;
			}
			nextByte = data.readByte();
		}
		if (isPointer(nextByte)) {
			int pointer = constructPointer(nextByte);
			result = result + "." + getDomainName(getOffset(pointer));
		}
		return result;
	}

	/**
	 * Parses the byte array starting from offset to a domain name.
	 * Every domain name returned is placed onto the 'domains' map,
	 * with 'offset' as its key.
	 * 
	 * @param offset	The offset (from start) to start parsing.
	 * @return	The parsed domain name.
	 */
	private String getDomainName(int offset) {
		String result = "";
		int idx = offset;
		int nextByte = byteData[idx];

		if (domains.containsKey(offset)) {
			return domains.get(offset);
		}

		while (!isPointer(nextByte) && nextByte > 0) {
			byte[] domainParts = new byte[nextByte];
			idx++;
			for (int i = 0; i < nextByte; i++) {
				domainParts[i] = byteData[idx];
				idx++;
			}
			nextByte = byteData[idx];

			String domain = new String(domainParts);
			if (result.length() == 0) {
				result = domain;
			} else {
				result = result + "." + domain;
			}
		}
		if (isPointer(nextByte)) {
			int pointerPart = byteData[idx + 1];
			int pointer = constructPointer(nextByte, pointerPart);
			result = result + "." + getDomainName(getOffset(pointer));
		}

		domains.put(offset, result);
		return result;
	}

	/**
	 * Compresses the answers field, placing the result into
	 * 'compressedAnswers'. Answers are commpressed by removing
	 * redundant CNAMEs. A CNAME is redundant if:
	 * 
	 * 		- A domain name has a CNAME that has another CNAME in 
	 * 		  the same answer block. In this case, a new CNAME record 
	 * 		  is constructed with the first CNAME host and the last 
	 * 		  CNAME text result.
	 * 
	 * 		- A domain name has a CNAME that the server knows the IP
	 * 		  of (i.e. the result we want is in this answer block). 
	 * 		  In this case we have essentially found the answer, so
	 * 		  a new record is constructed with the original domain
	 * 		  and the CNAME's IP.
	 */
	private void compressAnswers() {
		for (ResourceRecord answer : answers) {
			if (answer.getType() == RecordType.CNAME) {
				ResourceRecord next = getLastCName(answer);
				if (next == answer) {
					compressedAnswers.add(answer);
				} else {
					ResourceRecord update;
					if (next.getType() == RecordType.CNAME) {
						update = new ResourceRecord(
							answer.getHostName(), next.getType(), next.getTTL(), next.getTextResult()
						);
					} else {
						update = new ResourceRecord(
							answer.getHostName(), next.getType(), next.getTTL(), next.getInetResult()
						);
					}
					compressedAnswers.add(update);
				}
			}
		}
	}

	/**
	 * Recursive function to look for the 'last' CNAME for a given
	 * record. Two possible cases:
	 * 		1. Domain A has CNAME B, B has CNAME C, C has CNAME D,
	 * 		   and so on. Last CNAME in this 'path' should be returned.
	 * 		2. Domain A has CNAME B, B is an A or AAAA resource record.
	 * 		   Here, B should be returned.
	 * 
	 * @param record	The first CNAME record to start search.
	 * @return	The last CNAME found OR an A / AAAA record. If the 
	 * 			record has no further CNAMEs, then itself is returned.
	 */
	private ResourceRecord getLastCName(ResourceRecord record) {
	    Set<ResourceRecord> set = new HashSet<ResourceRecord>();
	    set.add(record);
	    return getLastCName(record, set);
	}

	private ResourceRecord getLastCName(ResourceRecord record, Set<ResourceRecord> old) {
		if (record.getType() == RecordType.CNAME) {
			RecordType next = getRecord(record.getTextResult());
		    if (!old.contains(next) {
    			if (next != null) {
    				return getLastCName(next, old);
    			}
		    } else {
		        return record;
		    }
		}
		return record;
	}

	/**
	 * Retrieves the first record in the response's answer block
	 * that matches the given host name.
	 * 
	 * @param hostName	The host name we are looking for.
	 * @return	ResourceRecord that matches the host name, if found.
	 * 			Null if not found.
	 */
	private ResourceRecord getRecord(String hostName) {
		for (ResourceRecord answer : answers) {
			if (answer.getHostName().equals(hostName)) {
				return answer;
			}
		}
		return null;
	}

	/* Helper functions for dealing with pointers */
	private boolean isPointer(int i) {
		return (i & POINTER_MASK) == POINTER_MASK;
	}

	private int constructPointer(int firstPart) throws IOException {
		int rest = data.readByte();
		return constructPointer(firstPart, rest);
	}

	private int constructPointer(int firstPart, int secondPart) {
		return ((firstPart & 0xff) << 8 | secondPart & 0xff);
	}

	private int getOffset(int pointer) {
		return (pointer & (1 << 14) - 1) & 0b0011111111111111;
	}

}
