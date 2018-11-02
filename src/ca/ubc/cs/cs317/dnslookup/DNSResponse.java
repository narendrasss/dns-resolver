package ca.ubc.cs.cs317.dnslookup;

import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class DNSResponse {

	private DataInputStream data;
	private byte[] byteData;

	private int id;
	private int numAnswers;
	private int numAuths;
	private int numAdds;

	public ArrayList<ResourceRecord> answers = new ArrayList<ResourceRecord>();
	public ArrayList<ResourceRecord> nameServers = new ArrayList<ResourceRecord>();

	public DNSResponse(byte[] data) {
		this.data = new DataInputStream(new ByteArrayInputStream(data));
		this.byteData = data;
		try {
			parseResponse();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseResponse() throws IOException {
		id = data.readShort(); // transaction id
		data.readShort(); // flags
		int numQuestions = data.readShort();

		numAnswers = data.readShort();
		numAuths = data.readShort();
		numAdds = data.readShort();

		for (int i = 0; i < numQuestions; i++) {
			int nextByte;
			while ((nextByte = data.readByte()) > 0) {
				byte[] domainParts = new byte[nextByte];
				for (int j = 0; j < nextByte; j++) {
					domainParts[j] = data.readByte();
				}
			}
			data.readShort(); // question type
			data.readShort(); // question class
		}
		parseAnswers();
	}

	private void parseAnswers() {
		for (int i = 0; i < numAnswers; i++) {

		}
	}

	private String getDomainName(int offset) {
		String result = "";
		int pointer = -64;
		int idx = offset;
		int nextByte = byteData[idx];

		while (nextByte != pointer && nextByte > 0) {
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
		if (nextByte == pointer) {
			int nextOffset = byteData[idx + 1];
			result = result + "." + getDomainName(nextOffset);
		}
		return result;
	}

	public ArrayList<ResourceRecord> getAnswers() {
		return this.answers;
	}

	public ArrayList<ResourceRecord> getNameServers() {
		return this.nameServers;
	}

	public int getId() {
		return this.id;
	}

}
