# anhnn-components

Bộ thư viện Android reusable dùng cho các app của Anhnn, phân phối qua **JitPack**.

---

## Cài đặt

### 1. Thêm JitPack vào `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

### 2. Thêm dependency theo module cần dùng

```kotlin
// build.gradle.kts (app)
dependencies {
    // Chọn module cần thiết:
    implementation("com.github.anhngocnguyen1034.anhnn-components:privacy:1.0.0")
    implementation("com.github.anhngocnguyen1034.anhnn-components:feedback:1.0.0")
    implementation("com.github.anhngocnguyen1034.anhnn-components:rate:1.0.0")
    implementation("com.github.anhngocnguyen1034.anhnn-components:exit:1.1.0")

    // Thư viện language (repo riêng):
    implementation("com.github.anhngocnguyen1034:anhnn-language:1.0.0")
}
```

---

## Modules

### :privacy — Privacy Policy Screen

Màn hình hiển thị Privacy Policy qua WebView với loading indicator.

#### Permissions

Module tự khai báo `INTERNET` permission. Không cần thêm vào `AndroidManifest.xml` của app.

#### Sử dụng

```kotlin
import com.anhnn.privacy.PrivacyPolicyScreen

@Composable
fun MyApp() {
    PrivacyPolicyScreen(
        url = "https://example.com/privacy",
        title = "Chính sách bảo mật",   // tuỳ chọn, mặc định "Privacy Policy"
        onBack = { navController.popBackStack() }
    )
}
```

| Tham số | Kiểu | Bắt buộc | Mô tả |
|---------|------|----------|-------|
| `url` | `String` | ✓ | URL trang Privacy Policy |
| `title` | `String` | ✗ | Tiêu đề TopAppBar (mặc định `"Privacy Policy"`) |
| `onBack` | `() -> Unit` | ✗ | Callback nút Back |

---

### :feedback — Feedback Screen

Màn hình soạn phản hồi gửi email bằng email client có sẵn trên thiết bị.

#### Sử dụng

```kotlin
import com.anhnn.feedback.FeedbackScreen

@Composable
fun MyApp() {
    FeedbackScreen(
        email = "support@yourapp.com",
        subject = "Phản hồi ứng dụng",  // tuỳ chọn
        title = "Góp ý",                // tuỳ chọn
        onBack = { navController.popBackStack() }
    )
}
```

| Tham số | Kiểu | Bắt buộc | Mô tả |
|---------|------|----------|-------|
| `email` | `String` | ✓ | Địa chỉ email nhận feedback |
| `subject` | `String` | ✗ | Tiêu đề email mặc định (mặc định `"App Feedback"`) |
| `title` | `String` | ✗ | Tiêu đề TopAppBar (mặc định `"Feedback"`) |
| `onBack` | `() -> Unit` | ✗ | Callback nút Back |

Nút **Send** chỉ enabled khi người dùng đã nhập nội dung. Khi nhấn, mở email client qua `Intent.ACTION_SENDTO`.

---

### :rate — In-App Review & Rate Dialog

Hai cách để yêu cầu người dùng đánh giá app:

1. **`requestInAppReview`** — Google Play In-App Review API (native, không rời app)
2. **`RateDialog`** — Dialog fallback mở Play Store khi In-App Review không khả dụng

#### `requestInAppReview` (khuyến nghị)

```kotlin
import com.anhnn.rate.requestInAppReview

// Trong Activity hoặc ViewModel
requestInAppReview(
    activity = this,
    onFallback = {
        // Hiện RateDialog khi In-App Review không khả dụng
        showRateDialog = true
    }
)
```

> **Lưu ý:** Google Play giới hạn số lần hiển thị In-App Review. Không gọi hàm này mỗi lần mở app — chỉ gọi sau khi người dùng hoàn thành một hành động có giá trị (ví dụ: dùng app 5 lần, hoàn thành task quan trọng).

#### `RateDialog` (fallback)

```kotlin
import com.anhnn.rate.RateDialog

@Composable
fun MyApp() {
    var showRateDialog by remember { mutableStateOf(false) }

    if (showRateDialog) {
        RateDialog(
            packageName = "com.yourapp.package",
            title = "Bạn thích app không?",          // tuỳ chọn
            message = "Hãy đánh giá để ủng hộ chúng tôi!", // tuỳ chọn
            confirmText = "Đánh giá ngay",            // tuỳ chọn
            dismissText = "Để sau",                   // tuỳ chọn
            onDismiss = { showRateDialog = false }
        )
    }
}
```

| Tham số | Kiểu | Bắt buộc | Mô tả |
|---------|------|----------|-------|
| `packageName` | `String` | ✓ | Package name của app để mở đúng trang Store |
| `title` | `String` | ✗ | Tiêu đề dialog |
| `message` | `String` | ✗ | Nội dung dialog |
| `confirmText` | `String` | ✗ | Nhãn nút xác nhận |
| `dismissText` | `String` | ✗ | Nhãn nút bỏ qua |
| `onDismiss` | `() -> Unit` | ✗ | Callback khi đóng dialog |

#### Dùng kết hợp (pattern chuẩn)

```kotlin
// Trong Activity
Button(onClick = {
    requestInAppReview(activity = this) {
        showRateDialog = true
    }
}) {
    Text("Đánh giá app")
}
```

---

### :exit — Exit App Screen

Màn xác nhận thoát app full-screen (giống `askExitApp` của Taymay ADX) nhưng thuần Compose
và **độc lập hệ quảng cáo**. Tự bắt nút Back, hiện màn xác nhận, gọi `onExit` khi xác nhận.
Quảng cáo (native/banner/bất kỳ) truyền qua slot nên gắn bao nhiêu cũng được.

#### Sử dụng (1 dòng)

```kotlin
import com.anhnn.exit.ExitAppHandler

@Composable
fun HomeScreen() {
    val activity = LocalContext.current as Activity
    ExitAppHandler(
        onExit = { activity.finish() },
        appIcon = painterResource(R.mipmap.ic_launcher),   // tuỳ chọn — icon đầu màn
        appName = stringResource(R.string.app_name),       // tuỳ chọn — tên app
        title = "Thoát ứng dụng?",                         // tuỳ chọn
        message = "Bạn có chắc muốn thoát?",               // tuỳ chọn
        confirmText = "Thoát",                             // tuỳ chọn
        dismissText = "Ở lại",                             // tuỳ chọn
        topContent = { MyBannerAd() },                     // tuỳ chọn — slot cố định trên cùng
        adContent = {                                      // tuỳ chọn — slot giữa, tự cuộn
            MyNativeAd("exit_native_1")
            MyNativeAd("exit_native_2")
        },
    )
}
```

Đặt `ExitAppHandler` trong composable của màn muốn chặn Back (vd Home). Bỏ trống `topContent`/
`adContent` thì màn không có quảng cáo. Màu sắc lấy từ `MaterialTheme.colorScheme` nên tự ăn theme.

| Tham số | Kiểu | Bắt buộc | Mô tả |
|---------|------|----------|-------|
| `onExit` | `() -> Unit` | ✓ | Chạy khi user xác nhận thoát (vd `activity.finish()`) |
| `enabled` | `Boolean` | ✗ | Bật/tắt chặn Back |
| `appIcon` | `Painter?` | ✗ | Icon app ở đầu màn; null = ẩn |
| `appName` | `String?` | ✗ | Tên app cạnh icon; null = ẩn |
| `title` / `message` | `String` | ✗ | Tiêu đề / nội dung |
| `confirmText` / `dismissText` | `String` | ✗ | Nhãn nút Thoát / Ở lại |
| `topContent` | `@Composable () -> Unit` | ✗ | Slot cố định trên cùng (vd banner) |
| `adContent` | `@Composable ColumnScope.() -> Unit` | ✗ | Slot quảng cáo giữa màn, tự cuộn |

---

### anhnn-language — Language Screen

Màn hình chọn ngôn ngữ hỗ trợ 24 ngôn ngữ, lưu bằng DataStore, áp dụng ngay khi chọn.

**Repo:** `https://github.com/anhngocnguyen1034/anhnn-language`

#### Bước 1 — Override `attachBaseContext` trong `Activity`

```kotlin
import com.anhnn.language.LanguageDataSource
import com.anhnn.language.LanguageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val code = runBlocking { LanguageDataSource(newBase).languageCode.first() }
        super.attachBaseContext(LanguageManager.setLanguage(newBase, code))
    }
}
```

Bước này bắt buộc để locale được áp dụng trước khi Activity inflate layout.

#### Bước 2 — Thêm route trong NavHost

```kotlin
composable("language") {
    val context = LocalContext.current
    LanguageScreen(
        onBack = { navController.popBackStack() },
        onLanguageSaved = {
            // Recreate Activity để locale mới có hiệu lực toàn app
            (context as? Activity)?.recreate()
        }
    )
}
```

#### Bước 3 — Điều hướng từ Settings

```kotlin
composable("settings") {
    SettingsScreen(
        onOpenLanguage = { navController.navigate("language") }
    )
}
```

#### Ngôn ngữ hỗ trợ

| Code | Ngôn ngữ | Code | Ngôn ngữ |
|------|----------|------|----------|
| `vi` | Tiếng Việt | `en` | English |
| `ja` | 日本語 | `ko` | 한국어 |
| `zh-CN` | 简体中文 | `zh-TW` | 繁體中文 |
| `fr` | Français | `de` | Deutsch |
| `es` | Español | `pt` | Português |
| `it` | Italiano | `ru` | Русский |
| `ar` | العربية | `hi` | हिन्दी |
| `th` | ไทย | `id` | Bahasa Indonesia |
| `tr` | Türkçe | `pl` | Polski |
| `nl` | Nederlands | `sv` | Svenska |
| `no` | Norsk | `fi` | Suomi |
| `el` | Ελληνικά | `cs` | Čeština |

#### Dùng `LanguageDataSource` trực tiếp (không cần màn hình)

```kotlin
val dataSource = LanguageDataSource(context)

// Đọc ngôn ngữ hiện tại (Flow)
val currentCode: Flow<String> = dataSource.languageCode

// Lưu ngôn ngữ mới
viewModelScope.launch {
    dataSource.setLanguageCode("vi")
}
```

---

## Ví dụ tích hợp đầy đủ

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val code = runBlocking { LanguageDataSource(newBase).languageCode.first() }
        super.attachBaseContext(LanguageManager.setLanguage(newBase, code))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyTheme {
                val navController = rememberNavController()
                var showRateDialog by remember { mutableStateOf(false) }

                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onOpenPrivacy   = { navController.navigate("privacy") },
                            onOpenFeedback  = { navController.navigate("feedback") },
                            onOpenLanguage  = { navController.navigate("language") },
                            onRateApp = {
                                requestInAppReview(this@MainActivity) {
                                    showRateDialog = true
                                }
                            }
                        )
                    }
                    composable("privacy") {
                        PrivacyPolicyScreen(
                            url = "https://example.com/privacy",
                            title = "Chính sách bảo mật",
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("feedback") {
                        FeedbackScreen(
                            email = "support@example.com",
                            subject = "Phản hồi ứng dụng",
                            title = "Góp ý",
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("language") {
                        val context = LocalContext.current
                        LanguageScreen(
                            onBack = { navController.popBackStack() },
                            onLanguageSaved = { (context as? Activity)?.recreate() }
                        )
                    }
                }

                if (showRateDialog) {
                    RateDialog(
                        packageName = packageName,
                        onDismiss = { showRateDialog = false }
                    )
                }
            }
        }
    }
}
```

---

## Yêu cầu

- Min SDK: **24**
- Compile SDK: **36**
- Kotlin: **2.0.21**
- Compose BOM: **2024.09.00**
