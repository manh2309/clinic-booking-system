## Đánh dấu tất cả thông báo đã đọc — 20/10/2026

`PATCH /api/v1/notifications/me/read-all`, body `{}`, JWT PATIENT/DOCTOR. Trả `data` là số thông báo vừa chuyển sang đã đọc (lặp lại trả 0). Một câu UPDATE giới hạn recipient hiện tại và is_read=false, giữ readAt của mục đã đọc. Không chỉ xử lý trang đang xem; thông báo mới phát sinh sau câu UPDATE vẫn chưa đọc. Không cần migration.

Web dùng dropdown tại chuông cho cả patient/doctor, hỗ trợ phân trang tại chỗ và đọc từng mục/đọc hết. Kiểm chứng: MedicalNotificationIntegrationTests 10/10 trên H2, gồm quyền sở hữu/readAt/idempotency và hai vai trò; web91 tests + production build pass. Chưa chạy lại trên MySQL cho thay đổi này.

# Kết quả khám và thông báo

Các endpoint yêu cầu Bearer token. Không gửi token thật vào tài liệu hoặc commit.

## Kết quả khám

| Role | Method và đường dẫn | Kết quả |
|---|---|---|
| DOCTOR | POST /api/v1/doctors/me/appointments/{appointmentId}/complete | Lưu kết quả và hoàn thành lịch hẹn |
| DOCTOR | GET /api/v1/doctors/me/appointments/{appointmentId}/medical-record | Kết quả của ca do mình phụ trách |
| PATIENT | GET /api/v1/patient/me/medical-records?page=0&size=10 | Trang kết quả của mình, giờ khám giảm dần |
| PATIENT | GET /api/v1/patient/me/medical-records/{appointmentId} | Kết quả một lịch hẹn của mình |

Body hoàn thành:

```json
{
  "diagnosis": "Nội dung chẩn đoán do bác sĩ nhập",
  "examinationNotes": "Ghi nhận khi khám, có thể bỏ trống",
  "treatmentAdvice": "Hướng dẫn do bác sĩ nhập, có thể bỏ trống"
}
```

Mỗi trường tối đa 5.000 ký tự. Diagnosis không được chỉ có khoảng trắng. Lịch phải CONFIRMED và đã đến giờ bắt đầu, bác sĩ đúng chủ sở hữu và còn hoạt động. Sau khi lưu thành công, kết quả được chốt; lượt này chưa có chức năng sửa sau hoàn thành. Lịch cũ đã COMPLETED nhưng chưa có bản ghi không được tự sinh kết quả.

Response completion là AppointmentResponse với status COMPLETED. Response đọc kết quả có id, appointmentId, doctorId, doctorName, startAt, diagnosis, examinationNotes, treatmentAdvice, createdAt; các trường tùy chọn có thể null. Chi tiết nhận **appointmentId**, không phải medical_record_id. Admin không được đọc kết quả qua API patient/doctor. Không có bản ghi thuộc phạm vi người dùng trả 404/code 5001; hoàn thành qua PATCH status mà chưa có kết quả trả 409/code 5002.

## Thông báo

Cả PATIENT và DOCTOR dùng cùng API, luôn lấy theo người đang đăng nhập:

| Method và đường dẫn | Kết quả |
|---|---|
| GET /api/v1/notifications/me?page=0&size=10 | Trang thông báo, createdAt và id giảm dần |
| GET /api/v1/notifications/me/unread-count | data là số chưa đọc |
| PATCH /api/v1/notifications/me/{notificationId}/read | Đánh dấu đã đọc, body {} |

Mỗi thông báo có id, appointmentId, type, title, message, read, readAt, createdAt. Type gồm APPOINTMENT_BOOKED, APPOINTMENT_CONFIRMED, APPOINTMENT_CANCELED, APPOINTMENT_COMPLETED, APPOINTMENT_EXPIRED, APPOINTMENT_NO_SHOW. Đánh dấu lặp giữ nguyên readAt; ID không thuộc người nhận trả 404/code 6001. Page >= 0, size 1..100; sai trả 400/code 1001. Không có nhắc lịch tự động/email/push trong lượt này.

## Thử tay

1. Khởi động lại BE để Flyway áp dụng V4, chạy FE mới. Dùng tài khoản test đã có; không đổi trực tiếp DB để giả lập trạng thái.
2. Patient đặt slot tương lai; patient và doctor đều thấy thông báo đặt lịch. Doctor xác nhận trước giờ bắt đầu; patient thấy cập nhật xác nhận.
3. Với lịch đã xác nhận và đã đến giờ khám, doctor chọn Ghi kết quả khám. Thử để trống chẩn đoán, sau đó nhập dữ liệu kiểm thử và Lưu kết quả và hoàn thành.
4. Patient mở Kết quả khám, kiểm tra đúng lịch/bác sĩ/nội dung. Refresh vẫn còn. Doctor mở Xem kết quả.
5. Đổi sang patient/doctor khác: không đọc được chi tiết kết quả trên. Đánh dấu thông báo của mình, refresh số chưa đọc phải giảm và giữ nguyên.
6. Thử hủy lịch tương lai hoặc để PENDING qua giờ kết thúc; kiểm tra thông báo tương ứng. Job hết hạn chạy mỗi 60 giây.

Chỉ dùng dữ liệu giả khi demo. Các test tự động dùng DB riêng; chưa thay thế kiểm tra toàn luồng bằng phiên đăng nhập thật.


## 20/10/2026 — weekly schedule / doctor leave

| Method | Route sau /api/v1 | Quyền |
|---|---|---|
| GET/POST | /admin/doctors/{doctorId}/weekly-schedules | ADMIN |
| POST | /admin/doctors/{doctorId}/schedules/generate | ADMIN |
| PATCH | /admin/doctors/{doctorId}/weekly-schedules/{id}/end?from=YYYY-MM-DD | ADMIN |
| GET/POST | /doctors/me/leaves | DOCTOR |
| PATCH | /doctors/me/leaves/{id}/withdraw | DOCTOR sở hữu |
| GET | /admin/leaves?doctorId=... | ADMIN |
| GET | /admin/leaves/{id}/affected | ADMIN |
| PATCH | /admin/leaves/{id}/review | ADMIN |
| PATCH | /admin/leaves/{id}/contacts/{contactId} | ADMIN |

POST weekly: `{ "dayOfWeek": 1, "startTime": "08:00:00", "endTime": "12:00:00", "effectiveFrom": "2026-10-26", "effectiveTo": "2026-12-20" }`.
Response POST/generate: `{created, conflicts[]}`. Ngày và thứ hiệu lực phải khớp mới có ca. POST weekly và generate được bọc cùng transaction.
POST leave: `{ "startAt": "2026-10-26T08:00:00", "endAt": "2026-10-26T12:00:00", "reason": "Nghỉ phép" }`.
PATCH review: `{ "decision": "APPROVED", "confirmCancellation": true, "reason": "Đã xác nhận" }`; decision khác được nhận là REJECTED. PENDING/APPROVED chặn slot; WITHDRAWN/REJECTED bỏ lý do chặn.
SlotResponse thêm blockReason nullable PENDING_LEAVE/APPROVED_LEAVE. ScheduleResponse thêm doctorAvailable. Patient available-slots chỉ trả slot có thể đặt và không lộ lý do xin nghỉ.
POST/DELETE/restore dưới /doctors/me/schedules hiện trả 403; GET vẫn giữ. Dữ liệu cũ không mất.
Config: clinic.schedules.horizon-days=30 (giới hạn 1..90), clinic.schedules.generation-enabled=true, clinic.schedules.generation-cron=0 5 0 * * *; cron/startup theo giờ phòng khám.


## Áp dụng mẫu tuần hàng loạt

ADMIN POST `/api/v1/admin/doctors/{doctorId}/weekly-schedules/batch`:
```json
{
  "daysOfWeek": [1,2,3,4,5],
  "windows": [{"startTime":"08:00","endTime":"12:00"},{"startTime":"13:00","endTime":"17:00"}],
  "effectiveFrom": "2026-10-20",
  "effectiveTo": "2027-01-18"
}
```
Response data: `{added, skipped, created, conflicts}`. skipped chỉ mẫu active cùng thứ, giờ và cả hai ngày hiệu lực. Giữ endpoint một mẫu cũ để tương thích script; web mới dùng batch duy nhất. Mẫu trùng một phần/vượt 8h/ngày trả 409 code 3014 kèm thứ/khung giờ lỗi, toàn bộ transaction rollback kể cả mẫu đã thêm trước đó. Bù/sinh ca vẫn giữ các conflict ca cũ trong response, không ghi đè dữ liệu cũ.

Kiểm chứng lượt này: toàn bộ BE 86 test H2 pass; 22 SchedulePlanningIntegrationTests pass trên MySQL 8 riêng (gồm 5 case batch). Web 75 test pass/build development pass, form render 390/1440px. Không có migration mới hoặc dữ liệu thật được tạo trong lượt triển khai.


## Ngày khám còn chỗ — 20/10/2026

Public GET `/api/v1/doctors/{doctorId}/available-dates?from=YYYY-MM-DD&days=30`.
- `from` mặc định ngày hiện tại theo Clock phòng khám; không trước hôm nay hoặc quá một năm tới. `days` 1..90, mặc định 30; khoảng ngày [from,from+days).
- data: `[{"date":"2026-10-26","availableSlots":8}]`, sắp tăng theo ngày; không trả ngày 0 slot.
- Dùng cùng query khả dụng với available-slots: ca active, slot AVAILABLE/chưa bắt đầu, không giao đơn nghỉ PENDING/APPROVED; doctor/account/chuyên khoa phải khả dụng. Không lộ lý do nghỉ.
- Số chỗ chỉ là snapshot; POST book vẫn giữ khóa và kiểm tra lại. Không giữ chỗ qua GET.
- Web/app tự mở ngày gần nhất lúc vào trang, giữ chọn ngày thủ công khi response discovery về trễ, cập nhật số chỗ từ lần tải slot mới. Lỗi tải slot/ngày riêng trạng thái không có dữ liệu.

Kiểm chứng: BE toàn bộ 90 test H2 pass; 26 test planning/availability pass trên MySQL 8 cô lập; web 83 test ChromeHeadless pass, development và production build pass. Flutter analyze sạch, 28 test pass, APK debug build pass. Client Dart chạy HTTP thật với Spring Boot + MySQL test: profile/auth, weekly generation, available-dates, book/confirm/cancel, count biến mất/trở lại, notices và records 404. Không kiểm tra medical record dương tính hoặc UI Android qua HTTP trong lượt này. Log BE/target/available-days-mysql-tests.log, available-days-http-server.log; mobile/build/available-days-http-smoke.log; FE/tmp/available-days-tests.log.
