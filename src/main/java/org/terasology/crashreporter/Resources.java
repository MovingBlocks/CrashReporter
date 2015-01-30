/*
 * Copyright 2015 MovingBlocks
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

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Provides version info based on a "versionInfo.properties" file.
 * @author Martin Steiger
 */
public class Resources
{
    public static Icon loadIcon(String fname) {
        try {
            String fullPath = "/" + fname;
            URL rsc = Resources.class.getResource(fullPath);
            if (rsc == null) {
                throw new FileNotFoundException(fullPath);
            }
            BufferedImage image = ImageIO.read(rsc);
            return new ImageIcon(image);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return null;
        }
    }
    
    /**
     * @return the version string
     */
    public static String getVersion()
    {
        String fname = "versionInfo.properties";
        URL location = Resources.class.getResource(fname);

        if (location == null)
            return "";

        try (InputStream is = location.openStream())
        {
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