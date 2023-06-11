package rpservice;

import cmd.CMD;
import cmd.Constants;
import cmd.ClientToRpIdpCmd;
import cmd.RpToClientResultCmd;

import com.alibaba.fastjson.JSON;

import util.Log;

import java.io.*;
import java.net.Socket;

/**
 * rpc service
 * send the client cmd to idp service
 * build the connection between client and idp service
 * client->rp(rpc service)->idp
 * idp->client
 */
public class RPConnectionService extends Thread {
    private static final String TAG = RPConnectionService.class.getSimpleName();

    private Socket mClientSocket;
    private final int rpId;

    public RPConnectionService(int rpId, Socket mClientSocket) {
        this.rpId = rpId;
        this.mClientSocket = mClientSocket;
        //System.out.println("RP connection");
    }

    @Override
    public void run() {
        BufferedReader mClientReader, mIDPServiceReader;
        BufferedWriter clientWriter, idpServiceWriter;
        //System.out.println("RP connection run start");
        try {
        	//System.out.println("RP connection run");
            Socket mIDPServiceSocket = new Socket(Constants.IDP_IP, Constants.IDP_PORT);
            mClientReader = new BufferedReader(new InputStreamReader(mClientSocket.getInputStream()));
            clientWriter = new BufferedWriter(new OutputStreamWriter(mClientSocket.getOutputStream()));
            mIDPServiceReader = new BufferedReader(new InputStreamReader(mIDPServiceSocket.getInputStream()));
            idpServiceWriter = new BufferedWriter(new OutputStreamWriter(mIDPServiceSocket.getOutputStream()));
            Log.v(TAG, "connected to idp service:" + Constants.IDP_IP + "@" + Constants.IDP_PORT);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "error connect to the idp service");
            return;
        }

        while (true) {
            try {
                String clientInputCmd = mClientReader.readLine();
                if (clientInputCmd != null && !clientInputCmd.isEmpty()) {
                    Log.v(TAG, "get command from client:" + clientInputCmd);
                    consumeClientCmd(clientInputCmd, clientWriter, mIDPServiceReader, idpServiceWriter);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }


    private void consumeClientCmd(String clientCmd, BufferedWriter clientWriter,
                                  BufferedReader mIDPServiceReader, BufferedWriter mIDPServiceWrite) {

        boolean isOkay = false;
        ClientToRpIdpCmd cmd = JSON.parseObject(clientCmd, ClientToRpIdpCmd.class);
        if (cmd != null) {
            Log.v(TAG, "consumeClientCmd clientCommand = " + clientCmd);
            cmd.rp_id = rpId;
          
            //cmd.clientIp = "172.23.82.3";
            //send the command to idp server
            try {
                mIDPServiceWrite.write(JSON.toJSONString(cmd) + "\n");
                mIDPServiceWrite.flush();
                Log.v(TAG, "send command to idp:" + clientCmd);
                isOkay = true;
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        if (!isOkay) {
            Log.e(TAG, "error parse cmd :" + clientCmd);
            RpToClientResultCmd resultCmd;
            if (cmd == null) {
                resultCmd = new RpToClientResultCmd(CMD.CODE_NONE, false, "鍛戒护閿欒");
            } else {
                resultCmd = new RpToClientResultCmd(cmd.code, false, "鍙戦�佸懡浠ゅ埌IDP鏈嶅姟澶辫触");
            }
            try {
                clientWriter.write(JSON.toJSONString(resultCmd) + "\n");
                clientWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            new RPGetIDPResultService(mIDPServiceReader, clientWriter).start();
        }
    }

}
