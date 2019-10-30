package com.petrsu.se.secretary;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.vk.api.sdk.VK;
import com.vk.api.sdk.auth.VKAccessToken;
import com.vk.api.sdk.auth.VKAuthCallback;
import com.vk.api.sdk.auth.VKScope;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int VR_REQUEST = 999;
    private static final int STORAGE_REQUEST_CODE = 102;
    private static final int RECORD_REQUEST_CODE = 103;
    private TextToSpeech repeatTTS;
    private Button speakButton;
    private boolean hasTelephony = false;
    private String currentVkUserToken = ""; // токен текущей сессии для работы с пользовательскими методами api
    private boolean screenRecordWorking = false;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private ScreenRecorder screenRecorder;
    private DataTransfer dt = null;

    public void speak(String text) {
        repeatTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null, String.valueOf(System.currentTimeMillis()));
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

        repeatTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int initStatus) {
                if(initStatus == TextToSpeech.SUCCESS) {
                    repeatTTS.setPitch(1.3f);
                    repeatTTS.setSpeechRate(1.0f);
                    repeatTTS.setLanguage(new Locale("ru"));//Язык
                }
            }
        });

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

        // авторизуемся в вк для работы с документами
        List<VKScope> scopes = new ArrayList<>();
        scopes.add(VKScope.DOCS);
        VK.login(this, scopes);

        // готовим сервис и файл для записи экрана
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }

        File outFile = new File("/data/user/0/com.petrsu.se.secretary/record.mp4"); // TODO: shift for any devices

        Log.d("FILE", outFile.getAbsolutePath());
        if (outFile.exists()) {
            if (outFile.delete()) {
                Log.d("RECORD", "Deleted in STA");
            }  else Log.e("RECORD", "File delete issues in STA");
        }
        if (!outFile.exists()) {
            try {
                if (outFile.createNewFile()) {
                    Log.d("RECORD", "Created in STA");
                } else Log.e("RECORD", "File create issues in STA");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, RECORD_REQUEST_CODE);
        Intent recIntent = new Intent(this, ScreenRecorder.class);
        bindService(recIntent, connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
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
        // проверим авторизацию вк
        if(!VK.onActivityResult(requestCode, resultCode, data, new VKAuthCallback() {
            @Override
            public void onLogin(@NotNull VKAccessToken vkAccessToken) {
                currentVkUserToken = vkAccessToken.getAccessToken(); // берём текущий токен для работы с документами от имени пользователя
                speak("Вы авторизованы ВКонтакте. Теперь можно работать с документами.");
            }

            @Override
            public void onLoginFailed(int i) {
                speak("Ошибка авторизации ВКонтакте, работа с документами недоступна.");
            }
        }))

        //проверяем результат распознавания речи
        if(requestCode == VR_REQUEST && resultCode == RESULT_OK)
        {
        //Добавляем распознанные слова в список результатов
            String suggestedCommand =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
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
                                   String[] contactNameWords = contactName.split(" ");
                                   String contactPatternString = "";
                                   for(int i = 0; i < contactNameWords.length; i++) {
                                       if(i != contactNameWords.length - 1) {
                                           contactPatternString += contactNameWords[i].substring(0, contactNameWords[i].length() - 2) + ". ";
                                       } else contactPatternString += contactNameWords[i].substring(0, contactNameWords[i].length() - 4) + ".{3}"; // фамилий на -ский/-ская
                                   }
                                   Log.d("PHONEBOOK", contactPatternString);
                                   Pattern namePattern = Pattern.compile(contactPatternString, Pattern.CASE_INSENSITIVE);
                                   Log.d("PHONEBOOK", "Regex: " + namePattern.toString());
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
                                                   speak("У контакта нет номера.");
                                               speak("Найден контакт " + nameInBook + " с номером " + phoneNumber);
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
                               } else speak("Вы не разрешили звонить");
                           } else speak("Вы не разрешили использовать телефонную книгу");
                       } else speak("Невозможно совершать звонки на этом устройстве");
                   } else speak( "Абонент не назван");
               } else if(commandParts[0].equalsIgnoreCase("отправь") && commandParts[1].equalsIgnoreCase("сообщение")) {
                   String messageToSend = "";
                   for(int i = 2; i < commandParts.length; i++) {
                       messageToSend += commandParts[i] + " ";
                   }
                   if(messageToSend != "") {
                       VkMessageRequest vmr = new VkMessageRequest();
                       vmr.execute(messageToSend);
                       try {
                           Thread.sleep(1000);

                           if(vmr.requestResult == 0) {
                               speak( "Сообщение отправлено.");
                           } else if(vmr.requestResult == -1) {
                               speak("Не удалось сформировать запрос.");
                           } else if(vmr.requestResult == -2) {
                               speak("Не удалось установить соединение с ВКонтакте.");
                           } else if(vmr.requestResult == -3) {
                               speak("Ошибка HTTP-запроса с кодом " + vmr.respCode + ".");
                           } else if(vmr.requestResult == -4) {
                               speak("Ошибка при отправке сообщения.");
                           } else if(vmr.requestResult == 1) {
                               speak("Сбой при запуске отправки сообщения.");
                           }
                       } catch (Exception e) {
                           e.printStackTrace();
                           speak("Ошибка при попытке сформировать запрос на отправку сообщения.");
                       }
                   } else speak("Сообщение не задано.");
               } else if(commandParts[0].equalsIgnoreCase("найди") && commandParts[1].equalsIgnoreCase("документ")) {
                   String fileName = "";
                   for(int i = 2; i < commandParts.length; i++) {
                       fileName += commandParts[i] + " ";
                   }
                   if(fileName != "") {
                       VkGetFileRequest vfr = new VkGetFileRequest();
                       ArrayList<String> docArgs = new ArrayList<>();
                       docArgs.add(fileName);
                       docArgs.add(currentVkUserToken);
                       vfr.execute(docArgs); // для асинков проверка кода возврата через свойство; через интерфейс неудобно, т.к. нужно обращаться к полям объекта задачи, для чего нужно его как-то пробрасывать
                       try {
                           Thread.sleep(3000);

                           if(vfr.requestResult == 0) {
                               if (vfr.docUrl != "") {
                                   String docUrl = vfr.docUrl;
                                   speak("Файл " + fileName + " найден.");
                                   Intent docOpenIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(docUrl)); // TODO: нормальный выброс url
                                   docOpenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                   docOpenIntent.setPackage("com.android.chrome");
                                   this.startActivity(docOpenIntent);
                               } else speak("Файл " + fileName + " не найден.");
                           } else if(vfr.requestResult == -1) {
                               speak("Не удалось сформировать запрос.");
                           } else if(vfr.requestResult == -2) {
                               speak("Не удалось установить соединение с ВКонтакте.");
                           } else if(vfr.requestResult == -3) {
                               speak("Ошибка HTTP-запроса с кодом " + vfr.respCode + ".");
                           } else if(vfr.requestResult == -4) {
                               speak("Ошибка при получении документа.");
                           } else if(vfr.requestResult == 1) {
                           speak("Сбой при запуске получения документа.");
                       }
                       } catch (Exception e) {
                           e.printStackTrace();
                           speak("Ошибка при попытке сформировать запрос на документ.");
                       }
                   } else speak("Имя файла не задано.");
               } else if(commandParts[0].equalsIgnoreCase("запусти") && commandParts[1].equalsIgnoreCase("трансляцию")
                       && commandParts[2].equalsIgnoreCase("экрана")) {
                   if (!screenRecordWorking) {
                       TVStatusChecker tvc = new TVStatusChecker();
                       tvc.execute("192.168.0.102"); // TODO: IP из настроек

                       try {
                           speak("Проверка статуса приёмника...");
                           Thread.sleep(3000);
                       } catch (Exception e) {
                           speak(tvc.tvStatus);
                           e.printStackTrace();
                       }

                       if (tvc.tvStatus.contains("Соединение установлено")) {
                           speak(tvc.tvStatus);
                           screenRecordWorking = true;

                           screenRecorder.startRecord();
                           dt = new DataTransfer(screenRecorder);
                           dt.execute("192.168.0.102");
                           // задержка, чтобы проверить, всё ли хорошо с передачей
                           try {
                               speak("Начало записи видео. Пожалуйста, подождите...");
                               Thread.sleep(5000);
                               if(dt.requestResult != 0) {
                                   if(dt.requestResult == -1) {
                                       speak("Задан некорректный IP-адрес. Проверьте настройки.");
                                   } else if(dt.requestResult == -2) {
                                       speak("Ошибка при создании сокета");
                                   } else if(dt.requestResult == -3) {
                                       speak("Ошибка при начале записи");
                                   }
                               } else speak("Запись успешно начата, можно продолжать работу.");
                           } catch (Exception e) {
                               e.printStackTrace();
                               speak("Ошибка при попытке записи видео");
                           }
                       } else {
                           speak(tvc.tvStatus);
                       }
                   } else {
                       speak("Трансляция экрана уже идёт.");
                   }
               }  else if(commandParts[0].equalsIgnoreCase("останови") && commandParts[1].equalsIgnoreCase("трансляцию")
                       && commandParts[2].equalsIgnoreCase("экрана")) {
                   if(screenRecordWorking) {
                       dt.working = false;
                       screenRecordWorking = false;
                       speak("Трансляция остановлена.");
                   } else {
                       speak("Трансляция экрана не запущена.");
                   }
               }
            }
        }

        if (requestCode == RECORD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                screenRecorder.setMediaProject(mediaProjection);
            } else {
                Log.e("RESULT CODE", Integer.toString(resultCode));
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

    private ServiceConnection connection = new ServiceConnection() { // соединение с сервисом записи экрана
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            ScreenRecorder.RecordBinder binder = (ScreenRecorder.RecordBinder) service;
            screenRecorder = binder.getScreenRecorder();
            Log.e("SCREEEN", "recorder");
            screenRecorder.setConfig(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi);
            screenRecorder.setMediaProject(mediaProjection);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };
}
