/*
 * Copyright 2005-2007 The Kuali Foundation
 * 
 * 
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.opensource.org/licenses/ecl2.php
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kuali.rice.kew.edl;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Templates;

import org.apache.log4j.Logger;
import org.kuali.rice.kew.edl.bo.EDocLiteAssociation;
import org.kuali.rice.kew.edl.service.EDocLiteService;
import org.kuali.rice.kew.exception.WorkflowRuntimeException;
import org.kuali.rice.kew.routeheader.DocumentRouteHeaderValue;
import org.kuali.rice.kew.service.KEWServiceLocator;
import org.kuali.rice.kew.util.XmlHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * Creates EDL controllers.  Contains a cache of parsed EDL configurations.  The parsed config is a definition name related to 
 * a Map containing config element and their associated class.
 * 
 * @author Kuali Rice Team (rice.collab@kuali.org)
 *
 */
public class EDLControllerFactory {

	private static final Logger LOG = Logger.getLogger(EDLControllerFactory.class);
	
	private static final String CONFIG_CACHE_GROUP_NAME = "EDLConfig";

	//private static Map edlConfigCache = new HashMap();

	public static EDLController createEDLController(EDocLiteAssociation edlAssociation, EDLGlobalConfig edlGlobalConfig) {
        EDLController edlController = new EDLController();
		edlController.setEdocLiteAssociation(edlAssociation);

		try {
			edlController.setEdlGlobalConfig(edlGlobalConfig);
			edlController.setDefaultDOM(getDefaultDOM(edlAssociation));
			loadConfigProcessors(edlController, edlGlobalConfig);
			loadPreProcessors(edlController, edlGlobalConfig);
			loadPostProcessor(edlController, edlGlobalConfig);
			loadStateComponents(edlController, edlGlobalConfig);
			loadStyle(edlController);
			
		} catch (Exception e) {
            String edl = null;
            if (edlAssociation != null) {
                edl = edlAssociation.getEdlName();
            }
            String message = "Error creating controller for EDL" + (edl == null ? "" : ": " + edl);
            LOG.error(message, e);
			throw new WorkflowRuntimeException("Problems creating controller for EDL: " + edl, e);
		}

		return edlController;
	}

	public static EDLController createEDLController(EDocLiteAssociation edlAssociation, EDLGlobalConfig edlGlobalConfig, DocumentRouteHeaderValue document) {
		EDLController edlController = createEDLController(edlAssociation, edlGlobalConfig);
		try {
			Document defaultDom = edlController.getDefaultDOM();
			Document documentDom = XmlHelper.readXml(document.getDocContent());
			// get the data node and import it into our default DOM
			Element documentData = (Element) documentDom.getElementsByTagName(EDLXmlUtils.DATA_E).item(0);
			if (documentData != null) {
				Element defaultDomEDL = EDLXmlUtils.getEDLContent(defaultDom, false);
				Element defaultDomData = (Element) defaultDomEDL.getElementsByTagName(EDLXmlUtils.DATA_E).item(0);
				defaultDomEDL.replaceChild(defaultDom.importNode(documentData, true), defaultDomData);
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug("Created default Node from document id " + document.getRouteHeaderId() + " content " + XmlHelper.jotNode(defaultDom));
			}
		} catch (Exception e) {
			throw new WorkflowRuntimeException("Problems creating controller for EDL " + edlAssociation.getEdlName() + " document " + document.getRouteHeaderId(), e);
		}
		return edlController;
	}

	private synchronized static void loadStyle(EDLController edlController) throws Exception {
		EDocLiteService edlService = getEDLService();
		Templates styleSheet = null;
		styleSheet = edlService.getStyleAsTranslet(edlController.getEdocLiteAssociation().getStyle());
		edlController.setStyle(styleSheet);
	}
	
	private synchronized static void loadPreProcessors(EDLController edlController, EDLGlobalConfig edlGlobalConfig) {
		edlController.setPreProcessors(cloneConfigMap(edlGlobalConfig.getPreProcessors(), edlController.getDefaultDOM()));
	}
	
	private synchronized static void loadPostProcessor(EDLController edlController, EDLGlobalConfig edlGlobalConfig) {
		edlController.setPostProcessors(cloneConfigMap(edlGlobalConfig.getPostProcessors(), edlController.getDefaultDOM()));
	}
	
	private synchronized static void loadStateComponents(EDLController edlController, EDLGlobalConfig edlGlobalConfig) {
		edlController.setStateComponents(cloneConfigMap(edlGlobalConfig.getStateComponents(), edlController.getDefaultDOM()));
	}

	private synchronized static void loadConfigProcessors(EDLController edlController, EDLGlobalConfig edlGlobalConfig) throws Exception {
		EDocLiteAssociation edlAssociation = edlController.getEdocLiteAssociation();
		Map configProcessorMappings = fetchConfigFromCache(edlAssociation.getDefinition());
		if (configProcessorMappings != null) {
			edlController.setConfigProcessors(cloneConfigMap(configProcessorMappings, edlController.getDefaultDOM()));
		} else {
			// these are classes mapped to the conf element from the edlconfig.
			Document document = getEDLService().getDefinitionXml(edlAssociation);
			Element definitionElement = (Element) document.getFirstChild();

			configProcessorMappings = new LinkedHashMap();
			edlController.setEdlGlobalConfig(edlGlobalConfig);
			NodeList edlDefinitionNodes = definitionElement.getChildNodes();
			for (int i = 0; i < edlDefinitionNodes.getLength(); i++) {
				Node definitionNode = edlDefinitionNodes.item(i);
				Class configProcessorClass = edlGlobalConfig.getConfigProcessor(definitionNode);
				if (configProcessorClass != null) {
					configProcessorMappings.put(definitionNode, configProcessorClass);
				}
			}
			putConfigInCache(edlAssociation.getDefinition(), configProcessorMappings);
//			edlConfigCache.put(edlAssociation.getDefinition(), configProcessorMappings);
			loadConfigProcessors(edlController, edlGlobalConfig);
		}
	}
	
	protected synchronized static Map fetchConfigFromCache(String definition) {
		return (Map)KEWServiceLocator.getCacheAdministrator().getFromCache(getConfigCacheKey(definition));
	}
	
	private synchronized static void putConfigInCache(String definition, Map configProcessorMappings) {
		KEWServiceLocator.getCacheAdministrator().putInCache(getConfigCacheKey(definition), configProcessorMappings, CONFIG_CACHE_GROUP_NAME);
	}
	
	private static String getConfigCacheKey(String definition) {
		return CONFIG_CACHE_GROUP_NAME + ":" + definition;
	}
	
	private synchronized static Map cloneConfigMap(Map configMap, Document defaultDom) {
		Map tempConfigProcessors = new LinkedHashMap();
		for (Iterator iter = configMap.entrySet().iterator(); iter.hasNext();) {
			Map.Entry configProcessorMapping = (Map.Entry) iter.next();
			tempConfigProcessors.put(defaultDom.importNode((Node)configProcessorMapping.getKey(), true), configProcessorMapping.getValue());
		}
		return tempConfigProcessors;
	}

	private static EDocLiteService getEDLService() {
		return KEWServiceLocator.getEDocLiteService();
	}

	private static Document getDefaultDOM(EDocLiteAssociation edlAssociation) throws Exception {
		Document dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element rootElement = dom.createElement("documentContent"); // this is a
		// throwback
		// to some
		// original
		// madness
		// to get EDL routing over a year ago we need to look into this being
		// eliminated.
		dom.appendChild(rootElement);
		Element edlContentElement = EDLXmlUtils.getEDLContent(dom, true);
		EDLXmlUtils.getDataFromEDLDocument(edlContentElement, true);
		
		// get the data element that was just created ***jitrue***
		Element edlData = EDLXmlUtils.getChildElement(edlContentElement, EDLXmlUtils.DATA_E);
		// set edlName attribute on data element of default DOM ***jitrue***
		edlData.setAttribute("edlName", edlAssociation.getEdlName());
		
		return dom;
	}

	public static void flushDefinitionFromConfigCache(String definition) {
		KEWServiceLocator.getCacheAdministrator().flushEntry(getConfigCacheKey(definition));
//		edlConfigCache.remove(defName);
	}
	
	public static void flushDefinitionCache() {
		KEWServiceLocator.getCacheAdministrator().flushGroup(CONFIG_CACHE_GROUP_NAME);
	}
}