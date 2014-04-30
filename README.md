#CrashReporter
**A crash reporting tool**

![image1](images/2014-04-26_crashreporter.png "The main panel")


### Log files

You can get your log file name in a slf4j / Logback configuration with this snippet:

```java
private static String getLogFilename() {
    
	String logFile = null;
    org.slf4j.Logger logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    if (logger instanceof Logger) {
        Logger logbackLogger = (Logger) logger;
        Iterator<Appender<ILoggingEvent>> it = logbackLogger.iteratorForAppenders();
        while (it.hasNext()) {
            Appender<ILoggingEvent> app = it.next();
        
            if (app instanceof FileAppender) {
                FileAppender<ILoggingEvent> fileApp = (FileAppender<ILoggingEvent>) app;
                if (logFile == null) {
                    logFile = fileApp.getFile();
                } else {
                    System.err.println("Multiple log files found!");
                }
            }
        }
    } else {
        System.err.println("Logger ist not a Logback logger, but " + logger.getClass().getName());
    }
    
    return logFile;
}    
```

### License

This module is licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).