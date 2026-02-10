package org.lsc.plugins.connectors.fusiondirectory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lsc.LscDatasets;
import org.lsc.beans.IBean;
import org.lsc.configuration.ConnectionType;
import org.lsc.configuration.ValuesType;
import org.lsc.exception.LscServiceCommunicationException;
import org.lsc.exception.LscServiceException;
import org.lsc.plugins.connectors.fusiondirectory.generated.Attribute;
import org.lsc.plugins.connectors.fusiondirectory.generated.Attributes;
import org.lsc.plugins.connectors.fusiondirectory.generated.AttributesTab;
import org.lsc.service.IService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class FusionDirectoryAbstractService implements IService {

	private static final Logger LOGGER = LoggerFactory.getLogger(FusionDirectoryAbstractService.class);
	protected static final String DN = "dn";
	protected FusionDirectoryDao dao;
	protected Class<IBean> beanClass;

	protected String entity;
	protected Optional<String> base;
	protected Optional<String> pivot;
	protected Optional<String> filter;
	protected Optional<String> allFilter;
	protected Optional<String> oneFilter;
	protected Optional<String> cleanFilter;
	protected Optional<String> template;
	protected Attributes attributesSettings;

	@Override
	public Map<String, LscDatasets> getListPivots() throws LscServiceException {
		try {
			return this.getList();
		} catch (Exception e) {
			LOGGER.error(String.format("Error while getting pivot list (%s)", e));
			LOGGER.debug(e.toString(), e);
			throw new LscServiceCommunicationException(e);
		}
	}
	protected Map<String, LscDatasets> getList() throws LscServiceException {
		return getList(allFilter.isPresent() ? allFilter : filter);
	}
	private Map<String, LscDatasets> getList(Optional<String> computedFilter) throws LscServiceException {
		Map<String, LscDatasets> resources = new LinkedHashMap<>();
		ObjectNode root = dao.getList(entity, base, pivot, computedFilter);
		Iterator<Map.Entry<String, JsonNode>> iter = root.fields();
		while (iter.hasNext()) {
			Map.Entry<String, JsonNode> entry = iter.next();
			Iterator<Map.Entry<String, JsonNode>> iter2 = entry.getValue().fields();
			while (iter2.hasNext()) {
				Map.Entry<String, JsonNode> entry2 = iter2.next();
				String pivotValue = ((ArrayNode) entry2.getValue()).get(0).textValue();
				LscDatasets datasets = new LscDatasets();
				datasets.put(DN, entry.getKey());
				datasets.put(entry2.getKey(), pivotValue);
				resources.put(pivotValue, datasets);
			}
		}
		return resources;

	}

	protected Optional<Entry<String, LscDatasets>> findFirstByPivots(LscDatasets pivots, boolean clean)
			throws LscServiceException {
		Optional<String> computedFilter = clean ? cleanFilter : oneFilter;
		if (computedFilter.isPresent()) {
			for (String somePivot : pivots.getAttributesNames()) {
				computedFilter = Optional.of(Pattern.compile("\\{" + somePivot + "\\}", Pattern.CASE_INSENSITIVE)
						.matcher(computedFilter.get())
						.replaceAll(Matcher.quoteReplacement(pivots.getValueForFilter(somePivot.toLowerCase()))));
			}
		} else {
			StringBuilder pivotFilter = new StringBuilder("(|");
			for (String somePivot : pivots.getAttributesNames()) {
				pivotFilter.append("(").append(getPivotName()).append("=")
						.append(pivots.getValueForFilter(somePivot.toLowerCase())).append(")");
			}
			pivotFilter.append(")");
			computedFilter = Optional
					.of(filter.map(f -> "(&" + f + pivotFilter.toString() + ")").orElse(pivotFilter.toString()));
		}
		return getList(computedFilter).entrySet().stream().findFirst();
	}

	protected String getPivotName() {
		return dao.getPivotName(pivot);
	}

	protected Optional<Entry<String, LscDatasets>> findFirstByPivot(String pivotValue) throws LscServiceException {
		StringBuilder pivotFilter = new StringBuilder();
		pivotFilter.append("(").append(dao.getPivotName(pivot)).append("=").append(pivotValue).append(")");
		return getList(Optional.of(pivotFilter.toString())).entrySet().stream().findFirst();
	}


	protected boolean create(Map<String, List<Object>> modificationsItemsByHash) throws LscServiceException {
		return dao.create(entity, prepareAttributes(modificationsItemsByHash), template);
	}

	public boolean modify(String mainIdentifier, Map<String, List<Object>> modificationsItemsByHash) throws LscServiceException {
		// retrieve DN
		Optional<Entry<String, LscDatasets>> entry = findFirstByPivot(mainIdentifier);
		if (entry.isPresent()) {
			return dao.modify(entity, entry.get().getValue().getStringValueAttribute(DN),
					prepareAttributes(modificationsItemsByHash), prepareAttributesToDelete(modificationsItemsByHash));
		}
		throw new LscServiceException(String.format("Cannot find entity %s", mainIdentifier));
	}

	public boolean delete(String mainIdentifier) throws LscServiceException {

		Optional<Entry<String, LscDatasets>> entry = findFirstByPivot(mainIdentifier);
		if (entry.isPresent()) {
			return dao.delete(entity, entry.get().getValue().getStringValueAttribute(DN));
		}
		throw new LscServiceException(String.format("Cannot find entity %s", mainIdentifier));
	}

	protected ValuesType getAttributes() {
		ValuesType flatAttributes = new ValuesType();
		for (AttributesTab attributesTab : attributesSettings.getTab()) {
			for (Attribute attribute : attributesTab.getAttribute()) {
				flatAttributes.getString().add(attribute.getValue());
			}
		}
		return flatAttributes;
	}

	protected Map<String, Object> getDetails(String dn) throws LscServiceException {
		return dao.getDetails(dn, entity, attributesSettings);
	}

	protected Optional<String> getStringParameter(String parameter) {
		return Optional.ofNullable(parameter).filter(f -> !f.trim().isEmpty());
	}

	private Map<String, Map<String, Object>> prepareAttributes(Map<String, List<Object>> modificationsItemsByHash)
			throws LscServiceException {
		Map<String, Map<String, Object>> attrs = new HashMap<String, Map<String, Object>>();
		for (String attribute : modificationsItemsByHash.keySet()) {
			TabAttribute tabAttribute = getTabAttribute(attribute);
			if (modificationsItemsByHash.get(attribute) instanceof ArrayList<?>) {
				ArrayList<?> list = (ArrayList<?>) modificationsItemsByHash.get(attribute);
				if (!list.isEmpty() || tabAttribute.getAttribute().isMultiple() || tabAttribute.isOption()) {
					if (attrs.get(tabAttribute.getTab()) == null) {
						attrs.put(tabAttribute.getTab(), new HashMap<String, Object>());
					}
					if (tabAttribute.isOption()) {
						String attributeShortName = tabAttribute.stripOptionFromAttributeName();
						attrs.get(tabAttribute.getTab()).put(attributeShortName, tabAttribute
								.getOptionValues(attrs.get(tabAttribute.getTab()).get(attributeShortName), list));
					} else if (tabAttribute.getAttribute().isMultiple()) {
						attrs.get(tabAttribute.getTab()).put(tabAttribute.getAttribute().getValue(), list);
					} else if (tabAttribute.getAttribute().getPasswordHash() != null) {
						// specific use case for userPassword attribute: need to be sent as an array
						// with hash to be set, otherwise new password is ignored by Fusiondirectory if
						// it was not set
						attrs.get(tabAttribute.getTab()).put(tabAttribute.getAttribute().getValue(),
								getPasswordArray(list.get(0), tabAttribute.getAttribute().getPasswordHash()));
					} else {
						attrs.get(tabAttribute.getTab()).put(tabAttribute.getAttribute().getValue(), list.get(0));
					}
				}
			} else {
				throw new LscServiceException(String.format("%s is not a supported type for attribute %s",
						modificationsItemsByHash.get(attribute).getClass().toString(), attribute));
			}
		}
		return attrs;
	}

	private List<String> prepareAttributesToDelete(Map<String, List<Object>> modificationsItemsByHash)
			throws LscServiceException {
		List<String> toDelete = new ArrayList<>();
		for (String attribute : modificationsItemsByHash.keySet()) {
			TabAttribute tabAttribute = getTabAttribute(attribute);
			if (modificationsItemsByHash.get(attribute) instanceof ArrayList<?>) {
				if (((ArrayList<?>) modificationsItemsByHash.get(attribute)).isEmpty()
						&& !tabAttribute.getAttribute().isMultiple() && !tabAttribute.isOption()) {
					toDelete.add(tabAttribute.getTab() + "/" + tabAttribute.getAttribute().getValue());
				}
			} else {
				throw new LscServiceException(String.format("%s is not a supported type for attribute %s",
						modificationsItemsByHash.get(attribute).getClass().toString(), attribute));
			}
		}
		return toDelete;
	}

	private String[] getPasswordArray(Object somePassword, String passwordHash) {
		String userPassword = somePassword instanceof String ? (String) somePassword
				: somePassword instanceof byte[] ? new String((byte[]) somePassword) : somePassword.toString();
		String[] passwordArr = { passwordHash, userPassword, userPassword, "", "" };
		return passwordArr;
	}




	private TabAttribute getTabAttribute(String attribute) throws LscServiceException {
		TabAttribute tabAttribute = null;

		for (AttributesTab attributesTab : attributesSettings.getTab()) {
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
			Matcher mopt = FusionDirectoryDao.PATTERN_ATTR_OPT.matcher(attribute.getValue());
			if (mopt.matches()) {
				String option = mopt.group(2);
				if (currentValues != null) {
					if (currentValues instanceof String) {
						newValues.add((String) currentValues);
					} else if (currentValues instanceof List<?>) {
						newValues.addAll((List<? extends String>) currentValues);
					}
				}
				for (Object value : list) {
					newValues.add(option.toLowerCase() + ";" + (String) value);
				}
			}
			return newValues;
		}
	}

	@Override
	public Collection<Class<? extends ConnectionType>> getSupportedConnectionType() {
		Collection<Class<? extends ConnectionType>> list = new ArrayList<Class<? extends ConnectionType>>();
		return list;
	}

}
