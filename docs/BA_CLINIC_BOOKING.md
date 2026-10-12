# Phân tích nghiệp vụ hệ thống Clinic Booking

Ngày đối chiếu: 05/10/2026. Phiên bản 1, dùng để đọc hiểu project và phát triển bằng tay.

Nguồn: đề cương DATN; báo cáo TTTN bản final; source Backend tại `D:/of/your_self/clinic-booking-system`; Web tại `D:/of/DATN_GTVT/FE`. Đây là bản phân tích làm việc, chưa phải đặc tả đã được giảng viên phê duyệt.

## 1. Cách đọc và phạm vi

Hệ thống giải quyết việc bệnh nhân tìm bác sĩ, chọn giờ khám và theo dõi lịch; bác sĩ khai báo giờ làm việc, xử lý lịch hẹn và ghi kết quả; admin quản lý tài khoản và dữ liệu dùng chung.

| Nhãn trong tài liệu | Ý nghĩa |
|---|---|
| Hiện có | Đã tìm thấy xử lý trong source; không đồng nghĩa đã kiểm thử lại trên DB hiện tại |
| Có một phần | Có một số thao tác, thiếu các thao tác hoặc rule khác |
| Đề xuất | Thiết kế để triển khai tiếp; chưa coi là hành vi hiện tại |
| Cần chốt | Có nhiều cách xử lý; cần chọn một cách trước khi code |

Ảnh DB gần nhất cho thấy 7 bảng nghiệp vụ của V1 và flyway_schema_history. File V2 đã được viết để thêm patient_profiles, medical_records, notifications; chưa có xác nhận V2 đã chạy trên DB. Chưa thấy Entity/Service/Controller cho ba module mới trong source được kiểm tra.

Phạm vi DATN: Web ba vai trò, Android cho bệnh nhân, tài khoản/phân quyền, hồ sơ cá nhân, bác sĩ/chuyên khoa, lịch làm việc, đặt/hủy/xác nhận/hoàn thành lịch, kết quả khám, thông báo. Payment, đơn thuốc chi tiết, refresh token, audit log, chat, khám trực tuyến không nằm trong phạm vi tối thiểu đã đọc. Không mở thêm các module này khi luồng chính chưa hoàn chỉnh.

## 2. Từ điển nghiệp vụ

| Khái niệm | Giải thích bằng ví dụ | Bảng |
|---|---|---|
| Account | Thông tin để đăng nhập: username, password hash, email, phone, trạng thái | accounts |
| Role | Quyền sử dụng: PATIENT, DOCTOR, ADMIN; mỗi account hiện có một role | roles |
| Patient profile | Thông tin cá nhân bệnh nhân: tên, ngày sinh, giới tính, địa chỉ | patient_profiles, đề xuất V2 |
| Doctor | Hồ sơ nghề nghiệp bác sĩ, liên kết với account DOCTOR và một chuyên khoa | doctors |
| Specialty | Danh mục như Tim mạch, Da liễu | specialties |
| Doctor schedule | Một khoảng làm việc của bác sĩ, ví dụ ngày 10/10 từ 09:00 đến 12:00 | doctor_schedules |
| Schedule slot | Một phần 30 phút của lịch làm việc, ví dụ 09:00–09:30 | schedule_slots |
| Appointment | Một lần bệnh nhân đặt một slot; có vòng đời riêng | appointments |
| Medical record | Kết quả của lần khám, không phải thông tin cá nhân và không phải lịch hẹn | medical_records, đề xuất V2 |
| Notification | Thông báo sự kiện cho một tài khoản cụ thể | notifications, đề xuất V2 |

Schedule, slot và appointment khác nhau: bác sĩ tạo một schedule → hệ thống sinh nhiều slot → bệnh nhân đặt một slot → hệ thống tạo một appointment. Bệnh nhân hủy appointment thì bản ghi lịch hẹn vẫn tồn tại, slot có thể được người khác đặt lại.

## 3. Actor và quyền

Ma trận này mô tả quyền hiện có và quyền mục tiêu. Những ô có chữ “đề xuất” chưa được triển khai.

| Chức năng | Khách chưa đăng nhập | PATIENT | DOCTOR | ADMIN |
|---|---|---|---|---|
| Xem chuyên khoa, bác sĩ, slot trống | Có | Có | Có | Có |
| Tự đăng ký PATIENT | Có | Không cần | Không cần | Không cần |
| Đặt lịch | Không | Của mình | Không | Không |
| Xem/hủy lịch bệnh nhân | Không | Của mình | Không | Theo dõi toàn hệ thống, đề xuất |
| Tạo/xem/vô hiệu hóa lịch làm việc | Không | Không | Của mình | Không ở phạm vi tối thiểu |
| Xác nhận/hoàn thành lịch hẹn | Không | Không | Lịch thuộc mình | Không ở phạm vi tối thiểu |
| Xem/sửa hồ sơ bệnh nhân | Không | Của mình, đề xuất | Không qua API profile | Không qua API profile |
| Ghi kết quả khám | Không | Không | Lịch thuộc mình, đề xuất | Không |
| Xem kết quả khám | Không | Của mình, đề xuất | Lịch thuộc mình, đề xuất | Không mặc định |
| Tạo/sửa/vô hiệu hóa bác sĩ/chuyên khoa | Không | Không | Không | Có tạo; phần còn lại đề xuất |
| Xem/khóa/mở tài khoản | Không | Không | Không | Có xem; khóa/mở đề xuất |
| Đọc thông báo | Không | Của mình, đề xuất | Của mình, đề xuất | Của mình nếu có, đề xuất |

Phân quyền gồm hai câu hỏi: người này có đúng role không, và bản ghi này có thuộc người này không? Kiểm tra role tại SecurityConfig/@PreAuthorize; kiểm tra sở hữu trong service. Route guard của Web chỉ giúp điều hướng, Backend vẫn phải kiểm tra.

## 4. Danh sách yêu cầu chức năng

| Mã | Chức năng / mong muốn người dùng | Điều kiện hoàn tất nghiệp vụ | Hiện trạng | Ưu tiên |
|---|---|---|---|---|
| FR01 | Khách đăng ký bệnh nhân | Account PATIENT được tạo, mật khẩu hash, username/email không trùng | Có | Duy trì |
| FR02 | Người dùng đăng nhập | Tài khoản hợp lệ nhận token và thông tin role | Có | Duy trì |
| FR03 | Bệnh nhân xem/sửa thông tin cá nhân | Chỉ sửa hồ sơ của mình, không sửa role/isActive | Chưa có module; có SQL V2 | Làm đầu tiên |
| FR04 | Admin xem và khóa/mở tài khoản | Khóa ngăn truy cập API, dữ liệu lịch sử vẫn giữ | Có xem; thiếu khóa/mở | Tiếp theo |
| FR05 | Admin quản lý chuyên khoa | Tạo, sửa, vô hiệu hóa, mở lại; xử lý liên kết bác sĩ | Có tạo/xem active | Tiếp theo |
| FR06 | Admin quản lý bác sĩ | Tạo account và hồ sơ, sửa, ngừng/mở nhận lịch | Có tạo và tìm/xem active | Tiếp theo |
| FR07 | Bệnh nhân tìm bác sĩ | Lọc tên/chuyên khoa, xem chi tiết, phân trang | Backend có; Web placeholder | Nối Web sau nhóm quản lý |
| FR08 | Bác sĩ tạo lịch làm việc | Sinh slot 30 phút, không chồng lịch | Backend có | Rà lại rule |
| FR09 | Bác sĩ xem/vô hiệu hóa lịch | Chỉ lịch mình; không vô hiệu hóa lịch có slot BOOKED | Backend có | Rà lại rule |
| FR10 | Bệnh nhân xem giờ trống | Chỉ slot tương lai có thể đặt | Backend có; cần rà active liên quan | Nối Web |
| FR11 | Bệnh nhân đặt lịch | Tạo PENDING, giữ slot, không double booking | Backend có; thiếu một số rule mục 9 | Nối Web |
| FR12 | Bệnh nhân xem lịch của mình | Danh sách có bác sĩ, thời gian, trạng thái | Backend có | Nối Web |
| FR13 | Bệnh nhân hủy lịch | Hủy đúng chủ, đúng trạng thái, trước giờ khám, trả slot | Backend có | Nối Web |
| FR14 | Bác sĩ xem danh sách hẹn | Chỉ lịch mình; nhận diện được bệnh nhân | Có danh sách; DTO mới có patientAccountId | Bổ sung tên bệnh nhân khi có profile |
| FR15 | Bác sĩ xác nhận lịch | PENDING → CONFIRMED, đúng bác sĩ | Backend có | Nối Web |
| FR16 | Bác sĩ hoàn thành buổi khám | CONFIRMED → COMPLETED; mục tiêu có kết quả khám | Có đổi trạng thái; chưa yêu cầu kết quả | Làm cùng FR17 |
| FR17 | Bác sĩ ghi kết quả khám | Một kết quả mỗi lần khám, đúng bác sĩ phụ trách | Chưa có module; có SQL V2 | Sau luồng Web cơ bản |
| FR18 | Bệnh nhân xem lịch sử/kết quả khám | Chỉ dữ liệu của mình, phân trang và chi tiết | Chưa có module | Sau FR17 |
| FR19 | Hệ thống tạo thông báo | Đặt/xác nhận/hủy thành công tạo thông báo đúng người | Chưa có module; có SQL V2 | Sau luồng đặt lịch |
| FR20 | Người dùng đọc thông báo | Chỉ đọc/đánh dấu thông báo mình | Chưa có module | Cùng FR19 |
| FR21 | Admin theo dõi lịch hẹn | Xem/lọc lịch toàn hệ thống, không cần quyền sửa kết quả | Chưa có API quản trị lịch hẹn | Sau nhóm quản lý |
| FR22 | Bệnh nhân dùng Android | Dùng lại API tìm bác sĩ/slot/đặt/hủy/xem lịch | Chưa thấy project Flutter | Sau Web ổn |

## 5. Luồng sử dụng tổng thể

| Bước | Người thực hiện | Hành động | Dữ liệu thay đổi |
|---|---|---|---|
| 1 | Admin | Tạo chuyên khoa | specialties |
| 2 | Admin | Tạo bác sĩ thuộc chuyên khoa | accounts + doctors |
| 3 | Bác sĩ | Đăng nhập, khai báo lịch 09:00–12:00 | doctor_schedules + 6 schedule_slots |
| 4 | Bệnh nhân | Đăng ký, đăng nhập, bổ sung hồ sơ | accounts; patient_profiles khi triển khai |
| 5 | Bệnh nhân | Tìm bác sĩ và xem slot của ngày khám | Chỉ đọc |
| 6 | Bệnh nhân | Đặt slot 09:00–09:30 | appointments=PENDING; slot=BOOKED |
| 7a | Bệnh nhân | Hủy trước giờ khám nếu không đi | appointment=CANCELED; slot=AVAILABLE |
| 7b | Bác sĩ | Xác nhận lịch nếu tiếp nhận | appointment=CONFIRMED; slot vẫn BOOKED |
| 8 | Bác sĩ | Khám, nhập kết quả | medical_records khi triển khai |
| 9 | Bác sĩ | Đánh dấu hoàn thành | appointment=COMPLETED; slot hiện vẫn BOOKED |
| 10 | Bệnh nhân | Xem kết quả/lịch sử | Chỉ đọc |

Thông báo là dữ liệu phát sinh từ các sự kiện bước 6/7/9, không phải bước người dùng tự gọi API tạo thông báo.

## 6. Đặc tả các use case chính

### UC01 Đăng ký và đăng nhập

| Nội dung | Đặc tả |
|---|---|
| Actor | Khách/người dùng có tài khoản |
| Đầu vào đăng ký hiện có | username 4–50 ký tự; password 8–64; email hợp lệ tối đa 100; phone theo regex hiện tại |
| Luồng đăng ký | Validate → chuẩn hóa username/email → kiểm tra trùng → lấy role PATIENT → BCrypt → lưu account → trả DTO |
| Kết quả | Account được tạo; register không trả accessToken |
| Luồng đăng nhập | Tìm account theo username → so password → kiểm tra isActive → sinh JWT → trả accessToken, tokenType, account |
| Ngoại lệ | Trùng username/email hiện trả 400; sai thông tin đăng nhập 401; account inactive lúc login 403 |
| Web cần sửa | Register thành công chuyển login; đồng bộ password tối thiểu 8; không xử lý register như response login |

### UC02 Cập nhật hồ sơ cá nhân — đề xuất

| Nội dung | Đặc tả |
|---|---|
| Actor | PATIENT đã đăng nhập |
| Mục tiêu | Bệnh nhân bổ sung thông tin để bác sĩ nhận diện khi khám |
| GET | Lấy accountId từ token; đọc profile; chưa có thì trả trường profile null và phone/email từ account |
| PUT | Nhận fullName, dateOfBirth, gender, address, phone; lấy accountId từ token; validate; tạo/cập nhật profile và phone trong một transaction |
| Đầu ra | Hồ sơ mới nhất; email chỉ đọc trong phiên bản đầu |
| Rule đề xuất | fullName bắt buộc khi lưu, trim 2–100; ngày sinh không tương lai; gender MALE/FEMALE/OTHER hoặc null; address tối đa 500; phone cùng chuẩn account |
| Không cho sửa | accountId, username, role, password, isActive qua DTO hồ sơ |
| Điều kiện đạt | Hai token patient chỉ thấy/sửa dữ liệu riêng; PUT lại không tạo thêm profile |

### UC03 Tạo lịch làm việc

| Nội dung | Đặc tả hiện có |
|---|---|
| Actor / điều kiện | DOCTOR đã đăng nhập, có hồ sơ doctor active |
| Đầu vào | startAt, endAt, cùng ngày; startAt tương lai; phút 00 hoặc 30; giây 00; ít nhất 30 phút |
| Luồng | Validate thời gian → khóa doctor → kiểm tra overlap → tạo schedule → sinh slot từng 30 phút → lưu |
| Công thức overlap | start_mới < end_cũ AND end_mới > start_cũ, xét lịch active cùng bác sĩ |
| Ví dụ | 09:00–12:00 sinh 6 slot; 12:00–13:00 nối tiếp được; 11:30–13:00 chồng lấn |
| Ngoại lệ | Thời gian sai: 400/code 3005; overlap: 409/code 3004 |
| Vô hiệu hóa | Chỉ lịch mình; từ chối nếu có slot BOOKED; schedule.active=false, các slot thành BLOCKED |
| Giới hạn | Chưa có lịch lặp theo tuần, sửa giờ trực tiếp hoặc mở lại lịch; không cần thêm ngay |

### UC04 Đặt lịch

| Nội dung | Đặc tả |
|---|---|
| Actor | PATIENT đã đăng nhập |
| Đầu vào hiện có | slotId; không nhận patientId/doctorId từ client |
| Luồng hiện có | Khóa slot → kiểm tra AVAILABLE, tương lai, schedule active → lấy patient từ token → tạo appointment PENDING → slot BOOKED → trả DTO |
| Hậu điều kiện | Appointment và thay đổi slot cùng thành công hoặc cùng rollback |
| Ngoại lệ hiện có | Không có slot: 404/code 3006; slot không đặt được: 409/code 3007 |
| Ràng buộc cần bổ sung | Doctor/account bác sĩ/specialty phải hoạt động tại thời điểm đặt; không tin slot client đã tải trước đó |
| Quyết định đề xuất | Không cho cùng patient có hai lịch PENDING/CONFIRMED chồng giờ, kể cả hai bác sĩ khác nhau |
| Đồng thời | Khóa slot giải quyết hai người đặt cùng slot. Nếu thêm chống trùng giờ của một patient, cần khóa/điều phối theo patient; chỉ query rồi insert chưa đủ |

### UC05 Hủy lịch

| Nội dung | Đặc tả hiện có |
|---|---|
| Actor | PATIENT sở hữu appointment |
| Điều kiện | PENDING hoặc CONFIRMED; startAt > hiện tại |
| Luồng | Khóa appointment → kiểm tra owner/trạng thái/thời gian → CANCELED → slot AVAILABLE |
| Lưu lịch sử | Không xóa appointment; người khác có thể tạo appointment mới cho slot đó |
| Ngoại lệ | Không có appointment: 404; sai owner/trạng thái/thời gian hiện cùng dùng 400/code 3008 |
| Cần rà | Hủy và đặt lại đồng thời phải bảo đảm một slot chỉ có tối đa một appointment còn hiệu lực |

### UC06 Xác nhận và hoàn thành buổi khám

| Nội dung | Hiện có | Mục tiêu đề xuất |
|---|---|---|
| Actor | DOCTOR có doctorId trùng appointment.doctorId | Giữ nguyên |
| Xác nhận | PENDING → CONFIRMED | Tạo thông báo cho patient |
| Hoàn thành | CONFIRMED → COMPLETED | Yêu cầu có kết quả khám hợp lệ trước khi hoàn thành |
| Thời gian hoàn thành | Chưa thấy rule chặn trước giờ khám | Chỉ cho hoàn thành từ startAt trở đi |
| Slot sau hoàn thành | Vẫn BOOKED | Giữ nguyên: giờ đã sử dụng, không trả lại AVAILABLE |
| Ngoại lệ | Sai owner hoặc chuyển sai trạng thái: 400/code 3008 | Có thể tách mã lỗi để FE giải thích rõ |

### UC07 Ghi và xem kết quả khám — đề xuất

| Nội dung | Đặc tả đề xuất |
|---|---|
| Actor ghi | Bác sĩ phụ trách appointment |
| Điều kiện | Appointment CONFIRMED, đã đến startAt |
| Input | diagnosis bắt buộc, examinationNotes và treatmentAdvice tùy chọn |
| Lưu | Một medical_record mỗi appointment; PUT tạo lần đầu/cập nhật cùng bản ghi |
| Sau COMPLETED | Đề xuất kết quả chỉ đọc ở bản đầu; nếu cho sửa cần chốt chính sách riêng |
| Bệnh nhân đọc | Truy qua appointment.patient_account_id; chỉ trả kết quả của mình, đề xuất chỉ hiển thị khi COMPLETED |
| History | CANCELED vẫn thuộc lịch sử hẹn; hồ sơ kết quả khám là danh sách khác |
| Admin | Không mặc định cấp quyền đọc kết quả chỉ vì có role ADMIN |

### UC08 Quản lý dữ liệu và thông báo — đề xuất

| Thao tác | Rule đề xuất |
|---|---|
| Khóa account | Không cho admin tự khóa mình; ngăn login và request token cũ; giữ dữ liệu liên quan |
| Ngừng nhận lịch bác sĩ | Khác khóa account; doctor có thể cần truy cập lịch sử. Bản đầu: từ chối ngừng khi còn PENDING/CONFIRMED |
| Vô hiệu hóa chuyên khoa | Từ chối khi còn doctor active; chuyển/vô hiệu hóa bác sĩ trước |
| Xóa dữ liệu | Dùng active=false, không xóa vật lý dữ liệu đang được lịch sử tham chiếu |
| Thông báo đặt lịch | Gửi cho patient và doctor, mỗi người một row |
| Thông báo xác nhận | Gửi patient |
| Thông báo hủy | Gửi patient và doctor |
| Thông báo hoàn thành | Gửi patient, có thể liên kết kết quả |
| Cách tạo | Tạo trong transaction nghiệp vụ để rollback không để lại thông báo sai |
| Đọc thông báo | recipient_account_id phải bằng account từ token; read_at ghi khi đọc lần đầu; đọc lại không đổi thời điểm |

## 7. Vòng đời trạng thái

### Appointment

| Trạng thái trước | Hành động | Actor | Trạng thái sau | Slot sau | Hiện trạng |
|---|---|---|---|---|---|
| Chưa có | Đặt lịch | PATIENT | PENDING | BOOKED | Có |
| PENDING | Xác nhận | DOCTOR phụ trách | CONFIRMED | BOOKED | Có |
| PENDING | Hủy trước giờ | PATIENT sở hữu | CANCELED | AVAILABLE | Có |
| CONFIRMED | Hủy trước giờ | PATIENT sở hữu | CANCELED | AVAILABLE | Có |
| CONFIRMED | Hoàn thành | DOCTOR phụ trách | COMPLETED | BOOKED | Có; thiếu điều kiện kết quả/thời gian |
| CANCELED/COMPLETED | Sửa trạng thái | Bất kỳ | Không cho | Không đổi | Giữ rule hiện có |

Chưa có REJECTED/NO_SHOW/EXPIRED hoặc doctor hủy. Chưa tự hết hạn PENDING. Nếu cần xử lý không đến khám hoặc bác sĩ nghỉ đột xuất, phải thống nhất vòng đời mới; không tự thêm enum rồi đổi tùy ý.

### Slot và trạng thái hoạt động

| Trường | Ý nghĩa | Không đồng nghĩa với |
|---|---|---|
| accounts.is_active | Tài khoản được phép đăng nhập/truy cập | Bác sĩ có lịch rảnh |
| doctors.active | Hồ sơ bác sĩ đang nhận lịch, cần thống nhất quyền lịch sử | Account có thể đăng nhập |
| specialties.active | Chuyên khoa còn dùng cho danh mục/đặt lịch | Xóa bác sĩ thuộc chuyên khoa |
| doctor_schedules.active | Khoảng lịch làm việc còn dùng | Từng slot đều trống |
| slot AVAILABLE | Slot đang trống; vẫn phải kiểm tra giờ và active liên quan | Chắc chắn đặt được ở mọi thời điểm |
| slot BOOKED | Đã giữ/dùng bởi appointment còn hiệu lực hoặc đã hoàn thành | Buổi khám đã xong |
| slot BLOCKED | Slot bị chặn do lịch vô hiệu hóa | Một appointment bị hủy |

## 8. Mô hình dữ liệu và nguồn thông tin

| Bảng | Khóa/quan hệ | Dữ liệu thuộc trách nhiệm bảng | Ghi chú |
|---|---|---|---|
| roles | role_id; một role nhiều accounts | role_name | Seed ba role; không cần UI tạo role ở bản đầu |
| accounts | account_id; role_id → roles | username, password hash, email, phone, is_active | Không trả password trong response |
| patient_profiles | account_id unique → accounts | full_name, date_of_birth, gender, address | V2; role PATIENT phải kiểm tra tại service |
| specialties | specialty_id | name, description, active | Tên không trùng theo rule service |
| doctors | account_id unique → accounts; specialty_id → specialties | full_name, qualification, description, active | Một doctor thuộc một specialty trong bản hiện tại |
| doctor_schedules | doctor_id → doctors | start_at, end_at, active | Một doctor có nhiều schedule |
| schedule_slots | schedule_id → schedules; doctor_id → doctors | start_at, end_at, status | Unique doctor_id/start_at; doctorId phải đúng doctor của schedule |
| appointments | patient_account_id → accounts; doctor_id → doctors; slot_id → slots | status và liên kết lần đặt | doctorId phải khớp slot; không UNIQUE slot_id vì cần lịch sử hủy/đặt lại |
| medical_records | appointment_id unique → appointments | diagnosis, examination_notes, treatment_advice | V2; không lưu lặp patientId/doctorId |
| notifications | recipient_account_id → accounts; appointment_id nullable | type, title, message, is_read, read_at | V2; recipient có thể patient hoặc doctor |
| flyway_schema_history | Flyway quản lý | Version/checksum/lần chạy migration | Không phải bảng nghiệp vụ, không sửa thủ công |

Thêm bảng không làm chức năng tự chạy: cần migration chạy thành công, Entity khớp schema, service có rule, controller có quyền, Web/App gọi API. V2 hiện cho phép profile chưa đủ dữ liệu; PUT có thể yêu cầu dữ liệu hợp lệ chặt hơn DB.

## 9. Khoảng trống cần xử lý trước khi mở rộng

| Mã | Điều quan sát trong source | Hệ quả nghiệp vụ | Việc tiếp theo |
|---|---|---|---|
| GAP01 | Register trả AccountResponse; FE xử lý giống login | Đăng ký xong chưa có token để vào trang chính | Chuyển login sau register; thống nhất DTO |
| GAP02 | FE cho password 6 ký tự; BE tối thiểu 8 | Form cho gửi nhưng Backend từ chối | Đồng bộ validation |
| GAP03 | accounts chưa có tên bệnh nhân; AppointmentResponse chỉ patientAccountId | Bác sĩ khó nhận diện bệnh nhân | Làm profile, thêm patientName ở DTO phù hợp |
| GAP04 | book mới kiểm tra slot và schedule | SlotId cũ có thể vượt rule ngừng nhận lịch khi bổ sung active | Kiểm tra doctor/account/specialty trong đặt lịch |
| GAP05 | search chỉ lọc doctor.active | Khi bổ sung khóa/vô hiệu hóa, tìm kiếm có thể vẫn hiện bác sĩ không nhận lịch | Thống nhất tiêu chí tìm và slot trống |
| GAP06 | Chưa thấy kiểm tra patient trùng giờ ở hai slot khác nhau | Một người có thể đặt hai lịch cùng giờ | Chốt rule, thêm kiểm tra với xử lý đồng thời |
| GAP07 | Vô hiệu hóa schedule giữ slot cũ với unique doctor/start | Tạo lại cùng giờ có thể conflict DB | Chọn cơ chế tái dùng/mở lại lịch; kiểm tra trước |
| GAP08 | COMPLETED không yêu cầu record hoặc đã đến giờ | Hoàn thành trước khám, không có kết quả | Bổ sung khi làm medical record |
| GAP09 | Nhiều màn hình Web là placeholder | API có nhưng người dùng chưa thao tác được | Nối từng luồng hoàn chỉnh |
| GAP10 | Lọc slot dùng Between từ đầu ngày đến đầu ngày kế tiếp | Có khả năng lấy cả mốc 00:00 ngày sau | Dùng khoảng startAt >= from AND startAt < to |

Các hàng GAP là quan sát source hoặc nguy cơ cần kiểm thử, không phải kết luận mọi lỗi đã tái hiện trên hệ thống chạy thật.

## 10. Danh mục API

Tất cả đường dẫn có tiền tố `/api/v1`. “Có” dựa trên controller hiện tại. Endpoint mới là đề xuất giữ nhất quán với project.

| Nhóm | Method / endpoint | Quyền | Trạng thái |
|---|---|---|---|
| Auth | POST /auth/register; POST /auth/login | Public | Có |
| Account | GET /accounts; GET /accounts/{id}; GET /roles | ADMIN | Có |
| Account | PATCH /admin/accounts/{id}/status | ADMIN | Đề xuất |
| Profile | GET /patients/me/profile; PUT /patients/me/profile | PATIENT | Đề xuất |
| Specialty | GET /specialties | Public | Có |
| Specialty | POST /admin/specialties | ADMIN | Có |
| Specialty | GET /admin/specialties; PUT /admin/specialties/{id}; PATCH /admin/specialties/{id}/status | ADMIN | Đề xuất |
| Doctor | GET /doctors?keyword=&specialtyId=&page=0&size=20; GET /doctors/{id} | Public | Có |
| Doctor | POST /admin/doctors | ADMIN | Có |
| Doctor | GET /admin/doctors; PUT /admin/doctors/{id}; PATCH /admin/doctors/{id}/status | ADMIN | Đề xuất |
| Schedule | POST/GET /doctors/me/schedules; DELETE /doctors/me/schedules/{id} | DOCTOR | Có |
| Slot | GET /doctors/{doctorId}/available-slots?date=YYYY-MM-DD | Public | Có |
| Appointment | POST /appointments; GET /appointments/me; PATCH /appointments/{id}/cancel | PATIENT | Có |
| Appointment | GET /doctors/me/appointments; PATCH /doctors/me/appointments/{id}/status | DOCTOR | Có |
| Appointment | GET /admin/appointments | ADMIN | Đề xuất |
| Record | PUT/GET /doctors/me/appointments/{id}/medical-record | DOCTOR phụ trách | Đề xuất |
| Record | GET /patients/me/medical-records; GET /patients/me/medical-records/{id} | PATIENT sở hữu | Đề xuất |
| Notification | GET /notifications/me; PATCH /notifications/{id}/read | Chủ thông báo | Đề xuất |

Response thành công hiện tại: `{ "code": 1000, "message": "Success", "data": ... }`. Danh sách phân trang nằm trong data với content, page, size, totalElements, totalPages, last. Không phải endpoint GET nào cũng phân trang; GET /accounts và /specialties hiện trả list.

Payload để đọc hiểu luồng có sẵn:

```json
// POST /appointments
{ "slotId": 123 }
```

```json
// PATCH /doctors/me/appointments/45/status
{ "status": "CONFIRMED" }
```

Payload hồ sơ đề xuất:

```json
{
  "fullName": "Nguyễn Tiến Mạnh",
  "dateOfBirth": "2001-09-23",
  "gender": "MALE",
  "address": "Hà Nội",
  "phone": "0912345678"
}
```

## 11. Tiêu chí nghiệm thu bằng tình huống

| Case | Given / chuẩn bị | When / thao tác | Then / kết quả mong đợi | Loại |
|---|---|---|---|---|
| AC01 | Username/email chưa tồn tại | Đăng ký hợp lệ | Account PATIENT, password hash, không token | Hiện có |
| AC02 | Không token | Gọi API đặt lịch | 401, không thay đổi DB | Hiện có |
| AC03 | PATIENT đã login | Gọi API tạo bác sĩ | 403 | Hiện có |
| AC04 | Doctor có lịch 09:00–12:00 | Tạo 11:30–13:00 | 409 overlap, không tạo lịch/slot mới | Hiện có |
| AC05 | Doctor có lịch 09:00–12:00 | Tạo 12:00–13:00 | Thành công nếu tương lai, không overlap | Hiện có |
| AC06 | Slot AVAILABLE tương lai | Hai patient đặt đồng thời | Một thành công; một conflict; một lịch còn hiệu lực | Hiện có, kiểm tra lại |
| AC07 | Patient A đã đặt | Patient B hủy appointment của A | Bị từ chối, dữ liệu không đổi | Hiện có |
| AC08 | Appointment PENDING trước giờ | Chủ patient hủy | CANCELED, slot AVAILABLE, lịch sử vẫn còn | Hiện có |
| AC09 | Appointment của doctor A | Doctor B xác nhận | Bị từ chối | Hiện có |
| AC10 | Appointment PENDING | Doctor đổi thẳng COMPLETED | Bị từ chối | Hiện có |
| AC11 | Patient A/B có profile | A GET/PUT hồ sơ | Chỉ hồ sơ A; profile B không đổi | Đề xuất |
| AC12 | Doctor inactive / account khóa | Đặt bằng slotId đã tải trước | Bị từ chối, không tạo appointment | Đề xuất |
| AC13 | Chuyên khoa có doctor active | Admin vô hiệu hóa | Bị từ chối, giải thích cần xử lý doctor | Đề xuất |
| AC14 | Patient có lịch A 09:00–09:30 | Đặt bác sĩ khác 09:00–09:30 | Bị từ chối theo rule đề xuất | Cần chốt |
| AC15 | Appointment CONFIRMED chưa record | Hoàn thành | Bị từ chối theo rule mục tiêu | Đề xuất |
| AC16 | Record thuộc patient A | Patient B đọc chi tiết | Bị từ chối/không tìm thấy; không lộ nội dung | Đề xuất |
| AC17 | Đặt lịch rollback | Đọc thông báo | Không có thông báo đặt lịch thành công | Đề xuất |
| AC18 | Thông báo recipient A | B đánh dấu đọc | Bị từ chối, is_read không đổi | Đề xuất |

Không dùng báo cáo test cũ để đánh dấu phiên bản mới đã đạt. Lần kiểm tra trước: TypeScript noEmit đạt; Maven offline không resolve được parent; báo cáo 5 test Backend đạt là của 16/09/2026.

## 12. Những quyết định cần chốt

Đây là danh sách để suy nghĩ trước module liên quan, không cần giải quyết tất cả ngay hôm nay.

| Câu hỏi | Mặc định đề xuất cho bản đầu | Khi nào cần chốt |
|---|---|---|
| Một patient đặt hai lịch chồng giờ được không? | Không; tính PENDING/CONFIRMED | Trước bổ sung rule book |
| Bác sĩ có được hủy/từ chối lịch? | Chưa bổ sung; nếu cần phải thêm trạng thái/lý do/thông báo | Trước demo xử lý nghỉ đột xuất |
| Patient không đến khám xử lý thế nào? | Chưa có; cân nhắc NO_SHOW thay vì giả COMPLETED | Trước chốt luồng trạng thái cuối |
| Kết quả có sửa sau hoàn thành không? | Không trong bản đầu | Trước medical record |
| Ai nhận thông báo? | Theo bảng UC08 | Trước notification |
| Timezone lưu/đọc là gì? | Dùng giờ Asia/Ho_Chi_Minh nhất quán; LocalDateTime/DB hiện không mang offset | Trước tích hợp Web/Android |
| Lịch lặp tuần/đổi lịch có bắt buộc không? | Không; tạo từng lịch, hủy và đặt lại | Trước mở rộng schedule |
| Có đặt hộ người thân không? | Không; patientAccountId là người đăng nhập | Trước thay đổi mô hình patient |

## 13. Đọc code bằng tay theo một luồng

Không cần đọc toàn bộ class cùng lúc. Chọn một tình huống, viết dự đoán DB trước/sau rồi lần theo đường đi.

| Thứ tự đọc | File / module hiện có | Câu hỏi cần trả lời |
|---|---|---|
| 1 | appointment/controller/AppointmentController.java | URL nào? Nhận DTO nào? Ai được gọi? |
| 2 | appointment/dto/CreateAppointmentRequest.java | Client gửi gì? Vì sao không gửi patientId? |
| 3 | auth/security/CurrentAccount.java | Backend biết ai đang gọi bằng cách nào? |
| 4 | appointment/service/AppointmentService.java, method book | Rule kiểm tra theo thứ tự nào? Transaction bao trùm gì? |
| 5 | schedule/repository/ScheduleSlotRepository.java, lockById | Khóa bản ghi nào? Vì sao findById thông thường chưa đủ? |
| 6 | appointment/entity/Appointment.java và schedule/entity/ScheduleSlot.java | Quan hệ JPA tương ứng khóa ngoại nào? |
| 7 | common/response/ApiResponse.java và AppointmentResponse.java | Client thực sự nhận những trường nào? |
| 8 | common/exception/ErrorCode.java và GlobalExceptionHandler.java | Lỗi service thành HTTP response ra sao? |
| 9 | BookingFlowIntegrationTests.java | Test chuẩn bị actor/dữ liệu và chứng minh rule thế nào? |

Luồng request: HTTP → JWT filter → kiểm tra quyền → controller/DTO validation → service/rule → repository/DB → DTO → ApiResponse → Web. DTO là dữ liệu giao tiếp; Entity là mô hình lưu trữ; Service quyết định nghiệp vụ; Repository truy vấn dữ liệu.

## 14. Kế hoạch tự phát triển

| Chặng | Việc làm | Sản phẩm tự kiểm tra được | Chưa cần làm |
|---|---|---|---|
| 1. Hiểu phần đã có | Đọc book/cancel/create schedule; chạy request bằng ba role; vẽ DB trước/sau | Giải thích được schedule/slot/appointment và lý do khóa | Feature mới |
| 2. Chức năng nhỏ đầu tiên | Kiểm tra V2 đã chạy; tự viết GET hồ sơ, rồi PUT hồ sơ | Patient A/B tách dữ liệu; PUT cập nhật không tạo trùng | Medical record/notification |
| 3. Quản trị | Khóa/mở account; sửa/trạng thái doctor/specialty; danh sách admin | Case đúng quyền, active và dữ liệu lịch sử | Payment |
| 4. Một luồng Web hoàn chỉnh | Tìm bác sĩ → chọn slot → đặt → xem → hủy | Người dùng thao tác không cần Postman | Trang thống kê phức tạp |
| 5. Luồng bác sĩ | Lịch làm việc → danh sách bệnh nhân → xác nhận → ghi kết quả → hoàn thành | Patient đọc được kết quả của mình | Đơn thuốc nhiều bảng |
| 6. Hoàn thiện | Thông báo, Web admin, Android dùng lại API, demo và báo cáo | Luồng end-to-end đạt theo FR/AC | Tính năng ngoài đề cương |

Khi tự viết một chức năng, điền bảng nhỏ này trước khi code:

| Câu hỏi | Bạn tự ghi câu trả lời |
|---|---|
| Người dùng muốn đạt kết quả gì? | ... |
| Ai được gọi, bản ghi của ai? | ... |
| Input gồm những trường nào? | ... |
| Phải đọc bảng nào, sửa bảng nào? | ... |
| Rule nào khiến thao tác bị từ chối? | ... |
| Có hai request đồng thời thì sao? | ... |
| Response thành công và lỗi là gì? | ... |
| Ba case kiểm tra quan trọng nhất? | ... |

Nếu chưa trả lời được một hàng, quay lại nghiệp vụ hoặc đọc code liên quan trước. Có thể dùng AI để giải thích một đoạn, review giải pháp bạn viết và gợi ý case bị bỏ sót; giữ phần quyết định rule và viết luồng chính trong tay bạn.

## 15. Cách dùng tài liệu khác trong project

- `API_TODO_DATN.md`: checklist endpoint và bước triển khai chi tiết.
- `src/main/resources/db/migration/V1__init_clinic_schema.sql`: schema nền hiện có trong source.
- `src/main/resources/db/migration/V2__add_patient_profiles_medical_records_notifications.sql`: SQL ba bảng mới, cần xác nhận đã chạy.
- Báo cáo TTTN: minh chứng giai đoạn thực tập, không thay thế đặc tả DATN hoặc source hiện tại.

Bắt đầu bằng chặng 1 rồi chặng 2. Bản phân tích này giúp biết toàn bộ hệ thống để chọn đúng phần nhỏ cần làm, không yêu cầu triển khai tất cả cùng lúc.
