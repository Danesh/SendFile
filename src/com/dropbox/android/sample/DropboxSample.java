/*
 * Copyright (c) 2010 Evenflow, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.dropbox.android.sample;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.client.DropboxAPI;
import com.dropbox.client.DropboxAPI.Config;


public class DropboxSample extends Activity {
    private static final String TAG = "DropboxSample";

    // Replace this with your consumer key and secret assigned by Dropbox.
    // Note that this is a really insecure way to do this, and you shouldn't
    // ship code which contains your key & secret in such an obvious way.
    // Obfuscation is good.
    final static private String CONSUMER_KEY = "PUT_YOUR_CONSUMER_KEY_HERE";
    final static private String CONSUMER_SECRET = "PUT_YOUR_CONSUMER_SECRET_HERE";

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
        	// We can query the account info already, since we have stored 
        	// credentials
        	getAccountInfo();
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
    	showToast("Failed user authentication for stored login tokens.");
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