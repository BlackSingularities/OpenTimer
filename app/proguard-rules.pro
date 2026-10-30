# Compose i AndroidX dostarczają własne reguły konsumenckie.
-keepattributes *Annotation*, Signature, InnerClasses

# Usługi, odbiorniki i widżety system tworzy po nazwie klasy z manifestu.
-keep class com.blacksingularities.opentimer.**.*Receiver { *; }
-keep class com.blacksingularities.opentimer.**.*Service { *; }
-keep class com.blacksingularities.opentimer.**.*Widget { *; }
