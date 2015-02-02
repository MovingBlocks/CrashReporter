/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.crashreporter;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;

/**
 * Activates/deactivates the "next" button depending on property changed events.
 * @author Martin Steiger
 */
class PageCompleteListener implements PropertyChangeListener {

    private final JComponent nextButton;

    /**
     * @param nextButton the "next" button
     */
    public PageCompleteListener(JComponent nextButton) {
        this.nextButton = nextButton;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        nextButton.setEnabled(evt.getNewValue().equals(Boolean.TRUE));
    }

}
