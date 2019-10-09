package com.petrsu.se.secretary;

import android.os.AsyncTask;
import android.util.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

public class VkGetFileRequest extends AsyncTask<ArrayList<String>, Void, Integer> {
    @Override
    protected Integer doInBackground(ArrayList<String>... docList) {
        URL vkApiUrl;
        HttpsURLConnection vkConnection;
        String fileName = "";
        String currentVkUserToken = "";

        ArrayList<String> receivedList = docList[0];

        fileName = receivedList.get(0);
        currentVkUserToken = receivedList.get(1);
        Log.d("VKTEST", currentVkUserToken);

        /* пока просто поиск без скачивания */
        try {
            vkApiUrl = new URL("https://api.vk.com/method/docs.get?count=1&owner_id=-174962845" +
                    "&access_token=" + currentVkUserToken + "&v=5.101"); // парсим жсон, ищем по имени
            Log.d("VKTEST", "URL success");
        }  catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        try {
            vkConnection = (HttpsURLConnection) vkApiUrl.openConnection();
            vkConnection.setRequestProperty("User-Agent", "com-petrsu-se-secretary");
            Log.d("VKTEST", "Connect success");
        }
        catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
        try {
            int respCode = vkConnection.getResponseCode();
            if(respCode == 200) {
                Log.d("VKTEST", "200");
                InputStream vis = vkConnection.getInputStream();
                InputStreamReader visr = new InputStreamReader(vis, "UTF-8");
                char[] test = new char[255];
                //JsonReader jsonVk = new JsonReader(visr); // TODO: нормальный парсер надо бы (проверка на ошибки и всё такое)
                //jsonVk.beginObject();
                visr.read(test, 0, 255);
                String test1 = "";
                for (int i = 0; i < 255; i++) {
                    test1 += test[i];
                }
                Log.d("VKTEST", test1);
                //jsonVk.close();*/
            } else {
                Log.d("VKTEST", String.valueOf(respCode));
                vkConnection.disconnect();
                return -3;
            }
        } catch(Exception e) {
            e.printStackTrace();
            vkConnection.disconnect();
            return -4;
        }
        vkConnection.disconnect();
        return 0;
    }
}
