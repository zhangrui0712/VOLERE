import pretreatment.GetFeatureCoffients;
import pretreatment.SaveAudioSet;
import util.Log;
import util.WaveHeader;
import feature.FuseFeatures;
import db.DBManager;
//import idpservice.IDPGetVoicePatternService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;


//import org.apache.commons.io.IOUtils;
public class run {
	private static final String TAG = run.class.getSimpleName();
	
	static String filename="test.wav";
	static String unfusedfile="test2.wav";
	static String fusedfilename="fused.wav";
	float[] afterEndPtDetection;
	
	public static void main(String[] args) throws IOException {
		//fuseProcess();
		DBManager.initdatabase();
		Log.v(TAG, "database init suscess");
		//SaveAudioSet.saveProcess();
	}
	
	public static void fuseProcess() throws IOException{
		// TODO Auto-generated method stub

				/**
				 * process of template synthesize
				 **/
				//file input.
				float[] DataF=getData(filename);
				//byte[] DataB = getData(filename);
				
				//extract the voice feature vectors.
				GetFeatureCoffients featureCoffients = new GetFeatureCoffients(DataF);
				double voicefeaturevector[][] = featureCoffients.getvoiceFeatureVector();
				int row=voicefeaturevector.length;
				int col=voicefeaturevector[0].length;
				System.out.println("row="+row+"   col="+col);
				
				//fuse features and save the synthesized template.
				float[] DataUnfused=getData(unfusedfile);
				FuseFeatures fuse = new FuseFeatures(DataUnfused, voicefeaturevector);
				float[] DataFused = fuse.FuseData();
				
				
				//save synthesized voice pattern.
				SaveData(DataF, fusedfilename);
				//SaveData(DataB, fusedfilename);
				
				//匹配跟上面不是一个流程了，到时候可以分开
				/**
				 * process of matching and decision making based on ivector
				 **/
				//create UBM for all training speaker data
				
				//adapt the UBM to each speaker to create GMM speaker model
				
				//calculate the score for each model versus each speaker's data.
	}

	public static float[] getData(String filename) throws IOException{
		File pcmfile = new File(filename);
		FileInputStream fileInputStream = new FileInputStream(pcmfile);
		byte[] wavbyte = InputStreamToByte(fileInputStream);
		byte[] pcmbyte = Arrays.copyOfRange(wavbyte, 44, wavbyte.length);
		/*
		byte[] head = Arrays.copyOfRange(wavbyte, 0, 44);
		System.out.println("channels: "+head[22]);
		System.out.println("bits per sample: "+head[34]);
		int l;                                           
	    l = head[24];                                
	    l &= 0xff;                                       
	    l |= ((long) head[25] << 8);                 
	    l &= 0xffff;                                     
	    l |= ((long) head[26] << 16);                
	    l &= 0xffffff;                                   
	    l |= ((long) head[27] << 24);
		System.out.println("samples per sec: "+l);
		*/
		/**
		//byte->float
		int position=pcmbyte.length;
        float[] DataF = new float[position / 2];
        for (int i = 0; i < position / 2; i++) {
            int LSB = pcmbyte[2 * i];
            int MSB = pcmbyte[2 * i + 1];
            DataF[i] = MSB << 8 | (255 & LSB);//不对吧这长度对得上吗，float不得是32位么，得有4个byte拼一起吧 
        }
        **/
		//return pcmbyte;
		
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
	
	public static void SaveData(float[] data, String filename) throws IOException{
		byte[] datafusedB = FloatStreamToByte(data);
		FileOutputStream fos = new FileOutputStream(filename);
		
		//填入参数，比特率等等。这里用的是16位单声道 8000 hz
		WaveHeader header = new WaveHeader();
		
		//长度字段 = 内容的大小（PCMSize) + 头部字段的大小(不包括前面4字节的标识符RIFF以及fileLength本身的4字节)
		int PCMSize = datafusedB.length;
		//int PCMSize = data.length;
		   header.fileLength = PCMSize + (44 - 8);
		   header.FmtHdrLeth = 16;
		   header.BitsPerSample = 24;
		   header.Channels = 1;
		   header.FormatTag = 0x0001;
		   header.SamplesPerSec = 48000;
		   header.BlockAlign = (short)(header.Channels * header.BitsPerSample / 8);
		   header.AvgBytesPerSec = header.BlockAlign * header.SamplesPerSec;
		   header.DataHdrLeth = PCMSize;
		byte[] h = header.getHeader();
		
		assert h.length == 44; //WAV标准，头部应该是44字节
		
		//write header
		fos.write(h, 0, h.length);
		//write data stream
		fos.write(datafusedB, 0, PCMSize);
		//fos.write(data);
		fos.close();
		System.out.println("Save File OK!");
	}
	private static byte[] FloatStreamToByte(float[] datafused){
		byte[] dataB = new byte [datafused.length*4];
		for(int i=0;i<datafused.length;i++){
			int fbit = Float.floatToIntBits(datafused[i]);
			for (int j = 0; j < 4; j++) {  
		        dataB[i*4+j] = (byte) (fbit >> (24 - j * 8));  
		    }
			//翻转这四位
			for(int j=0;j<2;j++){
				byte b = dataB[i*4+j];
				dataB[i*4+j]=dataB[i*4+3-j];
				dataB[i*4+3-j]=b;
			}
		}
		return dataB;
	}
}
