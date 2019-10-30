package com.petrsu.se.secretary;

import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

class DataTransfer extends AsyncTask<String, Void, Integer> {
    byte[] stopPack;
    public static boolean timerRunning = false;
    private ScreenRecorder screenRecorder;
    private InetAddress ia;
    public boolean working = true;
    public int requestResult = 0;

    public DataTransfer(ScreenRecorder screenRecorder) {
        this.screenRecorder = screenRecorder;
    }

    @Override
    protected Integer doInBackground(String... args){
        Socket sock = null, lsock = null;
        String addria = "";

        for (String part : args) {
            addria += part;
        }

        try {
            ia = InetAddress.getByName(addria);
        } catch (Exception e) {
            Log.e("FATAL","Failed to resolve IP");
            requestResult = -1;
            return -1;
        }

        try {
            sock = new Socket(addria, 11112);
        } catch (Exception e) {
            Log.e("FATAL","Failed to create the socket");
            requestResult = -2;
            return -2;
        }

        Timer sendTimer = new Timer();
        TimerTask sendTask = new sendTask(sock, lsock, ia, sendTimer);

        stopPack = new byte[9];

        try {
            Thread.sleep(5000); // freeze to write some video
        } catch (Exception e) {
            e.printStackTrace();
            requestResult = -3;
            return -3;
        }

        sendTimer.schedule(sendTask, 0, 5000); // TODO: find optimal vid length

        Log.i("START", "Data transfer start");
        while (timerRunning);
        Log.i("STOP", "Data transfer end");

        try {
            if (!sock.isClosed()) sock.close();
        } catch (Exception e) {
            Log.e("FATAL", "Socket wasn't closed");
            requestResult = -4;
            return -4;
        }

        requestResult = 0;
        return 0;
    }

    @Override
    protected void onPostExecute(Integer result){

    }

    private class sendTask extends TimerTask {
        Socket sock, lsock;
        InetAddress ia;
        Timer sendTimer;
        //int sentFifth = 0;
        byte[] videoBytes;

        File sendFile = new File("/data/user/0/com.petrsu.se.secretary/record.mp4");

        public sendTask(Socket sock, Socket lsock, InetAddress ia, Timer sendTimer) {
            this.sock = sock;
            this.lsock = lsock;
            this.ia = ia;
            this.sendTimer = sendTimer;
            timerRunning = true;
        }

        @Override
        public void run() {
            long len = sendFile.length();
            DataOutputStream dos = null;
            try {
                dos = new DataOutputStream(sock.getOutputStream());
            } catch (IOException e) {
                Log.e("S2S", "1");
                e.printStackTrace();
            }
            if (working) {
                if (len >= 600000) {
                    screenRecorder.stopRecord();
                    videoBytes = new byte[(int) len];
                    int n;
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(sendFile);
                    } catch (Exception e) {
                        Log.e("S2S", "2");
                        e.printStackTrace();
                    }
                    Log.d("S2S", "Yeee");
                    try {
                        dos.writeLong(len);
                        Log.i("FILELEN", "Длина файла " + len);
                        if (sendFile.exists()) {
                            n = fis.read(videoBytes);
                            dos.write(videoBytes, 0, Math.min(videoBytes.length, (int) len));
                        } else Log.e("S2S", "Not found");
                    } catch (Exception e) {
                        Log.e("S2S", "3");
                        e.printStackTrace();
                    }
                    /*if (sentFifth != 5) { // debug
                        screenRecorder.startRecord();
                        sentFifth++;
                    } else {
                        sendTimer.cancel();
                        Log.i("FILELEN", "finish");
                    }*/
                    screenRecorder.startRecord();
                }
            } else {
                try {
                    dos.writeLong(-11);
                } catch (Exception e) {
                    Log.e("S2S", "4");
                    e.printStackTrace();
                }
                sendTimer.cancel();
                Log.i("FILELEN", "finish by user");
            }
        }
    }
}
