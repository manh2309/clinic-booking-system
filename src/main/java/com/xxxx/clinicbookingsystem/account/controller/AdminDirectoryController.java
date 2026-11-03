package com.xxxx.clinicbookingsystem.account.controller;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.dto.AccountResponse;
import com.xxxx.clinicbookingsystem.doctor.entity.Doctor;
import com.xxxx.clinicbookingsystem.doctor.dto.DoctorResponse;
import com.xxxx.clinicbookingsystem.specialty.entity.Specialty;
import com.xxxx.clinicbookingsystem.specialty.dto.SpecialtyResponse;
import com.xxxx.clinicbookingsystem.common.response.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.function.Function;

@RestController @RequestMapping("/api/v1/admin/directory") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") @Validated @Transactional(readOnly=true)
public class AdminDirectoryController {
    private final EntityManager em;
    @GetMapping("/accounts")
    public ApiResponse<PageResponse<AccountResponse>> accounts(@RequestParam(defaultValue="") @Size(max=100) String keyword,
            @RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return result("Account","a","(lower(a.username) like :q escape '!' or lower(coalesce(a.email,'')) like :q escape '!' or coalesce(a.phone,'') like :q escape '!')", "a.isActive",Account.class,
                a->new AccountResponse(a.getId(),a.getUsername(),a.getEmail(),a.getRole().getRoleName(),a.getIsActive()),keyword,active,page,size);
    }
    @GetMapping("/doctors")
    public ApiResponse<PageResponse<DoctorResponse>> doctors(@RequestParam(defaultValue="") @Size(max=100) String keyword,
            @RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return result("Doctor","d","(lower(d.fullName) like :q escape '!' or lower(d.specialty.name) like :q escape '!')","d.active",Doctor.class,
                d->new DoctorResponse(d.getId(),d.getFullName(),d.getQualification(),d.getDescription(),d.getSpecialty().getId(),d.getSpecialty().getName(),d.getActive()),keyword,active,page,size);
    }
    @GetMapping("/specialties")
    public ApiResponse<PageResponse<SpecialtyResponse>> specialties(@RequestParam(defaultValue="") @Size(max=100) String keyword,
            @RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return result("Specialty","s","lower(s.name) like :q escape '!'","s.active",Specialty.class,
                s->new SpecialtyResponse(s.getId(),s.getName(),s.getDescription(),s.getActive()),keyword,active,page,size);
    }
    private <E,T> ApiResponse<PageResponse<T>> result(String entity,String alias,String search,String activeField,Class<E> type,Function<E,T> mapper,String keyword,Boolean active,int page,int size) {
        String where=" from "+entity+" "+alias+" where "+search+" and (:active is null or "+activeField+"=:active)";
        String q="%"+keyword.trim().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var rows=em.createQuery("select "+alias+where+" order by "+alias+".id",type).setParameter("q",q).setParameter("active",active)
                .setFirstResult(Math.multiplyExact(page,size)).setMaxResults(size).getResultList();
        long total=em.createQuery("select count("+alias+")"+where,Long.class).setParameter("q",q).setParameter("active",active).getSingleResult();
        return ApiResponse.success(PageResponse.from(new PageImpl<>(rows.stream().map(mapper).toList(),PageRequest.of(page,size),total)));
    }
}
