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

/**
 * Provides UI-controller classes that implement the presentation-layer bindings for OA Hubs,
 * model objects, and property paths.  These controllers supply the “C” in an MVC design and
 * allow UI components—HTML fields, tables, selects, type-ahead inputs, and commands—to stay
 * synchronized with the OA Object Graph.
 *
 * <p>
 * The controllers in this package manage:
 * </p>
 *
 * <ul>
 *   <li><b>Component enable/disable and visible state</b> based on object metadata,
 *       validation rules, edit mode, and OAObjectCallback logic.</li>
 *
 *   <li><b>One-way and two-way data binding</b> between UI widgets and:
 *       <ul>
 *         <li>a Hub’s active object,</li>
 *         <li>a Hub’s selected items (multi-select),</li>
 *         <li>a property path of the active object, or</li>
 *         <li>a linked Hub relationship.</li>
 *       </ul>
 *   </li>
 *
 *   <li><b>Listening to Hub change events</b>—including add, remove, AO change, property change,
 *       and validation events—to update UI components in real time.</li>
 *
 *   <li><b>Command routing</b> (new, delete, save, refresh, etc.) through {@code OAUICommandController},
 *       which standardizes UI actions as controller operations rather than UI-specific code.</li>
 *
 *   <li><b>Table population and synchronization</b>, using {@link com.viaoa.ui.controller.OAUITableController},
 *       which monitors a Hub, its selection Hub, and optional link Hub to maintain row state
 *       and column updates.</li>
 *
 *   <li><b>Type-ahead and auto-suggest controllers</b>, which operate over a Hub and provide
 *       filtered choices as the user types.</li>
 *
 *   <li><b>Multi-select support</b> that maintains a separate selection Hub and keeps UI and
 *       Hub selection logic synchronized.</li>
 *
 *   <li><b>Automatic enable/disable visibility rules</b> driven by {@code OAObjectCallback}
 *       permissions, allowing UI components to respect domain-level access constraints.</li>
 * </ul>
 *
 * <p>
 * These controllers do not perform any rendering.  Instead, they provide a uniform,
 * framework-independent layer that allows:
 * </p>
 *
 * <ul>
 *   <li>Server-side HTML generation,</li>
 *   <li>Dynamic client-side UI updates,</li>
 *   <li>OA-Web, JavaScript, or any custom UI to bind to the OA Object Graph
 *       without duplicating logic.</li>
 * </ul>
 *
 * <p>
 * The package plays a central role in keeping UI state consistent with the Object Graph,
 * enforcing validation and edit rules, and reducing the amount of UI-specific code needed
 * to build forms, tables, and other interactive components.
 * </p>
 */
package com.viaoa.ui.controller;