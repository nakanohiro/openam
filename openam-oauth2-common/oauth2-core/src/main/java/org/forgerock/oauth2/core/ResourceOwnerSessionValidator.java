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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.oauth2.core;

import com.iplanet.sso.SSOToken;
import org.forgerock.oauth2.core.exceptions.AccessDeniedException;
import org.forgerock.oauth2.core.exceptions.BadRequestException;
import org.forgerock.oauth2.core.exceptions.InteractionRequiredException;
import org.forgerock.oauth2.core.exceptions.InvalidClientException;
import org.forgerock.oauth2.core.exceptions.LoginRequiredException;
import org.forgerock.oauth2.core.exceptions.NotFoundException;
import org.forgerock.oauth2.core.exceptions.ResourceOwnerAuthenticationRequired;
import org.forgerock.oauth2.core.exceptions.ServerException;

/**
 * Validates whether a resource owner has a current authenticated session.
 *
 * @since 12.0.0
 */
public interface ResourceOwnerSessionValidator {

    /**
     * Checks if the request contains valid resource owner session.
     *
     * @param request The OAuth2 request.
     * @return The ResourceOwner.
     * @throws ResourceOwnerAuthenticationRequired If the resource owner needs to authenticate before the authorize
     *          request can be allowed.
     * @throws AccessDeniedException If resource owner authentication fails.
     * @throws BadRequestException If the request is malformed.
     * @throws InteractionRequiredException If the OpenID Connect prompt parameter enforces that the resource owner
     *          is not asked to authenticate, but the resource owner does not have a current authenticated session.
     * @throws LoginRequiredException If authenticating the resource owner fails.
     * @throws ServerException If the server is misconfigured.
     * @throws NotFoundException If the realm does not have an OAuth 2.0 provider service.
     */
    ResourceOwner validate(OAuth2Request request) throws ResourceOwnerAuthenticationRequired, AccessDeniedException,
            BadRequestException, InteractionRequiredException, LoginRequiredException, ServerException, NotFoundException;

    /**
     * Gets the resource owner's session from the OAuth2 request.
     *
     * @param request The OAuth2 request.
     * @return The resource owner's {@code SSOToken}.
     */
    public SSOToken getResourceOwnerSession(OAuth2Request request);

}
