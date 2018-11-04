package ca.ubc.cs.cs317.dnslookup;

import ca.ubc.cs.cs317.dnslookup.RecordType;
import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.InetAddress;

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

	private void parseData(int numData, ArrayList<ResourceRecord> result) throws IOException {
		for (int i = 0; i < numData; i++) {
			ResourceRecord record = parseToResourceRecord();
			result.add(record);
		}
	}

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

	private ResourceRecord getLastCName(ResourceRecord record) {
		if (record.getType() == RecordType.CNAME) {
			ResourceRecord next = getRecord(record.getTextResult());
			if (next != null) {
				return getLastCName(next);
			}
		}
		return record;
	}

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
