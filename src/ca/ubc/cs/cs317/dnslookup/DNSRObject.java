package ca.ubc.cs.cs317.dnslookup;

public class DNSRObject {
	
	public DNSRObject(byte[] hostname, byte[] type, byte[] ip) {
		
	}
	
	public DNSRObject(byte[] hostname, byte[] type) {
		this(hostname, type, new byte[0]);
	}
	
	public String getHost() {
		return "";
	}
	
	public String getType() {
		return "";
	}
	
	public String getIP() {
		return "";
	}

}
