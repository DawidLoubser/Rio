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
package org.rioproject.system.measurable.memory.pool;

import org.rioproject.system.measurable.memory.ProcessMemoryUtilization;
import org.rioproject.system.measurable.memory.SystemMemoryUtilization;
import org.rioproject.watch.Calculable;

/**
 * Created by IntelliJ IDEA.
 * <p/>
 * User: dreedy Date: Sep 1, 2010 Time: 11:36:40 AM
 */
public class CalculableMemoryPool extends Calculable {
    /**
     * Holds value of property containing details about memory utilization for a
     * memory pool.
     */
    private MemoryPoolUtilization memoryPoolUtilization;

    public CalculableMemoryPool(String id,
                                double value,
                                long when,
                                MemoryPoolUtilization memoryPoolUtilization) {
        super(id, value, when);
        this.memoryPoolUtilization = memoryPoolUtilization;
    }

    public MemoryPoolUtilization getMemoryUtilization() {
        return memoryPoolUtilization;
    }
}
