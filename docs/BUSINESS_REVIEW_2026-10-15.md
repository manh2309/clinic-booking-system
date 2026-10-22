# Rà soát nghiệp vụ Clinic Booking

Ngày: 15/10/2026. Phạm vi: các luồng BE hiện có (auth, account, patient, specialty, doctor, schedule, appointment), controller/security, DTO/entity, migration và test; đối chiếu thao tác FE tương ứng. Không sửa source hoặc dữ liệu thật trong lượt này. Android chưa có source trong hai project được rà nên chưa đánh giá. Không coi thiếu module mới là bug của module hiện có.

## Kết luận

Chưa cần viết lại kiến trúc. Cần thống nhất trạng thái và vòng đời khám trước khi bổ sung medical record/notification. Các vấn đề chính: lịch quá giờ không có đường kết thúc phù hợp; khả năng đặt được hiển thị khác điều kiện book; hủy ca và tái tạo ca mâu thuẫn với UNIQUE slot; giao thức khóa chưa thống nhất; FE đang lọc/thống kê trên một trang nhưng trình bày như toàn bộ.

Phân loại bằng chứng:
- **Code**: hành vi đọc trực tiếp từ điều kiện/truy vấn; chưa chạy case HTTP/MySQL mới trong lượt audit.
- **Suy luận từ schema**: có đường đi gây xung đột theo constraint; cần test tái hiện riêng.
- **Rủi ro cạnh tranh**: cần test điều phối transaction trên MySQL cô lập; không khẳng định đã tái hiện mất dữ liệu.
- **Quyết định nghiệp vụ**: có nhiều lựa chọn hợp lệ; đề xuất dưới đây chưa phải rule đã được người dùng chốt.

Đường dẫn BE trong bảng tính từ `src/main/java/com/xxxx/clinicbookingsystem/`; FE tính từ `D:/of/DATN_GTVT/FE/src/app/`.

## Việc cần giải quyết trước khi thêm module

### B01 Trạng thái bác sĩ đang gộp quyền hoạt động với truy cập lịch sử

**Code, ưu tiên cao.** `doctor/service/DoctorService.java:122` dùng findByAccountIdAndActiveTrue; `appointment/service/AppointmentService.java:96,105` và `schedule/service/ScheduleService.java:53,59` sử dụng helper đó. Doctor inactive nhưng account vẫn active đăng nhập được, sau đó không xem được lịch hẹn/ca và nhận Doctor not found dù hồ sơ tồn tại. FE admin gọi trạng thái này là Nhận lịch khám (`features/admin/pages/doctors/admin-doctors.component.ts:235`).

Đề xuất: tách tìm hồ sơ với kiểm tra được phép thao tác. Account điều khiển đăng nhập; Doctor.active là hoạt động tại phòng khám, không phải còn slot hôm nay. Lịch sử vẫn đọc được khi chỉ doctor inactive; tạo ca/booking mới phải kiểm tra riêng. Không chỉ thay helper rồi coi toàn bộ quyền ghi đã đúng. Lập ma trận thao tác trước khi sửa.

### B02 Lịch quá giờ bị treo và cản ngừng hoạt động bác sĩ

**Code, ưu tiên cao.** `AppointmentService.java:85` chỉ cho patient hủy trước giờ. Enum chỉ PENDING/CONFIRMED/COMPLETED/CANCELED. `DoctorService.java:103` chặn inactive khi có bất kỳ PENDING/CONFIRMED, không phân biệt quá khứ. Không thấy job hoặc thao tác xử lý vắng mặt/hết hạn.

Ví dụ: patient đặt hôm qua, doctor chưa xác nhận. Patient không hủy được; admin không tắt doctor được. Doctor phải xác nhận rồi hoàn thành một buổi không hề khám để đóng lịch nếu chỉ dùng đường hiện tại.

Đề xuất: chốt trạng thái kết thúc cho PENDING quá hạn và CONFIRMED không đến (ví dụ EXPIRED/NO_SHOW), ai được ghi nhận và thời điểm áp dụng. Phân biệt job hết hạn với ghi nhận đã khám. Không tự thêm cron khi chưa chốt rule; không sửa trực tiếp DB để giả hoàn thành.

### B03 Có thể hoàn thành lịch khám trong tương lai

**Code, ưu tiên cao.** `AppointmentService.java:106` chỉ kiểm tra cặp trạng thái; CONFIRMED -> COMPLETED không xét thời gian, không yêu cầu kết quả khám. Bác sĩ có thể hoàn thành lịch tuần sau ngay hôm nay; FE có nút tương ứng (`features/doctor/pages/appointments/doctor-appointments.component.ts:84`).

Đề xuất: chốt thời điểm bắt đầu được hoàn thành; ghi kết quả và chuyển COMPLETED trong cùng transaction khi làm medical records. Hiện chưa có module kết quả khám nên chưa gọi việc thiếu record là lỗi của một module đã triển khai, nhưng phải chốt trước khi xây module đó.

### B04 Khóa tài khoản bác sĩ làm lịch hiện có không được xử lý

**Code + quyết định nghiệp vụ, ưu tiên cao.** `account/service/impl/AccountServiceImpl.java:46` khóa account không kiểm tra/giải quyết appointment, trong khi tắt Doctor lại chặn nếu có lịch chờ. JWT đúng là chặn tài khoản khóa ở request mới; tuy nhiên patient vẫn có PENDING/CONFIRMED và không có người thay thế xử lý trên ứng dụng.

Đề xuất: khóa account vì lý do truy cập vẫn phải có hiệu lực, không buộc bỏ khóa để giải quyết lịch. Admin cần xem được các lịch bị ảnh hưởng và có đường hủy có lý do/thông báo. Không tự hủy hàng loạt âm thầm, không tự chuyển bác sĩ. Chưa có API admin appointment là khoảng trống cần phối hợp.

### B05 Hiển thị slot/bác sĩ còn nhận đặt nhưng POST lại từ chối

**Code, ưu tiên cao.** `doctor/repository/DoctorRepository.java:15,23` chỉ lọc doctor.active. `ScheduleService.availableSlots()` chỉ gọi getActive doctor. `AppointmentService.book():57` kiểm tra cả doctor, account và specialty. Vì vậy khóa account doctor vẫn để danh sách public/slot hiện ra nhưng POST bị DOCTOR_NOT_AVAILABLE.

Đề xuất: thống nhất predicate availability ở search/chi tiết/slot/book: account enabled, doctor active, specialty active; slot còn trống và tương lai, schedule active. POST vẫn phải kiểm tra lại. Không cần đổi status Doctor theo Account trong DB chỉ để đồng bộ hiển thị.

### B06 Hủy ca rồi tạo lại cùng giờ đụng constraint slot cũ

**Suy luận từ schema, ưu tiên cao.** `ScheduleService.java:34` chỉ kiểm tra overlap ca active; deactivate ở `:65` giữ ca và slot, chuyển slot BLOCKED. V1 migration dòng 54 giữ UNIQUE(doctor_id,start_at) trên mọi slot. Tạo ca mới cùng giờ vượt overlap check nhưng chèn slot trùng unique, trả conflict chung. Chưa có restore ca.

Đề xuất: chọn đường phục hồi ca/slot cũ với kiểm tra overlap thay vì tạo bản sao. Không xóa slot đã có appointment CANCELED tham chiếu; không bỏ UNIQUE vội. Nếu không hỗ trợ tạo lại thì trả rule rõ ràng, nhưng UX sẽ hạn chế. Cần test cả ca có lịch hủy trong quá khứ tham chiếu slot.

### B07 Đặt lịch và hủy ca không dùng chung giao thức khóa

**Rủi ro cạnh tranh, ưu tiên cao.** Book khóa doctor -> slot rồi đọc schedule.active. Deactivate khóa schedule, query tồn tại BOOKED và cập nhật slots, không lấy cùng doctor lock. Check chưa BOOKED rồi request book chen vào là interleaving cần kiểm chứng; JOIN FETCH/DB có thể thay đổi phạm vi khóa thực tế.

Đề xuất: vẽ thứ tự khóa chung cho create/deactivate/book/cancel/status; test hai chiều ai giữ khóa trước trên MySQL cô lập, assert trạng thái DB sau commit. Không kết luận thêm @Transactional là đủ. Không vá bằng khóa rời rạc mà chưa xét deadlock.

### B08 Chưa chặn bệnh nhân đặt hai bác sĩ cùng giờ

**Code + quyết định nghiệp vụ, ưu tiên cao nếu một account đại diện một người khám.** Book chỉ kiểm tra slot/doctor, không kiểm tra lịch của patient. Hai slot của hai bác sĩ khác nhau cùng giờ đều có thể đặt.

Đề xuất: hiện model chỉ có một patient/account, chưa có người thân, nên cấm overlap appointment còn hiệu lực của cùng patient là lựa chọn hợp lý. Nếu chốt cấm, kiểm tra khoảng [start,end) và điều phối theo patient khi request đồng thời; query exists đơn thuần chưa đủ. Không thêm tính năng đặt hộ chỉ để biện minh cho hành vi này.

## Các lỗi chức năng cần sửa tiếp

### B09 Patient cũ thiếu profile không có đường tự hoàn thiện

**Code.** `patient/service/PatientProfileService.java:28,37` GET và PUT cùng throw khi thiếu profile. V2 chỉ tạo bảng; register mới tạo profile nhưng account cũ vẫn không có. Tên fallback trong appointment không giải quyết việc sửa hồ sơ.

Đề xuất: GET trả dữ liệu account kèm profile rỗng, PUT tạo lần đầu có kiểm soát hoặc backfill riêng; quyền và unique account_id vẫn giữ.

### B10 Bộ lọc trạng thái lịch patient chỉ lọc trang hiện tại

**Code.** FE `features/patient/pages/appointments/patient-appointments.component.ts:485,505` tải một page rồi filter mảng. Chọn Hoàn thành có thể hiện rỗng dù lịch hoàn thành nằm trang sau, trong khi paginator vẫn tính tất cả status.

Đề xuất: thêm filter status ở BE rồi đếm/phân trang cùng filter; reset page khi đổi filter. Hoặc ghi rõ chỉ lọc trang hiện tại, nhưng không phù hợp UX mong muốn.

### B11 Dashboard doctor đếm tối đa 50 bản ghi, không phải tổng

**Code.** FE `features/doctor/pages/dashboard/doctor-dashboard.component.ts:465` tải page 0 size 50, đếm status trên content và lấy slice(0,5) làm lịch gần đây. BE `AppointmentService.java:97` không có sort xác định. Khi dữ liệu tăng, số lượng sai và lịch gần đây không chắc gần nhất. Ca làm việc cũng tải tất cả ca active cả quá khứ rồi dùng schedules.length như ca mở.

Đề xuất: chốt thống kê cho hôm nay hay toàn thời gian; API aggregate theo doctor và khoảng ngày. Danh sách cần việc dùng status/time/sort rõ ràng. Mọi page appointment có sort thời gian + id để ổn định.

### B12 FE vẫn hiển thị hành động BE chắc chắn từ chối

**Code.** canCancel ở patient appointments dòng 522 chỉ xét status, BE còn xét giờ bắt đầu. Lịch quá giờ vẫn có nút Hủy. Doctor complete chỉ xét status tương ứng B03. FE doctor vẫn hiển thị mã patient (`doctor-appointments.component.ts:66`, dashboard `:164`) dù BE đã thêm tên/phone; đây là tích hợp chưa nối, không phải BE còn thiếu DTO.

Đề xuất: phản ánh rule thời gian ở UI, BE là nơi quyết định cuối. Đồng bộ model/template tên patient; xử lý thay đổi trạng thái sau khi request thành công.

### B13 Đổi ngày nhanh có thể tải nhầm slot ngày trước

**Rủi ro bất đồng bộ đọc từ code, chưa tái hiện browser.** FE `features/patient/pages/booking/patient-booking.component.ts:334` tạo subscription riêng mỗi lần đổi ngày, không hủy request cũ hoặc đối chiếu ngày khi response về. Request ngày A về sau B có thể ghi đè slots trong lúc selectedDate là B. Nguy hiểm vì POST chỉ gửi slotId, BE vẫn có thể đặt hợp lệ ngày A dù người dùng đang nhìn ngày B.

Đề xuất: switchMap hoặc request sequence/date guard, xóa slots ngay khi đổi ngày, xác nhận ngày giờ slot trước submit. Test cố tình đảo thứ tự response.

### B14 Biên ngày và độ chính xác thời gian chưa kín

**Code + rủi ro triển khai.** availableSlots dùng BETWEEN bao gồm cả hai đầu với to = 00:00 ngày sau; có thể trả slot đầu ngày sau. validateTime kiểm tra second nhưng không nano, nên nhận thời gian lệch microsecond khỏi mốc. LocalDateTime.now() phụ thuộc timezone JVM, chưa thấy Clock/ZoneId cố định trong code.

Đề xuất: khoảng ngày [from,to), căn cả nano, dùng giờ phòng khám/Clock nhất quán. Kiểm tra ngày quá khứ, đúng nửa đêm và deployment timezone khác. Không dùng cron để chữa bộ lọc thời gian.

### B15 Nội dung lịch sử đang lấy thông tin hiện tại

**Quyết định dữ liệu.** Appointment response map tên doctor, tên/phone patient hiện tại; cập nhật profile sẽ đổi nội dung hiển thị lịch cũ. Đây có thể hợp lý cho danh sách liên hệ, nhưng không nên vô tình dùng như bản ghi y khoa bất biến.

Đề xuất: giữ dữ liệu liên hệ hiện tại cho danh sách; khi thiết kế medical record chốt snapshot cần lưu, người ghi và lịch sử sửa. Không sao chép mọi trường vào appointment khi chưa có yêu cầu.

### B16 Cập nhật chuyên khoa bác sĩ không giải quyết lịch đã có

**Quyết định nghiệp vụ.** DoctorService.update đổi specialty bất kể có ca/appointment tương lai. Ca đặt khi bác sĩ ở chuyên khoa A sẽ hiện doctor ở B sau update, appointment không lưu specialty lúc đặt.

Đề xuất: chốt chặn đổi khi có lịch chưa xử lý hoặc lưu specialty tại thời điểm đặt để giữ ngữ cảnh; không tự đổi appointment cũ. Đưa vào thiết kế kết quả khám trước khi triển khai.

## Rủi ro kỹ thuật ảnh hưởng rule

- Account/specialty status không dùng cùng khóa với book/create/update doctor. Check trạng thái có thể bị đổi đồng thời. Tương tự specialty active doctor check vs mở/chuyển doctor. Cần test phối hợp và khóa chung theo invariant đã chốt.
- Book khóa cả doctor nên các booking khác slot của cùng doctor cũng tuần tự. Đang giúp phối hợp inactive nhưng có đánh đổi throughput; chưa cần tối ưu trước khi đúng dữ liệu.
- DB không có unique appointment.slot_id: không tự coi là sai vì hủy rồi đặt lại cần nhiều dòng lịch sử cùng slot. Invariant là tối đa một appointment còn hiệu lực, cần kiểm chứng qua luồng khóa; không thêm UNIQUE(slot_id) tùy tiện.
- Slot BOOKED sau COMPLETED đang giữ trạng thái chiếm chỗ: không tự coi bug; slot quá khứ không cần mở lại. Nhưng deactivate kiểm tra bất kỳ BOOKED nên ca có lịch completed cũng bị chặn hủy; cần tách quản lý ca tương lai khỏi lưu trữ lịch sử, không reset slot lịch cũ chỉ để xóa ca.
- Username kiểm tra độ dài trước trim; create doctor không có min fullName sau trim như update. Ví dụ username có khoảng trắng bao quanh có thể đạt min trước trim nhưng quá ngắn sau trim; tên doctor ngắn được tạo nhưng cùng tên lại bị từ chối khi update. @NotBlank vẫn chặn chuỗi toàn khoảng trắng. Unique DB có giữ chống trùng nhưng lỗi cạnh tranh trả conflict chung. Ưu tiên chuẩn hóa và validate sau normalize.
- AdminDoctorController không có @Validated/@Max(100) như hướng dẫn trước; service cap size ở 100. Không tuyên bố mọi validation nhờ annotation đã hoạt động ở mọi endpoint; case HTTP user đã chạy là bằng chứng của case cụ thể. Cần test riêng page=-1,size=20 và page=0,size=0 với ADMIN còn hạn.
- FE interceptor xóa storage khi 401 nhưng không gọi AuthService.logout để cập nhật BehaviorSubject; trạng thái trong bộ nhớ có thể cũ cho tới reload/login. normalizeRole vẫn mặc định PATIENT khi role lạ; nên fail rõ thay vì che contract lỗi. Không có bằng chứng vượt quyền BE từ hai điểm này.

## Những phần hiện có hợp lý nên giữ

- Register ép PATIENT, không tin role từ client; password BCrypt; DTO không trả hash.
- JWT đọc trạng thái account lại cho request mới; isEnabled được kiểm tra. Khóa account không cần tự ghi false sang mọi profile mới chặn truy cập.
- API admin và public tách riêng; inactive vẫn được admin đọc. Các thao tác patient/doctor có role và kiểm tra ownership trong service.
- Book tạo appointment và đổi slot trong transaction; cancel chỉ đúng patient, mở slot trước giờ; status chỉ đúng doctor.
- Schedule tạo slot nửa giờ và chặn overlap theo khoảng; ca tiếp giáp được phép.
- Gom profile theo accountIds cho trang lịch hẹn; fallback tên cũ giữ luồng đọc được. Cần kiểm tra SQL khi đo hiệu năng, không kết luận mọi query toàn luồng chỉ có hai câu.

## Các chức năng còn thiếu theo phạm vi

Medical records và notifications có bảng V2 nhưng chưa có entity/service/controller; admin theo dõi lịch hẹn toàn hệ thống chưa có API. FE tương ứng vẫn là trang đang phát triển. Đây là backlog chức năng, không phải lý do viết lại project. Không thêm đơn thuốc/thanh toán/microservices trong đợt sửa rule.

## Ma trận đề xuất cần chốt

| Tình huống | Đọc dữ liệu | Tạo ca/nhận booking mới | Lịch hẹn đã có |
|---|---|---|---|
| Account khóa | Bị chặn với account đó; admin vẫn xem được | Chặn | Giữ lịch sử, đưa vào luồng admin xử lý nếu bị ảnh hưởng |
| Doctor inactive, account mở | Đề xuất cho doctor đọc lịch sử | Chặn | Phải chốt cho xử lý ca tồn hoặc chỉ admin xử lý |
| Specialty inactive | Ẩn khỏi tìm kiếm/booking mới | Chặn booking mới | Giữ lịch sử; rule hiện tại buộc ngừng doctor trước khi tắt specialty |
| Không có ca tương lai | Doctor vẫn hoạt động, xem dữ liệu được | Không có slot để đặt | Không tự đổi Doctor.active |
| Slot hết giờ | Đọc lịch sử | Chặn đặt | Kết thúc theo trạng thái đã chốt, không giả là đã khám |

## Thứ tự triển khai sau khi chốt rule

1. Chốt ma trận trạng thái/quyền và vòng đời appointment; đây là cơ sở sửa B01–B05, B08.
2. Sửa ca/slot: restore hoặc tái tạo an toàn, biên giờ, predicate availability và thống nhất khóa; test rollback và cạnh tranh trên DB cô lập.
3. Sửa lịch hẹn quá hạn, hoàn thành và dữ liệu profile cũ; test ownership âm và thời gian.
4. Đồng bộ FE filter/paging, số liệu, tên patient, hành động theo giờ và response slot đảo thứ tự.
5. Làm medical records, notifications, admin appointment theo rule đã ổn; Android dùng contract được chốt.

Ưu tiên đầu tiên không phải thay mỗi getByAccountId. Cần chốt tối thiểu: doctor inactive nghĩa gì; ai xử lý lịch bị ảnh hưởng; xử lý PENDING quá giờ/NO_SHOW; giờ cho complete; cấm patient overlap; phục hồi ca hủy. Thực hiện từng nhóm và giữ báo cáo này làm checklist regression.

## Giới hạn kiểm chứng

Lượt này đọc source/schema/test và chạy lại test hiện có bằng H2 trong bộ nhớ: **23 test, 0 failure, 0 error, BUILD SUCCESS**, kết thúc 15/10/2026 13:55:56 Asia/Saigon. Log: target/business-audit-tests.log. Không gọi API trên database thật hoặc thao tác giao diện bằng tài khoản người dùng. Các scenario mới trong report chưa có test tái hiện trong lượt này. Không dùng H2 để khẳng định đúng row lock/isolation/deadlock của MySQL.
