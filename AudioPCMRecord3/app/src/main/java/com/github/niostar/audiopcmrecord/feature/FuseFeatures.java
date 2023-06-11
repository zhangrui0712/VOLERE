package com.github.niostar.audiopcmrecord.feature;

import android.provider.ContactsContract;
import android.util.Log;

import static android.content.ContentValues.TAG;

public class FuseFeatures {

	private static float[] DataUnfused;
	private static float[] DataFused;
	private static double voicefeaturevector[][];
    public static final int samplingRate = 22050;
    public static final int samplePerFrame = 512;
    public static int noOfFrames;
    
	public FuseFeatures(float[] d, double[][]v){
		this.DataUnfused=d;
        this.DataFused = new float[d.length*2];
		this.voicefeaturevector=v;
	}
	
	public static float[] FuseData(){
		//preprocess, do windowing, do framing
		//Log.d(TAG, "fuse data, preprocess");
		float[][] framedSignal = PreProcess(DataUnfused, samplePerFrame, samplingRate);
		//Log.d(TAG, "row="+framedSignal.length+"   col="+framedSignal[0].length);
		
		//calculate energy for voiced sound justify
		//Log.d(TAG, "fuse data, begin energy calculate");
		Energy e = new Energy(samplePerFrame);
		double[] energyValue = e.calcEnergy(framedSignal);
		double averageEnergy=0;
		for(int i=0;i<noOfFrames;i++)averageEnergy+=energyValue[i];
		averageEnergy=averageEnergy/noOfFrames;
		//Log.d(TAG, "averageEnergy="+averageEnergy);

		//Log.d(TAG, "fuse data, begin fuse");
		for(int i=0;i<noOfFrames;i++){
			//Log.d(TAG,"frame number "+i);
			//if is not a voiced sound, integrating MFCC parameters into audio
			if(energyValue[i]>averageEnergy){
				//Log.d(TAG,"over average energy, try to fuse");
				//try to fuse
				float[] fused = lmaFilter(framedSignal[i], voicefeaturevector[i%voicefeaturevector.length]);

				//Log.d(TAG,"fused");
				for(int j=0;j<samplePerFrame;j++){
					DataFused[i*samplePerFrame+j]=fused[j];
				}
				//Log.d(TAG,"add to DataFused finish");
			}
			else{
				for(int j=0;j<samplePerFrame;j++)DataFused[i*samplePerFrame+j]=framedSignal[i][j];
			}
		}
		//Log.d(TAG, "fuse data, finish, return");
		return DataFused;
	}
	
	private static float[][] PreProcess(float[] afterEndPtDetection, int samplePerFrame, int samplingRate) {

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
	
	private static float[] lmaFilter(float[] frame, double[] featurevector){
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
