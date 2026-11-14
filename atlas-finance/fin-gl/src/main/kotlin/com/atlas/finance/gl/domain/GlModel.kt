package com.atlas.finance.gl.domain

import com.atlas.finance.accounting.api.AccountType
import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "gl_accounts", schema = "fin")
class GlAccount(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "company_id", nullable = false)
    val companyId: UUID,

    @Column(name = "account_code", nullable = false, length = 30)
    val accountCode: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    val accountType: AccountType,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Column(name = "is_reconciliation", nullable = false)
    val isReconciliation: Boolean = false,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
)

@Entity
@Table(name = "fiscal_periods", schema = "fin")
class FiscalPeriod(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "company_id", nullable = false)
    val companyId: UUID,

    @Column(name = "ledger_code", nullable = false, length = 10)
    val ledgerCode: String,

    @Column(name = "fiscal_year", nullable = false)
    val fiscalYear: Int,

    @Column(name = "fiscal_period", nullable = false)
    val fiscalPeriod: Int,

    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    val endDate: LocalDate,

    @Column(name = "is_open", nullable = false)
    var isOpen: Boolean = true,

    @Column(name = "is_special_period", nullable = false)
    val isSpecialPeriod: Boolean = false
)

@Repository
interface GlAccountRepository : JpaRepository<GlAccount, UUID> {
    fun findByCompanyIdAndAccountCode(companyId: UUID, accountCode: String): GlAccount?
}

@Repository
interface FiscalPeriodRepository : JpaRepository<FiscalPeriod, UUID> {
    fun findByCompanyIdAndLedgerCodeAndFiscalYearAndFiscalPeriod(
        companyId: UUID,
        ledgerCode: String,
        fiscalYear: Int,
        fiscalPeriod: Int
    ): FiscalPeriod?
}

@Service
@Transactional
class GlService(
    private val accountRepository: GlAccountRepository,
    private val periodRepository: FiscalPeriodRepository
) {
    fun requireAccount(companyId: UUID, code: String): GlAccount {
        return accountRepository.findByCompanyIdAndAccountCode(companyId, code)
            ?: throw IllegalArgumentException("GL Account $code not found for company $companyId")
    }

    fun isPeriodOpen(companyId: UUID, ledgerCode: String, date: LocalDate): Boolean {
        val period = periodRepository.findByCompanyIdAndLedgerCodeAndFiscalYearAndFiscalPeriod(
            companyId = companyId,
            ledgerCode = ledgerCode,
            fiscalYear = date.year,
            fiscalPeriod = date.monthValue
        )
        return period?.isOpen ?: true // Default open if period record not populated in dev
    }

    fun closePeriod(companyId: UUID, ledgerCode: String, year: Int, period: Int) {
        val p = periodRepository.findByCompanyIdAndLedgerCodeAndFiscalYearAndFiscalPeriod(
            companyId, ledgerCode, year, period
        ) ?: throw IllegalArgumentException("Period $year-$period not found")
        p.isOpen = false
        periodRepository.save(p)
    }
}
