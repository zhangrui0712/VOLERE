package com.github.niostar.audiopcmrecord.cmd;


/**
 * idp service -> user
 */
public class ToClientChallengeCodeCmd extends CMD {

    public String[] challengeCode;
    //public String[] speakingMode;

    public ToClientChallengeCodeCmd() {
        super();
    }

    public ToClientChallengeCodeCmd(String[] challengeCode) {
        this.challengeCode = challengeCode;
        //this.speakingMode = speakingMode;
        this.code = CMD.CODE_CHALLENGE;
    }
}
