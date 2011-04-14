package com.dropbox.android.sample;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.danesh.sendfile.R;
import com.dropbox.client.DropboxAPI;
import com.dropbox.client.DropboxAPI.Config;
import com.dropbox.client.DropboxAPI.Entry;
import com.dropbox.client.DropboxAPI.FileDownload;


public class DropboxSample extends Activity {
	private static final String TAG = "DropboxSample";
    final static private String CONSUMER_KEY = "";
	final static private String CONSUMER_SECRET = "";
	private DropboxAPI api = new DropboxAPI();
	final static public String ACCOUNT_PREFS_NAME = "prefs";
	final static public String ACCESS_KEY_NAME = "ACCESS_KEY";
	final static public String ACCESS_SECRET_NAME = "ACCESS_SECRET";
	private boolean mLoggedIn;
	private EditText mLoginEmail;
	private EditText mLoginPassword;
	private Button mSubmit;
	private TextView mText;
	private Config mConfig;
	private String appname;
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		mLoginEmail = (EditText)findViewById(R.id.login_email);
		mLoginPassword = (EditText)findViewById(R.id.login_password);
		mSubmit = (Button)findViewById(R.id.login_submit);
		mText = (TextView)findViewById(R.id.text);
		mSubmit.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (mLoggedIn) {
					// We're going to log out
					api.deauthenticate();
					clearKeys();
					setLoggedIn(false);
					mText.setText("");
				} else {
					// Try to log in
					getAccountInfo();
				}
			}
		});
		String[] keys = getKeys();
		if (keys != null) {
			setLoggedIn(true);
			Log.i(TAG, "Logged in already");
		} else {
			setLoggedIn(false);
			Log.i(TAG, "Not logged in");
		}
		if (authenticate()) {
			DropboxAPI.Entry dbe = api.metadata("dropbox", "/Android", 10000, null, true);
			List<Entry> contents = dbe.contents;
			int result = Settings.Secure.getInt(getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0);
			if (result == 0) {
				showToast("Please enable unknown sources.");
				Intent intent = new Intent();
				intent.setAction(Settings.ACTION_APPLICATION_SETTINGS);
				startActivity(intent);
			}else{
				listPop(contents);
			}
			//api.delete("dropbox", "/Android/abc.apk");
		}
	}

	public void listPop(List<Entry> pack){
		final String[] dirlist = new String[pack.size()];
		for (int a=0;a<pack.size();a++){
			dirlist[a] = pack.get(a).fileName();
		};
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Pick an application");
		builder.setItems(dirlist, new DialogInterface.OnClickListener(){
			public void onClick(DialogInterface dialogInterface, int item) {
				if (dirlist[item].endsWith("apk")){
					File apk = new File("/sdcard/" + dirlist[item]);
					if (!apk.exists()){
						try {apk.createNewFile();} catch (IOException e1) {}
					}
					try {downloadDropboxFile("/Android/" + dirlist[item],apk);} catch (IOException e) {e.printStackTrace();}
					String fileName = Environment.getExternalStorageDirectory() + "/" + dirlist[item];
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setDataAndType(Uri.fromFile(new File(fileName)), "application/vnd.android.package-archive");
					appname = dirlist[item];
					startActivityForResult(intent,item);
				}
				return;
			}});
		builder.create().show();
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    super.onActivityResult(requestCode, resultCode, data);
	    showToast(appname);
	}
	private void downloadDropboxFile(String dbPath, File localFile) throws IOException {
		BufferedInputStream br = null;
		BufferedOutputStream bw = null;
		BufferedInputStream ttt = null;
		try {
			if (!localFile.exists()) {
				localFile.createNewFile(); //otherwise dropbox client will fail silently
			}

			FileDownload fd = api.getFileStream("dropbox", dbPath, null);
			br = new BufferedInputStream(fd.is);
			bw = new BufferedOutputStream(new FileOutputStream(localFile));

			byte[] buffer = new byte[4096];
			int read;
			while (true) {
				read = br.read(buffer);
				if (read <= 0) {
					break;
				}
				bw.write(buffer, 0, read);
			}
		}
		finally {
			//in finally block:
			if (bw != null) {
				bw.close();
			}
			if (br != null) {
				br.close();
			}
		}
	}
	/**
	 * This lets us use the Dropbox API from the LoginAsyncTask
	 */
	public DropboxAPI getAPI() {
		return api;
	}

	/**
	 * Convenience function to change UI state based on being logged in
	 */
	public void setLoggedIn(boolean loggedIn) {
		mLoggedIn = loggedIn;
		mLoginEmail.setEnabled(!loggedIn);
		mLoginPassword.setEnabled(!loggedIn);
		if (loggedIn) {
			mSubmit.setText("Log Out of Dropbox");
		} else {
			mSubmit.setText("Log In to Dropbox");
		}
	}

	public void showToast(String msg) {
		Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
		error.show();
	}

	private void getAccountInfo() {
		if (api.isAuthenticated()) {
			// If we're already authenticated, we don't need to get the login info
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

			// It's good to do Dropbox API (and any web API) calls in a separate thread,
			// so we don't get a force-close due to the UI thread stalling.
			LoginAsyncTask login = new LoginAsyncTask(this, email, password, getConfig());
			login.execute();
		}
	}

	/**
	 * Displays some useful info about the account, to demonstrate
	 * that we've successfully logged in
	 * @param account
	 */
	public void displayAccountInfo(DropboxAPI.Account account) {
		if (account != null) {
			String info = "Name: " + account.displayName + "\n" +
			"E-mail: " + account.email + "\n" + 
			"User ID: " + account.uid + "\n" +
			"Quota: " + account.quotaQuota;
			mText.setText(info);
		}
	}

	/**
	 * This handles authentication if the user's token & secret
	 * are stored locally, so we don't have to store user-name & password
	 * and re-send every time.
	 */
	protected boolean authenticate() {
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
		showToast("Please login.");
		clearKeys();
		setLoggedIn(false);
		return false;
	}

	protected Config getConfig() {
		if (mConfig == null) {
			mConfig = api.getConfig(null, false);
			// TODO On a production app which you distribute, your consumer
			// key and secret should be obfuscated somehow.
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

	/**
	 * Shows keeping the access keys returned from Trusted Authenticator in a local
	 * store, rather than storing user name & password, and re-authenticating each
	 * time (which is not to be done, ever).
	 * 
	 * @return Array of [access_key, access_secret], or null if none stored
	 */
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

	/**
	 * Shows keeping the access keys returned from Trusted Authenticator in a local
	 * store, rather than storing user name & password, and re-authenticating each
	 * time (which is not to be done, ever).
	 */
	public void storeKeys(String key, String secret) {
		// Save the access key for later
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