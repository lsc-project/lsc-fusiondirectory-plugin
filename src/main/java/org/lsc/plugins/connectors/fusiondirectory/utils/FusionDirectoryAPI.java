package org.lsc.plugins.connectors.fusiondirectory.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.lsc.exception.LscServiceException;
import org.lsc.plugins.connectors.fusiondirectory.FusionDirectoryDao;
import org.lsc.plugins.connectors.fusiondirectory.FusionDirectoryDstService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class FusionDirectoryAPI {
	private static final Logger LOGGER = LoggerFactory.getLogger(FusionDirectoryAPI.class);

	private FusionDirectoryDao dao;
	public FusionDirectoryAPI() {
		LOGGER.info("starting FD API utils !");
	}
	public void connect(String endpoint, String username, String password) throws LscServiceException {
		connect(endpoint, username, password, -1);
	}

	public void connect(String endpoint, String username, String password, int sessionLifetime) throws LscServiceException {
		connect(endpoint, username, password, sessionLifetime, null);
	}

	public synchronized void connect(String endpoint, String username, String password, int sessionLifetime, String directory) throws LscServiceException {		
		if (dao == null ) {
			LOGGER.debug( " connect to " + endpoint + " as " + username + " with sslt " + sessionLifetime + " and dir " + directory);
			dao = new FusionDirectoryDao(endpoint, username, password, sessionLifetime, Optional.ofNullable(directory));			
		}
	}

	public List<String> search(String entity) throws LscServiceException {
		return search(entity, null);
	}
	public List<String> search(String entity, String baseString) throws LscServiceException {
		return search(entity, baseString, null);
	}
	public List<String> search(String entity, String baseString, String filterString) throws LscServiceException {

		Optional<String> base = Optional.ofNullable(baseString).filter(f -> !f.trim().isEmpty());
		Optional<String> filter = Optional.ofNullable(filterString).filter(f -> !f.trim().isEmpty());
		List<String> results = new ArrayList<>();
		ObjectNode root = dao.getList(entity, base, Optional.empty(), filter);
		Iterator<Map.Entry<String, JsonNode>> iter = root.fields();
		while (iter.hasNext()) {
			Map.Entry<String, JsonNode> entry = iter.next();
			results.add(entry.getKey());
		}
		return results;
	}

	public List<String> getAttribute(String entity, String dn, String attribute) throws LscServiceException {

		return dao.getAttribute(entity, dn, attribute);
	}

	public void setAttribute(String entity, String dn, String tab, String attribute, String value) throws LscServiceException {
		setAttribute(entity, dn, tab,attribute, Arrays.asList(value), false);
	}

	public void setAttribute(String entity, String dn, String tab, String attribute, List<String> values) throws LscServiceException {
		setAttribute(entity, dn, tab,attribute, values, true);
	}

	private void setAttribute(String entity, String dn, String tab, String attribute, List<String> values, boolean isMultiple) throws LscServiceException {
		dao.setAttribute(entity, dn, tab, attribute, values, isMultiple);

	}
}
