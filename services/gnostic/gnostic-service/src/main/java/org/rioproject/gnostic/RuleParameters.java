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
package org.rioproject.gnostic;

import org.rioproject.core.OperationalStringManager;
import org.rioproject.core.ServiceBeanInstance;
import org.rioproject.core.ServiceElement;
import org.rioproject.sla.SLA;

/**
 * Data structure for use when using template for built-in rules.
 */
public class RuleParameters {
    private SLA sla;
    private String watchID;
    private ServiceElement serviceElement;

    public RuleParameters(SLA sla,
                          String watchID,
                          ServiceElement serviceElement) {
        this.sla = sla;
        this.watchID = watchID;
        this.serviceElement = serviceElement;
    }

    public String getWatchID() {
        return watchID;
    }

    public Double getLowThreshold() {
        return sla.getCurrentLowThreshold();
    }

    public Double getHighThreshold() {
        return sla.getCurrentHighThreshold();
    }

    public Integer getMaxServices() {
        return sla.getMaxServices();
    }

    public Integer getMinServices() {
        return serviceElement.getPlanned();
    }

    public String getServiceName() {
        return serviceElement.getName();
    }

    public String getOpStringName() {
        return serviceElement.getOperationalStringName();
    }
}
