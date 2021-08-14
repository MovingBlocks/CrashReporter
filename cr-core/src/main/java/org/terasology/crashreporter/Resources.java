// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

/**
 * Provides version info based on a "versionInfo.properties" file.
 */
public final class Resources {

    private Resources() {
        // no instances
    }

    /**
     * @param fname the absolute path in the jar/project
     * @return the buffered image, wrapped in an Icon
     */
    public static BufferedImage loadImage(String fname) {
        try {
            String fullPath = "/" + fname;
            URL rsc = Resources.class.getResource(fullPath);
            if (rsc == null) {
                throw new FileNotFoundException(fullPath);
            }
            return ImageIO.read(rsc);
        } catch (IOException e) {
            System.err.println(e.toString());
            return createDummyImage(64, 64, "?");
        }
    }

    private static BufferedImage createDummyImage(int w, int h, String text) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        float[] dash = new float[] {5, 5};
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0));
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, w - 1, h - 1);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g.drawString(text, (w - textWidth) / 2, (h + fm.getAscent()) / 2);
        g.dispose();
        return img;
    }

    /**
     * @param fname the absolute path in the jar/project
     * @return the buffered image, wrapped in an Icon
     */
    public static Icon loadIcon(String fname) {
        return new ImageIcon(loadImage(fname));
    }

    /**
     * @return the version string
     */
    public static String getVersion()     {
        String fname = "versionInfo.properties";
        URL location = Resources.class.getResource(fname);

        if (location == null) {
            return "";
        }

        try (InputStream is = location.openStream()) {
            Properties props = new Properties();
            props.load(is);
            return props.getProperty("displayVersion", "");
        } catch (IOException e) {
            System.err.println("Error reading version info " + fname);
            e.printStackTrace();
            return "";
        }
    }
}
