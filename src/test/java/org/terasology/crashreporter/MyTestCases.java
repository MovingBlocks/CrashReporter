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
 * An interactive test using Mockito
 * A few test cases for manual (not JUnit) inspection
 * @author Martin Steiger
 */
public class MyTestCases {

    private static final Logger logger = LoggerFactory.getLogger(MyTestCases.class);

    public static void main(String[] args) throws Exception {

        suppressedException();

        logger.info("Important information");
    }


    private static void suppressedException() throws Exception {

        try (MyEngine engine = Mockito.mock(MyEngine.class))
        {
            Mockito.doThrow(new RuntimeException("In run()")).when(engine).run();
            Mockito.doThrow(new RuntimeException("In dispose()")).when(engine).close();

            engine.init();
            engine.run();
        } 
        catch (RuntimeException e) {
            logger.warn("An exception occurred", e);

            if (!GraphicsEnvironment.isHeadless()) {
                Path logPath = Paths.get(".");
                Path logFile = logPath.resolve("details.log");
                CrashReporter.report(e, logFile);
            }
        }
    }
}
