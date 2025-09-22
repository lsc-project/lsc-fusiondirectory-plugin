package org.lsc.plugins.connectors.fusiondirectory.beans;

import java.util.Calendar;
import java.util.TimeZone;

public class Token {
	private Calendar created;
	private String sessionId;
	public Token(String sessionId) {
		created = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		this.sessionId = sessionId;
	}
	public String getSessionId() {
		return sessionId;
	}
	public boolean hasExpired(int validitySeconds) {
		if (validitySeconds > 0) {
			Calendar expireAt = (Calendar) created.clone();
			expireAt.setTime(created.getTime());
			expireAt.add(Calendar.SECOND, validitySeconds);
			return expireAt.compareTo(Calendar.getInstance(TimeZone.getTimeZone("UTC"))) <= 0;
		}
		return false;
	}
}
