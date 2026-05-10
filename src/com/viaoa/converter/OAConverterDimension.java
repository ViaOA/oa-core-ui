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
package com.viaoa.converter;

import java.awt.*;

/**
 * Converter for transforming values into {@link Dimension} objects and
 * formatting them into display-friendly {@link String} values.
 *
 * <h3>Conversion Rules</h3>
 * Supported input types when converting to {@code Dimension}:
 * <ul>
 *     <li>{@code null} → {@code null}</li>
 *     <li>{@link Dimension} — returned directly</li>
 *     <li>{@link Number} — interpreted as a packed width/height value:
 *         <ul>
 *             <li>Upper 16 bits: signed width</li>
 *             <li>Lower 16 bits: signed height</li>
 *         </ul>
 *     </li>
 *     <li>{@link String} — format: <code>"width,height"</code>
 *         <ul>
 *             <li>Whitespace is ignored</li>
 *             <li>Invalid input returns {@code null}</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h3>Formatting Rules</h3>
 * <ul>
 *     <li>If {@code fromValue} is {@code null}, returns {@code ""}</li>
 *     <li>Format ignores {@code fmt}; always returns <code>"width,height"</code></li>
 * </ul>
 *
 * <p>This converter provides a simple and reliable mapping between UI-friendly
 * <code>"w,h"</code> strings, compact numeric storage formats, and
 * {@link Dimension} objects used by Java components.</p>
 *
 * @see OAConverterInterface
 * @see Dimension
 */
public class OAConverterDimension implements OAConverterInterface<Dimension> {

    /**
     * Converts the supplied value into a {@link Dimension}.
     *
     * @param thisClass expected type (always {@code Dimension.class})
     * @param fromValue source value to convert; may be {@code null}
     * @param fmt ignored for this converter
     * @return a converted {@link Dimension}, or {@code null} when conversion is not possible
     */
	public Dimension convert(Class thisClass, Object fromValue, String fmt) {
        if (fromValue == null) return null;
        if (fromValue instanceof Dimension) return (Dimension) fromValue;
        if (fromValue instanceof Number) {
            long l = ((Number)fromValue).longValue();
            short w = (short) ((l >> 16) & 0xFFFF);
            short h = (short) (l & 0xFFFF);
            Dimension d = new Dimension(w, h);
            return d;
        }
        if (fromValue instanceof String) {
            String s = ((String) fromValue).trim();
            if (s.isEmpty()) return null;

            String[] parts = s.split(",");
            if (parts.length != 2) return null;
            try {
                int w = Integer.parseInt(parts[0].trim());
                int h = Integer.parseInt(parts[1].trim());
                return new Dimension(w, h);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Converts a {@link Dimension} into a <code>"width,height"</code> formatted string.
     *
     * @param fromValue the value to convert; may be {@code null}
     * @param fmt ignored for this converter
     * @return formatted <code>"width,height"</code> or {@code ""} if {@code fromValue} is null
     */	
	@Override
	public String convertToString(Dimension fromValue, String fmt) {
		if (fromValue == null) return "";
        return fromValue.width+","+ fromValue.height;
    }
}
