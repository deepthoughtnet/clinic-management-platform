package com.deepthoughtnet.clinic.platform.security;

import java.util.Set;

public interface PermissionEvaluator {
    boolean hasPermission(Set<String> roles, String permission);
}
