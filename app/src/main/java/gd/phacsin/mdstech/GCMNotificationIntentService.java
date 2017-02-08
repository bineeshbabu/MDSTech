package gd.phacsin.mdstech;

/**
 * Created by SGD on 10/4/2015.
 */
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.scottyab.aescrypt.AESCrypt;
import com.snappydb.DB;
import com.snappydb.DBFactory;
import com.snappydb.SnappydbException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;

public class GCMNotificationIntentService extends IntentService {
    // Sets an ID for the notification, so it can be updated
    public static final int notifyID = 9001;
    NotificationCompat.Builder builder;
    boolean flag=false;
    RequestParams params = new RequestParams();
    String data;
    String main_id[];
    String password = "mdstech";
    JSONObject jsonObject;
    public GCMNotificationIntentService() {
        super("GcmIntentService");
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);

        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR
                    .equals(messageType)) {
                sendNotification("Alert","Send error: " + extras.toString());
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED
                    .equals(messageType)) {
                sendNotification("Alert","Deleted messages on server: "
                        + extras.toString());
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE
                    .equals(messageType)) {
                //sendNotification(String.valueOf(extras.get(ApplicationConstants.MSG_KEY)));
            }
        }
        String json_string = String.valueOf(extras.get(ApplicationConstants.MSG_KEY));
        try {
            jsonObject = new JSONObject(json_string);
            if (jsonObject.getString("type").equals("Request")) {
                try {
                    sendNotification("Data Request",json_string);
                    DB snappydb = DBFactory.open(getApplicationContext());
                    main_id=snappydb.findKeys("main_id");
                    for(int i=0;i<main_id.length;i++)
                        if(jsonObject.getString("main_id").equals(snappydb.get(main_id[i])))
                            data = AESCrypt.decrypt(password, snappydb.get("data:"+i));
                    registerInBackground();
                } catch (SnappydbException e) {
                    Log.d("snappy", e.toString());
                } catch (GeneralSecurityException e) {

                }
            } else {
                sendNotification("Insert",json_string);
                try {
                    DB snappydb = DBFactory.open(getApplicationContext());
                    int count = snappydb.countKeys("main_id");
                    if (count == 0) {
                        snappydb.put("main_id:0", jsonObject.getString("main_id"));
                        snappydb.put("sub_id:0", jsonObject.getString("sub_id"));
                        snappydb.put("data:0", AESCrypt.encrypt(password, jsonObject.getString("data")));
                    }
                    else if(count==20)
                    {
                        sendNotification("Alert","Maximum Data Size Reached");
                    }
                    else {
                        main_id = snappydb.findKeys("main_id");
                        for(int i=0;i<main_id.length;i++)
                            if(jsonObject.getString("main_id").equals(snappydb.get(main_id[i]))&&jsonObject.getString("sub_id").equals(snappydb.get("sub_id:"+i)))
                            {
                                flag=true;
                                snappydb.put("data:" + i, AESCrypt.encrypt(password, jsonObject.getString("data")));
                                break;
                            }
                        if(!flag) {
                            snappydb.put("main_id:" + String.valueOf(count), jsonObject.getString("main_id"));
                            snappydb.put("sub_id:" + String.valueOf(count), jsonObject.getString("sub_id"));
                            snappydb.put("data:" + String.valueOf(count), AESCrypt.encrypt(password, jsonObject.getString("data")));
                        }
                    }

                } catch (SnappydbException e) {

                } catch (GeneralSecurityException e) {

                }
            }
        } catch (JSONException e) {

        }

    }

    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... pars) {
                AsyncHttpClient client = new AsyncHttpClient();
                try {
                    SharedPreferences prefs = getSharedPreferences("UserDetails",
                            Context.MODE_PRIVATE);
                    Log.d("pref",prefs.getString("regID",""));
                    String URL="http://www.phacsin.com/mds/insert.php";
                    String uri = Uri.parse(URL)
                            .buildUpon()
                            .appendQueryParameter("main_id", jsonObject.getString("main_id"))
                            .appendQueryParameter("gcm_id", prefs.getString("regID",""))
                            .appendQueryParameter("info", data)
                            .build().toString();
                    Log.d("URL",uri);
                    client.get(uri, params, new AsyncHttpResponseHandler() {
                        // When the response returned by REST has Http
                        // response code '200'
                        @Override
                        public void onSuccess(String response) {
                            Log.d("key", "Success");
                        }

                        @Override
                        public void onFailure(int statusCode, Throwable error,
                                              String content) {

                        }
                    });
                }catch (Exception e)
                {
                    e.printStackTrace();
                }
                return "";
            }

            @Override
            protected void onPostExecute(String msg) {

            }
        }.execute(null, null, null);
    }

    private void sendNotification(String title,String msg) {
        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.putExtra("msg", msg);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0,
                resultIntent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.Builder mNotifyBuilder;
        NotificationManager mNotificationManager;

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotifyBuilder = new NotificationCompat.Builder(this)
                .setContentTitle(title)
                .setContentText("You've received new message.")
                .setSmallIcon(R.drawable.ic_launcher);
        // Set pending intent
        mNotifyBuilder.setContentIntent(resultPendingIntent);

        // Set Vibrate, Sound and Light
        int defaults = 0;
        defaults = defaults | Notification.DEFAULT_LIGHTS;
        defaults = defaults | Notification.DEFAULT_VIBRATE;
        defaults = defaults | Notification.DEFAULT_SOUND;

        mNotifyBuilder.setDefaults(defaults);
        // Set the content for Notification
        mNotifyBuilder.setContentText(msg);
        // Set autocancel
        mNotifyBuilder.setAutoCancel(true);
        // Post a notification
        mNotificationManager.notify(notifyID, mNotifyBuilder.build());
    }

}