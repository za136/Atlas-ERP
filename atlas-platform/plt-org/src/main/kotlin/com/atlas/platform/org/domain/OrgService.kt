package com.atlas.platform.org.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

@Repository
interface CompanyRepository : JpaRepository<Company, UUID> {
    fun findByCode(code: String): Optional<Company>
}

@Repository
interface PlantRepository : JpaRepository<Plant, UUID> {
    fun findByCompanyIdAndCode(companyId: UUID, code: String): Optional<Plant>
}

@Repository
interface StorageLocationRepository : JpaRepository<StorageLocation, UUID> {
    fun findByPlantIdAndCode(plantId: UUID, code: String): Optional<StorageLocation>
}

@Repository
interface CostCenterRepository : JpaRepository<CostCenter, UUID> {
    fun findByCompanyIdAndCode(companyId: UUID, code: String): Optional<CostCenter>
}

@Repository
interface ProfitCenterRepository : JpaRepository<ProfitCenter, UUID> {
    fun findByCompanyIdAndCode(companyId: UUID, code: String): Optional<ProfitCenter>
}

@Service
@Transactional
class OrgService(
    private val companyRepository: CompanyRepository,
    private val plantRepository: PlantRepository,
    private val costCenterRepository: CostCenterRepository
) {
    fun getCompanyByCode(code: String): Company =
        companyRepository.findByCode(code).orElseThrow {
            IllegalArgumentException("Company not found with code: $code")
        }

    fun getPlantByCode(companyId: UUID, plantCode: String): Plant =
        plantRepository.findByCompanyIdAndCode(companyId, plantCode).orElseThrow {
            IllegalArgumentException("Plant not found with code: $plantCode in company: $companyId")
        }

    fun getCostCenterByCode(companyId: UUID, costCenterCode: String): CostCenter =
        costCenterRepository.findByCompanyIdAndCode(companyId, costCenterCode).orElseThrow {
            IllegalArgumentException("Cost center not found: $costCenterCode in company: $companyId")
        }
}
