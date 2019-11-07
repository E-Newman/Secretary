package com.petrsu.se.secretary;

import android.os.AsyncTask;
import android.os.Environment;
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
            return -1;
        }

        try {
            sock = new Socket(addria, 11112);
        } catch (Exception e) {
            Log.e("FATAL","Failed to create the socket");
            return -2;
        }

        Timer sendTimer = new Timer();
        TimerTask sendTask = new sendTask(sock, lsock, ia, sendTimer);

        stopPack = new byte[9];

        try {
            Thread.sleep(3000); // freeze to write some video
        } catch (Exception e) {
            e.printStackTrace();
        }

        sendTimer.schedule(sendTask, 0, 5000); // TODO: find optimal vid length

        Log.i("START", "Data transfer start");
        while (timerRunning);
        Log.i("STOP", "Data transfer end");

        try {
            if (!sock.isClosed()) sock.close();
        } catch (Exception e) {
            Log.e("FATAL", "Socket wasn't closed");
            return -4;
        }

        return 0;
    }

    @Override
    protected void onPostExecute(Integer result){

    }

    private class sendTask extends TimerTask {
        Socket sock, lsock;
        InetAddress ia;
        Timer sendTimer;
        byte[] videoBytes;

        public sendTask(Socket sock, Socket lsock, InetAddress ia, Timer sendTimer) {
            this.sock = sock;
            this.lsock = lsock;
            this.ia = ia;
            this.sendTimer = sendTimer;
            timerRunning = true;
        }

        @Override
        public void run() {
            File sendFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/record.mp4");
            long len = sendFile.length();
            DataOutputStream dos = null;
            try {
                dos = new DataOutputStream(sock.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (working) {
                    screenRecorder.stopRecord();
                    videoBytes = new byte[(int) len];
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(sendFile);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Log.d("RECORDED", "Yeee");
                    try {
                        Log.d("DOWNLOADFILE", "Длина файла " + len);
                        Log.d("DOWNLOADFILE", "Длина массива " + videoBytes.length);
                        if (sendFile.exists()) {
                            fis.read(videoBytes);
                            dos.writeLong(videoBytes.length);
                            dos.write(videoBytes, 0, videoBytes.length);
                        } else Log.e("FILE", "Not found");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    screenRecorder.startRecord();
            } else {
                try {
                    dos.writeLong(-11);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sendTimer.cancel();
                Log.i("FILELEN", "finish by user");
            }
        }
    }
}
