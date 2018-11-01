package ca.ubc.cs.cs317.dnslookup;

import java.nio.ByteBuffer;

public class DNSResponse {

	public byte[] header;
	public byte[] answer;
	public byte[] auths;
	public byte[] additionals;

	public DNSResponse(byte[] data) {
<<<<<<< HEAD
		int offset = 0;
		// GET HEADER
		for (int i = 0; i < 12; i++) {
			header[i] = data[i];
		}
		offset = 12;
		
		// GET QUESTIONS
		if (data[offset] == 0xc0) {
			offset = offset+6;
			question = new byte[6];
			for (int i = 0; i < 6; i++) {
				question[i] = data[offset+i];
			}
		} else {
			for (int j = 0; j < getByteInt(getNumQuestion()); j++) {
				int qsize = 0;
				while (data[offset+qsize] != 0x00) {
					qsize++;
				}
				qsize++;
				qsize = qsize+4;
				question = new byte[qsize];
				for (int i = 0; i < qsize; i++) {
					question[i] = data[offset+i];
				}
				offset += qsize;
			}
		}
		
		// GET ANSWERS
		if (data[offset] == 0xc0) {
			offset = offset+10;
			byte[] rbuf = new byte[2];
			rbuf[0] = data[offset];
			offset++;
			rbuf[1] = data[offset];
			offset++;
			int responseSize = getByteInt(rbuf);
			question = new byte[12+responseSize];
			for (int i = 0; i < 16; i++) {
				question[i] = data[offset+i];
			}
		} else {
			for (int j = 0; j < getByteInt(getNumAnswer()); j++) {
				int qsize = 0;
				while (data[offset+qsize] != 0x00) {
					qsize++;
				}
				qsize++;
				qsize = qsize+8;
				byte[] rbuf = new byte[2];
				rbuf[0] = data[offset+qsize];
				qsize++;
				rbuf[1] = data[offset+qsize];
				qsize++;
				int responseSize = getByteInt(rbuf);
				question = new byte[qsize];
				for (int i = 0; i < qsize; i++) {
					question[i] = data[offset+i];
				}
				offset += qsize;
			}
		}
	}
	
	int getByteInt(byte[] bytes) {
	     return ByteBuffer.wrap(bytes).getInt();
=======
		parseHeader(data);
		parseAnswer(data);
		parseAuth(data);
		parseAdditionals(data);
>>>>>>> parsing
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

	public getNumberAuthoritatives() {

	}

}
