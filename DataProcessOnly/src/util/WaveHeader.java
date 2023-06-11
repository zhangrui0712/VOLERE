package util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class WaveHeader {

	public final char fileID[] = {'R', 'I', 'F', 'F'};
	public int fileLength;
	public char wavTag[] = {'W', 'A', 'V', 'E'};;
	public char FmtHdrID[] = {'f', 'm', 't', ' '};
	public int FmtHdrLeth;
	public short FormatTag;
	public short Channels;
	public int SamplesPerSec;
	public int AvgBytesPerSec;
	public short BlockAlign;
	public short BitsPerSample;
	public char DataHdrID[] = {'d','a','t','a'};
	public int DataHdrLeth;

	public byte[] getHeader()throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		WriteChar(bos, fileID);
		WriteInt(bos, fileLength);
		WriteChar(bos, wavTag);
		WriteChar(bos, FmtHdrID);
		WriteInt(bos, FmtHdrLeth);
		WriteShort(bos, FormatTag);
		WriteShort(bos, Channels);
		WriteInt(bos, SamplesPerSec);
		WriteInt(bos, AvgBytesPerSec);
		WriteShort(bos, BlockAlign);
		WriteShort(bos, BitsPerSample);
		WriteChar(bos, DataHdrID);
		WriteInt(bos, DataHdrLeth);
		bos.flush();
		byte[] ret = bos.toByteArray();
		bos.close();
		return ret;
	}
	
	public void WriteChar(ByteArrayOutputStream bos, char[] content)throws IOException{
		for(int i=0;i<content.length;i++){
			char c = content[i];
			bos.write(c);
		}
	}
	
	public void WriteInt(ByteArrayOutputStream bos, int content) throws IOException{
		byte[] buf = new byte[4];
		buf[3] = (byte)(content>>24);
		buf[2] = (byte)((content<<8)>>24);
		buf[1] = (byte)((content<<16)>>24);
		buf[0] = (byte)((content<<24)>>24);
		bos.write(buf);
	}
	
	public void WriteShort(ByteArrayOutputStream bos, short content)throws IOException{
		byte[] buf = new byte[2];
		buf[1] = (byte)(content>>8);
		buf[0] = (byte)((content<<8)>>8);//这个地方我跟搜到的资料写的不一样，不知道我写得对不对
		bos.write(buf);
	}
}
