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
package org.rioproject.watch;

/**
 * Contains information about a Font used by the AccumulatorViewer
 */
public class FontDescriptor implements java.io.Serializable {
    static final long serialVersionUID = 1L;
    /** Holds value of property style. */
    private int style;
    /** Holds value of property size. */
    private int size;
    /** Holds value of property name. */
    private String name;

    /**
     * Creates new FontDescriptor
     *
     * @param name The font name
     * @param style The font style
     * @param size The font size
     */
    public FontDescriptor(String name, int style, int size) {
        setName(name);
        setStyle(style);
        setSize(size);
    }

    /** 
     * Getter for property style.
     *
     * @return Value of property style.
     */
    public int getStyle() {
        return(style);
    }

    /** 
     * Setter for property style.
     *
     * @param style New value of property style.
     */
    public void setStyle(int style) {
        this.style = style;
    }

    /** 
     * Getter for property size.
     *
     * @return Value of property size.
     */
    public int getSize() {
        return(size);
    }

    /** 
     * Setter for property size.
     *
     * @param size New value of property size.
     */
    public void setSize(int size) {
        this.size = size;
    }

    /** 
     * Getter for property name.
     *
     * @return Value of property name.
     */
    public String getName() {
        return(name);
    }

    /** 
     * Setter for property name.
     *
     * @param name New value of property name.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        return(name + ":" + style + ":" + size);
    }
}
