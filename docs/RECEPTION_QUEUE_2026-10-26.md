# Tiếp nhận và hàng chờ khám

## Luồng sử dụng

Admin xác nhận bệnh nhân đã có mặt → bệnh nhân vào hàng chờ của bác sĩ → bác sĩ bắt đầu khám → bác sĩ nhập kết quả và hoàn thành → bệnh nhân đọc kết quả trên web/app.

Đã triển khai cùng backend hiện có, website ba vai trò và ứng dụng bệnh nhân. Không thêm vai trò lễ tân, khách vãng lai, hàng chờ cấp cứu, dự đoán thời gian chờ hay tự check-in từ xa.

## Quy tắc

- Tiếp nhận chỉ áp dụng cho lịch CONFIRMED trong ngày phòng khám, từ 30 phút trước giờ bắt đầu đến 60 phút sau giờ kết thúc của slot. Giới hạn này là chính sách của phiên bản đồ án.
- Bác sĩ, account bác sĩ, chuyên khoa và ca phải còn hoạt động; account bệnh nhân phải còn hoạt động. Đơn nghỉ PENDING/APPROVED chặn tiếp nhận và bắt đầu khám trong khoảng nghỉ.
- Trạng thái lịch hẹn giữ nguyên bộ PENDING/CONFIRMED/COMPLETED/CANCELED/EXPIRED/NO_SHOW. Trạng thái tiếp nhận nằm riêng trong clinic_visits: WAITING/IN_PROGRESS/COMPLETED/CANCELED.
- Thứ tự gợi ý dùng max(giờ hẹn, thời điểm tiếp nhận), sau đó ID lượt tiếp nhận. Bệnh nhân đến muộn không tự chiếm vị trí trước những người đã đến chỉ vì có giờ hẹn sớm hơn. Đây không phải cam kết về thời gian chờ hoặc cơ chế ưu tiên y tế.
- Bác sĩ chỉ bắt đầu khi đúng ngày và đã đến giờ hẹn. Mỗi bác sĩ có tối đa một lượt IN_PROGRESS trên toàn hệ thống, kể cả lượt còn tồn từ hôm trước. Bảng luôn hiển thị lượt đang khám dù khác ngày đang lọc; cần hoàn thành lượt cũ trước khi mở lượt mới.
- Hàng chờ có phân trang. Bác sĩ chọn người bắt đầu từ danh sách; hệ thống không tự gọi bệnh nhân hoặc bắt buộc chọn đúng vị trí đầu tiên.
- Lưu kết quả bắt buộc có lượt IN_PROGRESS. Kết quả, trạng thái lịch, trạng thái lượt, giải phóng quyền đang khám, sự kiện và thông báo cùng transaction.
- Người đã tiếp nhận không được chuyển sang vắng mặt, hủy lịch, đổi giờ hoặc bị hủy do duyệt nghỉ qua các luồng cũ. Admin phải rút tiếp nhận khi còn WAITING, ghi rõ lý do; việc này không tự hủy lịch hẹn. IN_PROGRESS không cho rút tiếp nhận bằng thao tác này.
- Có thể tiếp nhận lại sau khi rút nếu vẫn đáp ứng điều kiện. clinic_visit_events giữ từng thao tác CHECKED_IN/CANCELED/STARTED/COMPLETED, người thực hiện, thời điểm và lý do. Không xóa dấu vết lần rút trước.
- Bấm nhận hoặc bắt đầu lặp lại trong trạng thái phù hợp trả về lượt hiện có, không tạo thêm sự kiện. Khóa patient → doctor → schedule → slot → appointment → visit và khóa duy nhất active_doctor_id bảo vệ trường hợp hai tab cùng bắt đầu khám.

## Màn hình

### Admin

Vào Lịch hẹn. Khối Tiếp nhận và hàng chờ ở đầu trang cho chọn bác sĩ, ngày và xem người đang khám/người đang chờ. Danh sách bác sĩ có phân trang. Từ danh sách lịch hẹn bên dưới, chọn lịch đã xác nhận đủ điều kiện rồi bấm Tiếp nhận bệnh nhân sau khi kiểm tra người đến tại phòng khám. Nếu bệnh nhân rời đi khi đang chờ, chọn Rút tiếp nhận và nhập lý do.

### Bác sĩ

Vào Lịch hẹn. Hàng chờ và lượt đang khám nằm ở đầu trang. Bấm Bắt đầu khám rồi Ghi kết quả và hoàn thành. Form kết quả vẫn kiểm tra chẩn đoán, giữ nội dung khi gửi lỗi và không cho gửi lặp. Lịch chưa tiếp nhận/bắt đầu không còn nút hoàn thành trực tiếp chỉ vì đã đến giờ.

### Bệnh nhân

Web/app hiển thị đã có mặt, chờ khám, đang khám hoặc đã rút tiếp nhận trong lịch hẹn. Không có nút tự nhận diện có mặt. Nút hủy không xuất hiện khi còn WAITING/IN_PROGRESS. Có thông báo trong ứng dụng; chưa có push/SMS/email. Làm mới hoặc quay lại tab để tải trạng thái mới, không mô tả là cập nhật realtime.

## API sau /api/v1

| Phương thức | Đường dẫn | Quyền |
|---|---|---|
| POST | /admin/appointments/{id}/check-in | ADMIN |
| PATCH | /admin/appointments/{id}/reception/cancel | ADMIN, body reason |
| POST | /doctors/me/appointments/{id}/start | DOCTOR phụ trách |
| GET | /admin/queue?doctorId=&date=&page=&size= | ADMIN |
| GET | /doctors/me/queue?date=&page=&size= | DOCTOR, tự xác định bác sĩ từ phiên |

Giữ endpoint hoàn thành hiện có /doctors/me/appointments/{id}/complete; thêm điều kiện lượt đang khám. Các API danh sách lịch hẹn bổ sung trường visit nullable, không bỏ trường cũ. Lịch sử đã hoàn thành trước migration có thể không có visit; vẫn đọc được kết quả cũ.

V8 tạo clinic_visits/clinic_visit_events, V9 mở rộng loại thông báo. Restart backend để Flyway áp dụng. Không chạy SQL tay, không đổi trạng thái hàng loạt các lịch cũ và không mặc định coi lịch đã xác nhận là đã có mặt.

## Kiểm chứng

- 12 trường hợp hàng chờ đạt trên MySQL 8.0.39 riêng cổng 13326: thời gian tiếp nhận, quyền sở hữu, nhận lặp, bắt đầu lặp, hai lượt cạnh tranh, hoàn thành kèm bệnh án, rollback, rút/tiếp nhận lại, chặn hủy/đổi lịch/vắng mặt/duyệt nghỉ và lượt cũ chưa hoàn thành.
- Cùng lượt MySQL chạy cả bộ đổi lịch, bệnh án/thông báo và lịch/nghỉ; 61 test đạt trong bốn nhóm. Kết quả lưu ở target/visit-mysql-tests.log và target/visit-mysql-results.
- Web: 104 test ChromeHeadless đạt, có kiểm tra bố cục hàng chờ 390/1440px, chặn thao tác lặp, lỗi tranh lượt và rút tiếp nhận cần lý do. Production build đạt. Log FE/tmp/visit-web-tests.log và FE/tmp/visit-build.log.
- Flutter: 35 test đạt, analyze sạch, APK debug build đạt. Log build/visit-tests.log, build/visit-analyze.log, build/visit-apk.log.
- Các kết quả này không thay thế nghiệm thu tay trọn luồng trên trình duyệt/emulator của người dùng. Chưa có số liệu vận hành thực tế hoặc đo thời gian chờ.

## Test tay

1. Restart backend, F5 web và chạy app/APK mới. Chuẩn bị lịch hôm nay gần giờ kiểm tra, đã đặt và được bác sĩ xác nhận; không sửa đồng hồ máy để vượt điều kiện giờ.
2. Trước giờ hẹn quá 30 phút hoặc sau giờ kết thúc quá 60 phút, tiếp nhận phải bị chặn. Lịch PENDING/CANCELED cũng không nhận được.
3. Admin tiếp nhận đúng khoảng giờ. Kiểm tra bệnh nhân xuất hiện một lần trong hàng chờ đúng bác sĩ, app/web bệnh nhân hiển thị chờ khám và không có nút hủy.
4. Bác sĩ bấm Bắt đầu khám khi đến giờ. Trên tab khác thử bắt đầu người thứ hai: phải bị chặn, không tạo hai lượt đang khám.
5. Ghi chẩn đoán và hoàn thành. Hàng chờ bỏ lượt đang khám cũ, cho bắt đầu lượt kế tiếp; patient xem cùng kết quả trên web/app. Làm mới trang không được tạo thêm kết quả hoặc sự kiện.
6. Với bệnh nhân đang chờ khác, rút tiếp nhận có lý do. Kiểm tra lịch hẹn vẫn CONFIRMED; có thể nhận lại nếu còn trong khoảng giờ. Không rút được bệnh nhân đã bắt đầu khám.
7. Thử ghi vắng mặt hoặc đổi giờ khi đã tiếp nhận: phải từ chối. Đơn nghỉ mới ảnh hưởng người đã tiếp nhận không được duyệt hủy âm thầm.
8. Mất mạng/bấm liên tục/quay lại trang: tải lại trạng thái trước khi gửi lại khi chưa biết kết quả. Chỉ xác nhận ca đạt sau khi quan sát dữ liệu backend, web và app nhất quán.

Bản thảo Word ngày 24/10 vẫn cần cập nhật chương thiết kế/cài đặt/kiểm thử và bổ sung ảnh thực tế sau nghiệm thu hai phần mới. Không tính cả tài liệu Word cũ là đã được cập nhật tự động.
