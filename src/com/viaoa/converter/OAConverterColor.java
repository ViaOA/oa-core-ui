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

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import com.viaoa.lang.OAString;

/**
 * Converter for transforming values into {@link Color} objects and formatting
 * them into HEX {@link String} representations.
 *
 * <h3>Conversion Rules</h3>
 * Supported input types when converting to {@link Color}:
 * <ul>
 *     <li>{@code null} → {@code null}</li>
 *     <li>{@link Color} — returned directly</li>
 *     <li>{@link Number} — interpreted as ARGB integer (alpha preserved)</li>
 *     <li>{@link Character} — char's integer value interpreted as ARGB</li>
 *     <li>{@link String} — supported formats:
 *         <ul>
 *             <li>Named colors (lowercase lookup): <code>"red"</code>, <code>"cyan"</code>, etc.</li>
 *             <li>CSS syntax: <code>rgb(r,g,b[,a])</code>
 *                 <ul>
 *                     <li>0–255 range enforced for each channel</li>
 *                     <li>Alpha defaults to 255 when omitted</li>
 *                 </ul>
 *             </li>
 *             <li>Hex values via {@link Color#decode(String)}:
 *                 <pre>#RRGGBB, 0xRRGGBB</pre>
 *             </li>
 *         </ul>
 *     </li>
 *     <li>{@link byte[]} — interpreted as packed ARGB integer</li>
 * </ul>
 *
 * <h3>Formatting Rules</h3>
 * <ul>
 *     <li>If {@code fromValue} is {@code null}, returns {@code ""}</li>
 *     <li>{@code fmt} is ignored; output is always:
 *         <code>#RRGGBB</code> (uppercase)
 *     </li>
 * </ul>
 *
 * <p>This converter provides a robust interpretation of multiple color
 * encodings and UI-friendly canonical output.</p>
 *
 * @see OAConverterInterface
 * @see Color
 */
public class OAConverterColor implements OAConverterInterface<Color> {

	/**
	 * Lookup table of predefined color names mapped to {@link Color} instances.
	 * <p>
	 * Keys are stored in lowercase and used for string-based color conversion.
	 */
	private static final Map<String, Color> hmColor = new HashMap<>();
	
	
	static { // lowercase
		hmColor.put("black", Color.black);
		hmColor.put("blue", Color.blue);
		hmColor.put("cyan", Color.cyan);
		hmColor.put("darkgray", Color.darkGray);
		hmColor.put("gray", Color.gray);
		hmColor.put("lightgray", Color.lightGray);
		hmColor.put("magenta", Color.magenta);
		hmColor.put("orange", Color.orange);
		hmColor.put("pink", Color.pink);
		hmColor.put("red", Color.red);
		hmColor.put("white", Color.white);
		hmColor.put("yellow", Color.yellow);
	}
	
    /**
     * Converts the supplied value into a {@link Color}.
     *
     * @param thisClass expected result type ({@code Color.class})
     * @param fromValue value to convert; may be {@code null}
     * @param fmt ignored
     * @return a converted {@link Color}, or {@code null} when conversion is not possible
     */	
	@Override
	public Color convert(Class<Color> thisClass, Object fromValue, String fmt) {
        if (fromValue == null) return null;
        if (fromValue instanceof Color) return (Color) fromValue;

        if (fromValue instanceof Number) {
            return new Color(((Number) fromValue).intValue(), true);            
        }

        if (fromValue instanceof Character) {
            return new Color( (int) (((Character) fromValue).charValue()), true );
        }

        if (fromValue instanceof String) {
        	String sValue = ((String) fromValue).trim().toLowerCase();
        	if (sValue.isEmpty()) return null;            
        	
            // rgb(r, g, b, a)
            if (sValue.startsWith("rgb(") && sValue.endsWith(")")) {
            	String s = sValue;
                s = s.substring(4, s.length()-1);
                String[] ss = s.split(",");
                try {
                	int x = ss.length;
                    int r = x > 0 ? Integer.valueOf(ss[0].trim()) : 0;
                    int g = x > 1 ? Integer.valueOf(ss[1].trim()) : 0;
                    int b = x > 2 ? Integer.valueOf(ss[2].trim()) : 0;
                    int a = x > 3 ? Integer.valueOf(ss[3].trim()) : 255;
                    
                    if (r < 0 || r > 255 || g < 0 || g > 255
                    	    || b < 0 || b > 255 || a < 0 || a > 255) return null;
                    
                    Color color = new Color(r, g, b, a);
                    return color;
                }
                catch (Exception e) {
                	return null;
                }
            }

            Color color = hmColor.get(sValue);
            if (color != null) return color;

            try {
                return Color.decode(sValue);
            }
            catch (Exception e) {
            }
        	return null;
        }

        if (fromValue instanceof byte[]) {
			return new Color(new java.math.BigInteger((byte[]) fromValue).intValue(), true);
    	}
        
        return null;
    }

	
    /**
     * Converts a {@link Color} into a HEX {@link String} format.
     *
     * @param fromValue the color to convert; may be {@code null}
     * @param fmt ignored
     * @return uppercase HEX representation <code>#RRGGBB</code>, or {@code ""} for {@code null}
     */
	@Override
	public String convertToString(Color fromValue, String fmt) {
		if (fromValue == null) return "";
        return OAString.colorToHex(fromValue).toUpperCase();
	}

}
