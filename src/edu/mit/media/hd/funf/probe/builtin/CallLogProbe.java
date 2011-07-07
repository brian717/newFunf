package edu.mit.media.hd.funf.probe.builtin;

import java.util.HashMap;
import java.util.Map;

import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import edu.mit.media.hd.funf.HashUtil;
import edu.mit.media.hd.funf.probe.CursorCell;
import edu.mit.media.hd.funf.probe.DatedContentProviderProbe;
import edu.mit.media.hd.funf.probe.CursorCell.HashedCell;
import edu.mit.media.hd.funf.probe.CursorCell.StringCell;

public class CallLogProbe extends DatedContentProviderProbe {

	public static final String CALLS = "CALLS";

	@Override
	public String[] getRequiredPermissions() {
		return new String[] {
			android.Manifest.permission.READ_CONTACTS
		};
	}

	
	static class PhoneNumberCell extends StringCell {
		@Override
		public String getData(Cursor cursor, int columnIndex) {
			return HashUtil.formatPhoneNumber(super.getData(cursor, columnIndex));
		}
	}
	
	protected Map<String,CursorCell<?>> getProjectionMap() {
		Map<String,CursorCell<?>> projectionKeyToType = new HashMap<String, CursorCell<?>>();
		projectionKeyToType.put(Calls._ID, intCell());
		projectionKeyToType.put(Calls.NUMBER, new HashedCell(this, new PhoneNumberCell()));
		projectionKeyToType.put(Calls.DATE, longCell());
		projectionKeyToType.put(Calls.TYPE, intCell());
		projectionKeyToType.put(Calls.DURATION, longCell());
		projectionKeyToType.put(Calls.CACHED_NAME, hashedStringCell());
		projectionKeyToType.put(Calls.CACHED_NUMBER_LABEL, hashedStringCell());
		projectionKeyToType.put(Calls.CACHED_NUMBER_TYPE, hashedStringCell());
		return projectionKeyToType;
	}
	
	
	protected String getDataName() {
		return CALLS;
	}


	@Override
	protected Uri getContentProviderUri() {
		return CallLog.Calls.CONTENT_URI;
	}


	@Override
	protected String getDateColumnName() {
		return Calls.DATE;
	}
	

}