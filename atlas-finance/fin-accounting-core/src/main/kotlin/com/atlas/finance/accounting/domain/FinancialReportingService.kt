package com.atlas.finance.accounting.domain

import com.atlas.finance.accounting.api.PostingSide
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class TrialBalanceLine(
    val accountCode: String,
    val currency: String,
    val totalDebit: BigDecimal,
    val totalCredit: BigDecimal,
    val balance: BigDecimal
)

data class TrialBalanceReport(
    val companyId: UUID,
    val ledgerCode: String,
    val asOfDate: LocalDate,
    val lines: List<TrialBalanceLine>,
    val totalDebits: BigDecimal,
    val totalCredits: BigDecimal,
    val isBalanced: Boolean
)

@Service
@Transactional(readOnly = true)
class FinancialReportingService(
    private val dsl: DSLContext
) {
    /**
     * Executes Trial Balance generation directly over the Universal Journal (FIN-GL-008, FIN-INV-3).
     */
    fun getTrialBalance(
        companyId: UUID,
        ledgerCode: String,
        asOfDate: LocalDate
    ): TrialBalanceReport {
        // Query line items via jOOQ DSL
        val entriesTable = table(name("fin", "universal_journal_entries"))
        val itemsTable = table(name("fin", "universal_journal_items"))

        val entryIdField = field(name("fin", "universal_journal_entries", "id"), UUID::class.java)
        val companyIdField = field(name("fin", "universal_journal_entries", "company_id"), UUID::class.java)
        val ledgerCodeField = field(name("fin", "universal_journal_entries", "ledger_code"), String::class.java)
        val postingDateField = field(name("fin", "universal_journal_entries", "posting_date"), LocalDate::class.java)

        val itemEntryIdField = field(name("fin", "universal_journal_items", "entry_id"), UUID::class.java)
        val accountCodeField = field(name("fin", "universal_journal_items", "account_code"), String::class.java)
        val postingSideField = field(name("fin", "universal_journal_items", "posting_side"), String::class.java)
        val amountField = field(name("fin", "universal_journal_items", "amount"), BigDecimal::class.java)
        val currencyField = field(name("fin", "universal_journal_items", "currency"), String::class.java)

        val debitAmount = `when`(postingSideField.eq(PostingSide.DEBIT.name), amountField).otherwise(BigDecimal.ZERO)
        val creditAmount = `when`(postingSideField.eq(PostingSide.CREDIT.name), amountField).otherwise(BigDecimal.ZERO)

        val results = dsl.select(
            accountCodeField,
            currencyField,
            sum(debitAmount).`as`("total_debit"),
            sum(creditAmount).`as`("total_credit")
        )
            .from(entriesTable)
            .join(itemsTable).on(entryIdField.eq(itemEntryIdField))
            .where(
                companyIdField.eq(companyId)
                    .and(ledgerCodeField.eq(ledgerCode))
                    .and(postingDateField.le(asOfDate))
            )
            .groupBy(accountCodeField, currencyField)
            .orderBy(accountCodeField)
            .fetch()

        val lines = results.map { r ->
            val debit = r.get("total_debit", BigDecimal::class.java) ?: BigDecimal.ZERO
            val credit = r.get("total_credit", BigDecimal::class.java) ?: BigDecimal.ZERO
            val balance = debit.subtract(credit)

            TrialBalanceLine(
                accountCode = r.get(accountCodeField) ?: "",
                currency = r.get(currencyField) ?: "USD",
                totalDebit = debit,
                totalCredit = credit,
                balance = balance
            )
        }

        val grandTotalDebits = lines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.totalDebit) }
        val grandTotalCredits = lines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.totalCredit) }

        return TrialBalanceReport(
            companyId = companyId,
            ledgerCode = ledgerCode,
            asOfDate = asOfDate,
            lines = lines,
            totalDebits = grandTotalDebits,
            totalCredits = grandTotalCredits,
            isBalanced = grandTotalDebits.compareTo(grandTotalCredits) == 0
        )
    }
}
