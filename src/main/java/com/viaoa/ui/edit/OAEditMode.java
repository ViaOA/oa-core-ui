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
package com.viaoa.ui.edit;

import com.viaoa.object.OAObject;

/**
 * Lightweight flag object controlling whether edits are currently permitted
 * within an OA editing session or component scope.
 *
 * <p>This class wraps a single boolean property {@code allowEdit} that can be
 * bound to UI elements or other controllers.  When {@code false}, participating
 * editors or Hubs can disable mutation of their underlying {@link OAObject}s.
 *
 * <p><b>Behavior</b>:
 * <ul>
 *   <li>Inherits OAObject event mechanics for {@code fireBeforePropertyChange}
 *       and {@code firePropertyChange}, allowing full observer propagation.</li>
 *   <li>Acts as a shared reference point for enabling/disabling editing across views.</li>
 * </ul>
 */
public class OAEditMode extends OAObject {
    private static final long serialVersionUID = 1L;
    
    public static final String PROPERTY_AllowEdit = "AllowEdit";
    public static final String P_AllowEdit = "AllowEdit";

    protected boolean allowEdit;
    
    public boolean getAllowEdit() {
        return allowEdit;
    }
    
    public void setAllowEdit(boolean newValue) {
        fireBeforePropertyChange(P_AllowEdit, this.allowEdit, newValue);
        boolean old = allowEdit;
        this.allowEdit = newValue;
        firePropertyChange(P_AllowEdit, old, this.allowEdit);
    }
}
