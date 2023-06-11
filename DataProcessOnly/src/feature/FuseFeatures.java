package feature;

import pretreatment.PreProcess;
import feature.Energy;

public class FuseFeatures {

	float[] DataUnfused;
	float[] DataFused;
	double voicefeaturevector[][];
    public static final int samplingRate = 22050;
    public static final int samplePerFrame = 512;
    public int noOfFrames;
    
	public FuseFeatures(float[] d, double[][]v){
		this.DataUnfused=d;
		this.voicefeaturevector=v;
	}
	
	public float[] FuseData(){
		//preprocess, do windowing, do framing
		//不对，这里感觉不需要跟之前一样的预处理了？好像直接分帧就行了？
		float[][] framedSignal = PreProcess(DataUnfused, samplePerFrame, samplingRate);
		
		//calculate energy for voiced sound justify
		Energy e = new Energy(samplePerFrame);
		double[] energyValue = e.calcEnergy(framedSignal);
		double averageEnergy=0;
		for(int i=0;i<noOfFrames;i++)averageEnergy+=energyValue[i];
		averageEnergy=averageEnergy/noOfFrames;
		
		for(int i=0;i<noOfFrames;i++){
			//if is not a voiced sound, integrating MFCC parameters into audio
			//清音的帧短时能量小、短时平均幅度小、短时过零率高
			//emmm所以这里的判断标准是呢？阈值应该怎么设定？要不然用平均值？
			if(energyValue[i]>averageEnergy){
				//try to fuse
				float[] fused = lmaFilter(framedSignal[i], voicefeaturevector[i%voicefeaturevector.length]);
				for(int j=0;j<samplePerFrame;j++){
					DataFused[i*samplePerFrame+j]=fused[j];
				}
			}
		}
		return DataFused;
	}
	
	private float[][] PreProcess(float[] afterEndPtDetection, int samplePerFrame, int samplingRate) {

		// calculate no of frames, for framing
		noOfFrames = 2 * afterEndPtDetection.length / samplePerFrame - 1;
        if (noOfFrames < 0) noOfFrames = 0;
        System.out.println("noOfFrames       " + noOfFrames + "  samplePerFrame     " + samplePerFrame + "  EPD length   " + afterEndPtDetection.length);
        float[][] framedSignal = new float[noOfFrames][samplePerFrame];
        for (int i = 0; i < noOfFrames; i++) {
            int startIndex = (i * samplePerFrame / 2);
            for (int j = 0; j < samplePerFrame; j++) {
                framedSignal[i][j] = afterEndPtDetection[startIndex + j];
            }
        }
        //doWindowing();
        return framedSignal;
    }
	
	private float[] lmaFilter(float[] frame, double[] featurevector){
		float[] FusedFrame=new float[samplePerFrame];
		
		for(int i=0;i<samplePerFrame;i++){
			float sum=0;
			for(int j=0;j<featurevector.length;j++){
				sum+=featurevector[j]*Math.pow(frame[j], -j);
			}
			FusedFrame[i]=(float) Math.pow(Math.E, sum);
		}
		return FusedFrame;
	}
}
