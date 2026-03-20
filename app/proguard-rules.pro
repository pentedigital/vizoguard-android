# Vizoguard VPN ProGuard Rules
-keepattributes *Annotation*
-keep class com.vizoguard.vpn.** { *; }

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

# Ktor / Kotlin serialization
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * { @kotlinx.serialization.Serializable *; }

# tun2socks native bridge
-keep class tun2socks.** { *; }
-keep class shadowsocks.** { *; }
