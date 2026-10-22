package com.xxxx.clinicbookingsystem.schedule.planning;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="clinic.schedules.generation-enabled",havingValue="true",matchIfMissing=true)
public class ScheduleGenerationJob  {
    private static final Logger log=LoggerFactory.getLogger(ScheduleGenerationJob.class);
    private final WeeklyScheduleRepository repository;
    private final SchedulePlanningService service;
    @Scheduled(cron="${clinic.schedules.generation-cron:0 5 0 * * *}",zone="Asia/Ho_Chi_Minh")
    public void daily() {
        generate();
    }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onReady() {
        generate();
    }
    public void generate() {
        for(Long id:repository.activeDoctorIds())try {
            var result=service.generate(id);
            if(!result.conflicts().isEmpty())log.warn("Schedule conflicts for doctor {}: {}",id,result.conflicts());
        }
        catch(Exception e) {
            log.error("Could not generate schedules for doctor {}",id,e);
        }
    }
}
