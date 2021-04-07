/*
 * This file is part of Vestige.
 *
 * Vestige is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Vestige is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Vestige.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.gaellalire.vestige.plugin.verify;

import java.io.File;
import java.util.Properties;

/**
 * @author Gael Lalire
 */
public final class PropertyExpander {

    private PropertyExpander() {
    }

    public static String expand(final String value, final Properties properties) {

        int p = value.indexOf("${", 0);

        // no special characters
        if (p == -1) {
            return value;
        }

        StringBuffer sb = new StringBuffer(value.length());
        int max = value.length();
        int i = 0; // index of last character we copied

        scanner: while (p < max) {
            if (p > i) {
                // copy in anything before the special stuff
                sb.append(value.substring(i, p));
                i = p;
            }
            int pe = p + 2;

            // do not expand ${{ ... }}
            if (pe < max && value.charAt(pe) == '{') {
                pe = value.indexOf("}}", pe);
                if (pe == -1 || pe + 2 == max) {
                    // append remaining chars
                    sb.append(value.substring(p));
                    break scanner;
                } else {
                    // append as normal text
                    pe++;
                    sb.append(value.substring(p, pe + 1));
                }
            } else {
                String dval = null;
                char lv = value.charAt(pe);
                while ((pe < max) && (lv != '}') && lv != ':') {
                    pe++;
                    lv = value.charAt(pe);
                }
                if (pe == max) {
                    // no matching '}' found, just add in as normal text
                    sb.append(value.substring(p, pe));
                    break scanner;
                }
                String prop = value.substring(p + 2, pe);
                if (lv == ':') {
                    pe++;
                    lv = value.charAt(pe);
                    if (lv == '-') {
                        int spe = pe + 1;
                        do {
                            pe++;
                            lv = value.charAt(pe);
                        } while ((pe < max) && (lv != '}'));
                        dval = value.substring(spe, pe);
                    }
                }
                if (prop.equals("/")) {
                    sb.append(File.separatorChar);
                } else {
                    String val = properties.getProperty(prop);
                    if (val == null) {
                        val = System.getProperty(prop);
                        if (val == null) {
                            val = System.getenv(prop);
                            if (val == null) {
                                if (dval != null) {
                                    sb.append(dval);
                                } else {
                                    throw new RuntimeException("unable to expand property " + prop);
                                }
                            }
                        }
                    }
                    if (val != null) {
                        sb.append(val);
                    }
                }
            }
            i = pe + 1;
            p = value.indexOf("${", i);
            if (p == -1) {
                // no more to expand. copy in any extra
                if (i < max) {
                    sb.append(value.substring(i, max));
                }
                // break out of loop
                break scanner;
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

}
