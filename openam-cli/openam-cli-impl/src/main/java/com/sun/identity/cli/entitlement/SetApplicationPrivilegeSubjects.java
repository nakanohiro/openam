/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009 Sun Microsystems Inc. All Rights Reserved
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
 * $Id: SetApplicationPrivilegeSubjects.java,v 1.1 2009/11/10 19:01:04 veiming Exp $
 *
 * Portions Copyrighted 2015 ForgeRock AS.
 */

package com.sun.identity.cli.entitlement;

import com.sun.identity.cli.CLIException;
import com.sun.identity.cli.ExitCodes;
import com.sun.identity.cli.IArgument;
import com.sun.identity.cli.LogWriter;
import com.sun.identity.cli.RequestContext;
import com.sun.identity.entitlement.ApplicationPrivilege;
import com.sun.identity.entitlement.ApplicationPrivilegeManager;
import com.sun.identity.entitlement.EntitlementException;
import com.sun.identity.entitlement.SubjectImplementation;
import com.sun.identity.entitlement.opensso.SubjectUtils;
import org.forgerock.openam.entitlement.service.ResourceTypeService;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.inject.Inject;
import javax.security.auth.Subject;

/**
 *
 * @author dennis
 */
public class SetApplicationPrivilegeSubjects extends ApplicationPrivilegeBase {

    @Inject
    public SetApplicationPrivilegeSubjects(ResourceTypeService resourceTypeService) {
        super(resourceTypeService);
    }

    /**
     * Services a Commandline Request.
     *
     * @param rc Request Context.
     * @throws CLIException if the request cannot serviced.
     */
    @Override
    public void handleRequest(RequestContext rc)
        throws CLIException {
        super.handleRequest(rc);
        String realm = getStringOptionValue(IArgument.REALM_NAME);
        String name = getStringOptionValue(PARAM_NAME);
        String[] params = {realm, name};
        Set<SubjectImplementation> newSubjects = getSubjects(rc);
        boolean bAdd = isOptionSet(PARAM_ADD);

        Subject userSubject = SubjectUtils.createSubject(
            getAdminSSOToken());
        ApplicationPrivilegeManager apm =
            ApplicationPrivilegeManager.getInstance(realm, userSubject);
        writeLog(LogWriter.LOG_ACCESS, Level.INFO,
            "ATTEMPT_UPDATE_APPLICATION_PRIVILEGE", params);

        try {
            ApplicationPrivilege appPrivilege = apm.getPrivilege(name);
            Set<SubjectImplementation> origSubjects =
                appPrivilege.getSubjects();
            Set<SubjectImplementation> subjects = (bAdd) ?
                mergeSubjects(origSubjects, newSubjects) : newSubjects;
            appPrivilege.setSubject(subjects);
            apm.replacePrivilege(appPrivilege);

            Object[] msgParam = {name};
            getOutputWriter().printlnMessage(MessageFormat.format(
                getResourceString("update-application-privilege-succeeded"),
                msgParam));
            writeLog(LogWriter.LOG_ACCESS, Level.INFO,
                "SUCCEEDED_UPDATE_APPLICATION_PRIVILEGE", params);
        } catch (EntitlementException ex) {
            String[] paramExs = {realm, name, ex.getMessage()};
            writeLog(LogWriter.LOG_ACCESS, Level.INFO,
                "FAILED_UPDATE_APPLICATION_PRIVILEGE", paramExs);
            throw new CLIException(ex, ExitCodes.REQUEST_CANNOT_BE_PROCESSED);
        }
    }

    private Set<SubjectImplementation> mergeSubjects(
        Set<SubjectImplementation> subjects1,
        Set<SubjectImplementation> subjects2
    ) {
        Set<SubjectImplementation> subjects = new
            HashSet<SubjectImplementation>();
        subjects.addAll(subjects1);
        subjects.addAll(subjects2);
        return subjects;
    }

}
