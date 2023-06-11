package com.github.niostar.audiopcmrecord;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.util.ResourceUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import com.github.niostar.audiopcmrecord.audio.PcmHandler;
import com.github.niostar.audiopcmrecord.audio.GetFeatureCoffients;
import com.github.niostar.audiopcmrecord.feature.FuseFeatures;
import com.github.niostar.audiopcmrecord.cmd.CMD;
import com.github.niostar.audiopcmrecord.cmd.ClientToRpIdpCmd;
import com.github.niostar.audiopcmrecord.cmd.ResultCmd;
import com.github.niostar.audiopcmrecord.cmd.RpToClientResultCmd;
import com.github.niostar.audiopcmrecord.cmd.ToClientChallengeCodeCmd;
import com.github.niostar.audiopcmrecord.cmd.ToIdpChallengeCodeResCmd;
import com.github.niostar.audiopcmrecord.utils.DeviceUtil;

public class AuthActivity extends Activity implements View.OnClickListener {

    private static final String TAG = AuthActivity.class.getSimpleName();
    private SpeechSynthesizer mTts;
    private int authPosition = -1;
    private ViewGroup codeContainer1,codeContainer2,codeContainer3,codeContainer4;
    private AudioRecord audioRecord;
    // 设置音频采样率，44100标准 22050，16000，11025
    private static int sampleRateInHz = 44100;
    // CHANNEL_IN_STEREO为双声道，CHANNEL_CONFIGURATION_MONO为单声道
    private static final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    private static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSizeInBytes = 0;
    private boolean isRecord = false;// 设置正在录制的状态
    private List<double[][]> userVoicePatternCodes;
    private List<String> authCodes = new ArrayList<>();
    private List<String> speakingmodes = new ArrayList<>();
   // private String authCodes = new String();
    private ServiceConnection serviceConnection = new MyServiceConnection();
    private Messenger mServerMessenger;
    private Handler mClientHandler = new MyClientHandler();
    private Messenger mClientMessenger = new Messenger(mClientHandler);
    private int code;
    private int schedule = 1;
    private double[][] voicefeaturevector;
    private int language;
    private int mode;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        mode = getIntent().getIntExtra("MODE", -1);
        if (mode == Constants.MODE_AUTHENTICATE) {
            code = CMD.CODE_AUTH;
            setTitle("Authenticate");
        } else if (mode == Constants.MODE_REGISTER) {
            code = CMD.CODE_REG;
            setTitle("Register");
        } else if (mode == Constants.MODE_RE_REGISTER) {
            code = CMD.CODE_RE_REG;
            setTitle("Re_Register");
        } else {
            code = CMD.CODE_DELETE_REG;
            findViewById(R.id.delete_view).setVisibility(View.VISIBLE);
            findViewById(R.id.main_view).setVisibility(View.GONE);
            setTitle("Delete_Register");
        }
        language = getIntent().getIntExtra("LANGUAGE", -1);
        Log.v(TAG, "code="+code+"   language="+language);

        TextView tv_hint = (TextView)findViewById(R.id.tv_hint);
        TextView tv_upload = (TextView)findViewById(R.id.tv_upload);

        switch (language) {
            case Constants.LANGUAGE_ENGLISH:
                tv_hint.setText("Follow me please:");
                tv_upload.setText("upload voiceprint");
                break;
            case Constants.LANGUAGE_FINISH:
                tv_hint.setText("Seuratkaa minua:");
                tv_upload.setText("lataa äänenpainatus");
                break;
            case Constants.LANGUAGE_CHINESE:
                tv_hint.setText("请跟着我读出以下字符");
                tv_upload.setText("上传声纹");
        }

        userVoicePatternCodes = new ArrayList<>();
        codeContainer1 = (ViewGroup) findViewById(R.id.code_container_1);
        codeContainer2 = (ViewGroup) findViewById(R.id.code_container_2);
        codeContainer3 = (ViewGroup) findViewById(R.id.code_container_3);
        codeContainer4 = (ViewGroup) findViewById(R.id.code_container_4);
        findViewById(R.id.upload_view).setVisibility(View.GONE);
        findViewById(R.id.upload_view).setOnClickListener(this);

        Intent intent = new Intent(AuthActivity.this, ConnectService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        //TODO 模式提示时的长句子语音合成效果不佳，或许需要改成播放音频。只在挑战码提示的时候用语音合成
        mTts = SpeechSynthesizer.createSynthesizer(this, new InitListener() {
            @Override
            public void onInit(int code) {
                Log.d(TAG, "InitListener init() code = " + code);
                if (code != ErrorCode.SUCCESS) {
                    showTip("初始化失败,错误码：" + code);
                } else {
                    initParams();
                }
            }
        });
    }

    private void sendMsgToService(CMD cmd) {
        if (!mBound) return;
        Message m = new Message();
        m.what = cmd.code;
        m.obj = cmd;
        m.replyTo = mClientMessenger;
        try {
            mServerMessenger.send(m);
        } catch (RemoteException e) {
            e.printStackTrace();
            showTip("error send msg to service");
        }
    }

    private void startRecord() {
        Log.d(TAG, "startRecord(), schedule="+schedule);
        if (audioRecord == null) {
            Log.d(TAG, "init audioRecord, schedule="+schedule);
            bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
        }
        audioRecord.startRecording();
        isRecord = true;
        new GetRecordTask().execute();

        mClientHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopRecord();
            }
        }, 2400);
    }

    private void stopRecord() {
        Log.d(TAG, "recordTask, stop record, schedule="+schedule);
        if (audioRecord != null) {
            System.out.println("stopRecord");
            isRecord = false;
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.stop();
                audioRecord.release();
            }
            audioRecord = null;
        }
    }

    private void processAuth() {
            //TODO
        Log.v(TAG, "processAuth, authPosition="+authPosition );
        if(authPosition == -1){
            //String filename = "ch_child_normal.mp3";

            String filename = generateSpeakingModes();
            //String filename = "sample-000000.mp3";
           // playTipsRecord(filename);

/*
            if(mBound)
                mClientHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        playTipsRecord("2_ch_haizi.mp3");
                    }
                }, 3000);
*/
            AssetManager assetManager;
            MediaPlayer player = null;
            try {
                player = new MediaPlayer();
                assetManager = getAssets();
                AssetFileDescriptor fileDescriptor = assetManager.openFd(filename);
                player.setDataSource(fileDescriptor.getFileDescriptor(),fileDescriptor.getStartOffset(),
                        fileDescriptor.getLength());
                player.prepare();
                player.start();

            } catch (IOException e) {
                e.printStackTrace();
            }

            if(mBound)
                mClientHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        authPosition++;
                        setAuthCodeView();
                        processAuth();
                    }
                }, 8000);
        }
        else if (authPosition < authCodes.size()){
            //领读挑战码便于用户跟读，同时录音
            startSpeak(authCodes.get(authPosition));
        }
        //这个提示一遍录一句的暂时不要了
        /**
            if (authPosition == -1 && schedule == 1) {
                startSpeak("Please read the sentence in a whisper.");
            }
            else if (authPosition == -1 && schedule == 2) {
                startSpeak("Please read the sentence in a dialect.");
            }
            else if (authPosition < authCodes.size()) {
                startSpeak(authCodes.get(authPosition));
            }
         */
            //else if (authPosition == 0){
            //    startSpeak(authCodes);
            //}
            else {//完毕
                //录音完毕
                Log.d(TAG, "record success size is :" + userVoicePatternCodes.size()+"   filesize is :"+userVoicePatternCodes.get(0).length);
                System.out.println(userVoicePatternCodes);
                findViewById(R.id.upload_view).setVisibility(View.VISIBLE);
            }
        //setAuthCodeView();
        //TODO: 两段录音，需要对其进行提取和融合处理，然后再发送出去
    }

    private String generateSpeakingModes(){
        String filename = new String();
        switch (language) {
            case Constants.LANGUAGE_ENGLISH:
            case Constants.LANGUAGE_FINISH:
                filename = filename+"en";
                break;
            case Constants.LANGUAGE_CHINESE:
                filename = filename+"ch";
                break;
        }
        switch (mode){
            case Constants.MODE_REGISTER:
            case Constants.MODE_RE_REGISTER:
                //随机生成新的speakingmodes，保存
                String[] speakingMode = new String[]{
                        "normal","child","sex","old"
                };
                ArrayList<String> speakingcode = new ArrayList<String>();
                for(String s:speakingMode){
                    speakingcode.add(s);
                }
                Collections.shuffle(speakingcode);
                filename = filename + "_" + speakingcode.get(0) + "_" + speakingcode.get(1) + ".mp3";
                Context context = AuthActivity.this;
                SharedPreferences sharedPreferences = getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("FirstMode", speakingcode.get(0));
                editor.putString("SecondMode", speakingcode.get(1));
                editor.commit();
                break;
            case Constants.MODE_AUTHENTICATE:
                //读取之前的speakingmodes
                Context context2 = AuthActivity.this;
                SharedPreferences sharedPreferences2 = getSharedPreferences(context2.getPackageName(), Context.MODE_PRIVATE);
                filename = filename + "_" + sharedPreferences2.getString("FirstMode", "");
                filename = filename + "_" + sharedPreferences2.getString("SecondMode", "");
                filename = filename + ".mp3";
                break;
        }
        Log.d(TAG, filename);
        return filename;
    }

    private void playTipsRecord(String filename){
        AssetManager assetManager;
        MediaPlayer player = null;
        try {
            player = new MediaPlayer();
            assetManager = getAssets();
            AssetFileDescriptor fileDescriptor = assetManager.openFd(filename);
            player.setDataSource(fileDescriptor.getFileDescriptor(),fileDescriptor.getStartOffset(),
                    fileDescriptor.getStartOffset());
            player.prepare();
            player.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initAuthCodeView() {
        codeContainer1.removeAllViews();
        codeContainer2.removeAllViews();
        codeContainer3.removeAllViews();
        codeContainer4.removeAllViews();

        Log.d(TAG, "authCode size = "+authCodes.size());
        for(int i =0; i<authCodes.size();i++){
            String code = authCodes.get(i);
            TextView tv = new TextView(this);
            tv.setText(code);
            //Log.d(TAG, "setText:"+code);
            tv.setPadding(20, 20, 20, 20);
            tv.setTextColor(0xff009688);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            if(i==0){
                Log.d(TAG, "set "+code+" to codeContainer1");
                codeContainer1.addView(tv);
            }
            else {
                Log.d(TAG, "set "+code+" to codeContainer2");
                codeContainer2.addView(tv);
            }
            /*
            if(i<9){
                codeContainer1.addView(tv);
            }else if(i<18){
                codeContainer2.addView(tv);
            }else if(i<27){
                codeContainer3.addView(tv);
            } else{
                codeContainer4.addView(tv);
            }
            */
        }

        findViewById(R.id.loading_view).setVisibility(View.GONE);
        codeContainer1.setVisibility(View.VISIBLE);
        codeContainer2.setVisibility(View.VISIBLE);
        codeContainer3.setVisibility(View.VISIBLE);
        codeContainer4.setVisibility(View.VISIBLE);
        processAuth();
    }

    private void setAuthCodeView() {
        if (authPosition >= 0 && authPosition < authCodes.size()) {
            TextView tv = null;
            /*
            if(authPosition<9){
                tv = (TextView) codeContainer1.getChildAt(authPosition);
            }else if(authPosition<18){
                tv = (TextView) codeContainer2.getChildAt(authPosition-9);
            }else if(authPosition<27){
                tv = (TextView) codeContainer2.getChildAt(authPosition-18);
            } else{
                tv = (TextView) codeContainer3.getChildAt(authPosition-27);
            }
            */
            if(authPosition == 0){
                Log.d(TAG, "set the first text view:"+codeContainer1.getChildAt(authPosition));
                tv = (TextView) codeContainer1.getChildAt(authPosition);
            }
            else if(authPosition == 1){
                Log.d(TAG, "set the second text view:"+codeContainer2.getChildAt(authPosition-1));
                tv = (TextView) codeContainer2.getChildAt(authPosition-1);
            }

            tv.setTextColor(0xfffc5b23);
            tv.setScaleX(1.3f);
            tv.setScaleY(1.3f);
        }

        if (authPosition > 0) {
            TextView tvPre =null;
            /*
            if(authPosition<=9){
                tvPre = (TextView) codeContainer1.getChildAt(authPosition - 1);
            }else if(authPosition<=18){
                tvPre = (TextView) codeContainer2.getChildAt(authPosition - 10);
            }else if(authPosition<=27){
                tvPre = (TextView) codeContainer3.getChildAt(authPosition - 19);
            } else{
                tvPre = (TextView) codeContainer4.getChildAt(authPosition - 28);
            }
            */
            tvPre = (TextView) codeContainer1.getChildAt(authPosition -1);

            if(tvPre!=null){
                tvPre.setTextColor(0xff009688);
                tvPre.setScaleX(1.0f);
                tvPre.setScaleY(1.0f);
            }

        }
    }

    private void startSpeak(String text) {
        Log.d(TAG, "start speak " + text);
        int code = mTts.startSpeaking(text, new SynthesizerListener() {
            @Override
            public void onSpeakBegin() {
                if (authPosition >= 0 && authPosition < authCodes.size()) {
                    showTip("请读" + authCodes.get(authPosition));
                }
            }

            @Override
            public void onBufferProgress(int i, int i1, int i2, String s) {

            }

            @Override
            public void onSpeakPaused() {

            }

            @Override
            public void onSpeakResumed() {

            }

            @Override
            public void onSpeakProgress(int i, int i1, int i2) {

            }

            @Override
            public void onCompleted(SpeechError speechError) {
                if (speechError == null) {
                    Log.d(TAG, "authPosition="+authPosition+"  schedule="+schedule);
                    /*
                    if(authPosition == -1 && schedule == 1){
                        authPosition = 0;
                        setAuthCodeView();
                        Log.d(TAG, "set auth code view of position 0, schedule 1");
                    }
                    else if (authPosition == -1 && schedule == 2){
                        authPosition = 1;
                        setAuthCodeView();
                        Log.d(TAG, "set auth code view of position 1, schedule 2");
                    }
                    else if (authPosition == 0 && schedule == 1){
                        authPosition = -1;
                        schedule = 2;
                        //setAuthCodeView();
                    }
                    else authPosition=2;
                    */
                    //authPosition++;
                    if(mBound)
                        mClientHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                authPosition++;
                                setAuthCodeView();
                                processAuth();
                            }
                        }, 4000);

                } else {
                    showTip(speechError.getPlainDescription(true));
                }

                //if(authPosition>=0){
                    Log.d(TAG, "start record, challengecode="+authCodes.get(authPosition)+"authPosition="+authPosition+"  schedule="+schedule);
                    startRecord();
                //}
            }

            @Override
            public void onEvent(int i, int i1, int i2, Bundle bundle) {

            }
        });


        if (code != ErrorCode.SUCCESS) {
            showTip("语音合成失败,错误码: " + code);
        }
    }


    private void initParams() {
        // 清空参数
        mTts.setParameter(SpeechConstant.PARAMS, null);
        mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
        mTts.setParameter(ResourceUtil.TTS_RES_PATH, getResourcePath());
        mTts.setParameter(SpeechConstant.VOICE_NAME, "xiaofeng");//xiaoyan
        mTts.setParameter(SpeechConstant.VOLUME, "50");
        //设置播放器音频流类型
        mTts.setParameter(SpeechConstant.STREAM_TYPE, "3");
    }

    //获取发音人资源路径
    private String getResourcePath() {
        //合成通用资源
        return ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "tts/common.jet") +
                ";" +//发音人资源
                ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "tts/xiaofeng.jet");
    }


    //录音task
    private class GetRecordTask extends AsyncTask<Void, Void, float[]> {

        byte[] pcmData = new byte[bufferSizeInBytes * 120];


        @Override
        protected float[] doInBackground(Void... params) {
            Log.d(TAG, "start recordTask, schedule="+schedule);

            byte[] readBuffer = new byte[bufferSizeInBytes];

            int len, position = 0;
            while (isRecord && position < pcmData.length && audioRecord != null) {
                if ((len = audioRecord.read(readBuffer, 0, readBuffer.length)) > 0) {
                    System.arraycopy(readBuffer, 0, pcmData, position, len);
                    position += len;
                }
                //Log.d(TAG, "recordTask, read buffer, schedule="+schedule+"content="+readBuffer);
            }

            if (isRecord) {
                stopRecord();
                //Log.d(TAG, "recordTask, stop record, schedule="+schedule);
            }

            System.out.println("bufferSizeInBytes:" + bufferSizeInBytes);
            System.out.println("position:" + position);

            String pcmString = JSON.toJSONString(pcmData);;
            Log.v(TAG, "pcmString = "+ pcmString);
            //byte->float
            //Log.d(TAG, "byte to float, schedule="+schedule);
            float[] pcmDataF = new float[position / 2];
            for (int i = 0; i < position / 2; i++) {
                int LSB = pcmData[2 * i];
                int MSB = pcmData[2 * i + 1];
                pcmDataF[i] = MSB << 8 | (255 & LSB);
            }
            String pcmFString = JSON.toJSONString(pcmDataF);;
            Log.v(TAG, "pcmFString = "+ pcmFString);
            //return pcmDataF;
            PcmHandler.normalizePCM(pcmDataF);
            return PcmHandler.handleEndPoint(pcmDataF, sampleRateInHz);
        }

        @Override
        protected void onPostExecute(float[] pcmF) {
            //Log.d(TAG, "recordTask, onPostExecute, schedule="+schedule);
            if(authPosition == 0){
                //the first record, extract its feature vector, waiting for feature fusing
                //audioRecord=null;
                String firstrecord = JSON.toJSONString(pcmF);
                Log.d(TAG,"first record data:"+firstrecord);
                GetFeatureCoffients featureCoffients = new GetFeatureCoffients(pcmF);
                voicefeaturevector = featureCoffients.getvoiceFeatureVector();
                String vfv = JSON.toJSONString(voicefeaturevector);
                Log.d(TAG,"voice feature vector:"+vfv);
                Log.d(TAG, "get voice feature vector from the first record");
            }
            else if(authPosition == 1){
                //Log.d(TAG, "recordTask, onPostExecute, schedule1 begin");
                //the second record, fuse the feature from the first one
                String secondrecord = JSON.toJSONString(pcmF);
                Log.d(TAG,"second record data:"+secondrecord);

                //FuseFeatures fuse = new FuseFeatures(pcmF, voicefeaturevector);
                //float[] DataFused=fuse.FuseData();
                //String df = JSON.toJSONString(DataFused);
                //Log.d(TAG,"fused data:"+df);
                //还是没找到哪一步运算导致的Infinity和null，要不然将就一下，在add之前检测一下排除掉这些异常数据吧
                //throw away the abnormal data with Infinity and null

                GetFeatureCoffients featureCoffients = new GetFeatureCoffients(pcmF);
                voicefeaturevector = featureCoffients.getvoiceFeatureVector();

                userVoicePatternCodes.add(voicefeaturevector);
                Log.d(TAG, "recordTask, onPostExecute,add fused data to socket");
            }
            //userVoicePatternCodes.add(pcmF);
        }
    }


    //service 发送消息在这儿接收
    private class MyClientHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "reveive msg from service：" + msg.what);
            switch (msg.what) {
                //获得 CHALLENGE_CODE.
                case CMD.CODE_CHALLENGE:
                    authCodes.clear();
                    userVoicePatternCodes.clear();
                    ToClientChallengeCodeCmd c = (ToClientChallengeCodeCmd) msg.obj;
                    Collections.addAll(authCodes, c.challengeCode);
                    //Collections.addAll(speakingmodes, c.speakingMode);
                    initAuthCodeView();
                    break;
                case CMD.CODE_REG:
                case CMD.CODE_AUTH:
                case CMD.CODE_RE_REG:
                case CMD.CODE_DELETE_REG:
                case CMD.CODE_NONE:
                    RpToClientResultCmd cc = (RpToClientResultCmd) msg.obj;
                    Log.d(TAG, "reveive msg from service：" + cc.success);
                    Intent i = new Intent(AuthActivity.this, ResultActivity.class);
                    if (cc.success) {
                        //showTip("操作成功");
                        //finish();
                        i.putExtra("RESULT", Constants.RESULTS_SUCCESS);
                        startActivity(i);
                    } else {
                        //showTip("操作失败");
                        i.putExtra("RESULT", Constants.RESULTS_FAIL);
                        startActivity(i);
                    }

                    if (msg.what == CMD.CODE_DELETE_REG) {
                        findViewById(R.id.delete_progress).setVisibility(View.GONE);
                        ((TextView) findViewById(R.id.delete_text)).setText(cc.success ? "删除注册成功" : "删除注册失败");
                    }
                    break;
                case CMD.CODE_RESULT:
                    ResultCmd resultCmd = (ResultCmd) msg.obj;
                    showTip(resultCmd.message);
                    break;
                default:
            }
        }
    }

    //service is bind
    private boolean mBound;

    private class MyServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "in MyServiceConnection onServiceConnected");
            mServerMessenger = new Messenger(binder);
            mBound = true;
            //发送命令到rp
            Log.v(TAG, "code="+code+"   language="+language);
            ClientToRpIdpCmd cmd = new ClientToRpIdpCmd(code, DeviceUtil.getLocalMacAddress(AuthActivity.this));
            cmd.clientPort = Constants.AUTH_SERVICE_PORT;
            cmd.clientIp = DeviceUtil.getLocalIpAddress(AuthActivity.this);
            cmd.clientLanguage = language;
            sendMsgToService(cmd);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "in MyServiceConnection onServiceDisconnected");
            mBound = false;
        }
    }

    @Override
    public void onClick(View v) {
        //Intent i = new Intent(AuthActivity.this, ResultActivity.class);

        switch (v.getId()) {
            case R.id.upload_view:
                ToIdpChallengeCodeResCmd c = new ToIdpChallengeCodeResCmd(authCodes, userVoicePatternCodes);
                c.clientId = DeviceUtil.getLocalMacAddress(this);
                sendMsgToService(c);
                showTip("上传中...");
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy");
        stopRecord();
        mTts.stopSpeaking();
        mTts.destroy();
        if (mBound) {
            unbindService(serviceConnection);
            mBound = false;
        }
    }


    private static Toast mToast;

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mToast == null) {
                    mToast = Toast.makeText(AuthActivity.this, str, Toast.LENGTH_SHORT);
                } else {
                    mToast.setText(str);
                }
                mToast.show();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.e(TAG, "onDestroy");
        stopRecord();
        mTts.stopSpeaking();
        mTts.destroy();
        if (mBound) {
            unbindService(serviceConnection);
            mBound = false;
        }
    }
}
