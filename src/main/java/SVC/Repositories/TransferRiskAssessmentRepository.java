package SVC.Repositories;

import SVC.Enums.AssessmentStatus;
import SVC.Models.TransferRiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferRiskAssessmentRepository extends JpaRepository<TransferRiskAssessment, UUID> {

    List<TransferRiskAssessment> findAllByStatusOrderByCreatedAtDesc(AssessmentStatus status);
}
