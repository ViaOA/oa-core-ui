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
import java.util.concurrent.atomic.AtomicInteger;

import com.viaoa.graph.OAGraphInternal;
import com.viaoa.hub.*;
import com.viaoa.hub.listener.HubChangeListener;
import com.viaoa.lang.OAArray;
import com.viaoa.metadata.OALinkInfo;
import com.viaoa.object.*;
import com.viaoa.runtime.OARuntime;

/*qqqqqqqqqqq

todo: if table is not enabled, then dont allow changing active row

*/

/**
 * Abstract controller for grid or table UI components that display the
 * contents of a {@link Hub}. OAUITableController keeps the table rows,
 * active row selection, and optional link/select Hubs synchronized with
 * the underlying object graph.
 *
 * <p>
 * Responsibilities include:
 * </p>
 *
 * <ul>
 *   <li>Mapping table rows to objects in the main Hub.</li>
 *   <li>Updating the Hub's active object when the user changes the
 *       selected row.</li>
 *   <li>Coordinating with an optional link Hub and select Hub.</li>
 *   <li>Listening for changes to table column definitions through a
 *       dedicated Hub.</li>
 * </ul>
 *
 * <p>
 * Concrete subclasses integrate the controller logic with a specific table
 * widget (Swing JTable, web grid, etc.). Some behaviors, such as disallowing
 * row selection when the table is disabled, are noted as TODOs in the source
 * and can be refined in later phases.
 * </p>
 */
public abstract class OAUITableController  {

	/**
	 * Primary UI controller used to monitor and synchronize the main Hub with
	 * the table UI. Handles list changes, active-object changes, and reset events.
	 */
    private OAUIController controlHub;
    
    /**
     * Optional controller used when the table is linked to another Hub through a
     * reference or master/detail relationship. Provides visibility and enabled
     * filtering based on the linked object.
     */
    private OAUIController controlLinkHub;
    
    /**
     * The main Hub whose contents and active object are displayed as rows in the
     * table component.
     */
    private Hub hub;
    
    /**
     * The Hub used when the table participates in a link relationship. May
     * represent a master Hub or a referenced Hub for the active row.
     */
    private Hub hubLink;
    
    /**
     * The name of the link property used to retrieve or update the referenced
     * object when the table participates in a link relationship.
     */
    private String linkPropertyName; 
    
    /**
     * Optional Hub containing the objects that are currently selected. Used for
     * multi-select or external selection synchronization.
     */
    private Hub hubSelect;

    /**
     * Listener installed on the select Hub to update table selection state when
     * objects are added, removed, or when the list is replaced.
     */
    private HubListenerAdapter hlSelect;

    /**
     * Listener used to detect property changes that affect table column values.
     * Fires row-level change notifications for the UI component.
     */
    private HubListenerAdapter hlTableColumns;
    
    /**
     * Unique listener name used when registering column property-path listeners
     * on the main Hub. Ensures column updates are routed correctly.
     */
    private String gridListenerName;
    
    /**
     * Counter used to generate unique grid listener names for column-based
     * property notifications.
     */
    private static final AtomicInteger aiNameCounter = new AtomicInteger();
    
    /**
     * Indicates that initial construction and listener setup have completed.
     * Prevents premature updates during initialization sequences.
     */
    private final boolean bInitialized;
    
    /**
     * Constructs a table controller for the specified main Hub, optional select
     * Hub, and optional set of column property paths. Initializes UI controllers,
     * installs selection listeners, and registers column-based listeners when
     * provided.
     *
     * @param hub the primary Hub displayed by the table.
     * @param hubSelect the Hub that tracks selected objects (optional).
     * @param columnPropertyPaths the properties that define table columns.
     */
    public OAUITableController(Hub hub, Hub hubSelect, String[] columnPropertyPaths) {
        this.hub = hub;
        this.hubSelect = hubSelect;
        
        getUIController();
        getLinkUIController();
        
        if (hubSelect != null) {
            hlSelect = new HubListenerAdapter() {
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
            };
            hubSelect.addHubListener(hlSelect);
        }
        
        if (columnPropertyPaths != null && columnPropertyPaths.length > 0) {
            this.gridListenerName = "table_"+aiNameCounter.incrementAndGet();
            
            hlTableColumns = new HubListenerAdapter() {
                @Override
                public void afterPropertyChange(final HubEvent e) {
                    if (!gridListenerName.equalsIgnoreCase(e.getPropertyName())) return;
                    int row = getHub().getPos(e.getObject());
                    changed(row);
                }
            };
        
            hub.addHubListener(hlTableColumns, gridListenerName, columnPropertyPaths);
        }
        
        bInitialized = true;
        if (hubSelect != null) updateSelected();
    }
    

    /**
     * Returns the main Hub managed by this table controller.
     *
     * @return the primary Hub.
     */
    public Hub getHub() {
        return getUIController().getHub();
    }
    
    /**
     * Returns the Hub containing the current selected objects, if configured.
     *
     * @return the select Hub or null.
     */
    public Hub getSelectHub() {
        return hubSelect;
    }
    
    /**
     * Returns the Hub used for linked-object behavior. If a link UI controller
     * exists, its Hub is returned; otherwise null is returned.
     *
     * @return the link Hub or null.
     */
    public Hub getLinkHub() {
        if (getLinkUIController() == null) return null;
        return getLinkUIController().getHub();
    }

    /**
     * Returns the name of the link property associated with the linked Hub. If
     * no link controller is in use, null is returned.
     *
     * @return the link property name or null.
     */
    public String getLinkPropertyName() {
        if (getLinkUIController() == null) return null;
        return getLinkUIController().getPropertyPath();
    }
    
    /**
     * Resets both the main and link UI controllers so that the table UI refreshes
     * based on the latest Hub state.
     */
    public void reset() {
        getUIController().reset();
        OAUIController c = getLinkUIController();
        if (c != null) c.reset();
    }

    /**
     * Releases resources by closing both UI controllers and removing any Hub
     * listeners registered by this table controller.
     */
    public void close() {
        getUIController().close();
        if (controlLinkHub != null) controlLinkHub.close();
        if (hlSelect != null) hubSelect.removeHubListener(hlSelect);
        if (hlTableColumns != null) hub.removeListener(hlTableColumns);
    }
    
    /**
     * Lazily creates and returns the primary UI controller for the table. Routes
     * Hub events to table-level methods such as add(), remove(), insert(),
     * changed(), newList(), and active-row changes.
     *
     * @return the primary UI controller.
     */
    protected OAUIController getUIController() {
        if (controlHub != null) return controlHub ;
        
        controlHub = new OAUIController(hub, null, null, false, HubChangeListener.Type.HubValid) {
            @Override
            protected void reset() {
                if (bInitialized) {
                    super.reset();
                }
            }
            @Override
            public void updateComponent(Object object) {
                // OAUITableController.this.updateComponent(object);
            }
            @Override
            public void updateLabel(Object object) {
                // OAUITableController.this.updateLabel(object);
            }

            @Override
            public void afterAdd(HubEvent e) {
                OAUITableController.this.add(e.getObject());
            }
            @Override
            public void afterChangeActiveObject(HubEvent e) {
                OAUITableController.this.setChangeAO(hub.getPos(e.getObject()));
            }
            @Override
            public void afterInsert(HubEvent e) {
                OAUITableController.this.insert(e.getObject(), e.getPos());
            }
            @Override
            public void afterMove(HubEvent e) {
                OAUITableController.this.remove(e.getFromPos());
                OAUITableController.this.insert(e.getObject(), e.getToPos());
            }
            @Override
            public void afterNewList(HubEvent e) {
                OAUITableController.this.newList();
            }
            @Override
            public void afterRemove(HubEvent e) {
                OAUITableController.this.remove(e.getPos());
            }
            @Override
            public void afterRemoveAll(HubEvent e) {
                OAUITableController.this.clear();
            }
            @Override
            public void afterSort(HubEvent e) {
                OAUITableController.this.newList();
            }
        };
        return controlHub;
    }
        
    /**
     * Lazily creates and returns a UI controller for managing link-based Hub
     * behavior. Determines the correct link Hub and link property based on the
     * main Hub’s metadata, and installs basic UI update routing.
     *
     * @return the link UI controller, or null if no link applies.
     */
    protected OAUIController getLinkUIController() {
        if (controlLinkHub != null || hubSelect != null) return controlLinkHub;
    
        hubLink = hub.getLinkHub(true);
        
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
            protected void reset() {
                if (bInitialized) {
                    super.reset();
                }
            }
            @Override
            public void updateComponent(Object object) {
                // OAUITableController.this.updateComponent(object);
            }
            @Override
            public void updateLabel(Object object) {
                // OAUITableController.this.updateLabel(object);
            }
        };
        
        return controlLinkHub;
    }

    /**
     * Returns whether the table is currently enabled. Delegates to the primary
     * UI controller and, when present, the link controller.
     *
     * @return true if both controllers report enabled; false otherwise.
     */
    public boolean isEnabled() {
        boolean b = getUIController().isEnabled();
        if (b) {
            b = getLinkUIController() == null || getLinkUIController().isEnabled();
        }
        return b;
    }

    /**
     * Returns whether the table should be visible. Delegates to the primary
     * UI controller and, if applicable, the link controller.
     *
     * @return true if both controllers report visible; false otherwise.
     */
    public boolean isVisible() {
        boolean b = getUIController().isVisible();
        if (b) {
            b = (getLinkUIController() == null || getLinkUIController().isVisible());
        }
        return b;
    }
    
    /**
     * Synchronizes the table's selection state with the contents of the select
     * Hub. Iterates through the main Hub and builds an index array for all
     * objects contained in the select Hub, then delegates to {@link #setMultiSelect(int[])}.
     */
    protected void updateSelected() {
        int[] poss = new int[0];
        int pos = 0;
        for (Object obj : OAUITableController.this.getHub()) {
            boolean b  = OAUITableController.this.getSelectHub().contains(obj);
            if (b) poss = OAArray.add(poss,  pos);
            pos++;
        }
        setMultiSelect(poss);
    }
    
    
    /**
     * Changes the active object in the table by selecting the row at the
     * specified position. Delegates to {@link #setMultiSelect(int[])} with a
     * single-index array.
     *
     * @param pos the row index to set as the active object.
     */
    public void setChangeAO(int pos) {
        setMultiSelect(new int[] {pos});
    }

    /**
     * Hook for subclasses to apply multi-row selection to the underlying table
     * widget. The default implementation does nothing.
     *
     * @param poss the indexes of rows that should be selected.
     */
    public void setMultiSelect(int[] poss) {
    }
    
    /**
     * Hook for subclasses to handle adding a new row to the table when an
     * object is added to the main Hub. The default implementation does nothing.
     *
     * @param obj the object that was added.
     */
    public void add(Object obj) {
    }
    
    /**
     * Hook for subclasses to handle inserting a new row at the given position
     * when an object is inserted into the main Hub. The default implementation
     * does nothing.
     *
     * @param obj the object being inserted.
     * @param pos the row index where the object should appear.
     */
    public void insert(Object obj, int pos) {
    }
    
    /**
     * Hook for subclasses to handle removing a row from the table at the given
     * position when an object is removed from the main Hub. The default
     * implementation does nothing.
     *
     * @param pos the row index to remove.
     */
    public void remove(int pos) {
    }
    
    /**
     * Hook for subclasses to clear all rows from the table when the main Hub
     * is emptied. The default implementation does nothing.
     */
    public void clear() {
    }
    
    /**
     * Hook for subclasses to rebuild or refresh the entire table when the main
     * Hub receives a new list. The default implementation does nothing.
     */
    public void newList() {
    }
    
    /**
     * Hook for subclasses to update the table row at the specified index when
     * one or more column properties change for the corresponding object. The
     * default implementation does nothing.
     *
     * @param row the index of the row to refresh.
     */
    public void changed(int row) {
    }
    
}
