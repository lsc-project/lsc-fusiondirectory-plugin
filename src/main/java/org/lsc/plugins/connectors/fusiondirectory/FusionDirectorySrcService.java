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

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

import org.lsc.LscDatasets;
import org.lsc.beans.IBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.TaskType;
import org.lsc.configuration.ConnectionType;
import org.lsc.exception.LscServiceCommunicationException;
import org.lsc.exception.LscServiceConfigurationException;
import org.lsc.exception.LscServiceException;
import org.lsc.plugins.connectors.fusiondirectory.generated.ServiceSettings;
import org.lsc.service.IService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FusionDirectorySrcService implements IService {

	protected static final Logger LOGGER = LoggerFactory.getLogger(FusionDirectorySrcService.class);
	private final Class<IBean> beanClass;
	private final FusionDirectoryDao dao;

	public FusionDirectorySrcService(final TaskType task) throws LscServiceConfigurationException {
		try {
			if (task.getPluginSourceService().getAny() == null || task.getPluginSourceService().getAny().size() != 1
					|| !(task.getPluginSourceService().getAny().get(0) instanceof ServiceSettings)) {
				throw new LscServiceConfigurationException(
						"Unable to identify the fusiondirectory service configuration inside the plugin source node of the task: "
								+ task.getName());
			}
			ServiceSettings settings = (ServiceSettings) task.getPluginSourceService()
					.getAny().get(0);
			PluginConnectionType pluginConnectionType = (PluginConnectionType) task.getPluginSourceService()
					.getConnection().getReference();
			if (pluginConnectionType == null) {
				throw new LscServiceConfigurationException(
						"Unable to identify the fusiondirectory connection settings inside the connection node of the task: "
								+ task.getName());
			}
			beanClass = (Class<IBean>) Class.forName(task.getBean());
			dao = new FusionDirectoryDao(pluginConnectionType, settings);
		} catch (Exception e) {
			throw new LscServiceConfigurationException(e);
		}
	}

	@Override
	public Map<String, LscDatasets> getListPivots() throws LscServiceException {
		try {
			return dao.getList();
		} catch (Exception e) {
			LOGGER.error(String.format("Error while getting pivot list (%s)", e));
			LOGGER.debug(e.toString(), e);
			throw new LscServiceCommunicationException(e);
		}
	}

	@Override
	public IBean getBean(String pivotRawValue, LscDatasets lscDatasets, boolean fromSameService)
			throws LscServiceException {
		LOGGER.debug(String.format("Call to getBean(%s, %s, %b)", pivotRawValue, lscDatasets, fromSameService));
		if (lscDatasets.getAttributesNames().size() < 1) {
			return null;
		}
		if (fromSameService) {
			return getBeanFromSameService(pivotRawValue, lscDatasets.getStringValueAttribute(FusionDirectoryDao.DN));
		} else {
			return getBeanForClean(lscDatasets.getStringValueAttribute(dao.getPivotName()), lscDatasets);
		}
	}

	private IBean getBeanFromSameService(String pivotValue, String dn) throws LscServiceException {
		if (dn == null) {
			return null;
		}
		try {
			Map<String, Object> entity = dao.getDetails(dn);
			IBean bean = beanClass.newInstance();
			bean.setMainIdentifier(pivotValue);
			LscDatasets datasets = new LscDatasets();
			entity.entrySet().stream().forEach(entry -> datasets.put(entry.getKey(),
					entry.getValue() == null ? new LinkedHashSet<>() : entry.getValue()));
			bean.setDatasets(datasets);
			return bean;
		} catch (NotFoundException e) {
			LOGGER.debug(String.format("dn %s not found", dn));
			return null;
		} catch (ProcessingException | WebApplicationException e) {
			LOGGER.error(String.format("Exception while getting bean with dn %s (%s)", dn, e));
			LOGGER.error(e.toString(), e);
			throw new LscServiceException(e);
		} catch (InstantiationException | IllegalAccessException e) {
			LOGGER.error("Bad class name: " + beanClass.getName() + "(" + e + ")");
			LOGGER.debug(e.toString(), e);
			throw new LscServiceException(e);
		}
	}

	private IBean getBeanForClean(String pivotValue, LscDatasets pivots) throws LscServiceException {
		String pivotName = dao.getPivotName();
		try {
			Optional<Entry<String, LscDatasets>> entity = dao.findFirstByPivots(pivots, true);
			if (entity.isPresent()) {
				IBean bean = beanClass.newInstance();
				bean.setMainIdentifier(entity.get().getKey().toString());
				bean.setDatasets(entity.get().getValue());
				return bean;
			} else {
				return null;
			}
		} catch (NotFoundException e) {
			LOGGER.debug(String.format("%s %s not found", pivotName, pivotValue));
			return null;
		} catch (ProcessingException | WebApplicationException e) {
			LOGGER.error(String.format("Exception while getting bean %s/%s (%s)", pivotName, pivotValue, e));
			LOGGER.error(e.toString(), e);
			throw new LscServiceException(e);
		} catch (InstantiationException | IllegalAccessException e) {
			LOGGER.error("Bad class name: " + beanClass.getName() + "(" + e + ")");
			LOGGER.debug(e.toString(), e);
			throw new LscServiceException(e);
		}
	}

	public Collection<Class<? extends ConnectionType>> getSupportedConnectionType() {
		Collection<Class<? extends ConnectionType>> list = new ArrayList<Class<? extends ConnectionType>>();
		return list;
	}
}
