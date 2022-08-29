/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.application.authentication.framework.config.model.graph.js.openjdk.nashorn;


import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.authentication.framework.config.model.graph.js.JsAuthenticatedUser;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.UserIdNotFoundException;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants;

/**
 * Javascript wrapper for Java level AuthenticatedUser.
 * This provides controlled access to AuthenticatedUser object via provided javascript native syntax.
 * e.g
 * var userName = context.lastAuthenticatedUser.username
 * <p>
 * instead of
 * var userName = context.getLastAuthenticatedUser().getUserName()
 * <p>
 * Also it prevents writing an arbitrary values to the respective fields, keeping consistency on runtime
 * AuthenticatedUser.
 *
 * @see AuthenticatedUser
 * Since Nashorn is deprecated in JDK 11 and onwards. We are introducing OpenJDK Nashorn engine.
 */
public class JsOpenJdkNashornAuthenticatedUser extends JsAuthenticatedUser
        implements AbstractOpenJdkNashornJsObject {

    private static final Log LOG = LogFactory.getLog(JsOpenJdkNashornAuthenticatedUser.class);

    /**
     * Constructor to be used when required to access step specific user details.
     *
     * @param context Authentication context
     * @param wrappedUser Authenticated user
     * @param step        Authentication step
     * @param idp         Authenticated Idp
     */
    public JsOpenJdkNashornAuthenticatedUser(AuthenticationContext context, AuthenticatedUser wrappedUser,
            int step, String idp) {

        super(context, wrappedUser, step, idp);
    }

    /**
     * Constructor to be used when required to access step specific user details.
     *
     * @param wrappedUser Authenticated user
     * @param step        Authentication step
     * @param idp         Authenticated Idp
     */
    public JsOpenJdkNashornAuthenticatedUser(AuthenticatedUser wrappedUser, int step, String idp) {

        super(wrappedUser, step, idp);
    }

    /**
     * Constructor to be used when required to access step independent user.
     *
     * @param wrappedUser Authenticated user
     */
    public JsOpenJdkNashornAuthenticatedUser(AuthenticatedUser wrappedUser) {

        super(wrappedUser);
    }

    public JsOpenJdkNashornAuthenticatedUser(AuthenticationContext context, AuthenticatedUser wrappedUser) {

        super(context, wrappedUser);
    }

    @Override
    public Object getMember(String name) {

        switch (name) {
        case FrameworkConstants.JSAttributes.JS_AUTHENTICATED_SUBJECT_IDENTIFIER:
            return getWrapped().getAuthenticatedSubjectIdentifier();
        case FrameworkConstants.JSAttributes.JS_USERNAME:
            return getWrapped().getUserName();
        case FrameworkConstants.JSAttributes.JS_UNIQUE_ID:
            Object userId = null;
            try {
                userId = getWrapped().getUserId();
            } catch (UserIdNotFoundException e) {
                LOG.error("Error while retrieving user Id of user : " + getWrapped().getLoggableUserId(), e);
            }
            return userId;
        case FrameworkConstants.JSAttributes.JS_USER_STORE_DOMAIN:
            return getWrapped().getUserStoreDomain();
        case FrameworkConstants.JSAttributes.JS_TENANT_DOMAIN:
            return getWrapped().getTenantDomain();
        case FrameworkConstants.JSAttributes.JS_LOCAL_CLAIMS:
            if (StringUtils.isNotBlank(idp)) {
                return new JsOpenJdkNashornClaims(getContext(), step, idp, false);
            } else {
                // Represent step independent user
                return new JsOpenJdkNashornClaims(getContext(), getWrapped(), false);
            }
        case FrameworkConstants.JSAttributes.JS_REMOTE_CLAIMS:
            if (StringUtils.isNotBlank(idp)) {
                return new JsOpenJdkNashornClaims(getContext(), step, idp, true);
            } else {
                // Represent step independent user
                return new JsOpenJdkNashornClaims(getContext(), getWrapped(), true);
            }
        case FrameworkConstants.JSAttributes.JS_LOCAL_ROLES:
            return getLocalRoles();
        case FrameworkConstants.JSAttributes.JS_CLAIMS:
            if (StringUtils.isNotBlank(idp)) {
                return new JsOpenJdkNashornRuntimeClaims(getContext(), step, idp);
            } else {
                // Represent step independent user
                return new JsOpenJdkNashornRuntimeClaims(getContext(), getWrapped());
            }
        default:
            return super.getMember(name);
        }
    }
}
