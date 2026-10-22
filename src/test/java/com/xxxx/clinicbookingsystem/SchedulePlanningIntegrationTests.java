package com.xxxx.clinicbookingsystem;
import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.appointment.dto.CreateAppointmentRequest;
import com.xxxx.clinicbookingsystem.appointment.repository.AppointmentRepository;
import com.xxxx.clinicbookingsystem.appointment.service.AppointmentService;
import com.xxxx.clinicbookingsystem.auth.security.CustomUserDetails;
import com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.repository.DoctorRepository;
import com.xxxx.clinicbookingsystem.role.repository.RoleRepository;
import com.xxxx.clinicbookingsystem.schedule.dto.CreateScheduleRequest;
import com.xxxx.clinicbookingsystem.schedule.repository.*;
import com.xxxx.clinicbookingsystem.schedule.service.ScheduleService;
import com.xxxx.clinicbookingsystem.schedule.planning.*;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.repository.SpecialtyRepository;
import com.xxxx.clinicbookingsystem.notification.repository.NotificationRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.xxxx.clinicbookingsystem.schedule.planning.PlanningDtos.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class SchedulePlanningIntegrationTests {
 @Autowired SchedulePlanningService planning;
 @Autowired ScheduleService schedules;
 @Autowired AppointmentService booking;
 @Autowired AccountRepository accounts;
 @Autowired DoctorRepository doctors;
 @Autowired SpecialtyRepository specialties;
 @Autowired RoleRepository roles;
 @Autowired ScheduleRepository scheduleRepository;
 @Autowired ScheduleSlotRepository slots;
 @Autowired AppointmentRepository appointments;
 @Autowired NotificationRepository notifications;
 @Autowired WeeklyScheduleRepository weekly;
 @Autowired DoctorLeaveRepository leaves;
 @Autowired Clock clock;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired com.xxxx.clinicbookingsystem.auth.jwt.JwtService jwt;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
 com.xxxx.clinicbookingsystem.notification.service.NotificationService notificationService;
 @Autowired PlatformTransactionManager transactions;
 @AfterEach void clear(){SecurityContextHolder.clearContext();org.mockito.Mockito.reset((Object)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notificationService));}
 @Test void generationIsIdempotentAndDoesNotCreateLunchSlots(){
  var f=fixture();var date=tomorrow().toLocalDate();
  planning.addWeekly(f.doctor().getId(),rule(date,8,12));planning.addWeekly(f.doctor().getId(),rule(date,13,17));
  assertThat(planning.generate(f.doctor().getId()).created()).isEqualTo(2);
  assertThat(planning.generate(f.doctor().getId()).created()).isZero();
  var available=schedules.availableSlots(f.doctor().getId(),date);
  assertThat(available).hasSize(16);assertThat(available).noneMatch(s->s.startAt().getHour()==12);
 }
 @Test void weeklyRejectsOverlapOverEightHoursAndPastDates(){
  var f=fixture();var d=tomorrow().toLocalDate();planning.addWeekly(f.doctor().getId(),rule(d,8,12));
  expect(()->planning.addWeekly(f.doctor().getId(),rule(d,11,13)),ErrorCode.WEEKLY_SCHEDULE_CONFLICT);
  expect(()->planning.addWeekly(f.doctor().getId(),rule(d,13,18)),ErrorCode.WEEKLY_SCHEDULE_CONFLICT);
  expect(()->planning.addWeekly(f.doctor().getId(),rule(d.minusDays(2),8,9)),ErrorCode.INVALID_SCHEDULE_TIME);
 }
 @Test void existingManualScheduleIsPreservedAndConflictReported(){
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));
  planning.addWeekly(f.doctor().getId(),rule(tomorrow().toLocalDate(),8,12));
  var result=planning.generate(f.doctor().getId());assertThat(result.created()).isZero();assertThat(result.conflicts()).hasSize(1);
  assertThat(scheduleRepository.findById(ca.id())).isPresent();
 }
 @Test void pendingLeaveBlocksBookingAndWithdrawRestoresAvailability(){
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));
  var l=planning.request(new LeaveRequest(tomorrow(),tomorrow().plusMinutes(30),"Nghỉ nửa ca"));
  assertThat(schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate())).hasSize(1);
  auth(f.patient());expect(()->booking.book(new CreateAppointmentRequest(ca.slots().get(0).id())),ErrorCode.SCHEDULE_NOT_AVAILABLE);
  auth(f.doctorAccount());planning.withdraw(l.id());
  assertThat(schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate())).hasSize(2);
 }
 @Test void withdrawalDoesNotReopenCanceledSchedule(){
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));
  var l=planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));schedules.deactivate(ca.id());planning.withdraw(l.id());
  assertThat(schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate())).isEmpty();
 }
 @Test void approvalRequiresConfirmationAndCancelsAtomicallyWithContactsAndNotices(){
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));
  auth(f.patient());var a=booking.book(new CreateAppointmentRequest(ca.slots().get(0).id()));
  auth(f.doctorAccount());var l=planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));
  auth(f.admin());expect(()->planning.review(l.id(),new ReviewRequest("APPROVED",false,"Đã xác nhận nghỉ")),ErrorCode.LEAVE_CANCELLATION_REQUIRED);
  assertThat(appointments.findById(a.id()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
  assertThat(leaves.findById(l.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
  planning.review(l.id(),new ReviewRequest("APPROVED",true,"Đã xác nhận nghỉ"));
  assertThat(appointments.findById(a.id()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CANCELED);
  assertThat(appointments.findById(a.id()).orElseThrow().getStatusReason()).contains("Bác sĩ nghỉ");
  assertThat(notifications.countByRecipientIdAndReadFalse(f.patient().getId())).isEqualTo(2);
  var affected=planning.affected(l.id());assertThat(affected).hasSize(1);assertThat(affected.get(0).contactedAt()).isNull();
  planning.contact(l.id(),affected.get(0).contactId());assertThat(planning.affected(l.id()).get(0).contactedAt()).isNotNull();
  expect(()->planning.review(l.id(),new ReviewRequest("APPROVED",true,"Lặp")),ErrorCode.LEAVE_REVIEW_CONFLICT);
  assertThat(notifications.countByRecipientIdAndReadFalse(f.patient().getId())).isEqualTo(2);
 }
 @Test void rejectingLeaveRetainsBookingAndReopensOnlyEmptySlots(){
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));
  auth(f.patient());var a=booking.book(new CreateAppointmentRequest(ca.slots().get(0).id()));
  auth(f.doctorAccount());var l=planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));
  auth(f.admin());planning.review(l.id(),new ReviewRequest("REJECTED",false,"Không duyệt"));
  assertThat(schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate())).hasSize(1);
  assertThat(appointments.findById(a.id()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
 }
 @Test void cancellationDuringPendingLeaveDoesNotExposeTheSlot(){
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));
  auth(f.patient());var a=booking.book(new CreateAppointmentRequest(ca.slots().get(0).id()));
  auth(f.doctorAccount());var l=planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));
  auth(f.patient());booking.cancel(a.id());assertThat(schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate())).isEmpty();
  auth(f.doctorAccount());planning.withdraw(l.id());
  assertThat(schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate())).hasSize(2);
 }
 @Test void pendingLeaveAlsoBlocksSchedulesGeneratedLater(){
  var f=fixture();auth(f.doctorAccount());planning.request(new LeaveRequest(tomorrow().withHour(8),tomorrow().withHour(12),"Nghỉ"));
  planning.addWeekly(f.doctor().getId(),rule(tomorrow().toLocalDate(),8,12));planning.generate(f.doctor().getId());
  assertThat(schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate())).isEmpty();
 }
 @Test void anotherDoctorCannotWithdrawTheRequest(){
  var f=fixture();auth(f.doctorAccount());var l=planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));
  auth(fixture().doctorAccount());expect(()->planning.withdraw(l.id()),ErrorCode.LEAVE_NOT_FOUND);
 }
 @Test void endingWeeklyScheduleCannotSilentlyCancelAppointments(){
  var f=fixture();var d=tomorrow().toLocalDate();var w=planning.addWeekly(f.doctor().getId(),rule(d,8,12));planning.generate(f.doctor().getId());
  var slot=schedules.availableSlots(f.doctor().getId(),d).get(0);auth(f.patient());var a=booking.book(new CreateAppointmentRequest(slot.id()));
  auth(f.admin());expect(()->planning.endWeekly(f.doctor().getId(),w.id(),d),ErrorCode.LEAVE_CANCELLATION_REQUIRED);
  assertThat(planning.weekly(f.doctor().getId()).get(0).active()).isTrue();
  booking.adminCancel(a.id(),new com.xxxx.clinicbookingsystem.appointment.dto.AppointmentReasonRequest("Ngừng lịch tuần"));planning.endWeekly(f.doctor().getId(),w.id(),d);
  assertThat(schedules.availableSlots(f.doctor().getId(),d)).isEmpty();
  assertThat(planning.generate(f.doctor().getId()).created()).isZero();
 }
 @Test void concurrentGenerationCreatesOnlyOneSet()throws Exception{
  var f=fixture();planning.addWeekly(f.doctor().getId(),rule(tomorrow().toLocalDate(),8,12));
  var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
  Callable<Integer> generate=()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException();return planning.generate(f.doctor().getId()).created();};
  try{var a=pool.submit(generate);var b=pool.submit(generate);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)+b.get(15,TimeUnit.SECONDS)).isEqualTo(1);}finally{start.countDown();pool.shutdownNow();}
  assertThat(schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate())).hasSize(8);
 }
 @Test void approvalRollsBackWhenNotificationCannotBeStored(){
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));
  auth(f.patient());var a=booking.book(new CreateAppointmentRequest(ca.slots().get(0).id()));
  auth(f.doctorAccount());var l=planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));auth(f.admin());
  var target=(com.xxxx.clinicbookingsystem.notification.service.NotificationService)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notificationService);
  org.mockito.Mockito.doThrow(new IllegalStateException("notification unavailable")).when(target).appointmentChanged(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(com.xxxx.clinicbookingsystem.notification.entity.NotificationType.APPOINTMENT_CANCELED));
  assertThatThrownBy(()->planning.review(l.id(),new ReviewRequest("APPROVED",true,"Đồng ý"))).isInstanceOf(IllegalStateException.class);
  assertThat(appointments.findById(a.id()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
  assertThat(leaves.findById(l.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
  assertThat(notifications.countByRecipientIdAndReadFalse(f.patient().getId())).isEqualTo(1);
 }
 @Test void planningEndpointsEnforceRolesAndReturnRealPreview()throws Exception{
  var f=fixture();String patient=token(f.patient());String doctor=token(f.doctorAccount());String admin=token(f.admin());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/leaves").header("Authorization",patient)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/leaves").header("Authorization",doctor)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/doctors/me/leaves").header("Authorization",admin)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/doctors/"+f.doctor().getId()+"/weekly-schedules").header("Authorization",admin)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));auth(f.patient());booking.book(new CreateAppointmentRequest(ca.slots().get(0).id()));auth(f.doctorAccount());var l=planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));
  SecurityContextHolder.clearContext();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/leaves/"+l.id()+"/affected").header("Authorization",admin)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].patientAccountId").value(f.patient().getId()));
 }
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
 void leaveAndBookingSerializeWithoutLosingAppointments(boolean leaveFirst)throws Exception{
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));clear();
  var pool=Executors.newFixedThreadPool(2);var holding=new CountDownLatch(1);var release=new CountDownLatch(1);
  try{
   var first=pool.submit(()->{
    auth(leaveFirst?f.doctorAccount():f.patient());
    try{new TransactionTemplate(transactions).executeWithoutResult(tx->{
     if(leaveFirst)planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));else booking.book(new CreateAppointmentRequest(ca.slots().get(0).id()));
     holding.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    });}finally{SecurityContextHolder.clearContext();}
   });
   assertThat(holding.await(5,TimeUnit.SECONDS)).isTrue();
   var second=pool.submit(()->{auth(leaveFirst?f.patient():f.doctorAccount());try{
    if(leaveFirst)booking.book(new CreateAppointmentRequest(ca.slots().get(0).id()));else planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));return (ErrorCode)null;
   }catch(AppException e){return e.getErrorCode();}finally{SecurityContextHolder.clearContext();}});
   assertThatThrownBy(()->second.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);release.countDown();first.get(10,TimeUnit.SECONDS);
   assertThat(second.get(10,TimeUnit.SECONDS)).isEqualTo(leaveFirst?ErrorCode.SCHEDULE_NOT_AVAILABLE:null);
   assertThat(schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate())).isEmpty();
   auth(f.admin());var l=planning.listLeaves(f.doctor().getId()).get(0);planning.review(l.id(),new ReviewRequest("APPROVED",true,"Đồng ý"));
   assertThat(planning.affected(l.id())).hasSize(leaveFirst?0:1);
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void httpRoundTripFromWeeklyTemplateToLeaveCancellation()throws Exception{
  var f=fixture();String admin=token(f.admin());String doctor=token(f.doctorAccount());String patient=token(f.patient());
  String date=tomorrow().toLocalDate().toString();
  String ruleBody="{\"dayOfWeek\":"+tomorrow().getDayOfWeek().getValue()+",\"startTime\":\"08:00\",\"endTime\":\"12:00\",\"effectiveFrom\":\""+date+"\",\"effectiveTo\":\""+date+"\"}";
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/doctors/"+f.doctor().getId()+"/weekly-schedules").header("Authorization",admin).contentType("application/json").content(ruleBody)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.created").value(1));
  var slot=schedules.availableSlots(f.doctor().getId(),tomorrow().toLocalDate()).get(0);
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/appointments").header("Authorization",patient).contentType("application/json").content("{\"slotId\":"+slot.id()+"}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  String leaveBody="{\"startAt\":\""+date+"T08:00:00\",\"endAt\":\""+date+"T12:00:00\",\"reason\":\"Nghỉ phép\"}";
  String result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/doctors/me/leaves").header("Authorization",doctor).contentType("application/json").content(leaveBody)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString();
  long leaveId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result).path("data").path("id").asLong();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/doctors/"+f.doctor().getId()+"/available-slots").param("date",date)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(0));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/leaves/"+leaveId+"/review").header("Authorization",admin).contentType("application/json").content("{\"decision\":\"APPROVED\",\"confirmCancellation\":true,\"reason\":\"Đã duyệt\"}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("APPROVED"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/leaves/"+leaveId+"/affected").header("Authorization",admin)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].status").value("CANCELED"));
 }
 @Test void batchAddsWorkWeekAndSkipsTheExistingIdenticalMonday(){
  var f=fixture();var from=tomorrow().toLocalDate();var to=from.plusDays(27);
  planning.addWeekly(f.doctor().getId(),new WeeklyRequest(1,LocalTime.of(8,0),LocalTime.of(12,0),from,to));
  var request=batch(from,to,Set.of(1,2,3,4,5));
  var result=planning.addWeeklyBatch(f.doctor().getId(),request);
  assertThat(result.added()).isEqualTo(9);assertThat(result.skipped()).isEqualTo(1);assertThat(result.created()).isEqualTo(40);
  assertThat(planning.weekly(f.doctor().getId())).hasSize(10);
  var again=planning.addWeeklyBatch(f.doctor().getId(),request);
  assertThat(again.added()).isZero();assertThat(again.skipped()).isEqualTo(10);assertThat(again.created()).isZero();
 }
 @Test void batchConflictOnLaterDayRollsBackEarlierDays(){
  var f=fixture();var from=tomorrow().toLocalDate();var to=from.plusDays(14);
  planning.addWeekly(f.doctor().getId(),new WeeklyRequest(2,LocalTime.of(8,30),LocalTime.of(12,0),from,to));
  assertThatThrownBy(()->planning.addWeeklyBatch(f.doctor().getId(),batch(from,to,Set.of(1,2))))
    .isInstanceOf(WeeklyBatchConflict.class).hasMessageContaining("Thứ 3");
  assertThat(planning.weekly(f.doctor().getId())).hasSize(1);
  assertThat(scheduleRepository.findAllByDoctorIdOrderByStartAtDesc(f.doctor().getId())).isEmpty();
 }
 @Test void oversizedBatchDoesNotPersistTheFirstWindow(){
  var f=fixture();var d=tomorrow().toLocalDate();var request=new WeeklyBatchRequest(Set.of(1),List.of(new TimeWindow(LocalTime.of(8,0),LocalTime.of(12,0)),new TimeWindow(LocalTime.of(13,0),LocalTime.of(18,0))),d,d.plusDays(7));
  assertThatThrownBy(()->planning.addWeeklyBatch(f.doctor().getId(),request)).isInstanceOf(WeeklyBatchConflict.class);
  assertThat(planning.weekly(f.doctor().getId())).isEmpty();
 }
 @Test void batchHttpValidatesNestedFieldsAndEnforcesAdmin()throws Exception{
  var f=fixture();var url="/api/v1/admin/doctors/"+f.doctor().getId()+"/weekly-schedules/batch";
  var body="{\"daysOfWeek\":[1],\"windows\":[null],\"effectiveFrom\":\""+tomorrow().toLocalDate()+"\",\"effectiveTo\":\""+tomorrow().toLocalDate()+"\"}";
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).header("Authorization",token(f.patient())).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).header("Authorization",token(f.admin())).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
 }
 @Test void twoConcurrentBatchesDoNotDuplicateTemplatesOrSlots()throws Exception{
  var f=fixture();var d=tomorrow().toLocalDate();var request=batch(d,d.plusDays(27),Set.of(1,2,3,4,5));var pool=Executors.newFixedThreadPool(2);
  var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
  Callable<WeeklyBatchResult> action=()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException();return planning.addWeeklyBatch(f.doctor().getId(),request);};
  try{var a=pool.submit(action);var b=pool.submit(action);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();var first=a.get(20,TimeUnit.SECONDS);var second=b.get(20,TimeUnit.SECONDS);assertThat(first.added()+second.added()).isEqualTo(10);assertThat(first.created()+second.created()).isEqualTo(40);assertThat(planning.weekly(f.doctor().getId())).hasSize(10);}finally{start.countDown();pool.shutdownNow();}
 }
 private WeeklyBatchRequest batch(LocalDate from,LocalDate to,Set<Integer> days){return new WeeklyBatchRequest(days,List.of(new TimeWindow(LocalTime.of(8,0),LocalTime.of(12,0)),new TimeWindow(LocalTime.of(13,0),LocalTime.of(17,0))),from,to);}
 @Test void availableDatesAreOrderedAndExcludeDaysWithoutFreeSlots(){
  var f=fixture();auth(f.doctorAccount());var date=tomorrow().toLocalDate();
  var first=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));
  schedules.create(new CreateScheduleRequest(tomorrow().plusDays(2),tomorrow().plusDays(2).plusMinutes(30)));
  var dates=schedules.availableDates(f.doctor().getId(),date,3);
  assertThat(dates).extracting(com.xxxx.clinicbookingsystem.schedule.dto.AvailableDateResponse::date).containsExactly(date,date.plusDays(2));
  assertThat(dates).extracting(com.xxxx.clinicbookingsystem.schedule.dto.AvailableDateResponse::availableSlots).containsExactly(2L,1L);
  planning.request(new LeaveRequest(tomorrow(),tomorrow().plusHours(1),"Nghỉ"));
  assertThat(schedules.availableDates(f.doctor().getId(),date,3)).hasSize(1);
  assertThat(schedules.availableDates(f.doctor().getId(),date,2)).isEmpty();
 }
 @Test void fullyBookedDaysDisappearAndCanceledBookingRestoresTheDate(){
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusMinutes(30)));
  auth(f.patient());var a=booking.book(new CreateAppointmentRequest(ca.slots().get(0).id()));
  assertThat(schedules.availableDates(f.doctor().getId(),tomorrow().toLocalDate(),1)).isEmpty();
  booking.cancel(a.id());assertThat(schedules.availableDates(f.doctor().getId(),tomorrow().toLocalDate(),1).get(0).availableSlots()).isEqualTo(1);
 }
 @Test void availableDatesArePublicAndValidateTheRequestedRange()throws Exception{
  var f=fixture();auth(f.doctorAccount());schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));SecurityContextHolder.clearContext();
  String path="/api/v1/doctors/"+f.doctor().getId()+"/available-dates";
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("from",tomorrow().toLocalDate().toString()).param("days","1"))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].availableSlots").value(2));
  for(String days:List.of("0","91","abc"))mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("days",days)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("from",LocalDate.now(clock).minusDays(1).toString())).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
 }
 @Test void availableDatesHonorDisabledScheduleAndDoctor(){
  var f=fixture();auth(f.doctorAccount());var ca=schedules.create(new CreateScheduleRequest(tomorrow(),tomorrow().plusHours(1)));schedules.deactivate(ca.id());
  assertThat(schedules.availableDates(f.doctor().getId(),tomorrow().toLocalDate(),1)).isEmpty();
  var d=doctors.findById(f.doctor().getId()).orElseThrow();d.setActive(false);doctors.saveAndFlush(d);
  expect(()->schedules.availableDates(d.getId(),tomorrow().toLocalDate(),1),ErrorCode.DOCTOR_NOT_AVAILABLE);
 }
 private String token(Account a){SecurityContextHolder.clearContext();return "Bearer "+jwt.generateToken(a.getUsername());}
 private WeeklyRequest rule(LocalDate d,int from,int to){return new WeeklyRequest(d.getDayOfWeek().getValue(),LocalTime.of(from,0),LocalTime.of(to,0),d,d);}
 private LocalDateTime tomorrow(){return LocalDate.now(clock).plusDays(1).atTime(9,0);}
 private void expect(Runnable action,ErrorCode code){assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(code));}
 private void auth(Account a){var p=new CustomUserDetails(a);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
 private Account account(String role){var a=new Account();var name=UUID.randomUUID().toString();a.setUsername(name);a.setEmail(name+"@test.com");a.setPassword("unused");a.setRole(roles.findByRoleName(role).orElseThrow());return accounts.saveAndFlush(a);}
 private Fixture fixture(){var da=account("DOCTOR");var s=new Specialty();s.setName("Planning "+UUID.randomUUID());s=specialties.saveAndFlush(s);var d=new Doctor();d.setFullName("Planning doctor");d.setAccount(da);d.setSpecialty(s);return new Fixture(da,account("PATIENT"),account("ADMIN"),doctors.saveAndFlush(d));}
 private record Fixture(Account doctorAccount,Account patient,Account admin,Doctor doctor){}
}
