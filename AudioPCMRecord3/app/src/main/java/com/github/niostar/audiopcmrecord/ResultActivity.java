package com.github.niostar.audiopcmrecord;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Created by ZhangRui on 2018/11/9.
 */

public class ResultActivity extends Activity{

    private TextView tv_result;

    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        tv_result = (TextView) findViewById(R.id.tv_authresult);

        int result = getIntent().getIntExtra("RESULT", -1);
        switch (result){
            case Constants.RESULTS_SUCCESS:
                tv_result.setText("Success!");
                break;
            case Constants.RESULTS_FAIL:
                tv_result.setText("Fail!");
                break;
        }
    }


}
