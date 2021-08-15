// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter;

/**
 * A simple definition of a game engine
 */
public interface MyEngine extends AutoCloseable {

    void init();

    void run();
}
