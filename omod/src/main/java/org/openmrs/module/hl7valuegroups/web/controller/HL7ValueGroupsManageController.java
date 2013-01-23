/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.hl7valuegroups.web.controller;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.hl7.HL7InQueue;
import org.openmrs.hl7.HL7Service;
import org.openmrs.hl7.HL7Source;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * The main controller.
 */
@Controller
@RequestMapping(value = "/module/hl7valuegroups/manage")
public class  HL7ValueGroupsManageController {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	@RequestMapping(method = RequestMethod.GET)
	public void manage(ModelMap model) {
	}


	@RequestMapping(method = RequestMethod.POST)
	public void apply(@RequestParam("hl7") MultipartFile hl7) {
		if (hl7 == null || hl7.isEmpty())
			return;

		log.warn("processing hl7 ...");
		HL7Service hl7Service = Context.getHL7Service();

		HL7InQueue queue = new HL7InQueue();
		queue.setHL7Source(hl7Service.getHL7Source(1));
		try {
			queue.setHL7Data(new String(hl7.getBytes()));
		} catch (IOException e) {
			log.warn("could not create hl7 from this data");
			return;
		}

		hl7Service.saveHL7InQueue(queue);
		log.warn("hl7 saved ...");
	}
}
