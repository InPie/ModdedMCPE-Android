package junit.framework;

public class AssertionFailedError extends Error {
    public AssertionFailedError() {}
    public AssertionFailedError(String message) {
        super(message);
    }
}
