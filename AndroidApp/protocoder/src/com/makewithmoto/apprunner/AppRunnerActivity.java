/*
 * Protocoder 
 * A prototyping platform for Android devices 
 * 
 * 
 * Copyright (C) 2013 Motorola Mobility LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions: 
 * 
 * The above copyright notice and this permission notice shall be included in all 
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN 
 * THE SOFTWARE.
 * 
 */

package com.makewithmoto.apprunner;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.commonjs.module.Require;

import android.app.ActionBar;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.makewithmoto.MainActivity;
import com.makewithmoto.R;
import com.makewithmoto.apprunner.api.JAndroid;
import com.makewithmoto.apprunner.api.JMedia;
import com.makewithmoto.apprunner.api.JSensors;
import com.makewithmoto.base.BaseActivity;
import com.makewithmoto.events.Events;
import com.makewithmoto.events.Events.ProjectEvent;
import com.makewithmoto.events.Project;
import com.makewithmoto.events.ProjectManager;
import com.makewithmoto.network.CustomWebsocketServer;
import com.makewithmoto.sensors.WhatIsRunning;
import com.makewithmoto.utils.StrUtils;

import de.greenrobot.event.EventBus;

/**
 * Original sourcecode from Droid Script :
 * https://github.com/divineprog/droidscript Copyright (c) Mikael Kindborg 2010
 * Source code license: MIT
 */
public class AppRunnerActivity extends BaseActivity {

	static ScriptContextFactory contextFactory;
	public Interpreter interpreter;

	String scriptFileName;
	private Project currentProject;
	private ActionBar actionBar;
	private CustomWebsocketServer ws;
	private JAndroid.onKeyListener onKeyListener;
	private JAndroid.onSmsReceivedListener onSmsReceivedListener;
	private JSensors.onNFCListener onNFCListener;
	private JMedia.onVoiceRecognitionListener onVoiceRecognitionListener;
	private BroadcastReceiver mIntentReceiver;

	private static final String TAG = "AppRunner";

	public static final int VOICE_RECOGNITION_REQUEST_CODE = 55;

	public String addInterface(Class c) { 
		String pkg = "Packages." + c.getName().toString();
		String clsName = c.getSimpleName();
		
		String c1 = "var " + clsName + " = " + pkg + "; \n";
		String c2 = "var " + clsName.substring(1).toLowerCase() + "(Activity);";
		
		String prefix = c1 + c2; 
		
		return prefix;
	}
	
	
	static final String SCRIPT_PREFIX = "//Prepend text for all scripts \n"
			+ "var window = this; \n"
			+ "var JAndroid = Packages.com.makewithmoto.apprunner.api.JAndroid; \n"
			+ "var android = JAndroid(Activity);\n"
			+ "var JUI = Packages.com.makewithmoto.apprunner.api.JUI; \n"
			+ "var ui = JUI(Activity);\n"
			+ "var JFileIO = Packages.com.makewithmoto.apprunner.api.JFileIO; \n"
			+ "var fileIO = JFileIO(Activity);\n"
			+ "var JDashboard = Packages.com.makewithmoto.apprunner.api.JDashboard; \n"
			+ "var dashboard = JDashboard(Activity);\n"
			+ "var JMakr = Packages.com.makewithmoto.apprunner.api.JMakr; \n"
			+ "var makr = JMakr(Activity);\n"
			+ "var JIOIO = Packages.com.makewithmoto.apprunner.api.JIOIO; \n"
			+ "var ioio = JIOIO(Activity);\n"
			+ "var JWebAppPlot = Packages.com.makewithmoto.apprunner.api.JWebAppPlot; \n"
			+ "var JMedia = Packages.com.makewithmoto.apprunner.api.JMedia; \n"
			+ "var media = JMedia(Activity);\n"
			+ "var JNetwork = Packages.com.makewithmoto.apprunner.api.JNetwork; \n"
			+ "var network = JNetwork(Activity);\n"
			+ "var JSensors = Packages.com.makewithmoto.apprunner.api.JSensors; \n"
			+ "var sensors = JSensors(Activity);\n"
			+ "var JConsole = Packages.com.makewithmoto.apprunner.api.JConsole; \n"
			+ "var console = JConsole(Activity);\n"
			+ "// End of Prepend Section \n";

	static final String SCRIPT_POSTFIX = "//Appends text for all scripts \n"
			+ "function onAndroidPause(){ }  \n" + "// End of Append Section"
			+ "\n";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_apprunner);

		AudioManager audio = (AudioManager) getSystemService(this.AUDIO_SERVICE);
		int currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

		initializeNFC();

		try {
			Log.d(TAG, "starting websocket server");
			ws = CustomWebsocketServer.getInstance(this);
		} catch (UnknownHostException e) {
			Log.d(TAG, "cannot start websocket server");
			e.printStackTrace();
		}

		String projectName = "";

		createInterpreter();
		// Read in the script given in the intent.
		Intent intent = getIntent();
		if (null != intent) {

			projectName = intent.getStringExtra("projectName");
			int projectType = intent.getIntExtra("projectType",
					ProjectManager.type);

			Log.d(TAG, "launching " + projectName + " " + projectType);

			currentProject = ProjectManager.getInstance().get(projectName,
					projectType);


			AppRunnerSettings.get().project = currentProject;

			String script = SCRIPT_PREFIX
					+ ProjectManager.getInstance().getCode(currentProject)
					+ SCRIPT_POSTFIX;

			Log.d("AppRunnerActivity", script);
			if (null != script) {
				eval(script, projectName);
			}

			// Set up the actionbar
			actionBar = getActionBar();
			if (actionBar != null) {
				actionBar.setDisplayHomeAsUpEnabled(true);
				actionBar.setTitle(projectName);
			}

		}

		// Call the onCreate JavaScript function.
		callJsFunction("onCreate", savedInstanceState);

		// send ready to the ide
		ready(true);

	}

	public void ready(boolean r) {

		JSONObject msg = new JSONObject();
		try {
			msg.put("type", "ide");
			msg.put("action", "conf");

			JSONObject values = new JSONObject();
			;
			values.put("ready", r);
			msg.put("values", values);

		} catch (JSONException e1) {
			e1.printStackTrace();
		}

		// Log.d(TAG, "update");

		try {
			CustomWebsocketServer ws = CustomWebsocketServer.getInstance(this);
			ws.send(msg);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	public void onEventMainThread(ProjectEvent evt) {
		Log.d(TAG, "event -> " + evt.getAction());

		if (evt.getAction() == "run") {
			finish();
		}
	}

	public void onEventMainThread(Events.ExecuteCodeEvent evt) {
		Log.d(TAG, "event -> " + evt.getCode());

		eval(evt.getCode());

	}

	public void changeTitle(String title) {
		Log.d(TAG, "change title to " + title);
		getActionBar().setTitle(title);
	}

	@Override
	public void onStart() {
		super.onStart();
		callJsFunction("onStart");
	}

	@Override
	public void onRestart() {
		super.onRestart();
		callJsFunction("onRestart");
	}

	@Override
	public void onResume() {
		super.onResume();
		EventBus.getDefault().register(this);

		if (nfcSupported) {
			mAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
		}
		// sms receive

		IntentFilter intentFilter = new IntentFilter("SmsMessage.intent.MAIN");
		mIntentReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(android.content.Context context, Intent intent) {
				String msg = intent.getStringExtra("get_msg");

				// Process the sms format and extract body &amp; phoneNumber
				msg = msg.replace("\n", "");
				String body = msg.substring(msg.lastIndexOf(":") + 1,
						msg.length());
				String pNumber = msg.substring(0, msg.lastIndexOf(":"));

				if (onSmsReceivedListener != null) {
					onSmsReceivedListener.onSmsReceived(pNumber, body);
				}
				// Add it to the list or do whatever you wish to

			}
		};
		this.registerReceiver(mIntentReceiver, intentFilter);

		callJsFunction("onResume");
	}

	@Override
	public void onPause() {
		super.onPause();
		EventBus.getDefault().unregister(this);

		callJsFunction("onPause");
		callJsFunction("onAndroidPause");

		if (nfcSupported) {
			mAdapter.disableForegroundDispatch(this);
		}
		this.unregisterReceiver(this.mIntentReceiver);
		ready(false);
	}

	@Override
	public void onStop() {
		super.onStop();
		callJsFunction("onStop");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		callJsFunction("onDestroy");
		WhatIsRunning.getInstance().stopAll();

	}

	// @Override
	// public Object onRetainNonConfigurationInstance() {
	// // TODO: We will need to somehow also allow JS to save
	// data and rebuild the UI.
	// return interpreter;
	// }

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {

		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view,
			ContextMenu.ContextMenuInfo info) {
		callJsFunction("onCreateContextMenu", menu, view, info);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		callJsFunction("onContextItemSelected", item);
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		callJsFunction("onCreateOptionsMenu", menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		callJsFunction("onPrepareOptionsMenu", menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		callJsFunction("onOptionsItemSelected", item);
		switch (item.getItemId()) {
		case android.R.id.home:
			// Up button pressed
			Intent intentHome = new Intent(this, MainActivity.class);
			intentHome.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intentHome);
			overridePendingTransition(R.anim.splash_slide_in_anim_reverse_set,
					R.anim.splash_slide_out_anim_reverse_set);
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public Object eval(final String code) {
		return eval(code, "");
	}

	public Object eval(final String code, final String sourceName) {
		final AtomicReference<Object> result = new AtomicReference<Object>(null);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					result.set(interpreter.eval(code, sourceName));
				} catch (Throwable e) {
					reportError(e);
					result.set(e);
				}
			}
		});
		while (null == result.get()) {
			Thread.yield();
		}

		return result.get();
	}

	/**
	 * This works because method is called from the "onXXX" methods which are
	 * called in the UI-thread. Thus, no need to use run on UI-thread. TODO:
	 * Could be a problem if someone calls it from another class, make private
	 * for now.
	 */
	private Object callJsFunction(String funName, Object... args) {
		try {
			return interpreter.callJsFunction(funName, args);
		} catch (Throwable e) {
			reportError(e);
			return false;
		}
	}

	protected void createInterpreter() {
		// Initialize global context factory with our custom factory.
		if (null == contextFactory) {
			contextFactory = new ScriptContextFactory();
			ContextFactory.initGlobal(contextFactory);
			Log.i("AppRunnerActivity", "Creating ContextFactory");
		}

		contextFactory.setActivity(this);

		if (null == interpreter) {
			// Get the interpreter, if previously created.
			Object obj = getLastNonConfigurationInstance();
			if (null == obj) {
				// Create interpreter.
				interpreter = new Interpreter();
			} else {
				// Restore interpreter state.
				interpreter = (Interpreter) obj;
			}
		}

		interpreter.setActivity(this);
	}

	public void reportError(Object e) {
		// Create error message.
		String message = "";
		if (e instanceof RhinoException) {
			RhinoException error = (RhinoException) e;
			message = error.getMessage()
					+ " "
					+ error.lineNumber()
					+ " ("
					+ error.columnNumber()
					+ "): "
					+ (error.sourceName() != null ? " " + error.sourceName()
							: "")
					+ (error.lineSource() != null ? " " + error.lineSource()
							: "") + "\n" + error.getScriptStackTrace();

			showError(message);

			JSONObject obj = new JSONObject();
			try {
				obj.put("type", "error");
				obj.put("values", message);
				ws.send(obj);
			} catch (JSONException er1) {
				// TODO Auto-generated catch block
				er1.printStackTrace();
			}

		} else {
			message = e.toString();
		}

		// Log the error message.
		Log.i("AppRunnerActivity", "JavaScript Error: " + message);
	}

	public static String preprocess(String code) throws Exception {
		return preprocessMultiLineStrings(extractCodeFromAppRunnerTags(code));
	}

	public static String extractCodeFromAppRunnerTags(String code)
			throws Exception {
		String startDelimiter = "DROIDSCRIPT_BEGIN";
		String stopDelimiter = "DROIDSCRIPT_END";

		// Find start delimiter
		int start = code.indexOf(startDelimiter, 0);
		if (-1 == start) {
			// No delimiter found, return code untouched
			return code;
		}

		// Find stop delimiter
		int stop = code.indexOf(stopDelimiter, start);
		if (-1 == stop) {
			// No delimiter found, return code untouched
			return code;
		}

		// Extract the code between start and stop.
		String result = code.substring(start + startDelimiter.length(), stop);

		// Replace escaped characters with plain characters.
		// TODO: Add more characters here
		return result.replace("&lt;", "<").replace("&gt;", ">")
				.replace("&quot;", "\"");
	}

	public static String preprocessMultiLineStrings(String code)
			throws Exception {
		StringBuilder result = new StringBuilder(code.length() + 1000);

		String delimiter = "\"\"\"";
		int lastStop = 0;
		while (true) {
			// Find next multiline delimiter
			int start = code.indexOf(delimiter, lastStop);
			if (-1 == start) {
				// No delimiter found, append rest of the code
				// to result and break
				result.append(code.substring(lastStop, code.length()));
				break;
			}

			// Find terminating delimiter
			int stop = code.indexOf(delimiter, start + delimiter.length());
			if (-1 == stop) {
				// This is an error, throw an exception with error message
				throw new Exception("Multiline string not terminated");
			}

			// Append the code from last stop up to the start delimiter
			result.append(code.substring(lastStop, start));

			// Set new lastStop
			lastStop = stop + delimiter.length();

			// Append multiline string converted to JavaScript code
			result.append(convertMultiLineStringToJavaScript(code.substring(
					start + delimiter.length(), stop)));
		}

		return result.toString();
	}

	public static String convertMultiLineStringToJavaScript(String s) {
		StringBuilder result = new StringBuilder(s.length() + 1000);

		char quote = '\"';
		char newline = '\n';
		String backslashquote = "\\\"";
		String concat = "\\n\" + \n\"";

		result.append(quote);

		for (int i = 0; i < s.length(); ++i) {
			char c = s.charAt(i);
			if (c == quote) {
				result.append(backslashquote);
			} else if (c == newline) {
				result.append(concat);
			} else {
				result.append(c);
			}
			// Log.i("Multiline", result.toString());
		}

		result.append(quote);

		return result.toString();
	}

	public static class Interpreter {
		Context context;
		Scriptable scope;
		Require require;

		public Interpreter() {
			// Creates and enters a Context. The Context stores information
			// about the execution environment of a script.
			context = Context.enter();
			context.setOptimizationLevel(-1);

			// Initialize the standard objects (Object, Function, etc.)
			// This must be done before scripts can be executed. Returns
			// a scope object that we use in later calls.
			scope = context.initStandardObjects();
		}

		public Interpreter setActivity(Activity activity) {
			// ScriptAssetProvider provider = new ScriptAssetProvider(activity);
			// Require require = new Require(context, scope, provider, null,
			// null, true);
			// require.install(scope);

			// Set the global JavaScript variable Activity.
			ScriptableObject.putProperty(scope, "Activity",
					Context.javaToJS(activity, scope));
			return this;
		}

		public Interpreter setErrorReporter(ErrorReporter reporter) {
			context.setErrorReporter(reporter);
			return this;
		}

		public void exit() {
			Context.exit();
		}

		public Object eval(String code, String sourceName) throws Throwable {
			String processedCode = preprocess(code);
			return context.evaluateString(scope, processedCode, sourceName, 1,
					null);
		}

		public Object callJsFunction(String funName, Object... args)
				throws Throwable {
			Object fun = scope.get(funName, scope);
			if (fun instanceof Function) {
				Log.i("AppRunnerActivity", "Calling JsFun " + funName);
				Function f = (Function) fun;
				Object result = f.call(context, scope, scope, args);
				return Context.toString(result); // Why did I use this?
			} else {
				// Log.i("AppRunnerActivity", "Could not find JsFun " +
				// funName);
				return null;
			}
		}
	}

	public static class ScriptContextFactory extends ContextFactory {
		AppRunnerActivity activity;

		public ScriptContextFactory setActivity(AppRunnerActivity activity) {
			this.activity = activity;
			return this;
		}

		@Override
		protected Object doTopCall(Callable callable, Context cx,
				Scriptable scope, Scriptable thisObj, Object[] args) {
			try {
				return super.doTopCall(callable, cx, scope, thisObj, args);
			} catch (Throwable e) {
				Log.i("AppRunnerActivity", "ContextFactory catched error: " + e);
				if (null != activity) {
					activity.reportError(e);
				}
				return e;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.makewithmoto.base.BaseActivity#onKeyDown(int,
	 * android.view.KeyEvent)
	 * 
	 * key handling, it will pass it to the javascript interface
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (onKeyListener != null) {
			onKeyListener.onKeyDown(keyCode);
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (onKeyListener != null) {
			onKeyListener.onKeyUp(keyCode);
		}

		return super.onKeyUp(keyCode, event);
	}

	public void addOnKeyListener(JAndroid.onKeyListener onKeyListener2) {
		onKeyListener = onKeyListener2;

	}

	public void addOnSmsReceivedListener(
			JAndroid.onSmsReceivedListener onSmsReceivedListener2) {
		onSmsReceivedListener = onSmsReceivedListener2;

	}

	public void showError(String message) {
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.console);
		rl.setVisibility(View.VISIBLE);
		TextView t = (TextView) findViewById(R.id.console_content);
		t.setText(message);
	}

	/*
	 * NFC
	 */

	private NfcAdapter mAdapter;

	private PendingIntent mPendingIntent;
	private IntentFilter[] mFilters;
	private String[][] mTechLists;
	private boolean nfcSupported;

	public void initializeNFC() {

		PackageManager pm = getPackageManager();
		nfcSupported = pm.hasSystemFeature(PackageManager.FEATURE_NFC);

		if (nfcSupported == false)
			return;

		// cuando esta en foreground
		Log.d(TAG, "Starting NFC");
		mAdapter = NfcAdapter.getDefaultAdapter(this);

		// Create a generic PendingIntent that will be deliver to this activity.
		// The NFC stack will fill in the intent with the details of the
		// discovered tag before
		// delivering to this activity.
		mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		// Setup an intent filter for all MIME based dispatches
		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			ndef.addDataType("*/*");
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("fail", e);
		}
		mFilters = new IntentFilter[] { ndef, };

		// Setup a tech list for all NfcF tags
		mTechLists = new String[][] { new String[] { NfcF.class.getName() } };

	}

	@Override
	public void onNewIntent(Intent intent) {
		Log.d(TAG, "New intent " + intent);

		if (intent.getAction() != null) {
			Log.d(TAG, "Discovered tag with intent: " + intent);
			// mText.setText("Discovered tag " + ++mCount + " with intent: " +
			// intent);

			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			// TechFilter t = new TechFilter();
			byte[] tagId = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
			// NdefMessage[] msgs = (NdefMessage[]) intent
			// .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

			String nfcID = StrUtils.bytetostring(tag.getId());
			// Toast.makeText(this, "Tag detected: " + nfcID,
			// Toast.LENGTH_LONG).show();
			onNFCListener.onNewTag(nfcID);

		}

	}

	public void addNFCListener(JSensors.onNFCListener onNFCListener2) {
		onNFCListener = onNFCListener2;

	}

	/**
	 * Handle the results from the recognition activity.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == VOICE_RECOGNITION_REQUEST_CODE
				&& resultCode == RESULT_OK) {
			// Fill the list view with the strings the recognizer thought it
			// could have heard
			ArrayList<String> matches = data
					.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

			for (String _string : matches) {
				Log.d(TAG, "" + _string);

			}
			onVoiceRecognitionListener.onNewResult(matches.get(0));

		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	public void addVoiceRecognitionListener(
			JMedia.onVoiceRecognitionListener onVoiceRecognitionListener2) {

		onVoiceRecognitionListener = onVoiceRecognitionListener2;
	}
}
