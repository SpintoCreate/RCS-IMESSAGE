# Called reflectively from libmatrix_sdk_ffi.so; R8 cannot see these JNI references.
-keep, includedescriptorclasses class org.rustls.platformverifier.** { *; }
