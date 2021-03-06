/*
 * Copyright 2008 the original author or authors.
 * Copyright 2005 Sun Microsystems, Inc.
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
package org.rioproject.monitor;

import org.rioproject.resources.servicecore.ServiceAdmin;
import java.rmi.RemoteException;

/**
 * The ProvisionMonitorAdmin extends ServiceAdmin and DeployAdmin interfaces
 *
 * @author Dennis Reedy
 */
public interface ProvisionMonitorAdmin extends ServiceAdmin, DeployAdmin {
    
    /**
     * Get the ProvisionMonitor instances that are being backed up
     * 
     * @return Array of PeerInfo objects
     *
     * @throws RemoteException if there are communication errors
     */
    ProvisionMonitor.PeerInfo[] getBackupInfo() throws RemoteException;        
}
