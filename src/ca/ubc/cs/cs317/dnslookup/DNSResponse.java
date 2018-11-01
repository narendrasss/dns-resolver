package ca.ubc.cs.cs317.dnslookup;

import java.nio.ByteBuffer;

public class DNSResponse {

	public byte[] header;
	public ArrayList<DNSRObject> answers = new ArrayList();
	public ArrayList<DNSRObject> auths = new ArrayList();

	public DNSResponse(byte[] data) {
		parseHeader(data);
		parseAnswer(data);
		parseAuth(data);
		parseAdditionals(data);
	}

	private void parseHeader(byte[] data) {
		byte[] header;
		this.header = header;
	}

	private void parseAuth(byte[] data) {
		byte[] auths;
		this.auths = auths;
	}

	private void parseAdditionals(byte[] data) {
		byte[] additionals;
		this.additionals = additionals;
	}

	public int getNumberAuthoritatives() {
		return 0;
	}

}
