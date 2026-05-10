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
import com.viaoa.graph.OAGraphInternal;
import com.viaoa.graph.service.object.OAObjectCallbackService;
import com.viaoa.hub.Hub;
import com.viaoa.object.OAObject;
import com.viaoa.runtime.OARuntime;

/**
 * Controller for UI components that support multiple selection from a
 * {@link Hub}. The controller exposes the Hub contents to the view and
 * relies on {@link OAObjectCallback} rules to determine when the control
 * is enabled or visible.
 *
 * <p>
 * This is typically used for list or HTML-select style widgets where the
 * user can choose multiple OAObjects from a backing Hub. Actual selection
 * handling is performed by the view, while this controller manages the
 * high-level enabled/visible semantics.
 * </p>
 */
public class OAUIMultiSelectController extends OAUIBaseController {

	/**
	 * Creates a controller used for multi-selection UI components backed by the
	 * specified Hub. Delegates initialization to the base controller.
	 *
	 * @param hubSelect the Hub whose objects can be selected by the user.
	 */
    public OAUIMultiSelectController(Hub hubSelect) {
        super(hubSelect);
    }

    /**
     * Determines whether the UI component should be enabled. Requires that the
     * base controller is enabled and that the AllowEnabled object-callback rule
     * associated with the Hub permits selection.
     *
     * @return true if the component is allowed to be enabled.
     */
    @Override
    public boolean isEnabled() {
        if (!super.isEnabled()) return false;
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(hub);
        OAObjectCallback eq = og.objectsInternal().callObjectCallbackGetAllowEnabledObjectCallback(getHub());
        return eq.getAllowed();
    }
    
    /**
     * Determines whether the UI component should be visible. Requires that the
     * base controller is visible and that the AllowVisible object-callback rule
     * associated with the Hub permits display.
     *
     * @return true if the component should be visible.
     */
    @Override
    public boolean isVisible() {
        if (!super.isVisible()) return false;
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(getHub());
        OAObjectCallback eq = og.objectsInternal().callObjectCallbackGetAllowVisibleObjectCallback(getHub());
        return eq.getAllowed();
    }

    
    /**
     * Returns the callback used to obtain a pre-confirmation message before the
     * new selection value is known. This allows UI layers (such as JavaScript
     * clients) to present a confirmation prompt prior to completing the
     * selection change.
     *
     * @return the callback used for generating a pre-confirmation message, or
     *         null if none is available.
     */
    public OAObjectCallback getPreConfirmMessage() {
        OAObjectCallback cb = null; 
        //qqqqqq todo: need to confirm any add/remove
        return cb;
    }

    
}
