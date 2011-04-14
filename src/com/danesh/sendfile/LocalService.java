package com.danesh.sendfile;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.dropbox.client.DropboxAPI;
import com.dropbox.client.DropboxAPI.Entry;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;


public class LocalService extends Service {
	private Timer timer = new Timer();
	private DropboxAPI api = new DropboxAPI();
    @Override
    public IBinder onBind(Intent arg0) {
          return null;
    }
    @Override
    public void onCreate() {
          super.onCreate();
          showToast("AAAAAAAAAAAAAAAAAAAAA");
          NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
          Notification notification = new Notification(R.drawable.icon, "HEY", System.currentTimeMillis());
          PendingIntent contentIntent = PendingIntent.getActivity(this, 1, new Intent(this, kill()), 0);
          notification.setLatestEventInfo(this, "Title here", ".. And here's some more details..", contentIntent);
          manager.notify(1, notification);
              timer.scheduleAtFixedRate( new TimerTask() {
                  public void run() {
                      Log.d("servy", "This proves that my service works.");
                  }
              }, 0, 5);
          ;
          
    }
    public void showToast(String msg) {
		Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
		error.show();
	}
    public Runnable onRun(){
    	showToast("HEY");
		return null;
    }
    public Class<?> kill(){
    	android.os.Process.killProcess(android.os.Process.myPid());
		return null;
    }
    @Override
    public void onDestroy() {
          super.onDestroy();
          Toast.makeText(this, "Service destroyed ...", Toast.LENGTH_LONG).show();
    }
}