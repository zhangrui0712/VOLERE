package com.github.niostar.audiopcmrecord.audio;

import com.github.niostar.audiopcmrecord.feature.FeatureExtract;
import com.github.niostar.audiopcmrecord.feature.FeatureVector;

import java.util.ArrayList;
import java.util.List;

public class GetFeatureCoffients {

    public static final int samplingRate = 22050;
    public static final int samplePerFrame = 512;
    private static int FEATUREDIMENSION = 39;
    private static FeatureExtract fExt;
    private static float[] afterEndPtDetection;

    public GetFeatureCoffients(float[] afterEndPtDetection) {
        super();
        this.afterEndPtDetection = afterEndPtDetection;
    }

    public static double[][] getvoiceFeatureVector() {
        PreProcess pr = new PreProcess(afterEndPtDetection, samplePerFrame, samplingRate);
        fExt = new FeatureExtract(pr.framedSignal, samplingRate, samplePerFrame);
        fExt.makeMfccFeatureVector();
        FeatureVector feature = fExt.getFeatureVector();
        int totalFrames = 0;
        List<double[]> allFeaturesList = new ArrayList<>();
        for (int k = 0; k < feature.getNoOfFrames(); k++) {
            allFeaturesList.add(feature.getFeatureVector()[k]);
            totalFrames++;
        }
       System.out.println("total frames  " + totalFrames + "  allFeaturesList.size   " + allFeaturesList.size());
       double allFeatures[][] = new double[totalFrames][FEATUREDIMENSION];
           for (int i = 0; i < totalFrames; i++) {
             double[] tmp = allFeaturesList.get(i);
             allFeatures[i] = tmp;
            //System.out.println(allFeatures[i]);
        }
        return allFeatures;

    }

}
