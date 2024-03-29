/*
 ****************************************************************************
 * Ldap Synchronization Connector provides tools to synchronize
 * electronic identities from a list of data sources including
 * any database with a JDBC connector, another LDAP directory,
 * flat files...
 *
 *                  ==LICENSE NOTICE==
 * 
 * Copyright (c) 2008 - 2020 LSC Project 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of the LSC Project nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *                  ==LICENSE NOTICE==
 *
 *               (c) 2008 - 2020 LSC Project
 *         Soisik Froger <soisik.froger@worteks.com>
 ****************************************************************************
 */
package org.lsc.plugins.connectors.fusiondirectory;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.lsc.LscDatasets;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.ValuesType;
import org.lsc.exception.LscServiceException;
import org.lsc.plugins.connectors.fusiondirectory.beans.Login;
import org.lsc.plugins.connectors.fusiondirectory.beans.Tab;
import org.lsc.plugins.connectors.fusiondirectory.generated.Attribute;
import org.lsc.plugins.connectors.fusiondirectory.generated.Attributes;
import org.lsc.plugins.connectors.fusiondirectory.generated.AttributesTab;
import org.lsc.plugins.connectors.fusiondirectory.generated.ServiceSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class FusionDirectoryDao {

	public static final String UID = "uid";
	public static final String DN = "dn";
	public static final String DEFAULT = "default";
	public static final Pattern PATTERN_ATTR_OPT = Pattern.compile("^(\\w+);(.*)$");
	private static final String SESSION_TOKEN = "Session-Token";
	private static final String OBJECTS = "objects";
	private static final Logger LOGGER = LoggerFactory.getLogger(FusionDirectoryDao.class);

	private final String entity;
	private final String username;
	private final String password;
	private final Optional<String> pivot;
	private final Optional<String> directory;
	private final Optional<String> base;
	private final Optional<String> filter;
	private final Optional<String> allFilter;
	private final Optional<String> oneFilter;
	private final Optional<String> cleanFilter;
	private final Optional<String> template;
	private final Attributes attributesSettings;

	private WebTarget target;
	private ObjectMapper mapper;

	// Keep one token / thread worker
	private Map<String, String> tokenCache;

	public FusionDirectoryDao(PluginConnectionType connection, ServiceSettings settings) throws LscServiceException {
		mapper = new ObjectMapper();
		this.entity = settings.getEntity();
		this.username = connection.getUsername();
		this.password = connection.getPassword();
		this.pivot = getStringParameter(settings.getPivot());
		this.base = getStringParameter(settings.getBase());
		this.filter = getStringParameter(settings.getFilter());
		this.allFilter = getStringParameter(settings.getAllFilter());
		this.oneFilter = getStringParameter(settings.getOneFilter());
		this.cleanFilter = getStringParameter(settings.getCleanFilter());
		this.directory = getStringParameter(settings.getDirectory());
		this.template = getStringParameter(settings.getTemplate());
		this.attributesSettings = settings.getAttributes();

		Client client = ClientBuilder.newClient().register(new JacksonFeature());
		target = client.target(connection.getUrl());

		tokenCache = new HashMap<>();
	}

	private void login() throws LscServiceException {
		Response response = null;
		try {
			Login login = new Login();
			login.setUser(username);
			login.setPassword(password);
			login.setDirectory(getDirectory());
			WebTarget currentTarget = target.path("login");
			LOGGER.info(String.format("Login to FusionDirectory %s as %s for thread %s", currentTarget.getUri().toString(), username, Thread.currentThread().getId()));
			response = currentTarget.request().post(Entity.entity(login, MediaType.APPLICATION_JSON));
			if (!checkResponse(response)) {
				String errorMessage = String.format("Cannot log in Fusiondirectory, message: %s", response.readEntity(String.class));
				LOGGER.error(errorMessage);
				throw new LscServiceException(errorMessage);
			}
			String token = response.readEntity(String.class).replaceAll("\n", "").replaceAll("\"", "");
			synchronized (tokenCache) {
				tokenCache.put(String.valueOf(Thread.currentThread().getId()), token);
				LOGGER.debug(String.format("Register token %s for thread %s", token, Thread.currentThread().getId()));
			}
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	private String getToken() {
		synchronized (tokenCache) {
			return tokenCache.get(String.valueOf(Thread.currentThread().getId()));
		}
	}

	/**
	 * Check if session is still valid, and open a new session if necessary.
	 * @throws LscServiceException
	 */
	private void ping() throws LscServiceException {
		Response response = null;

		String token = getToken();
		if (token == null) {
			login();
			return;
		}

		try {
			WebTarget currentTarget = target.path("token");
			response = currentTarget.request().header(SESSION_TOKEN, token).get();
			if (Response.Status.fromStatusCode(response.getStatus()) == Response.Status.UNAUTHORIZED) {
				LOGGER.info("FusionDirectory session has expired. Reconnecting ...");
				login();
			} else if (!checkResponse(response)) {
				String errorMessage = String.format("Cannot ping Fusiondirectory, message: %s", response.readEntity(String.class));
				LOGGER.error(errorMessage);
				throw new LscServiceException(errorMessage);
			}
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	private Optional<String> getStringParameter(String parameter) {
		return Optional.ofNullable(parameter).filter(f -> !f.trim().isEmpty());
	}

	public Map<String, LscDatasets> getList() throws LscServiceException {
		return getList(allFilter.isPresent() ? allFilter : filter);
	}

	public String getDirectory() {
		return directory.map(p -> p).orElse(DEFAULT);
	}

	public String getPivotName() {
		return pivot.map(p -> p).orElse(UID);
	}

	public Map<String, LscDatasets> getList(Optional<String> computedFilter) throws LscServiceException {

		// Keeps session opened
		ping();

		Map<String, LscDatasets> resources = new LinkedHashMap<>();
		Response response = null;
		try {
			WebTarget currentTarget = target.path(OBJECTS).path(entity);
			if (base.isPresent()) {
				currentTarget = currentTarget.queryParam("base", base.get());
			}
			if (computedFilter.isPresent()) {
				currentTarget = currentTarget.queryParam("filter", computedFilter.get());
			}
			String pivotName = getPivotName();
			currentTarget = currentTarget.queryParam("attrs["+pivotName+"]", "*");

			LOGGER.debug(String.format("Search %s from: %s ", entity, currentTarget.getUri().toString()));
			response = currentTarget.request().accept(MediaType.APPLICATION_JSON).header(SESSION_TOKEN, getToken()).get(Response.class);
			if (!checkResponse(response)) {
				String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
						response.readEntity(String.class));
				LOGGER.error(errorMessage);
				throw new LscServiceException(errorMessage);
			}
			ObjectNode root = (ObjectNode)  mapper.readTree(response.readEntity(String.class));
			Iterator<Map.Entry<String, JsonNode>> iter = root.fields();
			while (iter.hasNext()) {
				Map.Entry<String, JsonNode> entry = iter.next();
				Iterator<Map.Entry<String, JsonNode>> iter2 = entry.getValue().fields();
				while (iter2.hasNext()) {
					Map.Entry<String, JsonNode> entry2 = iter2.next();
					String pivotValue =  ((ArrayNode)entry2.getValue()).get(0).textValue();
					LscDatasets datasets = new LscDatasets();
					datasets.put(DN, entry.getKey());
					datasets.put(entry2.getKey(), pivotValue);
					resources.put(pivotValue, datasets);
				}
			}
		} catch (JsonProcessingException e) {
			throw new LscServiceException(e);
		} finally {
			if (response != null) {
				response.close();
			}
		}
		return resources;
	}

	private static boolean checkResponse(Response response) {
		return Response.Status.Family.familyOf(response.getStatus()) == Response.Status.Family.SUCCESSFUL;
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getDetails(String dn) throws LscServiceException {

		// Keeps session opened
		ping();

		Response response = null;
		try {
			Map<String, Object> results = new HashMap<>();
			results.put(DN, dn);

			// Check for inactive tabs before requesting them (if an inactive tab is requested, a 400 error is sent)
			List<Tab> tabs = getEntityTabs(dn);

			for (AttributesTab attributesTab: attributesSettings.getTab()) {
				Optional<Tab> tab = tabs.stream().filter(p -> p.getClass_().equals(attributesTab.getName())).findFirst();
				if (!tab.isPresent()) {
					String errorMessage = String.format("Tab %s do not exists for object %s", attributesTab.getName(), entity);
					LOGGER.error(errorMessage);
					throw new LscServiceException(errorMessage);
				}
				if (tab.get().getActive()) {
					WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn).path(attributesTab.getName());
					response = currentTarget.request().accept(MediaType.APPLICATION_JSON).header(SESSION_TOKEN, getToken()).get(Response.class);

					if (!checkResponse(response)) {
						String errorMessage = String.format("status: %d, message: %s", response.getStatus(), response.readEntity(String.class));
						LOGGER.error(errorMessage);
						throw new LscServiceException(errorMessage);
					}
					Map<String, Object> raw = mapper.readValue(response.readEntity(String.class), Map.class);
					for (Attribute attribute : attributesTab.getAttribute()) {
						if (isOptionAttribute(attribute.getValue())) {
							String shortAttributeName = stripOptionFromAttributeName(attribute.getValue());
							Object rawValues = raw.get(shortAttributeName);
							if (rawValues == null) {
								throw new LscServiceException(String.format("Attribute %s could not be found in tab %s", shortAttributeName, attributesTab.getName()));
							}
							Object value = filterAndStripOptionFromValues(attribute.getValue(), rawValues);
							if (value != null) {
								results.put(attribute.getValue(), value);
							}
						} else {
							Object value = raw.get(attribute.getValue());
							if (value == null) {
								throw new LscServiceException(String.format("Attribute %s could not be found in tab %s", attribute.getValue(), attributesTab.getName()));
							}
							// Empty string value are considered unset
							if (value instanceof String && ((String)value).isEmpty()) {
								continue;
							}
							// LscBean does not accept Long object
							if (value instanceof Long) {
								value = ((Long)value).toString();
							}
							results.put(attribute.getValue(), value);
						}
					}
				}
			}
			return results;
		} catch (JsonProcessingException e) {
			throw new LscServiceException(e);
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	public Optional<Entry<String, LscDatasets>> findFirstByPivots(LscDatasets pivots, boolean clean) throws LscServiceException {
		Optional<String> computedFilter = clean ? cleanFilter : oneFilter;
		if (computedFilter.isPresent()) {
			for (String somePivot : pivots.getAttributesNames()) {
				computedFilter =  Optional.of(Pattern.compile("\\{" + somePivot + "\\}", Pattern.CASE_INSENSITIVE)
					.matcher(computedFilter.get()).replaceAll(Matcher.quoteReplacement(pivots.getValueForFilter(somePivot.toLowerCase()))));
			}
		} else {
			StringBuilder pivotFilter = new StringBuilder("(|");
			for (String somePivot : pivots.getAttributesNames()) {
				pivotFilter.append("(").append(getPivotName()).append("=").append(pivots.getValueForFilter(somePivot.toLowerCase())).append(")");
			}
			pivotFilter.append(")");
			computedFilter = Optional.of(filter.map(f -> "(&" + f  + pivotFilter.toString() + ")").orElse(pivotFilter.toString()));
		}
		return getList(computedFilter).entrySet().stream().findFirst();
	}
	
	public Optional<Entry<String, LscDatasets>> findFirstByPivot(String pivotValue) throws LscServiceException {
		StringBuilder pivotFilter = new StringBuilder();
		pivotFilter.append("(").append(getPivotName()).append("=").append(pivotValue).append(")");
		return getList(Optional.of(pivotFilter.toString())).entrySet().stream().findFirst();
	}

	public ValuesType getAttributes() {
		ValuesType flatAttributes = new ValuesType();
		for (AttributesTab attributesTab: attributesSettings.getTab()) {
			for (Attribute attribute : attributesTab.getAttribute()) {
				flatAttributes.getString().add(attribute.getValue());
			}
		}
		return flatAttributes;
	}

	public boolean create(Map<String, List<Object>> modificationsItemsByHash) throws LscServiceException {

		// Keeps session opened
		ping();

		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("attrs", prepareAttributes(modificationsItemsByHash));
		if (template.isPresent()) {
			payload.put("template", template.get());
		}
		WebTarget currentTarget = target.path(OBJECTS).path(entity);
		Response response = null;
		try {
			response = currentTarget.request().header(SESSION_TOKEN, getToken()).post(Entity.entity(payload, MediaType.APPLICATION_JSON));
			if (!checkResponse(response)) {
				String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
						response.readEntity(String.class));
				LOGGER.error(errorMessage);
				throw new LscServiceException(errorMessage);
			}
		} finally {
			if (response != null) {
				response.close();
			}
		}

		return true;
	}

	public boolean modify(String mainIdentifier, Map<String, List<Object>> modificationsItemsByHash) throws LscServiceException {

		// Keeps session opened
		ping();

		// retrieve DN
		Optional<Entry<String, LscDatasets>> entry = findFirstByPivot(mainIdentifier);

		if (entry.isPresent()) {
			String dn = entry.get().getValue().getStringValueAttribute(FusionDirectoryDao.DN);

			Map<String, Map<String, Object>> attributes = prepareAttributes(modificationsItemsByHash);

			if (attributes.size() > 0) {
				WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn);
				currentTarget.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
				Response response = null;
				try {
					response = currentTarget.request().header(SESSION_TOKEN, getToken()).method("PATCH", Entity.entity(attributes, MediaType.APPLICATION_JSON));
					if (!checkResponse(response)) {
						String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
								response.readEntity(String.class));
						LOGGER.error(errorMessage);
						throw new LscServiceException(errorMessage);
					}
				} finally {
					if (response != null) {
						response.close();
					}
				}
			}
			List<String> toDelete = prepareAttributesToDelete(modificationsItemsByHash);
			for (String deleteAttr: toDelete) {
				Response response = null;
				try {
					WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn).path(deleteAttr);
					response = currentTarget.request().header(SESSION_TOKEN, getToken()).delete();
					if (!checkResponse(response)) {
						String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
								response.readEntity(String.class));
						LOGGER.error(errorMessage);
						throw new LscServiceException(errorMessage);
					}
				} finally {
					if (response != null) {
						response.close();
					}
				}
			}
			return true;
		} else {
			throw new LscServiceException(String.format("Cannot find entity %s", mainIdentifier));
		}
	}

	public boolean delete(String mainIdentifier) throws LscServiceException {

		// Keeps session opened
		ping();

		Optional<Entry<String, LscDatasets>> entry = findFirstByPivot(mainIdentifier);
		if (entry.isPresent()) {
			String dn = entry.get().getValue().getStringValueAttribute(FusionDirectoryDao.DN);
			LOGGER.debug(String.format("Deleting %s with dn=%s", entity, dn));
			WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn);
			Response response = null;
			try {
				response = currentTarget.request().header(SESSION_TOKEN, getToken()).delete();
				if (!checkResponse(response)) {
					String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
							response.readEntity(String.class));
					LOGGER.error(errorMessage);
					throw new LscServiceException(errorMessage);
				}
			} finally {
				if (response != null) {
					response.close();
				}
			}
			return true;
		} else {
			throw new LscServiceException(String.format("Cannot find entity %s", mainIdentifier));
		}
	}
	private List<Tab> getEntityTabs(String dn) throws LscServiceException {
		Response response = null;
		try {
			WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn);
			response = currentTarget.request().accept(MediaType.APPLICATION_JSON).header(SESSION_TOKEN, getToken()).get(Response.class);
			if (!checkResponse(response)) {
				String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
						response.readEntity(String.class));
				LOGGER.error(errorMessage);
				throw new LscServiceException(errorMessage);
			}
			return Arrays.asList(mapper.readValue(response.readEntity(String.class), Tab[].class));
		} catch (JsonProcessingException e) {
			throw new LscServiceException(e);
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	private List<String> prepareAttributesToDelete(Map<String, List<Object>>  modificationsItemsByHash) throws LscServiceException {
		List<String> toDelete = new ArrayList<>();
		for (String attribute: modificationsItemsByHash.keySet()) {
			TabAttribute tabAttribute = getTabAttribute(attribute);
			if (modificationsItemsByHash.get(attribute) instanceof ArrayList<?>) {
				if (((ArrayList<?>) modificationsItemsByHash.get(attribute)).isEmpty() 
						&& !tabAttribute.getAttribute().isMultiple()
						&& !tabAttribute.isOption()) {
					toDelete.add(tabAttribute.getTab() + "/" + tabAttribute.getAttribute().getValue());
				}
			} else {
				throw new LscServiceException(String.format("%s is not a supported type for attribute %s",modificationsItemsByHash.get(attribute).getClass().toString(), attribute));
			}
		}
		return toDelete;
	}

	private Map<String, Map<String, Object>> prepareAttributes(Map<String, List<Object>>  modificationsItemsByHash) throws LscServiceException {
		Map<String, Map<String, Object>> attrs =  new HashMap<String, Map<String, Object>>();
		for (String attribute: modificationsItemsByHash.keySet()) {
			TabAttribute tabAttribute = getTabAttribute(attribute);
			if (modificationsItemsByHash.get(attribute) instanceof ArrayList<?>) {
				ArrayList<?> list = (ArrayList<?>) modificationsItemsByHash.get(attribute);
				if (!list.isEmpty() || tabAttribute.getAttribute().isMultiple() || tabAttribute.isOption()) {
					if (attrs.get(tabAttribute.getTab()) == null) {
						attrs.put(tabAttribute.getTab(), new HashMap<String, Object>());
					}
					if (tabAttribute.isOption()) {
						String attributeShortName=tabAttribute.stripOptionFromAttributeName();
						attrs.get(tabAttribute.getTab()).put(attributeShortName,
								tabAttribute.getOptionValues(attrs.get(tabAttribute.getTab()).get(attributeShortName), list));
					}
					else if (tabAttribute.getAttribute().isMultiple()) {
						attrs.get(tabAttribute.getTab()).put(tabAttribute.getAttribute().getValue(), list);
					}
					else if (tabAttribute.getAttribute().getPasswordHash() != null) {
						// specific use case for userPassword attribute: need to be sent as an array with hash to be set, otherwise new password is ignored by Fusiondirectory if it was not set
						attrs.get(tabAttribute.getTab()).put(tabAttribute.getAttribute().getValue(),
								getPasswordArray(list.get(0), tabAttribute.getAttribute().getPasswordHash()));
					}
					else {
						attrs.get(tabAttribute.getTab()).put(tabAttribute.getAttribute().getValue(), list.get(0));
					}
				}
			} else {
				throw new LscServiceException(String.format("%s is not a supported type for attribute %s",modificationsItemsByHash.get(attribute).getClass().toString(), attribute));
			}
		}
		return attrs;
	}
	private String[] getPasswordArray(Object somePassword, String passwordHash) {
		String userPassword = somePassword instanceof String ? (String)somePassword :
			somePassword instanceof byte[] ? new String((byte[])somePassword) : somePassword.toString();
		String[] passwordArr = { passwordHash, userPassword, userPassword, "", "" };
		return passwordArr;
	}
	public static boolean isOptionAttribute(String attribute) {
		return PATTERN_ATTR_OPT.matcher(attribute).matches();
	}
	public static String stripOptionFromAttributeName(String attribute) {
		Matcher mopt = PATTERN_ATTR_OPT.matcher(attribute);
		if (mopt.matches()) {
			return mopt.group(1);
		}
		return attribute;
	}
	@SuppressWarnings("unchecked")
	private List<String> filterAndStripOptionFromValues(String attribute, Object rawValues) {
		Matcher mopt = PATTERN_ATTR_OPT.matcher(attribute);
		List<String> values = new ArrayList<String>();
		if (mopt.matches()) {
			String option = mopt.group(2);
			if (rawValues instanceof String && ((String) rawValues).toLowerCase().startsWith(option.toLowerCase() + ";")) {
				values.add(((String) rawValues).replaceAll("(?i)" + option + ";", ""));
			} else if (rawValues instanceof List<?>) {
				for (Object rawValue: (List<Object>)rawValues) {
					if (((String) rawValue).toLowerCase().startsWith(option.toLowerCase() + ";")) {
						values.add(((String) rawValue).replaceAll("(?i)" + option + ";", ""));
					}
				}
			}
		}
		return values;
	}
	private TabAttribute getTabAttribute(String attribute) throws LscServiceException {
		TabAttribute tabAttribute = null;

		for (AttributesTab attributesTab: attributesSettings.getTab()) {
			for (Attribute someAttribute : attributesTab.getAttribute()) {
				if (someAttribute.getValue().equalsIgnoreCase(attribute)) {
					tabAttribute = new TabAttribute(attributesTab.getName(), someAttribute);
				}
			}
		}
		if (tabAttribute == null) {
			throw new LscServiceException(String.format("Cannot find tab for attribute %s", attribute));
		}
		return tabAttribute;
	}
	private class TabAttribute {
		String tab;
		Attribute attribute;
		public TabAttribute(String tab, Attribute attribute) {
			this.tab = tab;
			this.attribute = attribute;
		}
		public String getTab() {
			return tab;
		}
		public Attribute getAttribute() {
			return attribute;
		}
		public boolean isOption() {
			return FusionDirectoryDao.isOptionAttribute(attribute.getValue());
		}
		public String stripOptionFromAttributeName() {
			return FusionDirectoryDao.stripOptionFromAttributeName(attribute.getValue());
		}
		@SuppressWarnings("unchecked")
		public Object getOptionValues(Object currentValues, ArrayList<?> list) {
			List<String> newValues = new ArrayList<>();
			Matcher mopt = PATTERN_ATTR_OPT.matcher(attribute.getValue());
			if (mopt.matches()) {
				String option = mopt.group(2);
				if (currentValues != null) {
					if (currentValues instanceof String) {
						newValues.add((String)currentValues);
					} else if (currentValues instanceof List<?>) {
						newValues.addAll((List<? extends String>)currentValues);
					}
				}
				for (Object value: list) {
					newValues.add(option.toLowerCase() + ";" + (String)value);
				}
			}
			return newValues;
		}
	}
}
