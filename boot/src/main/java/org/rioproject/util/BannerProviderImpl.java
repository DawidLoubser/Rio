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
package org.rioproject.util;

import org.rioproject.RioVersion;

/**
 * Produces a ascii banner
 */
public class BannerProviderImpl implements BannerProvider {

    public String getBanner(String service) {
        StringBuffer banner = new StringBuffer();
        banner.append("\n");
        banner.append("____ _ ____\n");
        banner.append("|__/ | |  |   "+service+"\n");
        banner.append("|  \\ | |__|   Version: "+ RioVersion.VERSION+", Build: "+RioVersion.getBuildNumber()+"\n");
        banner.append("\n");
        banner.append("Rio Home: "+System.getProperty("RIO_HOME"));
        return banner.toString();
    }
}