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
import java.util.List;
import java.util.Map;
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
import org.lsc.exception.LscServiceException;
import org.lsc.plugins.connectors.fusiondirectory.beans.Login;
import org.lsc.plugins.connectors.fusiondirectory.beans.Tab;
import org.lsc.plugins.connectors.fusiondirectory.beans.Token;
import org.lsc.plugins.connectors.fusiondirectory.generated.Attribute;
import org.lsc.plugins.connectors.fusiondirectory.generated.Attributes;
import org.lsc.plugins.connectors.fusiondirectory.generated.AttributesTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class FusionDirectoryDao {

	private static final String UID = "uid";
	private static final String DN = "dn";
	private static final String DEFAULT = "default";
	private static final String SESSION_TOKEN = "Session-Token";
	private static final String OBJECTS = "objects";
	private static final Logger LOGGER = LoggerFactory.getLogger(FusionDirectoryDao.class);
	public static final Pattern PATTERN_ATTR_OPT = Pattern.compile("^(\\w+);(.*)$");

	private final String username;
	private final String password;
	private final int sessionLifetime;
	private final String directory;

	private WebTarget target;
	private ObjectMapper mapper;

	// Keep one token / thread worker
	private Map<String, Token> tokenCache;

	public FusionDirectoryDao(String url, String username, String password, int sessionLifetime,
			Optional<String> directory) {
		mapper = new ObjectMapper();
		this.username = username;
		this.password = password;
		this.sessionLifetime = sessionLifetime;
		this.directory = getDirectory(directory);
		Client client = ClientBuilder.newClient().register(new JacksonFeature());
		target = client.target(url);

		tokenCache = new HashMap<>();
	}

	private String getDirectory(Optional<String> directory) {
		return directory.orElse(DEFAULT);
	}
	
	public String getPivotName(Optional<String> pivot) {
		return pivot.orElse(UID);
	}


	public Token startSession() throws LscServiceException {
		Response response = null;
		try {
			Login login = new Login();
			login.setUser(username);
			login.setPassword(password);
			login.setDirectory(directory);
			WebTarget currentTarget = target.path("login");
			LOGGER.info(String.format("Login to FusionDirectory %s as %s for thread %s", currentTarget.getUri().toString(), username, Thread.currentThread().getId()));
			response = currentTarget.request().post(Entity.entity(login, MediaType.APPLICATION_JSON));
			if (!checkResponse(response)) {
				String errorMessage = String.format("Cannot log in Fusiondirectory, message: %s", response.readEntity(String.class));
				LOGGER.error(errorMessage);
				throw new LscServiceException(errorMessage);
			}
			return new Token(response.readEntity(String.class).replaceAll("\n", "").replaceAll("\"", ""));
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}
	private void closeSession(Token token) {
		Response response = null;
		try {
			WebTarget currentTarget = target.path("logout");
			LOGGER.info(String.format("Logout from FusionDirectory %s as %s for thread %s", currentTarget.getUri().toString(), username, Thread.currentThread().getId()));
			response = currentTarget.request().header(SESSION_TOKEN, token.getSessionId()).post(Entity.json(null));
			if (!checkResponse(response)) {
				String warnMessage = String.format("Cannot logout from Fusiondirectory, message: %s", response.readEntity(String.class));
				LOGGER.warn(warnMessage);
			}
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}
	private Token getToken(boolean resetSession) throws LscServiceException {
		if (resetSession == true) {
			LOGGER.info(String.format("Reset FusionDirectory session as %s for thread %s", username, Thread.currentThread().getId()));
		}
		synchronized (tokenCache) {
			Token token = tokenCache.get(String.valueOf(Thread.currentThread().getId()));
			if (token != null && token.hasExpired(this.sessionLifetime)) {
				LOGGER.info(String.format("Expire FusionDirectory session for thread %s after %s seconds.", Thread.currentThread().getId(), this.sessionLifetime));
				closeSession(token);
				resetSession = true;
			}
			if (token == null || resetSession) {
				token = startSession();
				tokenCache.put(String.valueOf(Thread.currentThread().getId()), token);
			}
			return token;
		}
	}

	public Response httpGet(WebTarget webTarget) throws LscServiceException {
		return httpGet(webTarget, false);
	}
	private Response httpGet(WebTarget webTarget, boolean resetSession) throws LscServiceException {
		Response response = webTarget.request().accept(MediaType.APPLICATION_JSON).header(SESSION_TOKEN, getToken(resetSession).getSessionId()).get(Response.class);
		if (!resetSession && Response.Status.fromStatusCode(response.getStatus()) == Response.Status.UNAUTHORIZED) {
			// Try again once to restart session.
			return httpGet(webTarget, true);
		}
		if (!checkResponse(response)) {
			String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
					response.readEntity(String.class));
			LOGGER.error(errorMessage);
			throw new LscServiceException(errorMessage);
		}
		return response;
	}

	public Response httpPost(WebTarget webTarget, Entity<?> entity) throws LscServiceException {
		return httpPost(webTarget, entity, false);
	}
	private Response httpPost(WebTarget webTarget, Entity<?> entity, boolean resetSession) throws LscServiceException {
		Response response = webTarget.request().header(SESSION_TOKEN, getToken(resetSession).getSessionId()).post(entity);
		if (!resetSession && Response.Status.fromStatusCode(response.getStatus()) == Response.Status.UNAUTHORIZED) {
			// Try again once to restart session.
			return httpPost(webTarget, entity, true);
		}
		if (!checkResponse(response)) {
			String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
					response.readEntity(String.class));
			LOGGER.error(errorMessage);
			throw new LscServiceException(errorMessage);
		}
		return response;
	}

	public Response httpPatch(WebTarget webTarget, Entity<?> entity) throws LscServiceException {
		return httpPatch(webTarget, entity, false);
	}
	private Response httpPatch(WebTarget webTarget, Entity<?> entity, boolean resetSession) throws LscServiceException {
		Response response = webTarget.request().header(SESSION_TOKEN, getToken(resetSession).getSessionId()).method("PATCH", entity);
		if (!resetSession && Response.Status.fromStatusCode(response.getStatus()) == Response.Status.UNAUTHORIZED) {
			// Try again once to restart session.
			return httpPatch(webTarget, entity, true);
		}
		if (!checkResponse(response)) {
			String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
					response.readEntity(String.class));
			LOGGER.error(errorMessage);
			throw new LscServiceException(errorMessage);
		}
		return response;
	}

	public Response httpPut(WebTarget webTarget, Entity<?> entity) throws LscServiceException {
		return httpPut(webTarget, entity, false);
	}

	private Response httpPut(WebTarget webTarget, Entity<?> entity, boolean resetSession) throws LscServiceException {
		Response response = webTarget.request().header(SESSION_TOKEN, getToken(resetSession).getSessionId())
				.put(entity);
		if (!resetSession && Response.Status.fromStatusCode(response.getStatus()) == Response.Status.UNAUTHORIZED) {
			// Try again once to restart session.
			return httpPut(webTarget, entity, true);
		}
		if (!checkResponse(response)) {
			String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
					response.readEntity(String.class));
			LOGGER.error(errorMessage);
			throw new LscServiceException(errorMessage);
		}
		return response;
	}

	public Response httpDelete(WebTarget webTarget) throws LscServiceException {
		return httpDelete(webTarget, false);
	}
	private Response httpDelete(WebTarget webTarget, boolean resetSession) throws LscServiceException {
		Response response = webTarget.request().header(SESSION_TOKEN, getToken(resetSession).getSessionId()).delete();
		if (!resetSession && Response.Status.fromStatusCode(response.getStatus()) == Response.Status.UNAUTHORIZED) {
			// Try again once to restart session.
			return httpDelete(webTarget, true);
		}
		if (!checkResponse(response)) {
			String errorMessage = String.format("status: %d, message: %s", response.getStatus(),
					response.readEntity(String.class));
			LOGGER.error(errorMessage);
			throw new LscServiceException(errorMessage);
		}
		return response;
	}

	public ObjectNode getList(String entity, Optional<String> base, Optional<String> pivot,
			Optional<String> computedFilter) throws LscServiceException {
		Response response = null;
		try {
			WebTarget currentTarget = target.path(OBJECTS).path(entity);
			if (base.isPresent()) {
				currentTarget = currentTarget.queryParam("base", base.get());
			}
			if (computedFilter.isPresent()) {
				currentTarget = currentTarget.queryParam("filter", computedFilter.get());
			}
			if (pivot.isPresent()) {
				currentTarget = currentTarget.queryParam("attrs[" + getPivotName(pivot) + "]", "*");
			}
			LOGGER.debug(String.format("Search %s from: %s with filter %s ", entity, currentTarget.getUri().toString(), computedFilter));
			response = httpGet(currentTarget);
			
			return (ObjectNode) mapper.readTree(response.readEntity(String.class));

		} catch (JsonProcessingException e) {
			throw new LscServiceException(e);
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	private static boolean checkResponse(Response response) {
		return Response.Status.Family.familyOf(response.getStatus()) == Response.Status.Family.SUCCESSFUL;
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getDetails(String dn, String entity, Attributes attributesSettings)
			throws LscServiceException {

		Response response = null;
		try {

			Map<String, Object> results = new HashMap<>();
			results.put(DN, dn);

			// Check for inactive tabs before requesting them (if an inactive tab is requested, a 400 error is sent)
			List<Tab> tabs = getEntityTabs(dn, entity);

			for (AttributesTab attributesTab: attributesSettings.getTab()) {
				Optional<Tab> tab = tabs.stream().filter(p -> p.getClass_().equals(attributesTab.getName())).findFirst();
				if (!tab.isPresent()) {
					String errorMessage = String.format("Tab %s do not exists for object %s", attributesTab.getName(), entity);
					LOGGER.error(errorMessage);
					throw new LscServiceException(errorMessage);
				}
				if (tab.get().getActive()) {
					WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn).path(attributesTab.getName());
					response = httpGet(currentTarget);
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
			if (rawValues instanceof String
					&& ((String) rawValues).toLowerCase().startsWith(option.toLowerCase() + ";")) {
				values.add(((String) rawValues).replaceAll("(?i)" + option + ";", ""));
			} else if (rawValues instanceof List<?>) {
				for (Object rawValue : (List<Object>) rawValues) {
					if (((String) rawValue).toLowerCase().startsWith(option.toLowerCase() + ";")) {
						values.add(((String) rawValue).replaceAll("(?i)" + option + ";", ""));
					}
				}
			}
		}
		return values;
	}

	public boolean create(String entity, Map<String, Map<String, Object>> attributes, Optional<String> template)
			throws LscServiceException {

		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("attrs", attributes);
		if (template.isPresent()) {
			payload.put("template", template.get());
		}
		WebTarget currentTarget = target.path(OBJECTS).path(entity);
		Response response = null;
		try {
			response = httpPost(currentTarget, Entity.entity(payload, MediaType.APPLICATION_JSON));
		} finally {
			if (response != null) {
				response.close();
			}
		}

		return true;
	}

	public boolean modify(String entity, String dn, Map<String, Map<String, Object>> updateAttributes,
			List<String> deleteAttributes)
			throws LscServiceException {

		if (updateAttributes.size() > 0) {
				WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn);
				currentTarget.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
				Response response = null;
				try {
					response = httpPatch(currentTarget, Entity.entity(updateAttributes, MediaType.APPLICATION_JSON));
				} finally {
					if (response != null) {
						response.close();
					}
				}
			}
			// List<String> toDelete = prepareAttributesToDelete(modificationsItemsByHash);
			for (String deleteAttr : deleteAttributes) {
				Response response = null;
				try {
					WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn).path(deleteAttr);
					response = httpDelete(currentTarget);
				} finally {
					if (response != null) {
						response.close();
					}
				}
			}
			return true;

	}

	public boolean delete(String entity, String dn) throws LscServiceException {
		LOGGER.debug(String.format("Deleting %s with dn=%s", entity, dn));
		WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn);
		Response response = null;
		try {
			response = httpDelete(currentTarget);
		} finally {
			if (response != null) {
				response.close();
			}
		}
		return true;
	}

	private List<Tab> getEntityTabs(String dn, String entity) throws LscServiceException {
		Response response = null;
		try {
			WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn);
			response = httpGet(currentTarget);
			return Arrays.asList(mapper.readValue(response.readEntity(String.class), Tab[].class));
		} catch (JsonProcessingException e) {
			throw new LscServiceException(e);
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	public List<String> getAttribute(String entity, String dn, String attribute) throws LscServiceException {
		List<String> results = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();
		Response response = null;
		try {
			WebTarget currentTarget = target.path(OBJECTS).path(entity);
			currentTarget = currentTarget.queryParam("base", dn);
			currentTarget = currentTarget.queryParam("attrs[" + attribute + "]", "*");

			response = httpGet(currentTarget);

			ObjectNode root = (ObjectNode) mapper.readTree(response.readEntity(String.class));
			Iterator<Map.Entry<String, JsonNode>> iter = root.fields();
			while (iter.hasNext()) {
				Map.Entry<String, JsonNode> entry = iter.next();
				Iterator<Map.Entry<String, JsonNode>> iter2 = entry.getValue().fields();
				while (iter2.hasNext()) {
					Map.Entry<String, JsonNode> entry2 = iter2.next();
					ArrayNode attributes = ((ArrayNode) entry2.getValue());
					attributes.forEach(jsonNode -> results.add(jsonNode.asText()));
				}
				break;
			}
		} catch (JsonProcessingException e) {
			throw new LscServiceException(e);
		} finally {
			if (response != null) {
				response.close();
			}
		}
		return results;
	}

	public void setAttribute(String entity, String dn, String tab, String attribute, List<String> values, boolean isMultiple) throws LscServiceException {
		Object payload = isMultiple ? values : "\"" + values.get(0) + "\"";
		WebTarget currentTarget = target.path(OBJECTS).path(entity).path(dn).path(tab).path(attribute);
		Response response = null;
		try {
			response = httpPut(currentTarget, Entity.entity(payload, MediaType.APPLICATION_JSON));
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

}
