package com.github.niostar.audiopcmrecord;

import android.animation.Animator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ResourceUtil;

import com.github.niostar.audiopcmrecord.utils.FileUtil;
import com.github.niostar.audiopcmrecord.utils.ResultParser;


public class MainActivity extends Activity implements View.OnClickListener, InitListener {


    private static String TAG = MainActivity.class.getSimpleName();
    // 语音识别对象
    RecognizerDialog recognizerDialog;
    private TextView recResult;
    private static final String grmPath =
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/msc/grammar";
    private String mResPath;
    private View cmdContainer;
    private View langContainer;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_reg).setOnClickListener(this);
        findViewById(R.id.btn_auth).setOnClickListener(this);
        findViewById(R.id.btn_reg_update).setOnClickListener(this);
        findViewById(R.id.btn_reg_delete).setOnClickListener(this);
        findViewById(R.id.btn_speak).setOnClickListener(this);
        findViewById(R.id.btn_show_cmd).setOnClickListener(this);

        findViewById(R.id.btn_chos_language).setOnClickListener(this);
        findViewById(R.id.btn_en).setOnClickListener(this);
        findViewById(R.id.btn_fi).setOnClickListener(this);
        findViewById(R.id.btn_ch).setOnClickListener(this);

        recResult = (TextView) findViewById(R.id.tv_result);
        cmdContainer = findViewById(R.id.cmd_container);
        langContainer = findViewById(R.id.language_container);

        mResPath = ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "asr/common.jet");
        recognizerDialog = new RecognizerDialog(this, this);
    }

    @Override
    public void onInit(int i) {
        Log.d(TAG, "SpeechRecognizer init() code = " + i);
        if (i != ErrorCode.SUCCESS) {
            showTip("讯飞语音初始化失败,错误码：" + i);
        } else {
            buildGrammer();
        }
    }

    private void buildGrammer() {
        // 设置文本编码格式
        SpeechRecognizer mAsr = SpeechRecognizer.createRecognizer(this, null);
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
        mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
        mAsr.setParameter(ResourceUtil.ASR_RES_PATH, mResPath);

        String mGrammerStr = FileUtil.readAssetsFile(this, "cmd.bnf", "utf-8");

        int result = mAsr.buildGrammar("bnf", mGrammerStr, new GrammarListener() {
            @Override
            public void onBuildFinish(String grammarId, SpeechError speechError) {
                if (speechError == null) {
                    Log.d(TAG, "语法构建成功：" + grammarId);
                    recognizerDialog.setParameter(SpeechConstant.LOCAL_GRAMMAR, grammarId);
                } else {
                    showTip("语法构建失败,错误码：" + speechError.getErrorCode());
                }
            }
        });

        if (result != ErrorCode.SUCCESS) {
            showTip("构建语法失败,错误码: " + result);
        }
    }


    @Override
    public void onClick(View v) {
        //mAsr.stopListening();
        //showTip("停止识别");
        // 取消识别
        //mAsr.cancel();
        //showTip("取消识别");

        Intent i = new Intent(MainActivity.this, AuthActivity.class);
        int constants_lang = 0; TextView tv_lang = (TextView)findViewById(R.id.btn_chos_language);
        switch (tv_lang.getText().toString()) {
            case "Language":
            case "English":
                constants_lang = 501;
                break;
            case "Suomalainen":
                constants_lang = 502;
                break;
            case "中文":
                constants_lang = 503;
                break;
        }
        switch (v.getId()) {
            case R.id.btn_speak:
                startListening();
                break;
            case R.id.btn_reg:
                i.putExtra("MODE", Constants.MODE_REGISTER);
                i.putExtra("LANGUAGE", constants_lang);
                startActivity(i);
                break;
            case R.id.btn_auth:
                i.putExtra("MODE", Constants.MODE_AUTHENTICATE);
                i.putExtra("LANGUAGE", constants_lang);
                startActivity(i);
                break;
            case R.id.btn_reg_delete:
                i.putExtra("MODE", Constants.MODE_DELETE_REG);
                i.putExtra("LANGUAGE", constants_lang);
                startActivity(i);
                break;
            case R.id.btn_reg_update:
                i.putExtra("MODE", Constants.MODE_RE_REGISTER);
                i.putExtra("LANGUAGE", constants_lang);
                startActivity(i);
                break;
            case R.id.btn_show_cmd:
                showCmdsCard();
                break;

            case R.id.btn_chos_language:
                showLangsCard();
                break;
            case R.id.btn_en:
                setLang("English");
                break;
            case R.id.btn_fi:
                setLang("Suomalainen");
                break;
            case R.id.btn_ch:
                setLang("中文");
                break;
        }
    }
    //开始语音识别
    private void startListening() {
        setRecognizerParams();
        if (recognizerDialog.isShowing()) {
            recognizerDialog.dismiss();
        }
        recognizerDialog.show();
    }


    private RecognizerDialogListener recognizerListener = new RecognizerDialogListener() {
        @Override
        public void onResult(RecognizerResult result, boolean b) {
            if (null != result && !TextUtils.isEmpty(result.getResultString())) {
                Log.e(TAG, "recognizer result：" + result.getResultString());
                int id = ResultParser.parserCommand(result.getResultString());
                int constants_lang = 0; TextView tv_lang = (TextView)findViewById(R.id.btn_chos_language);
                switch (tv_lang.getText().toString()) {
                    case "Language":
                    case "English":
                        constants_lang = 501;
                        break;
                    case "Suomalainen":
                        constants_lang = 502;
                        break;
                    case "中文":
                        constants_lang = 503;
                        break;
                }
                switch (id) {
                    case 100:
                        Intent i = new Intent(MainActivity.this, AuthActivity.class);
                        i.putExtra("MODE", Constants.MODE_REGISTER);
                        i.putExtra("LANGUAGE", constants_lang);
                        startActivity(i);
                        break;
                    case 101:
                        Intent ii = new Intent(MainActivity.this, AuthActivity.class);
                        ii.putExtra("MODE", Constants.MODE_AUTHENTICATE);
                        ii.putExtra("LANGUAGE", constants_lang);
                        startActivity(ii);
                        break;
                    case 102:
                        Intent iii = new Intent(MainActivity.this, AuthActivity.class);
                        iii.putExtra("MODE", Constants.MODE_RE_REGISTER);
                        iii.putExtra("LANGUAGE", constants_lang);
                        startActivity(iii);
                        break;
                    case 103:
                        Intent iiii = new Intent(MainActivity.this, AuthActivity.class);
                        iiii.putExtra("MODE", Constants.MODE_DELETE_REG);
                        iiii.putExtra("LANGUAGE", constants_lang);
                        startActivity(iiii);
                        break;
                    //// TODO: 2017/2/23
                    default:
                        showTip("没有听清,请再说一遍");
                        startListening();
                        break;
                }
                // 显示
            } else {
                Log.d(TAG, "recognizer result : null");
            }
        }

        @Override
        public void onError(SpeechError speechError) {
            switch (speechError.getErrorCode()) {
                case ErrorCode.ERROR_NO_MATCH:
                    showTip("没有听清,请再说一遍");
                    startListening();
                    break;
                case ErrorCode.ERROR_NO_NETWORK:
                    showTip("没有网络连接");
                    break;
                default:
                    showTip("识别错误 Code：" + speechError.getErrorCode());
                    break;
            }
        }
    };


    public void setRecognizerParams() {
        recognizerDialog.setListener(recognizerListener);
        recognizerDialog.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
        recognizerDialog.setParameter(SpeechConstant.RESULT_TYPE, "json");
        recognizerDialog.setParameter(SpeechConstant.ASR_THRESHOLD, "30");
        recognizerDialog.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        recognizerDialog.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
        recognizerDialog.setParameter(ResourceUtil.ASR_RES_PATH, mResPath);
        recognizerDialog.setParameter(SpeechConstant.ASR_AUDIO_PATH, getFilesDir() + "/msc/asr.wav");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cmdContainer.getVisibility() == View.VISIBLE) {
            hideCmdsCard();
        }
        if (langContainer.getVisibility() == View.VISIBLE) {
            hideLangsCard();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recognizerDialog.cancel();
        recognizerDialog.destroy();
    }

    private static Toast mToast;

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mToast == null) {
                    mToast = Toast.makeText(MainActivity.this, str, Toast.LENGTH_SHORT);
                } else {
                    mToast.setText(str);
                }
                mToast.show();
            }
        });
    }

    private void showCmdsCard() {
        Animator animator = ViewAnimationUtils.createCircularReveal(
                cmdContainer,
                cmdContainer.getWidth(),
                cmdContainer.getHeight(),
                0,
                (float) Math.hypot(cmdContainer.getWidth(), cmdContainer.getHeight()));
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(220);
        animator.start();
        cmdContainer.setVisibility(View.VISIBLE);
    }

    private void hideCmdsCard() {
        Animator animator = ViewAnimationUtils.createCircularReveal(
                cmdContainer,
                cmdContainer.getWidth(),
                cmdContainer.getHeight(),
                (float) Math.hypot(cmdContainer.getWidth(), cmdContainer.getHeight()),
                0);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(220);
        animator.start();
        cmdContainer.postDelayed(new Runnable() {
            @Override
            public void run() {
                cmdContainer.setVisibility(View.INVISIBLE);
            }
        }, 220);
    }


    @Override
    public void onBackPressed() {
        if (cmdContainer.getVisibility() == View.VISIBLE) {
            hideCmdsCard();
        } else {
            super.onBackPressed();
        }
        if (langContainer.getVisibility() == View.VISIBLE) {
            hideLangsCard();
        } else {
            super.onBackPressed();
        }
    }

    public void showLangsCard(){
        Animator animator = ViewAnimationUtils.createCircularReveal(
                langContainer,
                langContainer.getWidth(),
                langContainer.getHeight(),
                0,
                (float) Math.hypot(langContainer.getWidth(), langContainer.getHeight()));
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(220);
        animator.start();
        langContainer.setVisibility(View.VISIBLE);
    }

    public void hideLangsCard(){
        Animator animator = ViewAnimationUtils.createCircularReveal(
                langContainer,
                langContainer.getWidth(),
                langContainer.getHeight(),
                (float) Math.hypot(langContainer.getWidth(), langContainer.getHeight()),
                0);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(220);
        animator.start();
        langContainer.postDelayed(new Runnable() {
            @Override
            public void run() {
                langContainer.setVisibility(View.INVISIBLE);
            }
        }, 220);
    }

    public void setLang(String lang){
        TextView tv = (TextView)findViewById(R.id.btn_chos_language);
        tv.setText(lang);
        hideLangsCard();

        TextView tv_result = (TextView)findViewById(R.id.tv_result);
        TextView btn_show_cmd = (TextView)findViewById(R.id.btn_show_cmd);
        TextView btn_reg = (TextView)findViewById(R.id.btn_reg);
        TextView btn_auth = (TextView)findViewById(R.id.btn_auth);
        TextView btn_reg_update = (TextView)findViewById(R.id.btn_reg_update);
        TextView btn_reg_dele = (TextView)findViewById(R.id.btn_reg_delete);

        switch(lang) {
            case "English":
                tv_result.setText("Tell me what do you want to do.");
                btn_show_cmd.setText("Operation options");
                btn_reg.setText("register");
                btn_auth.setText("authenticate");
                btn_reg_update.setText("update registration");
                btn_reg_dele.setText("delete registration");
                break;
            case "Suomalainen":
                tv_result.setText("Kerro mitä haluat tehdä.");
                btn_show_cmd.setText("käyttö-vaihtoehdot");
                btn_reg.setText("ekisteröinti");
                btn_auth.setText("todentaa");
                btn_reg_update.setText("päivitä rekisteröinti");
                btn_reg_dele.setText("poista rekisteröinti");
                break;
            case "中文":
                tv_result.setText("请说出你需要的操作");
                btn_show_cmd.setText("操作选项");
                btn_reg.setText("注册");
                btn_auth.setText("认证");
                btn_reg_update.setText("更新注册信息");
                btn_reg_dele.setText("注销账号");
                break;
        }
    }
}
