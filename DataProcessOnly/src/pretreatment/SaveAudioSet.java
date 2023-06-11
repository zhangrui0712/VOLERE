package pretreatment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import db.DBManager;

public class SaveAudioSet {
	public static void saveProcess() throws IOException{
		//1 read audio file
		for(int i=1;i<100;i++){
			String filename;
			if(i<=9)filename="video/sample-00000"+i+".mp3";
			else filename="video/sample-0000"+i+".mp3";
			float[] DataF=getData(filename);
			
			//2 generate uaId
			String uaId;
			if(i<=9)uaId="00.00.00.00.00.0"+i;
			else uaId="00.00.00.00.00."+i;
					
			//3 save to database after feature extract
			DBManager.saveUserVoice(uaId, DataF);
		}
	}
	
	public static float[] getData(String filename) throws IOException{
		File pcmfile = new File(filename);
		FileInputStream fileInputStream = new FileInputStream(pcmfile);
		byte[] wavbyte = InputStreamToByte(fileInputStream);
		byte[] pcmbyte = Arrays.copyOfRange(wavbyte, 44, wavbyte.length);
		int position = pcmbyte.length;
		float[] DataF = new float[position / 4];
		for (int i=0;i<position/4;i++){
			int l;                                           
		    l = pcmbyte[i*4 + 0];                                
		    l &= 0xff;                                       
		    l |= ((long) pcmbyte[i*4 + 1] << 8);                 
		    l &= 0xffff;                                     
		    l |= ((long) pcmbyte[i*4 + 2] << 16);                
		    l &= 0xffffff;                                   
		    l |= ((long) pcmbyte[i*4 + 3] << 24); 
		    DataF[i]=Float.intBitsToFloat(l);
		}
		return DataF;
	}
	/**
	 * 输入流转byte二进制数据
	 * @param fis
	 * @return
	 * @throws IOException
	 */
	private static byte[] InputStreamToByte(FileInputStream fis) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		long size = fis.getChannel().size();
		byte[] buffer = null;
		if (size <= Integer.MAX_VALUE) {
			buffer = new byte[(int) size];
		} else {
			buffer = new byte[8];
			for (int ix = 0; ix < 8; ++ix) {
				int offset = 64 - (ix + 1) * 8;
				buffer[ix] = (byte) ((size >> offset) & 0xff);
			}//can't understand
		}
		int len;
		while ((len = fis.read(buffer)) != -1) {
			byteStream.write(buffer, 0, len);
		}
		byte[] data = byteStream.toByteArray();
		//IOUtils.closeQuietly(byteStream);
		return data;
	}
}
