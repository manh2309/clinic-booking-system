> Cập nhật 20/10/2026: đã triển khai lượt đầu. Bằng chứng và khác biệt so với đề xuất nằm trong SCHEDULING_IMPLEMENTATION_2026-10-20.md; phần dưới giữ làm căn cứ thiết kế.

# Đề xuất lịch định kỳ và nghỉ bác sĩ — 20/10/2026

Trạng thái: thiết kế đề xuất sau khi đọc code, chưa triển khai hoặc thay đổi dữ liệu.

## Phạm vi
Phòng khám ngoại trú theo lịch hẹn, không cấp cứu, không quản lý thủ thuật/phẫu thuật hoặc hàng đợi khám thực tế. Slot là thời lượng dự kiến để đặt hẹn, không cam kết thời gian khám thực tế. Giữ 30 phút ở lượt đầu để không làm sai dữ liệu cũ. Giờ làm, ngày làm, thời lượng là chính sách phòng khám, không suy ra chuẩn y khoa.

## Phân công
Admin cấu hình mẫu lịch tuần theo bác sĩ, ngày hiệu lực và các khoảng giờ; ví dụ 08–12 và 13–17 (8 giờ làm, nghỉ trưa không sinh slot), ngày nghỉ không sinh. Doctor xem lịch, gửi đơn nghỉ có khoảng thời gian/lý do. Hệ thống sinh ca/slot cụ thể cho 30 ngày tới, ngay khi áp dụng mẫu và bù mỗi ngày theo Asia/Ho_Chi_Minh. 30 ngày là mặc định đề xuất có cấu hình. Không đợi đúng ngày khám mới sinh lịch.

Mẫu thay đổi chỉ tác động tương lai; không xóa/đổi slot đã có lịch hẹn, không sinh trùng slot thủ công cũ. Gặp xung đột đưa danh sách admin xử lý, không bỏ qua âm thầm. Không tự mở lại slot đã bị chặn bằng đơn nghỉ/ngoại lệ.

## Nghỉ và lịch hẹn bị ảnh hưởng
Đơn: PENDING -> APPROVED/REJECTED; doctor được rút đơn PENDING. Khoảng [from,to) có thể nửa ngày, một ngày hoặc nhiều ngày; không nhận yêu cầu hồi tố qua luồng thường. Lưu người duyệt, thời điểm, lý do. PENDING chưa phải được duyệt; để tránh phát sinh khách trong lúc chờ, tạm giữ slot trống giao khoảng đơn. Không thay đổi lịch đã đặt lúc nộp. Từ chối/rút đơn chỉ giải phóng giữ nếu không còn lý do chặn khác và slot còn tương lai.

Admin xem trước số slot và PENDING/CONFIRMED bị ảnh hưởng. Nếu không có lịch hẹn: duyệt và chặn slot. Nếu đã có: duyệt kèm xác nhận hủy các lịch bị ảnh hưởng, lưu CANCELED và mã DOCTOR_LEAVE, thông báo bệnh nhân/doctor trong cùng giao dịch; bệnh nhân được hướng dẫn chọn lịch mới. Không ghi EXPIRED/NO_SHOW vì bác sĩ nghỉ. Không tự chuyển bác sĩ hoặc giờ khi bệnh nhân chưa đồng ý. In-app hiện có không bảo đảm bệnh nhân đã biết: cần danh sách admin liên hệ ngoài hệ thống, trạng thái liên hệ tách trạng thái đã đọc.

Nghỉ gấp trong ngày chỉ tác động slot chưa bắt đầu; ca đang khám và ca quá khứ không bị đổi hàng loạt. Nếu có ca đang diễn ra, báo xung đột để admin xử lý riêng. Không tái mở lịch hẹn đã hủy khi đơn nghỉ được điều chỉnh.

## Dữ liệu và đồng thời
Thêm weekly_schedule_template (effective dates/day/time windows), leave_request, slot_block (reason/source id) hoặc cấu trúc tương đương có thể biểu diễn nhiều lý do chặn. Giữ doctor_schedule/schedule_slot/appointment hiện có và lịch sử. Không dùng một boolean cho tất cả nguyên nhân khóa.
Khả dụng = account/doctor/specialty cho phép + ca hiệu lực + slot còn tương lai + chưa đặt + không có block áp dụng.
Job idempotent, unique khóa bác sĩ/thời điểm, transaction theo bác sĩ/ngày, retry có giới hạn và quan sát lỗi; kiểm tra leave cả khi sinh lịch lẫn booking. Approval và booking cùng thứ tự khóa doctor -> schedule -> slot -> appointment; luồng có patient lock giữ quy ước hiện có patient trước doctor, không lấy thêm patient lock sau doctor. Chạy lại preview và danh sách affected trong transaction duyệt để tránh khách đặt chen. Test MySQL thực cho race.

## Hiển thị
Ca: Sắp tới / Đang diễn ra / Đã kết thúc / Đã hủy. Đang diễn ra không đồng nghĩa còn slot nhận khách. Slot quá giờ chưa đặt: Đã qua giờ; slot đã đặt giữ lịch sử Đã đặt; có lý do chặn hiển thị phù hợp. Chỉ số slot còn nhận khách tính theo điều kiện khả dụng. Mặc định hôm nay/sắp tới, lịch sử riêng. Không cần cron đổi active=false cho mọi ca cũ để UI đúng.

## Thứ tự triển khai
1. Sửa nhãn ca/slot quá khứ, múi giờ, bộ lọc và kiểm thử ranh giới giờ.
2. Mẫu lịch admin, job sinh trước 30 ngày, xử lý ca cũ và chống trùng; thay form tạo ca doctor bằng xem lịch (đặc biệt ca bổ sung do admin).
3. Đơn nghỉ, giữ slot, preview ảnh hưởng, duyệt/hủy có lý do và thông báo, danh sách liên hệ.
4. Đồng bộ web/app; test quyền, tranh chấp, rollback, job chạy lại và app/web end-to-end.

## Nguồn và giới hạn
FHIR là tham chiếu mô hình dữ liệu, không quy định quy trình xin nghỉ hay thời lượng khám cho đề tài. Các chính sách 30 phút, 30 ngày, giữ slot khi chờ duyệt, hủy có xác nhận là đề xuất riêng cần được trình bày như giả định của hệ thống.
- https://hl7.org/fhir/R5/schedule.html : tách Schedule, Slot và Appointment, có planning horizon và blocked slot.
- https://fhir.hl7.org/fhir/slot.html : slot là khoảng thời gian có thể đặt, tùy loại dịch vụ.
- https://hl7.org/fhir/R5/appointment.html : trạng thái/lý do hủy của appointment độc lập slot.

## Bằng chứng code hiện tại
ScheduleService SLOT_MINUTES=30; create là doctor tự tạo từng ca, chưa có weekly template/job sinh lịch. availableSlots có now filter; deactivate từ chối BOOKED; restore ca tương lai. FE DoctorScheduleComponent đang ghi Đang mở theo active và Trống theo AVAILABLE nên nhãn quá khứ gây hiểu nhầm. Chưa chứng minh runtime/patient UI trong lượt review này.
