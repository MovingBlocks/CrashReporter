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

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

/**
 * An interactive test using Mockito
 * A few test cases for manual (not JUnit) inspection
 */
@SuppressWarnings("unused")
public final class InteractiveTestCases {

    private static final Logger logger = LoggerFactory.getLogger(InteractiveTestCases.class);

    private InteractiveTestCases() {
        // no instances
    }

    public static void main(String[] args) throws Exception {
        System.out.println("ARGS: " + Arrays.toString(args));

        String methodName = args.length > 0 ? args[0] : "setupForSingleException";
        logger.info("Method: {}", methodName);

        String logFileName = args.length > 1 ? args[1] : "details.log";
        logger.info("Log file: {}", logFileName);

        Locale locale = args.length > 2 ? new Locale(args[2]) : Locale.getDefault();
        logger.info("Log file: {}", locale);

        Locale.setDefault(locale);

        try (MyEngine engine = Mockito.mock(MyEngine.class)) {
            Method method = InteractiveTestCases.class.getDeclaredMethod(methodName, MyEngine.class);
            method.invoke(null, engine);

            engine.init();
            engine.run();
        }
        catch (RuntimeException e) {
            logger.warn("An exception occurred", e);

            if (!GraphicsEnvironment.isHeadless()) {
                Path logPath = Paths.get(".");
                CrashReporter.report(e, logPath,true);
            }
        }
    }


    private static void setupForExtraLongMessageException(MyEngine engine) throws Exception {
        String text =
                "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor " +
                "invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam " +
                "et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus";

        Mockito.doThrow(new RuntimeException(text)).when(engine).run();
    }

    private static void setupForSingleException(MyEngine engine) throws Exception {
        Mockito.doThrow(new RuntimeException("In run()")).when(engine).run();
    }

    private static void setupForSuppressException(MyEngine engine) throws Exception {
        Mockito.doThrow(new RuntimeException("In run()")).when(engine).run();
        Mockito.doThrow(new RuntimeException("AND in dispose()")).when(engine).close();
    }
}
