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
package org.rioproject.monitor.tasks;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import org.rioproject.deploy.DeployAdmin;
import org.rioproject.monitor.OpStringMangerController;
import org.rioproject.monitor.ProvisionMonitor;
import org.rioproject.monitor.peer.ProvisionMonitorPeer;
import org.rioproject.monitor.persistence.StateManager;

import java.io.File;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scheduled Task which will load configured OperationalString files
 */
public class InitialOpStringLoadTask extends TimerTask {
    private Configuration config;
    private ProvisionMonitorPeer provisionMonitorPeer;
    private StateManager stateManager;
    private DeployAdmin deployAdmin;
    private OpStringMangerController opStringMangerController;
    static Logger logger = Logger.getLogger(InitialOpStringLoadTask.class.getName());
    static final String CONFIG_COMPONENT = "org.rioproject.monitor";

    public InitialOpStringLoadTask(Configuration config,
                                   DeployAdmin deployAdmin,
                                   ProvisionMonitorPeer provisionMonitorPeer,
                                   OpStringMangerController opStringMangerController,
                                   StateManager stateManager) {
        this.config = config;
        this.provisionMonitorPeer = provisionMonitorPeer;
        this.deployAdmin = deployAdmin;
        this.opStringMangerController = opStringMangerController;
        this.stateManager = stateManager;
    }

    /**
     * The action to be performed by this timer task.
     */
    public void run() {
        loadInitialOpStrings(config);
    }

    void loadInitialOpStrings(Configuration config) {
        if (stateManager!=null && !stateManager.inRecovery()) {
            String[] initialOpStrings = new String[]{};
            try {
                initialOpStrings =
                    (String[]) config.getEntry(CONFIG_COMPONENT,
                                               "initialOpStrings",
                                               String[].class,
                                               initialOpStrings);
                if (logger.isLoggable(Level.FINE)) {
                    if (initialOpStrings.length > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < initialOpStrings.length; i++) {
                            if (i < 0)
                                sb.append(", ");
                            sb.append(initialOpStrings[i]);
                        }
                        logger.fine("Loading initialOpStrings " +
                                    "[" + sb.toString() + "]");
                    } else {
                        logger.fine("No initialOpStrings to load");
                    }
                }
            } catch (ConfigurationException e) {
                logger.log(Level.WARNING, "Getting initialOpStrings", e);
            }
            try {
                /*
                * Wait for all peers to be in a "ready" state. Once this
                * method returns begin our own initialOpStrings and OARs
                * deployment, setting the PeerInfo state to
                * LOADING_INITIAL_DEPLOYMENTS.
                */
                waitForPeers();
                ProvisionMonitor.PeerInfo peerInfo = provisionMonitorPeer.doGetPeerInfo();
                peerInfo.setInitialDeploymentLoadState(ProvisionMonitor.PeerInfo.LOADING_INITIAL_DEPLOYMENTS);

                /* Load initialOpStrings */
                for (String initialOpString : initialOpStrings) {
                    URL opstringURL;
                    try {
                        if (initialOpString.startsWith("http:"))
                            opstringURL = new URL(initialOpString);
                        else
                            opstringURL =
                                new File(initialOpString).toURI().toURL();
                        Map errorMap = deployAdmin.deploy(opstringURL, null);
                        opStringMangerController.dumpOpStringError(errorMap);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING,
                                   "Loading OperationalString : " +
                                   initialOpString,
                                   t);
                    }
                }

            } finally {
                ProvisionMonitor.PeerInfo peerInfo = provisionMonitorPeer.doGetPeerInfo();
                peerInfo.setInitialDeploymentLoadState(ProvisionMonitor.PeerInfo.LOADED_INITIAL_DEPLOYMENTS);
            }
        }
    }

    /**
     * Wait until peers are ready.
     * <p/>
     * if a peer's initialDeploymentState is LOADING_INITIAL_DEPLOYMENTS,
     * wait until the LOADED_INITIAL_DEPLOYMENTS state is set. If a peer
     * has not loaded (INITIAL_DEPLOYMENTS_PENDING) this is fine as well.
     * Once we find that all peers are ready, return
     */
    void waitForPeers() {
        ProvisionMonitor.PeerInfo[] peers = provisionMonitorPeer.getBackupInfo();
        long t0 = System.currentTimeMillis();
        if (logger.isLoggable(Level.FINE))
            logger.fine("Number of peers to wait on [" + peers.length + "]");
        if (peers.length == 0)
            return;
        boolean peersReady = false;
        while (!peersReady) {
            int numPeersReady = 0;
            StringBuffer b = new StringBuffer();
            b.append("ProvisionMonitor Peer verification\n");
            for (int i = 0; i < peers.length; i++) {
                if (i > 0)
                    b.append("\n");
                try {
                    ProvisionMonitor peer = peers[i].getService();
                    ProvisionMonitor.PeerInfo peerInfo = peer.getPeerInfo();
                    int state = peerInfo.getInitialDeploymentLoadState();
                    b.append("Peer at ");
                    b.append(peerInfo.getAddress());
                    b.append(", " + "state=");
                    b.append(getStateName(state));
                    switch (state) {
                        case ProvisionMonitor.PeerInfo.INITIAL_DEPLOYMENTS_PENDING:
                            numPeersReady++;
                            break;
                        case ProvisionMonitor.PeerInfo.LOADED_INITIAL_DEPLOYMENTS:
                            numPeersReady++;
                            break;
                        case ProvisionMonitor.PeerInfo.LOADING_INITIAL_DEPLOYMENTS:
                            break;
                    }
                } catch (RemoteException e) {
                    b.append("Peer [" + 0 + "] exception " + "[");
                    b.append(e.getMessage());
                    b.append("], continue");
                    if (logger.isLoggable(Level.FINEST))
                        logger.log(Level.FINEST, "Getting PeerInfo", e);
                    numPeersReady++;
                }

            }
            if (logger.isLoggable(Level.FINE))
                logger.fine(b.toString());
            b.delete(0, b.length());
            if (numPeersReady == peers.length) {
                peersReady = true;
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        long t1 = System.currentTimeMillis();
        if (logger.isLoggable(Level.FINER))
            logger.finer("Peer state resolution took " + (t1 - t0) + " millis");
    }

    private String getStateName(int state) {
        String name;
        switch (state) {
            case ProvisionMonitor.PeerInfo.INITIAL_DEPLOYMENTS_PENDING:
                name = "INITIAL_DEPLOYMENTS_PENDING";
                break;
            case ProvisionMonitor.PeerInfo.LOADED_INITIAL_DEPLOYMENTS:
                name = "LOADED_INITIAL_DEPLOYMENTS";
                break;
            default:
                name = "LOADING_INITIAL_DEPLOYMENTS";
        }
        return (name);

    }
}
