package ca.ubc.cs.cs317.dnslookup;

import java.nio.ByteBuffer;

public class DNSResponse {

	private byte[] header;
	public ArrayList<DNSRObject> responses = new ArrayList();

	public DNSResponse(byte[] data) {
		parseHeader(data);
		parseAnswer(data);
		parseAuth(data);
		parseAdditionals(data);
	}

	private void parseHeader(byte[] data) {
		byte[] header;
	}

	private void parseAuth(byte[] data) {
		byte[] auths;
	}

	private void parseAdditionals(byte[] data) {
		byte[] additionals;
	}

	public ArrayList<DNSRObject> getResponses() {
		return this.responses;
	}

}
