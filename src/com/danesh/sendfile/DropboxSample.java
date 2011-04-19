package com.danesh.sendfile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.danesh.sendfile.R;
import com.dropbox.client.DropboxAPI;
import com.dropbox.client.DropboxAPI.Config;
import com.dropbox.client.DropboxAPI.Entry;
import com.dropbox.client.DropboxAPI.FileDownload;


public class DropboxSample extends Activity {

	public static final String TAG = "DropboxSample";
	private Button mSubmit,enable,disable,save;
	Intent mine;
	public String appname;
	public ProgressDialog dialog;
	final static public String CONSUMER_KEY = "";
	final static public String CONSUMER_SECRET = "";
	private DropboxAPI api = new DropboxAPI();
	final static public String ACCOUNT_PREFS_NAME = "prefs";
	final static public String ACCESS_KEY_NAME = "ACCESS_KEY";
	final static public String ACCESS_SECRET_NAME = "ACCESS_SECRET";
	private boolean mLoggedIn;
	private EditText mLoginEmail, mLoginPassword, interval, folder;
	public Config mConfig;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		mLoginEmail = (EditText)findViewById(R.id.login_email);
		mLoginPassword = (EditText)findViewById(R.id.login_password);
		interval = (EditText)findViewById(R.id.interval);
		folder = (EditText)findViewById(R.id.folder);
		mSubmit = (Button)findViewById(R.id.login_submit);
		enable = (Button)findViewById(R.id.enable);
		disable = (Button)findViewById(R.id.disable);
		save = (Button)findViewById(R.id.save);
		SharedPreferences settings = getSharedPreferences("prefs", 0);
		interval.setText(settings.getString("interval", "60"));
		folder.setText(settings.getString("folder", "/send"));
		mine = new Intent(this, LocalService.class);
		mSubmit.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (mLoggedIn) {
					api.deauthenticate();
					clearKeys();
					setLoggedIn(false);
				} else {
					getAccountInfo();
				}
			}
		});
		save.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				SharedPreferences settings = getSharedPreferences("prefs", 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.putString("folder",folder.getText().toString());
				editor.putString("interval", interval.getText().toString());
				showToast("Preferences Saved");
				stopService(mine);
				startService(mine);
				editor.commit();
			}
		});
		enable.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startService(mine);
			}
		});
		disable.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				stopService(mine);
			}
		});
		String[] keys = getKeys();
		if (keys != null) {
			setLoggedIn(true);
		} else {
			setLoggedIn(false);
		}
		if (authenticate()) {
			mSubmit.setEnabled(false);
			enable.setEnabled(true);
			disable.setEnabled(true);
			Intent abc = getIntent();
			String path = "'";
			if (abc!=null)
				if (abc.getExtras()!=null){
					path = abc.getExtras().getString("status");
				}
			if (path.contains("1"))
				initialize(folder.getText().toString());
		}else{
			mSubmit.setEnabled(true);
			enable.setEnabled(false);
			disable.setEnabled(false);
		}

	}

	public void initialize(String path){
		DropboxAPI.Entry dbe = api.metadata("dropbox", path, 10000, null, true);
		if (dbe!=null){
			List<Entry> contents = dbe.contents;
			if (contents!=null && contents.size()>0){
				int result = Settings.Secure.getInt(getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0);
				if (result == 0) {
					showToast("Please enable unknown sources.");
					Intent intent = new Intent();
					intent.setAction(Settings.ACTION_APPLICATION_SETTINGS);
					startActivity(intent);
				}else{
					listPop(contents);
				}
			}
		}
	}
	public boolean isEmpty(){
		DropboxAPI.Entry dbe = api.metadata("dropbox", folder.getText().toString(), 10000, null, true);
		List<Entry> contents = dbe.contents;
		if (contents==null){
			return true;
		}else{
			return false;
		}
	}
	public void onBackPressed() {
		finish();
		//android.os.Process.killProcess(android.os.Process.myPid());
		return;
	}
	public void restart(){
		Intent mainIntent = new Intent(this, DropboxSample.class);
		mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(mainIntent);
		finish();
	}
	public void listPop(List<Entry> pack){
		final String[] dirlist = new String[pack.size()];
		for (int a=0;a<pack.size();a++){
			dirlist[a] = pack.get(a).fileName();
		};
		if (dirlist.length>=1){
			final File apk = new File("/sdcard/" + dirlist[0]);
			displayNotification(1001,"Downloading App","Syncing...");
			Thread temp = new Thread(new Runnable() {
				public void run() {
					try {downloadDropboxFile(folder.getText().toString() + "/" + dirlist[0],apk);} catch (IOException e) {e.printStackTrace();}
					String fileName = Environment.getExternalStorageDirectory() + "/" + dirlist[0];
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setDataAndType(Uri.fromFile(new File(fileName)), "application/vnd.android.package-archive");
					appname = dirlist[0];
					startActivityForResult(intent,dirlist.length-1);
				}});
			temp.start();
		}else{
			finish();
		}
	}

	public void displayNotification(int id, String ticker, String msg){
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notification1 = new Notification(R.drawable.icon, ticker, System.currentTimeMillis());
		notification1.flags = Notification.FLAG_AUTO_CANCEL;
		PendingIntent contentIntent = PendingIntent.getActivity(this, id, new Intent(this, DropboxSample.class), 0);
		notification1.setLatestEventInfo(this, ticker, msg, contentIntent);
		manager.notify(id, notification1);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		manager.cancelAll();
		super.onActivityResult(requestCode, resultCode, data);
		File tmp = new File("/sdcard/"+appname);
		tmp.delete();
		api.delete("dropbox", folder.getText().toString() + "/" + appname);
		if (requestCode>0){
			DropboxAPI.Entry dbe = api.metadata("dropbox", folder.getText().toString(), 10000, null, true);
			List<Entry> contents = dbe.contents;
			listPop(contents);
		}else{
			//stopService(mine);
			startService(mine);
			finish();
		}
	}
	public void downloadDropboxFile(String dbPath, File localFile) throws IOException {
		BufferedInputStream br = null;
		BufferedOutputStream bw = null;
		try {
			if (!localFile.exists()) {
				localFile.createNewFile();
			}
			FileDownload fd = api.getFileStream("dropbox", dbPath, null);
			br = new BufferedInputStream(fd.is);
			bw = new BufferedOutputStream(new FileOutputStream(localFile));

			byte[] buffer = new byte[4096];
			int read;int cc=0;
			while (true) {
				read = br.read(buffer);
				cc++;
				if (read <= 0) {
					break;
				}
				bw.write(buffer, 0, read);
			}
		}
		finally {
			if (bw != null) {
				bw.close();
			}
			if (br != null) {
				br.close();
			}
		}
	}

	public DropboxAPI getAPI() {
		return api;
	}

	public void setLoggedIn(boolean loggedIn) {
		mLoggedIn = loggedIn;
		mLoginEmail.setEnabled(!loggedIn);
		mLoginPassword.setEnabled(!loggedIn);
		if (!loggedIn) {
			mSubmit.setText("Log In to Dropbox");
		}
	}

	public void showToast(String msg) {
		Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
		error.show();
	}

	public void getAccountInfo() {
		if (api.isAuthenticated()) {
			LoginAsyncTask login = new LoginAsyncTask(this, null, null, getConfig());
			login.execute();    		
		} else {
			String email = mLoginEmail.getText().toString();
			if (email.length() < 5 || email.indexOf("@") < 0 || email.indexOf(".") < 0) {
				showToast("Error, invalid e-mail");
				return;
			}
			String password = mLoginPassword.getText().toString();
			if (password.length() < 6) {
				showToast("Error, password too short");
				return;
			}
			LoginAsyncTask login = new LoginAsyncTask(this, email, password, getConfig());
			login.execute();
		}
	}

	public boolean authenticate() {
		if (mConfig == null) {
			mConfig = getConfig();
		}
		String keys[] = getKeys();
		if (keys != null) {
			mConfig = api.authenticateToken(keys[0], keys[1], mConfig);
			if (mConfig != null) {
				return true;
			}
		}
		clearKeys();
		setLoggedIn(false);
		return false;
	}

	public Config getConfig() {
		if (mConfig == null) {
			mConfig = api.getConfig(null, false);
			mConfig.consumerKey=CONSUMER_KEY;
			mConfig.consumerSecret=CONSUMER_SECRET;
			mConfig.server="api.dropbox.com";
			mConfig.contentServer="api-content.dropbox.com";
			mConfig.port=80;
		}
		return mConfig;
	}

	public void setConfig(Config conf) {
		mConfig = conf;
	}

	public String[] getKeys() {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		String key = prefs.getString(ACCESS_KEY_NAME, null);
		String secret = prefs.getString(ACCESS_SECRET_NAME, null);
		if (key != null && secret != null) {
			String[] ret = new String[2];
			ret[0] = key;
			ret[1] = secret;
			return ret;
		} else {
			return null;
		}
	}

	public void storeKeys(String key, String secret) {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		Editor edit = prefs.edit();
		edit.putString(ACCESS_KEY_NAME, key);
		edit.putString(ACCESS_SECRET_NAME, secret);
		edit.commit();
	}

	public void clearKeys() {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		Editor edit = prefs.edit();
		edit.clear();
		edit.commit();
	}    	
}