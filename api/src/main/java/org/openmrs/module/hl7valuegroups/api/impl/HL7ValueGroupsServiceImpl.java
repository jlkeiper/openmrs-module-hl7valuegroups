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
package org.openmrs.module.hl7valuegroups.api.impl;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.app.ApplicationException;
import ca.uhn.hl7v2.app.MessageTypeRouter;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericParser;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.hl7.HL7Constants;
import org.openmrs.hl7.HL7InArchive;
import org.openmrs.hl7.HL7InError;
import org.openmrs.hl7.HL7InQueue;
import org.openmrs.module.hl7valuegroups.api.HL7ValueGroupsService;
import org.openmrs.module.hl7valuegroups.api.db.HL7ValueGroupsDAO;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * It is a default implementation of {@link HL7ValueGroupsService}.
 */
public class HL7ValueGroupsServiceImpl extends BaseOpenmrsService implements HL7ValueGroupsService {
	
	protected final Log log = LogFactory.getLog(this.getClass());
	
	private HL7ValueGroupsDAO dao;

	private MessageTypeRouter router;

	/**
     * @param dao the dao to set
     */
    public void setDao(HL7ValueGroupsDAO dao) {
	    this.dao = dao;
    }

	/**
	 * Used by spring to inject the router
	 *
	 * @param router the router to use
	 */
	public void setRouter(MessageTypeRouter router) {
		this.router = router;
	}

	/**
	 * @see org.openmrs.hl7.HL7Service#processHL7InQueue(org.openmrs.hl7.HL7InQueue)
	 */
	@Override
	public HL7InQueue processHL7InQueue(HL7InQueue hl7InQueue) throws HL7Exception {

		if (hl7InQueue == null)
			throw new HL7Exception("hl7InQueue argument cannot be null");

		// mark this queue object as processing so that it isn't processed twice
		if (OpenmrsUtil.nullSafeEquals(HL7Constants.HL7_STATUS_PROCESSING, hl7InQueue.getMessageState()))
			throw new HL7Exception("The hl7InQueue message with id: " + hl7InQueue.getHL7InQueueId()
					+ " is already processing. " + ",key=" + hl7InQueue.getHL7SourceKey() + ")");
		else
			hl7InQueue.setMessageState(HL7Constants.HL7_STATUS_PROCESSING);

		if (log.isDebugEnabled())
			log.debug("Processing HL7 inbound queue (id=" + hl7InQueue.getHL7InQueueId() + ",key="
					+ hl7InQueue.getHL7SourceKey() + ")");

		// Parse the HL7 into an HL7Message or abort with failure
		String hl7Message = hl7InQueue.getHL7Data();
		try {
			// Parse the inbound HL7 message using the parser
			// NOT making a direct call here so that AOP can happen around this
			// method
			Message parsedMessage = Context.getHL7Service().parseHL7String(hl7Message);

			// Send the parsed message to our receiver routine for processing
			// into db
			// NOT making a direct call here so that AOP can happen around this
			// method
			this.processHL7Message(parsedMessage);

			// Move HL7 inbound queue entry into the archive before exiting
			log.debug("Archiving HL7 inbound queue entry");

			Context.getHL7Service().saveHL7InArchive(new HL7InArchive(hl7InQueue));

			log.debug("Removing HL7 message from inbound queue");
			Context.getHL7Service().purgeHL7InQueue(hl7InQueue);
		}
		catch (HL7Exception e) {
			boolean skipError = false;
			log.debug("Unable to process hl7inqueue: " + hl7InQueue.getHL7InQueueId(), e);
			log.debug("Hl7inqueue source: " + hl7InQueue.getHL7Source());
			log.debug("hl7_processor.ignore_missing_patient_non_local? "
					+ Context.getAdministrationService().getGlobalProperty(
					OpenmrsConstants.GLOBAL_PROPERTY_IGNORE_MISSING_NONLOCAL_PATIENTS, "false"));
			if (e.getCause() != null
					&& e.getCause().getMessage().equals("Could not resolve patient")
					&& !hl7InQueue.getHL7Source().getName().equals("local")
					&& Context.getAdministrationService().getGlobalProperty(
					OpenmrsConstants.GLOBAL_PROPERTY_IGNORE_MISSING_NONLOCAL_PATIENTS, "false").equals("true")) {
				skipError = true;
			}
			if (!skipError)
				setFatalError(hl7InQueue, "Trouble parsing HL7 message (" + hl7InQueue.getHL7SourceKey() + ")", e);

		}
		catch (Throwable t) {
			setFatalError(hl7InQueue, "Exception while attempting to process HL7 In Queue (" + hl7InQueue.getHL7SourceKey()
					+ ")", t);
		}

		return hl7InQueue;
	}

	/**
	 * @see org.openmrs.hl7.HL7Service#processHL7Message(ca.uhn.hl7v2.model.Message)
	 */
	private Message processHL7Message(Message message) throws HL7Exception {
		// Any post-parsing (pre-routing) processing would go here
		// or a module can use AOP to do the post-parsing

		Message response;
		try {
			if (!router.canProcess(message))
				throw new HL7Exception("No route for hl7 message: " + message.getName()
						+ ". Make sure you have a module installed that registers a hl7handler for this type");
			response = router.processMessage(message);
		}
		catch (ApplicationException e) {
			throw new HL7Exception("Error while processing HL7 message: " + message.getName(), e);
		}

		return response;
	}

	/**
	 * Convenience method to respond to fatal errors by moving the queue entry into an error bin
	 * prior to aborting
	 */
	private void setFatalError(HL7InQueue hl7InQueue, String error, Throwable cause) {
		HL7InError hl7InError = new HL7InError(hl7InQueue);
		hl7InError.setError(error);
		if (cause == null)
			hl7InError.setErrorDetails("");
		else {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw, true);
			cause.printStackTrace(pw);
			pw.flush();
			sw.flush();
			hl7InError.setErrorDetails(OpenmrsUtil.shortenedStackTrace(sw.toString()));
		}
		Context.getHL7Service().saveHL7InError(hl7InError);
		Context.getHL7Service().purgeHL7InQueue(hl7InQueue);
		log.error(error, cause);
	}

	/**
	 * Sets the given handlers as router applications that are available to HAPI when it is parsing
	 * an hl7 message.<br/>
	 * This method is usually used by Spring and the handlers are set in the
	 * applicationContext-server.xml method.<br/>
	 * The key in the map is a string like "ORU_R01" where the first part is the message type and
	 * the second is the trigger event.
	 *
	 * @param handlers a map from MessageName to Application object
	 */
	public void setHL7Handlers(Map<String, Application> handlers) {
		// loop over all the given handlers and add them to the router
		for (Map.Entry<String, Application> entry : handlers.entrySet()) {
			String messageName = entry.getKey();
			if (!messageName.contains("_"))
				throw new APIException("Invalid messageName.  The format must be messageType_triggerEvent, e.g: ORU_R01");

			String messageType = messageName.split("_")[0];
			String triggerEvent = messageName.split("_")[1];

			router.registerApplication(messageType, triggerEvent, entry.getValue());
		}
	}

}