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
    implementation("com.github.anhngocnguyen1034.anhnn-components:feedback:1.9.0")
    implementation("com.github.anhngocnguyen1034.anhnn-components:rate:1.9.0")
    implementation("com.github.anhngocnguyen1034.anhnn-components:exit:1.1.0")
    // ⚠️ artifactId của module ads là dạng đầy đủ `anhnn-components-ads` (không phải `ads`):
    implementation("com.github.anhngocnguyen1034.anhnn-components:anhnn-components-ads:1.2.1")
    implementation("com.github.anhngocnguyen1034.anhnn-components:anhnn-components-analytics:1.3.0")

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

#### `FeedbackDialog` — dialog nổi, chọn chủ đề bằng chip

Khác `FeedbackScreen` (một màn hình đầy đủ, gửi email): đây là dialog nổi, trả nội dung về qua
`onSubmit` nên app tự quyết định gửi đi đâu (email, backend, analytics…). Gửi xong hiện màn cảm ơn
rồi tự đóng.

```kotlin
import com.anhnn.feedback.FeedbackDialog

if (showFeedback) {
    FeedbackDialog(
        onSubmit = { text -> Analytics.logEvent("submit_feedback", mapOf("content" to text)) },
        onDismiss = { showFeedback = false },
    )
}
```

Nội dung trả về đã gộp chủ đề đã chọn vào đầu chuỗi: `"[UI Design, Ads] chữ user nhập"`.

| Tham số | Kiểu | Bắt buộc | Mô tả |
|---------|------|----------|-------|
| `onSubmit` | `(String) -> Unit` | ✓ | Nhận nội dung đã gộp; chỉ gọi khi khác rỗng |
| `onDismiss` | `() -> Unit` | ✓ | Đóng dialog |
| `title` / `subtitle` | `String` / `String?` | ✗ | Tiêu đề và dòng mô tả (`subtitle = null` để ẩn) |
| `tags` | `List<String>` | ✗ | Chủ đề chọn nhanh (mặc định `DefaultFeedbackTags`); rỗng = ẩn chip |
| `hint` / `submitText` | `String` | ✗ | Placeholder ô nhập và nhãn nút gửi |
| `dismissOnOutside` | `Boolean` | ✗ | Cho phép đóng khi bấm ra ngoài / Back (mặc định `true`) |
| `thanksTitle` | `String?` | ✗ | `null` = gửi xong đóng luôn, không hiện màn cảm ơn |
| `thanksMessage` / `thanksDurationMillis` | `String` / `Long` | ✗ | Nội dung và thời lượng màn cảm ơn (mặc định 1500ms) |

---

### :rate — In-App Review & Rate Dialog

Ba cách để yêu cầu người dùng đánh giá app:

1. **`requestInAppReview`** — Google Play In-App Review API (native, không rời app)
2. **`RateAndFeedbackDialog`** — dialog chấm điểm 2 nhánh: chấm thấp thì thu góp ý trong app, chấm cao mới đẩy ra Store
3. **`RateDialog`** — Dialog fallback mở Play Store khi In-App Review không khả dụng

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

#### `RateAndFeedbackDialog` — chấm điểm 2 nhánh (giữ điểm Store)

Chỉ user hài lòng (từ `goodRateThreshold` trở lên, mặc định 4/5) mới thấy nút mở Store; user chưa
hài lòng thì chọn chủ đề + nhập góp ý, nội dung trả về qua `onSubmitFeedback` rồi hiện màn cảm ơn
và tự đóng. Nhờ vậy phản hồi tiêu cực về thẳng team thay vì lên Store.

```kotlin
import com.anhnn.rate.RateAndFeedbackDialog
import com.anhnn.rate.openPlayStore
import com.anhnn.rate.requestInAppReview
import com.anhnn.rate.setRated
import com.anhnn.rate.shouldAskRate

var showRate by rememberSaveable { mutableStateOf(shouldAskRate(context)) }

if (showRate) {
    RateAndFeedbackDialog(
        onRate = {
            setRated(context)
            requestInAppReview(activity) { openPlayStore(activity) }
            showRate = false
        },
        onSubmitFeedback = { text -> Analytics.logEvent("submit_feedback", mapOf("content" to text)) },
        onDismiss = { showRate = false },
    )
}
```

| Tham số | Kiểu | Bắt buộc | Mô tả |
|---------|------|----------|-------|
| `onRate` | `() -> Unit` | ✓ | Chạy khi user chấm cao và bấm nút Store |
| `onDismiss` | `() -> Unit` | ✓ | Đóng dialog |
| `onSubmitFeedback` | `(String) -> Unit` | ✗ | Nhận góp ý đã gộp chủ đề khi user chấm thấp |
| `goodRateThreshold` | `Int` | ✗ | Mức coi là hài lòng (mặc định `4`) |
| `emojis` / `labels` | `List<String>` | ✗ | Emoji và nhãn của 5 mức (`DefaultRateEmojis` / `DefaultRateLabels`) |
| `tags` | `List<String>` | ✗ | Chủ đề hiện khi chấm thấp; rỗng = ẩn chip |
| `title` / `lowRateTitle` | `String` | ✗ | Tiêu đề khi chấm cao / chấm thấp |
| `hint` / `sendText` / `rateText` | `String` | ✗ | Placeholder ô nhập, nhãn nút gửi, nhãn nút Store |
| `dismissText` | `String?` | ✗ | Nhãn nút bỏ qua; `null` = ẩn (bắt buộc chọn) |
| `thanksTitle` / `thanksMessage` / `thanksDurationMillis` | | ✗ | Màn cảm ơn sau khi gửi góp ý |

#### Gate — hỏi đúng lúc

`shouldAskRate` tự đếm số lần mở app và trả `false` khi chưa đủ điều kiện, nên gọi thẳng ở chỗ cần:

```kotlin
shouldAskRate(context, minSessions = 2, oncePerSession = true)  // → Boolean
isRated(context)                // user đã đánh giá chưa
setRated(context)               // đánh dấu đã đánh giá (gọi trong onRate)
resetRateState(context)         // xoá trạng thái, hữu ích khi test
openPlayStore(context)          // mở Play Store (market:// → fallback trình duyệt)
```

Mặc định `minSessions = 2` nên **lần cài đặt đầu tiên không bao giờ bị hỏi**, và mỗi lần chạy app
chỉ hỏi tối đa một lần. Sau khi `setRated` thì không hỏi lại nữa.

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

Màn xác nhận thoát app full-screen, thuần Compose và **độc lập hệ quảng cáo**. Tự bắt nút Back,
hiện màn xác nhận, gọi `onExit` khi xác nhận.
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

### :ads — AdMob Manager (nhanh & mượt)

Quản lý quảng cáo AdMob với cơ chế **preload trước vào cache → lấy ra hiện ngay → tự nạp lại**
cho lượt sau. Native + Interstitial được cache nên hiện tức thì;
Banner load inline (nhẹ). Gộp sẵn **consent (UMP)** + init Mobile Ads SDK. Module **không phụ
thuộc Firebase** — app tự bơm cấu hình (ad unit id, bật/tắt ads, cooldown) qua `AdsConfig`.

#### Manifest (app tiêu thụ)

App **bắt buộc** khai báo AdMob App ID, nếu không SDK crash khi init:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXX~YYYYYYYY" />
```

#### Bước 1 — Khai báo cấu hình (1 lần)

```kotlin
import com.anhnn.ads.*

// adFormat: app tự khai báo định dạng cho từng tên vị trí
val formats = mapOf(
    "splash_open" to AdFormat.INTERSTITIAL,
    "exit_native" to AdFormat.NATIVE,
    "exit_banner" to AdFormat.BANNER,
)

Ads.init(AdsConfig(
    adsEnabled = { remoteConfig.adsEnabled() },          // vd đọc Remote Config
    adUnitId   = { name -> remoteConfig.adUnitId(name) }, // map sang test/production
    adFormat   = { name -> formats[name] },
    interCooldownMs = { 30_000L },                        // tùy chọn
))
```

#### Bước 2 — Consent + init SDK, rồi preload

```kotlin
// Trong Activity.onCreate
Ads.start(this) {
    // consent xong + MobileAds init xong:
    Ads.preload(this, "splash_open", "exit_native", "exit_banner")
}
```

#### Bước 3 — Dùng ad

```kotlin
// Interstitial: hiện nếu sẵn & qua cooldown; nếu chưa thì chạy tiếp NGAY (không chặn user)
Ads.showInterstitial(activity, "home_tuvi") { navController.navigate("input") }

// Native / Banner: composable tự lấy ad đã preload (hiện ngay), tự hủy đúng lifecycle
NativeAd(adName = "exit_native")   // màu theo MaterialTheme.colorScheme
BannerAd(adName = "exit_banner")
```

#### API

| Hàm | Mô tả |
|-----|-------|
| `Ads.init(config)` | Khai báo `AdsConfig` (gọi 1 lần, trước mọi thao tác) |
| `Ads.start(activity) { }` | Thu thập consent (UMP) + init Mobile Ads, xong gọi callback |
| `Ads.preload(context, vararg names)` | Nạp trước vào cache theo định dạng từng tên |
| `Ads.isInterstitialReady(name)` | true nếu interstitial đã load sẵn |
| `Ads.showInterstitial(activity, name) { }` | Hiện nếu sẵn (+cooldown), không thì callback ngay + preload lại |
| `Ads.canRequestAds(activity)` | true nếu đủ điều kiện request ad (consent) |
| `Ads.clear()` | Hủy toàn bộ ad đang cache (vd khi mua gói no-ads) |
| `NativeAd(adName, modifier)` | Composable native (cache-first, fallback inline, theme-aware) |
| `BannerAd(adName, modifier)` | Composable banner adaptive (load inline, full-width khung) |

---

### :analytics — Event Tracking (hạ tầng dùng chung)

**Hạ tầng** tracking dùng chung cho mọi app — module KHÔNG chứa event cụ thể. Mặc định bắn
**Firebase Analytics**; app có thể thêm sink riêng (vd gửi backend) qua `AnalyticsConfig`. Mỗi app
tự khai báo danh sách event của mình rồi gọi `Analytics.logEvent(...)`.

> Cần `google-services.json` + plugin `com.google.gms.google-services` cho Firebase. Không có thì
> dùng `AnalyticsConfig(firebaseEnabled = false)` + sink riêng.

#### Sử dụng

```kotlin
import com.anhnn.analytics.*

// 1. Khởi tạo 1 lần (Application.onCreate)
Analytics.init(context)                                   // chỉ Firebase
// hoặc thêm sink riêng:
Analytics.init(context, AnalyticsConfig(
    extraSinks = listOf(AnalyticsSink { name, params -> myBackend.send(name, params) })
))

// 2. screen_view tự động — đặt cạnh NavHost
TrackScreenViews(navController)

// 3. App tự định nghĩa event của mình rồi log
object Events { const val CHART_CREATE = "chart_create" }
Analytics.logEvent(Events.CHART_CREATE, mapOf("gender" to g, "lich_type" to t))

// 4. User property + consent
Analytics.setUserProperty("theme", "dark")
Analytics.setEnabled(hasConsent)                          // tắt thu thập khi user từ chối
```

#### API

| Hàm | Mô tả |
|-----|-------|
| `Analytics.init(context, config)` | Khởi tạo (Firebase + sink tùy chọn) |
| `Analytics.logEvent(name, params)` | Ghi event; params hỗ trợ String/Long/Int/Double/Boolean |
| `Analytics.setScreen(name)` | Ghi `screen_view` thủ công |
| `Analytics.setUserProperty(key, value)` | Đặt user property |
| `Analytics.setEnabled(value)` | Bật/tắt thu thập (gắn consent) |
| `TrackScreenViews(navController)` | Composable tự bắn `screen_view` theo điều hướng |

Tên event/khóa tự chuẩn hóa theo ràng buộc Firebase (snake_case, cắt độ dài). **Không log PII** —
chỉ enum/boolean (app tự đảm bảo).

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
