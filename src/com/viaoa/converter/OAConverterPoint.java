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
 * Converter for transforming values into {@link Point} objects and formatting
 * them into display-friendly {@link String} values.
 *
 * <h3>Conversion Rules</h3>
 * Supported input types when converting to {@link Point}:
 * <ul>
 *     <li>{@code null} → {@code null}</li>
 *     <li>{@link Point} — returned directly</li>
 *     <li>{@link Number} — interpreted as a packed value of <code>x</code> and
 *         <code>y</code> coordinates:
 *         <ul>
 *             <li>Upper 16 bits = signed {@code x}</li>
 *             <li>Lower 16 bits = signed {@code y}</li>
 *         </ul>
 *     </li>
 *     <li>{@link String} — expected format: <code>"x,y"</code>
 *         <ul>
 *             <li>Whitespace ignored</li>
 *             <li>Invalid formats return {@code null}</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h3>Formatting Rules</h3>
 * <ul>
 *     <li>If {@code fromValue} is {@code null}, returns {@code ""}</li>
 *     <li>Format ignores {@code fmt}; always returns <code>"x,y"</code></li>
 * </ul>
 *
 * <p>This converter provides a safe and consistent method of representing
 * simple two-dimensional coordinates in UI and data storage.</p>
 *
 * @see OAConverterInterface
 * @see Point
 */
public class OAConverterPoint implements OAConverterInterface<Point> {

	
    /**
     * Converts the supplied value into a {@link Point}.
     *
     * @param thisClass expected type (always {@code Point.class})
     * @param fromValue value to convert; may be {@code null}
     * @param fmt ignored for this converter
     * @return a converted {@link Point} instance, or {@code null} when
     *         conversion is not possible
     */	
	@Override
	public Point convert(Class<Point> thisClass, Object fromValue, String fmt) {
        if (fromValue == null) return null;
        if (fromValue instanceof Point) return (Point) fromValue;
        if (fromValue instanceof Number) {
            long l = ((Number)fromValue).longValue();
            short x = (short) ((l >> 16) & 0xFFFF);
            short y = (short) (l & 0xFFFF);
            return new Point(x, y);            
        }
        
        if (fromValue instanceof String) {
            String s = ((String) fromValue).trim();
            if (s.isEmpty()) return null;

            String[] parts = s.split(",");
            if (parts.length != 2) return null;
            try {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                return new Point(x, y);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Converts a {@link Point} into an <code>"x,y"</code> text format.
     *
     * @param fromValue the value to convert; may be {@code null}
     * @param fmt ignored for this converter
     * @return formatted <code>"x,y"</code> or {@code ""} if {@code fromValue} is null
     */
	@Override
	public String convertToString(Point fromValue, String fmt) {
		if (fromValue == null) return "";
        return fromValue.x+","+ fromValue.y;
    }

}
