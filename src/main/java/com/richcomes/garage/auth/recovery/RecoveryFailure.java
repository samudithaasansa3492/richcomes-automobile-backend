package com.richcomes.garage.auth.recovery;
public final class RecoveryFailure extends RuntimeException {
    public RecoveryFailure() { super("Unable to complete recovery request"); }
}
