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

import com.viaoa.hub.*;
import com.viaoa.log.OALogger;

/**
 * Base Controller used to have UI components interact with Hub and OAObjects.
 * 
 *  *************** NOTE ***********************
 *  This is replaced by OAUIController
 *  *************** NOTE ***********************
 *  
 * <p>
 * @deprecated
 */
public abstract class OAUIBaseController {
    private static final Logger LOG = OALogger.getLogger(OAUIBaseController.class);

    protected final Hub hub;

    private String title;
    private String description;
    private String confirmMessage;
    private String completedMessage;


    public OAUIBaseController(Hub hub) {
        this.hub = hub;
    }

    public Hub getHub() {
        return hub;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    public String getTitle() {
        return this.title;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    public String getDescription() {
        return this.description;
    }
    
    public void setConfirmMessage(String msg) {
        this.confirmMessage = msg;
    }
    public String getConfirmMessage() {
        return this.confirmMessage;
    }

    public void setCompletedMessage(String msg) {
        this.completedMessage = msg;
    }
    public String getCompletedMessage() {
        return this.completedMessage;
    }
    
    public boolean isEnabled() {
        if (hub == null) return false;
        return hub.isValid();
    }

    public boolean isVisible() {
        return true;
    }
    
    
    /**
     * These allow for overwriting to handle user interactions.
     */
    protected boolean onConfirm(String confirmMessage, String title) {
        return true;
    }

    protected void onError(String errorMessage, String detailMessage) {
    }
    
    protected void onCompleted(String completedMessage, String title) {
    }
    
}
