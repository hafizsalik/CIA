package com.cia.app.domain

/**
 * انواع تراکنش در CIA.
 * انتقال داخلی، قرض و تسویه باید از درآمد/هزینه عادی تفکیک شوند.
 */
enum class TransactionType(val title: String, val requiresPartyChoice: Boolean) {
    MONEY_RECEIVED("پول گرفتم", true),
    MONEY_PAID("پول دادم", true),
    INCOME("درآمد", false),
    EXPENSE("هزینه", false),
    GIFT_RECEIVED("هدیه گرفتم", false),
    GIFT_GIVEN("هدیه دادم", false),
    INTERNAL_TRANSFER("انتقال بین حساب‌های خودم", false),
    LOAN_GIVEN("قرض دادم", true),
    LOAN_RECEIVED("قرض گرفتم", true),
    DEBT_SETTLEMENT("تسویه بدهی", true)
}

enum class PartySelection {
    /** کاربر طرف‌حساب مشخصی انتخاب کرده است. */
    SELECTED,

    /** کاربر آگاهانه گزینه بدون طرف‌حساب را انتخاب کرده است. */
    NONE
}

data class TransactionDraft(
    val type: TransactionType,
    val amountMinor: Long?,
    val currencyCode: String = "IRR",
    val occurredAtEpochMillis: Long,
    val accountId: String?,
    val destinationAccountId: String? = null,
    val partyId: String? = null,
    val partySelection: PartySelection? = null,
    val categoryId: String? = null,
    val description: String? = null,
    val attachmentUris: List<String> = emptyList(),
    val sourceSmsId: String? = null
)

sealed interface DraftValidation {
    data object Valid : DraftValidation
    data class Invalid(val errors: List<String>) : DraftValidation
}

object TransactionValidator {
    fun validate(draft: TransactionDraft): DraftValidation {
        val errors = mutableListOf<String>()

        if (draft.amountMinor == null || draft.amountMinor <= 0L) {
            errors += "مبلغ باید بیشتر از صفر باشد."
        }
        if (draft.occurredAtEpochMillis <= 0L) {
            errors += "تاریخ و ساعت تراکنش معتبر نیست."
        }
        if (draft.accountId.isNullOrBlank()) {
            errors += "انتخاب حساب الزامی است."
        }

        if (draft.type.requiresPartyChoice) {
            when (draft.partySelection) {
                PartySelection.SELECTED -> {
                    if (draft.partyId.isNullOrBlank()) {
                        errors += "طرف‌حساب را انتخاب کنید."
                    }
                }
                PartySelection.NONE -> Unit
                null -> errors += "طرف‌حساب را انتخاب کنید یا گزینه بدون طرف‌حساب را بزنید."
            }
        }

        if (draft.type == TransactionType.INTERNAL_TRANSFER) {
            if (draft.destinationAccountId.isNullOrBlank()) {
                errors += "حساب مقصد را انتخاب کنید."
            } else if (draft.destinationAccountId == draft.accountId) {
                errors += "حساب مبدأ و مقصد نمی‌توانند یکسان باشند."
            }
        }

        return if (errors.isEmpty()) DraftValidation.Valid
        else DraftValidation.Invalid(errors)
    }
}
