package com.petrsu.se.secretary;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

public class VkGetFileRequest extends AsyncTask<ArrayList<String>, Void, Integer> {
    public String docUrl = "";
    public int requestResult = 1;
    public int respCode = 0;
    @Override
    protected Integer doInBackground(ArrayList<String>... docList) {
        URL vkApiUrl;
        HttpsURLConnection vkConnection;
        String fileName = "";
        String currentVkUserToken = "";

        ArrayList<String> receivedList = docList[0];

        /* Буквы Ё гугл не читает, все файлы именуем только с Е */
        fileName = receivedList.get(0);
        if(fileName.endsWith(" ")) { // распознавалка гугла цепляет к последнему слову пробел, уберём его для нормального поиска
            fileName = fileName.substring(0, fileName.length() - 1);
        }
        currentVkUserToken = receivedList.get(1);
        Log.d("VKTEST", currentVkUserToken);

        try {
            vkApiUrl = new URL("https://api.vk.com/method/docs.get?owner_id=-174962845" +
                    "&access_token=" + currentVkUserToken + "&v=5.101");
            Log.d("VKTEST", "URL success");
        }  catch (Exception e) {
            e.printStackTrace();
            requestResult = -1;
            return -1;
        }
        try {
            vkConnection = (HttpsURLConnection) vkApiUrl.openConnection();
            vkConnection.setRequestProperty("User-Agent", "com-petrsu-se-secretary");
            Log.d("VKTEST", "Connect success");
        }
        catch (Exception e) {
            e.printStackTrace();
            requestResult = -2;
            return -2;
        }
        try {
            respCode = vkConnection.getResponseCode();
            if(respCode == 200) {
                Log.d("VKTEST", "200");
                InputStream vis = vkConnection.getInputStream();
                InputStreamReader visr = new InputStreamReader(vis, "UTF-8");
                BufferedReader vkbr = new BufferedReader(visr);
                StringBuilder vksb = new StringBuilder();
                String jsonOneString = "";
                while ((jsonOneString = vkbr.readLine()) != null) {
                    vksb.append(jsonOneString);
                    vksb.append("\n");
                }
                String jsonFull = vksb.toString();
                Log.d("VKTEST", jsonFull);
                JSONObject jsonResponse = new JSONObject(jsonFull);
                JSONObject response = jsonResponse.getJSONObject("response");
                JSONArray docArray = response.getJSONArray("items");
                for(int i = 0; i < docArray.length(); i++) {
                    String nameWithoutExt = docArray.getJSONObject(i).getString("title").split("\\.")[0]; // убираем расширение
                    if(fileName.equalsIgnoreCase(nameWithoutExt)) {
                        docUrl = docArray.getJSONObject(i).getString("url");
                        break;
                    }
                }
                Log.d("VKTEST", docArray.getJSONObject(0).getString("url"));
            } else {
                Log.d("VKTEST", String.valueOf(respCode));
                vkConnection.disconnect();
                requestResult = -3;
                return -3;
            }
        } catch(Exception e) {
            e.printStackTrace();
            vkConnection.disconnect();
            requestResult = -4;
            return -4;
        }
        vkConnection.disconnect();
        requestResult = 0;
        return 0;
    }
}
