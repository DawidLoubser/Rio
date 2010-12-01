/*
 * Copyright to the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rioproject.examples.hospital;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rioproject.test.RioTestRunner;
import org.rioproject.test.SetTestManager;
import org.rioproject.test.TestManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Testing the hospital example using the Rio test framework
 */
@RunWith (RioTestRunner.class)
public class ITHospitalDeployTest {
    @SetTestManager
    static TestManager testManager;
    Hospital hospital;
    List<Doctor> docs;

    @Before
    public void setup() throws Exception {
	    Assert.assertNotNull(testManager);
        testManager.waitForDeployment(testManager.getOperationalStringManager());
        hospital = (Hospital)testManager.waitForService(Hospital.class);
        Assert.assertNotNull(hospital);
        /* Wait for rules to load */
        Thread.sleep(10*1000);
        List<Bed> beds = hospital.getBeds();
        Assert.assertEquals("Should have 10 Beds", 10, beds.size());
        docs = hospital.getDoctors();
        Assert.assertEquals("Should have 4 Doctors", 4, docs.size());
        int onDuty = getNumDoctorsForStatus(docs, Doctor.Status.ON_DUTY);
        Assert.assertTrue("1 Doctor should be ON_DUTY, have "+onDuty, onDuty==1);
        int onCall = getNumDoctorsForStatus(docs, Doctor.Status.ON_CALL);
        Assert.assertTrue("2 Doctor should be ON_CALL, have "+onCall, onCall==2);
        int offDuty = getNumDoctorsForStatus(docs, Doctor.Status.OFF_DUTY);
        Assert.assertTrue("1 Doctor should be OFF_DUTY, have "+offDuty, offDuty==1);
    }

    @After
    public void release() {
        Assert.assertNotNull("Should not have a null Hospital", hospital);
        try {
            for(Patient patient : hospital.getAdmittedPatients()) {
                hospital.release(patient);
            }
            int numAdmitted = hospital.getAdmittedPatients().size();
            Assert.assertTrue("Should have no admitted Patients, have "+numAdmitted, numAdmitted==0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPatientAlerts() {
        Throwable thrown = null;
        List<Patient> presidents = first8Presidents();
        Patient patient = null;
        try {
            patient = hospital.admit(presidents.get(0));
        } catch (Exception e) {
            thrown = e;
            e.printStackTrace();
        }
        Assert.assertNull("Should not have thrown an exception", thrown);
        Assert.assertNotNull("Should not have a null Patient", patient);
        long t0 = System.currentTimeMillis();
        boolean atRisk = false;
        try {
            atRisk = waitForPatientStatus(patient, Patient.Status.AT_RISK);
        } catch (Exception e) {
            thrown = e;
            e.printStackTrace();
        }
        Assert.assertNull("Should not have thrown an exception", thrown);
        long t1 = System.currentTimeMillis();
        System.out.println("===> Waited ("+(double)(t1-t0)+") millis for Patient to be AT_RISK: "+atRisk);
        Assert.assertTrue("Patient should be AT_RISK", atRisk);

        boolean stable = false;
        t0 = System.currentTimeMillis();
        try {
            stable = waitForPatientStatus(patient, Patient.Status.STABLE);
        } catch (Exception e) {
            thrown = e;
            e.printStackTrace();
        }
        Assert.assertNull("Should not have thrown an exception", thrown);
        t1 = System.currentTimeMillis();
        System.out.println("===> Waited ("+(double)(t1-t0)+") millis for Patient to be STABLE: "+stable);
        /* Assert.assertTrue("Patient should be STABLE", stable); */
    }

    @Test
    public void testDeployment() {
        Throwable thrown = null;
        try {
            for(Patient p : first8Presidents()) {
                hospital.admit(p);
            }
            Thread.sleep(2000);
            int numAssigned = getAssignedDocs();
            Assert.assertTrue("Should have 2 Doctors assigned, have "+numAssigned, numAssigned==2);

            List<Patient> patients = next8Presidents();
            int onDuty = getNumDoctorsForStatus(docs, Doctor.Status.ON_DUTY);
            Assert.assertTrue("3 Doctors should be ON_DUTY, have "+onDuty, onDuty==3);

            try {
                hospital.admit(patients.remove(0));
            } catch(Exception e) {
                System.out.println(e.getClass().getName()+": "+e.getLocalizedMessage());
            }
            try {
                hospital.admit(patients.remove(0));
            } catch(Exception e) {
                System.out.println(e.getClass().getName()+": "+e.getLocalizedMessage());
            }
            int found = waitForBeds(11);
            Assert.assertTrue("Should have at least 11 Beds, have "+found, 11<=found);
            hospital.admit(patients.remove(0));
            found = waitForBeds(12);
            Assert.assertTrue("Should have at least 12 Beds, have "+found, 12<=found);
            int waitingRoomSize=0;
            for(Patient p : patients) {
                try {
                    hospital.admit(p);
                } catch(AdmissionException e) {
                    int wSize = hospital.getWaitingRoom().size();
                    System.out.println("AdmissionException: "+e.getLocalizedMessage()+" for "+p.getPatientInfo().getName()+", waiting room size="+wSize);
                    waitingRoomSize++;
                    Assert.assertTrue("Should have "+waitingRoomSize+" Patients in Waiting Room, " +
                                      "instead we have "+wSize,
                                      waitingRoomSize==wSize);
                }
            }
            found = waitForBeds(16);
            Assert.assertTrue("Should have at least 16 Beds, have "+found, 16 <= found);

            onDuty = getNumDoctorsForStatus(docs, Doctor.Status.ON_DUTY);
            Assert.assertTrue("3 Doctors should be ON_DUTY, have "+onDuty, onDuty==3);
            int onCall = getNumDoctorsForStatus(docs, Doctor.Status.ON_CALL);
            Assert.assertTrue("0 Doctors should be ON_CALL, have "+onCall, onCall==0);
            int offDuty = getNumDoctorsForStatus(docs, Doctor.Status.OFF_DUTY);
            Assert.assertTrue("1 Doctor should be OFF_DUTY, have "+offDuty, offDuty==1);

            Doctor d = getOffDutyDoctor(docs);
            Assert.assertNotNull(d);
            d.onDuty();
            onDuty = getNumDoctorsForStatus(docs, Doctor.Status.ON_DUTY);
            Assert.assertTrue("4 Doctors should be ON_DUTY, have "+onDuty, onDuty==4);

        } catch (Exception e) {
            thrown = e;
            e.printStackTrace();
        }
        Assert.assertNull("Should not have thrown an exception", thrown);
    }

    private int getAssignedDocs() throws IOException {
        int numAssigned = 0;
        for(Doctor d : hospital.getDoctors()) {
            System.out.println("===> "+d.getName()+", "+d.getStatus()+", num patients: "+d.getPatients().size());
            if(d.getPatients().size()>0) {
                numAssigned++;
            }
        }
        return numAssigned;
    }

    private int waitForBeds(int num) throws Exception {
        int found = 0;
        int iterations = 20;
        long t0 = System.currentTimeMillis();
        for(int i=0; i<iterations; i++) {
            List <Bed> beds = hospital.getBeds();
            found = beds.size();
            if(found<num) {
                Thread.sleep(500);
            } else {
                break;
            }
        }
        long t1 = System.currentTimeMillis();
        System.out.println("Waited ("+(double)(t1-t0)+") millis for "+num+" Beds, found "+found);
        return found;
    }

    private int getNumDoctorsForStatus(List<Doctor> docs, Doctor.Status status) throws IOException {
        int count = 0;
        for(Doctor d : docs) {
            if(d.getStatus().equals(status)) {
                count++;
            }
        }
        return count;
    }

    private Doctor getOffDutyDoctor(List<Doctor> docs) throws IOException {
        Doctor doc = null;
        for(Doctor d : docs) {
            if(d.getStatus().equals(Doctor.Status.OFF_DUTY)) {
                doc = d;
                break;
            }
        }
        return doc;
    }

    private List<Patient> first8Presidents() {
        List<Patient> patients = new ArrayList<Patient>();
        patients.add(create("George Washington", new GregorianCalendar(1732, 1, 22)));
        patients.add(create("John Adams", new GregorianCalendar(1735, 9, 30)));
        patients.add(create("Thomas Jefferson", new GregorianCalendar(1743, 3, 13)));
        patients.add(create("James Madison", new GregorianCalendar(1751, 2, 16)));
        patients.add(create("James Monroe", new GregorianCalendar(1758, 3, 28)));
        patients.add(create("John Quincy Adams", new GregorianCalendar(1767, 6, 11)));
        patients.add(create("Andrew Jackson", new GregorianCalendar(1767, 2, 15)));
        patients.add(create("Martin Van Buren", new GregorianCalendar(1782, 11, 5)));
        return patients;
    }

    private List<Patient> next8Presidents() {
        List<Patient> patients = new ArrayList<Patient>();
        patients.add(create("William Henry Harrison", new GregorianCalendar(1773, 1, 9)));
        patients.add(create("John Tyler", new GregorianCalendar(1790, 2, 29)));
        patients.add(create("James Knox Polk", new GregorianCalendar(1795, 10, 2)));
        patients.add(create("Zachary Taylor", new GregorianCalendar(1784, 10, 24)));
        patients.add(create("Millard Fillmore", new GregorianCalendar(1800, 0, 7)));
        patients.add(create("Franklin Pierce", new GregorianCalendar(1804, 10, 23)));
        patients.add(create("James Buchanan", new GregorianCalendar(1791, 3, 23)));
        patients.add(create("Abraham Lincoln", new GregorianCalendar(1809, 1, 12)));
        return patients;
    }

    private Patient create(String name, Calendar cal) {
        return new Patient(new Patient.PatientInfo(name, "Male", cal.getTime()));
    }

    private boolean waitForPatientStatus(Patient patient, Patient.Status status) throws IOException {
        int iterations = 120;
        boolean hasStatus = false;
        for(int i=0; i<iterations; i++) {
            for(Patient p : patient.getDoctor().getPatients()) {
                if(patient.equals(p)) {
                    if(p.getStatus().equals(status)) {
                        hasStatus = true;
                        break;
                    }
                }
            }

            if(!hasStatus) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        return hasStatus;
    }
}