package ca.ubc.cs.cs317.dnslookup;

import java.nio.ByteBuffer;

public class DNSResponse {
	
	public byte[] header = new byte[12];
	public byte[] question;
	public byte[] answer;
	
	public DNSResponse(byte[] data) {
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
	}
	
	public byte[] getQueryId() {
		byte[] rbuf = new byte[2];
		rbuf[0] = header[0];
		rbuf[1] = header[1];
		return rbuf;
	}
	
	public byte[] getNumQuestion() {
		byte[] rbuf = new byte[2];
		rbuf[0] = header[4];
		rbuf[1] = header[5];
		return rbuf;
	}
	public byte[] getNumAnswer() {
		byte[] rbuf = new byte[2];
		rbuf[0] = header[6];
		rbuf[1] = header[7];
		return rbuf;
	}
	public byte[] getNumAuthority() {
		byte[] rbuf = new byte[2];
		rbuf[0] = header[8];
		rbuf[1] = header[9];
		return rbuf;
	}
	public byte[] getNumAdditional() {
		byte[] rbuf = new byte[2];
		rbuf[0] = header[10];
		rbuf[1] = header[11];
		return rbuf;
	}

}
