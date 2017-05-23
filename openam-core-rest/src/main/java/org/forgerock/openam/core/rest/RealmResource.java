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
 * Copyright 2012-2016 ForgeRock AS.
 */

package org.forgerock.openam.core.rest;

import static com.sun.identity.sm.SMSException.STATUS_NO_PERMISSION;
import static org.forgerock.json.resource.Responses.newQueryResponse;
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.openam.rest.RestUtils.*;
import static org.forgerock.openam.utils.Time.*;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.security.AccessController;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.iplanet.sso.SSOTokenManager;
import com.sun.identity.idm.IdConstants;
import com.sun.identity.security.AdminTokenAction;
import com.sun.identity.shared.debug.Debug;
import com.sun.identity.sm.OrganizationConfigManager;
import com.sun.identity.sm.SMSException;
import org.forgerock.services.context.Context;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.ConflictException;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.ForbiddenException;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.PermanentException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.openam.forgerockrest.utils.PrincipalRestUtils;
import org.forgerock.openam.rest.RealmContext;
import org.forgerock.openam.rest.RestUtils;
import org.forgerock.openam.utils.RealmUtils;
import org.forgerock.openam.utils.StringUtils;
import org.forgerock.util.promise.Promise;

/**
 * A simple {@code Map} based collection resource provider.
 *
 * @deprecated Has been replaced by
 * {@link org.forgerock.openam.core.rest.sms.SmsRealmProvider}.
 */
@Deprecated
public class RealmResource implements CollectionResourceProvider {

    private static final String ACTIVE = "Active";
    private static final String INACTIVE = "Inactive";

    private static final Debug debug = Debug.getInstance("frRest");

    // TODO: filters, sorting, paged results.

    final private static String SERVICE_NAMES = "serviceNames";
    final private static String TOP_LEVEL_REALM = "topLevelRealm";
    final private static String FORWARD_SLASH = "/";

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ActionResponse, ResourceException> actionCollection(Context context, ActionRequest request) {
        return RestUtils.generateUnsupportedOperation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(Context context, String resourceId,
            ActionRequest request) {
        return RestUtils.generateUnsupportedOperation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> createInstance(Context context, CreateRequest request) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        String realmPath = realmContext.getResolvedRealm();

        ResourceResponse resource;
        String parentRealm;
        String childRealm;
        String realm = null;

        try {
            hasPermission(context);
            final JsonValue jVal = request.getContent();
            // get the realm
            realm = jVal.get("realm").asString();

            if (StringUtils.isBlank(realm)) {
                realm = request.getNewResourceId();
            }

            realm = checkForTopLevelRealm(realm);
            if (StringUtils.isBlank(realm)) {
                throw new BadRequestException("No realm name provided.");
            } else if (!realm.startsWith("/")) {
                realm = "/" + realm;
            }
            if (!realmPath.equalsIgnoreCase("/")) {
                // build realm to comply with format if not top level
                realm = realmPath + realm;
            }

            parentRealm = RealmUtils.getParentRealm(realm);
            childRealm = RealmUtils.getChildRealm(realm);

            OrganizationConfigManager ocm = new OrganizationConfigManager(getSSOToken(), parentRealm);

            Map defaultValues = createServicesMap(jVal);
            ocm.createSubOrganization(childRealm, defaultValues);
            String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
            debug.message("RealmResource.createInstance :: CREATE of realm " +
                    childRealm + " in realm " + parentRealm + " performed by " + principalName);

            // create a resource for handler to return
            OrganizationConfigManager realmCreated = new OrganizationConfigManager(getSSOToken(), realm);
            resource = newResourceResponse(childRealm, String.valueOf(currentTimeMillis()),
                    createJsonMessage("realmCreated", realmCreated.getOrganizationName()));
            return newResultPromise(resource);

        } catch (SMSException smse) {

            debug.error("RealmResource.createInstance() : Cannot find "
                    + realm, smse);

            try {
                configureErrorMessage(smse);
                return new BadRequestException(smse.getMessage(), smse).asPromise();
            } catch (NotFoundException nf) {
                debug.error("RealmResource.createInstance() : Cannot find "
                        + realm, nf);
                return nf.asPromise();
            } catch (ForbiddenException fe) {
                // User does not have authorization
                debug.error("RealmResource.createInstance() : Cannot CREATE "
                        + realm, fe);
                return fe.asPromise();
            } catch (PermanentException pe) {
                debug.error("RealmResource.createInstance() : Cannot CREATE "
                        + realm, pe);
                // Cannot recover from this exception
                return pe.asPromise();
            } catch (ConflictException ce) {
                debug.error("RealmResource.createInstance() : Cannot CREATE "
                        + realm, ce);
                return ce.asPromise();
            } catch (BadRequestException be) {
                debug.error("RealmResource.createInstance() : Cannot CREATE "
                        + realm, be);
                return be.asPromise();
            } catch (Exception e) {
                debug.error("RealmResource.createInstance() : Cannot CREATE "
                        + realm, e);
                return new BadRequestException(e.getMessage(), e).asPromise();
            }
        } catch (SSOException sso){
            debug.error("RealmResource.createInstance() : Cannot CREATE "
                    + realm, sso);
            return new PermanentException(401, "Access Denied", null).asPromise();
        } catch (ForbiddenException fe){
            debug.error("RealmResource.createInstance() : Cannot CREATE "
                    + realm, fe);
            return fe.asPromise();
        } catch (BadRequestException be){
            debug.error("RealmResource.createInstance() : Cannot CREATE "
                    + realm, be);
            return be.asPromise();
        } catch (PermanentException pe) {
            debug.error("RealmResource.createInstance() : Cannot CREATE "
                    + realm, pe);
            // Cannot recover from this exception
            return pe.asPromise();
        } catch (Exception e) {
            debug.error("RealmResource.createInstance()" + realm + ":" + e);
            return new BadRequestException(e.getMessage(), e).asPromise();
        }
    }

    /**
     * Returns a JsonValue containing appropriate identity details
     *
     * @param message Description of result
     * @return The JsonValue Object
     */
    private JsonValue createJsonMessage(String key, Object message) {
        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(1));
        try {
            result.put(key, message);
            return result;
        } catch (final Exception e) {
            throw new JsonValueException(result);
        }
    }

    /**
     * Returns a JsonValue containing Service Names and Values
     *
     * @param ocm          The organization configuration manager
     * @param serviceNames Names of the services available to the organization
     * @return The JsonValue Object containing attributes assigned
     *         to the services
     */
    private JsonValue serviceNamesToJSON(OrganizationConfigManager ocm, Set serviceNames) throws SMSException {
        JsonValue realmServices = new JsonValue(new LinkedHashMap<String, Object>(1));
        try {
            for (Object service : serviceNames) {
                String tmp = (String) service;
                Object holdAttrForService = ocm.getAttributes(tmp);
                realmServices.add(tmp, holdAttrForService);
            }
        } catch (SMSException e) {
            debug.error("RealmResource.serviceNamesToJSON :: " + e);
            throw e;
        }
        return realmServices;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> deleteInstance(Context context, String resourceId,
            DeleteRequest request) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        String realmPath = realmContext.getResolvedRealm();

        boolean recursive = false;
        ResourceResponse resource;
        String holdResourceId = checkForTopLevelRealm(resourceId);

        try {
            hasPermission(context);

            if (holdResourceId != null && !holdResourceId.startsWith("/")) {
                holdResourceId = "/" + holdResourceId;
            }
            if (!realmPath.equalsIgnoreCase("/")) {
                holdResourceId = realmPath + holdResourceId;
            }
            OrganizationConfigManager ocm = new OrganizationConfigManager(getSSOToken(), holdResourceId);
            ocm.deleteSubOrganization(null, recursive);
            String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
            debug.message("RealmResource.deleteInstance :: DELETE of realm " + holdResourceId + " performed by " +
                    principalName);
            // handle resource
            resource = newResourceResponse(resourceId, "0", createJsonMessage("success", "true"));
            return newResultPromise(resource);
        } catch (SMSException smse) {
            try {
                configureErrorMessage(smse);
                return new BadRequestException(smse.getMessage(), smse).asPromise();
            } catch (NotFoundException nf) {
                debug.error("RealmResource.deleteInstance() : Cannot find "
                        + resourceId + ":" + smse);
                return nf.asPromise();
            } catch (ForbiddenException fe) {
                // User does not have authorization
                debug.error("RealmResource.deleteInstance() : Cannot DELETE "
                        + resourceId + ":" + smse);
                return fe.asPromise();
            } catch (PermanentException pe) {
                debug.error("RealmResource.deleteInstance() : Cannot DELETE "
                        + resourceId + ":" + smse);
                // Cannot recover from this exception
                return pe.asPromise();
            } catch (ConflictException ce) {
                debug.error("RealmResource.deleteInstance() : Cannot DELETE "
                        + resourceId + ":" + smse);
                return ce.asPromise();
            } catch (BadRequestException be) {
                debug.error("RealmResource.deleteInstance() : Cannot DELETE "
                        + resourceId + ":" + smse);
                return be.asPromise();
            } catch (Exception e) {
                return new BadRequestException(e.getMessage(), e).asPromise();
            }
        } catch (SSOException sso){
            debug.error("RealmResource.updateInstance() : Cannot DELETE "
                    + resourceId + ":" + sso);
            return new PermanentException(401, "Access Denied", null).asPromise();
        } catch (ForbiddenException fe){
            debug.error("RealmResource.updateInstance() : Cannot DELETE "
                    + resourceId + ":" + fe);
            return fe.asPromise();
        } catch (Exception e) {
            return new BadRequestException(e.getMessage(), e).asPromise();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> patchInstance(Context context, String resourceId,
            PatchRequest request) {
        return RestUtils.generateUnsupportedOperation();
    }

    /**
     * Returns names of all realms included in the subtree rooted by the realm indicated
     * in the query url.
     *
     * Names are unsorted and given as full paths.
     *
     * Filtering, sorting, and paging of results is not supported.
     *
     * {@inheritDoc}
     */
    @Override
    public Promise<QueryResponse, ResourceException> queryCollection(Context context, QueryRequest request,
            QueryResourceHandler handler) {

        final String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
        final RealmContext realmContext = context.asContext(RealmContext.class);
        final String realmPath = realmContext.getResolvedRealm();

        try {

            final SSOTokenManager mgr = SSOTokenManager.getInstance();
            final SSOToken ssoToken = mgr.createSSOToken(getCookieFromServerContext(context));

            final OrganizationConfigManager ocm = new OrganizationConfigManager(ssoToken, realmPath);
            final List<String> realmsInSubTree = new ArrayList<String>();
            realmsInSubTree.add(realmPath);
            for (final Object subRealmRelativePath : ocm.getSubOrganizationNames("*", true)) {
                if (realmPath.endsWith("/")) {
                    realmsInSubTree.add(realmPath + subRealmRelativePath);
                } else {
                    realmsInSubTree.add(realmPath + "/" + subRealmRelativePath);
                }
            }

            debug.message("RealmResource :: QUERY : performed by " + principalName);

            for (final Object realmName : realmsInSubTree) {
                JsonValue val = new JsonValue(realmName);
                ResourceResponse resource = newResourceResponse((String) realmName, "0", val);
                handler.handleResource(resource);
            }
            return newResultPromise(newQueryResponse());

        } catch (SSOException ex) {
            debug.error("RealmResource :: QUERY by " + principalName + " failed : " + ex);
            return new ForbiddenException().asPromise();
        } catch (SMSException ex) {
            debug.error("RealmResource :: QUERY by " + principalName + " failed :" + ex);
            switch (ex.getExceptionCode()) {
                case STATUS_NO_PERMISSION:
                    // This exception will be thrown if permission to read realms from SMS has not been delegated
                    return new ForbiddenException().asPromise();
                default:
                    return new InternalServerErrorException().asPromise();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(Context context, String resourceId,
            ReadRequest request) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        String realmPath = realmContext.getResolvedRealm();

        ResourceResponse resource;
        JsonValue jval;
        String holdResourceId = checkForTopLevelRealm(resourceId);

        try {
            hasPermission(context);
            if (holdResourceId != null && !holdResourceId.startsWith("/")) {
                holdResourceId = "/" + holdResourceId;
            }
            if (!realmPath.equalsIgnoreCase("/")) {
                holdResourceId = realmPath + holdResourceId;
            }
            OrganizationConfigManager ocm = new OrganizationConfigManager(getSSOToken(), holdResourceId);
            // get associated services for this realm , include mandatory service names.
            Set serviceNames = ocm.getAssignedServices();
            jval = createJsonMessage(SERVICE_NAMES, serviceNames);

            String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);

            resource = newResourceResponse(resourceId, String.valueOf(currentTimeMillis()), jval);
            if(debug.messageEnabled()) {
                debug.message("RealmResource.readInstance :: READ : Successfully read realm, " +
                        resourceId + " performed by " + principalName);
            }
            return newResultPromise(resource);

        } catch (SSOException sso){
            debug.error("RealmResource.updateInstance() : Cannot READ "
                    + resourceId, sso);
            return new PermanentException(401, "Access Denied", null).asPromise();
        } catch (ForbiddenException fe){
            debug.error("RealmResource.readInstance() : Cannot READ "
                    + resourceId + ":" + fe);
            return fe.asPromise();
        }  catch (SMSException smse) {

            debug.error("RealmResource.readInstance() : Cannot READ "
                    + resourceId, smse);

            try {
                configureErrorMessage(smse);
                return new BadRequestException(smse.getMessage(), smse).asPromise();
            } catch (NotFoundException nf) {
                debug.error("RealmResource.readInstance() : Cannot READ "
                        + resourceId, nf);
                return nf.asPromise();
            } catch (ForbiddenException fe) {
                // User does not have authorization
                debug.error("RealmResource.readInstance() : Cannot READ "
                        + resourceId, fe);
                return fe.asPromise();
            } catch (PermanentException pe) {
                debug.error("RealmResource.readInstance() : Cannot READ "
                        + resourceId, pe);
                // Cannot recover from this exception
                return pe.asPromise();
            } catch (ConflictException ce) {
                debug.error("RealmResource.readInstance() : Cannot READ "
                        + resourceId, ce);
                return ce.asPromise();
            } catch (BadRequestException be) {
                debug.error("RealmResource.readInstance() : Cannot READ "
                        + resourceId, be);
                return be.asPromise();
            } catch (Exception e) {
                debug.error("RealmResource.readInstance() : Cannot READ "
                        + resourceId, e);
                return new BadRequestException(e.getMessage(), e).asPromise();
            }
        } catch (Exception e) {
            return new BadRequestException(e.getMessage(), e).asPromise();
        }
    }

    /**
     * Throws an appropriate HTTP status code
     *
     * @param exception SMSException to be mapped to HTTP Status code
     * @throws ForbiddenException
     * @throws NotFoundException
     * @throws PermanentException
     * @throws ConflictException
     * @throws BadRequestException
     */
    private void configureErrorMessage(final SMSException exception)
            throws ForbiddenException, NotFoundException, PermanentException,
            ConflictException, BadRequestException {
        if (exception.getErrorCode().equalsIgnoreCase("sms-REALM_NAME_NOT_FOUND")) {
            throw new NotFoundException(exception.getMessage(), exception);
        } else if (exception.getErrorCode().equalsIgnoreCase("sms-INVALID_SSO_TOKEN")) {
            throw new PermanentException(401, "Unauthorized-Invalid SSO Token", exception);
        } else if (exception.getErrorCode().equalsIgnoreCase("sms-organization_already_exists1")) {
            throw new ConflictException(exception.getMessage(), exception);
        } else if (exception.getErrorCode().equalsIgnoreCase("sms-invalid-org-name")) {
            throw new BadRequestException(exception.getMessage(), exception);
        } else if (exception.getErrorCode().equalsIgnoreCase("sms-cannot_delete_rootsuffix")) {
            throw new PermanentException(401, "Unauthorized-Cannot delete root suffix", exception);
        } else if (exception.getErrorCode().equalsIgnoreCase("sms-entries-exists")) {
            throw new ConflictException(exception.getMessage(), exception);
        } else if (exception.getErrorCode().equalsIgnoreCase("sms-SMSSchema_service_notfound")) {
            throw new NotFoundException(exception.getMessage(), exception);
        } else if (exception.getErrorCode().equalsIgnoreCase("sms-no-organization-schema")) {
            throw new NotFoundException(exception.getMessage(), exception);
        } else if (exception.getErrorCode().equalsIgnoreCase("sms-attribute-values-does-not-match-schema")) {
            throw new BadRequestException(exception.getMessage(), exception);
        } else {
            throw new BadRequestException(exception.getMessage(), exception);
        }

    }

    /**
     * Creates Organization within OpenAM
     *
     * @param ocm   Organization Configuration Manager
     * @param jVal  JSONvalue that contains the payload
     * @param realm Name of the realm to be created
     * @throws SMSException
     * @throws Exception
     */
    private void createOrganization(OrganizationConfigManager ocm, JsonValue jVal, String realm, String realmPath)
            throws Exception {

        Map defaultValues = null;
        OrganizationConfigManager realmCreatedOcm;
        if (realmPath != null && !realmPath.endsWith("/")) {
            realmPath = realmPath + "/";
        }
        try {
            JsonValue realmDetails = jVal;
            if (jVal != null) {
                defaultValues = createServicesMap(jVal);
            }
            ocm.createSubOrganization(realm, defaultValues);
            // Get the Organization Configuration Manager for the new Realm
            realmCreatedOcm = new OrganizationConfigManager(getSSOToken(), realmPath + realm);
            List newServiceNames = realmDetails.get(SERVICE_NAMES).asList();
            if (newServiceNames != null && !newServiceNames.isEmpty()) {
                // assign services to realm
                assignServices(realmCreatedOcm, newServiceNames);
            }
        } catch (SMSException smse) {
            debug.error("RealmResource.createOrganization()", smse);
            throw smse;
        } catch (Exception e) {
            debug.error("RealmResource.createOrganization()", e);
            throw e;
        }
    }

    /**
     * Creates a Map from JsonValue content
     *
     * @param realmDetails Payload that is from request
     * @return Map of default Services needed to create realm
     * @throws Exception
     */
    private Map createServicesMap(JsonValue realmDetails) throws Exception {
        // Default Attribtes
        final String rstatus = realmDetails.get(IdConstants.ORGANIZATION_STATUS_ATTR).asString();
        // get the realm/DNS Aliases
        final String realmAliases = realmDetails.get(IdConstants.ORGANIZATION_ALIAS_ATTR).asString();
        Map defaultValues = new HashMap(2);
        try {
            Map map = new HashMap(2);
            Set values = new HashSet(2);

            values.add(getStatusAttribute(rstatus));
            map.put(IdConstants.ORGANIZATION_STATUS_ATTR, values);
            if (realmAliases != null && !realmAliases.isEmpty()) {
                Set values1 = new HashSet(2);
                values1.add(realmAliases);
                map.put(IdConstants.ORGANIZATION_ALIAS_ATTR, values1);
            }
            defaultValues.put(IdConstants.REPO_SERVICE, map);
        } catch (Exception e) {
            throw e;
        }
        return defaultValues;
    }

    /**
     * Defaults new realms to being active.
     */
    private String getStatusAttribute(String status) {
        if (INACTIVE.equalsIgnoreCase(status)) {
            return INACTIVE;
        } else {
            return ACTIVE;
        }
    }

    /**
     * Update a service with new attributes
     *
     * @param ocm          Organization Configuration Manager
     * @param serviceNames Map of service names
     * @throws SMSException
     */
    private void updateConfiguredServices(OrganizationConfigManager ocm,
                                          Map serviceNames) throws SMSException {
        try {
            ocm.setAttributes(IdConstants.REPO_SERVICE, (Map) serviceNames.get(IdConstants.REPO_SERVICE));
        } catch (SMSException smse) {
            throw smse;
        }
    }


    /**
     * Assigns Services to a realm
     *
     * @param ocm             Organization Configuration Manager
     * @param newServiceNames List of service names to be assigned/unassigned
     * @throws SMSException
     */
    private void assignServices(OrganizationConfigManager ocm, List newServiceNames)
            throws SMSException {
        try {
            // include mandatory, otherwise pass in false
            Set assignedServices = ocm.getAssignedServices();
            // combine new services names with current assigned services
            Set allServices = new HashSet(newServiceNames.size() + assignedServices.size());

            // add all to make union of the two sets of service names
            allServices.addAll(assignedServices);
            allServices.addAll(newServiceNames);

            // update services associated with realm
            for (Object tmp : allServices) {
                String serviceName = (String) tmp;
                if (newServiceNames.contains(serviceName) && assignedServices.contains(serviceName)) {
                    // do nothing, keep current service name as it is for now
                } else if (newServiceNames.contains(serviceName) && !assignedServices.contains(serviceName)) {
                    // assign the service to realm
                    ocm.assignService(serviceName, null);
                } else if (!newServiceNames.contains(serviceName) && assignedServices.contains(serviceName)) {
                    // unassign the service from the realm  if not mandatory
                    ocm.unassignService(serviceName);
                }
            }
        } catch (SMSException smse) {
            debug.error("RealmResource.assignServices() : Unable to assign services");
            throw smse;
        }
    }

    /**
     * Create an amAdmin SSOToken
     *
     * @return SSOToken adminSSOtoken
     */
    private SSOToken getSSOToken() {
        return AccessController.doPrivileged(AdminTokenAction.getInstance());
    }

    /**
     * Maps distinguished realm name to / for top level realm;
     * returns realm if not top level realm
     * @param realm realm to check whether top level or not
     * @return
     */
    private String checkForTopLevelRealm(String realm) {
        if (StringUtils.isBlank(realm)) {
            return null;
        }

        if (realm.equalsIgnoreCase(TOP_LEVEL_REALM)){
            return FORWARD_SLASH;
        } else {
            return realm;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> updateInstance(Context context, String resourceId,
            UpdateRequest request) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        String realmPath = realmContext.getResolvedRealm();

        final JsonValue realmDetails = request.getContent();
        ResourceResponse resource;
        String realm;
        OrganizationConfigManager ocm;
        OrganizationConfigManager realmCreatedOcm;

        String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);

        try {

            hasPermission(context);
            realm = checkForTopLevelRealm(resourceId);
            if (realm != null && !realm.startsWith("/")) {
                realm = "/" + realm;
            }
            if (!realmPath.equalsIgnoreCase("/")) {
                realm = realmPath + realm;
            }

            // Update a realm - if it's not found, error out.
            ocm = new OrganizationConfigManager(getSSOToken(), realm);
            List newServiceNames;
            // update ID_REPO attributes
            updateConfiguredServices(ocm, createServicesMap(realmDetails));
            newServiceNames = realmDetails.get(SERVICE_NAMES).asList();
            if (newServiceNames == null || newServiceNames.isEmpty()) {
                debug.error("RealmResource.updateInstance() : No Services defined.");
            } else {
                assignServices(ocm, newServiceNames); //assign services to realm
            }
            // READ THE REALM
            realmCreatedOcm = new OrganizationConfigManager(getSSOToken(), realm);

            debug.message("RealmResource.updateInstance :: UPDATE of realm " + realm + " performed by " +
                    principalName);

            // create a resource for handler to return
            resource = newResourceResponse(realm, String.valueOf(currentTimeMillis()),
                    createJsonMessage("realmUpdated", realmCreatedOcm.getOrganizationName()));
            return newResultPromise(resource);
        } catch (SMSException e) {
            try {
                configureErrorMessage(e);
                return new NotFoundException(e.getMessage(), e).asPromise();
            } catch (ForbiddenException fe) {
                // User does not have authorization
                debug.error("RealmResource.updateInstance() : Cannot UPDATE "
                        + resourceId, fe);
                return fe.asPromise();
            } catch (PermanentException pe) {
                debug.error("RealmResource.updateInstance() : Cannot UPDATE "
                        + resourceId, pe);
                // Cannot recover from this exception
                return pe.asPromise();
            } catch (ConflictException ce) {
                debug.error("RealmResource.updateInstance() : Cannot UPDATE "
                        + resourceId, ce);
                return ce.asPromise();
            } catch (BadRequestException be) {
                debug.error("RealmResource.updateInstance() : Cannot UPDATE "
                        + resourceId, be);
                return be.asPromise();
            } catch (Exception ex) {
                debug.error("RealmResource.updateInstance() : Cannot UPDATE "
                        + resourceId, ex);
                return new NotFoundException("Cannot update realm.", ex).asPromise();
            }
        } catch (SSOException sso){
            debug.error("RealmResource.updateInstance() : Cannot UPDATE "
                    + resourceId, sso);
            return new PermanentException(401, "Access Denied", null).asPromise();
        } catch (ForbiddenException fe){
            debug.error("RealmResource.updateInstance() : Cannot UPDATE "
                    + resourceId, fe);
            return fe.asPromise();
        } catch (PermanentException pe) {
            debug.error("RealmResource.Instance() : Cannot UPDATE "
                    + resourceId, pe);
            // Cannot recover from this exception
            return pe.asPromise();
        } catch (Exception ex) {
            debug.error("RealmResource.updateInstance() : Cannot UPDATE "
                    + resourceId, ex);
            return new NotFoundException("Cannot update realm.", ex).asPromise();
        }
    }
}
