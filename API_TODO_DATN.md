# Checklist API để tiếp tục đồ án Clinic Booking

Đối chiếu ngày 05/10/2026 với mã nguồn Backend, đề cương DATN và báo cáo TTTN `NguyenTienManh_5250094_Bao_cao_TTTN_Clinic_Booking_final.docx`.

Mục tiêu trước mắt: hoàn thiện quản lý hồ sơ và dữ liệu quản trị, sau đó tích hợp Web vào luồng đặt lịch đã có. Các endpoint đánh dấu **đề xuất** dưới đây chưa được triển khai; đây là phương án tổ chức API, không phải tên endpoint bắt buộc trong đề cương.

## 1. Báo cáo giúp gì

- Mục 2.3.2: xác định schema hiện có gồm accounts, roles, specialties, doctors, doctor_schedules, schedule_slots, appointments. Chưa có bảng hồ sơ bệnh nhân, kết quả khám hay thông báo.
- Mục 2.3.4–2.3.5: mô tả contract xác thực và kiểm tra 401/403; dùng làm cơ sở kiểm thử tiếp.
- Mục 2.3.7–2.3.8: quy tắc sinh slot 30 phút, chống lịch chồng lấn và đặt trùng slot; không cần xây lại module này từ đầu.
- Mục 3.1: tạo bác sĩ/chuyên khoa và đặt lịch đã có minh chứng; Angular mới có khung. Từ “quản lý” trong phần mô tả không chứng minh đã đủ sửa, xóa, khóa/mở.
- Mục 4.3: là định hướng mở rộng. Theo đề cương DATN đã đọc, ưu tiên hồ sơ kết quả khám và thông báo; payment, đơn thuốc, refresh token, audit log và CI/CD chưa phải mục bắt buộc.

## 2. API hiện có — giữ lại và kiểm thử

Tất cả đường dẫn trong bảng có tiền tố `/api/v1`. “Có” nghĩa là có controller/service trong source, không khẳng định tất cả đã kiểm thử lại trên bản hiện tại.

| Method | Endpoint | Quyền | Công dụng |
|---|---|---|---|
| POST | /auth/register | Public | Đăng ký PATIENT; trả AccountResponse, không cấp token |
| POST | /auth/login | Public | Trả accessToken và account |
| GET | /roles | ADMIN | Danh sách vai trò |
| GET | /accounts | ADMIN | Danh sách tài khoản |
| GET | /accounts/{id} | ADMIN | Xem tài khoản |
| GET | /specialties | Public | Danh sách chuyên khoa hoạt động |
| POST | /admin/specialties | ADMIN | Tạo chuyên khoa |
| GET | /doctors | Public | Tìm theo keyword, specialtyId; phân trang |
| GET | /doctors/{id} | Public | Chi tiết bác sĩ hoạt động |
| POST | /admin/doctors | ADMIN | Tạo tài khoản DOCTOR và hồ sơ bác sĩ |
| POST | /doctors/me/schedules | DOCTOR | Tạo lịch và sinh slot |
| GET | /doctors/me/schedules | DOCTOR | Xem lịch của mình |
| DELETE | /doctors/me/schedules/{id} | DOCTOR | Vô hiệu hóa lịch của mình; từ chối khi có slot BOOKED |
| GET | /doctors/{doctorId}/available-slots?date=YYYY-MM-DD | Public | Xem slot trống |
| POST | /appointments | PATIENT | Đặt lịch với body {"slotId":123} |
| GET | /appointments/me | PATIENT | Danh sách lịch của mình, phân trang |
| PATCH | /appointments/{id}/cancel | PATIENT | Hủy lịch của mình và trả slot về AVAILABLE |
| GET | /doctors/me/appointments | DOCTOR | Danh sách lịch hẹn của bác sĩ, phân trang |
| PATCH | /doctors/me/appointments/{id}/status | DOCTOR | Body {"status":"CONFIRMED"} hoặc {"status":"COMPLETED"} |

Hiện có chuyển trạng thái PENDING → CONFIRMED → COMPLETED; bệnh nhân hủy PENDING/CONFIRMED trước giờ khám. Lịch hẹn COMPLETED chưa phải hồ sơ kết quả khám.

## 3. Nhóm A — làm ngay: hồ sơ bệnh nhân

API **đề xuất**, tiền tố `/api/v1`:

| Checklist | Endpoint | Input / output chính |
|---|---|---|
| [ ] | GET /patients/me/profile | Trả fullName, dateOfBirth, gender, address, phone, email |
| [ ] | PUT /patients/me/profile | Cập nhật fullName, dateOfBirth, gender, address, phone; email tạm chỉ đọc |

Thiết kế tối thiểu đề xuất: thêm bảng patient_profiles có account_id UNIQUE, full_name, date_of_birth, gender, address. Phone/email tiếp tục lấy từ accounts, tránh lưu trùng. Đây là lựa chọn triển khai, không phải schema bắt buộc trong báo cáo.

Các bước làm:
1. Thêm migration mới V2, không sửa V1 của DB đã chạy.
2. Tạo PatientProfile entity, repository, DTO request/response, service và controller.
3. Lấy accountId từ CurrentAccount; request không nhận accountId/role/isActive.
4. Với tài khoản cũ chưa có profile: GET trả dữ liệu tài khoản và các trường hồ sơ null; PUT tạo profile lần đầu.
5. Validate tên, ngày sinh không nằm trong tương lai, enum giới tính và độ dài trường. Không bắt nhập dữ liệu khám bệnh ở bước này.

Đạt khi: PATIENT đọc/sửa hồ sơ mình được; thiếu token bị 401; DOCTOR/ADMIN bị 403 theo thiết kế endpoint này; body không thể sửa quyền hay tài khoản người khác. Hai patient đăng nhập khác nhau nhận hai hồ sơ khác nhau.

## 4. Nhóm B — quản trị tài khoản, bác sĩ, chuyên khoa

API **đề xuất**, ADMIN, tiền tố `/api/v1`. Giữ GET /accounts hiện có để tránh đổi contract không cần thiết.

| Checklist | Endpoint | Nội dung cần làm |
|---|---|---|
| [ ] | PATCH /admin/accounts/{id}/status | Body {"isActive":false} để khóa, true để mở |
| [ ] | PUT /admin/doctors/{id} | Sửa fullName, qualification, description, specialtyId; không sửa password/role qua DTO này |
| [ ] | PATCH /admin/doctors/{id}/status | Body {"active":false/true}; điều khiển bác sĩ có nhận lịch mới không |
| [ ] | PUT /admin/specialties/{id} | Sửa name, description; kiểm tra trùng tên, bỏ qua chính bản ghi đang sửa |
| [ ] | PATCH /admin/specialties/{id}/status | Body {"active":false/true}; vô hiệu hóa/mở lại chuyên khoa |
| [ ] | GET /admin/doctors | Danh sách quản trị gồm cả bác sĩ inactive, phân trang/lọc active |
| [ ] | GET /admin/specialties | Danh sách quản trị gồm cả chuyên khoa inactive |
| [ ] | GET /admin/appointments | Theo dõi toàn hệ thống, phân trang; lọc status, doctorId, patientId và khoảng ngày |

Rule tối thiểu đề xuất:
- Không xóa vật lý bác sĩ/chuyên khoa đang được lịch sử tham chiếu.
- Không cho admin tự khóa mình qua API status.
- Khóa account khác với vô hiệu hóa doctor: account khóa thì không truy cập API; doctor inactive thì ngừng nhận lịch mới. Cần giải thích rõ hai trạng thái trên giao diện.
- Từ chối vô hiệu hóa doctor nếu còn lịch PENDING/CONFIRMED chưa xử lý. Khi vô hiệu hóa thành công, chặn cả xem slot để đặt mới lẫn POST /appointments bằng slotId cũ.
- Từ chối vô hiệu hóa specialty khi còn doctor active thuộc chuyên khoa đó; chuyển/vô hiệu hóa bác sĩ trước.
- POST /appointments phải kiểm tra trạng thái doctor, account bác sĩ và specialty ngay lúc đặt; không chỉ dựa vào danh sách slot mà client đã tải trước đó.
- GET quản trị không trả password/hash; trả rõ active/isActive để Web quản lý được.

Đạt khi: ADMIN thao tác được; PATIENT/DOCTOR bị 403; id không tồn tại trả lỗi not found; khóa tài khoản khiến token cũ không còn truy cập được; vô hiệu hóa không làm mất lịch sử.

## 5. Nhóm C — kiểm tra lại luồng đặt lịch rồi nối Web

Không tạo lại các endpoint đã có. Trước khi tích hợp, kiểm tra:
- [ ] Tạo lịch hợp lệ sinh đúng slot; overlap bị từ chối; lịch quá khứ/thời gian lệch mốc 30 phút bị từ chối.
- [ ] Hai request đặt cùng slot chỉ một request thành công.
- [ ] Patient khác không hủy được lịch của người khác.
- [ ] Doctor khác không cập nhật được lịch hẹn không thuộc mình.
- [ ] Hủy lịch hợp lệ trả slot về AVAILABLE; không hủy lịch đã COMPLETED hoặc đã đến giờ khám theo rule hiện có.
- [ ] Thống nhất liệu một patient được đặt hai slot chồng thời gian của hai bác sĩ không. Đề xuất chặn; hiện source mới thấy chặn trùng cùng slot.
- [ ] Kiểm tra tạo lại lịch sau khi vô hiệu hóa: unique key doctor_id/start_at có thể xung đột với slot BLOCKED cũ; chọn tái sử dụng slot hoặc quy tắc không tạo lại rõ ràng.
- [ ] Đồng bộ Web đăng ký: Backend yêu cầu password 8–64 ký tự; đăng ký thành công chuyển sang login vì register không trả token.

Thứ tự màn hình: tìm bác sĩ → chi tiết/slot theo ngày → đặt lịch → lịch của tôi/hủy → lịch làm việc bác sĩ → danh sách hẹn/xác nhận/hoàn thành → trang quản trị.

## 6. Nhóm D — làm sau: kết quả khám và thông báo

API **đề xuất**, tiền tố `/api/v1`:

| Checklist | Endpoint | Quyền và mục đích |
|---|---|---|
| [ ] | PUT /doctors/me/appointments/{id}/medical-record | DOCTOR phụ trách; ghi/cập nhật diagnosis, examinationNotes, treatmentAdvice |
| [ ] | GET /doctors/me/appointments/{id}/medical-record | DOCTOR phụ trách; xem kết quả đã ghi |
| [ ] | GET /patients/me/medical-records | PATIENT; lịch sử kết quả khám của mình, phân trang |
| [ ] | GET /patients/me/medical-records/{id} | PATIENT sở hữu; xem chi tiết |
| [ ] | GET /notifications/me | Người đã đăng nhập; chỉ thông báo của mình, phân trang |
| [ ] | PATCH /notifications/{id}/read | Chủ thông báo; đánh dấu đã đọc |

Thêm bảng medical_records (appointment_id UNIQUE) và notifications. Chốt thời điểm ghi kết quả: đề xuất ghi khi appointment CONFIRMED và yêu cầu có kết quả trước khi chuyển COMPLETED. Giữ một giao dịch để tránh lưu kết quả dở dang. Chính sách sửa sau COMPLETED cần quy định rõ trước khi triển khai.

Thông báo tạo tự động khi book/confirm/cancel, chỉ lưu khi thao tác nghiệp vụ thành công. Ban đầu dùng thông báo trong ứng dụng; email/push không bắt buộc theo câu chữ đề cương. Không cần API để client tự tạo thông báo.

## 7. Lộ trình làm từng việc

1. Chạy lại Backend và test; ghi rõ môi trường, lỗi còn lại. Lần kiểm tra trước bị thiếu parent dependency Maven offline, không chứng minh lỗi nghiệp vụ.
2. Làm GET/PUT hồ sơ bệnh nhân (nhóm A) và kiểm tra bằng hai token patient.
3. Làm khóa/mở account.
4. Làm sửa/trạng thái specialty, doctor và danh sách quản trị.
5. Làm GET lịch hẹn toàn hệ thống; rà rule trạng thái và ownership.
6. Sửa contract đăng ký Web; nối luồng Web bệnh nhân bằng API đã có.
7. Nối Web bác sĩ/admin, bổ sung kết quả khám và thông báo.
8. Flutter dùng lại các API patient; sau đó kiểm thử demo và hoàn thiện tài liệu DATN.

Mỗi endpoint chỉ đánh dấu hoàn tất sau: DTO → service → controller → quyền → validation → Postman happy path và case lỗi → ghi request/response để FE dùng. Không cần hoàn thiện toàn bộ Backend mới được tích hợp một luồng Web đã ổn.

Việc đầu tiên: GET /api/v1/patients/me/profile, rồi PUT cùng đường dẫn. Chưa cần làm đồng thời tất cả các mục trong file này.
