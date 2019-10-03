package com.petrsu.se.secretary;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogActivity extends AppCompatActivity implements View.OnClickListener, TextToSpeech.OnInitListener {
    private static final int VR_REQUEST = 999;
    private int MY_DATA_CHECK_CODE = 0;
    private TextToSpeech repeatTTS;
    private Button speakButton;
    private boolean hasTelephony = false;

    public void onInit(int initStatus){
        if(initStatus == TextToSpeech.SUCCESS)
            repeatTTS.setLanguage(new Locale("ru"));//Язык
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog);
        speakButton = (Button) findViewById(R.id.speakButton);
        //проверяем, поддерживается ли распознавание речи
        PackageManager packManager = getPackageManager();
        List<ResolveInfo> intActivities= packManager.queryIntentActivities(new
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),0);
        if(intActivities.size() != 0){
            // распознавание поддерживается, будем отслеживать событие щелчка по кнопке
            speakButton.setOnClickListener(this);
        }
        else
        {
            speakButton.setEnabled(false);
            speakButton.setText("Распознавание речи не поддерживается на Вашем устройстве");
        }
        // проверка фишек
        PackageManager packageManager = getPackageManager();
        hasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if (!hasTelephony) Toast.makeText(this, "Невозможно совершать звонки на этом устройстве", Toast.LENGTH_LONG).show();
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.READ_CONTACTS }, 111);
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CALL_PHONE }, 121);
        }
        VkRestRequest vrr = new VkRestRequest(); // пробуем тупой пост-запрос
        vrr.execute();
        /*VK.initialize(this);
        ArrayList<VKScope> scopes = new ArrayList<VKScope>();
        scopes.add(VKScope.MESSAGES);
        VK.setCredentials(this, 138569761, "f9fe9ed3100e140135bbb150b4db60c3fdbbc490db8aa20163d6d6c2abcb359d0e09a603e0538fdd7cf11", "test", true);
        VK.login(this, scopes);*/
    }

    public void onClick(View view) {
        if (view.getId() == R.id.speakButton) {
            recognizeCommand(view);
        }
    }

    protected void recognizeCommand(View view) {
        // проверяем подключение к сети
        if(checkConnection(this)) {
            // если есть, распознаём речь
            Intent listenIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            listenIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, // пакет
                    getClass().getPackage().getName());
            listenIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, // модель распознавания речи
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            listenIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            startActivityForResult(listenIntent, VR_REQUEST);
        } else Toast.makeText(this,"Пожалуйста, проверьте соединение с Интернетом", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        // проверка авторизации вк
        /*if (!VK.onActivityResult(requestCode, resultCode, data, new VKAuthCallback() {
            @Override
            public void onLogin(@NotNull VKAccessToken vkAccessToken) {
                Log.d("VKTEST", "Success");
                VkRestRequest vrr = new VkRestRequest();
                vrr.execute();
            }

            @Override
            public void onLoginFailed(int i) {
                Log.d("VKTEST", "Fail");
            }
        }))*/
        //проверяем результат распознавания речи
        if(requestCode == VR_REQUEST && resultCode == RESULT_OK)
        {
        //Добавляем распознанные слова в список результатов
            String suggestedCommand =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            /*String variants = "";
            for (int i = 0; i < suggestedWords.size(); i++) {
                variants += suggestedWords.get(i) + " ";
            }*/
            //Toast.makeText(this, "Вы сказали: " + suggestedCommand, Toast.LENGTH_LONG).show();
            // разбиваем полученную фразу на слова и пробегаем по списку команд
            String[] commandParts = suggestedCommand.split(" ");
            if (!commandParts[0].equals("")) {
               if(commandParts[0].equalsIgnoreCase("позвони")) {
                   String contactName = "";
                   for (int i = 1; i < commandParts.length; i++) {
                       contactName += commandParts[i] + " ";
                   }
                   if(!contactName.equals("")) {
                       if (hasTelephony) {
                           if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                               if(ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                   boolean contactFound = false;
                                   Pattern namePattern = Pattern.compile(contactName.substring(0, contactName.length() - 2) + "*", Pattern.CASE_INSENSITIVE);
                                   Log.d("PHONEBOOK", "Regex: " + namePattern.toString()); // TODO: многословные контакты и предлоги
                                   String phoneNumber = "";
                                   Uri ContentUri = ContactsContract.Contacts.CONTENT_URI;
                                   String Id = ContactsContract.Contacts._ID;
                                   String DisplayName = ContactsContract.Contacts.DISPLAY_NAME;
                                   String HasPhoneNumber = ContactsContract.Contacts.HAS_PHONE_NUMBER;

                                   Uri PhoneContentUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                                   String PhoneContactId = ContactsContract.CommonDataKinds.Phone.CONTACT_ID;
                                   String Number = ContactsContract.CommonDataKinds.Phone.NUMBER;

                                   ContentResolver contentResolver = getContentResolver();
                                   Cursor cursor = contentResolver.query(ContentUri, null, null, null, null);
                                   if (cursor.getCount() > 0) {
                                       while (cursor.moveToNext()) {
                                           String contactId = cursor.getString(cursor.getColumnIndex(Id));
                                           String nameInBook = cursor.getString(cursor.getColumnIndex(DisplayName));
                                           Log.d("PHONEBOOK", nameInBook);
                                           Matcher m = namePattern.matcher(nameInBook);
                                           if (m.matches()) {
                                               //Log.d("PHONEBOOK", nameInBook + "kk");
                                               int hasPhoneNumber = Integer.parseInt(cursor.getString(cursor.getColumnIndex(HasPhoneNumber)));
                                               if (hasPhoneNumber > 0) {
                                                   Cursor phoneCursor = contentResolver.query(PhoneContentUri, null,
                                                           PhoneContactId + " = ?", new String[]{contactId}, null);
                                                   while (phoneCursor.moveToNext()) {
                                                       phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(Number));
                                                   }
                                                   contactFound = true;
                                               } else
                                                   Toast.makeText(this, "У контакта нет номера", Toast.LENGTH_LONG).show();
                                               Toast.makeText(this, "Найден контакт " + nameInBook + " с номером " + phoneNumber, Toast.LENGTH_LONG).show();
                                               cursor.close();
                                               break;
                                           }
                                       }
                                       if (!cursor.isClosed()) cursor.close();
                                       // звоним
                                       if (contactFound) {
                                           Intent intent = new Intent(Intent.ACTION_CALL);
                                           intent.setData(Uri.parse("tel:" + Uri.encode(phoneNumber))); // кодирование решётки для ussd
                                           startActivity(intent);
                                       }
                                   }
                               } else Toast.makeText(this, "Вы не разрешили звонить", Toast.LENGTH_LONG).show();
                           } else Toast.makeText(this, "Вы не разрешили использовать телефонную книгу", Toast.LENGTH_LONG).show();
                       } else Toast.makeText(this, "Невозможно совершать звонки на этом устройстве", Toast.LENGTH_LONG).show();
                   } else Toast.makeText(this, "А кому звонить?", Toast.LENGTH_LONG).show();
               }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected boolean checkConnection(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }
}
