/**
 * 
 * Funf: Open Sensing Framework
 * Copyright (C) 2010-2011 Nadav Aharony, Wei Pan, Alex Pentland.
 * Acknowledgments: Alan Gardner
 * Contact: nadav@media.mit.edu
 * 
 * This file is part of Funf.
 * 
 * Funf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Funf is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with Funf. If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package edu.mit.media.funf.storage;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import edu.mit.media.funf.util.EqualsUtil;
import edu.mit.media.funf.util.HashCodeUtil;
import edu.mit.media.funf.util.LockUtil;
import edu.mit.media.funf.util.LogUtil;

public abstract class UploadService extends IntentService {

	
	public UploadService() {
		super("UploadService");
		// TODO Auto-generated constructor stub
	}
	
	public static final int
	MAX_REMOTE_ARCHIVE_RETRIES = 6,
	MAX_FILE_RETRIES = 3;
	
	public static final int
		NETWORK_ANY = 0,
		NETWORK_WIFI_ONLY = 1;
	
	public static final String 
	ARCHIVE_ID = "archive_id",
	REMOTE_ARCHIVE_ID = "remote_archive_id",
	NETWORK = "network";

	private ConnectivityManager connectivityManager;
	private Map<String, Integer> fileFailures;
	private Map<String, Integer> remoteArchiveFailures;
	private Queue<ArchiveFile> filesToUpload;

	private void runUpload() {
		while(!filesToUpload.isEmpty()) {
			ArchiveFile archiveFile = filesToUpload.poll();
			runArchive(archiveFile.archive, archiveFile.remoteArchive, archiveFile.file, archiveFile.network);
		}
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		WakeLock wakeLock = LockUtil.getWakeLock(this);
		Log.i(LogUtil.TAG, "Starting...");		
		try {
			// NOTE: moved all this initialization into onHandleIntent - we were seeing inconsistent uploads with a standard service, 
			// as well as ANR dialogs for background processes when using the old implementation.
			connectivityManager =(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			fileFailures = new HashMap<String, Integer>();
			remoteArchiveFailures = new HashMap<String, Integer>();
			filesToUpload = new ConcurrentLinkedQueue<ArchiveFile>();
			
			// For some reason, the intent passed in can be null... check for it and skip 
			int network = intent.getIntExtra(NETWORK, NETWORK_ANY);
			if (isOnline(network)) {
				String archiveName = intent.getStringExtra(ARCHIVE_ID);
				String remoteArchiveName = intent.getStringExtra(REMOTE_ARCHIVE_ID);
				if (archiveName != null && remoteArchiveName != null) {
					FileArchive archive = getArchive(archiveName);
					RemoteFileArchive remoteArchive = getRemoteArchive(remoteArchiveName);
					if (archive != null && remoteArchive != null) {
						for (File file : archive.getAll()) {
							archive(archive, remoteArchive, file, network);
						}
					}
				}
			}
	
			runUpload();	
		}
		finally {
			wakeLock.release();
		}
	}
	
	/**
	 * Returns the file archive that the upload service will use.
	 * @param databaseName
	 * @return
	 */
	public FileArchive getArchive(String name) {
		return DefaultArchive.getArchive(this, name);
	}

	/**
	 * Get the remote archive with the specified name
	 * @param name
	 * @return
	 */
	protected abstract RemoteFileArchive getRemoteArchive(final String name);
	
	
	public void archive(FileArchive archive, RemoteFileArchive remoteArchive, File file, int network) {
		ArchiveFile archiveFile = new ArchiveFile(archive, remoteArchive, file, network);
		if (!filesToUpload.contains(archiveFile)) {
			Log.i(LogUtil.TAG, "Queuing " + file.getName());
			filesToUpload.offer(archiveFile);
		}
	}
	
	protected void runArchive(FileArchive archive, RemoteFileArchive remoteArchive, File file, int network) {
		Integer numRemoteFailures = remoteArchiveFailures.get(remoteArchive.getId());
		numRemoteFailures = (numRemoteFailures == null) ? 0 : numRemoteFailures;
		if (numRemoteFailures < MAX_REMOTE_ARCHIVE_RETRIES && isOnline(network)) {
			Log.i(LogUtil.TAG, "Archiving..." + file.getName());
			if(remoteArchive.add(file)) {
				archive.remove(file);
			} else {
				Integer numFileFailures = fileFailures.get(file.getName());
				numFileFailures = (numFileFailures == null) ? 1 : numFileFailures + 1;
				numRemoteFailures += 1;
				fileFailures.put(file.getName(), numFileFailures);
				remoteArchiveFailures.put(remoteArchive.getId(), numRemoteFailures);
				// 3 Attempts
				if (numFileFailures < MAX_FILE_RETRIES) {
					filesToUpload.offer(new ArchiveFile(archive, remoteArchive, file, network));
				} else {
					Log.i(LogUtil.TAG, "Failed to upload '" + file.getAbsolutePath() + "' after 3 attempts.");
				}
			}
		} else {
			Log.i(LogUtil.TAG, "Canceling upload.  Remote archive '" + remoteArchive.getId() + "' is not currently available.");
		}
	}
	
	
	/**
	 * Convenience class for pairing the database name with the db file
	 */
	protected class ArchiveFile {
		public final FileArchive archive;
		public final RemoteFileArchive remoteArchive;
		public final File file;
		public final int network;
		public ArchiveFile(FileArchive archive, RemoteFileArchive remoteArchive, File file, int network) {
			this.archive = archive;
			this.remoteArchive = remoteArchive;
			this.file = file;
			this.network = network;
		}
		@Override
		public boolean equals(Object o) {
			return o != null && o instanceof ArchiveFile 
				&& EqualsUtil.areEqual(remoteArchive.getId(), ((ArchiveFile)o).remoteArchive.getId())
				&& EqualsUtil.areEqual(file, ((ArchiveFile)o).file);
		}
		@Override
		public int hashCode() {
			return HashCodeUtil.hash(HashCodeUtil.hash(HashCodeUtil.SEED, file), remoteArchive.getId());
		}
	}
	
	public boolean isOnline(int network) {
	    NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
	    
	    if (network == NETWORK_ANY && netInfo != null && netInfo.isConnectedOrConnecting()) {
	        return true;
	    } else if (network == NETWORK_WIFI_ONLY ) {
	    	Log.i(LogUtil.TAG,"we are in isOnline(): NETOWORK_WIFI_ONLY "+ network);
	    	Log.i(LogUtil.TAG, "Prining out debugging info of connectivityStatus: ");

		    State wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
		    Log.i(LogUtil.TAG, wifiInfo.toString());
		    if (State.CONNECTED.equals(wifiInfo) || State.CONNECTING.equals(wifiInfo)) {
		    	return true;
		    }
	    }
	    return false;
	}

	/**
	 * Binder interface to the probe
	 */
	public class LocalBinder extends Binder {
		public UploadService getService() {
            return UploadService.this;
        }
    }
	private final IBinder mBinder = new LocalBinder();
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
}
