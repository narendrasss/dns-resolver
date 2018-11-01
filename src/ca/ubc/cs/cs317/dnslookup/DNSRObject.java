package ca.ubc.cs.cs317.dnslookup;

public class DNSRObject {
	
	public DNSRObject(byte[] hostname, byte[] type, byte[] ttl, byte[] ip) {
		
	}
	
	public DNSRObject(byte[] hostname, byte[] type, byte[] ttl) {
		this(hostname, type, ttl, new byte[0]);
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
