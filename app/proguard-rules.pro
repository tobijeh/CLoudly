# kotlinx.serialization ships its own consumer rules; keep our @Serializable models' companions.
-keepclassmembers @kotlinx.serialization.Serializable class io.github.tobijeh.cloudly.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
