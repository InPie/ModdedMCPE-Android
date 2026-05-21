-keepattributes Signature

-keep class com.google.gson.reflect.TypeToken {
    *;
}

-keep class * extends com.google.gson.reflect.TypeToken {
    *;
}

-keep class org.endercore.android.operator.instance.model.** {
    *;
}

-keep class org.endercore.android.operator.OptionsManager$OptionsJsonBean {
    *;
}

-keep class org.endercore.android.operator.OptionsManager$NModOptionsElement {
    *;
}

-keep class org.endercore.android.utils.NModJsonBean$* {
    *;
}
