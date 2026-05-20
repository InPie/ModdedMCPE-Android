package com.google.firebase.iid;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;

import java.io.IOException;
import java.util.concurrent.Executor;

public class FirebaseInstanceId {
    private static final String EMPTY = "";
    private static final FirebaseInstanceId INSTANCE = new FirebaseInstanceId();
    private static final InstanceIdResult EMPTY_RESULT = new InstanceIdResult() {
        @Override
        public String getId() {
            return EMPTY;
        }

        @Override
        public String getToken() {
            return EMPTY;
        }
    };
    private static final Task<InstanceIdResult> EMPTY_TASK = new ImmediateTask<>(EMPTY_RESULT);

    public static synchronized FirebaseInstanceId getInstance() {
        return INSTANCE;
    }

    public static synchronized FirebaseInstanceId getInstance(FirebaseApp app) {
        return INSTANCE;
    }

    public String getId() {
        return EMPTY;
    }

    public long getCreationTime() {
        return 0L;
    }

    public Task<InstanceIdResult> getInstanceId() {
        return EMPTY_TASK;
    }

    public void deleteInstanceId() throws IOException {
    }

    public String getToken() {
        return EMPTY;
    }

    public String getToken(String authorizedEntity, String scope) throws IOException {
        return EMPTY;
    }

    public void deleteToken(String authorizedEntity, String scope) throws IOException {
    }

    public static synchronized void clearInstancesForTest() {
    }

    public boolean isGmsCorePresent() {
        return false;
    }

    public boolean isFcmAutoInitEnabled() {
        return false;
    }

    public void setFcmAutoInitEnabled(boolean enabled) {
    }

    private static final class ImmediateTask<TResult> extends Task<TResult> {
        private final TResult result;

        private ImmediateTask(TResult result) {
            this.result = result;
        }

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public boolean isSuccessful() {
            return true;
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public TResult getResult() {
            return result;
        }

        @Override
        public <X extends Throwable> TResult getResult(Class<X> cls) throws X {
            return result;
        }

        @Override
        public Exception getException() {
            return null;
        }

        @Override
        public Task<TResult> addOnSuccessListener(OnSuccessListener<? super TResult> listener) {
            if (listener != null) {
                listener.onSuccess(result);
            }
            return this;
        }

        @Override
        public Task<TResult> addOnSuccessListener(
                Executor executor,
                OnSuccessListener<? super TResult> listener
        ) {
            if (listener != null) {
                if (executor != null) {
                    executor.execute(() -> listener.onSuccess(result));
                } else {
                    listener.onSuccess(result);
                }
            }
            return this;
        }

        @Override
        public Task<TResult> addOnSuccessListener(
                android.app.Activity activity,
                OnSuccessListener<? super TResult> listener
        ) {
            return addOnSuccessListener(listener);
        }

        @Override
        public Task<TResult> addOnFailureListener(OnFailureListener listener) {
            return this;
        }

        @Override
        public Task<TResult> addOnFailureListener(
                Executor executor,
                OnFailureListener listener
        ) {
            return this;
        }

        @Override
        public Task<TResult> addOnFailureListener(
                android.app.Activity activity,
                OnFailureListener listener
        ) {
            return this;
        }

        @Override
        public Task<TResult> addOnCompleteListener(OnCompleteListener<TResult> listener) {
            if (listener != null) {
                listener.onComplete(this);
            }
            return this;
        }

        @Override
        public Task<TResult> addOnCompleteListener(
                Executor executor,
                OnCompleteListener<TResult> listener
        ) {
            if (listener != null) {
                if (executor != null) {
                    executor.execute(() -> listener.onComplete(this));
                } else {
                    listener.onComplete(this);
                }
            }
            return this;
        }

        @Override
        public Task<TResult> addOnCompleteListener(
                android.app.Activity activity,
                OnCompleteListener<TResult> listener
        ) {
            return addOnCompleteListener(listener);
        }

        @Override
        public Task<TResult> addOnCanceledListener(OnCanceledListener listener) {
            return this;
        }

        @Override
        public Task<TResult> addOnCanceledListener(
                Executor executor,
                OnCanceledListener listener
        ) {
            return this;
        }

        @Override
        public Task<TResult> addOnCanceledListener(
                android.app.Activity activity,
                OnCanceledListener listener
        ) {
            return this;
        }
    }
}
