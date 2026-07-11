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

import java.lang.reflect.Method;
import java.util.logging.Logger;

import com.viaoa.annotation.OAOne;
import com.viaoa.callback.OAObjectCallback;
import com.viaoa.compare.OACompare;
import com.viaoa.converter.OAConv;
import com.viaoa.converter.OAConverter;
import com.viaoa.datasource.OADataSource;
import com.viaoa.find.OAFinder;
import com.viaoa.hub.Hub;
import com.viaoa.metadata.OALinkInfo;
import com.viaoa.metadata.OAPropertyInfo;
import com.viaoa.oa.OA;
import com.viaoa.object.*;
import com.viaoa.path.OAPath;
import com.viaoa.runtime.OARuntime;
import com.viaoa.runtime.OAThreadLocalService;
import com.viaoa.runtime.OAThreadService;
import com.viaoa.secure.OAEncryption;
import com.viaoa.hub.HubEvent;
import com.viaoa.hub.HubListenerAdapter;
import com.viaoa.hub.listener.HubChangeListener;
import com.viaoa.hub.listener.HubChangeListener.HubProp;
import com.viaoa.hub.util.HubTemp;
import com.viaoa.lang.OAString;
import com.viaoa.lang.oa.VString;
import com.viaoa.template.OATemplate;
import com.viaoa.undo.OAUndoableEdit;

/**
 * Base controller for UI components that are bound to an OA {@link Hub} and
 * an optional property path. This class listens to Hub and OAObject events
 * and translates them into UI state updates for subclasses.
 *
 * <p>
 * OAUIController encapsulates the standard logic for:
 * </p>
 *
 * <ul>
 *   <li>Tracking the current {@code Hub} and its active object (AO).</li>
 *   <li>Resolving an optional {@code propertyPath} using {@link OAPath}.</li>
 *   <li>Listening for changes in the Hub (size, AO, content) and on the
 *       selected OAObject properties.</li>
 *   <li>Computing display values, tooltips, error messages, and templates
 *       for use by the concrete UI component.</li>
 *   <li>Managing enabled/visible state based on callbacks and Hub state.</li>
 *   <li>Integrating with undo/redo via {@link OAUndoableEdit}.</li>
 * </ul>
 *
 * <p>
 * Subclasses implement {@code updateComponent()} and related methods to
 * update the concrete UI widget when the underlying Hub or property changes.
 * This allows a consistent MVC pattern where Hubs/OAObjects are the model,
 * OAUIController is the controller, and the actual UI toolkit (Swing, web,
 * etc.) acts as the view.
 * </p>
 */
public abstract class OAUIController extends HubListenerAdapter {
    private static Logger LOG = Logger.getLogger(OAUIController.class.getName());

    /**
     * Per-instance debug flag used to enable verbose debugging for a specific UI
     * component.
     */
    public boolean DEBUG; // used for debugging a single component. ex: ((OALabel)lbl).setDebug(true)
    
    /**
     * Global debug flag used by all UI controllers to enable or disable
     * framework-level debug output.
     */
    public static boolean DEBUGUI = false; // used by debug() to show info

    /**
     * The primary Hub associated with this controller. It provides the active
     * object and acts as the root for property-based UI updates.
     */
    protected Hub hub;
    
    /**
     * Indicates whether the controller should respond only to changes on the
     * active object (AO) rather than all objects in the Hub.
     */
    protected final boolean bAoOnly;
    
    /**
     * Optional property path used to derive the value displayed or edited by the
     * UI component.
     */
    protected String propertyPath;
    
    /**
     * Parsed representation of the {@link #propertyPath}, used to resolve
     * property names, link navigation, and metadata.
     */
    protected OAPath oaPropertyPath;

    /**
     * Tracks the last HubEvent processed to prevent duplicate UI updates during
     * event cascades.
     */
    private volatile HubEvent heLastUpdate;

    /**
     * Class that defines the property immediately preceding the end of the
     * property path. Used when resolving callbacks and metadata.
     */
    protected Class endPropertyFromClass; // oaObj class (same as hub, or class for pp end)
    
    /**
     * The name of the final property in the property path. Used for formatting,
     * tooltips, and callback operations.
     */
    protected String endPropertyName;
    
    /**
     * The Java type of the final property in the property path.
     */
    protected Class endPropertyClass;

    /**
     * Property name used when registering listeners on the Hub. May be a synthetic
     * name derived from a multi-segment property path.
     */
    protected String hubListenerPropertyName;

    /**
     * Optional single object used when the controller is bound to a standalone
     * object rather than a Hub. Stored in a temporary Hub when necessary.
     */
    protected OAObject hubObject; // single object, that will be put in temp hub
    
    /**
     * Temporary Hub created when the controller is initialized with a standalone
     * object instead of a real Hub.
     */
    protected Hub hubTemp;

    /**
     * Indicates whether OAObjectCallback mechanisms should be used to validate,
     * format, and govern property edits.
     */
    protected final boolean bUseObjectCallback = true;

    /**
     * Default HubChangeListener type used when registering for Hub or property
     * change notifications.
     */
    protected HubChangeListener.Type hubChangeListenerType;

    /**
     * True if the end property is a Hub-calculated property (method taking a Hub
     * argument), requiring special resolution logic.
     */
    protected boolean bIsHubCalc;

    /**
     * Indicates whether the controller should listen for Hub size changes when
     * determining when to update the UI.
     */
    protected boolean bListenToHubSize;
    
    /**
     * Flag indicating whether undo/redo support should be applied to property
     * changes initiated through this controller.
     */
    protected boolean bEnableUndo = true;
    
    /**
     * Description used when creating undoable edits to identify the action in
     * undo/redo UI components.
     */
    protected String undoDescription;
    
    /**
     * Optional Hub used for multi-selection or alternate selection models within
     * the UI component.
     */
    protected Hub hubSelect;

    /**
     * Explicit format string used when converting property values to displayable
     * text. If null, a default format is determined.
     */
    protected String format;
    
    /**
     * Property path used to determine the font to apply when rendering this UI
     * component.
     */
    protected String fontPropertyPath;
    
    /**
     * Property path used to determine the background color of the UI component.
     */
    protected String backgroundColorPropertyPath;
    
    /**
     * Property path used to determine the foreground (text) color for rendering
     * the component.
     */
    protected String foregroundColorPropertyPath;
    
    /**
     * Property path used to determine the icon color used by the UI component.
     */
    protected String iconColorPropertyPath;

    /**
     * Confirmation message displayed before executing an operation such as a
     * property update or user action.
     */
    private String confirmMessage;

    /**
     * Maximum height and width allowed when displaying image content associated
     * with the component.
     */
    protected int maxImageHeight, maxImageWidth;

    /**
     * Root filesystem directory used to resolve image files for this component.
     */
    protected String imageDirectory;
    
    /**
     * Classpath location used to locate images when not provided from the
     * filesystem.
     */
    protected String imageClassPath;
    
    /**
     * Root class whose classloader is used to resolve image resources from the
     * classpath.
     */
    protected Class rootImageClassPath;;
    
    /**
     * Property path whose value indicates which image should be used for display.
     */
    protected String imagePropertyPath;

    /**
     * Property path used to retrieve tooltip text for the component from the
     * underlying OAObject.
     */
    protected String toolTipTextPropertyPath;
    
    /**
     * Text to display when the property value is null. Defaults to an empty
     * string, but may be customized to show alternate descriptions.
     */
    protected String nullDescription = "";
    
    /**
     * Indicates whether the UI component should treat display text as HTML.
     */
    protected boolean bHtml;

    /**
     * Minimum number of characters to display for the component's text value.
     */
    private int minDisplay;
    
    /**
     * Maximum number of characters to display for the component's text value.
     */
    private int maxDisplay;
    
    /**
     * Maximum number of input characters allowed when editing the value in this
     * component.
     */
    private int maxLength;

    /**
     * Listener used to track Hub and property changes relevant to updating the
     * UI component.
     */
    protected MyHubChangeListener changeListener; // listens for any/all hub+propPaths needed for component
    
    /**
     * Listener used to track Hub and property changes affecting the enabled state
     * of the component.
     */
    protected MyHubChangeListener changeListenerEnabled;
    
    /**
     * Listener used to track Hub and property changes affecting the visible
     * state of the UI component.
     */
    protected MyHubChangeListener changeListenerVisible;

    /** HTML used for displaying in some components (label, combo, list, autocomplete), and used for table cell rendering */
    /**
     * Template used to generate display text for the component, allowing custom
     * formatting through {@link OATemplate}.
     */
    protected String displayTemplate;
    
    /**
     * Compiled template instance used to process {@link #displayTemplate} when
     * generating display text.
     */
    protected OATemplate templateDisplay;

    /**
     * Template used to generate tooltip text for the component.
     */
    protected String toolTipTextTemplate;
    
    /**
     * Compiled template instance used to process the tooltip text template.
     */
    protected OATemplate templateToolTipText;
    
    /**
     * Indicates whether the property represented by this controller is required.
     * Derived from OA metadata when available.
     */
    protected boolean bRequired;

    /**
     * Optional message that explains why a component is enabled or disabled.
     */
    private String enabledMessage;
    
    /**
     * Optional message that explains why a component is visible or hidden.
     */
    private String visibleMessage;
    
    /*
     * 'U'ppercase, 'L'owercase, 'T'itle, 'J'ava identifier 'E'ncrpted password/encrypt 'S'HA password
     */
    /**
     * Character code indicating the conversion rule applied to input values:
     * uppercase, lowercase, title case, Java identifier, encrypted, SHA hash, etc.
     */
    protected char conversion;
    
    // This is used to handle password/encrypted data
    /**
     * Mask used when displaying encrypted or hashed password fields to prevent
     * exposing actual values.
     */
    private final static String maskPasswordValue = "******";

    /**
     * Title associated with this UI component, used for descriptive UI text.
     */
    private String title;
    
    /**
     * Description associated with this UI component, typically used in labels or
     * tooltips for explanatory text.
     */
    private String description;
    
    /**
     * Message displayed after a user operation completes successfully.
     */
    private String completedMessage;
    
    
    /**
     * Convenience constructor that initializes the controller with a Hub and
     * property path, using default settings for AO-only behavior and change
     * listener type.
     *
     * @param hub the Hub this controller will listen to.
     * @param propertyPath the property path used to retrieve values.
     */
    public OAUIController(Hub hub, String propertyPath) {
        this(hub, null, propertyPath, true, HubChangeListener.Type.AoNotNull);
    }    
    
    /**
     * Constructs a controller using the supplied Hub, optional single object,
     * property path, AO-only mode, and change-listener type. Initializes internal
     * state and performs a full reset.
     *
     * @param hub the Hub for the component.
     * @param object optional single object used when Hub is null.
     * @param propertyPath the property path used to retrieve the displayed value.
     * @param bAoOnly true to listen only to the active object; false to listen to all.
     * @param type the HubChangeListener type to register for.
     */
    public OAUIController(Hub hub, OAObject object, 
        String propertyPath,
        boolean bAoOnly, 
        HubChangeListener.Type type) 
    {
        this.hub = hub;
        this.hubObject = object;
        this.propertyPath = propertyPath;
        this.bAoOnly = bAoOnly;
        this.hubChangeListenerType = type;

        reset();
    }

    // used to track the last values used by reset
    /**
     * Tracks the last Hub used during the previous reset operation.
     */
    private Hub hubLast;

    /**
     * Tracks the last standalone object used when the controller was associated
     * with an object instead of a Hub.
     */
    private Object hubObjectLast;
    
    /**
     * Tracks the last listener registration created for the default change
     * listener type so it can be removed or updated properly on reset.
     */
    private HubChangeListener.HubProp hubChangeListenerTypeLast;
    
    /**
     * Used internally to suppress UI updates during reset or other bulk
     * operations.
     */
    protected volatile boolean bIgnoreUpdate;

    /**
     * Performs a full reconfiguration of the controller based on the current Hub,
     * object, and property path. Suppresses updates during reset and triggers a
     * post-reset update.
     */
    protected void reset() {
        try {
            bIgnoreUpdate = true;
            _reset();
        }
        finally {
            bIgnoreUpdate = false;
        }
        callUpdate();
    }

    // called when hub, property, etc is changed.
    // does not include resetting HubChangeListeners (changeListener, visibleChangeListener, enabledChangeListener)
    /**
     * Internal reset routine that reinitializes Hub listeners, temporary Hubs,
     * property-path resolution, metadata, callback registration, and listener
     * setup without triggering UI updates. Called only from {@link #reset()}.
     */
    protected void _reset() {
        // note: dont call close, want to keep visibleChangeListener, enabledChangeListener
        if (hubLast != null) {
            hubLast.removeHubListener(this);
        }
        if (hubObjectLast != null) {
            HubTemp.deleteHub(hubObjectLast);
        }
        if (changeListenerEnabled != null && hubChangeListenerTypeLast != null) {
            changeListenerEnabled.remove(hubChangeListenerTypeLast);
            hubChangeListenerTypeLast = null;
        }
        if (changeListener != null) {
            changeListener.close();
            changeListener = null;
        }

        if (hub != null) {
            this.hubTemp = null;
            this.hubObject = null;
        }
        else {
            if (hubObject == null) {
                this.hub = null;
                this.hubTemp = null;
            }
            else {
                this.hub = this.hubTemp = HubTemp.createHub(hubObject);
            }
        }

        hubObjectLast = hubObject;
        hubLast = this.hub;

        if (this.hub == null) {
            return;
        }

        if (propertyPath != null && propertyPath.indexOf('.') >= 0) {
            hubListenerPropertyName = propertyPath.replace('.', '_'); // (com.cdi.model.oa.WebItem)B_WebPart_Title
            hub.addHubListener(this, hubListenerPropertyName, new String[] { propertyPath }, bAoOnly);
        }
        else {
            hubListenerPropertyName = propertyPath;
            if (OAString.isNotEmpty(hubListenerPropertyName)) {
                hub.addHubListener(this, hubListenerPropertyName, bAoOnly);
            }
            else {
                hub.addHubListener(this);
            }
        }

        oaPropertyPath = new OAPath(hub.getObjectClass(), propertyPath);
        final String[] properties = oaPropertyPath.getProperties();
        endPropertyName = (properties == null || properties.length == 0) ? null : properties[properties.length - 1];

        if (hubChangeListenerType != null) { // else: this class already is listening to hub
            if (hubChangeListenerType == HubChangeListener.Type.HubNotEmpty || hubChangeListenerType == HubChangeListener.Type.HubEmpty) {
                bListenToHubSize = true;
            }
            hubChangeListenerTypeLast = getEnabledChangeListener().add(hub, hubChangeListenerType);
        }

        if (oaPropertyPath.getEndLinkInfo() != null && properties != null && properties.length == 1) {
            OAOne oaOne = oaPropertyPath.getOAOneAnnotation();
            if (oaOne != null) {
                if (OAString.isNotEmpty(oaOne.defaultPath())) {
                    if (!oaOne.defaultPathCanBeChanged()) {
                        getEnabledChangeListener().addPropertyNull(hub, properties[0]);
                    }
                }
            }
        }

        Method[] ms = oaPropertyPath.getMethods();
        endPropertyFromClass = hub.getObjectClass();
        if (ms != null && ms.length > 0) {
            Class[] cs = ms[ms.length - 1].getParameterTypes();
            bIsHubCalc = cs.length == 1 && cs[0].equals(Hub.class);
            endPropertyClass = ms[ms.length - 1].getReturnType();

            if (ms.length > 1) {
                endPropertyFromClass = ms[ms.length - 2].getReturnType();
            }
        }
        else {
            bIsHubCalc = false;
            endPropertyClass = String.class;
        }
        bDefaultFormat = false;

        if (bUseObjectCallback) {
            Class cz = hub.getObjectClass();
    		final OA oa = OARuntime.oa(cz);
            String ppPrefix = "";
            int cnt = 0;
            for (String prop : properties) {
                if (cnt == 0) {
                    addEnabledObjectCallbackCheck(hub, prop);
                    addVisibleObjectCallbackCheck(hub, prop);
                }
                else {
                	oa.internal().objects().rules().addObjectCallbackChangeListeners(hub, cz, prop, ppPrefix, getEnabledChangeListener(), true);
                	oa.internal().objects().rules().addObjectCallbackChangeListeners(hub, cz, prop, ppPrefix, getVisibleChangeListener(), false);
                }
                ppPrefix += prop + ".";
                cz = oaPropertyPath.getClasses()[cnt++];
            }

            if (cnt == 0) {
                addEnabledObjectCallbackCheck(hub, null);
                addVisibleObjectCallbackCheck(hub, null);
            }
        }

        
        if (hub != null) {
            OAPropertyInfo pi = hub.getOAObjectInfo().getPropertyInfo(endPropertyName);
            if (pi != null) {
                bRequired = pi.getRequired();
                minDisplay = pi.getDisplayLength();
                maxLength = pi.getMaxLength();

                OADataSource ds = OARuntime.datasource().get(hub.getObjectClass());
                if (ds != null) {
                    maxLength = ds.getMaxLength(hub.getObjectClass(), endPropertyName);
                    if (endPropertyClass != null) {
                        if (endPropertyClass.equals(String.class)) {
                            if (maxLength > 254) {
                                maxLength = -1;
                            }
                        }
                    }
                }
            }
            else {
                OALinkInfo li = hub.getOAObjectInfo().getLinkInfo(endPropertyName);
                bRequired = (li != null && li.getRequired());
            }
        }
    }

    /**
     * Rebinds this controller to a new Hub and property path, then performs a
     * full reset to reinitialize listeners and state.
     *
     * @param hub the new Hub to associate with this controller.
     * @param propertyPath the new property path to use.
     */
    public void bind(Hub hub, String propertyPath) {
        this.hub = hub;
        this.propertyPath = propertyPath;
        reset();
    }

    /**
     * Returns the Hub currently associated with this controller.
     *
     * @return the controller's Hub.
     */
    public Hub getHub() {
        return hub;
    }

    /**
     * Assigns a new Hub to this controller and performs a full reset to update
     * listeners and internal metadata.
     *
     * @param hub the new Hub to associate.
     */
    public void setHub(Hub hub) {
        this.hub = hub;
        reset();
    }

    /**
     * Returns the standalone object associated with this controller when no Hub
     * is supplied, or null if a Hub is used.
     *
     * @return the standalone object or null.
     */
    public Object getObject() {
        return hubObject;
    }

    /**
     * Returns the property path used to extract values from the Hub's active
     * object.
     *
     * @return the property path string.
     */
    public String getPropertyPath() {
        return propertyPath;
    }

    /**
     * Updates the property path and performs a full reset to apply the new path
     * to listeners and metadata.
     *
     * @param propPath the new property path.
     */
    public void setPropertyPath(String propPath) {
        propertyPath = propPath;
        reset();
    }

    /**
     * Returns the name of the final property in the resolved property path.
     *
     * @return the final property name or null.
     */
    public String getEndPropertyName() {
        return endPropertyName;
    }

    /**
     * Returns the Java type of the final property in the resolved property path.
     *
     * @return the end property class.
     */
    public Class getEndPropertyClass() {
        return endPropertyClass;
    }

    /**
     * Returns the class declaring the property immediately preceding the final
     * property in the property path.
     *
     * @return the class of the parent property.
     */
    public Class getEndPropertyFromClass() {
        return endPropertyFromClass;
    }

    /**
     * Returns the synthetic or direct property name used when registering Hub
     * listeners for property change events.
     *
     * @return the listener property name.
     */
    public String getHubListenerPropertyName() {
        return hubListenerPropertyName;
    }

    /**
     * Ensures that {@link #close()} is invoked before garbage collection to
     * release listeners and temporary Hubs.
     *
     * @throws Throwable if the superclass finalize method throws.
     */
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    /**
     * Releases all listeners, temporary Hubs, and associated resources. Removes
     * any listeners from the Hub and clears internal change listeners.
     */
    public void close() {
        if (hubObject != null) {
            HubTemp.deleteHub(hubObject);
        }
        if (changeListener != null) {
            changeListener.close();
            changeListener = null;
        }
        if (changeListenerEnabled != null) {
            changeListenerEnabled.close();
            changeListenerEnabled = null;
        }
        if (changeListenerVisible != null) {
            changeListenerVisible.close();
            changeListenerVisible = null;
        }

        enableVisibleListener(false);
        if (hub != null) {
            hub.removeHubListener(this);
        }
    }

    /**
     * Allows subclasses to register or unregister a UI-specific visibility
     * listener, typically tied to window or tab visibility.
     *
     * @param b true to enable the listener; false to disable it.
     */
    public void enableVisibleListener(boolean b) {
    }

    /**
     * Returns the Hub used for selection operations such as multi-select lists.
     *
     * @return the selection Hub.
     */
    public Hub getSelectHub() {
        return hubSelect;
    }

    /**
     * Returns the Hub used for multi-selection. Equivalent to
     * {@link #getSelectHub()}.
     *
     * @return the multi-select Hub.
     */
    public Hub getMultiSelectHub() {
        return hubSelect;
    }

    /**
     * Indicates whether objects may be removed from the selection Hub.
     *
     * @return true if removal is allowed; otherwise false.
     */
    public boolean getAllowRemovingFromSelectHub() {
        return bAllowRemovingFromSelectHub;
    }

    /**
     * Internal flag controlling whether the UI component can remove items from
     * the selection Hub. Defaults to true.
     */
    private boolean bAllowRemovingFromSelectHub;

    /**
     * Assigns a selection Hub to the controller and configures whether removal is
     * permitted. Existing listeners are removed and new ones are registered.
     *
     * @param newHub the Hub to use for selection.
     * @param bAllowRemovingFromSelectHub true to allow removal; false otherwise.
     */
    public void setSelectHub(Hub newHub, boolean bAllowRemovingFromSelectHub) {
        this.bAllowRemovingFromSelectHub = bAllowRemovingFromSelectHub;
        if (hubSelect != null) {
            getChangeListener().remove(hubSelect);
        }
        this.hubSelect = newHub;
        if (hubSelect != null) {
            getChangeListener().add(hubSelect);
        }
    }

    /**
     * Assigns a selection Hub and allows removal from it. Convenience overload of
     * {@link #setSelectHub(Hub, boolean)}.
     *
     * @param newHub the Hub to use.
     */
    public void setSelectHub(Hub newHub) {
        setSelectHub(newHub, true);
    }

    /**
     * Convenience method that assigns a Hub used for multi-selection. Removal
     * from the Hub is allowed by default.
     *
     * @param newHub the Hub to use for multi-selection.
     */
    public void setMultiSelectHub(Hub newHub) {
        setSelectHub(newHub, true);
    }

    /**
     * Tracks the class of the parent object used when resolving the real object
     * for a component whose Hub differs from its parent container's Hub.
     */
    private Class fromParentClass;

    /**
     * Cached property path used to navigate from a parent object's class to the
     * object represented in this controller's Hub.
     */
    private String fromParentPropertyPath;

    /**
     * Resolves the corresponding object within this controller's Hub when the
     * input object originates from a parent Hub (e.g., a table row). Uses cached
     * property-path navigation when necessary.
     *
     * @param fromObject the originating object.
     * @return the resolved object within this Hub, or the original object if
     *         resolution is not required.
     */
    protected Object getRealObject(Object fromObject) {
        if (fromObject == null || hub == null) {
            return fromObject;
        }
        Class c = hub.getObjectClass();
        if (c == null || c.isAssignableFrom(fromObject.getClass())) {
            return fromObject;
        }
        if (!(fromObject instanceof OAObject)) {
            return fromObject;
        }
        if (!OAObject.class.isAssignableFrom(getHub().getObjectClass())) {
            return fromObject;
        }

		final OA oa = OARuntime.oa((OAObject) fromObject);
        if (fromParentClass == null || !fromParentClass.equals(fromObject.getClass())) {
            fromParentClass = fromObject.getClass();
            fromParentPropertyPath = oa.internal().objects().reflect().getPathFromMaster((OAObject) fromObject, getHub());
        }
        return oa.internal().objects().reflect().getProperty((OAObject) fromObject, fromParentPropertyPath);
    }

    /**
     * Retrieves the value represented by this controller for the given object.
     * Handles Hub-calculated properties, direct object access, selection
     * checking, and OAObject property retrieval.
     *
     * @param obj the source object.
     * @return the evaluated value, or null if none applies.
     */
    public Object getValue(Object obj) {
        obj = getRealObject(obj);
        if (obj == null) {
            return null;
        }

        if (OAString.isEmpty(propertyPath) && hubSelect != null) {
            return hubSelect.contains(obj);
        }

        if (bIsHubCalc) {
    		final OA oa = OARuntime.oa(getHub());
            obj = oa.internal().objects().reflect().getProperty(getHub(), propertyPath);
        }
        else {
            if (OAString.isEmpty(propertyPath)) {
                return obj;
            }
            if (!(obj instanceof OAObject)) {
                return obj;
            }
            obj = ((OAObject) obj).getProperty(propertyPath);
        }
        return obj;
    }

    /**
     * Returns the controller value for the object formatted as a String, using
     * the default format.
     *
     * @param obj the source object.
     * @return the formatted value string.
     */
    public String getValueAsString(Object obj) {
        return getValueAsString(obj, getFormat());
    }

    /**
     * Returns the controller value for the object formatted using the supplied
     * format.
     *
     * @param obj the source object.
     * @param fmt the formatting string.
     * @return the formatted value string.
     */
    public String getValueAsString(Object obj, String fmt) {
        return getValueAsString(obj, fmt, -1);
    }
    
    /**
     * Returns the controller value as a formatted String, applying an optional
     * maximum length limit. Supports Hub-calculated properties and traversing
     * links via {@link OAFinder}.
     *
     * @param obj the source object.
     * @param fmt the formatting string.
     * @param maxLength the maximum number of characters allowed, or -1 for none.
     * @return the formatted, possibly truncated value string.
     */
    public String getValueAsString(Object obj, final String fmt, final int maxLength) {
        String s;
        if (obj == null) s = "";
        else if (obj instanceof OAObject) {
            if (oaPropertyPath != null && oaPropertyPath.getHasHubProperty()) {
                final VString vs = new VString("");
                OAFinder finder = new OAFinder(oaPropertyPath.getPathLinksOnly()) {
                    @Override
                    protected void onFound(OAObject obj) {
                        Object objx = obj.getProperty(oaPropertyPath.getLastPropertyName());
                        if (maxLength < 0 || vs.getValue().length() < maxLength) {
                            String s = OAConv.toString(objx, getFormat());
                            vs.setValue(OAString.concat(vs.getValue(), s, ", "));
                            if (maxLength > 0 && vs.getValue().length() >= maxLength) {
                                vs.setValue(vs.getValue().substring(0, maxLength-3) + "...");
                            }
                        }
                    }
                };
                finder.find((OAObject) obj);
                s = vs.getValue();
            }        
            else {
                s = ((OAObject) obj).getPropertyAsString(propertyPath, fmt);
            }
        }
        else {
            obj = getValue(obj);
            s = OAConv.toString(obj, fmt);
        }
        return s;
    }

    // calls the set method on the actualHub.ao
    /**
     * Sets the property value on the Hub's active object using the default
     * format. Performs conversion and callback processing as needed.
     *
     * @param value the new value to assign.
     */
    public void setValue(Object value) {
        String fmt = getFormat();
        Object obj = getHub().getAO();
        setValue(obj, value, fmt);
    }

    /**
     * Sets the property value on the specified object using the default format.
     *
     * @param obj the target object.
     * @param value the new value.
     */
    public void setValue(Object obj, Object value) {
        String fmt = getFormat();
        setValue(obj, value, fmt);
    }

    /**
     * Sets a property value on the specified object, applying case conversion,
     * password masking, encryption, title casing, hashing, and other transformations
     * based on {@link #conversion}. If the target object is an OAObject, delegates
     * to its {@code setProperty} method.
     *
     * @param obj the target object.
     * @param newValue the value to assign.
     * @param fmt the format string for conversion.
     */
    public void setValue(Object obj, Object newValue, String fmt) {
        if (obj == null) {
            return;
        }
        
        // conversion
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
                if (maskPasswordValue.equals(text)) return; // no change
                text = OAString.getSHAHash(text);
            } else if (conversion == 'P' || conversion == 'p') {
                if (maskPasswordValue.equals(text)) return; // no change
                text = OAString.getSHAHash(text);
            } else if (conversion == 'E' || conversion == 'e') {
                try {
                    if (maskPasswordValue.equals(text)) return; // no change
                    text = OAEncryption.encrypt(text);
                } catch (Exception e) {
                    throw new RuntimeException("encryption failed", e);
                }
            }
            newValue = text;
        }        
        
        if (obj instanceof OAObject) {
            ((OAObject) obj).setProperty(propertyPath, newValue, fmt);
        }
    }

    /**
     * Directly sets the raw property value on the object without formatting or
     * conversion logic. Only applies when the object is an OAObject.
     *
     * @param obj the target object.
     * @param newValue the raw value to assign.
     */
    public void setValueDirectly(Object obj, Object newValue) {
        if (obj instanceof OAObject) {
            ((OAObject) obj).setProperty(propertyPath, newValue, null);
        }
    }    
    
    /**
     * Enables or disables undo support for this controller. When disabled,
     * property changes do not generate undoable edits.
     *
     * @param b true to enable undo support; false to disable.
     */
    public void setEnableUndo(boolean b) {
        bEnableUndo = b;
    }

    /**
     * Indicates whether undo support is enabled for property changes.
     *
     * @return true if undo support is enabled.
     */
    public boolean getEnableUndo() {
        return bEnableUndo;
    }

    /**
     * Assigns the confirmation message that will be shown before committing
     * property changes or actions initiated by the component.
     *
     * @param msg the confirmation message.
     */
    public void setConfirmMessage(String msg) {
        confirmMessage = msg;
    }

    /**
     * Returns the confirmation message shown before executing a property change
     * or component action.
     *
     * @return the confirmation message, or null if none is set.
     */
    public String getConfirmMessage() {
        return confirmMessage;
    }

    /**
     * Invoked before a property change occurs. Default implementation calls
     * {@code super.beforePropertyChange(e)}. Subclasses may override to customize
     * behavior.
     *
     * @param e the HubEvent describing the change.
     */
    @Override
    public void beforePropertyChange(HubEvent e) {
        // TODO Auto-generated method stub
        super.beforePropertyChange(e);
    }

    /*
     * public void setNameValueHub(Hub<String> hub) { this.hubNameValue = hub; } public Hub<String> getNameValueHub() { return hubNameValue; }
     */

    /**
     * Performs confirmation logic for a pending property change. May invoke
     * OAObject callbacks, template processing, and UI confirmation dialogs.
     *
     * @param obj the target object.
     * @param newValue the proposed new value.
     * @return true if the change is confirmed; false to cancel.
     */
    protected boolean confirmPropertyChange(final Object obj, Object newValue) {
        String confirmMessage = getConfirmMessage();
        String confirmTitle = "Confirm";

        if (obj instanceof OAObject) {
            Object objx = obj;
            String prop;
            if (oaPropertyPath != null && oaPropertyPath.hasLinks()) {
                prop = endPropertyName;
                objx = oaPropertyPath.getLastLinkValue((OAObject) obj);
            }
            else {
                prop = propertyPath;
            }
            if (objx instanceof OAObject) {
        		final OA oa = OARuntime.oa((OAObject) objx);
                OAObjectCallback em = oa.internal().objects().rules().getConfirmPropertyChangeObjectCallback((OAObject) objx, prop, newValue, confirmMessage, confirmTitle);
                confirmMessage = em.getConfirmMessage();
                confirmTitle = em.getConfirmTitle();
            }
        }

        boolean result = true;
        if (OAString.isNotEmpty(confirmMessage)) {
            if (OAString.isEmpty(confirmTitle)) {
                confirmTitle = "Confirmation";
            }

            if (confirmMessage != null && confirmMessage.indexOf("<%=") >= 0 && obj instanceof OAObject) {
                OATemplate temp = new OATemplate(confirmMessage);
                temp.setProperty("newValue", newValue); // used by <%=$newValue%>
                confirmMessage = temp.process((OAObject) obj);
                if (confirmMessage != null && confirmMessage.indexOf('<') >= 0 && confirmMessage.toLowerCase().indexOf("<html>") < 0) {
                    confirmMessage = "<html>" + confirmMessage;
                }
            }

            result = onConfirmPropertyChangeShowOptionDialog(confirmMessage, confirmTitle);
        }
        return result;
    }

    /**
     * Hook for subclasses to display a confirmation dialog to the user. The
     * default implementation returns true without showing UI.
     *
     * @param confirmMessage the message to display.
     * @param confirmTitle the dialog title.
     * @return true to confirm the change; false to cancel.
     */
    protected boolean onConfirmPropertyChangeShowOptionDialog(String confirmMessage, String confirmTitle) {
        return true;
    }


    /**
     * Converts the supplied value into the correct type for the end property
     * using OAConv with the provided format.
     *
     * @param value the value to convert.
     * @param fmt the format used for conversion.
     * @return the converted value.
     */
    public Object getConvertedValue(Object value, String fmt) {
        value = OAConv.convert(endPropertyClass, value, fmt);
        return value;
    }

    /**
     * Tracks the HubProp used to enforce view-only behavior, typically restricting
     * edits unless the user has elevated privileges.
     */
    private HubProp hpViewOnly;

    /**
     * Enables or disables view-only mode. When enabled, adds a HubProp that
     * prevents editing unless the OAContext indicates super-admin privileges.
     *
     * @param b true to enable view-only mode; false to disable it.
     */
    public void setViewOnly(boolean b) {
        if (b) {
            if (hpViewOnly == null) {
                hpViewOnly = getEnabledChangeListener().addOnlySuperAdmin(); // viewOnly, unless OAContext.SuperAdmin=true
            }
        }
        else {
            if (hpViewOnly != null) {
                getEnabledChangeListener().remove(hpViewOnly);
                hpViewOnly = null;
            }
        }
    }

    /**
     * Alias for {@link #setViewOnly(boolean)} to support read-only controller
     * behavior.
     *
     * @param b true to make the controller read-only.
     */
    public void setReadOnly(boolean b) {
        setViewOnly(b);
    }

    /**
     * Validates a pending property change using OAObjectCallback validation rules.
     *
     * @param obj the target object.
     * @param newValue the proposed new value.
     * @return null if valid; otherwise an error message describing the failure.
     */
    public String isValid(final Object obj, Object newValue) {
        if (!bUseObjectCallback) {
            return null;
        }
        if (!(obj instanceof OAObject)) {
            return null;
        }
        OAObject oaObj = (OAObject) obj;

        String fmt = getFormat();
        newValue = getConvertedValue(newValue, fmt);

        Object objx = obj;
        String prop;
        if (oaPropertyPath != null && oaPropertyPath.hasLinks()) {
            prop = endPropertyName;
            objx = oaPropertyPath.getLastLinkValue((OAObject) obj);
        }
        else {
            prop = propertyPath;
        }

        String result = null;
        if (objx instanceof OAObject) {
    		final OA oa = OARuntime.oa((OAObject) objx);
            OAObjectCallback em = oa.internal().objects().rules().getVerifyPropertyChangeObjectCallback((OAObject) objx, prop, null, newValue);
            if (!em.getAllowed()) {
                result = em.getResponse();
                Throwable t = em.getThrowable();
                if (OAString.isEmpty(result)) {
                    if (t != null) {
                        for (; t != null; t = t.getCause()) {
                            result = t.getMessage();
                            if (OAString.isNotEmpty(result)) {
                                break;
                            }
                        }
                        if (OAString.isEmpty(result)) {
                            result = em.getThrowable().toString();
                        }
                    }
                    else {
                        result = "invalid value";
                    }
                }
            }
        }
        return result;
    }

    /**
     * Indicates whether the default format has been auto-computed and cached.
     */
    private boolean bDefaultFormat;

    /**
     * Cached default format derived from metadata or type conversion rules for the
     * end property.
     */
    private String defaultFormat;

    /**
     * Returns the format string used for displaying property values. If none was
     * explicitly set, computes and caches a default format using metadata or
     * OAObjectCallback overrides.
     *
     * @return the effective format string.
     */
    public String getFormat() {
        if (format != null) {
            return format;
        }
        if (!bDefaultFormat) {
            bDefaultFormat = true;
            if (oaPropertyPath != null) {
                defaultFormat = oaPropertyPath.getFormat();
            }
            if (defaultFormat == null) {
                defaultFormat = OAConverter.getFormat(endPropertyClass);
            }
        }

        if (bUseObjectCallback) {
            Object objx = hub.getAO();
            if (objx instanceof OAObject) {
                String prop;
                if (oaPropertyPath != null && oaPropertyPath.hasLinks()) {
                    objx = oaPropertyPath.getLastLinkValue((OAObject) objx);
                }
                if (objx instanceof OAObject) {
            		final OA oa = OARuntime.oa((OAObject) objx);
                    return oa.internal().objects().rules().getFormat((OAObject) objx, endPropertyName, defaultFormat);
                }
            }
        }
        return defaultFormat;
    }

    /**
     * Sets the format for displaying property values and triggers an update if it
     * differs from the previous value.
     *
     * @param fmt the new format, or "" for no formatting.
     */
    public void setFormat(String fmt) {
        String old = this.format;
        this.format = fmt;
        bDefaultFormat = true;
        defaultFormat = null;
        if (OACompare.isNotEqual(this.format, old)) {
            callUpdate();
        }
    }

    /**
     * Hook for determining whether parent UI containers are enabled. Default
     * implementation always returns true.
     *
     * @return true if the parent is enabled.
     */
    public boolean isParentEnabled() {
        return true;
    }

    /**
     * Sets the property path used to determine the font. Registers a listener on
     * the specified property and updates the component if changed.
     *
     * @param pp the property path for font lookup.
     */
    public void setFontPropertyPath(String pp) {
        String old = this.fontPropertyPath;
        fontPropertyPath = pp;
        if (OAString.isNotEmpty(pp)) {
            getChangeListener().add(hub, pp);
        }
        if (OACompare.isNotEqual(this.fontPropertyPath, old)) {
            callUpdate();
        }
    }

    /**
     * Returns the property path used to determine the font of the component.
     *
     * @return the font property path.
     */
    public String getFontProperty() {
        return fontPropertyPath;
    }

    /**
     * Sets the property path used to compute the foreground color and registers a
     * corresponding listener.
     *
     * @param pp the property path for foreground color.
     */
    public void setForegroundColorPropertyPath(String pp) {
        String old = this.foregroundColorPropertyPath;
        this.foregroundColorPropertyPath = pp;
        if (OAString.isNotEmpty(pp)) {
            getChangeListener().add(hub, pp);
        }
        if (OACompare.isNotEqual(this.foregroundColorPropertyPath, old)) {
            callUpdate();
        }
    }

    /**
     * Returns the property path used to determine the foreground color.
     *
     * @return the foreground color property path.
     */
    public String getForegroundColorPropertyPath() {
        return foregroundColorPropertyPath;
    }

    /**
     * Sets the property path used to determine the background color and registers
     * a listener for changes.
     *
     * @param pp the background color property path.
     */
    public void setBackgroundColorPropertyPath(String pp) {
        String old = this.backgroundColorPropertyPath;
        this.backgroundColorPropertyPath = pp;
        if (OAString.isNotEmpty(pp)) {
            getChangeListener().add(hub, pp);
        }
        if (OACompare.isNotEqual(this.backgroundColorPropertyPath, old)) {
            callUpdate();
        }
    }

    /**
     * Returns the property path used to determine the background color.
     *
     * @return the background color property path.
     */
    public String getBackgroundColorPropertyPath() {
        return backgroundColorPropertyPath;
    }

    /**
     * Sets the property path used to determine icon color for the component.
     * Registers a listener if the path is non-empty.
     *
     * @param pp the icon color property path.
     */
    public void setIconColorPropertyPath(String pp) {
        String old = iconColorPropertyPath;
        iconColorPropertyPath = pp;
        if (OAString.isNotEmpty(pp)) {
            getChangeListener().add(hub, pp);
        }
        if (OACompare.isNotEqual(this.iconColorPropertyPath, old)) {
            callUpdate();
        }
    }

    /**
     * Returns the property path used to determine the icon color.
     *
     * @return the icon color property path.
     */
    public String getIconColorPropertyPath() {
        return iconColorPropertyPath;
    }

    /**
     * Sets the property path used to compute tooltip text. Registers a listener if
     * non-empty and triggers a UI update if the value changes.
     *
     * @param pp the tooltip text property path.
     */
    public void setToolTipTextPropertyPath(String pp) {
        String old = this.toolTipTextPropertyPath;
        this.toolTipTextPropertyPath = pp;
        if (OAString.isNotEmpty(pp)) {
            getChangeListener().add(hub, pp);
        }
        if (OACompare.isNotEqual(this.toolTipTextPropertyPath, old)) {
            callUpdate();
        }
    }

    /**
     * Returns the property path used to retrieve tooltip text for the component.
     *
     * @return the tooltip text property path.
     */
    public String getToolTipTextPropertyPath() {
        return toolTipTextPropertyPath;
    }

    /**
     * Sets the root filesystem directory for images, normalizing path separators
     * and ensuring a trailing slash. Triggers an update if changed.
     *
     * @param s the directory path.
     */
    public void setImageDirectory(String s) {
        String old = this.imageDirectory;
        if (s != null) {
            s += "/";
            s = OAString.convert(s, "\\", "/");
            s = OAString.convert(s, "//", "/");
        }
        this.imageDirectory = s;
        if (OACompare.isNotEqual(this.imageDirectory, old)) {
            callUpdate();
        }
    }

    /**
     * Returns the root filesystem directory used to load images for this
     * component.
     *
     * @return the image directory path, or null if none is set.
     */
    public String getImageDirectory() {
        return imageDirectory;
    }

    /**
     * Sets the classpath location used to load images by associating a root class
     * and a relative classpath. Triggers an update if changed.
     *
     * @param root the root class whose classloader is used.
     * @param path the relative classpath to image resources.
     */
    public void setImageClassPath(Class root, String path) {
        String old = this.imageClassPath;
        this.rootImageClassPath = root;
        this.imageClassPath = path;
        if (OACompare.isNotEqual(this.imageClassPath, old)) {
            callUpdate();
        }
    }

    /**
     * Sets the property path whose value determines the image to display.
     * Registers a listener on the Hub if the path is non-empty and triggers a UI
     * update when changed.
     *
     * @param pp the image property path.
     */
    public void setImagePropertyPath(String pp) {
        String old = this.imagePropertyPath;
        this.imagePropertyPath = pp;
        if (OAString.isNotEmpty(pp)) {
            getChangeListener().add(hub, pp);
        }
        if (OACompare.isNotEqual(this.imagePropertyPath, old)) {
            callUpdate();
        }
    }

    /**
     * Returns the property path used to determine which image to display.
     *
     * @return the image property path.
     */
    public String getImagePropertyPath() {
        return imagePropertyPath;
    }

    /**
     * Returns the text used to represent null values when displaying property
     * data.
     *
     * @return the null-description text.
     */
    public String getNullDescription() {
        return nullDescription;
    }

    /**
     * Sets the description used to represent null values and triggers a UI update
     * if the value changes.
     *
     * @param s the description text to use for null values.
     */
    public void setNullDescription(String s) {
        String old = this.nullDescription;
        this.nullDescription = s;
        if (OACompare.isNotEqual(this.nullDescription, old)) {
            callUpdate();
        }
    }

    /**
     * Returns the listener used to monitor property and Hub changes that require
     * updating the UI. Lazily initializes an instance of {@code MyHubChangeListener}
     * with an {@code onChange} handler that invokes {@link #callUpdate()}.
     *
     * @return the change listener instance.
     */
    public HubChangeListener getChangeListener() {
        if (changeListener != null) {
            return changeListener;
        }
        changeListener = new MyHubChangeListener() {
            @Override
            protected void onChange() {
                OAUIController.this.callUpdate();
            }
        };
        return changeListener;
    }

    /**
     * Returns the listener used to track conditions that affect the enabled state
     * of the component. Lazily initializes a {@code MyHubChangeListener}.
     *
     * @return the enabled-state change listener.
     */
    public HubChangeListener getEnabledChangeListener() {
        if (changeListenerEnabled != null) {
            return changeListenerEnabled;
        }
        changeListenerEnabled = new MyHubChangeListener() {
            @Override
            protected void onChange() {
                OAUIController.this.callUpdate();
            }
        };
        return changeListenerEnabled;
    }

    /**
     * Returns the listener used to track conditions that affect the visible state
     * of the component. Lazily initializes a {@code MyHubChangeListener}.
     *
     * @return the visible-state change listener.
     */
    public HubChangeListener getVisibleChangeListener() {
        if (changeListenerVisible != null) {
            return changeListenerVisible;
        }
        changeListenerVisible = new MyHubChangeListener() {
            @Override
            protected void onChange() {
                OAUIController.this.callUpdate();
            }
        };
        return changeListenerVisible;
    }

    /**
     * Registers a property-path-based enabled-state check on the specified Hub.
     *
     * @param hub the Hub to monitor.
     * @param pp the property path to listen to.
     * @return the created HubProp describing the registration.
     */
    public HubProp addEnabledCheck(Hub hub, String pp) {
        return getEnabledChangeListener().add(hub, pp);
    }

    /**
     * Registers an enabled-state check that triggers when the property identified
     * by the path equals the specified value.
     *
     * @param hub the Hub to monitor.
     * @param pp the property path.
     * @param value the value to compare against.
     * @return the corresponding HubProp.
     */
    public HubProp addEnabledCheck(Hub hub, String pp, Object value) {
        return getEnabledChangeListener().add(hub, pp, value);
    }

    /**
     * Registers an enabled-state check using a specific HubChangeListener type.
     *
     * @param hub the Hub to monitor.
     * @param property the property to listen to.
     * @param type the type of listener event.
     * @return the created HubProp.
     */
    public HubProp addEnabledCheck(Hub hub, String property, HubChangeListener.Type type) {
        return getEnabledChangeListener().add(hub, property, type);
    }

    /**
     * Registers an enabled-state check using only the Hub and listener type,
     * without specifying a property path.
     *
     * @param hub the Hub to monitor.
     * @param type the event type to listen for.
     * @return the created HubProp.
     */
    public HubProp addEnabledCheck(Hub hub, HubChangeListener.Type type) {
        return getEnabledChangeListener().add(hub, type);
    }

    /**
     * Registers a callback-driven enabled-state check using OAObjectCallback
     * rules for determining editability.
     *
     * @param hub the Hub to monitor.
     * @param propertyName the property name being validated.
     * @return the HubProp created for this rule.
     */
    public HubProp addEnabledEditQueryCheck(Hub hub, String propertyName) {
        return getEnabledChangeListener().addObjectCallbackEnabled(hub, propertyName);
    }

    /**
     * Registers an object-callback-based enabled check for the given property.
     * Useful when enabling depends on OAObject business rules.
     *
     * @param hub the Hub to monitor.
     * @param propertyName the property to validate.
     * @return the HubProp created for this check.
     */
    public HubProp addEnabledObjectCallbackCheck(Hub hub, String propertyName) {
        return getEnabledChangeListener().addObjectCallbackEnabled(hub, propertyName);
    }

    /**
     * Registers a visibility rule based on the value of the specified property
     * path.
     *
     * @param hub the Hub to monitor.
     * @param pp the property path to check.
     * @return the HubProp describing the rule.
     */
    public HubProp addVisibleCheck(Hub hub, String pp) {
        return getVisibleChangeListener().add(hub, pp);
    }

    /**
     * Registers a visibility rule that triggers when the property at the path
     * equals the specified value.
     *
     * @param hub the Hub to monitor.
     * @param pp the property path to evaluate.
     * @param value the required value.
     * @return the created HubProp.
     */
    public HubProp addVisibleCheck(Hub hub, String pp, Object value) {
        return getVisibleChangeListener().add(hub, pp, value);
    }

    /**
     * Registers a visibility rule associated with a specific listener event type.
     *
     * @param hub the Hub to monitor.
     * @param property the property being listened to.
     * @param type the listener type.
     * @return the HubProp assigned to this rule.
     */
    public HubProp addVisibleCheck(Hub hub, String property, HubChangeListener.Type type) {
        return getVisibleChangeListener().add(hub, property, type);
    }

    /**
     * Registers a visibility rule that uses OAObjectCallback edit-query logic to
     * determine whether the component should be visible.
     *
     * @param hub the Hub to monitor.
     * @param propertyName the property evaluated by callbacks.
     * @return the created HubProp.
     */
    public HubProp addVisibleEditQueryCheck(Hub hub, String propertyName) {
        return getVisibleChangeListener().addObjectCallbackVisible(hub, propertyName);
    }

    /**
     * Registers a visibility rule using object callback logic associated with the
     * specified property.
     *
     * @param hub the Hub to monitor.
     * @param propertyName the callback-driven property.
     * @return the created HubProp.
     */
    public HubProp addVisibleObjectCallbackCheck(Hub hub, String propertyName) {
        return getVisibleChangeListener().addObjectCallbackVisible(hub, propertyName);
    }


    /**
     * Requests a UI update unless updates are currently suppressed. Delegates to
     * {@link #_update()} to perform the actual refresh.
     */
    protected void callUpdate() {
        if (bIgnoreUpdate) return;
        _update();
    }

    /**
     * Performs the core UI update logic. Prevents redundant updates by tracking
     * the last processed HubEvent, checks visibility rules, determines the active
     * object, and invokes {@link #updateComponent(Object)} and
     * {@link #updateLabel(Object)}.
     */
    protected void _update() {
        if (bIgnoreUpdate) return;

		final OAThreadLocalService srvcOAThreadLocal = ((OAThreadService) OARuntime.thread()).getThreadLocalService();  
        final HubEvent he = srvcOAThreadLocal.getCurrentHubEvent();
        if (heLastUpdate != null && (he == heLastUpdate)) {
            return;
        }
        heLastUpdate = he;

        if (isVisibleListenerEnabled()) {
            // check to see if component is visible
            if (!isVisibleOnScreen()) {
                return;
            }
        }
        
        Object obj;
        if (hub != null) {
            obj = hub.getAO();
        }
        else {
            obj = null;
        }
        
        updateComponent(obj);
        updateLabel(obj);
    }



    
    /**
     * Returns the visible state of the component based on the visibility change
     * listener's evaluation rules.
     *
     * @return true if the component should be visible.
     */
    public boolean isVisible() {
        return getVisibleChangeListener().getValue();
    }

    /**
     * Returns the enabled state of the component according to the enabled
     * change listener's rules.
     *
     * @return true if the component should be enabled.
     */
    public boolean isEnabled() {
        return getEnabledChangeListener().getValue();
    }
    
    /**
     * Returns an optional message explaining why the component is enabled or
     * disabled.
     *
     * @return the enabled-state message, or null.
     */
    public String getEnabledMessage() {
        return enabledMessage;
    }

    /**
     * Returns an optional message explaining the component's visibility state.
     *
     * @return the visible-state message, or null.
     */
    public String getVisibleMessage() {
        return visibleMessage;
    }

    /**
     * Sets the minimum number of characters to display when representing the
     * component's value.
     *
     * @param x the minimum display width.
     */
    public void setMinDisplay(int x) {
        this.minDisplay = x;
    }

    /**
     * Returns the minimum number of characters the component should display.
     *
     * @return the minimum display width.
     */
    public int getMinDisplay() {
        return this.minDisplay;
    }

    /**
     * Sets the maximum number of characters allowed when displaying the value.
     *
     * @param x the maximum display width.
     */
    public void setMaxDisplay(int x) {
        this.maxDisplay = x;
    }

    /**
     * Returns the maximum character width allowed when displaying the value.
     *
     * @return the maximum display width.
     */
    public int getMaxDisplay() {
        return this.maxDisplay;
    }
    
    /**
     * Sets the maximum input length the UI component should allow for editing
     * operations.
     *
     * @param x the maximum number of characters allowed.
     */
    public void setMaxLength(int x) {
        maxLength = x;
    }

    /**
     * Returns the maximum height allowed for images displayed by the component.
     *
     * @return the maximum image height.
     */
    public int getMaxImageHeight() {
        return maxImageHeight;
    }

    /**
     * Sets the maximum image height and triggers an update if it changes.
     *
     * @param maxImageHeight the new maximum image height.
     */
    public void setMaxImageHeight(int maxImageHeight) {
        int old = this.maxImageHeight;
        this.maxImageHeight = maxImageHeight;
        if (OACompare.isNotEqual(this.maxImageHeight, old)) {
            callUpdate();
        }
    }

    /**
     * Returns the maximum width permitted for images displayed by the component.
     *
     * @return the maximum image width.
     */
    public int getMaxImageWidth() {
        return maxImageWidth;
    }

    /**
     * Sets the maximum image width and triggers an update if the value changes.
     *
     * @param maxImageWidth the new maximum image width.
     */
    public void setMaxImageWidth(int maxImageWidth) {
        int old = this.maxImageWidth;
        this.maxImageWidth = maxImageWidth;
        if (OACompare.isNotEqual(this.maxImageWidth, old)) {
            callUpdate();
        }
    }

    /**
     * Enables or disables HTML rendering for display text.
     *
     * @param b true to treat display text as HTML.
     */
    public void setHtml(boolean b) {
        this.bHtml = b;
    }

    /**
     * Returns whether HTML rendering is enabled for this component.
     *
     * @return true if HTML mode is enabled.
     */
    public boolean getHtml() {
        return this.bHtml;
    }

    /**
     * Sets the description used when generating undoable edits for property
     * changes.
     *
     * @param s the description for undo/redo presentation.
     */
    public void setUndoDescription(String s) {
        undoDescription = s;
    }

    /**
     * Returns the undo/redo presentation description associated with this
     * controller.
     *
     * @return the undo description, or null.
     */
    public String getUndoDescription() {
        return undoDescription;
    }

    /**
     * Invoked after an object is added to the Hub. If the property is a hub
     * calculation or if size-based updates are enabled, triggers a UI update as
     * appropriate.
     *
     * @param e the HubEvent describing the addition.
     */
    @Override
    public void afterAdd(HubEvent e) {
        if (bIsHubCalc) {
            callUpdate();
        }
        else if (bListenToHubSize) {
            if (getHub().size() == 1) {
                callUpdate();
            }
        }
    }

    /**
     * Invoked after an object is removed from the Hub. Triggers an update when
     * the property is Hub-calculated or when size-based updates are enabled.
     *
     * @param e the HubEvent describing the removal.
     */
    @Override
    public void afterRemove(HubEvent e) {
        if (bIsHubCalc) {
            callUpdate();
        }
        else if (bListenToHubSize) {
            if (getHub().size() == 0) {
                callUpdate();
            }
        }
    }

    /**
     * Invoked when all objects are removed from the Hub. Updates the UI when the
     * property is Hub-calculated or when size-based updates are enabled.
     *
     * @param e the HubEvent describing the event.
     */
    @Override
    public void afterRemoveAll(HubEvent e) {
        if (bIsHubCalc) {
            callUpdate();
        }
        else if (bListenToHubSize) {
            callUpdate();
        }
    }

    /**
     * Called when the Hub receives a completely new list of objects. Always
     * triggers a UI update.
     *
     * @param e the HubEvent representing the new list.
     */
    @Override
    public void onNewList(HubEvent e) {
        callUpdate();
    }

    /**
     * Invoked after an object is inserted into the Hub. Triggers a UI update for
     * Hub-calculated properties or when size-based update rules apply.
     *
     * @param e the HubEvent describing the insertion.
     */
    @Override
    public void afterInsert(HubEvent e) {
        if (bIsHubCalc) {
            callUpdate();
        }
        else if (bListenToHubSize) {
            if (getHub().size() == 1) {
                callUpdate();
            }
        }
    }

    /**
     * Invoked when the Hub's active object changes. Delegates to
     * {@link #afterChangeActiveObject()} for processing.
     *
     * @param e the event representing the change.
     */
    @Override
    public void afterChangeActiveObject(HubEvent e) {
        afterChangeActiveObject();
    }

    /**
     * Invoked after a new list is created or assigned to the Hub. Always triggers
     * a UI update.
     *
     * @param e the HubEvent describing the new list.
     */
    @Override
    public void afterNewList(HubEvent e) {
        callUpdate();
    }

    /**
     * Invoked after a property on an object in the Hub changes. Applies AO-only
     * filtering and property-path matching before delegating to
     * {@link #_afterPropertyChange(HubEvent)}.
     *
     * @param e the HubEvent describing the property change.
     */
    @Override
    public void afterPropertyChange(final HubEvent e) {
        Object ao = getHub().getAO();
        if (bAoOnly) {
            if (ao == null || e.getObject() != ao) {
                return;
            }
        }
        if (!isListeningTo(hub, e.getObject(), e.getPropertyName())) {
            return;
        }
        _afterPropertyChange(e);
    }

    /**
     * Internal handler for property-change events. Calls
     * {@link #afterPropertyChange()} and then requests a UI update.
     *
     * @param e the HubEvent describing the property change.
     */
    protected void _afterPropertyChange(HubEvent e) {
        afterPropertyChange();
        callUpdate();
    }

    // called if the actual property is changed in the actualHub.activeObject
    /**
     * Hook invoked when the end property of the controller changes on the active
     * object. Subclasses may override to perform custom behavior.
     */
    protected void afterPropertyChange() {
    }

    /**
     * Hook invoked when the Hub's active object changes. Default implementation
     * triggers a UI update.
     */
    protected void afterChangeActiveObject() {
        callUpdate();
    }

    /**
     * Sets the display template used to format values for this component and
     * clears the cached compiled template.
     *
     * @param s the display template text.
     */
    public void setDisplayTemplate(String s) {
        this.displayTemplate = s;
        templateDisplay = null;
    }

    /**
     * Returns the display template text used to format component values.
     *
     * @return the template text, or null.
     */
    public String getDisplayTemplate() {
        return displayTemplate;
    }

    /**
     * Returns a compiled template for the display text. Lazily creates a new
     * {@link OATemplate} instance when needed.
     *
     * @return the compiled display template, or null if none is defined.
     */
    public OATemplate getTemplateForDisplay() {
        if (OAString.isNotEmpty(getDisplayTemplate())) {
            if (templateDisplay == null) {
                templateDisplay = new OATemplate<>(getDisplayTemplate());
            }
        }
        return templateDisplay;
    }

    /**
     * Returns formatted display text for the given object using the display
     * template if defined; otherwise returns the supplied default text.
     *
     * @param obj the object to display.
     * @param defaultText the fallback display text.
     * @return the resolved display text.
     */
    public String getDisplayText(Object obj, String defaultText) {
        obj = getRealObject(obj);
        if (!(obj instanceof OAObject)) {
            return defaultText;
        }

        String s = getDisplayTemplate();
        if (OAString.isEmpty(s)) {
            return defaultText;
        }

        defaultText = getTemplateForDisplay().process((OAObject) obj);
        if (defaultText != null && defaultText.indexOf('<') >= 0 && defaultText.toLowerCase().indexOf("<html>") < 0) {
            defaultText = "<html>" + defaultText;
        }

        return defaultText;
    }

    /**
     * Sets the tooltip text template used for generating component tooltips.
     * Clears the cached template so it will be rebuilt when next needed.
     *
     * @param s the tooltip template text.
     */
    public void setToolTipTextTemplate(String s) {
        this.toolTipTextTemplate = s;
        templateToolTipText = null;
    }

    /**
     * Returns the tooltip template text used for generating component tooltips.
     *
     * @return the tooltip template, or null.
     */
    public String getToolTipTextTemplate() {
        return toolTipTextTemplate;
    }

    /**
     * Returns a compiled tooltip template. Lazily initializes a new
     * {@link OATemplate} if needed.
     *
     * @return the tooltip OATemplate, or null if none is defined.
     */
    public OATemplate getTemplateForToolTipText() {
        if (OAString.isNotEmpty(getToolTipTextTemplate())) {
            if (templateToolTipText == null) {
                templateToolTipText = new OATemplate<>(getToolTipTextTemplate());
            }
        }
        return templateToolTipText;
    }

    /**
     * Computes the tooltip text for a given object. Can use property paths,
     * templates, and OAObject callbacks to resolve dynamic tooltip text, and
     * applies HTML wrapping when needed.
     *
     * @param obj the object to display a tooltip for.
     * @param ttDefault the default tooltip text.
     * @return the resolved tooltip text.
     */
    public String getToolTipText(Object obj, String ttDefault) {
        obj = getRealObject(obj);
        Object objx = obj;

        if (obj instanceof OAObject) {
            if (OAString.isNotEmpty(toolTipTextPropertyPath)) {
                ttDefault = ((OAObject) obj).getPropertyAsString(toolTipTextPropertyPath);
            }

            String s = getToolTipTextTemplate();
            if (OAString.isNotEmpty(s)) {
                ttDefault = s;
            }

            String prop;
            if (oaPropertyPath != null && oaPropertyPath.hasLinks()) {
                objx = oaPropertyPath.getLastLinkValue((OAObject) objx);
            }
            if (objx instanceof OAObject) {
        		final OA oa = OARuntime.oa((OAObject) objx);
                ttDefault = oa.internal().objects().rules().getToolTip((OAObject) objx, endPropertyName, ttDefault);
            }
        }
        else {
            if (OAString.isNotEmpty(toolTipTextPropertyPath) || OAString.isNotEmpty(getToolTipTextTemplate())) {
                ttDefault = null;
            }
        }

        if (ttDefault != null && ttDefault.indexOf("<%=") >= 0 && objx instanceof OAObject) {
            if (templateToolTipText == null || !ttDefault.equals(templateToolTipText.getTemplate())) {
                templateToolTipText = new OATemplate(ttDefault);
            }
            ttDefault = templateToolTipText.process((OAObject) obj, (OAObject) objx);
        }

        if (ttDefault != null && ttDefault.indexOf('<') >= 0 && ttDefault.toLowerCase().indexOf("<html>") < 0) {
            ttDefault = "<html>" + ttDefault;
        }

        return ttDefault;
    }

    /**
     * Called when visibility-based listener conditions change. Default behavior
     * triggers a component update.
     */
    protected void onVisibleListenerChange() {
        callUpdate();
    }


    /**
     * Determines whether this controller is listening to changes for the given
     * Hub, object, and property. Checks the main controller listener and all
     * associated change listeners.
     *
     * @param hub the Hub generating the event.
     * @param object the object whose property changed.
     * @param prop the property name.
     * @return true if this controller should process the event.
     */
    protected boolean isListeningTo(Hub hub, Object object, String prop) {
        if (hub == null || object == null || prop == null) {
            return false;
        }

        if (getHub() == hub && prop.equalsIgnoreCase(getHubListenerPropertyName())) {
            if (!bAoOnly || hub.getAO() == object) {
                return true;
            }
        }

        final MyHubChangeListener[] mcls = new MyHubChangeListener[] { changeListener, changeListenerEnabled, changeListenerVisible };
        for (MyHubChangeListener mcl : mcls) {
            if (mcl == null) {
                continue;
            }
            if (mcl.originalIsListeningTo(hub, object, prop)) {
                return true;
            }
        }
        return false;
    }

    // Shares hubListeners between hub and all 3 hubChangeListeners
    /**
     * Specialized HubChangeListener used by OAUIController to coordinate shared
     * listeners across enabled/visible/general update channels. Overrides listener
     * routing so all controller listeners behave as a unified group.
     */
    protected abstract class MyHubChangeListener extends HubChangeListener {
        
    	/**
    	 * Overrides default logic to route listener checks through the outer
    	 * OAUIController, ensuring all controller-related listeners share consistent
    	 * event-filtering behavior.
    	 *
    	 * @param hub the Hub generating the event.
    	 * @param object the object whose property changed.
    	 * @param property the property name.
    	 * @return true if the controller is listening for the event.
    	 */
    	@Override
        public boolean isListeningTo(Hub hub, Object object, String property) {
            // need to check others, since the hubListener is shared across the changeListeners
            return OAUIController.this.isListeningTo(hub, object, property);
        }

    	/**
    	 * Checks whether *this specific* MyHubChangeListener instance is listening to
    	 * the event, bypassing OAUIController's shared logic.
    	 *
    	 * @param hub the Hub generating the event.
    	 * @param object the object being evaluated.
    	 * @param property the property name.
    	 * @return true if this listener alone is listening.
    	 */
        public boolean originalIsListeningTo(Hub hub, Object object, String property) {
            // just check this one by calling the super
            return super.isListeningTo(hub, object, property);
        }

        /**
         * Removes a listener registration for the given Hub and property across the
         * entire set of controller listeners, ensuring that shared listeners are only
         * removed when no other listener depends on them.
         *
         * @param hub the Hub whose listener is being removed.
         * @param prop the property path being unregistered.
         */
        @Override
        public void remove(Hub hub, String prop) {
            final MyHubChangeListener[] mcls = new MyHubChangeListener[] { changeListener, changeListenerEnabled, changeListenerVisible };

            HubProp hp = null;
            for (MyHubChangeListener mcl : mcls) {
                if (mcl == null) {
                    continue;
                }

                for (HubProp hpx : mcl.hubProps) {
                    if (hpx.hub != hub) {
                        continue;
                    }
                    if (hpx.hubListener == null) {
                        continue;
                    }
                    if (!OAString.equals(prop, hpx.path)) {
                        continue;
                    }
                    hp = hpx;
                    break;
                }
            }
            if (hp == null) {
                return;
            }

            int cnt = 0;
            for (MyHubChangeListener mcl : mcls) {
                if (mcl == null) {
                    continue;
                }
                for (HubProp hpx : mcl.hubProps) {
                    if (hpx.hubListener == hp.hubListener) {
                        cnt++;
                    }
                }
            }

            if (cnt == 1) {
                hp.hub.removeHubListener(hp.hubListener);
            }
            hp.hubListener = null;
        }

        /**
         * Closes this listener and unregisters any Hub listeners that are no longer
         * shared with other controller listeners. Ensures that listener cleanup does
         * not disrupt other MyHubChangeListener instances.
         */
        @Override
        public void close() {
            final MyHubChangeListener[] mcls = new MyHubChangeListener[] { changeListener, changeListenerEnabled, changeListenerVisible };

            for (final HubProp hp : hubProps) {
                if (hp.bIgnore) {
                    continue;
                }
                if (hp.hubListener == null) {
                    continue;
                }
                if (hp.hub == OAUIController.this.hub) {
                    hp.hubListener = null;
                    continue;
                }

                boolean b = false;
                for (MyHubChangeListener mcl : mcls) {
                    if (mcl == null) {
                        continue;
                    }
                    if (mcl == this) {
                        continue;
                    }
                    for (HubProp hpx : mcl.hubProps) {
                        if (hpx.hubListener == hp.hubListener) {
                            b = true;
                            break;
                        }
                    }
                }
                if (!b && hp.hub != null) {
                    hp.hub.removeHubListener(hp.hubListener);
                }
                for (HubProp hpx : hubProps) {
                    if (hpx == hp) {
                        continue;
                    }
                    if (hpx.hubListener == hp.hubListener) {
                        hpx.hubListener = null;
                    }
                }
                hp.hubListener = null;
            }
        }

        /**
         * Attempts to reuse an existing HubListener for the new HubProp. Falls back
         * to the base implementation if reuse is not possible.
         *
         * @param newHubProp the Hub property registration being assigned.
         */
        @Override
        protected void assignHubListener(HubProp newHubProp) {
            if (!_assignHubListener(newHubProp)) {
                super.assignHubListener(newHubProp);
            }
        }

        /**
         * Attempts internal listener sharing among all controller listeners.
         * Evaluates Hub, property path, and calculation/derived-property rules to
         * determine whether an existing HubListener can be reused.
         *
         * @param newHubProp the HubProp to configure.
         * @return true if an existing listener was reused; false otherwise.
         */
        protected boolean _assignHubListener(HubProp newHubProp) {
            if (OAUIController.this.hub == newHubProp.hub) {
                if (newHubProp.path == null) {
                    return true;
                }
                if (newHubProp.path.indexOf('.') < 0) {
                    if (newHubProp.hub.getOAObjectInfo().getCalcInfo(newHubProp.path) == null) {
                        // 20221011
                        OALinkInfo lix = newHubProp.hub.getOAObjectInfo().getLinkInfo(newHubProp.path);
                        if (lix == null || lix.getType() == OALinkInfo.ONE) {
                            return true;
                        }
                    }
                }
                if (newHubProp.path.equalsIgnoreCase(OAUIController.this.propertyPath)) {
                    return true;
                }
            }

            Hub h = OAUIController.this.hub;
            if (h != null) {
                h = h.getLinkHub(true);
                if (h != null && h == newHubProp.hub) {
                    if (newHubProp.path == null) {
                        return true;
                    }
                }
            }

            final MyHubChangeListener[] mcls = new MyHubChangeListener[] { changeListener, changeListenerEnabled, changeListenerVisible };
            for (MyHubChangeListener mcl : mcls) {
                if (mcl == null) {
                    continue;
                }
                for (HubProp hp : mcl.hubProps) {
                    if (hp.bIgnore) {
                        continue;
                    }
                    if (hp.hub != newHubProp.hub) {
                        continue;
                    }
                    if (hp.hubListener == null) {
                        continue;
                    }
                    if (newHubProp.path == null) {
                        newHubProp.hubListener = hp.hubListener;
                        return true;
                    }
                    if (newHubProp.path.indexOf('.') < 0) {
                        if (newHubProp.hub != null && newHubProp.hub.getOAObjectInfo().getCalcInfo(newHubProp.path) == null) {
                            newHubProp.hubListener = hp.hubListener;
                            return true;
                        }
                    }
                    if (!newHubProp.path.equalsIgnoreCase(hp.path)) {
                        continue;
                    }
                    newHubProp.hubListener = hp.hubListener;
                    return true;
                }
            }
            return false;
        }
    }
    
    /**
     * Returns whether the property represented by this controller is required,
     * based on metadata from OAObjectInfo or link definitions.
     *
     * @return true if the property is required.
     */
    public boolean isRequired() {
        return bRequired;
    }

    /**
     * Returns the static mask value used when displaying encrypted or hashed
     * password strings.
     *
     * @return the password mask string.
     */
    public static String getMaskPasswordValue() {
        return maskPasswordValue;
    }
    

    
    /**
     * 'U'ppercase, 'L'owercase, 'T'itle, 'J'ava identifier 'E'ncrpted password/encrypt 'S'HA password (one way hash)
     */
    /**
     * Sets the character conversion rule applied to String input. Supported
     * values include uppercase, lowercase, title case, Java identifier, SHA hash,
     * and encryption.
     *
     * @param conv the conversion code.
     */
    public void setConversion(char conv) {
        conversion = conv;
    }

    /**
     * Returns the character conversion rule currently applied to String input.
     *
     * @return the conversion character.
     */
    public char getConversion() {
        return conversion;
    }
    
    
    /**
     * Indicates whether a visibility-based UI listener is active. Default
     * implementation returns false; subclasses may override.
     *
     * @return true if a visibility listener is active.
     */
    public boolean isVisibleListenerEnabled() {
        return false;
    }

    /**
     * Determines whether the UI component is presently visible within the UI
     * (e.g., active tab or window). Default implementation always returns true.
     *
     * @return true if the component is visible on screen.
     */
    public boolean isVisibleOnScreen() {
        return true;
    }

    /**
     * Sets the controller's title, often used by UI components for labeling or
     * display.
     *
     * @param title the title text.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the controller's title string.
     *
     * @return the title text, or null.
     */
    public String getTitle() {
        return this.title;
    }
    
    /**
     * Sets a human-readable description associated with the component.
     *
     * @param description descriptive text.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the user-facing description associated with the component.
     *
     * @return the description text.
     */
    public String getDescription() {
        return this.description;
    }
    
    /**
     * Sets the message displayed after an operation completes successfully.
     *
     * @param msg the completion message.
     */
    public void setCompletedMessage(String msg) {
        this.completedMessage = msg;
    }
    
    /**
     * Returns the completion message that may be shown after successful
     * operations.
     *
     * @return the completed message, or null.
     */
    public String getCompletedMessage() {
        return this.completedMessage;
    }

    public OA getOA() {
    	Class<? extends OAObject> c;
    	if (hub != null) c = hub.getObjectClass();
    	else c = null;
    	OA oa = OARuntime.oa(c);
    	return oa;
    }

    
    /**
     * Abstract method that subclasses must implement to update the UI component
     * when the underlying model or property value changes.
     *
     * @param object the active object used to populate the UI.
     */
    public abstract void updateComponent(Object object);

    /**
     * Abstract method that subclasses implement to update any label or text
     * representation associated with the component.
     *
     * @param object the active object used for label generation.
     */
    public abstract void updateLabel(Object object);
}
