package net.classcardauto.mobile;

import kotlin.Metadata;

/* compiled from: AppPassword.kt */
/* loaded from: classes.dex */
public final class AppPassword {
    public static final AppPassword INSTANCE = new AppPassword();
    private static final PasswordSession session = new PasswordSession(true, null, 2, null);

    private AppPassword() {
    }

    public final boolean getAllowed() {
        return session.getUnlocked();
    }

    public final PasswordSession getSession() {
        return session;
    }

    public final void lock() {
        session.lock();
    }
}
