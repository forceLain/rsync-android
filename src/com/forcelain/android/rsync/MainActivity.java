package com.forcelain.android.rsync;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.List;

import org.metastatic.rsync.Checksum32;
import org.metastatic.rsync.Configuration;
import org.metastatic.rsync.Generator;
import org.metastatic.rsync.JarsyncProvider;
import org.metastatic.rsync.Matcher;
import org.metastatic.rsync.Rebuilder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

public class MainActivity extends Activity implements OnClickListener {

	private static final String RSYNC_FILE_NAME = "rsync";
	private static final String SERVER_DIR_NAME = "SERVER";
	private static final String CLIENT1_DIR_NAME = "CLIENT_WITH_NATIVE_RSYNC";
	private static final String CLIENT2_DIR_NAME = "CLIENT_WITH_JARSYNC";
	private static final String TEST_FILE_NAME = "test.txt";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		findViewById(R.id.execute).setOnClickListener(this);
		findViewById(R.id.execute_java).setOnClickListener(this);
		makeEnvironment();
	}

	private void makeEnvironment() {
		deleteRecursive(getEnvironmentDir());
		File serverDir = getEnvironmentDir(SERVER_DIR_NAME);
		serverDir.mkdirs();
		File client1Dir = getEnvironmentDir(CLIENT1_DIR_NAME);
		client1Dir.mkdirs();
		File client2Dir = getEnvironmentDir(CLIENT2_DIR_NAME);
		client2Dir.mkdirs();		
		copyFromAssets("file_on_server.txt", serverDir.getAbsolutePath()+File.separator+TEST_FILE_NAME);
		copyFromAssets("file_on_client.txt", client2Dir.getAbsolutePath()+File.separator+TEST_FILE_NAME);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.execute:
			try {
				executeNative();
			} catch (Exception e) {
				showAlert(Log.getStackTraceString(e));
			}
			break;
		case R.id.execute_java:
			try {
				executeJarSync();
			} catch (Exception e) {
				showAlert(Log.getStackTraceString(e));
			}
			break;
		}
	}
	
	private void executeNative() throws IOException, InterruptedException {	
		String rsyncLocation = getDir("FILES", MODE_PRIVATE).getAbsolutePath()+File.separator+RSYNC_FILE_NAME;
		
		copyFromAssets(RSYNC_FILE_NAME, rsyncLocation);
		chMod(rsyncLocation, "700");
		
		String serverDir = getEnvironmentDir(SERVER_DIR_NAME).getAbsolutePath();
		String clientDir = getEnvironmentDir(CLIENT1_DIR_NAME).getAbsolutePath();
		String[] cmd = new String[] {rsyncLocation, "-r", "--delete-during", serverDir, clientDir };
		ProcessBuilder pb = new ProcessBuilder(cmd);
		Process p = pb.start();
		int val = p.waitFor();
		if (val == 0){
			showAlert("Success");
		} else {
			showAlert("Fail. Error code returned: "+val);
		}
	}
	
	private void chMod(String file, String mask) throws IOException {
		Runtime runtime = Runtime.getRuntime();
		runtime.exec(new String[]{"chmod", mask, file});
	}

	private void executeJarSync() throws IOException, NoSuchAlgorithmException, NoSuchProviderException {
		File clientFile = new File(getEnvironmentDir(CLIENT2_DIR_NAME).getAbsolutePath()+File.separator+TEST_FILE_NAME);
		FileInputStream serverStream = new FileInputStream(getEnvironmentDir(SERVER_DIR_NAME).getAbsolutePath()+File.separator+TEST_FILE_NAME);
		Configuration conf = buildConfig();
		Generator gen = new Generator(conf);
		List sums = gen.generateSums(clientFile);
		Matcher mat = new Matcher(conf);
		List deltas = mat.hashSearch(sums, serverStream);
		Rebuilder.rebuildFileInPlace(clientFile, deltas);
		showAlert("Success");
	}

	private Configuration buildConfig() throws NoSuchAlgorithmException, NoSuchProviderException {
		Security.addProvider(new JarsyncProvider());
		Configuration conf = new Configuration();
		conf.strongSum = MessageDigest.getInstance("MD4", "JARSYNC");
		conf.strongSumLength = conf.strongSum.getDigestLength();
		conf.blockLength = 1024;
		conf.weakSum = new Checksum32();
		return conf;
	}

	private void copyFromAssets(String assetName, String destinationName) {

		if (fileExist(destinationName)) {
			return;
		}

		AssetManager assetManager = getAssets();
		InputStream in = null;
		OutputStream out = null;
		try {
			in = assetManager.open(assetName);
			out = new FileOutputStream(destinationName);

			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			out.flush();
		} catch (IOException e) {
			showAlert(Log.getStackTraceString(e));
		} finally {
			closeStream(in);
			in = null;
			closeStream(out);
			out = null;
		}
	}

	private boolean fileExist(String path) {
		File file = new File(path);
		return file.exists();
	}

	private void closeStream(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				showAlert(Log.getStackTraceString(e));
			}
		}
	}

	private void showAlert(String message) {
		AlertDialog.Builder ad = new AlertDialog.Builder(this);
		ad.setMessage(message);
		ad.setPositiveButton("OK", null);
		ad.create().show();
	}
	
	private File getEnvironmentDir(String dirName){
		File dir = new File(Environment.getExternalStorageDirectory()+File.separator+"RSYNC"+File.separator+dirName+File.separator);
		return dir;
	}
	
	private File getEnvironmentDir(){
		File dir = new File(Environment.getExternalStorageDirectory()+File.separator+"RSYNC");
		return dir;
	}
	
	void deleteRecursive(File fileOrDirectory) {
	    if (fileOrDirectory.isDirectory())
	        for (File child : fileOrDirectory.listFiles())
	        	deleteRecursive(child);
	    fileOrDirectory.delete();
	}
}
