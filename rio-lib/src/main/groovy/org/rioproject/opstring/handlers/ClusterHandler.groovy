/*
 * Copyright 2008 the original author or authors.
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
package org.rioproject.opstring.handlers

/**
 * Handles the parsing for Cluster elements
 *
 * @author Jerome Bernard
 */
class ClusterHandler implements Handler {
    public parse(element, options) {
        def sDescriptor = options.serviceDescriptor
        def global = options.global
        def parent = options.parent

        /* Ensure that we dont process the Element if the Element's parent
         * is a ServiceBean and the ParsedService is null. This will happen if
         * the Element is declared as a child of the OperationalString
         * (global configuration) and overridden in the child Element */
        if(!((parent.name() == "ServiceBean" || parent.name() == "SpringBean") && sDescriptor == null)) {
            String includeGlobalDecl = element.'@IncludeGlobalDecl'
            boolean append = includeGlobalDecl == "yes"
            def cluster = element.Machine.collect { it.text() } as String[]
            if (sDescriptor)
                sDescriptor.setCluster(cluster, append)
            else
                global.setCluster(cluster, append)
        }
    }
}
