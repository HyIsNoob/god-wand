# Audio Chiêu 4 (Cosmic Supernova) — Track liền mạch từ lúc charge đến khi kết thúc

Thay file `src/main/resources/assets/godwand/sounds/supernova_sequence.ogg` bằng file audio **OGG Vorbis** của bạn.

File đi kèm hiện tại là placeholder im lặng. Khi chưa thay file (hoặc dùng placeholder), mod tự động dùng âm tụ lực Beacon và các âm thanh va chạm mặc định của Minecraft. Khi bạn thay bằng file âm thanh tùy chỉnh khác, mod sẽ tự nhận diện và phát track âm thanh này từ lúc bắt đầu giữ chuột vận chiêu (charge) cho đến khi vụ nổ tan biến hoàn toàn.

Nếu người chơi nhả chuột sớm trước khi nạp đủ lực (hủy chiêu), bài nhạc sẽ tự động dừng ngay lập tức.

## Bảng mốc thời gian chi tiết (tính ở 20 TPS)

Mốc **0,00 s** là thời điểm **bắt đầu nhấn giữ chuột phải** để vận chiêu.

| Thời gian | Trạng thái / Animation |
| --- | --- |
| **0,00 s** | Bắt đầu giữ chuột (charge). Bầu trời bắt đầu rách, áp lực tích tụ dần. |
| **0,00–7,00 s** | Giai đoạn vận chiêu (140 tick). Vết rách không gian mở rộng trên bầu trời. |
| **7,00 s** | Đủ lực, hố đen giáng thế tại mục tiêu đã khóa (bắt đầu chuỗi Supernova). |
| **7,00–19,50 s** | Hố đen quay và hút các khối xung quanh vào tâm. |
| **19,50–23,20 s** | Bắt đầu co thắt, nuốt khối dồn dập với cường độ cực cao. |
| **23,20–23,50 s** | Khoảng lặng chân không (vacuum silence), hố đen co lại cực nhỏ. |
| **23,50 s** | Vòng sóng xung kích (pre-impact shockwave) bung tản nhanh ra xung quanh (trước va chạm 0.5s). |
| **24,00 s** | Lõi cắm xuống đất, flash lóa sáng toàn màn hình, anime impact frame. |
| **24,40 s** | Vụ đại nổ bùng phát, địa hình bị xé toạc, rung chấn toàn khu vực. |
| **24,40–38,40 s** | Vùng nổ tàn lửa và dư chấn lắng dần, bầu trời khép lại. |
| **38,40 s** | Kết thúc hoàn toàn chuỗi hiệu ứng. |

## Khuyến nghị khi làm file âm thanh

- **Độ dài file khuyến nghị**: Khoảng **38 đến 40 giây**.
- **Định dạng**: Ogg Vorbis (.ogg). Không đổi đuôi trực tiếp từ MP3/WAV mà phải export bằng codec Vorbis.
- File phát trên thanh âm lượng **Players** và có độ giảm âm theo khoảng cách đối với người xem xung quanh.
