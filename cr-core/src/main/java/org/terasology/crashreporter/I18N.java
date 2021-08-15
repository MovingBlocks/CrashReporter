// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Internationalization support
 */
public final class I18N {

    private static final String MESSAGE_BUNDLE = "i18n.MessagesBundle";

    private I18N() {
    }

    /**
     * @param key the messsage key
     * @return the localized message
     */
    public static String getMessage(String key) {
        Locale locale = Locale.getDefault();
        try {
            return ResourceBundle.getBundle(MESSAGE_BUNDLE, locale).getString(key);
        } catch (MissingResourceException e) {
            System.err.println("Missing message translation! key=" + key + ", locale=" + locale);
            return "$" + key + "$";
        }
    }

    /**
     * @param key the message key
     * @param arguments a number of arguments that will be inserted according to the string format specifiers
     * @return the formatted localized text string
     */
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
