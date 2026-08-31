# Treadless — R8 規則
#
# 這支 App 自己的程式碼沒有反射、沒有序列化框架（分組是手寫的
# 「名稱:值,值|名稱:值」字串格式），所以不需要為自家類別留 keep。
# Compose、Health Connect client、coroutines 都自帶 consumer rules，
# 由 AAR 合併進來，這裡只補它們沒涵蓋到的部分。
#
# 修改後務必重跑一輪實機驗收：R8 的問題全部是執行期才炸，編譯過不代表沒事。

# --- AGSL / RuntimeShader ---
# 玻璃元件的 shader 是字串常數，R8 不會動；但 RuntimeShader 只存在於
# API 33+，minSdk 26 的降級路徑靠 SDK_INT 判斷，別讓 R8 因為找不到而中斷。
-dontwarn android.graphics.RuntimeShader

# --- 除錯用 ---
# 保留行號並改名 source file，crash 堆疊才對得回原始碼
# （對應表在 app/build/outputs/mapping/release/mapping.txt，發版時留存）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
