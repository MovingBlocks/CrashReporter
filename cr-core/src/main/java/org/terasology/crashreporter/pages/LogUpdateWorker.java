/*
 * Copyright 2017 MovingBlocks
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
package org.terasology.crashreporter.pages;

import com.sun.nio.file.SensitivityWatchEventModifier;

import javax.swing.SwingWorker;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.StandardWatchEventKinds;

import java.util.List;

/**
 * LogUpdateWorker watches log folder change.
 * It watches change in the background thread, see {@code doInBackground} method.
 * It processes event in EDT thread, see {@code process} method.
 */
public class LogUpdateWorker extends SwingWorker<Void, WatchEvent<Path>> {

    public static final String CREATED = "CREATE_LOG";
    public static final String MODIFIED = "MODIFIED_LOG";

    private Path directory;
    private WatchService watchService;

    public LogUpdateWorker(Path directory) {
        try {
            this.directory = directory;
            this.watchService = FileSystems.getDefault().newWatchService();
            directory.register(watchService, new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE}, SensitivityWatchEventModifier.HIGH);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Void doInBackground() throws Exception {
        for (;;) {
            // wait for key to be signalled
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                publish((WatchEvent<Path>) event);
            }

            // reset key return if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
        return null;
    }

    @Override
    protected void process(List<WatchEvent<Path>> chunks) {
        super.process(chunks);
        for (WatchEvent<Path> event : chunks) {
            WatchEvent.Kind<?> kind = event.kind();
            Path fileName = event.context();
            Path fileResolvedName = directory.resolve(fileName);
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                firePropertyChange(CREATED, null, fileResolvedName);
            } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                firePropertyChange(MODIFIED, null, fileResolvedName);
            }
        }
    }
}
