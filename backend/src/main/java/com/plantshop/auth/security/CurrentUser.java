package com.plantshop.auth.security;

/**
 * The authenticated shopper, independent of the token source.
 *
 * @param ownerId stable owner id: {@code github:{id}} or {@code local:{username}}
 * @param displayName name to show in the UI
 * @param avatarUrl avatar URL, or {@code null}
 * @param provider token source: {@code github} or {@code local}
 */
public record CurrentUser(String ownerId, String displayName, String avatarUrl, String provider) {}
