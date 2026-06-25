package com.xxxx.clinicbookingsystem.role.entity;

import com.xxxx.clinicbookingsystem.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
//@SuperBuilder
//@Builder
public class Role extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long id;
    
    @Column(name = "role_name", nullable = false, unique = true, length = 50)
    private String roleName;

}
