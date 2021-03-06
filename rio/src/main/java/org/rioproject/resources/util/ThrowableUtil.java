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
package org.rioproject.resources.util;

import com.sun.jini.constants.ThrowableConstants;
import org.rioproject.core.JSBInstantiationException;

/**
 * Utility for getting things from a Throwable
 *
 * @author Dennis Reedy
 */
public class ThrowableUtil {
    public static Throwable getRootCause(Throwable e) {
        if(e instanceof JSBInstantiationException) {
            if(((JSBInstantiationException)e).getCauseExceptionDescriptor()!=null) {
                JSBInstantiationException.ExceptionDescriptor exDesc =
                    ((JSBInstantiationException)e).getCauseExceptionDescriptor();
                Throwable t = new Throwable(exDesc.getMessage());
                t.setStackTrace(exDesc.getStacktrace());
                return t;
            }
        }
        Throwable cause = e;
        Throwable t = cause;
        while(t != null) {
            t = cause.getCause();
            if(t != null)
                cause = t;
        }
        return (cause);
    }

    public static boolean isRetryable(Throwable t) {
        boolean retryable = true;
        final int category = ThrowableConstants.retryable(t);
        Throwable cause = getRootCause(t);
        if (category == ThrowableConstants.BAD_INVOCATION ||
            category == ThrowableConstants.BAD_OBJECT ||
            cause instanceof java.net.ConnectException) {
            retryable = false;
        }
        return retryable;
    }
}
