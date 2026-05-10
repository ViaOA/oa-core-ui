/*
 * Copyright 1999–2025 ViaOA (info@viaoa.com)
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
package com.viaoa.ui.controller;

import com.viaoa.callback.OAObjectCallback;
import com.viaoa.converter.OAConv;
import com.viaoa.graph.OAGraphInternal;
import com.viaoa.graph.service.object.OAObjectCallbackService;
import com.viaoa.hub.Hub;
import com.viaoa.lang.OAStr;
import com.viaoa.lang.OAString;
import com.viaoa.metadata.OAObjectInfo;
import com.viaoa.metadata.OAPropertyInfo;
import com.viaoa.object.OAObject;
import com.viaoa.runtime.OARuntime;
import com.viaoa.secure.OAEncryption;


/**
 *  *************** NOTE ***********************
 *  This is replaced by OAUIController
 *  *************** NOTE ***********************
 */

/**
 * Legacy controller for binding a single OAObject property to a UI
 * component. This class has been superseded by {@link OAUIController},
 * which provides a more general propertyPath-based controller model.
 *
 * <p>
 * OAUIPropertyController manages:
 * </p>
 *
 * <ul>
 *   <li>A single property name on the active object in a {@link Hub}.</li>
 *   <li>Formatting and type conversion of the property value.</li>
 *   <li>Optional masking/encryption handling for password fields.</li>
 *   <li>Enabled/visible state via {@link OAObjectCallbackDelegate}.</li>
 * </ul>
 *
 * <p>
 * New code should generally prefer {@link OAUIController} with a property
 * path over using this class directly. It remains for backward compatibility
 * with existing UI components.
 * </p>
 * 
 * @deprecated replaced by OAUIController
 */
public class OAUIPropertyController extends OAUIBaseController {

    private final String propertyName;
    private String format;
    private char conversion;
    
    // This is used to handle password/encrypted data
    private final static String maskPasswordValue = "*****";

    
    public OAUIPropertyController(Hub hub, String propertyName) {
        super(hub);
        this.propertyName = propertyName;
    }
    
    public String getPropertyName() {
        return this.propertyName;
    }
    
    /**
     * 'U'ppercase, 'L'owercase, 'T'itle, 'J'ava identifier 'E'ncrpted password/encrypt 'S'HA password (one way hash)
     */
    public void setConversion(char conv) {
        conversion = conv;
    }

    public char getConversion() {
        return conversion;
    }
    
    
    public String getFormat() {
        return format;
    }
    public void setFormat(String format) {
        this.format = format;
    }
    
    public boolean isRequired() {
        if (hub == null) return false;
        OAPropertyInfo pi = hub.getOAObjectInfo().getPropertyInfo(getPropertyName()); 
        return (pi != null && pi.getRequired());
    }
    
    @Override
    public boolean isEnabled() {
        return isEnabled((OAObject) hub.getAO());
    }
    public boolean isEnabled(OAObject obj) {
        if (!super.isEnabled()) return false;
        if (obj == null) return false;
        
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(obj);
        OAObjectCallback eq = og.objectsInternal().callObjectCallbackGetAllowEnabledObjectCallback(OAObjectCallback.CHECK_ALL, getHub(), obj, getPropertyName());
        return eq.getAllowed();
    }
        
    @Override
    public boolean isVisible() {
        return isVisible((OAObject) hub.getAO());
    }
    public boolean isVisible(OAObject obj) {
        if (!super.isVisible()) return false;
        
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(getHub(), obj);
        OAObjectCallback eq = og.objectsInternal().callObjectCallbackGetAllowVisibleObjectCallback(getHub(), obj, getPropertyName());
        return eq.getAllowed();
    }
    
    public boolean onSetProperty(Object value) {
        final OAObject obj = (OAObject) hub.getAO();
        return onSetProperty(obj, value);
    }

    public boolean onSetProperty(OAObject obj, Object value) {
        if (_onSetProperty(obj, value)) {
            String msg = getCompletedMessage();
            if (OAStr.isNotEmpty(msg)) {
                onCompleted(msg, getTitle()); 
            }
        }
        return true;
    }
    
    /**
     * This can be used to get the confirm message before the actual new value is known.<br>
     * This is used to send a confirm message to browser.
    public OAObjectCallback getPreConfirmMessage() {
        final OAObject obj = (OAObject) hub.getAO();

        OAObjectCallback cb = OAObjectCallbackDelegate.getPreConfirmPropertyChangeObjectCallback(obj, getPropertyName(), getConfirmMessage(), getTitle());
        return cb;
    }
    */

    public static String getMaskPasswordValue() {
        return maskPasswordValue;
    }
    
    private boolean _onSetProperty(final OAObject obj, Object newValue) {
        OAObjectCallback cb; 
        String s;

        // 0: conversion
        if (newValue instanceof String && (getConversion() != 0) && ((String) newValue).length() > 0) {
            String text = (String) newValue;
            if (conversion == 'U' || conversion == 'u') {
                text = text.toUpperCase();
            } else if (conversion == 'L' || conversion == 'l') {
                text = text.toLowerCase();
            } else if (conversion == 'T' || conversion == 't') {
                if (text.toLowerCase().equals(text) || text.toUpperCase().equals(text)) {
                    text = OAString.toTitleCase(text);
                }
            } else if (conversion == 'J' || conversion == 'j') {
                text = OAString.makeJavaIdentifier(text);
            } else if (conversion == 'S' || conversion == 's') {
                if (maskPasswordValue.equals(text)) return true;
                text = OAString.getSHAHash(text);
            } else if (conversion == 'P' || conversion == 'p') {
                if (maskPasswordValue.equals(text)) return true;
                text = OAString.getSHAHash(text);
            } else if (conversion == 'E' || conversion == 'e') {
                try {
                    if (maskPasswordValue.equals(text)) return true;
                    text = OAEncryption.encrypt(text);
                } catch (Exception e) {
                    throw new RuntimeException("encryption failed", e);
                }
            }
            newValue = text;
        }        

		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(getHub(), obj);
        
        // 1: confirm
        cb = og.objectsInternal().callObjectCallbackGetConfirmPropertyChangeObjectCallback(obj, getPropertyName(), newValue, getConfirmMessage(), getTitle());
        s = cb.getConfirmMessage();
        if (OAStr.isNotEmpty(s)) {
            if (!onConfirm(s, OAStr.notEmpty(cb.getConfirmTitle(), getTitle()) )) {
                return false;
            }
        }
        
        // 2: verify
        cb = og.objectsInternal().callObjectCallbackGetVerifyPropertyChangeObjectCallback(OAObjectCallback.CHECK_ALL, obj, getPropertyName(), null, newValue); 
        if (!cb.getAllowed()) {
            onError(cb.getResponse(), cb.getDisplayResponse());
            return false;
        }
            
        // 3: call method
        obj.setProperty(getPropertyName(), newValue, getFormat());

        return true;
    }
    
    public String getValueAsString() {
        if (hub == null) return null;
        final Object obj = hub.getAO();
        return getValueAsString(obj);
    }

    public String getValueAsString(Object obj) {
        if (obj == null) return null;
        
        if (!(obj instanceof OAObject)) return OAConv.toString(obj, getFormat());
        String s = ((OAObject) obj).getPropertyAsString(getPropertyName(), getFormat());
        return s;
    }
    
    public Object getValue() {
        if (hub == null) return null;
        final Object obj = hub.getAO();
        if (obj == null) return null;
        return getValue(obj);
    }

    public Object getValue(Object obj) {
        if (obj == null) return null;
        
        if (!(obj instanceof OAObject)) return obj;
        
        Object objx = ((OAObject) obj).getProperty(getPropertyName());
        return objx;
    }
}
