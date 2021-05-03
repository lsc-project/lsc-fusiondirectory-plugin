package org.lsc.plugins.connectors.fusiondirectory.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.lsc.exception.LscServiceException;
import org.lsc.plugins.connectors.fusiondirectory.beans.Login;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class FusionDirectorySearch {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FusionDirectorySearch.class);
	
	private static final String DEFAULT = "default";
	private static final String SESSION_TOKEN = "Session-Token";
	private static final String OBJECTS = "objects";

	private WebTarget target;
	private String token;
	
	public FusionDirectorySearch() {
		// Instancied by LSC through customLibrary.
	}
	
	private WebTarget getTarget(String endpoint) {
		if (target == null) {
			Client client = ClientBuilder.newClient().register(new JacksonFeature());
			target = client.target(endpoint);
		}
		return target;
	}
	
	public void connect(String endpoint, String username, String password) throws LscServiceException {
		connect(endpoint, username, password, null);
	}
	
	public void connect(String endpoint, String username, String password, String directory) throws LscServiceException {
		if (!ping(endpoint)) {
			target = getTarget(endpoint);
			Response response = null;
			try {
				Login login = new Login();
				login.setUser(username);
				login.setPassword(password);
				login.setDirectory(getDirectory(optional(directory)));
				WebTarget currentTarget = target.path("login");
				response = currentTarget.request().post(Entity.entity(login, MediaType.APPLICATION_JSON));
				if (!checkResponse(response)) {
					String errorMessage = String.format("Cannot login Fusiondirectory, message: %s", response.readEntity(String.class));
					LOGGER.error(errorMessage);
					throw new LscServiceException(errorMessage);
				}
				token = response.readEntity(String.class).replaceAll("\n", "").replaceAll("\"", "");
			} finally {
				if (response != null) {
					response.close();
				}
			}
		}
	}
	
	private boolean ping(String endpoint) throws LscServiceException {
		if (token != null) {
			Response response = null;
			try {
				WebTarget currentTarget = getTarget(endpoint).path("token");
				response = currentTarget.request().header(SESSION_TOKEN, token).get();
				if (Response.Status.fromStatusCode(response.getStatus()) == Response.Status.UNAUTHORIZED) {
					return false;
				} else if (!checkResponse(response)) {
					String errorMessage = String.format("Cannot ping Fusiondirectory, message: %s", response.readEntity(String.class));
					LOGGER.error(errorMessage);
					throw new LscServiceException(errorMessage);
				}
				return true;
			} finally {
				if (response != null) {
					response.close();
				}
			}
		}
		return false;
	}
	
	public List<String> search(String entity) throws LscServiceException {
		return search(entity, null);
	}
	public List<String> search(String entity, String baseString) throws LscServiceException {
		return search(entity, baseString, null);
	}
	public List<String> search(String entity, String baseString, String filterString) throws LscServiceException {
		
		Optional<String> base = optional(baseString);
		Optional<String> filter = optional(filterString);
		
		List<String> results = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();
		Response response = null;
		try {
			WebTarget currentTarget = target.path(OBJECTS).path(entity);
			if (base.isPresent()) {
				currentTarget = currentTarget.queryParam("base", base.get());
			}
			if (filter.isPresent()) {
				currentTarget = currentTarget.queryParam("filter", filter.get());
			}
			
			response = currentTarget.request().accept(MediaType.APPLICATION_JSON).header(SESSION_TOKEN, token).get(Response.class);
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
				results.add(entry.getKey());
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
	
	public List<String> attribute(String entity, String dn, String attribute) throws LscServiceException {
		List<String> results = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();
		Response response = null;
		try {
			WebTarget currentTarget = target.path(OBJECTS).path(entity);
			currentTarget = currentTarget.queryParam("base", dn);
			currentTarget = currentTarget.queryParam("attrs["+attribute+"]", "*");

			response = currentTarget.request().accept(MediaType.APPLICATION_JSON).header(SESSION_TOKEN, token).get(Response.class);
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
					ArrayNode attributes = ((ArrayNode)entry2.getValue());
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
	
	private String getDirectory(Optional<String> directory) {
		return directory.map(p -> p).orElse(DEFAULT);
	}
	
	private static boolean checkResponse(Response response) {
		return Response.Status.Family.familyOf(response.getStatus()) == Response.Status.Family.SUCCESSFUL;
	}
	
	private Optional<String> optional(String string) {
		return Optional.ofNullable(string).filter(f -> !f.trim().isEmpty());
	}
}
