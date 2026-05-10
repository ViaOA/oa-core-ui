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
import com.viaoa.graph.OAGraphImpl;
import com.viaoa.graph.OAGraphInternal;
import com.viaoa.graph.service.object.OAObjectCallbackService;
import com.viaoa.hub.Hub;
import com.viaoa.lang.OAStr;
import com.viaoa.object.OAObject;
import com.viaoa.reflect.OAReflect;
import com.viaoa.runtime.OARuntime;

/**
 * Controller that binds a UI action to a named method on the current
 * active {@link OAObject} in a {@link Hub}. The controller uses
 * {@link OAObjectCallback} rules to determine whether the method is
 * currently enabled or visible, and then invokes the method when the
 * UI component is activated.
 *
 * <p>
 * Responsibilities include:
 * </p>
 *
 * <ul>
 *   <li>Tracking the method name to be called on the active OAObject.</li>
 *   <li>Using {@link OAObjectCallbackDelegate} to evaluate AllowEnabled
 *       and AllowVisible callbacks.</li>
 *   <li>Invoking the method via reflection (using OAReflect) when allowed.</li>
 *   <li>Handling completion messages and failure responses for the UI.</li>
 * </ul>
 *
 * <p>
 * This controller is typically used for domain actions such as
 * {@code approve()}, {@code close()}, {@code submit()}, etc., where
 * business rules determine when the operation is allowed for the
 * active object.
 * </p>
 */
public class OAUIMethodController extends OAUIBaseController {

	/**
	 * The name of the method to invoke on the active OAObject when this
	 * controller's action is triggered.
	 */
	private final String methodName;
    
	/**
	 * Creates a controller that binds a UI action to a method on the active
	 * object in the supplied Hub.
	 *
	 * @param hub the Hub whose active object contains the method.
	 * @param methodName the name of the method to invoke.
	 */
    public OAUIMethodController(Hub hub, String methodName) {
        super(hub);
        this.methodName = methodName;
    }
    
    /**
     * Returns the name of the method that will be invoked on the active object.
     *
     * @return the method name.
     */
    public String getMethodName() {
        return this.methodName;
    }
    
    /**
     * Determines whether the controller is enabled. Requires that the base
     * controller is enabled, the active object exists, and the AllowEnabled
     * callback for the method permits execution.
     *
     * @return true if the method may be invoked.
     */
    @Override
    public boolean isEnabled() {
        if (!super.isEnabled()) return false;

        OAObject obj = (OAObject) hub.getAO();
        if (obj == null) return false;
        
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(obj);
        OAObjectCallback eq = og.objectsInternal().callObjectCallbackGetAllowEnabledObjectCallback(OAObjectCallback.CHECK_ALL, getHub(), obj, getMethodName());
        return eq.getAllowed();
    }
    
        
    /**
     * Determines whether the controller is visible. Requires that the base
     * controller is visible and the AllowVisible callback for the method
     * allows the method to be displayed.
     *
     * @return true if the method should be visible.
     */
    @Override
    public boolean isVisible() {
        if (!super.isVisible()) return false;
        
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(getHub());
        OAObjectCallback eq = og.objectsInternal().callObjectCallbackGetAllowVisibleObjectCallback(getHub(), (OAObject) hub.getAO(), getMethodName());
        return eq.getAllowed();
    }

    
    /**
     * Executes the method on the current active object. Delegates to
     * {@link #onCallMethod(Hub, OAObject)} using the controller's Hub and
     * active object.
     *
     * @return true after processing completes.
     */
    public boolean onCallMethod() {
        return onCallMethod(hub, (OAObject) hub.getAO());
    }

    /**
     * Executes the bound method on the specified object. Processes confirmation,
     * verification, and completion handling. Invokes {@code _onCallMethod} to
     * perform the actual call.
     *
     * @param hub the Hub containing the object.
     * @param obj the target object whose method will be invoked.
     * @return true after the call sequence completes.
     */
    public boolean onCallMethod(final Hub hub, final OAObject obj) {
        Response resp = new Response();
        _onCallMethod(hub, obj, resp);
        if (resp.bCompleted) {
            String msg = getCompletedMessage();
            if (OAStr.isNotEmpty(msg)) {
                onCompleted(msg, getTitle()); 
            }
        }
        return true;
    }
    
    /**
     * Internal container used to track completion state and the result of the
     * invoked method.
     */
    private static class Response {
    	/**
    	 * Internal container used to track completion state and the result of the
    	 * invoked method.
    	 */
        boolean bCompleted;

        /**
         * The result returned from the invoked method.
         */
        Object result;
    }
    
    /**
     * Performs the confirmation, verification, and reflective invocation of the
     * target method. Updates the response object to indicate completion status
     * and returned value.
     *
     * @param hub the Hub supplying context.
     * @param obj the object whose method is invoked.
     * @param resp the response container tracking results.
     */
    private void _onCallMethod(final Hub hub, final OAObject obj, final Response resp) {
        OAObjectCallback cb; 
        String s;

		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(hub, obj);
        // 1: confirm
        cb = og.objectsInternal().callObjectCallbackGetConfirmCommandObjectCallback(obj, getMethodName(), getConfirmMessage(), getTitle());
        s = cb.getConfirmMessage();
        if (OAStr.isNotEmpty(s)) {
            if (!onConfirm(s, OAStr.notEmpty(cb.getConfirmTitle(), getTitle()) )) {
                resp.bCompleted = false;
            }
        }
        
        // 2: verify
        cb = og.objectsInternal().callObjectCallbackGetVerifyCommandObjectCallback(obj, getMethodName(), OAObjectCallback.CHECK_ALL);
        if (!cb.getAllowed()) {
            onError(cb.getResponse(), cb.getDisplayResponse());
            resp.bCompleted = false;
            return;
        }
            
        // 3: call method
        resp.result = OAReflect.executeMethod(obj, getMethodName());
        resp.bCompleted = true;
    }


}
