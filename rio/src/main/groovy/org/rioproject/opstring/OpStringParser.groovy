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
package org.rioproject.opstring

import org.rioproject.opstring.OpString
import org.rioproject.opstring.GlobalAttrs
import org.rioproject.opstring.ParsedService

/**
 * Defines the semantics for an OperationalString parser
 *
 * @author Jerome Bernard
 */
interface OpStringParser {
    def List<OpString> parse(source,
                             ClassLoader loader,
                             boolean verify,
                             String[] defaultExportJars,
                             String[] defaultGroups,
                             loadPath)

    def parseElement(element,
                     GlobalAttrs global,
                     ParsedService sDescriptor,
                     OpString opString) throws Exception
}
