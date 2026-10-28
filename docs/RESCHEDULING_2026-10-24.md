# Đổi giờ khám có xác nhận bệnh nhân

## Phạm vi đã triển khai

Admin đề xuất một giờ còn trống với cùng bác sĩ. Bệnh nhân xem lịch sử và chấp nhận hoặc từ chối trên website hoặc Android. Admin có thể rút đề xuất đang chờ. Không đổi bác sĩ, không thêm thanh toán hoặc hàng chờ trong lượt này.

- Lịch nguồn PENDING/CONFIRMED phải chưa bắt đầu. Lịch CANCELED chỉ được mời đặt lại khi có leave_contact chứng minh đã bị hủy trong luồng duyệt nghỉ.
- Chỉ có một đề xuất chờ cho mỗi lịch nguồn. Khi lịch nguồn đã có đề xuất được chấp nhận, không cho tạo thêm lời mời từ lịch đó. Có thể đổi tiếp từ lịch mới nếu đủ điều kiện.
- Hạn phản hồi nằm trong 7 ngày, trước giờ mới và trước giờ cũ nếu lịch cũ còn hiệu lực. Đề xuất không giữ cứng slot mới.
- Chấp nhận kiểm tra lại bác sĩ/account/chuyên khoa, ca hoạt động, slot tương lai còn trống, đơn nghỉ và lịch khác của bệnh nhân trùng giờ. Loại lịch nguồn khỏi phép kiểm tra trùng giờ.
- Tạo appointment mới PENDING để bác sĩ xác nhận. Chỉ sau khi đặt thành công mới hủy lịch nguồn còn hiệu lực và giải phóng slot cũ. Lịch đã hủy do nghỉ giữ nguyên lý do và trạng thái cũ.
- Appointment mới, thay đổi lịch cũ, slot, phản hồi và thông báo cùng transaction. Không chuyển hoặc sao chép bệnh án cũ.
- Hết hạn hoặc lịch nguồn không còn phù hợp được trả về EXPIRED/INVALIDATED ngay khi đọc hoặc phản hồi; không cần chờ job. Bản ghi PENDING quá hạn được chốt trạng thái và bỏ khóa duy nhất khi admin tạo đề xuất tiếp theo.
- Mã người đề xuất lấy từ phiên admin, không nhận từ client. Mỗi phản hồi lưu thời điểm và liên kết lịch thay thế; patient chỉ đọc và phản hồi lịch của mình.

## API

| Phương thức | Đường dẫn sau /api/v1 | Quyền |
|---|---|---|
| POST | /admin/appointments/{appointmentId}/reschedules | ADMIN |
| GET | /admin/appointments/{appointmentId}/reschedules | ADMIN |
| PATCH | /admin/appointments/{appointmentId}/reschedules/{id}/withdraw | ADMIN |
| GET | /appointments/{appointmentId}/reschedules | PATIENT chủ sở hữu |
| PATCH | /appointments/{appointmentId}/reschedules/{id}/accept | PATIENT chủ sở hữu |
| PATCH | /appointments/{appointmentId}/reschedules/{id}/decline | PATIENT chủ sở hữu |

POST nhận `slotId`, `reason` tối đa 500 ký tự và `expiresAt` theo giờ phòng khám, ví dụ `2026-10-25T17:00:00`. Không nhận doctorId hoặc patientId từ giao diện. Kết quả có giờ mới, trạng thái, người đề xuất, thời gian phản hồi và replacementAppointmentId.

Migration V6 thêm reschedule_proposals và các khóa ngoại/khóa duy nhất, V7 mở rộng loại thông báo trên H2/MySQL. Không sửa migration đã có, không xóa lịch cũ. Restart backend để Flyway áp dụng. Không chạy SQL tay.

## Màn hình

- Admin → Lịch hẹn → Đổi giờ / Lịch sử → chọn ngày còn chỗ, giờ mới, hạn phản hồi và lý do → Gửi đề xuất.
- Patient web → Lịch khám → Đề xuất đổi giờ / Lịch sử → Chọn giờ mới → xác nhận hoặc Từ chối.
- Patient app → Lịch hẹn → Đề xuất đổi giờ / Lịch sử → Chấp nhận giờ mới hoặc Từ chối → xác nhận.
- Thông báo in-app dẫn về lịch hẹn. Chưa có push/SMS/email. Lịch thay thế xuất hiện trong danh sách chờ xác nhận của bác sĩ.

## Kiểm chứng ngày 24/10/2026

- Toàn bộ bộ test H2 chạy thành công sau triển khai ban đầu, gồm 12 trường hợp đổi lịch. Sau đó bổ sung một trường hợp HTTP đầy đủ.
- Bộ đổi lịch cuối có 13 test đạt trên MySQL 8.0.39 riêng ở cổng 13326. Gồm HTTP admin đề xuất → bệnh nhân nhận → bác sĩ xác nhận; nhận lặp; hai bệnh nhân tranh một slot; hủy do nghỉ rồi đặt lại; hết hạn; sai quyền; trùng lịch bệnh nhân; đơn nghỉ mới; notification failure rollback.
- Web: 95 test ChromeHeadless đạt, production build thành công.
- Flutter: 31 test đạt, analyze không có vấn đề; build APK debug thành công.
- Bằng chứng backend: target/reschedule-all-tests.log, target/reschedule-mysql-tests.log và target/reschedule-mysql-results.xml. Web: FE/tmp/reschedule-web-tests.log, FE/tmp/reschedule-build-verified.log. Mobile: build/reschedule-tests.log, build/reschedule-analyze.log, build/reschedule-apk.log.
- Chưa nghiệm thu trực tiếp trọn luồng trên phiên web/emulator của người dùng. Không coi widget test hoặc build APK là nghiệm thu sử dụng thực tế.

## Test tay trước khi chốt demo

1. Restart backend, làm mới web, chạy lại app/APK mới. Chuẩn bị cùng bác sĩ có ít nhất hai giờ trống tương lai; dùng tài khoản test riêng.
2. Bệnh nhân đặt, bác sĩ xác nhận. Admin gửi đề xuất giờ khác, hạn trước cả hai giờ khám. Kiểm tra lịch cũ vẫn CONFIRMED và giờ mới vẫn có thể đặt bởi người khác.
3. Patient nhận trên web; xem thông báo và lịch sử trên app. Đồng ý trên một nền tảng. Cả hai nền tảng thấy lịch cũ CANCELED và lịch mới PENDING. Bác sĩ xác nhận lịch mới.
4. Với lịch khác, từ chối; kiểm tra lịch cũ không đổi. Admin tạo lại và rút; bệnh nhân không nhận được đề xuất đã rút.
5. Dùng tài khoản khác đặt mất giờ mới trước khi bệnh nhân đồng ý. Hệ thống báo xung đột và giữ lịch cũ. Không dùng việc tải danh sách giờ trước đó làm bằng chứng slot đã giữ.
6. Tạo đề xuất hạn gần để test hết hạn; thử nhận trên tab cũ. Backend từ chối, không tạo lịch mới.
7. Duyệt nghỉ có lịch đã đặt, rồi mời đặt lại ngoài khoảng nghỉ. Chấp nhận chỉ tạo lịch mới, không khôi phục lịch đã hủy và không đánh dấu contact task là đã liên hệ.
8. Bấm nhanh nhiều lần, mất mạng và mở lại màn hình. Khi kết quả chưa rõ phải tải lại lịch sử/danh sách trước khi thử lại; client không tự retry PATCH.

Ngày 26/10 đã bổ sung tiếp nhận/hàng chờ; xem RECEPTION_QUEUE_2026-10-26.md. Đổi lịch bị chặn khi bệnh nhân đã tiếp nhận hoặc đang khám; cần rút tiếp nhận có lý do trước nếu còn chờ. Bản thảo Word trước đó vẫn mô tả đổi lịch là đề xuất; chỉ cập nhật chương kết quả sau khi có ảnh nghiệm thu cuối.
