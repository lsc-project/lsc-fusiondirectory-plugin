package org.lsc.plugins.connectors.fusiondirectory.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Tab {
	
	private String name;
	
	private boolean active;
	
	@JsonProperty("class")
	private String class_;
	
	public Tab() {
		super();
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public boolean getActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	public String getClass_() {
		return class_;
	}
	public void setClass_(String class_) {
		this.class_ = class_;
	}
	
}
