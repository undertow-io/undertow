package io.undertow.servlet.core;

import io.undertow.security.idm.Account;
import io.undertow.servlet.api.AuthorizationManager;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.SecurityRoleRef;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.api.SingleConstraintMatch;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default authorization manager that simply implements the rules as specified by the servlet spec
 *
 * @author Stuart Douglas
 */
public class DefaultAuthorizationManager implements AuthorizationManager {

    public static final DefaultAuthorizationManager INSTANCE = new DefaultAuthorizationManager();

    private DefaultAuthorizationManager() {
    }

    @Override
    public boolean isUserInRole(String role, Account account, ServletInfo servletInfo, HttpServletRequest request, Deployment deployment) {

        final Map<String, Set<String>> principalVersusRolesMap = deployment.getDeploymentInfo().getPrincipalVersusRolesMap();
        final Set<String> roles = principalVersusRolesMap.get(account.getPrincipal().getName());
        //TODO: a more efficient imple
        for (SecurityRoleRef ref : servletInfo.getSecurityRoleRefs()) {
            if (ref.getRole().equals(role)) {
                if (roles != null && roles.contains(ref.getLinkedRole())) {
                    return true;
                }
                return account.getRoles().contains(ref.getLinkedRole());
            }
        }
        if (roles != null && roles.contains(role)) {
            return true;
        }
        return account.getRoles().contains(role);
    }

    @Override
    public boolean canAccessResource(List<SingleConstraintMatch> constraints, Account account, ServletInfo servletInfo, HttpServletRequest request, Deployment deployment) {
        if (constraints == null || constraints.isEmpty()) {
            return true;
        }
        for (final SingleConstraintMatch constraint : constraints) {

            boolean found = false;

            Set<String> roleSet = constraint.getRequiredRoles();
            if (roleSet.isEmpty() && constraint.getEmptyRoleSemantic() != SecurityInfo.EmptyRoleSemantic.DENY) {
                    /*
                     * The EmptyRoleSemantic was either PERMIT or AUTHENTICATE, either way a roles check is not needed.
                     */
                found = true;
            } else if (account != null) {
                final Set<String> roles = deployment.getDeploymentInfo().getPrincipalVersusRolesMap().get(account.getPrincipal().getName());

                for (String role : roleSet) {
                    if (roles != null) {
                        if (roles.contains(role)) {
                            found = true;
                            break;
                        }
                    }
                    if (account.getRoles().contains(role)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;

    }

    @Override
    public TransportGuaranteeType transportGuarantee(TransportGuaranteeType currentConnectionGuarantee, TransportGuaranteeType configuredRequiredGuarentee, HttpServletRequest request) {
        return configuredRequiredGuarentee;
    }
}
