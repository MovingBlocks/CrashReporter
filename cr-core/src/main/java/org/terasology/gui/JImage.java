// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.gui;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * Displays an image (and a background color)
 */
public class JImage extends JComponent {

    private static final long serialVersionUID = -3761506251509784400L;

    private BufferedImage image;

    /**
     * @param image the image to display
     */
    public JImage(BufferedImage image) {
        this.image = image;
        setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(getBackground());
        g.fillRect(image.getWidth(), 0, getWidth(), getHeight());
        g.fillRect(0, image.getHeight(), image.getWidth(), getHeight());
        g.drawImage(image, 0, 0, null);
    }

}
