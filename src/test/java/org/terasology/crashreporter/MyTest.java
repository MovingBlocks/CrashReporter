/*
 * Copyright 2014 MovingBlocks
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

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO Type description
 * @author Martin Steiger
 */
public class MyTest {
    
    private static final Logger logger = LoggerFactory.getLogger(MyTest.class);
    
    public static void main(String[] args) {
        MyEngine engine = Mockito.mock(MyEngine.class);
        
        logger.info("Important information");
        
        tryToCatch(engine);
    }
    
    
    private static void tryToCatch(MyEngine engine) {
        try {
            try {
                engine.init();
                engine.run();
            } finally {
                try {
                    engine.dispose();
                } catch (Exception e) {
                    // Just log this one to System.err because we don't want it 
                    // to replace the one that came first (thrown above).
                    e.printStackTrace();
                }
            }
        } catch (RuntimeException e) {

            if (!GraphicsEnvironment.isHeadless()) {
                Path logPath = Paths.get(".");
                Path logFile = logPath.resolve("details.log");
                CrashReporter.report(e, logFile);
            }
        }
    }
}
