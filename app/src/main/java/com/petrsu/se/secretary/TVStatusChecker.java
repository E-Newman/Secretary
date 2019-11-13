package com.petrsu.se.secretary;

import android.os.AsyncTask;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

class TVStatusChecker extends AsyncTask<String, Void, Integer> {
    public String tvStatus = "Неизвестная ошибка";

    @Override
    protected Integer doInBackground(String... args){
        Socket controlSock = null;
        String addria = "";
        DataOutputStream dos = null;
        DataInputStream dis = null;
        for (String part : args) {
            addria += part;
        }

        try {
            controlSock = new Socket(addria,11110);
            dos = new DataOutputStream(controlSock.getOutputStream());
            dis = new DataInputStream(controlSock.getInputStream());
        } catch (Exception e) {
            Log.e("FATAL","Failed to create the socket");
            tvStatus = "Не удалось создать сокет";
            return -2;
        }

        try {
            dos.writeInt(1);
            controlSock.setSoTimeout(3000);
        } catch (Exception e) {
            Log.e("FATAL","Failed to send datagram");
            tvStatus = "Не удалось отправить запрос на подключение";
            try {
                if (!controlSock.isClosed()) {
                    controlSock.close();
                    tvStatus = "Отказано в соединении";
                    return -4;
                }
            } catch (Exception e1) {
                Log.e("FATAL","Socket wasn't closed");
                tvStatus = "Ошибка при закрытии сокета";
                return -5;
            }
        }

        try {

            int ansCode = dis.readInt();
            Log.i("FILELEN", Integer.toString(ansCode));
            if (ansCode == -10) {
                Log.i("NOPE", "Nope");
                try {
                    if (!controlSock.isClosed()) {
                        controlSock.close();
                        tvStatus = "Отказано в соединении";
                        return -4;
                    }
                } catch (Exception e) {
                    Log.e("FATAL","Socket wasn't closed");
                    tvStatus = "Ошибка при закрытии сокета";
                    return -5;
                }
            } else if(ansCode != 10) {
                Log.i("NOPE", "We got smth wrong");
                try {
                    if (!controlSock.isClosed()) {
                        controlSock.close();
                        tvStatus = "Получено неверное сообщение";
                        return -4;
                    }
                } catch (Exception e) {
                    Log.e("FATAL","Socket wasn't closed");
                    tvStatus = "Ошибка при закрытии сокета";
                    return -5;
                } // process?
            }
        } catch (Exception e) {
            Log.e("FATAL","Failed to receive datagram");
            tvStatus = "Не удалось получить ответ от телевизора";
            return -6;

        }

        try {
            if (!controlSock.isClosed()) controlSock.close();
        } catch (Exception e) {
            Log.e("FATAL","Socket wasn't closed");
            tvStatus = "Ошибка при закрытии сокета";
            return -7;
        }

        tvStatus = "Соединение с " + addria + " установлено";
        Log.i("FILELEN", "1" + tvStatus);
        return 0;
    }

    @Override
    protected void onPostExecute(Integer result) {
    }
}
