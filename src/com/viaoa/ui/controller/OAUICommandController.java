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

import java.util.logging.Logger;

import com.viaoa.callback.OAObjectCallback;
import com.viaoa.graph.OAGraphInternal;
import com.viaoa.graph.service.object.OAObjectCallbackService;
import com.viaoa.graph.service.object.OAObjectReflectService;
import com.viaoa.hub.*;
import com.viaoa.hub.listener.HubChangeListener;
import com.viaoa.lang.OAStr;
import com.viaoa.log.OALogger;
import com.viaoa.object.*;
import com.viaoa.runtime.OARuntime;


// qqqqqqq 20250116 under construction

/**
 * Controller used to bind UI command components (buttons, menu items, etc.)
 * to operations on a {@link Hub} and its active {@link OAObject}. The
 * {@link Command} enum describes the supported actions (navigation, create,
 * delete, submit, save, remove, etc.) and the controller manages enable/
 * visible state based on Hub and object conditions.
 *
 * <p>
 * OAUICommandController centralizes the logic for:
 * </p>
 *
 * <ul>
 *   <li>Determining when a command should be enabled (e.g. AO present,
 *       Hub not empty, object not submitted).</li>
 *   <li>Reacting to HubChangeListener events to keep command state in sync.</li>
 *   <li>Executing the configured command against the Hub and AO when the
 *       UI component is invoked.</li>
 * </ul>
 *
 * <p>
 * Some command types and related configuration are still under construction,
 * particularly the clipboard-related commands noted in the source. The core
 * navigation and CRUD-oriented commands are fully implemented and provide a
 * reusable controller for typical UI workflows.
 * </p>
 */
public class OAUICommandController extends OAUIController {
    private static final Logger LOG = OALogger.getLogger(OAUICommandController.class);

    /**
     * The command that this controller represents. Determines enable/visible
     * rules and the behavior executed when the command is invoked.
     */
    private Command command;

    /**
     * Optional property name used to apply a post-command update to an object
     * or collection of objects.
     */
    private String updateProperty;

    /**
     * Optional target object to receive a property update after the command
     * completes. If null, updates are applied to the Hub’s AO or selected objects.
     */
    private OAObject updateObject;
    
    /**
     * Value assigned to {@link #updateProperty} on the target object(s) after
     * command execution.
     */
    private Object updateValue;
    
    
    
/*qqqqq these need to be added    
    public static final ButtonCommand CUT = ButtonCommand.Cut;
    public static final ButtonCommand COPY = ButtonCommand.Copy;
    public static final ButtonCommand PASTE = ButtonCommand.Paste;

    public static final ButtonCommand OBJECT_METHOD = ButtonCommand.ObjectMethod;
    public static final ButtonCommand HUB_METHOD = ButtonCommand.HubMethod;
    public static final ButtonCommand OK = ButtonCommand.Ok;
    public static final ButtonCommand REFRESH = ButtonCommand.Refresh;
    public static final ButtonCommand STATIC_OBJECT_METHOD = ButtonCommand.StaticObjectMethod;
    Cancel, 
    Wizard;
*/    
    
    
    /**
     * Enumeration describing all supported UI command types, including
     * navigation, CRUD operations, submission, clipboard placeholders, and
     * miscellaneous AO/Hub-based commands. Each enum value defines whether it
     * changes the Hub's AO and the HubChangeListener.Type used to monitor
     * enabled/visibility conditions.
     */
    public static enum Command {
        /**
         * Misc command that uses a Hub or AO.
         * These should overwrite performCommand.
         */
        OtherUsesHub(HubChangeListener.Type.HubValid), 
        OtherUsesAO(HubChangeListener.Type.AoNotNull),
        /**
         * Save the current object.
         */
        Save(HubChangeListener.Type.AoNotNull), // might want to use submit command instead of Save 
        /**
         * Nav commands for changing active object.
         */
        First(true, HubChangeListener.Type.HubNotEmpty), 
        Last(true, HubChangeListener.Type.HubNotEmpty),
        Next(true, HubChangeListener.Type.HubNotEmpty), 
        Previous(true, HubChangeListener.Type.HubNotEmpty), 
        
        /**
         * Delete the Hub.AO
         */
        Delete(true, HubChangeListener.Type.AoNotNull), 
        /**
         * Remove the Hub.AO
         */
        Remove(true, HubChangeListener.Type.AoNotNull),
        /**
         * Remove all objects in Hub.
         */
        RemoveAll(true, HubChangeListener.Type.HubNotEmpty),
        /**
         * Submit (save) the current object.
         * Uses the OAObject.isSubmitted to check.
         */
        Submit(HubChangeListener.Type.AoNotNull),

        /**
         * Create new object and add or insert.
         */
        InsertNew(true, HubChangeListener.Type.HubValid), 
        AddNew(true, HubChangeListener.Type.HubValid),
        /**
         * Manually add or insert.  This will call getManualObject to supply the object to use.
         */
        NewManual(true, HubChangeListener.Type.HubValid), 
        AddManual(true, HubChangeListener.Type.HubValid),
        /**
         * Manually change the Hub AO, by calling getManualObject to get the object to use.
         */
        ManualChangeAO(true, HubChangeListener.Type.HubNotEmpty),
        /**
         * Set Hub AO to null.
         */
        ClearAO(true, HubChangeListener.Type.AoNotNull), 
        /**
         * Used to go to the Hub AO.
         */
        GoTo(HubChangeListener.Type.HubValid), 
        HubSearch(true, HubChangeListener.Type.HubValid), 
        Search(HubChangeListener.Type.HubValid),
        /**
         * Creates a copy of the current AO and adds to Hub.
         */
        Copy(HubChangeListener.Type.AoNotNull),
        Select(HubChangeListener.Type.HubValid), 
        /**
         * Calls OAObject.refresh on the current AO.
         */
        Refresh(HubChangeListener.Type.AoNotNull),
        /**
         * Move Hub.AO
         */
        MoveUp(true, HubChangeListener.Type.AoNotNull),
        MoveDown(true, HubChangeListener.Type.AoNotNull);
    
    	/**
    	 * The listener type associated with this command, used to determine when the
    	 * component should be re-evaluated for enable/visible state.
    	 */
        HubChangeListener.Type changeListenerType;
        
        /**
         * Indicates whether the command will change the Hub's active object when
         * executed.
         */
        private boolean bChangesAO;
        
        /**
         * Constructor for command values that do not change the active object.
         *
         * @param type the listener type used to track Hub/AO state.
         */
        private Command(HubChangeListener.Type type) {
            this.changeListenerType = type;
        }
        
        /**
         * Constructor for command values that may change the active object.
         *
         * @param changesAO true if executing the command changes the AO.
         * @param type the listener type used for state tracking.
         */
        private Command(boolean changesAO, HubChangeListener.Type type) {
            this.bChangesAO = changesAO;
            this.changeListenerType = type;
        }
        
        /**
         * Returns whether executing this command changes the Hub's active object.
         *
         * @return true if the command alters the AO.
         */
        public boolean getChangesAO() {
            return bChangesAO;
        }
    }

    /**
     * Creates a controller bound to the given Hub and command, using AO-only
     * mode and the command’s configured HubChangeListener.Type.
     *
     * @param hub the Hub this command operates on.
     * @param command the command type for this controller.
     */
    public OAUICommandController(Hub hub, Command command) {
        super(hub, null, null, true, command.changeListenerType);
        this.command = command;
    }

    /**
     * Returns the command associated with this controller.
     *
     * @return the command value.
     */
    public Command getCommand() {
        return command;
    }

    /**
     * Evaluates whether the command should be enabled for the current Hub and
     * its active object, delegating to {@link #isEnabled(Hub, OAObject)}.
     *
     * @return true if the command is currently enabled.
     */
    public boolean isEnabled() {
        Hub h = getHub();
        return isEnabled(h, (OAObject) h.getAO());
    }    
    
    /**
     * Determines whether this command is enabled based on Hub validity, AO
     * state, navigation boundaries, OAObject callbacks, and command-specific
     * rules. Uses callback delegates (e.g., allowSave, allowDelete) to enforce
     * business logic.
     *
     * @param hub the Hub used by the controller.
     * @param obj the active OAObject.
     * @return true if the command may be invoked.
     */
    public boolean isEnabled(final Hub hub, final OAObject obj) {
        if (!hub.isValid()) return false;
        
        if (command.getChangesAO()) {
            Hub hubLink = hub.getLinkHub(true);
            if (hubLink != null) {
                if (obj == null) return false;
                else {
                    if (!obj.isEnabled(hub.getLinkPath(true))) return false;                    
                }
            }
        }
        
        final int hubSize = hub.getSize();
        final int pos = hub.getPos();
        OAObjectCallback cb = null; 
        
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(hub, obj);

        switch (command) {
        case OtherUsesHub:
            return hub.isValid(); 
        case OtherUsesAO:
            return hub.getAO() != null; 
        case Save:
            cb = og.objectsInternal().callObjectCallbackGetAllowSaveObjectCallback(obj, OAObjectCallback.CHECK_ALL);
            break;
        case First:
            if (hubSize == 0) return false;
            break;
        case Last:
            if (hubSize == 0) return false;
            break;
        case Next:
            if (pos+1 >= hubSize) return false;
            break;
        case Previous:
            if (pos <= 0) return false;
            break;
        case Delete:
            if (pos < 0) return false;
            cb = og.objectsInternal().callObjectCallbackGetAllowDeleteObjectCallback((OAObject) hub.getAO());
            break;
        case Remove:
            if (pos < 0) return false;
            cb = og.objectsInternal().callObjectCallbackGetAllowRemoveObjectCallback(hub, (OAObject) hub.getAO(), OAObjectCallback.CHECK_ALL);
            break;
        case RemoveAll:
            cb = og.objectsInternal().callObjectCallbackGetAllowRemoveAllObjectCallback(hub, OAObjectCallback.CHECK_ALL);
            break;
        case Submit:
            if (obj == null) return false;
            if (!obj.isSubmitted()) return false;
            cb = obj.getAllowSubmit();
            break;
        case InsertNew:
        case AddNew:
        case NewManual:
        case AddManual:
            cb = og.objectsInternal().callObjectCallbackGetAllowNewObjectCallback(hub);
            break;
        case ManualChangeAO:
            break;
        case ClearAO:
            return pos >= 0;
        case GoTo:
            return hub.isValid();
        case HubSearch:
            return hubSize > 0;
        case Search:
            break;
        case Copy:
            cb = og.objectsInternal().callObjectCallbackGetAllowAddObjectCallback(hub, obj, OAObjectCallback.CHECK_ALL);
            if (cb.getAllowed()) {
                cb = og.objectsInternal().callObjectCallbackGetAllowCopyObjectCallback(obj);
            }
            break;
            
        case Select:
            break;
        case Refresh:
            return pos >= 0;
        case MoveUp:
            cb = og.objectsInternal().callObjectCallbackGetAllowNewObjectCallback(hub);
            break;
        case MoveDown:
            break;
        default:
            LOG.warning("Unhandled command "+command+" for OAUICommandController");
        }
        return cb == null || cb.getAllowed();
    }
    
    /**
     * Executes the command by calling the internal handler and, if successful,
     * triggers the completion message via {@link #onCompleted(String, String)}.
     *
     * @return true once command processing completes.
     */
    public boolean onCommand() {
        final OAObject obj = (OAObject) hub.getAO();
        if (_onCommand(hub, obj)) {
            String msg = getCompletedMessage();
            if (OAStr.isNotEmpty(msg)) {
                onCompleted(msg, getTitle()); 
            }
        }
        return true;
    }
    
    /**
     * Internal command dispatcher. Performs command staging: determining new AO,
     * validating link changes, performing confirmations, and finally invoking
     * {@link #performCommand(Hub, OAObject)}. Also evaluates object callbacks
     * and confirmation prompts.
     *
     * @param hub the Hub for the operation.
     * @param obj the current active object.
     * @return true if the command should proceed; false if cancelled.
     */
    private boolean _onCommand(final Hub hub, final OAObject obj) {
        OAObjectCallback cb; 
        OAObject newObject = null;
        String s;
        boolean bUseNewObject = false;
        
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(hub, obj);
		        
        // Step 1: get or create newObject
        cb = null;
        switch (command) {
        case OtherUsesHub:
        case OtherUsesAO:
            break;
        case First:
            newObject = (OAObject) hub.getAt(0);
            bUseNewObject = true;
            break;
        case Last:
            newObject = (OAObject) hub.getAt(hub.getSize()-1);
            bUseNewObject = true;
            break;
        case Next:
            newObject = (OAObject) hub.getAt(hub.getPos()+1);
            bUseNewObject = true;
            break;
        case Previous:
            newObject = (OAObject) hub.getAt(hub.getPos()-1);
            bUseNewObject = true;
            break;
        case Delete:
            break;
        case Remove:
            break;
        case RemoveAll:
            newObject = null;
            bUseNewObject = true;
            break;
        case Submit:
            break;
        case InsertNew:
        case AddNew:
            newObject = (OAObject) og.objectsInternal().callObjectReflectCreateNewObject(hub.getObjectClass());
            bUseNewObject = true;
            break;
        case NewManual:
            newObject = (OAObject) getManualObject();
            bUseNewObject = true;
            break;
        case AddManual:
            newObject = (OAObject) getManualObject();
            bUseNewObject = true;
            break;
        case ManualChangeAO:
            newObject = (OAObject) getManualObject();
            bUseNewObject = true;
            break;
        case ClearAO:
            newObject = null;
            bUseNewObject = true;
            break;
        case GoTo:
            break;
        case HubSearch:
            break;
        case Search:
            break;
        case Copy:
            newObject = og.objectsInternal().callObjectCallbackGetCopy(obj);
            bUseNewObject = true;
            break;
        case Select:
            break;
        case Refresh:
            break;
        }
        
        // Step 2: check to see if there is a link hub change
        cb = null;
        if (command.getChangesAO()) {
            Hub hubLink = hub.getLinkHub(true);
            if (hubLink != null) {
                final OAObject objx = (OAObject) hubLink.getAO();
                if (objx == null) {
                    onError("Link to hub AO is null", "");
                    return false;
                }
                
                final String propx = hub.getLinkPath(true);
                
                cb = objx.getIsValidPropertyChangeObjectCallback(propx, newObject);
                if (!cb.getAllowed()) {
                    onError(cb.getResponse(), cb.getDisplayResponse());
                    return false;
                }
                
                cb = og.objectsInternal().callObjectCallbackGetConfirmPropertyChangeObjectCallback(objx, propx, newObject, getConfirmMessage(), getTitle());
                s = cb.getConfirmMessage();
                if (OAStr.isNotEmpty(s)) {
                    if (!onConfirm(s, OAStr.notEmpty(cb.getConfirmTitle(), getTitle()) )) return false;
                }
            }
        }
        
        // Step 3: confirm
        cb = null;

        switch (command) {
        case OtherUsesHub:
        case OtherUsesAO:
            break;
        case Save:
            cb = og.objectsInternal().callObjectCallbackGetConfirmSaveObjectCallback(obj, getConfirmMessage(), getTitle());
            break;
        case First:
            break;
        case Last:
            break;
        case Next:
            break;
        case Previous:
            break;
        case Delete:
            cb = og.objectsInternal().callObjectCallbackGetConfirmDeleteObjectCallback(obj, getConfirmMessage(), getTitle());
            break;
        case Remove:
            cb = og.objectsInternal().callObjectCallbackGetConfirmRemoveObjectCallback(hub, obj, getConfirmMessage(), getTitle());
            break;
        case RemoveAll:
            cb = og.objectsInternal().callObjectCallbackGetConfirmRemoveAllObjectCallback(hub, getConfirmMessage(), getTitle());
            break;
        case InsertNew:
            cb = og.objectsInternal().callObjectCallbackGetConfirmAddObjectCallback(hub, newObject, getConfirmMessage(), getTitle());
            break;
        case AddNew:
            cb = og.objectsInternal().callObjectCallbackGetConfirmAddObjectCallback(hub, newObject, getConfirmMessage(), getTitle());
            break;
        case NewManual:
            cb = og.objectsInternal().callObjectCallbackGetConfirmAddObjectCallback(hub, newObject, getConfirmMessage(), getTitle());
            break;
        case AddManual:
            cb = og.objectsInternal().callObjectCallbackGetConfirmAddObjectCallback(hub, newObject, getConfirmMessage(), getTitle());
            break;
        case ManualChangeAO:
            break;
        case ClearAO:
            break;
        case GoTo:
            break;
        case HubSearch:
            break;
        case Search:
            break;
        case Copy:
            cb = og.objectsInternal().callObjectCallbackGetConfirmAddObjectCallback(hub, newObject, getConfirmMessage(), getTitle());
            break;
        case Select:
            break;
        case Refresh:
            break;
        default:
            LOG.warning("Unhandled command "+command+" for OAUICommandController");
        }

        if (cb != null) {
            s = cb.getConfirmMessage();
            if (OAStr.isNotEmpty(s)) {
                if (!onConfirm(s, OAStr.notEmpty(cb.getConfirmTitle(), getTitle()) )) return false;
            }
        }
        else {
            s = this.getConfirmMessage();
            if (OAStr.isNotEmpty(s)) {
                if (!onConfirm(s, getTitle())) return false;
            }
        }
        
        // Step 4: actual command
        return performCommand(hub, bUseNewObject ? newObject : obj);
    }


    
    /**
     * Executes the final command logic after confirmations. Supports navigation,
     * creation, deletion, submission, copying, AO changes, removal operations,
     * and Refresh. Updates additional target objects when {@link #updateProperty}
     * is set.
     *
     * @param hub the Hub to operate on.
     * @param obj the object to apply the command to.
     * @return true if the command was successfully performed.
     */
    protected boolean performCommand(final Hub hub, final OAObject obj) {
        switch (command) {
        case OtherUsesHub:
        case OtherUsesAO:
            break;
        case Save:
            if (obj == null) return false;
            obj.save();
            break;
        case First:
            hub.setPos(0);
            break;
        case Last:
            hub.setPos(hub.getSize()-1);
            break;
        case Next:
            hub.setPos(hub.getPos()+1);
            break;
        case Previous:
            hub.setPos(hub.getPos()-1);
            break;
        case Delete:
            if (obj == null) return false;
            obj.delete();
            break;
        case Remove:
            hub.remove(obj);
            break;
        case RemoveAll:
            hub.removeAll();
            break;
        case Submit:
            if (obj == null) return false;
            obj.save();
            break;
        case InsertNew:
            hub.insert(obj, hub.getPos());
            hub.setAO(obj);
            break;
        case AddNew:
        case NewManual:
        case AddManual:
            hub.add(obj);
            hub.setAO(obj);
            break;
        case ManualChangeAO:
            hub.setAO(obj);
            break;
        case ClearAO:
            hub.setPos(-1);
        case GoTo:
            break;
        case HubSearch:
            break;
        case Search:
            break;
        case Copy:
            hub.add(obj);
            hub.setAO(obj);
            break;
        case Select:
            break;
        case Refresh:
            if (obj != null) obj.refresh();
            break;
        default:
            LOG.warning("Unhandled command "+command+" for OAUICommandController, title="+getTitle());
        }
        
        if (updateProperty != null) {
            try {
                if (updateObject != null) {
                    updateObject.setProperty(updateProperty, updateValue);
                } else {
                    if (hubSelect != null) {
                        for (Object objx : hubSelect) {
                            if (objx instanceof OAObject) {
                                ((OAObject) objx).setProperty(updateProperty, updateValue);
                            }
                        }
                    }
                    if (getHub() != null) {
                        Object objx = getHub().getAO();
                        if (objx instanceof OAObject) {
                            ((OAObject) objx).setProperty(updateProperty, updateValue);
                        }
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException("OAUICommandController update property=" + updateProperty, ex);
            }
        }
        
        return true;
    }
    
    /**
     * Hook for intercepting confirmation prompts. Default implementation always
     * returns true. Subclasses override to display UI confirmation dialogs.
     *
     * @param confirmMessage the message shown to the user.
     * @param title optional title for the dialog.
     * @return true to continue command execution.
     */
    protected boolean onConfirm(String confirmMessage, String title) {
        return true;
    }

    /**
     * Hook invoked when an error prevents command execution. Subclasses may show
     * UI error dialogs or log details.
     *
     * @param errorMessage the primary error description.
     * @param detailMessage additional detail text.
     */
    protected void onError(String errorMessage, String detailMessage) {
    }
    
    /**
     * Hook invoked after a command completes successfully. Subclasses may override
     * to display a completion dialog or notification.
     *
     * @param completedMessage the message to show upon completion.
     * @param title optional title for the completion UI.
     */
    protected void onCompleted(String completedMessage, String title) {
    }

    /**
     * Supplies a manually created object for commands that require one
     * (NewManual, AddManual, ManualChangeAO). Default implementation returns null.
     * Subclasses override to provide domain-specific object creation.
     *
     * @return the manually supplied object or null.
     */
    protected Object getManualObject() {
        return null;
    }

    /**
     * Called when the UI component must refresh its display based on the active
     * object. Default implementation does nothing; subclasses override to update
     * visuals.
     *
     * @param object the active object used to populate UI state.
     */
    @Override
    public void updateComponent(Object object) {
    }

    /**
     * Updates any label or descriptive text associated with the UI component.
     * Default implementation does nothing.
     *
     * @param object the active object supplying label data.
     */
    @Override
    public void updateLabel(Object object) {
    }

    /**
     * Configures the controller so that after command execution, a property update
     * will be applied automatically to selected objects, the Hub AO, or a specific
     * target object if assigned. Registers enabled/visible rules tied to the
     * property and then requests a UI update.
     *
     * @param property the property name to update.
     * @param newValue the value assigned to the property.
     */
    public void setUpdateObject(String property, Object newValue) {
        this.updateObject = null;
        this.updateProperty = property;
        this.updateValue = newValue;

        addEnabledCheck(getHub(), HubChangeListener.Type.AoNotNull);
        addEnabledEditQueryCheck(getHub(), property);
        addVisibleEditQueryCheck(getHub(), property);

        callUpdate();
    }
}
