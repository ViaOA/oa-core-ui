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

import com.viaoa.converter.OAConv;
import com.viaoa.graph.OAGraphInternal;
import com.viaoa.hub.*;
import com.viaoa.hub.listener.HubChangeListener;
import com.viaoa.lang.OAArray;
import com.viaoa.lang.OAStr;
import com.viaoa.metadata.OALinkInfo;
import com.viaoa.object.*;
import com.viaoa.runtime.OARuntime;

/**
 * Abstract controller used by UI components that present a selectable list
 * of OAObjects from a {@link Hub}. Once a value is chosen, the controller
 * updates either the active object in the main Hub or a linked reference,
 * depending on configuration.
 *
 * <p>
 * OAUISelectController coordinates several Hubs:
 * </p>
 *
 * <ul>
 *   <li>The main Hub whose active object will be affected by the selection.</li>
 *   <li>An optional link Hub representing a reference property.</li>
 *   <li>An optional select Hub that provides the list of choices.</li>
 * </ul>
 *
 * <p>
 * It supports both single-select and multi-select scenarios and uses a
 * Hub listener to keep the UI and the underlying Hubs synchronized. Concrete
 * subclasses integrate with specific UI toolkits (Swing, web controls, etc.).
 * </p>
 */
public abstract class OAUISelectController  {

	/**
	 * The internal UI controller responsible for monitoring and updating the
	 * main Hub associated with this selector.
	 */
    private OAUIController controlHub;

    /**
     * Optional UI controller used when selections affect a linked reference Hub.
     * Created only when the controller represents a link-based selection.
     */
    private OAUIController controlLinkHub;

    /**
     * The primary Hub whose active object is affected by the user's selection.
     */
    private Hub hub;
    
    /**
     * The property path used by the main UI controller to determine or update
     * the selected value on the active object.
     */
    private String propertyPath;
    
    /**
     * The Hub representing a linked reference (master or related object) when
     * selection is applied to a relationship rather than a simple property.
     */
    private Hub hubLink;
    
    /**
     * Indicates whether the link relationship is based on positional indexing
     * within the Hub rather than a direct object reference.
     */
    private boolean linkOnPos;
    
    /**
     * The name of the link property on the active object used to retrieve or
     * update the reference when a selection changes.
     */
    private String linkPropertyName; 
    
    /**
     * Flag set after construction to indicate that initialization is complete.
     * Used to prevent premature updates during controller construction.
     */
    private final boolean bInitialized;
    
    /**
     * Optional Hub providing the list of objects that may be selected. When
     * non-null, a listener is attached to track changes and update selection UI.
     */
    private Hub hubSelect;
    
    /**
     * Listener used to track modifications in the select Hub so that the UI
     * component’s selected indices remain synchronized with the underlying data.
     */
    private HubListenerAdapter hlSelect;
    
    
    /**
     * Creates a selection controller for the given Hub and property path.
     * Optionally associates a select Hub and initializes listeners to keep the
     * UI state synchronized with underlying Hub changes.
     *
     * @param hub the main Hub whose AO is modified by selection.
     * @param propertyPath the property path to update when selections change.
     * @param hubSelect the Hub representing selectable choices (optional).
     * @param bCallReset whether to call reset() after initialization.
     */
    public OAUISelectController(Hub hub, String propertyPath, Hub hubSelect, boolean bCallReset) {
        this.hub = hub;
        
        this.propertyPath = propertyPath;
        
        this.hubSelect = hubSelect;
        
        getUIController();
        getLinkUIController();
        
        if (hubSelect != null) {
            hubSelect.addHubListener(new HubListenerAdapter() {
                void updateSelected() {
                    int[] poss = new int[0];
                    int pos = 0;
                    for (Object obj : OAUISelectController.this.getHub()) {
                        boolean b  = OAUISelectController.this.getSelectHub().contains(obj);
                        if (b) poss = OAArray.add(poss,  pos);
                        pos++;
                    }
                    OAUISelectController.this.setSelected(poss);
                }
                @Override
                public void afterAdd(HubEvent e) {
                    updateSelected();
                }
                @Override
                public void afterInsert(HubEvent e) {
                    updateSelected();
                }
                @Override
                public void afterNewList(HubEvent e) {
                    updateSelected();
                }
                @Override
                public void afterRemove(HubEvent e) {
                    updateSelected();
                }
                @Override
                public void afterRemoveAll(HubEvent e) {
                    updateSelected();
                }
            });
        }
        
        bInitialized = true;
        if (bCallReset) reset();
    }
    
    /**
     * Returns the main Hub associated with this controller, sourced from the
     * primary UI controller.
     *
     * @return the main Hub instance.
     */
    public Hub getHub() {
        return getUIController().getHub();
    }

    /**
     * Returns the property path name associated with the selection operation.
     *
     * @return the property path used by this controller.
     */
    public String getPropertyName() {
        return getUIController().getPropertyPath();
    }
    
    /**
     * Returns the Hub providing selectable choices, or null if no select Hub is
     * used.
     *
     * @return the select Hub.
     */
    public Hub getSelectHub() {
        return hubSelect;
    }
    
    /**
     * Returns the Hub used for link-based selection updates, if applicable. If
     * no link controller exists, null is returned.
     *
     * @return the link Hub or null.
     */
    public Hub getLinkHub() {
        if (getLinkUIController() == null) return null;
        return getLinkUIController().getHub();
    }

    /**
     * Returns the link property name used by the link UI controller. Returns null
     * when no link controller is active.
     *
     * @return the link property name or null.
     */
    public String getLinkPropertyName() {
        if (getLinkUIController() == null) return null;
        return getLinkUIController().getPropertyPath();
    }
    
    
    /**
     * Resets both the primary and link controllers so that the UI component
     * refreshes based on current Hub state.
     */
    public void reset() {
        getUIController().reset();
        OAUIController c = getLinkUIController();
        if (c != null) c.reset();
    }
    
    /**
     * Closes both the main and link UI controllers and removes the select Hub
     * listener if one was assigned, releasing all controller resources.
     */
    public void close() {
        getUIController().close();
        OAUIController c = getLinkUIController();
        if (c != null) c.close();
        if (hubSelect != null) {
            hubSelect.removeHubListener(hlSelect);
        }
    }
    
    /**
     * Lazily creates and returns the primary UI controller responsible for
     * monitoring the main Hub. Overrides multiple Hub events so that selection
     * state, list changes, and property changes propagate to this controller’s
     * higher-level selection methods.
     *
     * @return the primary OAUIController.
     */
    protected OAUIController getUIController() {
        if (controlHub != null) return controlHub ;
        
        controlHub = new OAUIController(hub, null, propertyPath, false, HubChangeListener.Type.HubValid) {
            @Override
            protected void reset() {
                if (bInitialized) {
                    super.reset();
                }
            }
            @Override
            public void updateComponent(Object object) {
                OAUISelectController.this.updateComponent(object);
            }
            @Override
            public void updateLabel(Object object) {
                OAUISelectController.this.updateLabel(object);
            }

            @Override
            public void afterAdd(HubEvent e) {
                OAUISelectController.this.add(e.getObject());
            }
            @Override
            public void afterChangeActiveObject(HubEvent e) {
                if (OAUISelectController.this.getSelectHub() == null) {
                    OAUISelectController.this.setSelected(hub.getPos(e.getObject()));
                }
            }
            @Override
            public void afterInsert(HubEvent e) {
                OAUISelectController.this.insert(e.getObject(), e.getPos());
            }
            @Override
            public void afterMove(HubEvent e) {
                OAUISelectController.this.remove(e.getFromPos());
                OAUISelectController.this.insert(e.getObject(), e.getToPos());
            }
            @Override
            public void afterNewList(HubEvent e) {
                OAUISelectController.this.newList();
            }
            @Override
            public void afterPropertyChange(HubEvent e) {
                if (OAStr.isEqualIgnoreCase(e.getPropertyName(), OAUISelectController.this.propertyPath)) {
                    OAUISelectController.this.changed(e.getObject());
                }
            }
            @Override
            public void afterRemove(HubEvent e) {
                OAUISelectController.this.remove(e.getPos());
            }
            @Override
            public void afterRemoveAll(HubEvent e) {
                OAUISelectController.this.clear();
            }
            @Override
            public void afterSort(HubEvent e) {
                OAUISelectController.this.newList();
            }
        };
        return controlHub;
    }
        
    /**
     * Lazily creates and returns the UI controller responsible for monitoring
     * link-based Hub updates. Determines the correct link Hub and property name
     * using Hub metadata, and installs event routing into this controller.
     *
     * @return the link Hub controller, or null if no link is available.
     */
    protected OAUIController getLinkUIController() {
        if (controlLinkHub != null || hubSelect != null) return controlLinkHub;
    
        hubLink = hub.getLinkHub(true);
        
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(hub);
        if (hubLink != null) {
            linkPropertyName = hub.getLinkPath(true);
        }
        else {
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
        linkOnPos = og.hubsInternal().callHubLinkGetLinkedOnPos(hub);
        
        controlLinkHub = new OAUIController(hubLink, null, linkPropertyName, true, HubChangeListener.Type.AoNotNull) {
            @Override
            protected void reset() {
                if (bInitialized) {
                    super.reset();
                }
            }
            @Override
            public void updateComponent(Object object) {
                OAUISelectController.this.updateComponent(object);
            }
            @Override
            public void updateLabel(Object object) {
                OAUISelectController.this.updateLabel(object);
            }
        };
        
        return controlLinkHub;
    }

    /**
     * Returns whether this selector is currently enabled. The result is based on
     * the enabled state of the primary UI controller and, when present, the link
     * UI controller.
     *
     * @return true if both the main and link controllers (if any) are enabled;
     *         false otherwise.
     */
    public boolean isEnabled() {
        boolean b = getUIController().isEnabled();
        if (b) {
            b = getLinkUIController() == null || getLinkUIController().isEnabled();
        }
        return b;
    }

    /**
     * Returns whether this selector should be visible. The result is based on
     * the visible state of the primary UI controller and, when present, the link
     * UI controller.
     *
     * @return true if both the main and link controllers (if any) are visible;
     *         false otherwise.
     */
    public boolean isVisible() {
        boolean b = getUIController().isVisible();
        if (b) {
            b = (getLinkUIController() == null || getLinkUIController().isVisible());
        }
        return b;
    }
    
    /**
     * Returns the displayable String value for the supplied object, taking into
     * account link-based selection. When the link is position-based, the value
     * is resolved by index into the main Hub before being formatted.
     *
     * @param hubFrom the originating Hub (not used directly in this method).
     * @param obj the source object whose selection value is to be displayed.
     * @return the formatted value string for the resolved selection.
     */
    public String getValueAsString(Hub hubFrom, Object obj) {
        Object objx = getLinkUIController().getValue(obj);
        if (linkOnPos && objx instanceof Number) objx = getHub().getAt(OAConv.toInt(objx));  
        String s = getUIController().getValueAsString(objx);
        return s;
    }
    
    /**
     * Returns the displayable String value for the supplied object using the
     * primary UI controller's formatting rules.
     *
     * @param obj the object whose value should be displayed.
     * @return the formatted value string.
     */
    public String getValueAsString(Object obj) {
        String s = getUIController().getValueAsString(obj);
        return s;
    }
    
    /**
     * Convenience method that selects a single index by delegating to
     * {@link #setSelected(int[])} with a one-element array.
     *
     * @param pos the index to select.
     */
    public void setSelected(int pos) {
        setSelected(new int[] {pos});
    }
    
    /**
     * Updates the current selection based on the supplied index positions.
     * Subclasses are expected to implement this to synchronize the UI widget
     * with the controller's selection state.
     *
     * @param poss the indexes that should be marked as selected.
     */
    public void setSelected(int[] poss) {
    }
    
    /**
     * Notification hook invoked when an object is added to the main Hub. 
     * Subclasses can override to update their UI representation of the list.
     *
     * @param obj the object that was added.
     */
    public void add(Object obj) {
    }
    
    /**
     * Notification hook invoked when an object is inserted into the main Hub at
     * a specific position. Subclasses can override to update the UI list.
     *
     * @param obj the object that was inserted.
     * @param pos the position at which the object was inserted.
     */
    public void insert(Object obj, int pos) {
    }
    
    /**
     * Notification hook invoked when an object is removed from the main Hub at
     * the given position. Subclasses can override to update their UI state.
     *
     * @param pos the position of the removed object.
     */
    public void remove(int pos) {
    }
    
    /**
     * Notification hook invoked when all objects are cleared from the main Hub.
     * Subclasses can override to clear any corresponding UI list or selection.
     */
    public void clear() {
    }
    
    /**
     * Notification hook invoked when the main Hub receives a new list. Subclasses
     * can override to rebuild their UI representation from the updated list.
     */
    public void newList() {
    }

    /**
     * Notification hook invoked when the value associated with a particular
     * object changes in a way that may affect the selection display. Subclasses
     * can override to refresh the UI element for that object.
     *
     * @param object the object whose state changed.
     */
    public void changed(Object object) {
    }

    /**
     * Invoked when the UI component needs to refresh its state based on the
     * supplied object. Concrete subclasses must implement this to synchronize
     * their widget with the controller.
     *
     * @param object the object used to update the component.
     */
    public abstract void updateComponent(Object object);
    

    /**
     * Invoked when any label or textual description associated with the selector
     * needs to be updated. Concrete subclasses must implement this to update
     * label text in their UI environment.
     *
     * @param object the object used to generate label content.
     */
    public abstract void updateLabel(Object object);
    
    
    
    //qqqqqqqq events from client JS to change hub.AO ... need to validate ...
    /*
    call this to check hubLink.AO change is valid ...
    
    1: isEnabled
    2: public String isValid(final Object obj, Object newValue)
    3: [confirm]
    4: hubLink.setAO(newValue) 
    */
    
}
