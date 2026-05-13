# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Room 数据库规则：保护 AppDatabase_Impl 类不被移除
-keep class androidx.room.Room
-keep class * extends androidx.room.RoomDatabase {
    public <init>(...);
}
# 保护你的 AppDatabase 实现类
-keep class com.example.demo.data.AppDatabase_Impl { *; }
# 保护你的 DAO 接口
-keep class * implements com.example.demo.data.PersonaDao { *; }
# 保护你的 Entity
-keep class com.example.demo.data.Persona { *; }