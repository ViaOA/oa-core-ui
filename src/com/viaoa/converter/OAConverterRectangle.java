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

import java.awt.Rectangle;

/**
 * Converter for transforming values into {@link Rectangle} objects and
 * formatting them into display-friendly {@link String} representations.
 *
 * <h3>Conversion Rules</h3>
 * Supported input types when converting to {@code Rectangle}:
 * <ul>
 *     <li>{@code null} → {@code null}</li>
 *     <li>{@link Rectangle} — returned directly</li>
 *     <li>{@link Number} — interpreted as encoded rectangle definition:
 *         <ul>
 *             <li>Bits 63–48: signed {@code x}</li>
 *             <li>Bits 47–32: signed {@code y}</li>
 *             <li>Bits 31–16: signed {@code width}</li>
 *             <li>Bits 15–0: signed {@code height}</li>
 *         </ul>
 *     </li>
 *     <li>{@link String} — expected format:
 *         <code>"x,y,width,height"</code>
 *         <ul>
 *             <li>Whitespace trimmed</li>
 *             <li>Invalid input returns {@code null}</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h3>Formatting Rules</h3>
 * <ul>
 *     <li>If {@code rect} is {@code null}, returns {@code ""}</li>
 *     <li>{@code fmt} is ignored; format always {@code "x,y,width,height"}</li>
 * </ul>
 *
 * <p>This converter enables compact storage and UI-friendly text representation
 * of {@link Rectangle} coordinates and dimensions.</p>
 *
 * @see OAConverterInterface
 * @see Rectangle
 */
public class OAConverterRectangle implements OAConverterInterface<Rectangle> {

    /**
     * Converts a supplied value into a {@link Rectangle}.
     *
     * @param thisClass expected result type (always {@code Rectangle.class})
     * @param fromValue source value to convert; may be {@code null}
     * @param fmt ignored for this converter
     * @return converted {@link Rectangle}, or {@code null} if conversion is not possible
     */	
	@Override
	public Rectangle convert(Class<Rectangle> thisClass, Object fromValue, String fmt) {
        if (fromValue == null) return null;
        if (fromValue instanceof Rectangle) return (Rectangle) fromValue;
        if (fromValue instanceof Number) {
            long l = ((Number)fromValue).longValue();
            short x = (short) ((l >> 48) & 0xFFFF);
            short y = (short) ((l >> 32) & 0xFFFF);
            short w = (short) ((l >> 16) & 0xFFFF);
            short h = (short) (l & 0xFFFF);

            Rectangle r = new Rectangle(x, y, w, h);
            return r;
        }
        if (fromValue instanceof String) {
            String s = ((String) fromValue).trim();
            if (s.isEmpty()) return null;
            Rectangle r = new Rectangle();

            String[] parts = s.split(",");
            if (parts.length != 4) return null;
            try {
                r.x = Integer.parseInt(parts[0].trim());
                r.y = Integer.parseInt(parts[1].trim());
                r.width = Integer.parseInt(parts[2].trim());
                r.height = Integer.parseInt(parts[3].trim());
            } catch (Exception e) {
                return null;
            }
            return r;
        }
        return null;
    }

    /**
     * Converts a {@link Rectangle} into a compact <code>"x,y,width,height"</code> text format.
     *
     * @param rect the rectangle to convert; may be {@code null}
     * @param fmt ignored for this converter
     * @return formatted rectangle string or {@code ""} if {@code rect} is null
     */	
	@Override
	public String convertToString(Rectangle rect, String fmt) {
		if (rect == null) return "";
        return rect.x+","+ rect.y+","+rect.width+","+rect.height;
	}
}
