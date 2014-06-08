/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.crashreporter;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Internationalization support
 * @author Martin Steiger
 */
public final class I18N {

    private static final String MESSAGE_BUNDLE = "i18n.MessagesBundle";

    private I18N() {
    }

    public static String getMessage(String key) {
        Locale locale = Locale.getDefault();
        try {
            return ResourceBundle.getBundle(MESSAGE_BUNDLE, locale).getString(key);
        } catch (MissingResourceException e) {
            System.err.println("Missing message translation! key=" + key + ", locale=" + locale);
            return "$" + key + "$";
        }
    }

    public static String getMessage(String key, Object ... arguments) {
        Locale locale = Locale.getDefault();
        String pattern;
        try {
            pattern = ResourceBundle.getBundle(MESSAGE_BUNDLE, locale).getString(key);
            final MessageFormat messageFormat = new MessageFormat(pattern, locale);
            return messageFormat.format(arguments, new StringBuffer(), null).toString();
        } catch (MissingResourceException e) {
            System.err.println("Missing message translation! key=" + key + ", locale=" + locale);
            return "$" + key + "$";
        }
    }
}
