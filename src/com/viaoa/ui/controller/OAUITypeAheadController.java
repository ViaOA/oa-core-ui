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

import java.util.*;

import com.viaoa.graph.OAGraphInternal;
import com.viaoa.hub.*;
import com.viaoa.hub.listener.HubChangeListener;
import com.viaoa.hub.listener.HubChangeListener.Type;
import com.viaoa.lang.OAStr;
import com.viaoa.lang.OAString;
import com.viaoa.metadata.OALinkInfo;
import com.viaoa.object.*;
import com.viaoa.runtime.OARuntime;
import com.viaoa.ui.typeahead.OATypeAhead;

/**
 * Controller for type-ahead / autocomplete UI components. The controller
 * uses a backing {@link Hub} of candidate objects and exposes a thin
 * {@link TypeAheadValue} representation (id, display text, dropdown text)
 * for the UI to render and select from.
 *
 * <p>
 * OAUITypeAheadController:
 * </p>
 *
 * <ul>
 *   <li>Tracks the active object and property via {@link OAUIController}.</li>
 *   <li>Maintains a Hub of matching candidates as the user types.</li>
 *   <li>Provides conversion between the selected candidate and the
 *       underlying OAObject or property value.</li>
 *   <li>Uses Hub listeners to keep suggestions and selection aligned
 *       with the object graph.</li>
 * </ul>
 *
 * <p>
 * Subclasses integrate this controller with a specific type-ahead widget,
 * handling key events and dropdown behavior, while OAUITypeAheadController
 * manages the model and selection logic.
 * </p>
 */
public abstract class OAUITypeAheadController extends OAUIController {

	/**
	 * Backing {@link OATypeAhead} instance used by this controller to
	 * perform searches, obtain the candidate hub, and resolve selected
	 * values.
	 */
    private final OATypeAhead typeAhead;

    /**
     * Lazily created {@link OAUIController} that tracks the linked hub
     * for the type-ahead's underlying hub and forwards UI update events
     * back to this controller.
     */
    private OAUIController controlLinkHub;

    public static class TypeAheadValue {
        public String id, display, dropDownDisplay;
        
        public TypeAheadValue(String id, String display, String dropDownDisplay) {
            this.id = id;
            this.display = display;
            if (OAStr.isNotEqual(display, dropDownDisplay)) this.dropDownDisplay = dropDownDisplay;
        }
    }
    
    /**
     * Creates a new type-ahead controller for the supplied
     * {@link OATypeAhead} configuration.
     * <p>
     * The superclass is initialized with the type-ahead hub and a
     * {@link Type#HubValid} change type, and the link UI controller
     * is initialized.
     *
     * @param typeAhead the {@link OATypeAhead} instance that provides
     *                  the backing hub and search behavior for this
     *                  controller
     */
    public OAUITypeAheadController(OATypeAhead typeAhead) {
        super(typeAhead.getHub(), null, null, false, Type.HubValid);
        this.typeAhead = typeAhead;
        getLinkUIController();
    }
    
    /**
     * Returns the {@link OATypeAhead} instance associated with this
     * controller.
     *
     * @return the backing {@link OATypeAhead} configuration
     */
    public OATypeAhead getTypeAhead() {
        return typeAhead;
    }
    
    /**
     * Resets the link {@link OAUIController}, if it has been created.
     * <p>
     * If the link controller exists, its {@link OAUIController#reset()}
     * method is invoked to clear or reinitialize its state.
     */
    public void reset() {
        OAUIController c = controlLinkHub;
        if (c != null) c.reset();
    }
    
    /**
     * Closes the link {@link OAUIController}, if it has been created.
     * <p>
     * If the link controller exists, its {@link OAUIController#close()}
     * method is invoked to release any resources and detach listeners.
     */
    public void close() {
        OAUIController c = controlLinkHub;
        if (c != null) c.close();
    }
    
    /**
     * Lazily creates and returns the {@link OAUIController} that tracks
     * the hub linked to the type-ahead hub.
     * <p>
     * The method attempts to determine the link hub in two ways:
     * <ul>
     *   <li>By asking the type-ahead hub for its link hub and link path.</li>
     *   <li>If no link hub exists, by resolving the master hub via
     *       {@link HubDetailDelegate#getMasterHub(Hub)} and obtaining
     *       a one-to-one {@link OALinkInfo} from master to detail.</li>
     * </ul>
     * If a link hub is found, a new {@link OAUIController} is created
     * that listens for {@link HubChangeListener.Type#AoNotNull} events
     * and delegates {@link #updateComponent(Object)} and
     * {@link #updateLabel(Object)} calls back to this controller.
     *
     * @return the link {@link OAUIController}, or {@code null} if no
     *         suitable link hub can be determined
     */
    protected OAUIController getLinkUIController() {
        if (controlLinkHub != null) return controlLinkHub;
    
        Hub hub = getTypeAhead().getHub();
        Hub hubLink = hub.getLinkHub(true);
        String linkPropertyName = null;
        
        if (hubLink != null) {
            linkPropertyName = hub.getLinkPath(true);
        }
        else {
    		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(hub);
            Hub hubx = og.hubsInternal().callHubDetailGetMasterHub(hub);
            if (hubx != null) {
                OALinkInfo li = og.hubsInternal().callHubDetailGetLinkInfoFromMasterToDetail(hub);
                if (li != null && li.getType() == li.TYPE_ONE) {
                    hubLink = hubx;
                    linkPropertyName = li.getName();
                }
            }
        }

        if (hubLink == null) return null;
        
        controlLinkHub = new OAUIController(hubLink, null, linkPropertyName, true, HubChangeListener.Type.AoNotNull) {
            @Override
            public void updateComponent(Object object) {
                OAUITypeAheadController.this.updateComponent(object);
            }
            @Override
            public void updateLabel(Object object) {
                OAUITypeAheadController.this.updateLabel(object);
            }
        };
        
        return controlLinkHub;
    }
    
    
    
    /**
     * Performs a type-ahead search and converts the resulting objects
     * into a list of {@link TypeAheadValue} instances.
     * <p>
     * The underlying {@link OATypeAhead} is asked to perform the search,
     * and for each returned {@link OAObject} a new {@link TypeAheadValue}
     * is created using the object's key, display value, and drop-down
     * display value.
     *
     * @param search the search text entered by the user; passed to the
     *               underlying {@link OATypeAhead#search(String)} method
     * @return a list of {@link TypeAheadValue} instances representing
     *         the matching candidates; never {@code null}
     */
    public List<TypeAheadValue> getTypeAheadValues(final String search) {
        List<TypeAheadValue> al =  new ArrayList<>(); 
        
        OATypeAhead ta = getTypeAhead();
        if (ta == null) return al;
        
        List<OAObject> alObj = ta.search(search);
        if (alObj != null) {
            for (OAObject obj : alObj) {
                TypeAheadValue tav = new TypeAheadValue(obj.getObjectKey().toString(), ta.getDisplayValue(obj), ta.getDropDownDisplayValue(obj));
                al.add(tav);
            }
        }
        return al;
    }

    /**
     * Resolves an object based on its identifier by delegating to the
     * underlying {@link OATypeAhead}.
     *
     * @param id the identifier used to locate the target object
     * @return the resolved object, or {@code null} if the identifier
     *         cannot be matched
     */
    public Object findObjectUsingId(String id) {
        Object obj = getTypeAhead().findObjectUsingId(id);
        return obj;
    }
 
    
    /**
     * Builds a JSON array representation of the type-ahead results for
     * the given search text.
     * <p>
     * The method calls {@link #getTypeAheadValues(String)} to obtain the
     * matching values and then constructs a JSON array string where each
     * element has an {@code id} and {@code display} property, and an
     * optional {@code dropDownDisplay} when present. All fields are
     * escaped using {@link OAString#escapeJson(String)}.
     *
     * @param search the search text entered by the user
     * @return a JSON array string representing the matching type-ahead
     *         candidates
     */
    public String getJson(String search) {
        List<TypeAheadValue> al = getTypeAheadValues(search);
        
        String json = "";
        for (TypeAheadValue tav : al) {
            if (json.length() > 0) json += ", ";
  
            json += "{\"id\":\"" + OAString.escapeJson(tav.id) + "\"" + 
                    ",\"display\":\"" + OAString.escapeJson(tav.display) + "\"";
            
            if (OAStr.isNotEmpty(tav.dropDownDisplay)) {
                json += ",\"dropDownDisplay\":\"" + OAString.escapeJson(tav.dropDownDisplay) + "\""; 
            }
            json += "}";
        }
        return "[" + json + "]";
    }    
    
    
    /**
     * Called when the bound UI component needs to be updated in response
     * to a change in the linked object or type-ahead selection.
     * <p>
     * Implementations should update the concrete UI widget associated
     * with this controller based on the supplied object.
     *
     * @param object the current object or selection that should be
     *               reflected in the UI component
     */
    public abstract void updateComponent(Object object);
    

    /**
     * Called when the label or textual representation associated with
     * the type-ahead component needs to be updated.
     * <p>
     * Implementations should refresh any label or display text based on
     * the supplied object.
     *
     * @param object the current object whose state should be reflected
     *               in the label or display text
     */
    public abstract void updateLabel(Object object);
}
