## Hoàn thiện trải nghiệm khám — 20/10/2026

- Web doctor: thao tác ghi kết quả trực tiếp, giải thích giờ bắt đầu, tự cập nhật điều kiện theo giờ; nút làm mới lịch/trạng thái bác sĩ. 84 test web + production build pass.
- App: ngày còn chỗ hiển thị một hàng cuộn ngang, không đẩy giờ khám ra khỏi màn hình điện thoại; 29 test + analyze pass trong lượt sửa ngay trước đó.
- Người dùng hoãn test tay hoàn thành khám, lưu và xem bệnh án sang ngày mai; chưa đánh dấu nghiệm thu.
- Thứ tự tiếp theo: nghiệm thu luồng hiện tại trên web/app; xử lý lỗi tìm được; sau đó thiết kế tiếp nhận/check-in và hàng chờ, đổi lịch có xác nhận bệnh nhân, thống kê vận hành. Đây là đề xuất ưu tiên, chưa có triển khai các chức năng mới này.
- Chưa commit/push; không đổi dữ liệu MySQL người dùng.

## Cập nhật tiếp 20/10/2026 — tìm ngày khám và đồng bộ app

- Hoàn thiện bước chọn ngày của T03/T11: API ngày còn chỗ; web/app gợi ý số slot từng ngày và tự chọn ngày gần nhất, không yêu cầu bệnh nhân dò từng ngày làm việc.
- Sửa web phân biệt lỗi tải slot với danh sách rỗng, chặn chọn slot quá giờ/khác danh sách và thao tác đổi ngày khi đang đặt.
- Vị trí hiện tại: Flutter `D:/of/DATN_GTVT/mobile`, SDK `mobile/.tools/flutter`; độc lập FE. Các đoạn FE/mobile bên dưới là lịch sử trước khi chuyển.
- Người dùng đã chạy app trên emulator và cung cấp ảnh login, danh sách bác sĩ, profile và các tab; đó là bằng chứng kiểm tra cơ bản, chưa nghiệm thu toàn bộ T11/T12.
- BE90 test H2 pass, planning26 MySQL pass; web83 tests + production build; Flutter28 tests/analyze/APK pass; HTTP Dart ↔ BE ↔ MySQL test pass. Server/container test đã dừng, không ghi DB người dùng.
- Còn nghiệm thu cùng người dùng: lịch nghỉ có booking thực; doctor hoàn thành ca đã bắt đầu và patient đọc kết quả trên web/app; bản Android mới chạy lại trên emulator. Không thêm luồng mới ngoài phạm vi đồ án trong lượt này; chưa commit/push.

## Cập nhật 20/10/2026 — lịch tuần và đơn nghỉ

Đã triển khai BE/FE cho admin thiết lập mẫu tuần, tự sinh trước 30 ngày, doctor gửi/rút đơn nghỉ và admin duyệt kèm xử lý lịch hẹn. Bỏ thao tác doctor tự tạo/hủy ca ở HTTP và UI. Xem SCHEDULING_IMPLEMENTATION_2026-10-20.md để biết giới hạn và kịch bản kiểm tra. Chưa commit/push.

# Kế hoạch hoàn thành Clinic Booking

## Cập nhật app bệnh nhân ngày 19/10/2026

- Theo người dùng xác nhận chưa có app, đã tạo Flutter Android trong D:/of/DATN_GTVT/FE/mobile; dùng chung API BE. Các màn: auth PATIENT, bác sĩ/chuyên khoa, chọn slot/đặt lịch, lịch hẹn/hủy/lọc, hồ sơ, kết quả, thông báo.
- T05 đã kiểm tra môi trường và build APK debug; có Flutter SDK riêng trong FE/.tools (ignore), Android SDK/NDK/Gradle đã build được. Không đổi PATH hệ thống hoặc framework web.
- T11 có source và build đầu tiên: Flutter analyze sạch, 24 test pass. Đã kiểm tra client Dart qua HTTP thật với BE riêng ở 19091 và H2 in-memory: profile, tìm/đặt/xác nhận/hủy, slot, thông báo và kết quả chưa có. Bắt và sửa lỗi JSON tiếng Việt bằng mã hóa UTF-8; có regression test loopback.
- BE/DB test đã dừng; không kết nối MySQL project, không tạo thêm endpoint fixture. Lượt này không thay nghiệp vụ BE.
- FE web bổ sung hồ sơ doctor đọc dữ liệu thật và menu màn hình hẹp ba role; 60 test pass, build development pass.
- Còn thiếu nghiệm thu T11/T12: cài/chạy APK trên emulator/điện thoại, kiểm tra native network và toàn luồng kết quả dương tính với BE; chưa đánh dấu nghiệm thu hoàn thành. Không coi H2 hoặc widget tests là bằng chứng chạy trên thiết bị/MySQL.
- Chi tiết: D:/of/DATN_GTVT/FE/mobile/README.md và MOBILE_IMPLEMENTATION_REPORT.md. Chưa commit/push.


## Cập nhật đợt 5 ngày 18/10/2026

- POST /api/v1/doctors/me/appointments/{id}/complete nhận diagnosis bắt buộc, examinationNotes/treatmentAdvice tùy chọn; mỗi trường tối đa 5.000 ký tự. Chỉ doctor phụ trách còn hoạt động, lịch CONFIRMED đã bắt đầu. Lưu kết quả, COMPLETED, đóng slot và thông báo trong một transaction, giữ thứ tự khóa đợt 3. Gửi lặp bị từ chối, không ghi đè kết quả đã chốt.
- PATCH status cũ không hoàn thành được nếu chưa có kết quả (409/code 5002). Không tự tạo chẩn đoán cho các lịch COMPLETED cũ; nếu chưa có bản ghi thì API đọc trả 404/code 5001.
- Patient đọc danh sách kết quả của mình, phân trang và xếp giờ khám giảm dần; chi tiết dùng appointmentId. Doctor chỉ đọc kết quả lịch của mình, kể cả profile inactive. Admin không có quyền đọc hồ sơ qua các API này.
- Thông báo lưu cho patient và doctor khi đặt, xác nhận, hủy, hoàn thành, hết hạn hoặc vắng mặt. Nội dung chỉ có mã/giờ/trạng thái lịch hẹn, không chứa chẩn đoán. Cập nhật cùng transaction; đọc chỉ theo người nhận, đánh dấu đọc lặp không đổi readAt.
- Migration Java V4__notification_lifecycle mở CHECK cho EXPIRED/NO_SHOW, giữ dữ liệu thông báo cũ; dùng DROP CHECK trên MySQL và DROP CONSTRAINT trên H2. Flyway tự chạy khi khởi động BE, không chạy SQL thủ công thay V1/V2/V3.
- FE: form ghi kết quả trong danh sách doctor, giữ nội dung khi lỗi và chặn gửi lặp; xem kết quả sau hoàn thành. Patient có danh sách kết quả thật. Hai role có trang thông báo, phân trang, đánh dấu đọc và số chưa đọc ở header; thêm route/menu doctor/notifications, không đổi role/token.
- Số chưa đọc tải khi chuyển trang, đánh dấu đọc hoặc làm mới trang thông báo. Chưa có email/push, websocket hoặc job nhắc lịch trước giờ khám; không hiển thị lời hứa những tính năng đó.
- Kiểm chứng H2: 64 test BE pass, gồm rollback khi lưu thông báo lỗi, hoàn thành đồng thời, kiểm tra chủ sở hữu và migration giữ thông báo cũ. FE: 56 test ChromeHeadless pass, có render form/kết quả/thông báo với dữ liệu test trong vùng nội dung rộng 390px/1440px; không đồng nghĩa kiểm tra trực quan toàn bộ layout/đăng nhập ba vai trò.
- MySQL 8: 64 test pass trong container riêng, gồm kiểm chứng migration V4 và hoàn thành đồng thời (các test migration riêng tiếp tục dùng H2). Container đã dừng/tự xóa sau kiểm tra. Không kết nối DB project. Build development FE pass; chưa commit/push.
- Hợp đồng API và hướng dẫn thử tay: docs/CLINICAL_FLOW_API.md. Bước tiếp theo là chạy nghiệm thu toàn luồng bằng tài khoản test thật và nối app bệnh nhân theo API đã ổn định.


## Cập nhật đợt 4 ngày 18/10/2026

- Doctor dashboard lấy thống kê riêng qua GET /api/v1/doctors/me/appointments/summary: tổng lịch hẹn, PENDING, CONFIRMED, COMPLETED trên toàn bộ dữ liệu của bác sĩ hiện tại. Không đếm từ trang 50 phần tử. Ca khám chưa kết thúc = ca active có endAt > giờ hiện tại, gồm ca đang diễn ra và ca tương lai.
- Danh sách gần đây chỉ lấy 5 lịch hẹn, sắp theo giờ khám giảm dần; tổng dùng totalElements. Lỗi tải danh sách và lỗi thống kê hiển thị riêng, có thử lại. Bác sĩ inactive vẫn đọc được theo chính sách đợt 1.
- Admin dashboard thay số 24/7 bằng tổng lịch hẹn thật từ totalElements của danh sách admin. Không hiển thị lỗi như số 0.
- Danh sách lịch hẹn doctor lọc đủ 6 trạng thái ở BE trước phân trang; đổi bộ lọc về trang đầu. Sau cập nhật tải lại danh sách, lùi trang nếu trang cuối không còn kết quả.
- Tài khoản PATIENT cũ thiếu patient_profiles: GET trả thông tin liên hệ tài khoản và các trường hồ sơ trống, không ghi DB trong GET, không lấy username làm tên thật. PUT tạo profile ở lần lưu đầu, khóa account trước khi tìm/tạo để tránh trùng khi hai request đồng thời. Giữ kiểm tra email trùng và validation hiện có; không cần migration mới.
- FE nhắc hoàn thiện hồ sơ, giữ email/phone, gửi null cho ngày sinh/giới tính bỏ trống và chặn gửi lặp khi đang lưu.
- Kiểm chứng: 54 test BE pass trên H2, gồm dữ liệu vượt 50 lịch hẹn, phạm vi bác sĩ/quyền truy cập, ca đã hủy/quá giờ, hồ sơ cũ và hai lần lưu đầu đồng thời. FE 37 test ChromeHeadless pass; build development pass. Chưa chạy MySQL hoặc kiểm tra trực quan ba vai trò ở đợt này; không tác động DB project, chưa commit/push.
- Bước tiếp theo: đợt 5, hoàn thiện kết quả khám và thông báo, nối FE với nghiệp vụ thật trước khi triển khai app bệnh nhân.

## Cập nhật đợt 3 ngày 17/10/2026

- GET /doctors/me/schedules trả cả ca đã hủy và ca đang mở, thêm active; slot trả thêm status để FE phân biệt Trống/Đã đặt/Đã đóng.
- DELETE ca là hủy logic, chỉ trước giờ bắt đầu và không có slot BOOKED. PATCH /doctors/me/schedules/{id}/restore mở lại ca cũ, giữ nguyên ID slot và lịch sử appointment; yêu cầu đúng doctor, còn hoạt động, ca tương lai, không chồng ca active/slot BOOKED.
- Tạo ca đè khoảng giờ ca inactive trả 3010 hướng dẫn khôi phục, tránh UNIQUE slot cũ. Không xóa vật lý ca/slot có lịch sử.
- Booking/cancel/status/expiry theo thứ tự patient -> doctor -> schedule -> slot -> appointment; create/hủy/restore ca giữ doctor trước schedule/slots. Book đọc schedule bằng locking read để không dùng snapshot cũ sau khi chờ lần hủy ca commit trên MySQL.
- Slot trống dùng khoảng ngày [00:00,00:00 ngày sau), loại đúng giờ đã bắt đầu; validate ca chặn cả nano khác 0. Giờ phòng khám tiếp tục dùng Clock đã chốt.
- FE có nút Hủy ca/Khôi phục, giữ ca đã hủy trên danh sách, chặn thao tác ca quá giờ và gửi lặp; đổi ngày booking hủy request cũ, xóa lựa chọn cũ và kiểm tra ngày slot trước submit.
- Đã chạy bộ 49 test BE với datasource MySQL 8 trong container riêng: pass, gồm hai thứ tự giữ khóa booking/hủy ca và kiểm tra DB sau commit. Test migration riêng vẫn dùng H2. Container test đã dừng và tự xóa; không dùng DB project. Kiểm thử FE 28/28 pass; build development pass.
- MySQL CHECK dùng mã lỗi 3819, H2 dùng 23513; test kiểm tra đúng mã CHECK thay vì giả định Spring luôn bọc cùng exception.
- Chạy lại toàn bộ bộ test với cấu hình H2: 49/49 pass.
- Case kiểm tra tay: doctor tạo ca ngày mai -> hủy -> patient không thấy slot -> khôi phục -> slot xuất hiện lại; có booking thì hủy ca bị từ chối. Chưa kiểm tra giao diện trực quan end-to-end trong lượt này. Chưa commit/push.

## Cập nhật đợt 2 ngày 15/10/2026

- PENDING chỉ xác nhận trước giờ bắt đầu; hết giờ kết thúc thì job chuyển EXPIRED. Job chạy mỗi 60 giây, tối đa 100 lịch/lượt, mỗi lịch một transaction. Không tự chuyển CONFIRMED sang NO_SHOW/COMPLETED.
- CONFIRMED được hoàn thành từ giờ bắt đầu; bác sĩ hoặc admin ghi nhận NO_SHOW sau giờ kết thúc. Gắn bắt buộc kết quả khám vào thao tác hoàn thành vẫn thuộc đợt 5.
- Book khóa patient trước doctor/slot và kiểm tra overlap PENDING/CONFIRMED theo khoảng [start,end). Cancel/status/expiry dùng cùng thứ tự khóa appointment; hủy ca vẫn cần xử lý ở đợt 3.
- Admin có GET /api/v1/admin/appointments?page&size&status&doctorId, PATCH /{id}/cancel và /{id}/no-show; hai thao tác ghi nhận body {reason}, bắt buộc lý do. Hủy chỉ áp dụng lịch chưa bắt đầu, kể cả doctor/account bị khóa; lưu statusReason và audit người thay đổi.
- API patient và doctor hỗ trợ status trước phân trang; sắp xếp giờ khám giảm dần rồi id. FE nối admin list/actions, tên/phone bệnh nhân, trạng thái EXPIRED/NO_SHOW và điều kiện giờ; patient filter gửi về BE.
- Migration V3 thêm status_reason, chuẩn hóa CANCEL thành CANCELED và CHECK các trạng thái hợp lệ. Không tự giải phóng slot của dữ liệu CANCEL cũ vì cần kiểm tra lịch khác còn hiệu lực. Không sửa V1/V2.
- Dùng Clock giờ Asia/Ho_Chi_Minh (cấu hình clinic.time-zone). Có thể tắt job bằng clinic.appointments.expiry-enabled=false; test luôn tắt job nền.
- Kiểm chứng: 40 test BE pass trên H2; thêm một test riêng nâng DB V2 có CANCEL sang V3 và chặn status sai đã pass; FE 22/22 ChromeHeadless pass. Chưa chạy MySQL/visual end-to-end. Comment mới tập trung vào thứ tự khóa, kiểm tra lại sau khóa và transaction của job.
- Khi restart BE, Flyway áp dụng V3 và job bắt đầu xử lý lịch PENDING cũ quá giờ. Kiểm tra tay: admin xem/hủy lịch tương lai kèm lý do; patient thấy status/lý do; doctor không hoàn thành lịch tương lai; hai bác sĩ cùng giờ bị chặn với một patient. Chưa commit/push.

## Cập nhật đợt 1 ngày 15/10/2026

- Đã tách đọc hồ sơ/lịch sử bác sĩ khỏi kiểm tra được phép ghi. Doctor inactive và account mở đọc được lịch, không tạo/hủy ca hoặc cập nhật trạng thái lịch hẹn.
- Điều kiện thao tác mới dùng chung doctor.active, account.isActive, specialty.active. Danh sách/chi tiết public lọc cùng ba điều kiện; slot còn yêu cầu schedule active. Chưa thay đổi giao thức khóa đồng thời (đợt 3).
- Thêm GET /api/v1/doctors/me (DOCTOR, trả DoctorResponse, gồm active); namespace /doctors/me được bảo vệ trước matcher public.
- FE dùng endpoint self để khóa thao tác ghi khi inactive/chưa biết trạng thái/tải trạng thái lỗi. Admin hiển thị Trạng thái bác sĩ, Đang hoạt động/Ngừng hoạt động.
- Kiểm chứng: BE 31/31 test pass trên H2 (8 case HTTP mới, gồm JWT còn hạn sau khóa account); FE 14/14 ChromeHeadless pass (4 case mới); build development thành công. Chưa kiểm tra giao diện end-to-end với MySQL thật hoặc chứng minh concurrency MySQL.
- Case kiểm tra tay tiếp: doctor không còn lịch chưa xử lý -> admin tắt doctor -> doctor F5 vẫn đọc lịch, form/nút ghi bị ẩn; public không thấy doctor, POST slot cũ bị từ chối. Account khóa thì token cũ bị 401 ở endpoint cần xác thực. Bật lại doctor/account kiểm tra luồng bình thường.
- Vòng đời quá hạn/vắng mặt, hoàn thành trước giờ, khôi phục ca, patient overlap và thống kê/filter vẫn thuộc các đợt tiếp theo. Chưa commit/push.

Ngày lập: 13/10/2026. Nguồn: BE_AUDIT_REPORT.md và FE_AUDIT_REPORT.md ngày 13/10/2026, phạm vi đề cương đã đọc trong cuộc trao đổi. Đây là kế hoạch triển khai, không phải xác nhận chức năng đã chạy.

## Mục tiêu và giới hạn

Hoàn thành Web cho PATIENT/DOCTOR/ADMIN, Android cho PATIENT, backend và MySQL; kèm tài liệu thiết kế, báo cáo, hướng dẫn chạy và kịch bản demo. Bám phạm vi đặt lịch, hồ sơ bệnh nhân, kết quả khám, thông báo và quản trị. Không đưa thanh toán, đơn thuốc có cấu trúc, Kafka, Redis, microservices vào kế hoạch hiện tại.

Đề cương kết thúc ngày 20/12/2026. Người thực hiện từng nói còn một tháng; hạn thực tế và số giờ làm mỗi tuần chưa được xác nhận. Các tuần dưới đây là thứ tự mục tiêu, chưa phải cam kết thời lượng. Flutter chưa được audit nên phải kiểm tra môi trường/source ngay giai đoạn đầu. Sau luồng web đầu tiên, ước lượng lại bằng thời gian thực làm.

## Căn cứ và hiệu chỉnh hai report

- Theo BE audit: 23 test, 18 pass, 2 failure, 3 error. Năm ca lỗi ở DoctorServiceTest có mock findById chưa theo service lockById. Cần giữ assertion nghiệp vụ khi sửa, không bỏ test để lấy màu xanh.
- Theo FE audit: production build pass; test dừng ở biên dịch spec tham chiếu AppComponent.title không còn tồn tại. Không phải kết quả kiểm thử trình duyệt.
- Nhãn auth “đã kiểm chứng” trong FE report chưa có bằng chứng end-to-end tương ứng trong lượt audit. Tạm xếp có code, cần chạy lại.
- Đã đọc lại SecurityConfig: /api/v1/accounts/** yêu cầu ADMIN, không public như một dòng trong FE report.
- Đã đọc lại AppointmentService: bác sĩ chỉ chuyển PENDING -> CONFIRMED -> COMPLETED; enum có CANCELED không đồng nghĩa doctor được hủy.
- normalizeRole suy ra ADMIN từ email/username là lỗi phân quyền giao diện; chưa chứng minh vượt quyền backend.
- Bản ghi inactive biến mất khỏi danh sách public không phải mất dữ liệu vĩnh viễn. Cần list quản trị để bật lại bằng giao diện.
- Datetime UTC/Z với LocalDateTime cần kiểm tra giờ đầu vào, lưu DB và hiển thị đầu ra. Không mặc định kết luận chắc chắn HTTP 400.
- Medical record, thông báo và admin theo dõi lịch hẹn là chức năng phải có theo phạm vi; không để nhãn P2 làm chúng bị quên.

## Cách làm và điều kiện hoàn thành

Một đầu việc đi từ rule -> API/DTO -> BE -> FE -> kiểm thử luồng -> ghi bằng chứng. BE và FE dùng cùng task ID và contract, không tự suy đoán endpoint hoặc trường dữ liệu. Có thể làm độc lập những việc không phụ thuộc nhau; thay đổi contract phải được ghi trước khi bên còn lại tích hợp.

Mỗi task ghi: trạng thái (chưa làm/đang làm/bị chặn/đã kiểm chứng), thay đổi, lệnh/case đã chạy, kết quả, phần chưa kiểm tra và bước tiếp theo. Chỉ làm một task chính mỗi lần. Build pass không đủ đánh dấu chức năng hoàn thành.

## Backlog chung theo thứ tự

| ID | Việc | Phía thực hiện | Phụ thuộc | Tiêu chí hoàn thành |
|---|---|---|---|---|
| T01 | Khôi phục bộ kiểm tra hiện có | BE + FE | Không | Sửa mock lỗi thời, sửa spec FE không còn phù hợp; giữ kiểm tra nghiệp vụ có ý nghĩa; ghi kết quả chạy mới, không dùng report cũ |
| T02 | Sửa route booking và role giao diện | FE | Không | Nút đặt lịch mở đúng component; role lấy từ backend; patient có email admin@ vẫn ở cổng patient; role không hợp lệ không tự thành admin |
| T03 | Đồng bộ contract luồng đặt lịch | BE + FE | Không | Validation register khớp; thống nhất giờ phòng khám; tạo 09:00 thì BE/DB/Web vẫn là 09:00; UI không gửi chuyển trạng thái BE không hỗ trợ |
| T04 | Kiểm chứng demo đặt lịch đầu tiên | BE + FE | T01–T03 | Doctor tạo ca; patient tìm, đặt; doctor xác nhận; patient xem/hủy; reload giữ đúng dữ liệu; sai ownership bị từ chối; kiểm tra slot bị chiếm |
| T05 | Kiểm tra môi trường Android | Mobile | Không | Xác định có source Flutter hay chưa; build/chạy bản tối thiểu và gọi API dev từ emulator/thiết bị; ghi trở ngại sớm |
| T06 | Hoàn thiện profile và quản trị | BE rồi FE | T03 | Account cũ thiếu profile đọc/sửa được theo rule chốt; DTO có trạng thái; admin list cả inactive; khóa/mở rồi reload đúng; role khác bị từ chối |
| T07 | Kiểm chứng tính nhất quán booking/schedule | BE | T01, T03 | Same-slot, book vs deactivate schedule/doctor, schedule overlap, hủy rồi đặt lại; kiểm tra dữ liệu cuối transaction trên MySQL cô lập; chốt cách xử lý tạo lại ca inactive cùng giờ |
| T08 | Ghi và xem kết quả khám | BE rồi FE | T04, rule kết quả khám | Đúng doctor ghi, đúng patient đọc; một kết quả/appointment; chẩn đoán và lời dặn; rule hoàn thành/sửa kết quả rõ; test ownership và rollback |
| T09 | Thông báo trong ứng dụng | BE rồi FE | T04 | Book/confirm/cancel thành công tạo thông báo đúng người nhận; rollback không tạo; chỉ chủ nhận đọc/đánh dấu đã đọc được |
| T10 | Admin theo dõi lịch hẹn | BE rồi FE | T06 | Danh sách có phân trang và lọc cần thiết; hiển thị tên/thời gian/trạng thái thật; dashboard không giả số lịch hẹn |
| T11 | Android cho bệnh nhân | Mobile | T05, contract T03/T04 ổn định | Đăng nhập, tìm bác sĩ, xem slot, đặt/hủy/theo dõi lịch; xử lý loading/error/session; chạy trên Android kết nối BE |
| T12 | Nghiệm thu và đóng gói | Toàn hệ thống | T06–T11 | Web ba role và Android đi hết kịch bản; hướng dẫn chạy được kiểm tra; cấu hình API thực; dữ liệu demo riêng; build, bằng chứng, báo cáo và slide nhất quán |

## Các quyết định nghiệp vụ đề xuất

Các lựa chọn sau là đề xuất để giữ phạm vi gọn, chưa phải yêu cầu đã được người dùng xác nhận. Chốt khi bắt đầu task liên quan.

- Một phòng khám, giờ Asia/Ho_Chi_Minh; giữ contract LocalDateTime hiện có nếu kiểm tra round-trip đạt. Web và Android không tự chuyển giờ địa phương thành UTC rồi gửi như giờ không offset.
- Giữ doctor confirm/complete, patient cancel theo rule hiện tại; tạm bỏ nút doctor cancel nếu chưa có yêu cầu bổ sung.
- Kết quả khám gồm chẩn đoán, ghi chú khám và lời dặn; không mở rộng thành module đơn thuốc. Đề xuất hoàn thành buổi khám cùng lúc ghi kết quả trong transaction; chính sách sửa sau hoàn thành phải chốt rõ.
- Thông báo trong ứng dụng đủ cho bước đầu; không tự thêm email, SMS hoặc push. Chốt người nhận của từng sự kiện.
- Không tự xóa lịch sử khi khóa tài khoản/bác sĩ/chuyên khoa. Chốt xử lý lịch chờ quá giờ và ca bị vô hiệu hóa trước khi sửa.
- Chốt có cấm bệnh nhân đặt hai bác sĩ trùng giờ hay không; nếu cấm cần xử lý cả cạnh tranh đồng thời.

## Mục tiêu theo tuần

| Giai đoạn | Kết quả cần đạt |
|---|---|
| Tuần 1 | T01–T04: luồng web đặt lịch được kiểm chứng. T05: phát hiện sớm rủi ro Android. Bắt đầu xác minh rủi ro T07 ngay khi luồng cơ bản chạy |
| Tuần 2 | T06–T10: hoàn thiện phạm vi web/backend và kiểm chứng các rủi ro dữ liệu chính. Đây là nhóm lớn; phải ước lượng lại sau tuần 1 |
| Tuần 3 | T11: hoàn thành các luồng patient trên Android dùng API ổn định; sửa các lỗi tích hợp phát hiện được |
| Tuần 4 | T12: kiểm thử tổng thể, đóng gói, báo cáo và diễn tập. Nếu lượng việc vượt thời gian, báo sớm và xếp lại theo hạn thực, không bỏ âm thầm chức năng trong đề cương |

Viết báo cáo và lưu minh chứng ngay sau mỗi task, không chờ tuần cuối. Nếu hạn vẫn là 20/12, dùng phần thời gian còn lại làm dự phòng, sửa lỗi và luyện bảo vệ. Không hứa bốn tuần hoàn thành khi chưa biết quỹ giờ và tình trạng Flutter.

## Cách học cùng tiến độ

- T02–T04: tự chỉ được nút -> component -> service -> HTTP -> controller -> service -> DB -> response -> UI.
- T06: giải thích role khác ownership, DTO khác entity và trạng thái khóa tài khoản khác ngừng nhận lịch.
- T07: giải thích transaction, dirty checking, check-then-save và khóa; tự dự đoán kết quả trước khi chạy case.
- T08–T09: giải thích quan hệ dữ liệu, trạng thái và rollback khi một bước lỗi.
- Mỗi buổi dành 20–30 phút giải thích lại phần vừa làm; câu chưa cần cho task ghi vào danh sách học sau. Không quay lại ôn toàn bộ Java/Angular giữa task.

## Việc đầu tiên

Làm T01–T03, sau đó T04. Không giao AI “hoàn thiện toàn bộ FE/BE” trong một lượt. Với mỗi lượt, cung cấp task ID, contract thực tế, file liên quan và tiêu chí hoàn thành; yêu cầu báo rõ kiểm tra nào chưa chạy. Chưa triển khai task nào chỉ bằng việc tạo kế hoạch này.
