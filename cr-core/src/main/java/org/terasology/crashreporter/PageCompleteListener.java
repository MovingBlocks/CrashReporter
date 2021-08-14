// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter;

import javax.swing.JComponent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Activates/deactivates the "next" button depending on property changed events.
 */
class PageCompleteListener implements PropertyChangeListener {

    private final JComponent nextButton;

    /**
     * @param nextButton the "next" button
     */
    PageCompleteListener(JComponent nextButton) {
        this.nextButton = nextButton;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        nextButton.setEnabled(evt.getNewValue().equals(Boolean.TRUE));
    }

}
