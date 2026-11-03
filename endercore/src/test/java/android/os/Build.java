package android.os;

// Shadows the android.jar stub in unit tests
public class Build {
    public static final String[] SUPPORTED_32_BIT_ABIS = {"armeabi-v7a"};

    public static class VERSION {
        public static final int SDK_INT = 33;
    }
}
