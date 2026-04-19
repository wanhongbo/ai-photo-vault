# 一期：保留 R8 映射供 Play 符号化；后续按依赖补充

# Hilt / Room / 反射（按需收紧）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
