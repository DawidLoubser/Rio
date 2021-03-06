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
package org.rioproject.examples.hospital.service;

import org.rioproject.associations.Association;
import org.rioproject.associations.AssociationServiceListener;
import org.rioproject.bean.Started;
import org.rioproject.core.jsb.ServiceBeanContext;
import org.rioproject.examples.hospital.*;
import org.rioproject.examples.hospital.Doctor.Status;
import org.rioproject.watch.CounterWatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a {@link Hospital}
 */
public class HospitalImpl implements Hospital {
    private Iterable<Doctor> doctors;
    private Iterable<Bed> beds;
    private CounterWatch availableBeds;
    private ServiceBeanContext context;
    private final List<Patient> waitingRoom = new ArrayList<Patient>();
    private final static Logger logger = Logger.getLogger(HospitalImpl.class.getName());

    public void setServiceBeanContext(ServiceBeanContext context) {
        this.context = context;
        availableBeds = new CounterWatch("availableBeds");
        context.getWatchRegistry().register(availableBeds);
    }

    @Started
    public void started() {
        Association<Bed> association = context.getAssociationManagement().getAssociation(Bed.class,
                                                                                         "Beds",
                                                                                         "Hospital");
        association.registerAssociationServiceListener(new AssociationServiceListener<Bed>() {

            public void serviceAdded(Bed b) {
                availableBeds.increment();
                synchronized(waitingRoom) {
                    if(waitingRoom.size()>0) {
                        Patient p = waitingRoom.get(0);
                        try {
                            doAdmitWithBed(p, b);
                            waitingRoom.remove(p);
                        } catch (AdmissionException e) {
                            if(logger.isLoggable(Level.FINE)) {
                                logger.fine("Unable to assign patient "+p.getPatientInfo().getName());
                            }
                        }
                    }
                }
            }

            public void serviceRemoved(Bed b) {
                availableBeds.decrement();
            }
        });
    }

    public Patient admit(Patient p) throws AdmissionException {
        Bed bed = getEmptyBed();
        return doAdmitWithBed(p, bed);
    }

    public Patient release(Patient p) throws AdmissionException {
        Patient released = null;
        try {
            p = p.getBed().removePatient();
            if(p!=null) {
                availableBeds.increment();
                p.getDoctor().removePatient(p);
                released = p;
            }
        } catch (IOException e) {
            throw new AdmissionException("Patient "+p.getPatientInfo().getName()+" could not be released", e);
        }
        return released;
    }

    private Patient doAdmitWithBed(Patient p, Bed bed) throws AdmissionException {
        if(bed==null) {
            addToWaitingRoom(p);
            throw new AdmissionException("No available beds");
        }
        Doctor d = getAvailableDoctor();
        if(d==null) {
            addToWaitingRoom(p);
            throw new AdmissionException("No available Doctor");
        }
        p.setDoctor(d);
        try {
            d.assignPatient(p);
        } catch(IOException e) {
            p.setDoctor(null);
            throw new AdmissionException("Could not get the patient a doctor",
                                         e);
        }
        try {
            p.setBed(bed);
            bed.setPatient(p);
        } catch(IOException e) {
            p.setDoctor(null);
            throw new AdmissionException("Could not get the patient into a bed",
                                         e);
        }
        availableBeds.decrement();
        return p;
    }

    private Doctor getAvailableDoctor() {
        Doctor d = null;
        List<Doctor> onDuty = get(Status.ON_DUTY, false);
        if(onDuty.size()>0) {
            if(onDuty.size()>1)
                Collections.sort(onDuty, new DoctorComparator());
            d = onDuty.get(0);
        }
        return d;
    }

    private Bed getEmptyBed() {
        Bed bed = null;
        for(Bed b : beds) {
            try {
                if(b.getPatient()==null) {
                    bed = b;
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return bed;
    }

    public List<Bed> getBeds() {
        List<Bed> bs = new ArrayList<Bed>();
        for(Bed b : beds)
            bs.add(b);
        return Collections.unmodifiableList(bs);
    }

    public void setDoctors(Iterable<Doctor> drs) {
        doctors = drs;
    }

    public void setBeds(Iterable<Bed> bs) {
        beds = bs;
    }

    public List<Doctor> getDoctors() {
        return get(null, true);
    }

    public List<Doctor> getDoctorsOnCall() {
        return get(Status.ON_CALL, true);
    }

    public List<Doctor> getDoctorsOnDuty() {
        return get(Status.ON_DUTY, true);
    }

    public List<Patient> getWaitingRoom() {
        return getWaitingRoomList();
    }

    private void addToWaitingRoom(Patient p) {
        synchronized(waitingRoom) {
            waitingRoom.add(p);
        }
    }

    public List<Patient> getAdmittedPatients() {
        List<Patient> l = new ArrayList<Patient>();
        for(Bed b : getBeds()) {
            try {
                Patient p = b.getPatient();
                if(p!=null)
                    l.add(p);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not get Patient from Bed", e);
            }
        }
        return Collections.unmodifiableList(l);
    }

    private List<Patient> getWaitingRoomList() {
        List<Patient> l = new ArrayList<Patient>();
        synchronized(waitingRoom) {
            l.addAll(waitingRoom);
        }
        return Collections.unmodifiableList(l);
    }

    private List<Doctor> get(Status status, boolean immutable) {
        List<Doctor> list = new ArrayList<Doctor>();
        for(Doctor d : doctors) {
            if(status==null) {
                list.add(d);
            } else {
                try {
                    if(d.getStatus().equals(status)) {
                        list.add(d);
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not get Doctor's Status", e);
                }
            }
        }
        return immutable?Collections.unmodifiableList(list):list;
    }

    class DoctorComparator implements Comparator<Doctor> {

        public int compare(Doctor dr1, Doctor dr2) {
            int dr1Patients = getPatientCount(dr1);
            int dr2Patients = getPatientCount(dr2);
            if(dr1Patients==dr2Patients)
                return 0;
            return dr1Patients<dr2Patients?-1:1;
        }
        
        int getPatientCount(Doctor d) {
            int count = 0;
            try {
                List<Patient> l = d.getPatients();
                count = l.size();
            } catch (IOException e) {
            }
            return count;
        }
    }
}