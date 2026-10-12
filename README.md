# GreateGolf — ứng dụng golf cho Android

Kotlin + Jetpack Compose, Room, OpenStreetMap (osmdroid), Google Fused Location, ML Kit Pose Detection.
`minSdk 26`, `targetSdk 35`. Giao diện tiếng Việt.

| Tab | Tính năng |
|---|---|
| **Bản đồ** | 35 sân golf tại Việt Nam trên OpenStreetMap (tìm theo tên/tỉnh, lọc theo miền, vị trí của bạn, khoảng cách tới sân). Chọn sân → bắt đầu vòng / chơi với GPS. |
| **GPS** | Khoảng cách tới green (đứng trên green bấm "Đánh dấu green" một lần, lần sau app tự nhớ), gợi ý gậy, **tự đo khoảng cách cú đánh**: bấm *Đánh* (hoặc bật *tự phát hiện swing*), đi tới bóng — app tự chốt khi bạn đứng lại và lưu vào đúng gậy đã chọn. |
| **Scorecard** | Nhiều vòng 9/18 hố, par/gậy/putt từng hố, tổng, Out/In, so với par. |
| **Gậy** | Bộ gậy tùy chỉnh; mỗi gậy "map" với khoảng cách trung vị học từ các cú đánh (GPS hoặc nhập tay); nhập khoảng cách tới cờ → gợi ý gậy. |
| **Swing** | (1) Phát hiện swing bằng cảm biến gia tốc (điện thoại trong túi). (2) Quay/chọn video tại driving range → nhận diện tư thế trên máy, tìm các pha (address / đỉnh / impact / finish), tính nhịp swing, xoay vai/hông, X-factor, đầu dịch ngang, góc cột sống; vẽ khung xương lên ảnh và đưa ra nhận xét. |

## Cấu trúc

```
app/src/main/java/com/greategolf/
  domain/    logic thuần Kotlin (không phụ thuộc Android, có unit test):
             GeoMath, ShotTracker, ClubAdvisor, SwingDetector, SwingAnalysis
  data/      Room (rounds, scores, clubs, shots, swings, greens) + danh mục sân (assets/courses_vn.json)
  platform/  FusedLocation, cảm biến swing, phân tích video (ML Kit)
  ui/        Compose: map, play (GPS), score, clubs, swing
```

## Build

```
./gradlew testDebugUnitTest assembleDebug     # cần Android SDK (compileSdk 35), JDK 17
```
CI (`.github/workflows/android.yml`) chạy test và build APK debug.

## Lưu ý quan trọng

- **Toạ độ sân** trong `courses_vn.json` là *xấp xỉ* (đến cấp khu vực), chưa kiểm chứng từng sân; par mặc định 72. Nên rà soát/bổ sung (địa chỉ, số điện thoại, layout từng hố) trước khi phát hành.
- Chưa có dữ liệu vị trí green sẵn: người chơi tự đánh dấu. Có thể thay bằng dataset thật sau.
- Phân tích video là heuristic 2D (xoay vai/hông ước lượng từ độ rộng vai/hông, chỉ có nghĩa khi quay chính diện) — công cụ tham khảo, không phải đo lường chính xác. Ngưỡng của `SwingDetector` (cảm biến) cần hiệu chỉnh trên thiết bị thật.
- Đo cú đánh bằng GPS chỉ chạy khi tab GPS đang mở (giữ màn hình sáng). Bước tiếp theo hợp lý: foreground service.
- Bản đồ dùng tile OpenStreetMap công cộng — phù hợp phát triển/thử nghiệm; bản phát hành quy mô lớn nên dùng nhà cung cấp tile riêng.
