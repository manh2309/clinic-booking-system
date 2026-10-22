## Bổ sung áp dụng hàng loạt

Admin hiện chọn nhiều thứ và nhiều khoảng giờ, mặc định T2–T6 / 08–12 / 13–17, bấm Áp dụng lịch tuần một lần. Mẫu giống hệt được bỏ qua; trùng một phần rollback cả lượt. Endpoint batch, test và payload xem CLINICAL_FLOW_API.md. BE 86 test H2, 22 test planning trên MySQL riêng, web 75 test pass. Restart BE rồi F5 web để dùng. Không cần migration mới.

# Lịch tuần và nghỉ bác sĩ — triển khai 20/10/2026

## Đã có
- Admin: `/admin/planning` (menu Lịch tuần / Nghỉ). Chọn bác sĩ, thêm mẫu theo thứ/giờ/ngày hiệu lực, tạo hoặc bù lịch, dừng mẫu từ ngày mai trở đi.
- Doctor: `/doctor/schedule` chỉ xem ca; `/doctor/leaves` gửi hoặc rút đơn PENDING. Các endpoint doctor tự tạo/hủy/khôi phục ca cũ bị chặn 403.
- Mỗi khoảng giờ tối thiểu 30 phút và căn mốc 00/30; tổng mẫu giao ngày hiệu lực không quá 8 giờ/ngày. Tạo mẫu sáng và chiều riêng để nghỉ trưa. Không áp một thời lượng khám thật cho mọi bệnh.
- Sinh ca trước 30 ngày, khi thêm mẫu, khi khởi động và 00:05 mỗi ngày theo Asia/Ho_Chi_Minh. Job chạy lại không nhân đôi; bác sĩ/account/chuyên khoa inactive không sinh mới.
- PENDING/APPROVED leave là lý do chặn slot độc lập. Cả GET slot public và POST booking kiểm tra. Rút/từ chối đơn không thay trạng thái ca/slot gốc, nên không mở nhầm ca đã hủy hoặc slot đã đặt.
- Admin xem ảnh hưởng rồi duyệt/từ chối, bắt buộc lý do. Nếu có lịch hẹn phải xác nhận hủy; server kiểm tra lại dưới khóa. Hủy lịch, lý do, contact task và notification cùng transaction.
- Nghỉ không ghi bệnh nhân NO_SHOW/EXPIRED. Không tự chuyển bác sĩ/giờ, không phục hồi lịch đã hủy. Contact task riêng trạng thái đọc notification; admin đánh dấu sau khi thực sự liên hệ.
- Ca quá khứ vẫn lưu, hiển thị Đã kết thúc; slot quá giờ chưa đặt hiển thị Đã qua giờ. Mặc định hôm nay/sắp tới; lịch sử chọn bộ lọc. Bộ đếm còn nhận lịch loại slot quá giờ/đã đặt/bị chặn và bác sĩ unavailable.

## Giữ dữ liệu và giới hạn
- V5 thêm weekly_schedules, doctor_leaves, leave_contacts và nguồn mẫu trên doctor_schedules; không sửa migration cũ, không xóa lịch/slot/appointment hiện có.
- Mẫu mới không tự sửa hoặc xóa ca có sẵn. Nếu trùng ca thủ công, ca đã hủy hoặc ca khác khoảng giờ, trả danh sách conflict và giữ nguyên. Chưa có công cụ di chuyển hàng loạt lịch cũ hoặc đổi mẫu chồng lấn trong thời gian đã sinh ca; cần xử lý từng trường hợp trước khi áp mẫu thay thế.
- Dừng mẫu đóng các ca tương lai được sinh từ mẫu đó; nếu còn BOOKED thì từ chối toàn bộ, admin xử lý lịch hẹn trước. Không đóng ca thủ công không thuộc mẫu.
- Đơn nghỉ tối đa 31 ngày, không cho gửi hồi tố; đơn đã duyệt không sửa/rút. Duyệt muộn nếu có lịch đang diễn ra bị chặn, không tự hủy ca đang khám. Chưa có luồng đặc biệt hủy ca đang khám.
- Thông báo hiện tại là in-app, chưa có SMS/email/push. Thời lượng 30 phút và horizon 30 ngày là chính sách đồ án. Chưa thêm service duration hay phòng khám đa tài nguyên.
- Màn mới đã render trong ChromeHeadless ở 390/1440px; chưa thao tác trực tiếp đủ ba vai trò trên phiên web/MySQL của người dùng. App không đổi contract patient; chỉ cập nhật script smoke tạo mẫu bằng admin thay API doctor cũ.

## Kiểm chứng
- H2: toàn bộ 80 test pass trước khi thêm HTTP round-trip cuối; source cuối kiểm tra tiếp trên MySQL.
- MySQL 8: toàn bộ 81 test pass, 0 failure/error; 17 case SchedulePlanningIntegrationTests. Hai migration test chuyên biệt vẫn chủ động dùng H2. MySQL dùng container riêng, cổng 13316, không tác động DB người dùng cổng 3306.
- Các case mới: 8h/ngày/lunch/overlap; job idempotent và hai generator song song; ca cũ conflict; pending leave chặn booking, reject/withdraw, hủy patient trong pending leave; approve cần xác nhận; notification failure rollback; ownership/role; booking và đơn nghỉ theo cả hai thứ tự; HTTP create weekly → book → request leave → approve → canceled preview.
- Web: 64 ChromeHeadless test pass; development build pass. File bằng chứng: FE/tmp/planning-web-tests.log, FE/tmp/planning-build.log. BE/target/planning-mysql-tests.log.
- Flutter analyze sạch sau cập nhật script smoke. Chưa chạy lại smoke qua HTTP/mobile emulator trong lượt này.

## Test tay nhanh
1. Restart BE để Flyway chạy V5; chạy web rồi F5. Không cần chạy SQL tay hay sửa lịch cũ.
2. ADMIN → Lịch tuần / Nghỉ → chọn bác sĩ → chọn ngày hiệu lực chứa ngày mai → thêm khoảng 08–12 và 13–17 cho thứ tương ứng. Kiểm tra 2 ca / 16 slot, không có slot 12–13. Bấm bù lịch không được nhân đôi.
3. PATIENT đặt một slot; DOCTOR thấy lịch hẹn tương ứng. DOCTOR → Đơn nghỉ → gửi khoảng bao trùm slot đó và một slot trống.
4. PATIENT không thấy slot trống bị giữ và không đặt được bằng slot ID cũ. Lịch đã đặt chưa bị hủy trước khi duyệt.
5. ADMIN → đơn PENDING → Xem ảnh hưởng → thấy người đặt và số điện thoại. Không xác nhận hủy thì không duyệt được. Điền lý do, xác nhận hủy, duyệt.
6. PATIENT thấy CANCELED/lý do bác sĩ nghỉ và thông báo. ADMIN xem đơn APPROVED, liên hệ thật rồi đánh dấu. Slot nghỉ vẫn đóng sau restart/job bù.
7. Một đơn khác: từ chối/rút đơn → chỉ slot hợp lệ trở lại. Đặt/hủy trong khi pending rồi rút đơn cũng không làm mất slot.
8. Doctor xem bộ lọc Đã kết thúc: ca quá khứ không ghi Đang mở/Trống. Bác sĩ khác/patient không được quản trị mẫu/duyệt nghỉ.
