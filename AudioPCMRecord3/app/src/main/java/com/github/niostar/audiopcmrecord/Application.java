package com.github.niostar.audiopcmrecord;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;

public class Application extends android.app.Application {

    @Override
    public void onCreate() {
        super.onCreate();

        String param = (SpeechConstant.APPID + "=5c0730b3") +
                "," +
                SpeechConstant.ENGINE_MODE + "=" + SpeechConstant.MODE_MSC;
        // 设置使用v5+
        SpeechUtility.createUtility(Application.this, param);
        super.onCreate();

    }
}
