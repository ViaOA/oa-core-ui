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
package com.viaoa.hub.detail;

import com.viaoa.graph.OAGraphInternal;
import com.viaoa.graph.service.object.OAObjectInfoService;
import com.viaoa.hub.Hub;
import com.viaoa.hub.HubEvent;
import com.viaoa.hub.HubInternalBridge;
import com.viaoa.hub.HubListener;
import com.viaoa.hub.HubListenerAdapter;
import com.viaoa.metadata.OALinkInfo;
import com.viaoa.metadata.OAObjectInfo;
import com.viaoa.object.*;
import com.viaoa.runtime.OARuntime;


/**
 * This works similar to a HubDetail, except that it will first check to see if the data is loaded.
 * If not, it will load using swingWorker thread before setting the detail Hub
 * @author vvia
 */
public class HubJfcDetail {
    private Hub hubMaster;
    private Hub hubDetail;
    private String prop;
    private OALinkInfo li;

	private final HubInternalBridge faBridge = new HubInternalBridge();
	private final Hub.FriendAccess faHub;
    
    public HubJfcDetail(Hub hubMaster, Hub hubDetail, String prop) {
        this.hubMaster = hubMaster;
        this.hubDetail = hubDetail;
        this.prop = prop;
        
        this.faHub = faBridge.getHubFriendAccess();
        setup();
    }

    protected void setup() {
		final OAGraphInternal og = (OAGraphInternal) OARuntime.graph(hubMaster);
        OAObjectInfo oi = og.objectsInternal().callObjectInfoGetOAObjectInfo(hubMaster.getObjectClass());
        this.li = oi.getLinkInfo(prop);
        this.li = this.li.getReverseLinkInfo();
        HubListener hl = new HubListenerAdapter() {
            @Override
            public void afterChangeActiveObject(HubEvent e) {
                update();
            }
        };
        hubMaster.addListener(hl);
        update();
    }

    protected void update() {
        final OAObject obj = (OAObject) hubMaster.getAO();
     
        if (obj == null) {
            hubDetail.setSharedHub(null);
            faHub.getHubDataMaster(hubDetail).setMasterHub(hubMaster);
            faHub.getHubDataMaster(hubDetail).setDetailToMasterLinkInfo(li);
            return;
        }
        if (obj.isLoaded(prop)) {
            hubDetail.setSharedHub(((Hub) obj.getProperty(prop)), false);
            faHub.getHubDataMaster(hubDetail).setMasterHub(hubMaster);
            return;
        }
        hubDetail.setSharedHub(null);

        javax.swing.SwingWorker<Void, Void> sw = new javax.swing.SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                obj.getProperty(prop);
                return null;
            }

            @Override
            protected void done() {
                if (obj != hubMaster.getAO()) return;
                Hub h = (Hub) obj.getProperty(prop);
                hubDetail.setSharedHub(h, false);
                faHub.getHubDataMaster(hubDetail).setMasterHub(hubMaster);
                faHub.getHubDataMaster(hubDetail).setDetailToMasterLinkInfo(li);
            }
        };
        sw.execute();
    }

}
