/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007 Sun Microsystems Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * https://opensso.dev.java.net/public/CDDLv1.0.html or
 * opensso/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at opensso/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * $Id: SystemTimer.java,v 1.4 2008/08/08 00:40:59 ww203982 Exp $
 *
 * Portions Copyrighted 2012-2016 ForgeRock AS.
 */
package com.sun.identity.common;

import com.sun.identity.shared.debug.Debug;
import org.forgerock.util.thread.listener.ShutdownListener;

/**
 * SystemTimer is a TimerPool which has only 2 Timer shared in the system.
 */

public class SystemTimer {

    /** The name of the {@link TimerPool} instance. */
    public static final String TIMER_NAME = "SystemTimer";
    /** The name of the {@link TimerPool} scheduler. */
    public static final String SCHEDULER_NAME = TIMER_NAME + TimerPool.SCHEDULER_SUFFIX;
    protected static TimerPool instance;
    
    /**
     * Create and return the system timer.
     */
    
    public static synchronized TimerPool getTimer() {
        if (instance == null) {
            ShutdownManager shutdownMan = ShutdownManager.getInstance();
            // Don't load the Debug object in static block as it can
            // cause issues when doing a container restart.
            instance = new TimerPool(TIMER_NAME, 1, false, Debug.getInstance("SystemTimer"));
            shutdownMan.addShutdownListener(new ShutdownListener() {
                public void shutdown() {
                    instance.shutdown();
                    instance = null;
                }
            });
        }
        return instance;
    }
}
