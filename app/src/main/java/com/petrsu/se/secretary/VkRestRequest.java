package com.petrsu.se.secretary;

import android.os.AsyncTask;
import android.util.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

public class VkRestRequest extends AsyncTask<String, Void, Integer> {
    @Override
    protected Integer doInBackground(String... args) {
        URL vkApiUrl;
        HttpsURLConnection vkConnection;
        long randomId = 0;
        String idString = "";
        String messageToSend = "";

        for(String part : args) {
            messageToSend += part;
        }

        Random idRandom = new Random(System.currentTimeMillis());
        randomId = idRandom.nextLong();
        idString = String.valueOf(randomId);

        /* Что делать с ключом? При попытке взять его из strings пишет, что invalid */
        try {
            vkApiUrl = new URL("https://api.vk.com/method/messages.send?peer_id=2000000001&message=" + messageToSend +
                    "&access_token=" + new Token().token + "&v=5.101" +
                    "&random_id=" + idString);
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
            }
        } catch(Exception e) {
            e.printStackTrace();
            vkConnection.disconnect();
            return -3;
        }
        vkConnection.disconnect();
        return 0;
    }
}
