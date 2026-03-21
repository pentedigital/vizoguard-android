# Vizoguard VPN ProGuard Rules
-keepattributes *Annotation*

# API serialization models (accessed via kotlinx.serialization reflection)
-keep class com.vizoguard.vpn.api.LicenseResponse { *; }
-keep class com.vizoguard.vpn.api.VpnResponse { *; }
-keep class com.vizoguard.vpn.api.ErrorResponse { *; }
-keep class com.vizoguard.vpn.api.HealthResponse { *; }
-keep class com.vizoguard.vpn.api.LicenseRequest { *; }

# Tink (via androidx.security:security-crypto) references compile-only annotations
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

# Ktor debug detector references JMX (not available on Android)
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# Ktor engine + serialization (used via reflection/service loading)
-keep class io.ktor.client.engine.android.AndroidEngineContainer { *; }
-keep class io.ktor.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keep,allowobfuscation class * extends kotlinx.serialization.KSerializer

# kotlinx.serialization generated serializers
-keepclassmembers class **$$serializer { *; }

# tun2socks + shadowsocks native bridge (JNI)
-keep class tun2socks.** { *; }
-keep class shadowsocks.** { *; }
