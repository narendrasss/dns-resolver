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

		System.out.println(getDomainName())
		parseAnswers();
	}

	private void parseAnswers() {
		for (int i = 0; i < numAnswers; i++) {

		}
	}

	private String getDomainName(int offset) {
		String result = "";
		byte nextByte;
		int idx = offset;

		while ((nextByte = byteData[idx]) > 0) {
			byte[] domainParts = new byte[nextByte];
			for (int i = 0; i < nextByte; i++) {
				domainParts[i] = byteData[idx + i];
			}
			idx += nextByte;

			String part = new String(domainParts);
			if (result.length() == 0) {
				result = part;
			} else {
				result = result + "." + part;
			}
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
