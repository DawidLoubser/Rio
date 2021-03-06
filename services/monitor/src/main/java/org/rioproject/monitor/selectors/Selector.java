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
package org.rioproject.monitor.selectors;

import org.rioproject.monitor.InstantiatorResource;
import org.rioproject.monitor.ProvisionException;
import org.rioproject.monitor.ProvisionRequest;
import org.rioproject.resources.servicecore.ServiceResource;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Select a {@link org.rioproject.resources.servicecore.ServiceResource} based on a {@link org.rioproject.core.ServiceElement}
 */
public class Selector {
    private static final Logger logger = Logger.getLogger("org.rioproject.monitor.selector");

    /**
     * Get a ServiceBeanInstantiator that meets the operational requirements of a
     * ServiceElement
     *
     * @param request  The ProvisionRequest
     * @param selector A selector
     * @return A ServiceResource that contains an InstantiatorResource that meets the operational criteria of the
     * ServiceElement
     */
    public static ServiceResource acquireServiceResource(ProvisionRequest request, ServiceResourceSelector selector) {
        ServiceResource resource = null;
        try {
            if (request.getRequestedUuid() != null) {
                resource = selector.getServiceResource(request.getServiceElement(),
                                                       request.getRequestedUuid(),
                                                       true);
                /* If the returned resource is null, then try to get
                 * any resource */
                if (resource == null) {
                    resource = selector.getServiceResource(request.getServiceElement());
                }

            } else if (request.getExcludeUuid() != null) {
                resource = selector.getServiceResource(request.getServiceElement(),
                                                       request.getExcludeUuid(),
                                                       false);
            } else {
                resource = selector.getServiceResource(request.getServiceElement());
            }

            if (resource != null) {
                InstantiatorResource ir = (InstantiatorResource) resource.getResource();
                ir.incrementProvisionCounter(request.getServiceElement());
            }
        } catch (ProvisionException e) {
            if (e.isUninstantiable()) {
                request.setType(ProvisionRequest.Type.UNINSTANTIABLE);
                request.getListener().uninstantiable(request);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Getting ServiceResource", e);
        }
        return resource;
    }

}
