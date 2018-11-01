package ca.ubc.cs.cs317.dnslookup;

import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class DNSResponse {

	private byte[] header;
	private DataInputStream data;
	private int numAnswers;
	private int numAuths;
	private int numAdds;

	public ArrayList<DNSRObject> responses = new ArrayList();

	public DNSResponse(byte[] data) {
		this.data = new DataInputStream(new ByteArrayInputStream(data));
		try {
			parseResponse();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseResponse() throws IOException {
		data.readShort(); // transaction id
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

	}

	public ArrayList<DNSRObject> getResponses() {
		return this.responses;
	}

}
