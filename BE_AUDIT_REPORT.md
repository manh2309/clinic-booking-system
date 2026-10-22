# Báo cáo audit backend Clinic Booking và kế hoạch hoàn thành DATN

Ngày audit: **13/10/2026**. Đối tượng: source trong working tree, gồm cả thay đổi chưa commit. Báo cáo được ghi sau khi duyệt nội dung audit; không triển khai các hạng mục backlog.

## 1. Tóm tắt hiện trạng và phạm vi

Backend có 26 endpoint, bao phủ tài khoản, hồ sơ bệnh nhân, bác sĩ/chuyên khoa, lịch làm việc và đặt lịch. Chưa đủ chức năng theo đề cương: kết quả khám và thông báo mới có bảng; thiếu danh sách quản trị có bản ghi inactive và theo dõi lịch hẹn toàn hệ thống. DTO thiếu trạng thái cần cho FE. Có khóa doctor trong đặt lịch/ngừng nhận lịch nhưng chưa có test cạnh tranh hai chiều. Lần chạy test trong audit: **23 test, 2 failure, 3 error, BUILD FAILURE**.

Không tính phần trăm hoàn thành. Có controller, bảng dữ liệu hoặc task đã giao không chứng minh tính năng hoàn thành. Báo cáo phân biệt: code hiện có, test hiện có, kết quả thực chạy và rủi ro chưa tái hiện.

Nguồn đối chiếu:

- Backend: `D:\of\your_self\clinic-booking-system`.
- Frontend Angular: `D:\of\DATN_GTVT\FE`.
- Đề cương: `D:\of\DATN_GTVT\DE_CUONG_DATN_NGUYEN_TIEN_MANH_5250094.docx`, đọc nội dung OOXML, không thay đổi tài liệu.
- Checklist: `API_TODO_DATN.md`, ngày 05/10/2026; dùng làm kế hoạch tham khảo, không làm bằng chứng triển khai.

Phạm vi theo đề cương: tài khoản/phân quyền; profile patient; doctor/specialty; lịch làm việc và overlap; tìm doctor/slot; đặt/hủy/theo dõi; doctor xác nhận/cập nhật; kết quả khám; thông báo; admin quản lý và theo dõi lịch hẹn. Đề cương còn yêu cầu Web và Android; audit này chỉ đối chiếu Angular, chưa đánh giá Flutter. Không tự thêm payment, Kafka, Redis hoặc microservices.

### Repository và giới hạn thay đổi

Không tìm thấy `AGENTS.md` trong inventory BE/FE hoặc các thư mục cha đã kiểm tra. Trước và sau lượt audit, git status giữ nguyên:

```text
 M src/main/java/com/xxxx/clinicbookingsystem/appointment/service/AppointmentService.java
 M src/main/java/com/xxxx/clinicbookingsystem/doctor/repository/DoctorRepository.java
 M src/main/java/com/xxxx/clinicbookingsystem/doctor/service/DoctorService.java
 M src/main/java/com/xxxx/clinicbookingsystem/schedule/repository/ScheduleSlotRepository.java
?? src/test/java/com/xxxx/clinicbookingsystem/DoctorBookingConcurrencyIntegrationTests.java
```

Khi ghi báo cáo chỉ thêm file Markdown này. Không sửa source, migration, dependency, commit hoặc dữ liệu thật. Test ghi build artifact và dữ liệu H2 trong bộ nhớ; không chạy ứng dụng/migration vào database dev/prod. Không đưa secret vào báo cáo.

### Checklist đã cũ

- Profile patient đã có tại `/api/v1/patient/me/profile`, khác đề xuất `/patients/me/profile`.
- PUT profile hiện dùng `/patient/me/profile/update` và cho sửa email; checklist đề xuất email chỉ đọc.
- Các API khóa/mở account, sửa/status specialty và sửa/status doctor đã có code.
- V2 đã có `patient_profiles`, `medical_records`, `notifications`; nhận định chưa có ba bảng không còn đúng với source hiện tại.
- GET quản trị doctor/specialty, admin theo dõi appointment, medical record và notification vẫn thiếu.
- Patient cũ chưa có profile chưa được xử lý theo fallback GET/PUT tạo lần đầu trong checklist.

## 2. Bảng chức năng và API thực tế

Mọi URL trong bảng có tiền tố **`/api/v1`**, không có context-path bổ sung được tìm thấy trong cấu hình đã đọc. FE dev dùng `http://localhost:9000/api/v1`, khớp port BE 9000. Tất cả response dưới đây được bọc bằng `ApiResponse<T>` (`code`, `message`, `data`). Phân trang dùng `PageResponse<T>` (`content`, `page`, `size`, `totalElements`, `totalPages`, `last`).

Ký hiệu bằng chứng test:

- **B**: BookingFlowIntegrationTests, gọi service với H2; không phải kiểm thử toàn bộ HTTP endpoint.
- **D**: DoctorServiceTest, unit test mock; 4 test update pass, 5 test status lỗi trong lần audit.
- **S**: SpecialtyServiceTest, 4 unit test status pass.
- **H**: SecurityIntegrationTests, chỉ 2 MockMvc test: specialties public và PATIENT không đọc account.
- **—**: chưa thấy test tự động trực tiếp cho chức năng đó; không phủ nhận thông tin test thủ công đã trao đổi, nhưng không dùng làm bằng chứng lần audit.

| Method | URL sau tiền tố | Role | Request → response | Rule hiện có | Test / phần thiếu |
|---|---|---|---|---|---|
| POST | `/auth/register` | Public | username, fullName, password, email, phone → AccountResponse | Tạo PATIENT + profile trong transaction; kiểm tra username/email trùng; trim tên | —; thiếu test HTTP, rollback và đăng ký đồng thời |
| POST | `/auth/login` | Public | username, password → accessToken, tokenType, account | Kiểm tra password và account active | —; thiếu test JWT thực/account khóa |
| GET | `/roles` | ADMIN | Không → RoleResponse[] | Đọc danh sách role | — |
| GET | `/accounts` | ADMIN | Không → AccountResponse[] | Có cả inactive; không trả password | —; thiếu isActive và phân trang |
| GET | `/accounts/{id}` | ADMIN | id → AccountResponse | Not found nếu không tồn tại | H chỉ kiểm tra PATIENT bị 403; thiếu isActive |
| PATCH | `/admin/accounts/{id}/status` | ADMIN | isActive → void | Chặn tự khóa; transaction | —; thiếu test token cũ sau khóa |
| GET | `/patient/me/profile` | PATIENT | Không → PatientProfileResponse | Lấy account từ CurrentAccount | —; account cũ thiếu profile bị lỗi |
| PUT | `/patient/me/profile/update` | PATIENT | fullName, dateOfBirth, gender, email, phone, address → void | @Valid; tên sau trim ≥4; email trùng loại trừ chính account | —; thiếu ownership, validation, rollback |
| GET | `/specialties` | Public | Không → SpecialtyResponse[] | Chỉ active, sắp xếp tên | H public pass; không dùng làm danh sách admin đầy đủ |
| POST | `/admin/specialties` | ADMIN | name, description → SpecialtyResponse | @Valid; kiểm tra trùng tên | —; thiếu API/concurrency |
| PUT | `/admin/specialties/{id}` | ADMIN | name, description → SpecialtyResponse | @Valid; kiểm tra tên trùng loại trừ bản ghi hiện tại | — |
| PATCH | `/admin/specialties/{id}/status` | ADMIN | active → void | Chặn khóa khi còn doctor active | S pass; thiếu cạnh tranh tạo/chuyển/mở doctor |
| GET | `/doctors` | Public | specialtyId, keyword, page, size → PageResponse<DoctorResponse> | Tìm theo tên/chuyên khoa; chỉ lọc doctor.active | —; chưa lọc account/specialty inactive |
| GET | `/doctors/{id}` | Public | id → DoctorResponse | Chỉ doctor active | —; chưa kiểm tra account/specialty active |
| POST | `/admin/doctors` | ADMIN | username, password, email, phone, fullName, qualification, description, specialtyId → DoctorResponse | @Valid; tạo account DOCTOR; specialty active | —; thiếu rollback/concurrency |
| PUT | `/admin/doctors/{id}` | ADMIN | fullName, qualification, description, specialtyId → DoctorResponse | @Valid; tên trim ≥4; specialty active; giữ account/status | D: 4 update test pass; thiếu HTTP/persistence |
| PATCH | `/admin/doctors/{id}/status` | ADMIN | active → void | Khóa doctor; còn PENDING/CONFIRMED thì chặn inactive; mở yêu cầu specialty active | D: 5 status test lỗi; thiếu cạnh tranh hai chiều |
| POST | `/doctors/me/schedules` | DOCTOR | startAt, endAt → ScheduleResponse | Tương lai, cùng ngày, mốc 30 phút; khóa doctor; chống overlap; sinh slot | B tạo hợp lệ/overlap tuần tự; thiếu boundary/concurrency |
| GET | `/doctors/me/schedules` | DOCTOR | Không → ScheduleResponse[] | Chỉ lịch active của doctor hiện tại | —; slot response không có status |
| DELETE | `/doctors/me/schedules/{id}` | DOCTOR | id → void | Ownership; khóa schedule; từ chối khi có BOOKED; đổi slot BLOCKED | —; thiếu cạnh tranh book/cancel |
| GET | `/doctors/{doctorId}/available-slots` | Public | date (YYYY-MM-DD) → SlotResponse[] | Doctor active; AVAILABLE từ hiện tại đến biên cuối ngày | B slot trả lại sau hủy; thiếu lọc account/specialty/schedule active rõ ràng |
| POST | `/appointments` | PATIENT | slotId → AppointmentResponse | Khóa doctor rồi slot; slot tương lai/schedule active; doctor/account/specialty active; tạo PENDING | B happy path, 3 trạng thái từ chối, same-slot; chưa chặn patient trùng giờ giữa doctor |
| GET | `/appointments/me` | PATIENT | page, size → PageResponse<AppointmentResponse> | Chỉ lịch CurrentAccount | B kiểm tra lịch sau confirm; thiếu HTTP/ownership âm |
| PATCH | `/appointments/{id}/cancel` | PATIENT | id, không cần body → AppointmentResponse | Khóa appointment; đúng chủ; PENDING/CONFIRMED trước giờ; trả slot AVAILABLE | B cancel hợp lệ; thiếu negative/concurrency |
| GET | `/doctors/me/appointments` | DOCTOR | page, size → PageResponse<AppointmentResponse> | Chỉ lịch doctor hiện tại; doctor phải active | —; chỉ có patientAccountId, thiếu tên patient |
| PATCH | `/doctors/me/appointments/{id}/status` | DOCTOR | status → AppointmentResponse | Khóa appointment; đúng doctor; PENDING→CONFIRMED→COMPLETED | B confirm; thiếu complete/ownership âm/thời gian |

Các request body có `@Valid`; các API ghi có `@Transactional` tại service. Các GET profile/doctor/specialty/account/schedule/appointment có read-only transaction; RoleServiceImpl.findAll không khai báo transaction ở service. Không suy ra validation hoặc rollback đã được kiểm thử chỉ từ annotation.

Controller tương ứng: AuthenticationController, RoleController, AccountController/AdminAccountController, PatientProfileController, SpecialtyController/AdminSpecialtyController, DoctorController/AdminDoctorController, ScheduleController, AppointmentController/DoctorAppointmentController. Service thực thi: AuthenticationServiceImpl, RoleServiceImpl, AccountServiceImpl, PatientProfileService, SpecialtyService, DoctorService, ScheduleService, AppointmentService.

Không thấy mapping trùng method + URL. POST và GET cùng URL schedule là hợp lệ. Không thấy hai API độc lập triển khai cùng toàn bộ nghiệp vụ; có logic tương tự tạo account ở register/create doctor và kiểm tra tên sau trim. Security matcher `/admin/specialties/**` dư vì cùng rule đã có `/admin/**`, không phải mapping controller trùng.

### Contract response hiện có

| DTO | Trường chính | Giới hạn |
|---|---|---|
| AccountResponse | id, username, email, roleName | Không có isActive, fullName, phone; không lộ password/hash |
| RoleResponse | roleId, roleName | Khớp FE |
| DoctorResponse | id, fullName, qualification, description, specialtyId, specialtyName | Không có active/accountId/accountIsActive |
| SpecialtyResponse | id, name, description | Không có active |
| ScheduleResponse | id, startAt, endAt, slots | Không có active; slot không có status |
| SlotResponse | id, startAt, endAt | Không trả status |
| AppointmentResponse | id, status, doctorId, doctorName, patientAccountId, startAt, endAt | Chưa có tên patient/kết quả khám |
| PatientProfileResponse | patientProfileId, fullName, dateOfBirth, gender, phone, email, address, accountId | Có API thật, chưa có test tự động trực tiếp |

### Bảng dữ liệu khác tính năng hoàn chỉnh

| Chức năng | Bảng | Entity/repository/service/API | Khoảng trống |
|---|---|---|---|
| Profile patient | patient_profiles, account_id UNIQUE | Có | Patient cũ thiếu profile; thiếu test tự động |
| Kết quả khám | medical_records, appointment_id UNIQUE | Chưa thấy | Ghi/sửa, ownership, rule trạng thái, lịch sử patient |
| Thông báo | notifications, recipient, is_read/read_at, type | Chưa thấy | Phát sinh theo sự kiện, đọc/đánh dấu, ownership |
| Admin doctor/specialty | doctors/specialties | Có create/update/status | GET quản trị gồm inactive và DTO trạng thái |
| Admin theo dõi lịch hẹn | appointments | Chưa có API admin | Lọc/phân trang toàn hệ thống, số liệu thực |

Bằng chứng: `src/main/resources/db/migration/V2__add_patient_profiles_medical_records_notifications.sql:5,26,44`. Inventory Java/test và tìm kiếm MedicalRecord/Notification không thấy module tương ứng.

## 3. Ownership, validation, trạng thái và transaction

### Kiểm tra tuần tự có trong code

- Profile dùng CurrentAccount; request không nhận accountId/role/isActive. PUT dùng @NotBlank/@Size cho tên/email, @Email, @PastOrPresent cho ngày sinh, pattern giới tính/phone và giới hạn địa chỉ. Service kiểm tra tên sau trim và email trùng loại trừ account hiện tại.
- Register ép role PATIENT, mã hóa password và tạo profile cùng transaction. Password request 8–64; tên 4–100; username 4–50. Tạo doctor cũng tạo account/profile cùng transaction, nhưng validation tên tạo doctor chỉ NotBlank/max 100, khác update yêu cầu min 4.
- Account status có @NotNull; chặn admin tự khóa. JWT nạp lại account trên request và token validation kiểm tra enabled. Có cơ chế chặn token cũ sau account khóa; chưa có test JWT thực tương ứng.
- Doctor status chỉ đổi Doctor.active, không đổi Account.isActive, schedule hoặc slot. Không cho inactive khi còn bất kỳ PENDING/CONFIRMED, kể cả lịch quá khứ chưa xử lý. Mở doctor yêu cầu specialty active.
- Specialty status chặn inactive khi còn doctor active. Create/update kiểm tra tên trùng; request status bắt buộc Boolean.
- Tạo schedule yêu cầu start tương lai, cùng ngày, đủ/chia hết 30 phút, minute căn mốc và second=0; overlap dùng start<end mới và end>start mới. Chưa kiểm tra nano=0.
- Book kiểm tra slot AVAILABLE, start tương lai, schedule active và doctor/account/specialty active. slotId chỉ @NotNull, chưa @Positive.
- Cancel kiểm tra patient sở hữu, status PENDING/CONFIRMED và start tương lai. Doctor status kiểm tra doctor phụ trách; chỉ PENDING→CONFIRMED và CONFIRMED→COMPLETED. Chưa kiểm tra thời gian hoặc kết quả khám.
- Ownership schedule nằm trong truy vấn lockOwnedSchedule với doctorId hiện tại.

Bằng chứng: `AppointmentService.java:35,75,97`; `ScheduleService.java:30,60,80`; `PatientProfileService.java:27,36,47`; `AccountServiceImpl.java:46`; `JwtService.java:44`; `JwtAuthenticationFilter.java:57`; `SecurityConfig.java:27,72`. Các file Java ở `src/main/java/com/xxxx/clinicbookingsystem/` theo module tương ứng.

### Cơ chế đồng thời không đồng nghĩa đã kiểm chứng

| Tình huống | Cơ chế hiện có | Test thật trong source |
|---|---|---|
| Hai patient đặt cùng slot | Transaction; doctor lock → slot lock; kiểm tra AVAILABLE | Hai worker/latch cùng bắt đầu; chỉ một lời gọi thành công. Chưa assert đầy đủ DB cuối cùng hoặc chứng minh worker chờ khóa |
| Book vs doctor inactive | Cùng doctorRepository.lockById/PESSIMISTIC_WRITE | Chưa có hai bài A/B lấy khóa trước |
| Tạo schedule overlap đồng thời | Khóa doctor trước kiểm tra overlap | Chỉ test overlap tuần tự |
| Cancel vs appointment status | Cùng khóa appointment | Chưa test cạnh tranh |
| Book vs deactivate schedule | Book khóa doctor/slot; deactivate khóa schedule rồi cập nhật slots | Chưa test; chưa thấy giao thức khóa chung rõ ràng |
| Book vs account/specialty status | Book đọc trạng thái; updater không cùng khóa doctor | Chưa test cạnh tranh |
| Khóa specialty vs tạo/chuyển/mở doctor | Kiểm tra tồn tại active doctor, chưa dùng khóa chung specialty | Chưa test cạnh tranh |

`DoctorBookingConcurrencyIntegrationTests.java:46` chỉ có `fixtureIsCommittedAndVisibleInANewTransaction()`: dữ liệu commit, đọc transaction mới, không có executor/latch cạnh tranh. Không được tuyên bố hai chiều concurrency đã test từ tên file.

`BookingFlowIntegrationTests.java:157` có hai worker thực với CountDownLatch. Đây là test lời gọi đồng thời, nhưng chưa điều phối chắc chắn ai giữ khóa trước. Fixture test không thay thế hai bài patient thắng/admin thắng.

Các khoảng trống locking là rủi ro suy ra từ source, chưa phải lỗi đã tái hiện trên MySQL. JOIN FETCH/PESSIMISTIC_WRITE có thể ảnh hưởng phạm vi khóa tùy DB/dialect; cần đọc SQL và test trên MySQL cô lập trước khi kết luận. Hủy/status khóa appointment còn việc cập nhật slot và tương tác với khóa doctor/schedule phải được đánh giá như một giao thức chung.

## 4. Vấn đề ưu tiên và contract FE

### P0

Chưa có bằng chứng xác nhận P0 như vượt quyền backend hoặc mất dữ liệu thật. Không đồng nghĩa đã kiểm chứng an toàn toàn bộ hệ thống. Không chạy request tấn công hoặc test trên dữ liệu thật.

### P1

| ID | Vấn đề/tình huống | Tác động | File và dòng bằng chứng |
|---|---|---|---|
| P1-01 | Mock status doctor vẫn findById trong khi service dùng lockById | 5 test lỗi; regression suite không còn chứng minh rule status hiện tại | `doctor/service/DoctorService.java:94`; `src/test/java/com/xxxx/clinicbookingsystem/doctor/service/DoctorServiceTest.java:124,134,148,164,178` |
| P1-02 | AccountResponse không trả isActive; FE mặc định undefined là active | Khóa xong reload hiển thị sai, khó chọn mở lại | `account/dto/AccountResponse.java:13`; FE `src/app/features/admin/pages/accounts/admin-accounts.component.ts:95,229` |
| P1-03 | Admin doctor/specialty dùng list public active; DTO không trả active | Inactive biến mất sau reload, không quản trị/mở lại đầy đủ từ FE | `doctor/repository/DoctorRepository.java:16`; `specialty/service/SpecialtyService.java:26`; DTO doctor/specialty dòng 3; FE admin doctors `:401`, specialties `:292` |
| P1-04 | Medical record/notification mới có bảng; không có API admin appointment | Không đáp ứng các yêu cầu kết quả khám, thông báo, theo dõi toàn hệ thống trong đề cương | V2 `:26,44`; inventory entity/service/controller và tìm kiếm module không có kết quả |
| P1-05 | Tạo lại schedule inactive tại cùng giờ | Check overlap bỏ inactive nhưng slot BLOCKED cũ giữ UNIQUE doctor/start; có thể conflict DB | `schedule/service/ScheduleService.java:34,66`; V1 `:54` |
| P1-06 | Chưa chứng minh book/deactivate schedule an toàn đồng thời | Nguy cơ lịch bị inactive nhưng appointment/slot không nhất quán; cần tái hiện, chưa coi là lỗi MySQL xác nhận | `schedule/service/ScheduleService.java:60,62,65`; `appointment/service/AppointmentService.java:39,42,45,61` |
| P1-07 | Patient cũ không có profile | GET và PUT đều lỗi, chưa có create-on-update hoặc backfill | `patient/service/PatientProfileService.java:28,37`; V2 chỉ tạo bảng, không tạo profile từ account cũ |

Đường dẫn Java ngắn trong bảng tính từ `src/main/java/com/xxxx/clinicbookingsystem/`; V1/V2 là migration tương ứng trong `src/main/resources/db/migration/`. Đường dẫn FE tính từ `D:/of/DATN_GTVT/FE/`.

### P2 và chính sách cần chốt

| ID | Vấn đề/tình huống | Tác động/giới hạn kết luận | Bằng chứng |
|---|---|---|---|
| P2-01 | Public doctor/slot chưa lọc account/specialty inactive | Doctor vẫn hiển thị nhưng book mới bị 2007; chưa có test HTTP | `DoctorRepository.java:16,24`; `ScheduleService.java:71`; `ScheduleSlotRepository.java:14` |
| P2-02 | CONFIRMED→COMPLETED không kiểm tra thời gian hoặc medical record | Có thể hoàn thành trước giờ/không ghi kết quả; cần chốt rule | `AppointmentService.java:97,98,102` |
| P2-03 | Patient đặt hai doctor cùng giờ | Chưa kiểm tra patient overlap; chỉ check slot riêng. Là khoảng trống nếu nghiệp vụ cấm | `AppointmentService.java:35` |
| P2-04 | Nano và biên ngày available-slots | Validation không kiểm tra nano; BETWEEN inclusive có thể gồm start đúng biên ngày sau | `ScheduleService.java:80`; `ScheduleSlotRepository.java:14` |
| P2-05 | Response appointment chỉ có patientAccountId | Doctor thấy “Bệnh nhân #ID”, thiếu tên phục vụ tiếp nhận | `appointment/dto/AppointmentResponse.java:6`; FE doctor appointments `:53` |
| P2-06 | Khóa account/specialty không cùng giao thức khóa doctor | Trạng thái có thể đổi sau check khi request đang book; chưa tái hiện | `AccountServiceImpl.java:46,52`; `SpecialtyService.java:58,71`; `AppointmentService.java:49` |

### Mismatch FE đã đối chiếu

| Contract | Hiện trạng | Kết luận |
|---|---|---|
| Method/URL | Services FE hiện có khớp 26 endpoint BE, gồm patient singular và /update | Không cần tự đổi URL về đề xuất cũ; chưa thấy FE service medical record/notification/admin appointments |
| Register validation | FE fullName min 2, password min 6; BE min 4/min 8 và giới hạn max | FE chấp nhận dữ liệu BE từ chối; `register.component.ts:294,298` |
| Doctor cancel | FE gửi CANCELED qua endpoint doctor status | BE chỉ nhận chuyển CONFIRMED/COMPLETED; `doctor-appointments.component.ts:76`, `AppointmentService.java:97` |
| Trạng thái admin | FE optional active/isActive và dùng !==false làm mặc định | BE không trả trạng thái; optional đang che mismatch; inactive không được tải lại |
| Schedule datetime | FE toISOString gửi UTC/Z; BE dùng LocalDateTime không offset | Rủi ro lệch giờ, chưa test round-trip request thực; `doctor-schedule.component.ts:228,229`, `CreateScheduleRequest.java:6` |
| Account tên/phone | FE AccountResponse có fullName/phone optional, BE không trả | FE fallback tên sang username; `src/app/core/services/auth.service.ts:120` và `account/dto/AccountResponse.java:13` |
| Role | normalizeRole ưu tiên username=admin/email admin@ | Có thể điều hướng UI sai; BE vẫn kiểm tra role thật, chưa chứng minh vượt quyền; `src/app/core/services/auth.service.ts:13` |
| Dashboard admin | appointmentCount=0 không gọi API lấy số appointment; doctor/specialty count dùng public active | Không coi dashboard là theo dõi lịch hẹn thật; `admin-dashboard.component.ts:404,414,417,420` |
| Deployment | Dev port 9000 khớp; production FE còn domain placeholder | Chưa chứng minh demo production; `src/environments/environment.prod.ts:3` |

API wrapper, PageResponse, roleId và PatientProfileResponse nhìn chung khớp model FE. Đối chiếu tĩnh không thay thế chạy Web/BE end-to-end. Các service có cùng URL không chứng minh mọi thao tác UI dùng đúng rule.

## 5. Kết quả kiểm thử và giới hạn

Trong lượt audit đã chạy lệnh sau bằng PowerShell, Maven offline với dependency cache có sẵn:

```powershell
mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' `
  '-Dspring.profiles.active=test' `
  '-Dspring.datasource.url=jdbc:h2:mem:clinic_audit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1' `
  '-Dspring.datasource.driver-class-name=org.h2.Driver' `
  '-Dspring.datasource.username=sa' `
  '-Dspring.datasource.password=' `
  '-Dapp.bootstrap.admin.username=' test
```

Datasource được ép về H2 in-memory; không trỏ database thật. Spring test profile và Flyway chỉ khởi tạo schema test. Không đưa giá trị JWT/database secret vào báo cáo.

| Bộ test | Số ca | Kết quả lần audit |
|---|---:|---|
| BookingFlowIntegrationTests | 6 | Pass |
| ClinicBookingSystemApplicationTests | 1 | Pass |
| DoctorServiceTest | 9 | 4 pass, 2 failure, 3 error |
| DoctorBookingConcurrencyIntegrationTests | 1 | Fixture pass; chưa có cạnh tranh |
| SecurityIntegrationTests | 2 | Pass |
| SpecialtyServiceTest | 4 | Pass |
| **Tổng** | **23** | **18 pass, 2 failure, 3 error, 0 skipped; BUILD FAILURE** |

Lệnh kết thúc ngày 13/10/2026 lúc **14:24:17 Asia/Saigon**, exit code 1, khoảng 14 giây; không treo. Khi ghi file báo cáo chỉ đọc lại report và source, **không chạy lại test**, không tuyên bố có lần pass mới.

Bằng chứng: `target/surefire-reports/com.xxxx.clinicbookingsystem.doctor.service.DoctorServiceTest.txt` và các report cùng thư mục (artifact có thể bị ghi đè khi chạy test sau này).

Năm test status doctor lỗi:

- `updateStatusOnlyReactivatesDoctorWhenSpecialtyIsActive`: mong SPECIALTY_NOT_FOUND, nhận DOCTOR_NOT_FOUND.
- `updateStatusRejectsDeactivationWhenUnresolvedAppointmentExists`: mong DOCTOR_HAS_PENDING_APPOINTMENTS, nhận DOCTOR_NOT_FOUND.
- `updateStatusDeactivatesDoctorWithoutUnresolvedAppointments`: AppException Doctor not found.
- `updateStatusReturnsWithoutFurtherChecksWhenStatusIsUnchanged`: AppException Doctor not found.
- `updateStatusThrowsWhenDoctorDoesNotExist`: Mockito UnnecessaryStubbingException tại dòng 124.

Nguyên nhân đọc source: các mock status vẫn stub findById trong khi service đã gọi lockById. Đây là test không đồng bộ với code, không tự chứng minh rule sản phẩm sai; cũng không thể coi test status đang có là pass. Audit không sửa test.

Giới hạn:

- H2 MODE=MySQL không xác minh hành vi MySQL: row/gap lock, isolation, deadlock, SQL JOIN FETCH, collation hoặc migration trên MySQL cần test riêng cô lập.
- Không dùng kết quả test cũ trong cuộc trao đổi để tuyên bố bản hiện tại pass.
- Bốn ca availability gọi service và đọc DB có kiểm tra từ chối không tạo appointment/không đổi BOOKED; chưa kiểm tra HTTP 409 thật qua endpoint.
- Test booking flow có tạo schedule/overlap, đặt/xác nhận/hủy và same-slot; chưa phủ complete, lịch quá khứ, ownership âm, validation HTTP hoặc race khóa doctor.
- Hai MockMvc test chỉ kiểm tra public specialties và cấm PATIENT đọc account. @WithMockUser không kiểm thử JWT thật/account khóa.
- Không chạy Postman/browser end-to-end, Flutter hoặc MySQL thật. Báo cáo concurrency xác nhận code/cơ chế riêng với bằng chứng test.

## 6. Backlog tối thiểu theo thứ tự phụ thuộc

Các mục sau là đề xuất, chưa triển khai trong audit. Tên endpoint trong checklist là đề xuất; chỉ chốt contract trước khi làm API mới.

| Thứ tự | Công việc | Phụ thuộc | Tiêu chí hoàn thành |
|---:|---|---|---|
| 1 | Đồng bộ DoctorServiceTest với locking | Source hiện tại | Mock lockById đúng, giữ assertion nghiệp vụ; toàn bộ test hiện có pass; không né lỗi bằng lenient/loại test |
| 2 | Hai test book vs doctor inactive | Fixture/transaction đã có, mục 1 | A giữ doctor lock; B bắt đầu khi A giữ; timeout hữu hạn. Patient thắng: appointment PENDING, slot BOOKED, doctor vẫn active, B lỗi 2006. Admin thắng: doctor inactive, slot AVAILABLE, không appointment, B lỗi 2007. Đọc DB sau kết thúc cả hai |
| 3 | Kiểm chứng các race còn lại và MySQL | Mục 1–2 | Test book/deactivate schedule, cancel/status, create overlap và guard trạng thái; thống nhất thứ tự khóa sau tái hiện; assert DB commit/rollback; chạy MySQL test cô lập trước tuyên bố an toàn MySQL |
| 4 | Chốt và đồng bộ contract cốt lõi | Chốt câu hỏi bên dưới | Validation FE/BE khớp; datetime round-trip đúng; doctor cancel được bỏ hoặc triển khai theo rule; có test HTTP lỗi/happy path và ownership |
| 5 | Hoàn thiện profile và admin dữ liệu | Mục 4 | Patient cũ có cách đọc/sửa profile; ADMIN đọc inactive/status; FE reload rồi khóa/mở đúng; PATIENT/DOCTOR bị 403; không lộ password |
| 6 | Admin theo dõi appointment | Mục 5 | API ADMIN phân trang/lọc doctor, patient, status, khoảng ngày; DTO đủ tên/thời gian; quyền/validation có test; dashboard dùng dữ liệu thật |
| 7 | Kết quả khám | Rule trạng thái mục 4, appointment ổn | Entity→repository→service→API; một record/appointment; đúng doctor ghi, đúng patient đọc; chốt sửa sau complete; validation/rollback/ownership có test |
| 8 | Thông báo trong ứng dụng | Booking và status ổn, kết quả mục 7 nếu thông báo complete | Tạo thông báo book/confirm/cancel khi nghiệp vụ thành công; không tạo trên rollback; recipient ownership; phân trang/read flag; test transaction và quyền |
| 9 | Demo, tài liệu và Android | Các contract ổn định | Web patient/doctor/admin chạy end-to-end; Android dùng API patient; demo MySQL cô lập; lưu bằng chứng, sơ đồ, kịch bản bảo vệ; không dùng secret trong tài liệu |

Không cần hoàn thiện mọi module mới tích hợp một luồng Web đã ổn. Không xóa vật lý dữ liệu đang được lịch sử tham chiếu. Không sửa migration đã áp dụng như cách khắc phục nhanh; thay đổi schema sau này cần migration mới và test trên DB cô lập, trong task được duyệt riêng.

### Câu hỏi nghiệp vụ cần chốt

1. Patient có được đặt hai doctor cùng giờ không? Nếu cấm phải chặn cả request đồng thời, không chỉ query kiểm tra tuần tự.
2. Doctor được từ chối/hủy không? Ai hủy ở trạng thái nào, trước/sau giờ khám, slot có mở lại và ai được thông báo?
3. Khi nào cho CONFIRMED/COMPLETED? Có bắt buộc medical record trước complete không; có sửa kết quả sau complete không?
4. Schedule inactive được tạo lại cùng giờ không? Chọn phục hồi/tái sử dụng hay trả lỗi nghiệp vụ rõ ràng.
5. Account khóa, doctor inactive và specialty inactive ảnh hưởng lịch hiện có thế nào? Xử lý PENDING/CONFIRMED đã quá giờ ra sao?
6. Time contract dùng giờ địa phương phòng khám hay thời điểm có offset? Áp dụng nhất quán Web/Android/BE và biên ngày.
7. Patient cũ thiếu profile dùng tạo lười hay backfill có kiểm soát? Audit không tự chạy cập nhật dữ liệu thật.
8. Thông báo gửi patient/doctor/cả hai cho từng sự kiện? Trong ứng dụng là phương án tối thiểu đề xuất; email/push chưa được xác nhận bắt buộc.

## 7. Năm phần kiến thức BE chủ project cần giải thích khi bảo vệ

1. **Luồng request và phân quyền.** JWT filter → SecurityConfig/@PreAuthorize → controller/@Valid → service. Phân biệt 401/403 và lỗi nghiệp vụ 409. Ownership lấy từ CurrentAccount, không tin accountId client; kiểm tra role và ownership là hai việc khác nhau.
2. **Mô hình dữ liệu và trạng thái.** Giải thích quan hệ account/role/patient profile/doctor/specialty/schedule/slot/appointment. Phân biệt account.isActive, doctor.active, specialty.active, schedule.active, SlotStatus và AppointmentStatus; completed appointment không tự có kết quả khám.
3. **Transaction và rollback.** Tạo appointment và đổi slot cùng transaction; managed entity/dirty checking; exception và thời điểm commit; dữ liệu fixture phải commit trước khi worker khác đọc. Vì sao @Transactional trên toàn bộ test có thể che lỗi hoặc làm worker không thấy dữ liệu.
4. **Locking và concurrency.** Check-then-save chưa đủ; luồng doctor lock → slot lock; thời gian giữ khóa đến commit; thứ tự khóa, isolation, deadlock. Phân biệt test tuần tự, hai worker cùng chạy và test điều phối chắc chắn ai giữ khóa trước; H2 khác MySQL.
5. **Contract và bằng chứng kiểm thử.** DTO không lộ password/entity; validation, mã 2006/2007, timezone/phân trang. Test service khác test HTTP/JWT; bảng migration khác tính năng đã hoạt động. Trình bày kết quả thực chạy và giới hạn, không nói “hoàn thành” chỉ vì có controller hoặc task.
