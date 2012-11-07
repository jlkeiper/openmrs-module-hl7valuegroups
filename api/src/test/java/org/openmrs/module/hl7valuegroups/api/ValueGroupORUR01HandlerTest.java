package org.openmrs.module.hl7valuegroups.api;

import ca.uhn.hl7v2.app.MessageTypeRouter;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseContextSensitiveTest;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

/**
 * Test class for ValueGroupORUR01Handler
 */
public class ValueGroupORUR01HandlerTest extends BaseModuleContextSensitiveTest {

	// test data to load before all tests (from OpenMRS core)
	protected static final String ORU_INITIAL_DATA_XML = "org/openmrs/hl7/include/ORUTest-initialData.xml";

	// hl7 parser for all tests
	protected static GenericParser parser = new GenericParser();

	// hl7 router for all tests
	private static MessageTypeRouter router = new MessageTypeRouter();

	// registering the lab ORU^R01 handler
	static {
		router.registerApplication("ORU", "R01", new ValueGroupORUR01Handler());
	}

	/**
	 * Run this before each unit test in this class. This adds the hl7 specific data to the initial
	 * and demo data done in the "@Before" method in {@link BaseContextSensitiveTest}.
	 *
	 * @throws Exception
	 */
	@Before
	public void runBeforeEachTest() throws Exception {
		executeDataSet(ORU_INITIAL_DATA_XML);
	}

	/**
	 * @verifies process value grouped obs
	 * @see ValueGroupORUR01Handler#processMessage(ca.uhn.hl7v2.model.Message)
	 */
	@Test
	public void processMessage_shouldProcessValueGroupedObs() throws Exception {
		ObsService obsService = Context.getObsService();

		// largely borrowed from another test
		String hl7string = "MSH|^~\\&|REFPACS|IU|HL7LISTENER|AMRS.ELD|20080226102656||ORU^R01|ABC101083591|P|2.5|1||||||||16^AMRS.ELD.FORMID\r"
				+ "PID|||3^^^^||John3^Doe^||\r"
				+ "PV1||O|1^Unknown Location||||1^Super User (1-8)|||||||||||||||||||||||||||||||||||||20080212|||||||V\r"
				+ "ORC|RE||||||||20080226102537|1^Super User\r"
				+ "OBR|1|||1238^MEDICAL RECORD OBSERVATIONS^99DCT\r"
				+ "OBX|1|CWE|1558^PATIENT CONTACT METHOD^99DCT||1555^PHONE^99DCT~1726^FOLLOW-UP ACTION^99DCT|||||||||20080206\r"
				+ "OBX|5|DT|5096^RETURN VISIT DATE^99DCT||20080229|||||||||20080212";

		Message hl7message = parser.parse(hl7string);
		router.processMessage(hl7message);

		Patient patient = new Patient(3);

		// check for an encounter
		List<Encounter> encForPatient3 = Context.getEncounterService().getEncountersByPatient(patient);
		Assert.assertNotNull(encForPatient3);
		Assert.assertEquals("There should be an encounter created", 1, encForPatient3.size());

		// check for any obs
		List<Obs> obsForPatient3 = obsService.getObservationsByPerson(patient);
		Assert.assertNotNull(obsForPatient3);
		Assert.assertTrue("There should be some obs created for #3", obsForPatient3.size() > 0);

		// check for civil status observation(s)
		Concept concept = Context.getConceptService().getConcept(1558);
		List<Obs> actuals = obsService.getObservationsByPersonAndConcept(patient, concept);

		Assert.assertEquals("There should be two observations of contact method", 2, actuals.size());

		Integer valueGroupId = actuals.get(0).getValueGroupId();
		Assert.assertEquals("The value group id should be the same for both observations", valueGroupId,
				actuals.get(1).getValueGroupId());

		Obs valueGroupIndex = Context.getObsService().getObs(valueGroupId);
		Assert.assertNotNull("The index observation does not really exist", valueGroupIndex);
	}
}
