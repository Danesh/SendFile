package com.danesh.sendfile;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import com.dropbox.client.DropboxAPI;
import com.dropbox.client.DropboxAPI.Config;
import com.dropbox.client.DropboxAPI.Entry;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.IBinder;

public class LocalService extends Service {
	private Timer timer = new Timer();
	String interval,folder;
	public DropboxAPI api = new DropboxAPI();
	public boolean mLoggedIn;
	NotificationManager manager;
	public DropboxSample toc = new DropboxSample();
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	@Override
	public void onCreate() {
		super.onCreate();
		SharedPreferences settings = getSharedPreferences("prefs", 0);
		interval = settings.getString("interval", "60");
		folder = settings.getString("folder", "/send");
		authenticate();
		displayNotification(1011,"Service running","Status : Active");
		timer.scheduleAtFixedRate( new TimerTask() {
			public void run() {
				DropboxAPI.Entry dbe = api.metadata("dropbox", folder, 10000, null, true);
				if (dbe!=null){
					List<Entry> contents = dbe.contents;
					if (contents!=null && contents.size()>0){
						Intent dialogIntent = new Intent(getBaseContext(), DropboxSample.class);
						dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						dialogIntent.putExtra("status", "1");
						System.out.println("Found Apk");
						startActivity(dialogIntent);
						onDestroy();
					}
				}
			}
		}, 0, Integer.parseInt(interval)*1000);
		;
	}
	
	public void displayNotification(int id, String ticker, String msg){
		manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notification1 = new Notification(R.drawable.icon, ticker, System.currentTimeMillis());
		notification1.flags = Notification.FLAG_AUTO_CANCEL;
		PendingIntent contentIntent = PendingIntent.getActivity(this, id, new Intent(this, DropboxSample.class), 0);
		notification1.setLatestEventInfo(this, ticker, msg, contentIntent);
		manager.notify(id, notification1);
	}

	@Override
	public void onDestroy() {
		manager.cancelAll();
		timer.cancel();
		super.onDestroy();
	}

	public void setLoggedIn(boolean loggedIn) {
		mLoggedIn = loggedIn;
	}

	public boolean authenticate() {
		if (toc.mConfig == null) {
			toc.mConfig = getConfig();
		}
		String keys[] = getKeys();
		if (keys != null) {
			toc.mConfig = api.authenticateToken(keys[0], keys[1], toc.mConfig);
			if (toc.mConfig != null) {
				return true;
			}
		}
		clearKeys();
		setLoggedIn(false);
		return false;
	}

	public Config getConfig() {
		if (toc.mConfig == null) {
			toc.mConfig = api.getConfig(null, false);
			toc.mConfig.consumerKey=toc.CONSUMER_KEY;
			toc.mConfig.consumerSecret=toc.CONSUMER_SECRET;
			toc.mConfig.server="api.dropbox.com";
			toc.mConfig.contentServer="api-content.dropbox.com";
			toc.mConfig.port=80;
		}
		return toc.mConfig;
	}

	public String[] getKeys() {
		SharedPreferences prefs = getSharedPreferences(toc.ACCOUNT_PREFS_NAME, 0);
		String key = prefs.getString(toc.ACCESS_KEY_NAME, null);
		String secret = prefs.getString(toc.ACCESS_SECRET_NAME, null);
		if (key != null && secret != null) {
			String[] ret = new String[2];
			ret[0] = key;
			ret[1] = secret;
			return ret;
		} else {
			return null;
		}
	}

	public void clearKeys() {
		SharedPreferences prefs = getSharedPreferences(toc.ACCOUNT_PREFS_NAME, 0);
		Editor edit = prefs.edit();
		edit.clear();
		edit.commit();
	} 
}