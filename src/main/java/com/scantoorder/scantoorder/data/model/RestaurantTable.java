package com.scantoorder.scantoorder.data.model;

import com.scantoorder.scantoorder.utils.CodeGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@jakarta.persistence.Table(uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "display_number"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantTable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String tableId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @Column(unique = true)
    private String tableNumber;
    private Integer displayNumber;
    private  int Capacity;
    private boolean isActive;

    @PrePersist
    public void prePersist(){
         isActive=true;
         tableNumber= CodeGenerator.generate(CodePrefix.TABLE);
     }
}
