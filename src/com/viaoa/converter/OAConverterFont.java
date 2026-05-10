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

import java.awt.Font;

/**
 * Converter for transforming values into {@link Font} objects and formatting
 * them into a canonical, decodable {@link String} representation.
 *
 * <h3>Conversion Rules</h3>
 * Supported input types when converting to {@link Font}:
 * <ul>
 *   <li>{@code null} → {@code null}</li>
 *   <li>{@link Font} — returned directly</li>
 *   <li>{@link String} — parsed using {@link Font#decode(String)} with format:
 *       <code>"name-style-size"</code>
 *       <ul>
 *         <li>Whitespace trimmed</li>
 *         <li>Case-insensitive style (plain|bold|italic|bolditalic)</li>
 *         <li>Invalid inputs return {@code null}</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>Formatting Rules</h3>
 * <ul>
 *   <li>If {@code font} is {@code null}, returns an empty string {@code ""}</li>
 *   <li>{@code fmt} is ignored; output always:
 *       <code>"name-style-size"</code>
 *   </li>
 *   <li>Generates a string accepted by {@link Font#decode(String)} to enable
 *       clean round-trip conversions</li>
 * </ul>
 *
 * <p>This converter completes the two-way conversion support that Java's
 * {@link Font} API lacks — providing a safe encoding format corresponding to the
 * existing {@link Font#decode(String)} decoder.</p>
 *
 * @see OAConverterInterface
 * @see Font
 */
public class OAConverterFont implements OAConverterInterface<Font> {

    /**
     * Converts the supplied value into a {@link Font}.
     *
     * @param thisClass expected result type ({@code Font.class})
     * @param fromValue source value to convert; may be {@code null}
     * @param fmt ignored for this converter
     * @return converted {@link Font} or {@code null} when value cannot be parsed
     */
    @Override
    public Font convert(Class<Font> thisClass, Object fromValue, String fmt) {
        if (fromValue == null) return null;
        if (fromValue instanceof Font) return (Font) fromValue;

        if (fromValue instanceof String) {
            String s = ((String) fromValue).trim();
            if (s.isEmpty()) return null;
            try {
                return Font.decode(s);
            }
            catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Converts a {@link Font} into a canonical, decodable format.
     * <p>Format is <code>"name-style-size"</code>, where:
     * <ul>
     *   <li>{@code name} = font family</li>
     *   <li>{@code style} = plain, bold, italic, or bolditalic</li>
     *   <li>{@code size} = point size</li>
     * </ul>
     *
     * @param fromValue font value to convert; may be {@code null}
     * @param fmt ignored for this converter
     * @return decodable {@link String} or {@code ""} for {@code null}
     */
    @Override
    public String convertToString(Font fromValue, String fmt) {
        if (fromValue == null) return "";

        final int style = fromValue.getStyle();
        final boolean bold = (style & Font.BOLD) != 0;
        final boolean italic = (style & Font.ITALIC) != 0;

        final String styleStr = (bold && italic) ? "bolditalic"
                                 : bold ? "bold"
                                 : italic ? "italic"
                                 : "plain";

        return fromValue.getName() + "-" + styleStr + "-" + fromValue.getSize();
    }
}
