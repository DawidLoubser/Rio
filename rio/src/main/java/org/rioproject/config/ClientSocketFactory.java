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
package org.rioproject.config;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An {@link java.rmi.server.RMIClientSocketFactory} that uses a specific host
 * name (or address)
 */
public class ClientSocketFactory implements RMIClientSocketFactory,
                                            Serializable {
    private String host;
    private static Logger logger =
        Logger.getLogger(ClientSocketFactory.class.getName());

    public ClientSocketFactory(String host) {
        this.host = host;
    }

    public Socket createSocket(String s, int port) throws IOException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("Create Socket using host [" + host + "] " +
                        "instead of [" + s + "]");
        return new Socket(host, port);
    }
}
