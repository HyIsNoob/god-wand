# Audio chiêu 3 — một file cho cả bốn cấp

Thay file `src/main/resources/assets/godwand/sounds/earth_drill_sequence.ogg` bằng audio **OGG Vorbis** của bạn. Cả bốn cấp dùng cùng file, phát một lần với pitch 1.0. Không cần tạo biến thể theo cấp hay sửa `sounds.json`.

File đi kèm hiện là placeholder im lặng. Mod nhận diện đúng file placeholder và dùng chuỗi âm Minecraft thay thế; sau khi thay bằng audio khác, mod tự dùng file mới và bỏ chuỗi âm thay thế. Chiêu 4 vẫn giữ file audio riêng như trước.

Mốc 0 là lúc **thả chuột để kích hoạt**, khi đá bắt đầu được kéo lên. Thời gian giữ chuột để chọn cấp không nằm trong file audio vì người chơi tự quyết định giữ bao lâu.

| Thời gian ở 20 TPS | Animation chung cho cấp 1–4 |
| --- | --- |
| 0,00 s | Kích hoạt, lõi và đá bắt đầu hình thành |
| 0–1,90 s | Kéo đá lên, xoay quanh lõi |
| 1,95–4,40 s | Đá tập hợp thành drill |
| 4,45–7,90 s | Tăng tốc xoáy, nóng đỏ |
| 7,95 s | Bắt đầu đâm xuống |
| 8,35 s | Va chạm và nổ |
| 8,60 s | Drill biến mất, đá văng lên |
| 8,80–10,00 s | Đuôi âm / dư chấn, fade out |

Nên làm file dài khoảng 9–10 giây, kết thúc trước 10 giây. Cấp cao đổi kích thước, độ cao và hố phá; các mốc animation/va chạm trên giữ nguyên. Việc đào hố cấp cao có thể kéo dài hơn một chút sau va chạm, nên dùng đuôi âm dư chấn thay vì một nhịp cứng ở cuối.

Sau khi thay file trong source cần build và chép lại JAR, rồi mở lại game. Có thể thay cùng đường dẫn qua resource pack và reload tài nguyên. Server bị tụt TPS có thể khiến animation chậm hơn audio phát theo thời gian thực; bảng trên tính ở 20 TPS.

Âm mặc định phát tại phía người nghe với mức giảm âm theo khoảng cách, tránh tiếng tạo drill trên cao bị suy giảm gần hết. Đổi skill sau khi kích hoạt không cắt audio của drill đang chạy; một chuỗi drill mới hoặc rời world sẽ dừng chuỗi cũ.
