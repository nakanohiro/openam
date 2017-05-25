/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.openam.cts.utils;

import org.testng.annotations.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.forgerock.openam.utils.Time.*;
import static org.testng.Assert.assertEquals;

public class LDAPDataConversionTest {
    // Reference time zones useful for testing.
    public final static TimeZone CHICAGO = TimeZone.getTimeZone("America/Chicago");
    public final static TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");
    public final static TimeZone MALDIVES = TimeZone.getTimeZone("Indian/Maldives");

    @Test
    public void shouldConvertTimeToCalendarAndBackAgain() {
        // Given
        LDAPDataConversion conversion = new LDAPDataConversion();

        Calendar calendar = getCalendarInstance();
        calendar.setTimeZone(MALDIVES);
        calendar.setTimeInMillis(currentTimeMillis());

        // When
        Calendar result = conversion.fromLDAPDate(conversion.toLDAPDate(calendar));

        // Then
        assertEquals(result.getTimeInMillis(), calendar.getTimeInMillis());
        assertEquals(result.getTimeZone().getRawOffset(), calendar.getTimeZone().getRawOffset());
    }
}
