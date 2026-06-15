/*
 * Copyright 1999–2025 ViaOA (vvia@viaoa.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viaoa.ui.edit;

import com.viaoa.object.OAObject;

/**
 * Exception thrown when an invalid or disallowed edit occurs on an {@link OAObject} property.
 * <p>
 * OAEditException is raised by framework or application logic that performs property-level
 * validation and detects a user-supplied value that cannot be accepted.  It carries the
 * property name and attempted value so higher-level components (such as editors, UI
 * bindings, or transaction managers) can react or roll back gracefully.
 *
 * <p><b>Key Points</b>:
 * <ul>
 *   <li>Subclasses {@link RuntimeException} for lightweight, unchecked propagation.</li>
 *   <li>Stores the target property name and the invalid {@code newValue}.</li>
 *   <li>Convenience constructors handle primitive types (long, double, boolean).</li>
 * </ul>
 *
 * Typical usage is inside an OAObject property-setter, converter, or business rule that
 * performs inline validation and needs to abort a change while preserving context.
 */
public class OAEditException extends RuntimeException {
    static final long serialVersionUID = 1L;

    /**
     * Name of the OAObject property for which the invalid edit was attempted.
     */
    private String property;

    /**
     * The invalid value supplied during the edit attempt that triggered this
     * exception.
     */
    private Object newValue;

    /**
     * Creates a new exception indicating that an invalid value was supplied
     * for the specified property. The message is generated using the
     * property name. The invalid value is stored for later retrieval.
     *
     * @param obj       the object whose property was being edited
     * @param property  the name of the property being set
     * @param newValue  the invalid value that triggered the exception
     */
    public OAEditException(OAObject obj, String property, Object newValue) {
        super("Invalid entry for "+property);
//qqqqqqq obj is not used ??        
        this.property = property;
        this.newValue = newValue;
    }

    /**
     * Convenience constructor for invalid long values. Wraps the primitive
     * value in a {@link Long} and delegates to the main constructor.
     *
     * @param obj       the object whose property was being edited
     * @param property  the name of the property being set
     * @param newValue  the invalid long value
     */
    public OAEditException(OAObject obj, String property, long newValue) {
        this(obj, property, Long.valueOf(newValue));
    }

    /**
     * Convenience constructor for invalid double values. Wraps the primitive
     * value in a {@link Double} and delegates to the main constructor.
     *
     * @param obj       the object whose property was being edited
     * @param property  the name of the property being set
     * @param newValue  the invalid double value
     */
    public OAEditException(OAObject obj, String property, double newValue) {
        this(obj, property, Double.valueOf(newValue));
    }
    
    /**
     * Convenience constructor for invalid boolean values. Wraps the primitive
     * value in a {@link Boolean} and delegates to the main constructor.
     *
     * @param obj       the object whose property was being edited
     * @param property  the name of the property being set
     * @param newValue  the invalid boolean value
     */
    public OAEditException(OAObject obj, String property, boolean newValue) {
        this(obj, property, Boolean.valueOf(newValue));
    }
    
    /**
     * Returns the invalid value that triggered this exception.
     *
     * @return the rejected value supplied during the edit
     */
    public Object getNewValue() {
        return newValue;
    }
    
    /**
     * Returns the name of the property for which the invalid edit occurred.
     *
     * @return the property name associated with this exception
     */
    public String getProperty() {
        return property;
    }
}

